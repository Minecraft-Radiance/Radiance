# Blaze3D 26.2 Frame Observer Step 6 状态

## 目标

本步骤实现 26.2 Blaze3D 后端到 Radiance semantic ABI 的第一条 Java 侧同步链路：

```text
LevelRenderer.render(...)
  -> frame/camera/world/lightmap semantic ABI
  -> NativeBridge
  -> Blaze3D ABI runtime
```

这一步不是旧 proxy，也不是把画面委托回原版 OpenGL/Vulkan 后端。它给 native
`Blaze3DSceneProvider` 提供 Radiance 渲染所需的 frame/view/world 输入。

## 已实现

- 新增 `Blaze3DFrameObserver`：
  - 在 `LevelRenderer.render(...)` 入口调用 `beginFrameNative`。
  - 同步主 render target 尺寸和 partial tick。
  - 同步 camera position、view matrix、projection matrix、viewRotation matrix。
  - 保存上一帧 view/projection，供 native temporal/upscaler 使用。
  - 同步 world game time、day time、归一化 sky angle、fog color、fog start/end、fog type、rain/thunder。
  - 同步 `GameRenderer.levelLightmap()` 的 Radiance texture view handle。
  - 在 `LevelRenderer.render(...)` 返回时调用 `endFrameNative`。

- 新增 `LevelRendererFrameObserverMixin`：
  - 26.2 专用 mixin，注入 `LevelRenderer.render` HEAD/RETURN。
  - 通过 `@Shadow GameRenderer gameRenderer` 取得 main target 和 lightmap。

- 新增 `RadianceBackendHandles`：
  - 只暴露 26.2 Radiance backend 的 handle 查询。
  - 不恢复旧 `TextureProxy` / GL id 映射。

- 更新 `radiance-26.mixins.json`：
  - 注册 `LevelRendererFrameObserverMixin`。

## 当前边界

- 第一版仍然不细分 chunk/entity/item。这个 observer 只补齐 frame/world/view 语义。
- `skyAngle` 现在按 `dayTime % 24000 / 24000` 归一化，后续如果 native 需要 Minecraft
  timeline 的精确太阳/月亮 easing，再扩展独立字段。
- `nearPlane` 使用 26.2 world/hud 渲染路径中稳定可见的 `0.05F`。`farPlane/depthFar`
  使用 `CameraRenderState.depthFar`。
- lightmap 如果不是 Radiance texture view，则 handle 为 0，native 侧应作为缺省 lightmap
  处理，不能崩。

## 验证

已执行：

```text
.\gradlew.bat :26.2:compileJava
.\gradlew.bat :26.2:classes
```

结果：

```text
BUILD SUCCESSFUL
```

仅存在既有 `sun.misc.Unsafe` 编译警告，与本步骤无关。

## 下一步

继续按第一版文档推进 atlas sprite / PBR material 语义：

```text
resource-pack PBR sidecar scan
Minecraft atlas sprite rect sync
atlas texture handle + UV -> sprite id -> material id
```
