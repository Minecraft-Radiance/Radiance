<p align="center">
  <img src="./.assets/logo.png" alt="Radiance Logo" width="200">
</p>

<h1 align="center">
  Radiance
  <br>

  <a href="https://github.com/Minecraft-Radiance/Radiance">
    <img src="https://img.shields.io/github/stars/Minecraft-Radiance/Radiance?style=flat&logo=github" alt="GitHub Stars">
  </a>

  <a href="https://discord.gg/y4Uzf6acqk">
    <img src="https://img.shields.io/badge/Discord-Join-5865F2?style=flat&logo=discord&logoColor=white" alt="Discord">
  </a>

  <a href="https://modrinth.com/mod/radiance-mod-windows">
    <img src="https://img.shields.io/badge/Modrinth-Windows-5CA424?style=flat&logo=modrinth&logoColor=white" alt="Modrinth Windows">
  </a>

  <a href="https://modrinth.com/mod/radiance-mod-linux">
    <img src="https://img.shields.io/badge/Modrinth-Linux-5CA424?style=flat&logo=modrinth&logoColor=white" alt="Modrinth Linux">
  </a>

  <a href="https://www.curseforge.com/minecraft/mc-mods/radiance">
  <img src="https://img.shields.io/badge/CurseForge-Download-F16436?style=flat&logo=curseforge&logoColor=white" alt="CurseForge">
  </a>

  <a href="https://github.com/Minecraft-Radiance/Radiance/blob/main/LICENCE">
    <img src="https://img.shields.io/github/license/Minecraft-Radiance/Radiance?style=flat" alt="License">
  </a>

  <a href="https://github.com/Minecraft-Radiance/Radiance/releases">
    <img src="https://img.shields.io/github/v/release/Minecraft-Radiance/Radiance?include_prereleases&label=Release&style=flat&logo=github&v=1" alt="GitHub Release">
  </a>

  <a href="https://www.youtube.com/@RadianceMod">
    <img src="https://img.shields.io/badge/YouTube-Subscribe-FF0000?style=flat&logo=youtube&logoColor=white" alt="YouTube">
  </a>

  <a href="https://b23.tv/bN7CTv1">
    <img src="https://img.shields.io/badge/Bilibili-Follow-00A1D6?style=flat&logo=bilibili&logoColor=white" alt="Bilibili">
  </a>

  <br><br>
</h1>

English | [简体中文](README-CN.md)

# Radiance

[Radiance](https://www.minecraft-radiance.com/) brings Vulkan world rendering and hardware ray tracing to Minecraft. Version **0.1.6-alpha** adds DLSS and FSR frame generation, expands Minecraft and mod loader support, and improves lighting, materials, performance, and stability.

![Radiance 0.1.6: a hot-air balloon above a sunlit landscape](.assets/cover-0.1.6.png)

[Modrinth — Windows](https://modrinth.com/mod/radiance-mod-windows) · [Modrinth — Linux](https://modrinth.com/mod/radiance-mod-linux) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/radiance)

## Features

- Hardware ray tracing for world geometry, entities, lighting, reflections, and transparent materials.
- Advanced lighting with atmosphere, clouds, fog, water, and rain-driven surface wetness.
- DLSS Ray Reconstruction, NRD with DLSS Super Resolution or FSR, and DLSS / FSR frame generation where supported.
- Dedicated rendering support for Distant Horizons, Flywheel / Create, and Sable.
- OpenGL UI rendering alongside Vulkan world rendering.

This repository contains the Java mod. The native renderer is in [MCVR](https://github.com/Minecraft-Radiance/MCVR), and the Advanced shader source is in [Shaderpack-Advanced](https://github.com/Minecraft-Radiance/Shaderpack-Advanced).

## Supported versions

Radiance is a **client-side** mod for **64-bit Windows and Linux**, with **macOS support planned** (we've just acquired a Mac laptop for development).

| Minecraft | Fabric source | Forge source | NeoForge source | Java |
| --- | --- | --- | --- | --- |
| 1.20.1 | [Fabric](https://github.com/Minecraft-Radiance/Radiance/tree/release-0.1.6-1.20.1-fabric) | [Forge](https://github.com/Minecraft-Radiance/Radiance/tree/release-0.1.6-1.20.1-forge) | [NeoForge](https://github.com/Minecraft-Radiance/Radiance/tree/release-0.1.6-1.20.1-neoforge) | 17 |
| 1.21.1 | [Fabric](https://github.com/Minecraft-Radiance/Radiance/tree/release-0.1.6-1.21.1-fabric) | [Forge](https://github.com/Minecraft-Radiance/Radiance/tree/release-0.1.6-1.21.1-forge) | [NeoForge](https://github.com/Minecraft-Radiance/Radiance/tree/release-0.1.6-1.21.1-neoforge) | 21 |
| 1.21.4 | [Fabric](https://github.com/Minecraft-Radiance/Radiance/tree/release-0.1.6-1.21.4-fabric) | [Forge](https://github.com/Minecraft-Radiance/Radiance/tree/release-0.1.6-1.21.4-forge) | [NeoForge](https://github.com/Minecraft-Radiance/Radiance/tree/release-0.1.6-1.21.4-neoforge) | 21 |
| **26.x (port in progress)** | — | — | — | — |

## Installation

1. Install the matching Minecraft version and mod loader. Fabric builds also require [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the Radiance JAR for your Minecraft version, loader, and operating system, and place it in your instance's `mods` directory.
3. **Forge and NeoForge must launch directly on the high-performance GPU.**
4. On Windows, install the [Microsoft Visual C++ v14 x64 Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist).
5. To use DLSS features, install the optional NVIDIA runtime libraries described below. Otherwise, use an available NRD + FSR pipeline.

### Windows fix: JDK runtime libraries

Some JDK distributions bundle older MSVC runtime DLLs in their `bin` directory. Windows may load these instead of the newer system libraries, even after installing the latest Visual C++ Redistributable. This mismatch can crash native code at `std::mutex::lock`, as documented in this [MSVC runtime compatibility issue](https://github.com/microsoft/STL/issues/4730).

If this issue causes a startup crash:

1. Close the game and launcher, then open the `bin` directory of the Java installation used by your launcher (`<JDK>/bin`).
2. Rename `msvcp140.dll`, `vcruntime140.dll`, and `vcruntime140_1.dll` by adding `.bak`, or move them to a backup directory. This prevents Java from loading those bundled copies.
3. Install the [latest Microsoft Visual C++ v14 x64 Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist), then restart the launcher and game so Java can use the updated system runtime libraries.

### Optional DLSS runtime libraries

Paths below are relative to the **game instance directory**, which may differ from the launcher's main `.minecraft` directory.

Radiance does not bundle or automatically download NVIDIA DLSS runtime binaries. Obtain them from [NVIDIA's official DLSS repository](https://github.com/NVIDIA/DLSS), follow its license terms, and put the release libraries in `<instance>/radiance/`. Create that directory if necessary.

| Feature | Windows | Linux |
| --- | --- | --- |
| Super Resolution | `nvngx_dlss.dll` | `libnvidia-ngx-dlss.so.<version>` |
| Ray Reconstruction | `nvngx_dlssd.dll` | `libnvidia-ngx-dlssd.so.<version>` |
| Frame Generation | `nvngx_dlssg.dll` | `libnvidia-ngx-dlssg.so.<version>` |

Official download directories: [Windows](https://github.com/NVIDIA/DLSS/tree/v310.9.1/lib/Windows_x86_64/rel) · [Linux](https://github.com/NVIDIA/DLSS/tree/v310.9.1/lib/Linux_x86_64/rel). Keep the original filenames and use libraries from the same release. For these links, the Linux version suffix is `310.9.1`.

DLSS requires compatible NVIDIA hardware. Super Resolution, Ray Reconstruction, and Frame Generation are detected separately; support for one does not imply support for all three. Missing or unsupported DLSS features are unavailable in the pipeline selection. Empty placeholder DLL or SO files are not needed.

## Mod compatibility

The table below lists mods with dedicated rendering integrations; other mods may also be compatible without special integration. For compatibility requests or contributions, please open an [issue](https://github.com/Minecraft-Radiance/Radiance/issues) or [pull request](https://github.com/Minecraft-Radiance/Radiance/pulls).

| Minecraft / loader | Distant Horizons | Flywheel | Sable |
| --- | --- | --- | --- |
| 1.20.1 Fabric | 3.3.0 | 1.0.1–1.0.6 | — |
| 1.20.1 Forge / NeoForge | 3.3.0 | 1.0.1–1.0.6-281 | — |
| 1.21.1 Fabric | 3.3.1 or 3.3.2 | 1.0.1–1.0.6 | 2.0.5 |
| 1.21.1 NeoForge | 3.3.1 or 3.3.2 | 1.0.1–1.0.6-44 | 2.0.5 |
| 1.21.4 Fabric / NeoForge | 3.3.1 | — | — |
| 1.21.1 / 1.21.4 Forge | — | — | — |

## FAQ

### How do I clear the cache?

If you run into problems, try closing the game, clearing the contents of the `radiance` folder in your game instance, and restarting the game. You can keep the DLSS runtime libraries if you want to use DLSS.

### Why must Forge / NeoForge start on the high-performance GPU?

Forge and NeoForge create an OpenGL context for their early loading window before Radiance initializes. Radiance's Vulkan renderer must use the same GPU, so the game must start on the high-performance GPU.

### What do GPU matching or OpenGL extension errors mean?

Errors mentioning `GL_EXT_memory_object`, `GL_EXT_semaphore`, or a Vulkan device that does not match the OpenGL UI context indicate a missing interoperability feature or an unsuitable GPU selection. Check which GPU Java is using, update the graphics driver, and restart the game.

### Why is a DLSS or frame generation option unavailable?

The mode needs compatible hardware, a suitable driver, and its required runtime. Try updating your graphics driver if the mode is unavailable. DLSS frame generation has separate requirements from DLSS reconstruction. AMD and Intel users should choose an available NRD + FSR pipeline. FSR frame generation is also subject to the capabilities detected by Radiance. Available modes and multipliers are shown in the settings.

### Do I need a PBR resource pack?

Radiance includes emissive texture data, and a PBR pack can add richer material detail. Since 0.1.4, resource packs load normally without preprocessing. Use textures with the appropriate PBR channels for material and parallax effects.

## Build

Choose the source branch for your Minecraft version and loader from the table above. Use the corresponding Java toolchain and the **0.1.6 MCVR source**.

From the Java repository, generate JNI headers:

```sh
./gradlew compileJava -PradianceUiBackend=opengl
```

Then build and install MCVR into that Java checkout, following the [native build instructions](https://github.com/Minecraft-Radiance/MCVR#build), with `-DMCVR_UI_BACKEND=OPENGL` and `JAVA_PROJECT_ROOT_DIR` pointing to the checkout's absolute path. Return to the Java repository and package the mod:

```sh
./gradlew build -PradianceUiBackend=opengl
```

On Windows, use `gradlew.bat`. JARs are written to `build/libs/`; select the normal mod JAR for distribution. To build the alternative Vulkan UI variant, use `-PradianceUiBackend=vulkan` for both Gradle commands and `-DMCVR_UI_BACKEND=VULKAN` for CMake, with a separate native build directory. Build each Minecraft/loader combination against its own generated JNI headers.

## Credits


This project uses Vulkan technology. Please refer to [this page](https://www.vulkan.org/) for more information.

This project uses Nvidia's DLSS (Deep Learning Super Sampling) technology. Please refer to [this page](https://www.nvidia.com/en-us/geforce/technologies/dlss/) and [this page](https://github.com/NVIDIA/DLSS) for more information. 

This project uses FSR3. Please refer to [this page](https://gpuopen.com/fidelityfx-super-resolution-3/) for more information.

This project uses XeSS SR. Please refer to [this page](https://www.intel.com/content/www/us/en/developer/articles/technical/xess-sr-developer-guide.html) for more information.

Special thanks to all contributors of open-source libraries used in this project, including [NRD](https://github.com/NVIDIA-RTX/NRD), [GLFW](https://github.com/glfw/glfw), [GLM](https://github.com/icaven/glm), [STB Image](https://github.com/nothings/stb) and [VMA](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator). If any are not credited and should be, please inform the author and credit will be applied where required.

## Disclaimer

* This is a community-made third-party mod and is **not affiliated with, authorized, sponsored, endorsed by, or otherwise officially connected to Mojang Studios or Microsoft**. A commonly used wording on mod platforms is: **"NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."**
* **Minecraft** and related names, logos, and assets are trademarks and/or intellectual property of Mojang Studios and/or Microsoft.
* This project is also **not affiliated with, sponsored by, or endorsed by NVIDIA**. **NVIDIA / GeForce / RTX / DLSS** are trademarks of NVIDIA Corporation and remain the property of their respective owners.
* This mod is provided **"AS IS"** without warranties. You assume all risks arising from its use (including, but not limited to, crashes, visual issues, data loss, or incompatibilities with other mods). Please back up your worlds and configs before installation.
