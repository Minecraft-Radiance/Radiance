| English | [简体中文](https://github.com/Minecraft-Radiance/Radiance/blob/main/README-CN.md) |

We are currently working on code clean up / documentation and solving compatibility issues. Source codes will be available here after the first alpha version.

# Radiance

[Radiance](https://www.minecraft-radiance.com/) is a Minecraft mod that completely replace the vanilla OpenGL renderer with our Vulkan C++ renderer, which supports high performance rendering and hardware-accelerated ray tracing.
Due to the variety of C++ usage in the modern industrial rendering pipeline, a seamless integration of a modern industrial rendering module (such as DLSS and FSR) into our Vulkan C++ renderer is thus possible.

<img width="2560" height="1440" alt="" src="https://github.com/user-attachments/assets/97f50f4a-3a6e-424d-9dff-d5e6b220f91f" />

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

Currently, if the DLSS runtime libraries are not found, the mod will **cause a crash**. After the first alpha version, DLSS will be disabled and fall back to alternative implementations (we will possibly support FSR 3 or custom Denoising modules at that time).

### Download Instructions

#### Windows

Download the files listed below from [here](https://github.com/NVIDIA/DLSS/tree/v310.3.0/lib/Windows_x86_64/rel) to the `.minecraft/radiance` folder (if the folder not exist, create one).

* `nvngx_dlss.dll`
* `nvngx_dlssd.dll`

#### Linux

Download the files listed below from [here](https://github.com/NVIDIA/DLSS/tree/main/lib/Windows_x86_64/rel) to the `.minecraft/radiance` folder (if the folder not exist, create one).

* `libnvidia-ngx-dlss.so.310.3.0`
* `libnvidia-ngx-dlssd.so.310.3.0`

# Credits

This project uses Vulkan technology. Please refer to [this page](https://www.vulkan.org/) for more information.

This project also uses Nvidia's DLSS (Deep Learning Super Sampling) technology. Please refer to [this page](https://www.nvidia.com/en-us/geforce/technologies/dlss/) and [this page](https://github.com/NVIDIA/DLSS) for more information. 

Special thanks to all contributors of open-source libraries used in this project, including [GLFW](https://github.com/glfw/glfw), [GLM](https://github.com/icaven/glm), [STB Image](https://github.com/nothings/stb) and [VMA](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator). If any are not credited and should be, please inform the author and credit will be applied where required.

# Disclaimer

* This is a community-made third-party mod and is **not affiliated with, authorized, sponsored, endorsed by, or otherwise officially connected to Mojang Studios or Microsoft**. A commonly used wording on mod platforms is: **"NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."**
* **Minecraft** and related names, logos, and assets are trademarks and/or intellectual property of Mojang Studios and/or Microsoft.
* This project is also **not affiliated with, sponsored by, or endorsed by NVIDIA**. **NVIDIA / GeForce / RTX / DLSS** are trademarks of NVIDIA Corporation and remain the property of their respective owners.
* This mod is provided **"AS IS"** without warranties. You assume all risks arising from its use (including, but not limited to, crashes, visual issues, data loss, or incompatibilities with other mods). Please back up your worlds and configs before installation.
