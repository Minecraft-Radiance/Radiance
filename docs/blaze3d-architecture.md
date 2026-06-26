# Blaze3D 架构与 Deferred 渲染模块兼容性设计

> **状态**: 草案  
> **上下文**: MCVR (Minecraft Vulkan Renderer) 与 Radiance (Fabric Mod) 项目  
> **源码参考**: `G:\cpp\radiance\mcsrc` — Minecraft 1.21.x NeoForge 反编译源码  

---

## 1. 项目背景

### 1.1 两个项目的关系

| 项目 | 语言 | 角色 |
|------|------|------|
| **Radiance** | Java (Fabric Mod) | 管线配置、GUI、模块连接 |
| **MCVR** | C++23 (Vulkan) | 原生 Vulkan 渲染后端 |

目前架构: **Java → JNI → C++ Vulkan 渲染**, 通过 Mixin 拦截 Minecraft 渲染调用, 完全绕过 Blaze3D。

### 1.2 Blaze3D 是什么

Blaze3D 是 Minecraft 1.21+ 的官方渲染器抽象层, 替代了旧的直接 OpenGL 调用。Mojang 将其设计为**社区扩展接口** —— mod 不应绕过 Blaze3D, 而应在 Blaze3D 提供的接口之上构建。

Blaze3D 有两个已知后端:
- **OpenGL** (`GlDevice` / `GlRenderPass` / `GlCommandEncoder`) — 完整源码可见
- **Vulkan** (推测) — 闭源, 不在 mcsrc 中

---

## 2. Blaze3D 架构全览

### 2.1 包结构

```
com.mojang.blaze3d/
├── systems/
│   ├── GpuDevice.java          # 设备抽象 (interface)
│   ├── CommandEncoder.java     # 命令录制 (interface)
│   ├── RenderPass.java         # 渲染 pass (interface)
│   ├── RenderSystem.java       # 全局调度器 + 线程管理
│   └── GpuQuery.java           # GPU 查询
│
├── framegraph/
│   ├── FrameGraphBuilder.java  # Render Graph 构建器
│   └── FramePass.java          # 单个 pass 的接口
│
├── pipeline/
│   ├── RenderPipeline.java     # PSO 描述符 (Builder 模式)
│   ├── RenderTarget.java       # 渲染目标
│   ├── MainTarget.java         # 主帧缓冲
│   ├── BlendFunction.java      # 混合函数
│   └── CompiledRenderPipeline.java # 编译后的 pipeline
│
├── buffers/
│   ├── GpuBuffer.java          # GPU Buffer 抽象
│   └── GpuBufferSlice.java     # Buffer 切片
│
├── textures/
│   ├── GpuTexture.java         # GPU 纹理抽象
│   ├── GpuTextureView.java     # 纹理视图
│   ├── GpuSampler.java         # 采样器
│   ├── TextureFormat.java      # 纹理格式枚举
│   └── FilterMode.java         # 滤波模式
│
├── vertex/
│   ├── VertexFormat.java       # 顶点格式
│   ├── VertexFormatElement.java # 顶点元素 (POSITION, COLOR, UV, NORMAL 等)
│   ├── DefaultVertexFormat.java # 预设格式 (BLOCK, NEW_ENTITY 等)
│   ├── MeshData.java           # Mesh 数据
│   └── ByteBufferBuilder.java  # 顶点缓冲区构建
│
├── resource/
│   ├── ResourceDescriptor.java   # 资源描述符
│   ├── ResourceHandle.java       # 资源句柄
│   └── GraphicsResourceAllocator.java # 资源分配器
│
├── shaders/
│   ├── ShaderSource.java       # 着色器源码
│   └── UniformType.java        # Uniform 类型
│
├── opengl/                     # OpenGL 参考实现
│   ├── GlDevice.java           # GpuDevice 的 GL 实现
│   ├── GlCommandEncoder.java   # CommandEncoder 的 GL 实现
│   ├── GlRenderPass.java       # RenderPass 的 GL 实现
│   ├── GlBuffer.java           # GpuBuffer 的 GL 实现
│   └── GlTexture.java          # GpuTexture 的 GL 实现
│
└── platform/                   # 平台抽象 (窗口、输入等)
```

### 2.2 核心接口关系

```
                    RenderSystem (全局单例)
                          │
                    GpuDevice (interface)
                    ┌─────┴─────┐
                GlDevice    [VkDevice]
                    │         (闭源)
                    │
              createCommandEncoder()
                    │
              CommandEncoder (interface)
              ┌─────────┴─────────┐
        GlCommandEncoder    [VkCommandEncoder]
              │
    createRenderPass() / clear*() / writeTo*() / copy*()
              │
              RenderPass (interface)
              ┌─────────┴─────────┐
          GlRenderPass      [VkRenderPass]
              │
  setPipeline() / setVertexBuffer() / drawIndexed() / close()
```

---

## 3. FrameGraph — 渲染流程

### 3.1 概念

`FrameGraphBuilder` 是一个声明式的 Render Graph:

```java
FrameGraphBuilder graph = new FrameGraphBuilder();

// 导入外部资源 (swapchain, depth)
ResourceHandle<GpuTextureView> swapchain = graph.importExternal("swapchain", swapchainView);

// 声明 Pass
FramePass gbuffer = graph.addPass("deferred_gbuffer");
ResourceHandle<GpuTextureView> albedo = gbuffer.createsInternal("albedo", descriptor);
gbuffer.reads(depthBuffer);
gbuffer.executes(() -> { /* 绘制 G-buffer */ });

FramePass lighting = graph.addPass("deferred_lighting");
lighting.reads(albedo);
lighting.readsAndWrites(swapchain); // 写入最终输出
lighting.executes(() -> { /* 延迟光照 */ });

// 执行 (自动拓扑排序、资源分配、同步)
graph.execute(allocator);
```

### 3.2 自动保证

| 保证 | 实现方式 |
|------|---------|
| **Pass 执行顺序** | `resolvePassOrder()` — 基于依赖的拓扑排序 |
| **死 Pass 剔除** | `identifyPassesToKeep()` — 从外部资源反向追踪可达 pass |
| **资源生命周期** | `assignResourceLifetimes()` — 自动 acquire/release |
| **循环检测** | 排序时检测, 抛出 `IllegalStateException("Frame graph cycle detected")` |

### 3.3 和 MCVR Pipeline 的对比

| MCVR | Blaze3D FrameGraph |
|------|-------------------|
| Java `Pipeline.java` 中硬编码 `connect()` | `reads()` / `readsAndWrites()` 声明依赖 |
| Java 拓扑排序 → 序列化 → JNI → C++ | FrameGraphBuilder 纯 Java 排序 |
| C++ `WorldPipeline::sharedImages_[]` 手动管理 | `ResourceHandle` 自动管理 |
| 手动 `VkImageMemoryBarrier` | 后端自动插入 (接口不暴露 barrier API) |

---

## 4. RenderPipeline — PSO 描述

### 4.1 结构

```java
RenderPipeline.builder()
    .withLocation("radiance/deferred/gbuffer")    // 唯一标识
    .withVertexShader("core/terrain")              // shader 标识 (Identifier)
    .withFragmentShader("radiance/deferred/gbuffer")
    .withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.TRIANGLES)
    .withDepthTestFunction(DepthTestFunction.LEQUAL)
    .withDepthWrite(true)
    .withCull(true)
    .withUniform("Camera", UniformType.UNIFORM_BUFFER)  // 声明所需 uniform
    .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
    .withSampler("Sampler0")                            // 声明所需采样器
    .withShaderDefine("DEFERRED_GBUFFER")               // 编译期宏
    .build();
```

### 4.2 状态覆盖范围

`RenderPipeline` 描述完整的 PSO 状态:

| 状态 | 字段 |
|------|------|
| 着色器 | `vertexShader`, `fragmentShader` |
| 顶点格式 | `vertexFormat`, `vertexFormatMode` |
| 深度 | `depthTestFunction`, `writeDepth`, `depthBias*` |
| 裁剪 | `cull` |
| 混合 | `blendFunction` (optional) |
| 颜色写入 | `writeColor`, `writeAlpha` |
| 逻辑操作 | `colorLogic` |
| 多边形模式 | `polygonMode` |
| Stencil | `stencilTest` (NeoForge 扩展) |

---

## 5. RenderPass — 绘制录制

### 5.1 接口

```java
public interface RenderPass extends AutoCloseable {
    void setPipeline(RenderPipeline pipeline);
    void bindTexture(String name, GpuTextureView view, GpuSampler sampler);
    void setUniform(String name, GpuBuffer buffer);
    void setUniform(String name, GpuBufferSlice bufferSlice);
    void setVertexBuffer(int index, GpuBuffer buffer);
    void setIndexBuffer(GpuBuffer indexBuffer, IndexType indexType);
    void drawIndexed(int firstIndex, int index, int indexCount, int primCount);
    void draw(int firstIndex, int indexCount);  // 非索引绘制
    <T> void drawMultipleIndexed(              // 批量绘制 (类似 indirect)
        Collection<Draw<T>> draws,
        GpuBuffer indexBuffer,
        IndexType indexType,
        Collection<String> uniformNames,
        T userData
    );
    void setViewport(int x, int y, int w, int h);
    void enableScissor(int x, int y, int w, int h);
    void disableScissor();
    void close();  // 结束录制, 必须调用
}
```

### 5.2 状态管理行为 (根据 GlRenderPass 验证)

| 行为 | 确定性 |
|------|--------|
| Pipeline 状态去重 | `applyPipelineState()` 用 `lastPipeline` 比较, 不重复设置 GL 状态 |
| Uniform/Sampler 惰性绑定 | `dirtyUniforms` 脏标记, 攒到 `drawIndexed()` 时 flush |
| `close()` 后不可用 | `this.closed` 字段, 后续操作抛异常 |
| 同一 Encoder 只能一个活跃 Pass | `inRenderPass` 标志 |

### 5.3 运行时验证规则

`GlRenderPass.VALIDATION` 模式 (IDE 中默认开启) 强制执行以下规则:

```
1. draw 前必须 setPipeline()
2. pipeline 编译失败不能 draw
3. pipeline 声明了 uniform 就必须 setUniform()
4. vertex buffer 必须有 USAGE_VERTEX 标志
5. index buffer 必须有 USAGE_INDEX 标志
6. 纹理必须有 USAGE_TEXTURE_BINDING 标志
7. buffer 不能已关闭 (isClosed())
8. UBO offset 必须对齐到 uniformOffsetAlignment
9. pipeline 声明了深度纹理需求但未提供 → 警告
```

---

## 6. 几何数据流 — Chunk 渲染

### 6.1 Blaze3D 的 Chunk 数据结构

```
SectionCompiler (编译线程)
    │ 方块 → 顶点
    ▼
MeshData (CPU 端中间数据)
    │ vertexBuffer (ByteBuffer)
    │ indexBuffer (ByteBuffer)
    │ drawState (format, vertexCount, indexCount, mode, indexType)
    ▼
CompiledSectionMesh (GPU 端)
    │ uploadMeshLayer() → GPU Buffer
    ▼
SectionBuffers (每个 ChunkSectionLayer)
    ├── GpuBuffer vertexBuffer    # VERTEX usage
    ├── GpuBuffer indexBuffer     # INDEX usage
    ├── int indexCount
    └── IndexType indexType
```

### 6.2 ChunkSectionLayer

```java
enum ChunkSectionLayer {
    SOLID,         // 不透明方块
    CUTOUT,        // 带 alpha test 的方块 (玻璃、树叶等)
    TRANSLUCENT,   // 半透明方块 (水、冰等)
    TRIPWIRE,      // 线
}
```

每个 layer 关联一个 `RenderPipeline`, 定义该层使用的着色器。

### 6.3 顶点格式

Minecraft 方块使用 `DefaultVertexFormat.BLOCK`:
```
Position  : FLOAT × 3  (12 bytes)  — 世界坐标
Color     : UBYTE × 4  (4 bytes)   — 顶点颜色
UV0       : FLOAT × 2  (8 bytes)   — 纹理坐标
UV2       : SHORT × 2  (4 bytes)   — 光照贴图坐标
Normal    : BYTE  × 3  (3 bytes)   — 法线
Padding   : 1 byte
Total     : 28 bytes
```

### 6.4 和 MCVR 的 PBRVertex 对比

| 字段 | Blaze3D BLOCK | MCVR PBRVertex |
|------|---------------|----------------|
| Position | FLOAT×3 | FLOAT×3 |
| Normal | BYTE×3 | FLOAT×3 |
| Color | UBYTE×4 | FLOAT×4 (colorLayer) |
| UV0 | FLOAT×2 | FLOAT×2 |
| UV2 | SHORT×2 | — |
| TextureID | — | UINT |
| Metallic/Roughness | — | FLOAT×2 |
| Emission | — | FLOAT |
| **总计** | **28 bytes** | **80 bytes** |

Blaze3D 的顶点格式更紧凑, 缺少数值 roughness/metallic/emission (这些在 MC 方块模型中不存在)。如果你需要在 G-buffer 中写入 PBR 材质参数, 有两种方案:

1. **扩展 `VertexFormatElement`** (通过 `findNextId()` 注册新元素)
2. **在 shader 中从纹理采样获取** PBR 参数 (推荐 — 和现有 MC 纹理系统兼容)

---

## 7. Deferred 渲染模块的 Blaze3D 兼容设计

### 7.1 设计原则

```
┌─────────────────────────────────────────────────┐
│  模块不绕过 Blaze3D, 而是作为 FrameGraph 中的    │
│  一系列自定义 Pass, 使用 Blaze3D 接口录制绘制    │
│                                                 │
│  光栅化: 纯 Blaze3D 接口 (零 native 代码)       │
│  光追:   通过 NeoForge GpuDeviceExtension 扩展   │
└─────────────────────────────────────────────────┘
```

### 7.2 G-buffer Pass 示例

```java
FrameGraphBuilder graph = new FrameGraphBuilder();

// 导入 Blaze3D 管理的资源和 Minecraft 的 uniform
ResourceHandle<GpuTextureView> mainColor = graph.importExternal("main_color", mainTarget.getColorTextureView());
ResourceHandle<GpuTextureView> mainDepth = graph.importExternal("main_depth", mainTarget.getDepthTextureView());
ResourceHandle<GpuBufferSlice> cameraUBO = graph.importExternal("camera", RenderSystem.getModelViewStack());

// G-buffer Pass
FramePass gbufferPass = graph.addPass("deferred_gbuffer");
ResourceHandle<GpuTextureView> albedoRT   = gbufferPass.createsInternal("g_albedo",   albedoDesc);
ResourceHandle<GpuTextureView> normalRT   = gbufferPass.createsInternal("g_normal",   normalDesc);
ResourceHandle<GpuTextureView> materialRT = gbufferPass.createsInternal("g_material", materialDesc);
ResourceHandle<GpuTextureView> depthRT    = gbufferPass.createsInternal("g_depth",    depthDesc);
gbufferPass.reads(mainDepth);  // 需要原始深度做 depth test

gbufferPass.executes(() -> {
    CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();

    // 多个 color attachment 的 pass
    try (RenderPass rp = enc.createRenderPass(
        () -> "Deferred G-Buffer", albedoView, normalView, materialView, depthView
    )) {
        rp.setPipeline(GBUFFER_PIPELINE);
        rp.setUniform("Camera", cameraUBO.get());

        // 遍历所有可见 chunk section
        for (RenderSection section : visibleSections) {
            SectionBuffers bufs = section.getMesh().getBuffers(ChunkSectionLayer.SOLID);
            if (bufs == null) continue;

            rp.setVertexBuffer(0, bufs.getVertexBuffer());
            rp.setIndexBuffer(bufs.getIndexBuffer(), IndexType.INT);
            rp.drawIndexed(0, 0, bufs.getIndexCount(), 1);
        }
    }
});
```

### 7.3 延迟光照 Pass (Compute)

```java
FramePass lightingPass = graph.addPass("deferred_lighting");
lightingPass.reads(albedoRT);
lightingPass.reads(normalRT);
lightingPass.reads(materialRT);
lightingPass.reads(depthRT);
lightingPass.readsAndWrites(mainColor);  // 写入主 RT

lightingPass.executes(() -> {
    CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
    try (RenderPass rp = enc.createRenderPass(
        () -> "Deferred Lighting", mainColor.get(), OptionalInt.empty()
    )) {
        rp.setPipeline(LIGHTING_PIPELINE);
        rp.bindTexture("gAlbedo",  albedoRT.get(), getSampler());
        rp.bindTexture("gNormal",  normalRT.get(), getSampler());
        rp.bindTexture("gMaterial", materialRT.get(), getSampler());
        rp.bindTexture("gDepth",   depthRT.get(), getSampler());
        rp.setUniform("Lighting", lightUBO);
        rp.draw(0, 3);  // 全屏三角形
    }
});
```

### 7.4 RenderPipeline 定义

```java
public static final RenderPipeline GBUFFER_PIPELINE = RenderPipeline.builder()
    .withLocation("radiance:deferred/gbuffer")
    .withVertexShader("radiance/deferred/gbuffer")
    .withFragmentShader("radiance/deferred/gbuffer")
    .withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.TRIANGLES)
    .withDepthTestFunction(DepthTestFunction.LEQUAL)
    .withDepthWrite(true)
    .withCull(true)
    .withUniform("Camera", UniformType.UNIFORM_BUFFER)
    .build();

public static final RenderPipeline LIGHTING_PIPELINE = RenderPipeline.builder()
    .withLocation("radiance:deferred/lighting")
    .withVertexShader("radiance/deferred/fullscreen")
    .withFragmentShader("radiance/deferred/lighting")
    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
    .withDepthWrite(false)
    .withSampler("gAlbedo")
    .withSampler("gNormal")
    .withSampler("gMaterial")
    .withSampler("gDepth")
    .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
    .build();
```

### 7.5 需要扩展的部分

以下是 Blaze3D 接口当前**不提供**但光线追踪需要的能力:

| 需求 | 当前状态 | 解决路径 |
|------|---------|---------|
| BLAS/TLAS 构建 | 不存在 | NeoForge `GpuDeviceExtension` 扩展点 |
| vkCmdTraceRays | 不存在 | 需要新的 `CommandEncoder` 方法或单独的 RT 接口 |
| SBT 管理 | 不存在 | 同 BLAS/TLAS |
| `drawMultipleIndexed` 自定义 vertex layout | 仅 `setVertexBuffer(0, ...)` — 1 个 slot | NeoForge 可能需要扩展 |

建议通过 NeoForge 机制添加:
```java
// 扩展 GpuDevice
public interface RayTracingDevice extends GpuDevice {
    BLASBuilder createBLASBuilder();
    TLAS createTLAS(List<BLASInstance> instances);
    RayTracingPipeline createRayTracingPipeline(RayTracingPipelineDesc desc);
}

// 扩展 CommandEncoder
public interface RayTracingCommandEncoder extends CommandEncoder {
    RayTracingPass createRayTracingPass(
        RayTracingPipeline pipeline,
        GpuBuffer sbt,
        int width, int height, int depth
    );
}
```

---

## 8. 接口行为契约

### 8.1 已验证的确定性行为 (基于 Gl 实现)

| 行为 | 状态 |
|------|------|
| `RenderPass` 状态惰性绑定 (攒到 draw 时 flush) | ✅ 确定 |
| Pipeline 状态去重 (不重复设置相同状态) | ✅ 确定 |
| `close()` 后的 RenderPass 不可用 | ✅ 确定 |
| FrameGraph 保证 pass 执行顺序 | ✅ 确定 (纯 Java) |
| FrameGraph 资源生命周期管理 | ✅ 确定 (纯 Java) |
| `VALIDATION` 运行时检查 (IDE 模式) | ✅ 确定 |
| Uniform offset 对齐要求 | ✅ 确定 |
| Buffer usage 标志检查 | ✅ 确定 |

### 8.2 未验证的盲区 (Vulkan 后端不可见)

| 盲区 | 评估 |
|------|------|
| Pass 间资源 barrier (image layout transition) | FrameGraph 管理生命周期但不暴露 barrier API。GL 不需要 barrier, VK 需要。推测 VK 后端在 `assignResourceLifetimes()` 时自动插入 |
| `drawMultipleIndexed` 的 VK 实现 | GL 实现是循环, VK 可能用 indirect draw |
| 多线程渲染 | 当前 `RenderSystem.assertOnRenderThread()` 限制单线程 |
| Vulkan RT 扩展 | 不存在于当前接口 |
| GpuTexture 在 VK 下是否等于 VkImage | 推测是, 但无保证 |

### 8.3 保障策略

```
开发阶段: GlDevice 测试 → 验证行为正确
    ↓
代码只依赖接口: 不 downcast 到 Gl* 类, 不直接调 GL
    ↓
VK 后端发布后: 在 VK 上回归测试
    ↓
如有不一致 → NeoForge bug report (接口契约被违反)
```

核心保障: **Minecraft 原版渲染走同一套接口**。如果 VK 后端行为不一致, 原版方块渲染也会出问题, Mojang 有充分动机保证一致性。

---

## 9. 和现有 MCVR 的关系

### 9.1 MCVR 保留做什么

| 组件 | 用途 |
|------|------|
| `ShaderPackLoader` / Shader Pack 格式 | 可作为 `RenderPipeline` 序列化格式的参考 |
| `vk::BLAS` / `vk::TLAS` / `vk::SBT` | 如果 Blaze3D 不暴露光追, 保留作为 native 扩展 |
| `ShaderPack::ExecutionConfig` | 条件执行逻辑可映射到 FrameGraph |

### 9.2 MCVR 不再需要做什么

| 不再需要的 | 原因 |
|-----------|------|
| `vk::Device` / `vk::Buffer` / `vk::Image` | Blaze3D GpuDevice/GpuBuffer/GpuTexture 替代 |
| `vk::GraphicsPipeline` / `vk::ComputePipeline` | Blaze3D RenderPipeline 替代 |
| `vk::CommandBuffer` / `vk::RenderPass` | Blaze3D CommandEncoder/RenderPass 替代 |
| `vk::Swapchain` / `vk::Framebuffer` | Blaze3D RenderTarget/MainTarget 替代 |
| `ChunkBuildData` / geometry 上传 | Blaze3D SectionMesh 已有 GPU buffer |
| `WorldModule` 基类 | 替换为 `FramePass.executes()` |
| `Pipeline::collectWorldModules()` | 替换为 `FrameGraphBuilder.addPass()` |
| `sharedImages_` 资源管理 | 替换为 `FrameGraph ResourceHandle` |

---

## 10. 迁移路线图

### Phase 1: 纯 Blaze3D 光栅化 Deferred

```
目标: 在 Blaze3D 接口上实现 G-buffer + 延迟光照, 零 native 代码
```

- [ ] 创建 `RenderPipeline` 定义 (G-buffer, 光照合成)
- [ ] 在 `FrameGraphBuilder` 中组装 pass
- [ ] 用 GlDevice 测试正确性
- [ ] 输出格式和现有下游模块 (ToneMapping/PostRender) 对齐

### Phase 2: 光追扩展

```
目标: 通过 NeoForge 扩展点添加 Vulkan RT
```

- [ ] 定义 `RayTracingDevice` 扩展接口
- [ ] 实现 BLAS/TLAS 构建 (复用 MCVR 的 BLASBuilder 逻辑)
- [ ] 实现反射 pass (raymarching 从 G-buffer 发射)
- [ ] 实现 GI pass (probe-based 或 screen-space GI)

### Phase 3: Shader Pack 兼容

```
目标: 支持用户自定义着色器, 和 MCVR 的 shader pack 格式互操作
```

- [ ] `configs.json` → `RenderPipeline` 序列化/反序列化
- [ ] `execution` 脚本 → `FrameGraph` 条件执行
- [ ] 兼容 MCVR shader pack 生态
