package com.radiance.client;

import com.mojang.logging.LogUtils;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

public final class RadianceClient {

    public static final Logger LOGGER = LogUtils.getLogger();
    private static final long PROFILER_PLAYER_POSITION_LOG_INTERVAL_NANOS = 1_000_000_000L;
    private static final boolean PROFILER_AUTO_FORWARD = Profiler.ENABLED
        && Boolean.parseBoolean(System.getenv().getOrDefault("RADIANCE_PROFILER_AUTO_FORWARD", "false"));

    public static Path radianceDir;
    private static long nextProfilerPlayerPositionLogNanos = 0L;
    private static Vec3 lastProfilerPlayerPosition = null;
    private static boolean profilerAutoForwardPressed = false;
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        RadianceClient initializer = new RadianceClient();
        initializer.initializeClient();
        initialized = true;
    }

    private void initializeClient() {
        Minecraft minecraftClient = Minecraft.getInstance();
        Path minecraftBaseDirectory = minecraftClient.gameDirectory.toPath();
        radianceDir = minecraftBaseDirectory.resolve("radiance");
        try {
            Files.createDirectories(radianceDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        String operatingSystemName = System.getProperty("os.name");
        if (operatingSystemName.toLowerCase().contains("windows")) {
            Path libTargetPath = radianceDir.resolve("core.lib");
            Path libResourcePath = Path.of("core.lib");
            copyFileFromResource(libTargetPath, libResourcePath);

            Path dllTargetPath = radianceDir.resolve("core.dll");
            Path dllResourcePath = Path.of("core.dll");
            copyFileFromResource(dllTargetPath, dllResourcePath);
            Path xessPath = radianceDir.resolve("libxess.dll");
            Path xessDx11Path = radianceDir.resolve("libxess_dx11.dll");
            Path xessFgPath = radianceDir.resolve("libxess_fg.dll");
            copyOptionalFileFromResource(xessPath, Path.of("libxess.dll"));

            copyOptionalFileFromResource(xessDx11Path, Path.of("libxess_dx11.dll"));
            copyOptionalFileFromResource(xessFgPath, Path.of("libxess_fg.dll"));

            loadOptionalLibrary(xessPath);

            System.load(dllTargetPath.toAbsolutePath().toString());
        } else if (operatingSystemName.toLowerCase().contains("linux")) {
            Path soTargetPath = radianceDir.resolve("libcore.so");
            Path soResourcePath = Path.of("libcore.so");
            copyFileFromResource(soTargetPath, soResourcePath);

            System.load(soTargetPath.toAbsolutePath().toString());
        } else {
            throw new RuntimeException("The OS " + operatingSystemName + " is not supported");
        }
        if (Profiler.ENABLED) {
            Profiler.initialize();
        }


        Path shaderTargetPath = radianceDir.resolve("shaders");
        Path shaderResourcePath = Path.of("shaders");
        copyFolderFromResource(shaderTargetPath, shaderResourcePath);


        Path moduleTargetPath = radianceDir.resolve("modules");
        Path moduleResourcePath = Path.of("modules");
        copyFolderFromResource(moduleTargetPath, moduleResourcePath);

        RendererProxy.initFolderPath(radianceDir.toAbsolutePath().toString());
        Pipeline.initFolderPath(radianceDir);

        Options.readOptions();

        Pipeline.reloadAllModuleEntries();
    }

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        tickProfiler(Minecraft.getInstance());
    }

    private static void tickProfiler(Minecraft client) {
        if (Profiler.ENABLED) {
            updateProfilerAutoForward(client);
            logProfilerPlayerPosition(client);
        }
    }

    private static void updateProfilerAutoForward(Minecraft client) {
        if (!PROFILER_AUTO_FORWARD) {
            return;
        }

        boolean shouldPressForward = client.player != null && client.level != null
            && client.screen == null && client.isWindowActive() && client.mouseHandler.isMouseGrabbed();
        client.options.keyUp.setDown(shouldPressForward);
        profilerAutoForwardPressed = shouldPressForward;
    }

    private static void logProfilerPlayerPosition(Minecraft client) {
        if (client.player == null || client.level == null) {
            lastProfilerPlayerPosition = null;
            nextProfilerPlayerPositionLogNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        if (now < nextProfilerPlayerPositionLogNanos) {
            return;
        }

        Vec3 position = client.player.position();
        double dx = lastProfilerPlayerPosition == null ? 0.0 : position.x - lastProfilerPlayerPosition.x;
        double dy = lastProfilerPlayerPosition == null ? 0.0 : position.y - lastProfilerPlayerPosition.y;
        double dz = lastProfilerPlayerPosition == null ? 0.0 : position.z - lastProfilerPlayerPosition.z;
        double horizontalDelta = Math.sqrt(dx * dx + dz * dz);

        LOGGER.info(
            "radiance.profiler.player_pos x={} y={} z={} dx={} dy={} dz={} horizontal_delta={} forward_pressed={} auto_forward={} focused={} cursor_locked={} screen={}",
            roundProfilerLogValue(position.x), roundProfilerLogValue(position.y),
            roundProfilerLogValue(position.z), roundProfilerLogValue(dx), roundProfilerLogValue(dy),
            roundProfilerLogValue(dz), roundProfilerLogValue(horizontalDelta),
            client.options.keyUp.isDown(), profilerAutoForwardPressed,
            client.isWindowActive(), client.mouseHandler.isMouseGrabbed(),
            client.screen == null ? "none" : client.screen.getClass().getSimpleName());

        lastProfilerPlayerPosition = position;
        nextProfilerPlayerPositionLogNanos = now + PROFILER_PLAYER_POSITION_LOG_INTERVAL_NANOS;
    }

    private static double roundProfilerLogValue(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public void copyFileFromResource(Path targetPath, Path resourcePath) {
        try (InputStream resourceStream = getClass().getResourceAsStream(toResourcePath(resourcePath))) {
            if (resourceStream == null) {
                throw new IOException("Cannot find target path: " + resourcePath);
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void copyOptionalFileFromResource(Path targetPath, Path resourcePath) {
        try (InputStream resourceStream = getClass().getResourceAsStream(toResourcePath(resourcePath))) {
            if (resourceStream == null) {
                return;
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadOptionalLibrary(Path path) {
        if (Files.exists(path)) {
            System.load(path.toAbsolutePath().toString());
        }
    }

    public String toResourcePath(Path path) {
        String joined = StreamSupport.stream(path.spliterator(), false).map(Object::toString)
            .collect(Collectors.joining("/"));
        return "/" + joined;
    }

    public void copyFolderFromResource(Path targetPath, Path resourcePath) {
        String resourcePathString = toResourcePath(resourcePath);
        URL url = getClass().getResource(resourcePathString);

        if (url == null) {
            throw new RuntimeException("Resource folder not found: " + resourcePathString);
        }

        try {
            URI uri = url.toURI();

            if ("jar".equals(uri.getScheme())) {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                URI jarFileUri = connection.getJarFileURL().toURI();
                URI jarFileSystemUri = URI.create("jar:" + jarFileUri);

                FileSystem fileSystem = null;
                boolean createdFileSystem = false;
                try {
                    try {
                        fileSystem = FileSystems.getFileSystem(jarFileSystemUri);
                    } catch (FileSystemNotFoundException e) {
                        fileSystem = FileSystems.newFileSystem(jarFileSystemUri, Collections.emptyMap());
                        createdFileSystem = true;
                    }

                    Path resourceRoot = fileSystem.getPath(resourcePathString);
                    walkAndCopy(resourceRoot, targetPath, resourcePath);
                } finally {
                    if (createdFileSystem) {
                        try {
                            fileSystem.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            } else {
                Path resourceRoot = Paths.get(uri);
                walkAndCopy(resourceRoot, targetPath, resourcePath);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to copy resource folder", e);
        }
    }

    private void walkAndCopy(Path walkRoot, Path targetRoot, Path baseResourcePath)
        throws IOException {
        try (Stream<Path> stream = Files.walk(walkRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(source -> !isMutableShaderSettingsResource(source))
                .forEach(source -> {
                    String relativePathString = walkRoot.relativize(source).toString();
                    Path targetFile = targetRoot.resolve(relativePathString);
                    Path childResourcePath = baseResourcePath.resolve(relativePathString);
                    copyFileFromResource(targetFile, childResourcePath);
                });
        }
    }

    private boolean isMutableShaderSettingsResource(Path resourceFile) {
        Path fileName = resourceFile == null ? null : resourceFile.getFileName();
        return fileName != null && fileName.toString().endsWith(".zip.txt");
    }
}
