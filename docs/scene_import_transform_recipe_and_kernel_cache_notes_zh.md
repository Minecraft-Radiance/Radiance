# Scene Import Transform Recipe 与 Compute Kernel 缓存边界说明

本文记录 26.2 Blaze3D scene import 后续需要处理的两个问题：

- 把 `objectToWorld` 的提取从 C++ 硬编码升级为规则包里的 `transforms:` recipe。
- 明确哪些变换可以走 draw/instance transform，哪些必须进 compute import shader，并说明缓存/BLAS 重建边界。

目标不是让所有 Blaze3D draw 自动正确导入，而是让不同 Minecraft 版本和 mod 可以通过规则、import kernel、transform recipe 适配到同一套 native scene import 框架里。

## 1. 基本分层

scene import 应该把三类问题分开：

```text
importKernel:
  处理 mesh 自身格式。
  例如 Position/Color/UV/Normal 的 offset、stride、packed format、quad 展开。

transform recipe:
  处理这个 draw 对应的 mesh 怎么放到 scene 空间。
  例如 chunk section translation、ModelViewMat、ModelOffset、model matrix。

material / texture policy:
  处理纹理、PBR、透明、发光、双面、atlas/sprite 映射等。
```

`Position` 不应该被默认理解成 world-space。它可能是：

- chunk section local space。
- entity/model local space。
- camera-relative space。
- 已经被 Java/CPU 预变换过的空间。
- shader 里继续被 uniform、texture、SSBO、time 改写的中间输入。

因此，规则必须同时描述：

```text
source vertex format -> importKernel
draw transform layout -> transform recipe
material/texture semantics -> material policy
```

## 2. 为什么优先把普通变换放到 objectToWorld

普通刚体/仿射变换不建议烘进 compute kernel 输出的 position。推荐边界是：

```text
源 vertex/index buffer + vertex format -> local mesh
draw uniform / camera / model stack -> objectToWorld
```

原因是缓存稳定性。

以 26.2 terrain 为例，Minecraft 原 shader 逻辑是：

```glsl
vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
```

如果 compute import kernel 直接输出最终 `pos`，那么相机一动，`CameraBlockPos/CameraOffset` 变化，输出 vertex position 就变化。结果：

```text
camera move
  -> imported position buffer dirty
  -> BLAS geometry dirty
  -> geometry reimport / BLAS rebuild pressure
```

如果 kernel 只输出 section-local `Position`，native 只更新：

```text
objectToWorld = translate(ChunkPosition - cameraPos)
```

那么：

```text
camera move
  -> local mesh unchanged
  -> BLAS geometry can be reused
  -> only draw packet / TLAS instance / metadata changes
```

这不是正确性问题，而是缓存边界问题。

## 3. 当前 C++ 写死 transform policy 的问题

当前实现已经有类似：

```text
transformPolicy: chunk_section
transformPolicy: dynamic_transforms
```

native 根据 policy 读取固定 uniform 和固定 offset：

```text
ChunkSection:
  ChunkPosition at std140 offset 80

DynamicTransforms:
  ModelViewMat at offset 0
  ModelOffset at offset 80
```

这能修 26.2 原版的一部分问题，但边界不够好：

- 不同 Minecraft 版本的 uniform 名字、字段、offset 可能不同。
- mod 可以使用自定义 pipeline、custom uniform、custom vertex shader。
- 同一个 `DynamicTransforms` include 下，不同 shader 未必都使用 `ModelOffset`。
- native C++ 不应该长期知道 `ChunkSection`、`DynamicTransforms` 这些版本具体细节。

26.2 原版里至少要区分：

```text
terrain.vsh:
  Position + ChunkPosition camera-relative

block.vsh:
  Position + ModelOffset, then ModelViewMat

entity.vsh:
  Position, then ModelViewMat

particle.vsh:
  Position, then ModelViewMat
```

所以 `dynamic_transforms` 这种单一策略太粗，应拆为更明确的 transform recipe。

## 4. transforms: recipe 设计

`transforms:` recipe 是规则包里的一个表，用来描述如何从 draw 绑定的 uniform 中生成 `objectToWorld`。

它不是 runtime 每帧解析的脚本。建议流程是：

```text
启动加载 yml
  -> Java/native 注册 pipeline rules、kernels、transform recipes
  -> native 编译为 TransformRecipe 表
  -> draw classification 得到 transformRecipeId
  -> 每个 draw 按 id 执行固定 recipe
```

示例：

```yaml
pipelineRules:
  - id: minecraft.pipeline.terrain.solid
    semantic: terrain
    importKernel: terrain_chunk_quad_p28_color_uv_light
    transform: minecraft_26_2.chunk_section

  - id: minecraft.pipeline.block_like.solid
    semantic: block_like
    importKernel: block_like_quad_p28_color_uv_light
    transform: minecraft_26_2.dynamic_model_view_with_offset

  - id: minecraft.pipeline.entity_item.opaque
    semantic: entity
    importKernel: entity_item_quad_p36_color_uv_overlay_light_normal
    transform: minecraft_26_2.dynamic_model_view

transforms:
  - id: minecraft_26_2.chunk_section
    uniform: ChunkSection
    op: chunk_section_camera_relative
    fields:
      chunkPosition: { type: ivec3, offset: 80 }

  - id: minecraft_26_2.dynamic_model_view
    uniform: DynamicTransforms
    op: inverse_view_times_model_view
    fields:
      modelView: { type: mat4, offset: 0 }

  - id: minecraft_26_2.dynamic_model_view_with_offset
    uniform: DynamicTransforms
    op: inverse_view_times_model_view_times_translate
    fields:
      modelView: { type: mat4, offset: 0 }
      modelOffset: { type: vec3, offset: 80 }
```

native 不再按 Minecraft 具体名字写死逻辑，而是提供少数通用执行 primitive：

```text
find uniform binding by name
read field by offset/type
read current/previous WorldUBO camera/view
matrix multiply
translation
camera-relative translation
emit objectToWorld / previousObjectToWorld
```

第一版不建议做复杂表达式语言。只做少数内置 `op`：

```text
identity
chunk_section_camera_relative
inverse_view_times_model_view
inverse_view_times_model_view_times_translate
model_matrix
model_matrix_camera_relative
```

后续如果 mod 需要更多能力，再扩展 op，而不是把 native 变成通用脚本解释器。

## 5. recipe 能解决什么

它能解决的是 draw-level 仿射变换的版本/mod 差异：

```text
可以通过 recipe 适配：
  chunk position
  model matrix
  model-view matrix
  model offset
  camera-relative translation
  scale/rotate/translate 组合

不能仅靠 recipe 适配：
  顶点 shader 按时间摆动
  水面波动
  草/树叶风动
  骨骼蒙皮
  morph target
  从 texture/SSBO 读取 per-vertex 位移
  shader 生成 Position
```

这些非 draw-level 仿射变换，必须进 compute import shader，或者标记 unsupported。

## 6. Compute Import Shader 处理变换的边界

compute import shader 现在已经能拿到 `SceneImportDrawRecord`，其中包含：

```glsl
SceneImportBufferRef vertexBuffers[16];
SceneImportBufferRef indexBuffer;
SceneImportTextureRef textures[8];
SceneImportBufferRef uniforms[8];
mat4 objectToWorld;
mat4 previousObjectToWorld;
```

因此技术上 kernel 可以读取 `draw.uniforms[]` 指向的 uniform buffer，并把 uniform 影响直接烘进输出 position。

适合放进 compute import shader 的情况：

```text
顶点格式解码：
  packed position / packed normal / special uv layout

per-vertex 变形：
  每个顶点的位移不同
  依赖 vertex attribute、time、texture、SSBO

非仿射变换：
  无法用一个 objectToWorld 表达

shader-generated geometry:
  原始 vertex buffer 不是最终 mesh 空间的稳定表示
```

不建议放进 compute import shader 的情况：

```text
整个 draw 共用一个 model matrix
整个 draw 共用一个 chunk translation
整个 draw 共用一个 model offset
```

这些更适合 transform recipe + objectToWorld。

## 7. 缓存依赖规则

如果 compute import kernel 的输出 position/material 会受 uniform/texture/transform 影响，必须在 kernel spec 中声明依赖。

当前 `kernels.yml` 有两类依赖：

```yaml
dependencies: []
geometryDependencies: []
```

建议语义：

```text
dependencies:
  影响 import record / material / draw metadata 的签名。

geometryDependencies:
  影响输出 geometry position/index/material buffer 或 BLAS geometry 的签名。
```

典型配置：

```yaml
kernels:
  - id: normal_static_mesh
    shader: shaders/world/scene_import/static_mesh.comp
    dependencies: [textures, material_state]
    geometryDependencies: []
```

表示：

```text
texture/material 变了，需要更新 material/metadata；
local geometry 不受 transform/uniform 影响，可以复用。
```

如果 kernel 读取 uniform 并烘进 position：

```yaml
kernels:
  - id: custom_vertex_deform
    shader: shaders/world/scene_import/custom_vertex_deform.comp
    dependencies: [textures, material_state, uniforms]
    geometryDependencies: [uniforms]
```

如果 kernel 读取 `draw.objectToWorld` 并把它乘进 position：

```yaml
kernels:
  - id: baked_transform_mesh
    shader: shaders/world/scene_import/baked_transform_mesh.comp
    dependencies: [textures, material_state, transform]
    geometryDependencies: [transform]
```

但这会导致 objectToWorld 变化时 geometry dirty，通常不是首选。

## 8. 推荐决策表

```text
情况: vanilla terrain chunk
处理: kernel 输出 section-local Position，recipe 生成 chunk_section objectToWorld
缓存: geometryDependencies 不需要 transform

情况: vanilla block/entity/particle 普通 draw
处理: kernel 输出 local Position，recipe 生成 dynamic model transform
缓存: geometryDependencies 不需要 transform

情况: shader 使用 ModelOffset，但只有整个 draw 共用一个 offset
处理: recipe 中包含 ModelOffset
缓存: geometryDependencies 不需要 transform

情况: shader 根据 vertex attribute 做非线性变形
处理: custom compute import shader
缓存: geometryDependencies 声明相关 attribute/source/uniform 依赖

情况: shader 根据 time/uniform 做顶点动画
处理: custom compute import shader 或 unsupported
缓存: geometryDependencies 声明 uniforms/time；接受频繁 reimport/BLAS rebuild

情况: shader 用 texture/SSBO 位移
处理: custom compute import shader，并把 texture/SSBO 作为依赖
缓存: geometryDependencies 声明 textures/uniforms/ssbo，或者 unsupported
```

## 9. 对 mod 兼容性的意义

Blaze3D 只保证最终走类似：

```text
setPipeline
setUniform
bindTexture
setVertexBuffer
drawIndexed
```

它不保证：

```text
Position 是什么空间
uniform 名字是什么
uniform offset/layout 是什么
shader 是否使用 ModelOffset
shader 是否在 vertex stage 修改 Position
PBR/纹理语义如何绑定
```

因此目标应定义为：

```text
只要 mod 使用 Blaze3D，
并且提供匹配的 yml rule + import kernel + transform recipe + material policy，
就可以进入现有 Radiance scene import 管线；
不需要修改 native 主框架。
```

不是：

```text
所有 Blaze3D draw 不写规则就自动正确。
```

## 10. 实现路线建议

第一阶段：把当前 `transformPolicy` 拆细。

```text
default / identity
chunk_section
dynamic_model_view
dynamic_model_view_with_model_offset
```

这一步可以先继续用 enum，修正 26.2 原版 terrain/block/entity/particle 的明显错误。

第二阶段：引入 `transforms:` yml。

需要改动：

```text
Java rule parser:
  解析 transforms 表。
  pipelineRules 从 transformPolicy 改为 transform id。
  输出 pipeline classification report 时打印 transform recipe。

JNI/native registration:
  注册 transform recipe 表。
  pipeline record 存 transformRecipeId。

native scene_import_rules:
  classify 后返回 transformRecipeId。

native scene_import_runtime:
  用 recipe executor 生成 objectToWorld。
  recipe 缺失或 uniform 缺失时跳过 draw，并统计 diagnostics。

bundle/report:
  启动输出所有已注册 transform recipe、pipeline 使用关系、缺失/unsupported 情况。
```

第三阶段：允许 kernel 明确选择 baked 变换。

例如：

```yaml
pipelineRules:
  - id: some_mod.custom_deformed_mesh
    importKernel: some_mod_custom_deformed_mesh
    transform: identity

kernels:
  - id: some_mod_custom_deformed_mesh
    dependencies: [textures, material_state, uniforms]
    geometryDependencies: [uniforms]
```

这样特殊 mod 可以牺牲缓存稳定性，换取更高兼容性。

## 11. 当前 26.2 需要特别注意

当前 26.2 bundle 不应把所有 `DynamicTransforms` draw 都视为同一种 transform。

建议规则语义：

```text
block-like:
  dynamic_model_view_with_model_offset

entity item:
  dynamic_model_view

particle/weather:
  dynamic_model_view

terrain:
  chunk_section
```

如果后续发现某个 pipeline 的原 shader 用了 texture matrix、custom model offset 或特殊 vertex stage，应单独拆 rule/kernel/recipe，不要扩大通用规则的含义。

## 12. 关键原则

```text
1. objectToWorld 表达 draw-level 仿射空间关系。
2. compute import kernel 表达 source mesh 解码和无法由 objectToWorld 表达的 per-vertex 逻辑。
3. 一旦 kernel 烘进 uniform/transform 影响，必须声明 geometryDependencies。
4. native 框架提供通用 recipe 执行器，不维护具体版本/mod 的 shader 语义。
5. 版本/mod bundle 维护自己的 rules、kernels、transforms、materials。
```

