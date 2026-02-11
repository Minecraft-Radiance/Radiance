| [English](https://github.com/Minecraft-Radiance/Radiance/blob/main/README.md) | 简体中文 |

> 这里是Radiance mod的Java部分。C++部分请访问[Minecraft Vulkan Renderer (MCVR)](https://github.com/Minecraft-Radiance/MCVR)

# Radiance

Radiance是一个Minecraft Mod，旨在将原版的OpenGL渲染器完全替换成我们的高性能Vulkan C++渲染器，并且支持硬件加速光线追踪。
由于现代工业界广泛在渲染管线中使用C++，所以我们的Vulkan C++渲染器能够将一个现代工业级的渲染模块（例如DLSS和FSR）无缝集成进来。

[演示视频 (B站)](https://www.bilibili.com/video/BV1NevXBCEPg/)

<img width="2560" height="1440" alt="" src="https://image.puxuan.cc/PicGo/2026-02-09_00.45.30.png" />

# (重要) 关于PBR材质包

目前，Radiance需要一点小的文件结构修改，才能使用PBR材质包。这可能会在接下来的版本里面被简化掉。

1. 解压材质包
2. 进入解压出来的材质包，将`assets/minecraft/textures/**/*_s.png`移动到`assets/minecraft/textures/specular/**/*_s.png`，以及`assets/minecraft/textures/**/*_n.png`移动到`assets/minecraft/textures/normal/**/*_n.png`。这背后的原因是Radiance会给specular和normal贴图单独创建内置的贴图纹理，这个移动让原版不自己加载这些贴图，从而省下巨量内存。
3. 将处理好的文件夹压缩。

一个样例`python`脚本可以用作这个处理过程的[参考](https://gist.github.com/Ljiong201108/e5a2fe8f8ac63f3cc70bf5557a948377)。

# 安装指南

我们默认Minecraft本体安装在`.minecraft`文件夹下。如若安装路径不同，请自行替代。

## 正常下载和安装模组`.jar`文件

正常下载和安装模组 jar 文件到`.minecraft/mods`文件夹。

## (Windows修复) 调整JDK的运行库

因为一个已知的[MSVC问题](https://stackoverflow.com/questions/78598141/first-stdmutexlock-crashes-in-application-built-with-latest-visual-studio)，有些JDK自带的运行库会导致模组报错，游戏崩溃。

这种情况下，一种可能的解决方案为：

首先，尝试改动（重命名，删除，等）JDK bin目录（`${PATH_TO_JDK}/bin`）下的`msvcp140.dll`，`vcruntime140.dll`和`vcruntime140_1.dll`文件。最终要让这些文件不存在在JDK bin目录下。这一步的目的是移除JDK对于它自带的运行库的依赖。

然后，安装[最新的Microsoft Visual C++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist?view=msvc-170)，使用`Latest supported v14 (for Visual Studio 2017–2026)`版本。这一步的目的是让JDK重新使用最新的系统运行库。

## 下载和安装DLSS运行库

为尊重并遵守 NVIDIA DLSS SDK 的许可条款，本项目仓库与发行物（Release）不包含、也不提供任何 NVIDIA DLSS 的二进制文件或 SDK 组件（例如 nvngx_dlss.dll），亦不会在运行时为你自动下载这些文件。
若你希望启用本模组中的 DLSS 去噪 & 超分 模块，请你自行按照**下文的下载步骤**从 NVIDIA 官方渠道获取对应版本的 DLSS 运行库，并将其放置到`.minecraft/radiance`文件夹。

下载、安装或使用 NVIDIA DLSS 运行库（例如 nvngx_dlss.dll）即表示你已阅读并同意遵守 NVIDIA DLSS/RTX SDK 的许可协议（License Agreement）。若你不同意该许可协议，请勿下载、安装或使用该运行库，并请改用本模组提供的其他替代方案（第一个alpha版本后）。

目前，如果未检测到 DLSS 运行库，本模组会使**游戏崩溃**。第一个alpha版本后，如果检测不到 DLSS 运行库，DLSS 功能会被自动禁用并回退到替代方案（我们到时候应该能够支持 FSR 3 以及自定义 Denoiser 模块）。

### 下载步骤

针对 AMD 用户的提示： 若遇到 “DLSS runtime libraries not found” 错误，可以选择下载 DLSS 运行库，或者直接在相应目录下创建对应的同名空白文件 来绕过启动检查。这一强制依赖将在下个版本中移除。

#### Windows

从[这个](https://github.com/NVIDIA/DLSS/tree/v310.5.3/lib/Windows_x86_64/rel)路径中下载如下列表中的文件到`.minecraft/radiance`文件夹（如果文件夹不存在，请创建一个）。

* `nvngx_dlss.dll`
* `nvngx_dlssd.dll`

#### Linux

从[这个](https://github.com/NVIDIA/DLSS/tree/v310.5.3/lib/Linux_x86_64/rel)路径中下载如下列表中的文件到`.minecraft/radiance`文件夹（如果文件夹不存在，请创建一个）。

* `libnvidia-ngx-dlss.so.310.5.3`
* `libnvidia-ngx-dlssd.so.310.5.3`

# 构建

首先，用`gradle`编译Java来生成JNI头文件。

```
./gradlew compileJava
```

然后，克隆[Minecraft Vulkan Renderer (MCVR)](https://github.com/Minecraft-Radiance/MCVR)仓库。

```
git clone https://github.com/Minecraft-Radiance/MCVR.git
```

使用`cmake`编译和安装。详细信息请参考[这里](https://github.com/Minecraft-Radiance/MCVR)。

最后，用`./gradlew build`构建。

# Todo列表

- [ ] 移植到更多版本和mod加载器（WIP，最高优先级)

- [ ] 帧生成

- [ ] XESS支持

- [ ] HDR

以及更多...

# 致谢

这个项目使用了 Vulkan。获取更多信息请访问[这个页面](https://www.vulkan.org/)。

这个项目同时也使用了 Nvidia 的 DLSS (Deep Learning Super Sampling) 技术。获取更多信息请访问[这个](https://www.nvidia.com/en-us/geforce/technologies/dlss/)和[这个页面](https://github.com/NVIDIA/DLSS)。

这个项目也使用了FSR3。更多信息请访问[这个页面](https://gpuopen.com/fidelityfx-super-resolution-3/)。

特别感谢所有这个项目使用的开源库的制作者，包括[NRD](https://github.com/NVIDIA-RTX/NRD)、[GLFW](https://github.com/glfw/glfw)、[GLM](https://github.com/icaven/glm)、[STB Image](https://github.com/nothings/stb)和[VMA](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator)。
如果有应该致谢但是没有被提及的，请通知本项目作者。我们会把致谢添加在需要添加的位置。

# 免责声明

* 本项目为社区开发的第三方模组（Mod），**不隶属于、亦未获得 Mojang Studios / Microsoft 的授权附属、赞助或支持**。参考常见模组分发平台的表述：“**NOT AN OFFICIAL MINECRAFT SERVICE. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**”
* **Minecraft** 以及相关名称、标识与资源为 Mojang Studios / Microsoft 的商标或知识产权。
* 本项目同样**不隶属于、亦未获得 NVIDIA 的授权附属、赞助或支持**；**NVIDIA / GeForce / RTX / DLSS** 等名称与标识为 NVIDIA Corporation 的商标，归其各自权利人所有。
* 本项目按“**AS IS**”提供。使用本模组产生的任何风险（包括但不限于崩溃、画面问题、数据丢失、与其他模组冲突等）由使用者自行承担；请在安装前备份存档与配置。

