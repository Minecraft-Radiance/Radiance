package com.radiance.client;

import com.mojang.logging.LogUtils;
import com.radiance.client.util.LightSourceRegistry;
import com.radiance.client.util.MaterialToolkit;
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
            Path dllTargetPath = radianceDir.resolve("core.dll");
            Path dllResourcePath = Path.of("core.dll");
            copyFileFromResource(dllTargetPath, dllResourcePath);
            Path ngxDlssPath = radianceDir.resolve("nvngx_dlss.dll");
            Path ngxDlssdPath = radianceDir.resolve("nvngx_dlssd.dll");
            Path ngxDlssgPath = radianceDir.resolve("nvngx_dlssg.dll");
            Path xessPath = radianceDir.resolve("libxess.dll");
            Path xessDx11Path = radianceDir.resolve("libxess_dx11.dll");
            Path xessFgPath = radianceDir.resolve("libxess_fg.dll");
            copyOptionalFileFromResource(ngxDlssPath, Path.of("nvngx_dlss.dll"));
            copyOptionalFileFromResource(ngxDlssdPath, Path.of("nvngx_dlssd.dll"));
            copyOptionalFileFromResource(ngxDlssgPath, Path.of("nvngx_dlssg.dll"));
            copyOptionalFileFromResource(xessPath, Path.of("libxess.dll"));
            // currently not used, can be used later for fg
            copyOptionalFileFromResource(xessDx11Path, Path.of("libxess_dx11.dll"));
            copyOptionalFileFromResource(xessFgPath, Path.of("libxess_fg.dll"));
            copyOptionalFileFromResource(radianceDir.resolve("sl.interposer.dll"),
                Path.of("sl.interposer.dll"));
            copyOptionalFileFromResource(radianceDir.resolve("sl.common.dll"),
                Path.of("sl.common.dll"));
            copyOptionalFileFromResource(radianceDir.resolve("sl.reflex.dll"),
                Path.of("sl.reflex.dll"));
            copyOptionalFileFromResource(radianceDir.resolve("sl.pcl.dll"),
                Path.of("sl.pcl.dll"));
            copyOptionalFileFromResource(radianceDir.resolve("NvLowLatencyVk.dll"),
                Path.of("NvLowLatencyVk.dll"));

            loadOptionalLibrary(xessPath);

            try {
                System.load(dllTargetPath.toAbsolutePath().toString());
            } catch (UnsatisfiedLinkError e) {
                throw new RuntimeException("Failed to load core library from: " + dllTargetPath
                    + ". This may indicate missing dependencies or an incompatible platform.", e);
            }
        } else if (osName.toLowerCase().contains("linux")) {
            Path soTargetPath = radianceDir.resolve("libcore.so");
            Path soResourcePath = Path.of("libcore.so");
            copyFileFromResource(soTargetPath, soResourcePath);

            try {
                System.load(soTargetPath.toAbsolutePath().toString());
            } catch (UnsatisfiedLinkError e) {
                throw new RuntimeException("Failed to load core library from: " + soTargetPath
                    + ". This may indicate missing dependencies or an incompatible platform.", e);
            }
        } else {
            throw new RuntimeException("The OS " + osName + " is not supported");
        }

        // shaders
        Path shaderTargetPath = radianceDir.resolve("shaders");
        Path shaderResourcePath = Path.of("shaders");
        copyFolderFromResource(shaderTargetPath, shaderResourcePath, true);

        // modules
        Path moduleTargetPath = radianceDir.resolve("modules");
        Path moduleResourcePath = Path.of("modules");
        copyFolderFromResource(moduleTargetPath, moduleResourcePath, true);

        RendererProxy.initFolderPath(radianceDir.toAbsolutePath().toString());
        Pipeline.initFolderPath(radianceDir);
        MaterialToolkit.init(radianceDir);
        LightSourceRegistry.init(radianceDir);

        Options.readOptions();

        Pipeline.reloadAllModuleEntries();
    }

    public void copyFileFromResource(Path targetPath, Path resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(toResourcePath(resourcePath))) {
            if (is == null) {
                if (Files.exists(targetPath)) {
                    LOGGER.warn("Resource {} not found in jar, using existing file {}", resourcePath,
                        targetPath.toAbsolutePath());
                    return;
                }
                throw new IOException("Required runtime file is missing from both jar and disk. Resource: "
                    + resourcePath + ", expected existing file: " + targetPath.toAbsolutePath());
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void copyOptionalFileFromResource(Path targetPath, Path resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(toResourcePath(resourcePath))) {
            if (is == null) {
                return;
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
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
        copyFolderFromResource(targetPath, resourcePath, false);
    }

    public void copyFolderFromResource(Path targetPath, Path resourcePath, boolean preserveExisting) {
        String resourcePathStr = toResourcePath(resourcePath);
        URL url = getClass().getResource(resourcePathStr);

        if (url == null) {
            if (Files.isDirectory(targetPath)) {
                LOGGER.warn("Resource folder {} not found in jar, using existing directory {}",
                    resourcePathStr, targetPath.toAbsolutePath());
                return;
            }
            throw new RuntimeException("Required runtime folder is missing from both jar and disk. Resource: "
                + resourcePathStr + ", expected existing directory: " + targetPath.toAbsolutePath());
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
                    walkAndCopy(root, targetPath, resourcePath, preserveExisting);
                } finally {
                    if (created) {
                        try {
                            fs.close();
                        } catch (IOException e) {
                            LOGGER.warn("Failed to close JAR filesystem", e);
                        }
                    }
                }
            } else {
                Path root = Paths.get(uri);
                walkAndCopy(root, targetPath, resourcePath, preserveExisting);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to copy resource folder", e);
        }
    }

    private void walkAndCopy(Path walkRoot, Path targetRoot, Path baseResourcePath,
        boolean preserveExisting)
        throws IOException {
        try (Stream<Path> stream = Files.walk(walkRoot)) {
            stream.filter(Files::isRegularFile).forEach(source -> {
                String relativePathStr = walkRoot.relativize(source).toString();
                Path targetFile = targetRoot.resolve(relativePathStr);
                if (preserveExisting && Files.exists(targetFile)) {
                    return;
                }
                Path childResourcePath = baseResourcePath.resolve(relativePathStr);
                copyFileFromResource(targetFile, childResourcePath);
            });
        }
    }
}
