# Blaze3D 26.2 Java 后端接入状态 - Step 4

## 本步骤目标

把 26.2 的 Blaze3D backend 从旧 proxy / 旁路模型切到 Radiance 自己的
`GpuBackend` 实现：

```text
PreferredGraphicsApi
  -> RadianceBackend
  -> RadianceGpuDeviceBackend
  -> RadianceCommandEncoderBackend / RadianceRenderPassBackend
  -> com.radiance.client.blaze3d.abi.NativeBridge JNI
```

这一步不是委托 OpenGL/Vulkan 后端渲染。OpenGL/Vulkan 只保留为 Radiance 创建失败时
Minecraft 后续候选 backend 的 fallback。

## 已实现

- 新增 26.2 专用 `RadianceBackend implements GpuBackend`。
- 新增 26.2 专用 `RadianceGpuDeviceBackend implements GpuDeviceBackend`。
- 新增 26.2 专用 `RadianceGpuSurfaceBackend implements GpuSurfaceBackend`。
- 新增 26.2 专用 `RadianceCommandEncoderBackend implements CommandEncoderBackend`。
- 新增 26.2 专用 `RadianceRenderPassBackend implements RenderPassBackend`。
- 新增 Radiance Blaze3D 资源对象：
  - `RadianceGpuBuffer`
  - `RadianceGpuTexture`
  - `RadianceGpuTextureView`
  - `RadianceGpuSampler`
  - `RadianceGpuFence`
  - `RadianceGpuQueryPool`
  - `RadianceCompiledRenderPipeline`
  - `RadianceTransientMemory`

## ABI 行为

buffer 资源路径：

```text
createBuffer(size)
  -> NativeBridge.createBufferNative
  -> Java shadow 分配

createBuffer(data)
  -> NativeBridge.createBufferWithDataNative
  -> Java shadow 保存同一份 direct payload

writeToBuffer(slice, data)
  -> 更新 Java shadow
  -> NativeBridge.writeBufferNative

copyToBuffer(source, target)
  -> 更新 Java shadow
  -> NativeBridge.copyBufferNative

map(write).close()
  -> NativeBridge.writeBufferNative
```

draw 路径：

```text
setPipeline
  -> registerPipelineNative / setPipelineNative

bindTexture
  -> bindTextureNative(name, viewHandle, samplerHandle)

setUniform
  -> setUniformNative(name, bufferHandle, offset, length)

setVertexBuffer / setIndexBuffer
  -> setVertexBufferNative / setIndexBufferNative

drawIndexed
  -> drawIndexedNative

drawMultipleIndexed
  -> 对每个 RenderPass.Draw 单独执行 dynamic uniform uploader
  -> setIndexBuffer / setVertexBuffer / drawIndexed
```

unsupported/custom first-version 策略：

```text
non-indexed draw / texture pixel copy / post-like operations
  -> no-op，不抛异常
```

这些操作不会进入 scene。后续 native diagnostics 阶段需要记录 skipped reason。

## Pipeline 映射

`PrimitiveTopology` 不使用 Java enum ordinal，按 native ABI 显式映射：

```text
POINTS -> 0
LINES -> 1
DEBUG_LINES -> 2
DEBUG_LINE_STRIP -> 3
TRIANGLES -> 4
TRIANGLE_STRIP -> 5
TRIANGLE_FAN -> 6
QUADS -> 7
```

当前 JNI `registerPipelineNative` 只接收一个 vertex buffer layout，所以 Java 侧选取
第一个非空 `RenderPipeline.getVertexFormatBindings()` slot 注册。terrain 第一版通常在
slot 0，可进入后端；多 vertex layout pipeline 仍需下一步扩展 native ABI。

## Mixin / resource 组织

26.2 不再使用旧 `radiance.mixins.json` 和旧 `radiance.accesswidener`。

新增：

```text
versions/26.2/src/main/resources/radiance-26.mixins.json
versions/26.2/src/main/resources/fabric.mod.json
versions/26.2/src/main/java/com/radiance/mixins/blaze3d_26/PreferredGraphicsApiMixin.java
```

`PreferredGraphicsApiMixin` 在 `getBackendsToTry()` 返回值前插入
`new RadianceBackend()`。Radiance 创建失败时，原 OpenGL/Vulkan backend 仍保留为候选。

## 验证

已执行：

```powershell
.\gradlew.bat :26.2:compileJava
.\gradlew.bat :26.2:classes
```

结果：

```text
BUILD SUCCESSFUL
```

资源输出确认：

```text
build/resources/main/fabric.mod.json -> mixins = ["radiance-26.mixins.json"]
build/resources/main/radiance-26.mixins.json exists
build/resources/main/radiance.mixins.json absent
build/resources/main/radiance.accesswidener absent
```

## 当前剩余缺口

这一步完成的是 Java Blaze3D backend 到 native ABI 的接线，不等于完整最终目标。
后续还必须继续完成：

- native `Blaze3DSceneProvider` 接到 Radiance renderer runtime。
- frame/camera/world/lightmap semantic observer。
- texture write / atlas sprite / PBR material ABI。
- draw diagnostics 和 skipped reason。
- Radiance present 阶段确认使用 Blaze3D scene snapshot 渲染，而不是旧 world/chunk proxy。

