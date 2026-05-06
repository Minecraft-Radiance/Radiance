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

| English | [简体中文](https://github.com/Minecraft-Radiance/Radiance/blob/main/README-CN.md) |

> This is the Java part of the Radiance mod. For C++ part, please refer to [Minecraft Vulkan Renderer (MCVR)](https://github.com/Minecraft-Radiance/MCVR)

# Radiance

[Radiance](https://www.minecraft-radiance.com/) is a Minecraft mod that completely replace the vanilla OpenGL renderer with our Vulkan C++ renderer, which supports high performance rendering and hardware-accelerated ray tracing.
Due to the variety of C++ usage in the modern industrial rendering pipeline, a seamless integration of a modern industrial rendering module (such as DLSS and FSR) into our Vulkan C++ renderer is thus possible.

[Showcase Video (Youtube)](https://www.youtube.com/watch?v=jGIQffPM1Wg)

<img src="https://image.puxuan.cc/PicGo/corridor.png"/>

# PBR Texture Packs

Starting from 0.1.4, no pre-processing  is necessary. Load directly!

Although there are already internal emission textures, it is still recommended to use a PBR texture pack for better experience.

# Installation Guide

We assume that the Minecraft base is installed in `.minecraft` folder. If the installation folder is different, please replace the corresponding part yourself.

## Download and install `.jar` as usual

Download and install the mod jar to the `.minecraft/mods` folder as usual.

## (Windows Fix) Adjust JDK's runtime libraries

Due to a known [MSVC issue](https://stackoverflow.com/questions/78598141/first-stdmutexlock-crashes-in-application-built-with-latest-visual-studio), some libraries from JDK itself may cause a crash. 

In those circumstances, one possible solution could be:

First, try to manipulate (rename, delete, etc.) the `msvcp140.dll`, `vcruntime140.dll` and `vcruntime140_1.dll` in the JDK's bin folder (`${PATH_TO_JDK}/bin`) so that these files do not exist in that folder. 
This step aims to remove the JDK's dependency on those libraries.

Then, install the [latest Microsoft Visual C++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist?view=msvc-170), with version `Latest supported v14 (for Visual Studio 2017–2026)`.
This step let JDK depends on the latest system libraries.

## Download and install DLSS runtime libraries

To respect and comply with NVIDIA DLSS SDK licensing terms, this project and its releases do not include or redistribute any NVIDIA DLSS binaries or SDK components (e.g., nvngx_dlss.dll), and we do not auto-download them at runtime.
If you want to enable the DLSS denoising / upscaling module in this mod, you must follow the **download instructions below** and obtain the appropriate DLSS runtime library yourself from official NVIDIA DLSS repository and place it at `.minecraft/radiance` folder. 

By downloading, installing, or using the NVIDIA DLSS runtime library (e.g., nvngx_dlss.dll), you acknowledge that you have read and agree to comply with the NVIDIA DLSS SDK License Agreement. 
If you do not agree to the License Agreement, do not download, install, or use the runtime libraries, and use alternative options provided by this mod instead (after the first alpha version).

Currently, if the DLSS runtime libraries are not found, the mod will **cause a crash**. After the first alpha version, DLSS will be disabled and fall back to alternative implementations.

### Download Instructions

Note for AMD Users: If the DLSS runtime is missing, download the DLSS runtime libraries or create a dummy empty file with the same filename to bypass the startup check. This will be fixed in the next update.

#### Windows

Download the files listed below from [here](https://github.com/NVIDIA/DLSS/tree/v310.5.3/lib/Windows_x86_64/rel) to the `.minecraft/radiance` folder (if the folder not exist, create one).

* `nvngx_dlss.dll`
* `nvngx_dlssd.dll`

#### Linux

Download the files listed below from [here](https://github.com/NVIDIA/DLSS/tree/v310.5.3/lib/Linux_x86_64/rel) to the `.minecraft/radiance` folder (if the folder not exist, create one).

* `libnvidia-ngx-dlss.so.310.5.3`
* `libnvidia-ngx-dlssd.so.310.5.3`

##### Additional instructions for NixOS

Because of how NixOS works, **the mod, won't work by default** on NixOS.
The following instructions will show you how to tweak NixOS to expose the additional libs required by Radiance  to your launcher (**PrismLauncher will serve as an example here**):

1. Edit your `configuration.nix` file or your `Nix flake` (depending on what you use - if you don't know what a `flake` is, then you should be using `configuration.nix`).

2. Locate the part of your configuration that contains installed packages (by default, it is `environment.systemPackages`, but if you use Home-Manager it is `home-manager.users.<username>.home.packages`).
3. In said part, you should see `prismlauncher` (or `pkgs.prismlauncher`)
4. If the name was only `prismlauncher`, replace it with the following code block:
```nix
(prismlauncher.override {
  additionalLibs = [
    pkgs.bzip2
    pkgs.xz
    pkgs.openssl
  ];
})
```
*Note: If the name was `pkgs.prismlauncher`, just append `pkgs.` before `prismlauncher.override` (e.g: `pkgs.prismlauncher.override { <restOfTheCodeHere> }`)*

5. After this, run `nixos-rebuild switch` as root, close and re-open PrismLauncher, and you should be good to go.

# Build

First, compile Java with `gradle` to generate the JNI native headers.

```
./gradlew compileJava
```

Then, clone the [Minecraft Vulkan Renderer (MCVR)](https://github.com/Minecraft-Radiance/MCVR) repository.

```
git clone https://github.com/Minecraft-Radiance/MCVR.git
```

Use `cmake` to build it and install it. Please refer to [this](https://github.com/Minecraft-Radiance/MCVR) for detail.

Finally, build with `./gradlew build`.

# TODO List

- [ ] port to more versions and mod loaders (WIP, first priority)
- [x] XESS support
- [ ] Frame Generation
- [ ] HDR

And more...

# Credits

This project uses Vulkan technology. Please refer to [this page](https://www.vulkan.org/) for more information.

This project uses Nvidia's DLSS (Deep Learning Super Sampling) technology. Please refer to [this page](https://www.nvidia.com/en-us/geforce/technologies/dlss/) and [this page](https://github.com/NVIDIA/DLSS) for more information. 

This project uses FSR3. Please refer to [this page](https://gpuopen.com/fidelityfx-super-resolution-3/) for more information.

This project uses XeSS SR. Please refer to [this page](https://www.intel.com/content/www/us/en/developer/articles/technical/xess-sr-developer-guide.html) for more information.

Special thanks to all contributors of open-source libraries used in this project, including [NRD](https://github.com/NVIDIA-RTX/NRD), [GLFW](https://github.com/glfw/glfw), [GLM](https://github.com/icaven/glm), [STB Image](https://github.com/nothings/stb) and [VMA](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator). If any are not credited and should be, please inform the author and credit will be applied where required.

# Disclaimer

* This is a community-made third-party mod and is **not affiliated with, authorized, sponsored, endorsed by, or otherwise officially connected to Mojang Studios or Microsoft**. A commonly used wording on mod platforms is: **"NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."**
* **Minecraft** and related names, logos, and assets are trademarks and/or intellectual property of Mojang Studios and/or Microsoft.
* This project is also **not affiliated with, sponsored by, or endorsed by NVIDIA**. **NVIDIA / GeForce / RTX / DLSS** are trademarks of NVIDIA Corporation and remain the property of their respective owners.
* This mod is provided **"AS IS"** without warranties. You assume all risks arising from its use (including, but not limited to, crashes, visual issues, data loss, or incompatibilities with other mods). Please back up your worlds and configs before installation.
