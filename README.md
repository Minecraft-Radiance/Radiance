| English | [简体中文](https://github.com/Minecraft-Radiance/Radiance/blob/main/README-CN.md) |

> This is the Java part of the Radiance mod. For C++ part, please refer to [Minecraft Vulkan Renderer (MCVR)](https://github.com/Minecraft-Radiance/MCVR)

# Radiance

[Radiance](https://www.minecraft-radiance.com/) is a Minecraft mod that completely replace the vanilla OpenGL renderer with our Vulkan C++ renderer, which supports high performance rendering and hardware-accelerated ray tracing.
Due to the variety of C++ usage in the modern industrial rendering pipeline, a seamless integration of a modern industrial rendering module (such as DLSS and FSR) into our Vulkan C++ renderer is thus possible.

[Showcase Video (Youtube)](https://www.youtube.com/watch?v=jGIQffPM1Wg)

<img width="2560" height="1440" alt="" src="https://image.puxuan.cc/PicGo/2026-02-09_00.45.30.png" />

# (Important) PBR Texture Packs

Currently, to enable PBR texture packs, small adaption is necessary to allow miltiplexing. This maybe simpilfied in further versions.

1. Unzip the texture pack
2. Go into the unziped folder, move `assets/minecraft/textures/**/*_s.png` to `assets/minecraft/textures/specular/**/*_s.png` and `assets/minecraft/textures/**/*_n.png` to `assets/minecraft/textures/normal/**/*_n.png`. The fact behind is that Radiance will create seperate texture atlases for specular and normal textures. This disables vanilla minecraft to read them (4x memory save).
3. Zip the folder

A sample `python` script can be found [here](https://gist.github.com/Ljiong201108/e5a2fe8f8ac63f3cc70bf5557a948377).

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

- [ ] Frame Generation

- [ ] XESS support

- [ ] HDR

And more...

# Credits

This project uses Vulkan technology. Please refer to [this page](https://www.vulkan.org/) for more information.

This project uses Nvidia's DLSS (Deep Learning Super Sampling) technology. Please refer to [this page](https://www.nvidia.com/en-us/geforce/technologies/dlss/) and [this page](https://github.com/NVIDIA/DLSS) for more information. 

This project uses FSR3. Please refer to [this page](https://gpuopen.com/fidelityfx-super-resolution-3/) for more information.

Special thanks to all contributors of open-source libraries used in this project, including [NRD](https://github.com/NVIDIA-RTX/NRD), [GLFW](https://github.com/glfw/glfw), [GLM](https://github.com/icaven/glm), [STB Image](https://github.com/nothings/stb) and [VMA](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator). If any are not credited and should be, please inform the author and credit will be applied where required.

# Disclaimer

* This is a community-made third-party mod and is **not affiliated with, authorized, sponsored, endorsed by, or otherwise officially connected to Mojang Studios or Microsoft**. A commonly used wording on mod platforms is: **"NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."**
* **Minecraft** and related names, logos, and assets are trademarks and/or intellectual property of Mojang Studios and/or Microsoft.
* This project is also **not affiliated with, sponsored by, or endorsed by NVIDIA**. **NVIDIA / GeForce / RTX / DLSS** are trademarks of NVIDIA Corporation and remain the property of their respective owners.
* This mod is provided **"AS IS"** without warranties. You assume all risks arising from its use (including, but not limited to, crashes, visual issues, data loss, or incompatibilities with other mods). Please back up your worlds and configs before installation.
