package com.radiance.client;

import com.mojang.logging.LogUtils;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
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
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

public class RadianceClient implements ClientModInitializer {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static Path radianceDir;

    @Override
    public void onInitializeClient() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path mcBaseDir = mc.runDirectory.toPath();
        radianceDir = mcBaseDir.resolve("radiance");
        try {
            Files.createDirectories(radianceDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // core lib
        String osName = System.getProperty("os.name");
        if (osName.toLowerCase().contains("windows")) {
            Path libTargetPath = radianceDir.resolve("core.lib");
            Path libResourcePath = Path.of("core.lib");
            copyFileFromResource(libTargetPath, libResourcePath);

            Path dllTargetPath = radianceDir.resolve("core.dll");
            Path dllResourcePath = Path.of("core.dll");
            copyFileFromResource(dllTargetPath, dllResourcePath);

            System.load(dllTargetPath.toAbsolutePath().toString());

            Path dlssTargetPath = radianceDir.resolve("nvngx_dlss.dll");
            Path dlssDTargetPath = radianceDir.resolve("nvngx_dlssd.dll");

            if (!Files.exists(dlssTargetPath) || !Files.exists(dlssDTargetPath)) {
                throw new RuntimeException("DLSS runtime libraries not found! Please make sure they are present in " + radianceDir);
            }
        } else if (osName.toLowerCase().contains("linux")) {
            Path soTargetPath = radianceDir.resolve("libcore.so");
            Path soResourcePath = Path.of("libcore.so");
            copyFileFromResource(soTargetPath, soResourcePath);

            System.load(soTargetPath.toAbsolutePath().toString());

            Path dlssTargetPath = radianceDir.resolve("libnvidia-ngx-dlss.so.310.5.3");
            Path dlssDTargetPath = radianceDir.resolve("libnvidia-ngx-dlssd.so.310.5.3");

            if (!Files.exists(dlssTargetPath) || !Files.exists(dlssDTargetPath)) {
                throw new RuntimeException("DLSS runtime libraries not found! Please make sure they are present in " + radianceDir);
            }
        } else {
            throw new RuntimeException("The OS " + osName + " is not supported");
        }

        // shaders
        Path shaderTargetPath = radianceDir.resolve("shaders");
        Path shaderResourcePath = Path.of("shaders");
        copyFolderFromResource(shaderTargetPath, shaderResourcePath);

        // modules
        Path moduleTargetPath = radianceDir.resolve("modules");
        Path moduleResourcePath = Path.of("modules");
        copyFolderFromResource(moduleTargetPath, moduleResourcePath);

        RendererProxy.initFolderPath(radianceDir.toAbsolutePath().toString());
        Pipeline.initFolderPath(radianceDir);

        Options.readOptions();

        Pipeline.reloadAllModuleEntries();
    }

    public void copyFileFromResource(Path targetPath, Path resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(toResourcePath(resourcePath))) {
            if (is == null) {
                throw new IOException("Cannot find target path: " + resourcePath);
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String toResourcePath(Path path) {
        String joined = StreamSupport.stream(path.spliterator(), false).map(Object::toString)
            .collect(Collectors.joining("/"));
        return "/" + joined;
    }

    public void copyFolderFromResource(Path targetPath, Path resourcePath) {
        String resourcePathStr = toResourcePath(resourcePath);
        URL url = getClass().getResource(resourcePathStr);

        if (url == null) {
            throw new RuntimeException("Resource folder not found: " + resourcePathStr);
        }

        try {
            URI uri = url.toURI();

            if ("jar".equals(uri.getScheme())) {
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                URI jarFileUri = conn.getJarFileURL().toURI();
                URI jarFsUri = URI.create("jar:" + jarFileUri);

                FileSystem fs = null;
                boolean created = false;
                try {
                    try {
                        fs = FileSystems.getFileSystem(jarFsUri);
                    } catch (FileSystemNotFoundException e) {
                        fs = FileSystems.newFileSystem(jarFsUri, Collections.emptyMap());
                        created = true;
                    }

                    Path root = fs.getPath(resourcePathStr);
                    walkAndCopy(root, targetPath, resourcePath);
                } finally {
                    if (created) {
                        try {
                            fs.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            } else {
                Path root = Paths.get(uri);
                walkAndCopy(root, targetPath, resourcePath);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to copy resource folder", e);
        }
    }

    private void walkAndCopy(Path walkRoot, Path targetRoot, Path baseResourcePath)
        throws IOException {
        try (Stream<Path> stream = Files.walk(walkRoot)) {
            stream.filter(Files::isRegularFile).forEach(source -> {
                String relativePathStr = walkRoot.relativize(source).toString();
                Path targetFile = targetRoot.resolve(relativePathStr);
                Path childResourcePath = baseResourcePath.resolve(relativePathStr);
                copyFileFromResource(targetFile, childResourcePath);
            });
        }
    }
}
