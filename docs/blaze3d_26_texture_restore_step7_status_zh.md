# Blaze3D 26.2 Step 7: texture 目录恢复状态

日期：2026-06-27

## 本步范围

本步只放开并迁移：

```text
com/radiance/client/texture/**
```

同时只补它的直接编译依赖：

```text
com/radiance/client/constant/VulkanConstants.java
com/radiance/mixin_related/extensions/vanilla_resource_tracker/INativeImageExt.java
com/radiance/mixin_related/extensions/vanilla_resource_tracker/ISpriteExt.java
com/radiance/mixin_related/extensions/vanilla_resource_tracker/ISpriteContentsExt.java
```

旧 `client/proxy/**`、旧 `mixins/**`、旧 renderer replacement 路径仍然不放开。

## 编译策略

先把 26.2 的旧排除全部移除编译了一次，确认错误面主要来自旧 Yarn 包名、
旧 proxy 架构和旧 options enum 接口。随后按“每次只放一个目录”的策略回退为
单目录恢复。

本步最终配置为：

```text
texture 目录参与 26.2 编译
旧 proxy 继续排除
旧 mixins 继续排除
vanilla_resource_tracker 只保留 texture 需要的 extension
glyph 相关 extension 继续排除
```

## 已完成迁移

- `NativeImage` 迁移到 `com.mojang.blaze3d.platform.NativeImage`。
- `Identifier` 迁移到 `net.minecraft.resources.Identifier`。
- `ResourceManager` / `Resource` 迁移到 `net.minecraft.server.packs.resources.*`。
- `AuxiliaryTextureReloader` 迁移到 26.2 的 `PreparableReloadListener`。
- `MipmapUtil` 保留旧算法，增加 26.2/旧版兼容 helper。
- `EmissionRecorder` 保留 emission tile 计算逻辑，26.2 使用 `NativeImage.getPixel`。
- `AuxiliaryTextures.shouldSkipAtlasSprite` 保留 sidecar sprite 跳过语义：

```text
spriteId -> textures/<path>.png
如果 base sprite 存在，则 _s/_n/_f sidecar 不进入 atlas sprite
```

26.2 下使用：

```text
new FileToIdConverter("textures", ".png").idToFile(spriteId)
```

## 兼容桥整理

按当前代码组织要求，本步把大部分版本差异集中到：

```text
com/radiance/client/texture/compat/TextureCompat.java
com/radiance/client/texture/compat/TextureResourceCompat.java
com/radiance/client/texture/compat/TextureUploadCompat.java
com/radiance/client/texture/compat/TextureMaterialCompat.java
com/radiance/client/texture/compat/TextureReloadRegistrationCompat.java
com/radiance/client/texture/compat/AuxiliaryTextureReloaderCompat.java
```

业务类中的职责现在调整为：

```text
AuxiliaryTextures:
  保留 _s/_n/_f sidecar 命名、资源扫描、skip atlas sprite、prepared image cache。

MipmapUtil:
  保留 mipmap 算法，只通过 TextureCompat 读写像素/构造 NativeImage。

EmissionRecorder:
  保留 emission tile 计算，只通过 TextureCompat 读取像素。

TextureTracker:
  保留旧 GL texture metadata 表，format -> channel/vkFormat 映射移到 TextureCompat。

AuxiliaryTextureReloader:
  只作为稳定入口，继承 compat 层；ResourceReloader / PreparableReloadListener 差异移到桥层。
```

仍留在业务类或 extension 边界上的预处理只用于 Java 无类型别名的问题：

```text
NativeImage 包名:
  1.21.4: net.minecraft.client.texture.NativeImage
  26.2:   com.mojang.blaze3d.platform.NativeImage

Identifier 包名:
  1.21.4: net.minecraft.util.Identifier
  26.2:   net.minecraft.resources.Identifier
```

这些类型出现在公开方法签名、record key、扩展接口里，不能像普通方法调用一样完全藏到
compat 方法内部。后续如果要完全去掉这些 import 分支，需要把对应业务类进一步改成
`Object` 边界或拆成版本专用桥类，这会扩大到 mixin/extension 的调用面。

## 旧 GL 上传链迁移结论

`AuxiliaryTextures.loadAndUpload(...)` 在 26.2 下仍然不恢复旧 GL 上传链。

原因：旧实现依赖 OpenGL texture id、`TextureUtil.prepareImage`、`NativeImage.upload`
和旧 `TextureProxy` 上传链路。26.2 第一版目标应改为：

```text
资源包扫描 -> sprite id / sidecar material table
atlas upload/stitch observer -> atlas texture handle / sprite rect
native material ABI -> atlas lookup / PBR material
```

所以旧 GL 上传路径不能恢复为 26.2 的实现。26.2 的正式路径已经改为：

```text
资源包扫描:
  AuxiliaryTextureReloader -> AuxiliaryTextures.prepareDecodedImagesAsync
  解析 _s/_n/_f sidecar，缓存 prepared mip chain
  同步 sprite id -> PBR material record 到 native ABI

atlas/stitch/upload observer:
  TextureAtlas#upload 注入 TextureAtlasSyncMixin
  读取 TextureAtlasSprite 的 UV rect / pixel rect
  同步 atlas texture handle -> sprite rect -> material id 到 native ABI

native scene conversion:
  Blaze3DSceneMaterials 保存 PBR material 和 atlas sprite 表
  Blaze3DSceneMeshConverter 在 draw 转 scene 时用 bound atlas texture + UV 查 sprite
  SceneMaterialBinding 写入 textureId / materialId / contentName
```

当前旧 GL 上传实现已移入 `TextureUploadCompat`：

```text
1.21.4:
  继续走旧 TextureProxy / TextureUtil / NativeImage.upload。

26.2:
  对旧上传入口 no-op，不再创建 GL auxiliary texture。
  材质语义通过资源扫描、atlas 同步和 native material ABI 接入。
```

## 新增 26.2 atlas/material 入口

Java 侧：

```text
NativeBridge.registerPbrMaterialNative(...)
NativeBridge.clearAtlasNative(...)
NativeBridge.syncAtlasSpriteNative(...)

TextureMaterialCompat:
  隔离 26.2 NativeBridge 调用，TextureTracker 只保留稳定 API。

TextureAtlasSync:
  从 TextureAtlas#getTexture / getTextureView 得到 Radiance backend handle。
  从 TextureAtlasSprite 读取 U/V 和 pixel rect。

TextureReloadRegistrationCompat:
  26.2 使用 Fabric ResourceLoader 注册 AuxiliaryTextureReloader。
  1.21.4 保持 no-op，仍由旧 MinecraftClient mixin 注册。
```

Native 侧：

```text
Blaze3DSceneMaterials:
  registerPbrMaterial(sprite -> material id)
  clearAtlas(atlas texture handle)
  syncAtlasSprite(atlas texture handle, sprite rect, material id)
  atlasSpriteAtUv(atlas texture handle, uv)

Blaze3DSceneMeshConverter:
  convert(draw, resources, materials)
  读取 draw 绑定的 Sampler0 texture view -> atlas texture handle
  用第一个可用 UV0 查询 atlas sprite
  将 materialId / spriteId 写入 SceneMaterialBinding
```

当前第一版仍按 draw 级别选择一个 material id。这样满足原版世界/实体等普通 mesh draw
能携带材质语义进入 Radiance scene；以后如果需要一个 draw 内多 sprite/多 material 的精确
分裂，再在 mesh converter 或 scene packet 层做 per-triangle/per-range 拆分。

## 验证

已运行：

```text
.\gradlew.bat :26.2:compileJava --no-daemon --console=plain
.\gradlew.bat :1.21.4:compileJava --no-daemon --console=plain
G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Debug\blaze3d_scene_mesh_converter_test.exe
G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Debug\blaze3d_abi_runtime_test.exe
cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --target core --config Release
```

结果：

```text
Java compile: BUILD SUCCESSFUL
blaze3d_scene_mesh_converter_test: passed
blaze3d_abi_runtime_test: passed
Release core.dll: linked successfully
```

两个 Java 目标均通过。仅剩 `Unsafe` 相关 javac warning，非本步问题。

另外尝试过：

```text
cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --target core --config Debug
```

Debug `core` 在链接阶段失败，原因是现有 `shaderc_combined.lib` 为 Release CRT /
`_ITERATOR_DEBUG_LEVEL=0`，而 Debug core 对象为 Debug CRT / `_ITERATOR_DEBUG_LEVEL=2`。
这是构建配置/第三方库匹配问题，不是本步新增 texture/material ABI 的编译错误；Release
`core` 已通过。

## 下一步建议

继续按单目录策略放开下一组。建议下一轮处理：

```text
com/radiance/client/constant/Constants.java
```

原因：native ABI 和 draw/vertex/pipeline 的 enum 常量需要它，但它当前仍有旧
`RenderLayer` / `VertexFormat` 类型依赖，需要按 26.2 的 Blaze3D pipeline 语义拆出
可复用的纯常量和旧 renderer 专用逻辑。
