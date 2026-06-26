# Radiance 26.2 Backend Parity Checklist

Scope: Radiance must behave as a Blaze3D backend beside vanilla GL and Vulkan.
The old `RendererProxy` / `BufferProxy` / `EntityProxy` / `ChunkProxy` model is
not the compatibility boundary in 26.2. The compatibility boundary is the
`com.mojang.blaze3d.systems` backend interface surface plus semantic observers
used for shared-scene classification.

## Why The Old Proxy Layer Is Retired

The old proxy layer replaced or shadowed Minecraft rendering concepts before
they reached GL:

| Old proxy | Old responsibility | 26.2 replacement |
|---|---|---|
| `RendererProxy` | Owned alternate render-loop and renderer integration decisions. | Official `GpuBackend` registration plus `GpuDeviceBackend`/`RenderPassBackend` execution. |
| `BufferProxy` | Mirrored/replaced GL buffer uploads and built-buffer ownership. | `GpuBuffer`, `GpuBufferSlice`, `CommandEncoderBackend#writeToBuffer`, copies, maps, and backend CPU shadowing. |
| `EntityProxy` | Captured entity render state and transforms through renderer mixins. | `EntityRenderDispatcher#extractEntity/#submit`, `SubmitNodeCollection`, and backend draw correlation. |
| `ChunkProxy` | Captured chunk mesh/build/draw state through chunk renderer mixins. | `ChunkSectionsToRender`, `RenderPass.Draw`, dynamic uniform `"ChunkSection"`, terrain pipelines, and backend draw packets. |

They are retired because 26.2 already exposes the rendering stream through
Blaze3D backend interfaces. Re-porting old proxy business logic into mixins
would duplicate the engine, miss modded Blaze3D submissions, and prevent Fabric /
NeoForge-neutral backend behavior. Mixins now only attach semantic hints; backend
objects and draw packets are the source of truth.

## Backend Interface Coverage

| Interface | Required Radiance behavior | Notes from GL/Vulkan |
|---|---|---|
| `GpuBackend` | Expose name, set GLFW/window hints, translate window creation errors, create `GpuDevice`. | Selection is through `PreferredGraphicsApi#getBackendsToTry`; no old GL/window suppression mixins. |
| `GpuDeviceBackend` | Create surfaces, command encoder, samplers, textures/views, buffers, debug messages, pipelines, timestamp query pools, timestamp now, device info, cleanup. | `DeviceInfo.features()` drives vanilla validation, so advertised features must be exact. |
| `GpuSurfaceBackend` | Configure swapchain/surface, report suboptimal, acquire next texture, blit render target to surface, present, close, report present modes. | Raster compatibility depends on normal Minecraft surface lifecycle working without special game-loop hooks. |
| `CommandEncoderBackend` | Submit frames, rotate transient memory, begin/submit render passes, clear color/depth, buffer writes/copies, texture writes/copies/readback callbacks, fences, timestamps. | GL and Vulkan both allow two submits in flight and defer readback callbacks behind fence/destruction queues. |
| `RenderPassBackend` | Track pipeline, textures/samplers, uniforms, scissor, vertex/index buffers, all draw variants, `drawMultipleIndexed`, timestamps, debug groups. | This is the universal draw stream Radiance must capture for shared scene. |

## DeviceInfo And Feature Truthfulness

`RenderPass` validates features before it calls the backend. Radiance must not
advertise unsupported features.

| Feature | Meaning | Observed vanilla behavior |
|---|---|---|
| `shaderDrawParameters` | Shader can use draw parameters/base instance style values. | Vulkan advertises true. |
| `multiDrawDirectInterleaved` | Interleaved direct multi-draw buffer form. | Vulkan uses `VK_EXT_multi_draw`; GL throws for this backend method. |
| `multiDrawDirectSeparate` | Separate first/count/offset direct multi-draw arrays. | GL supports this; Vulkan throws for this backend method. |
| `multiDrawIndirect` | Indirect draw count greater than one. | GL and Vulkan support through their indirect paths. |
| `drawIndirect` | Single indirect draw. | GL and Vulkan support. |
| `nonZeroFirstInstance` | Non-zero first instance is legal. | GL uses ARB base instance; Vulkan advertises true. |
| `persistentMapping` | Transient memory can use persistent mapping. | GL may choose persistent or fallback; Vulkan transient memory is staging based. |

Required Radiance rule: feature bits describe native/backend support, not desired
scene-capture capability. If a feature is false, vanilla will avoid or reject that
call path before Radiance sees it.

## Object Semantics Required For Classification

Radiance needs the same backend object model as GL/Vulkan, plus compact semantic
tags for shared-scene view construction.

| Object | Backend requirement | Classification value |
|---|---|---|
| `RenderPipeline` | Compile/cache by pipeline identity, shaders, defines, bind layouts, color/depth state, topology, vertex formats. | Pipeline `location`, shader ids, defines, color/depth/blend state, topology, vertex format. |
| `GpuBuffer` | Track usage bits, size, close state, slices, map/read/write semantics, upload/copy history. | Labels, usage, CPU-shadowed contents, uniform slice names, vertex/index/indirect roles. |
| `GpuTexture` | Track label, usage, format, dimensions, mip levels, array/cubemap layers, close state. | Resource labels, atlas/lightmap/post target identity, sidecar PBR association. |
| `GpuTextureView` | Track base mip/mip count and parent texture lifetime. | View-specific mip/window for material and pass classification. |
| `RenderPassDescriptor` | Track label, color/depth attachments, clear values, render area. | World/pass/post/UI/shadow/offscreen separation. |
| `RenderPass.Draw<T>` | Execute per-draw uploader, index/vertex buffers, first/count/base vertex. | Terrain chunk-section metadata via dynamic uniform names such as `"ChunkSection"`. |
| `GpuBufferSlice` uniforms | Decode CPU-shadowed dynamic uniform slices where the name is stable. | Vanilla terrain `ChunkSection` slices decode position, visibility, atlas size, and model-view data for shared-scene filtering. |
| Render pass bindings | Track uniform and texture binding names on the current pass. | Enables mod/backend classification such as Sodium terrain without loader or mod API references. |
| Submit-node execution scope | Carry semantic tags from submit collection to feature execution draw calls. | Entity/model/item/text/particle nodes are prepared before drawing, so backend draw capture must read execution scope, not only collection-time observers. |

## Draw Path Coverage

Radiance must implement and capture every draw entry point:

| Draw entry point | Required behavior |
|---|---|
| `drawIndexed` | Indexed direct draw with current pipeline, uniforms, textures, index buffer, vertex buffers. |
| `multiDrawIndexed(IntBuffer, ...)` | Interleaved indexed multi-draw if advertised. |
| `multiDrawIndexed(PointerBuffer, IntBuffer, IntBuffer, ...)` | Separate indexed multi-draw if advertised. |
| `drawIndexedIndirect` | Validate/capture indirect command buffer usage and count. |
| `drawMultipleIndexed` | Required for vanilla terrain. Execute each draw's uniform uploader and capture per-draw `"ChunkSection"` slice. |
| `draw` | Non-indexed direct draw. |
| `multiDraw(IntBuffer, ...)` | Interleaved non-indexed multi-draw if advertised. |
| `multiDraw(IntBuffer, IntBuffer, ...)` | Separate non-indexed multi-draw if advertised. |
| `drawIndirect` | Non-indexed indirect draw. |

`drawMultipleIndexed` is not optional. Vanilla terrain uses it to upload dynamic
per-section uniforms and draw chunk sections in groups. Missing this path would
make Radiance unable to classify vanilla chunks correctly.

## Resource And Copy Coverage

| Path | Required behavior |
|---|---|
| Buffer creation with data | Create backend buffer and upload initial bytes through the same command/copy semantics used later. |
| `writeToBuffer` | Update GPU buffer and CPU shadow for semantic decoding. |
| `copyToBuffer` | Copy backend contents and maintain shadow where available. |
| `writeToTexture` | Upload texture data including mip/layer parameters. |
| `copyBufferToTexture` | Support atlas/staging uploads and row/height offsets. |
| `copyTextureToBuffer` | Support screenshots/readbacks and invoke callback after GPU completion. Current Java backend records readback metadata and writes deterministic zero-filled shadow bytes until native readback is wired. |
| `copyTextureToTexture` | Support render-target/post/atlas copies. |
| Clears | Preserve clear semantics for full-target and regional color/depth clears. |

## Current Radiance Implementation Status

| Area | Status |
|---|---|
| Backend registration shim | Staged under `src/minecraft26_2`; inserts Radiance candidate through `PreferredGraphicsApi`. |
| Real `GpuBackend` implementation | Added as opt-in `RadianceGpuBackend`; default selection remains disabled until native present is wired. |
| Device/surface/command encoder/render pass | Java skeleton implemented for all 26.2 backend interfaces. Surface `present` is explicit unsupported. |
| CPU-shadowed buffers/textures | Buffers are CPU-shadowed for creation, write, copy, map, and transient upload paths. Textures record dimensions/tags and write/copy/clear operation metadata; byte payload ABI remains deferred. |
| Pipeline compile/cache | Pipeline registration/cache hooks are implemented as capture metadata. Shader/native compilation is not wired. |
| Draw capture and shared-scene packet creation | All `RenderPassBackend` draw variants are routed to `RadianceRenderPassCapture`; command submit drains backend resource tables, material/atlas/image/glyph tables, world/entity state, plus draw packets into a Java native-bridge snapshot with compact tag bits, view masks, and self-describing tag/view dictionaries. |
| Vanilla chunk-section draw semantics | `drawMultipleIndexed` runs each draw uploader before capture and records the decoded `"ChunkSection"` uniform slice per draw. |
| Pass binding classification | Draw tags include policy results from current uniform names and texture binding names. |
| Tag policy integration | Backend objects combine render pass, pipeline, buffer, and dynamic-uniform tags through `ScenePolicyRuntime`; submit/resource hints remain side-channel inputs. |
| Submit-node semantic capture | Feature submit nodes store active submit/entity tags during collection and reapply them during execution so final draw packets are tagged. Entity extraction/submit state is exported as a frame-level entity table. |

## Current Native Execution Gaps

| Area | Gap |
|---|---|
| Surface/present | Java now routes device/surface/configure/acquire/present/destroy through `RadianceNativeBackendBridge`. The shipped `core.dll` loads, but it does not export the new 26.2 backend JNI ABI yet, so opt-in startup stops at a precise missing-symbol diagnostic. |
| Command execution | Clears, blits, texture copies/readbacks, and draw execution are capture-only until native command encoding is wired. Clear/copy/write/readback paths now produce structured metadata. |
| Texture payloads | Texture write/copy/clear operation descriptors are tracked, but full byte payload transfer to native is not finalized. |
| Feature bits | The Java backend advertises no multi-draw/indirect/non-zero-first-instance/persistent features yet. This is honest for the current capture skeleton, but Sodium and full vanilla parity require native implementation before enabling by default. |

## Implementation Log

| Date | Step | Result |
|---|---|---|
| 2026-06-22 | Vanilla GL/Vulkan backend parity investigation | Created this checklist from 26.2 `GpuBackend`, `GpuDeviceBackend`, `GpuSurfaceBackend`, `CommandEncoderBackend`, `RenderPassBackend`, `RenderPass`, `GpuBuffer`, `GpuTexture`, `RenderPipeline`, GL and Vulkan implementations. |
| 2026-06-22 | Java backend skeleton | Added opt-in Radiance backend/device/surface/encoder/pass/resource/query classes and connected them to capture registry packet generation while keeping native present disabled. |
| 2026-06-22 | Vanilla terrain uniform capture | Added per-draw dynamic uniform slice capture and std140 decoding for `DynamicUniforms.ChunkSectionInfo`. |
| 2026-06-22 | Pass binding classification | Added uniform/texture binding state to draw classification so Sodium raster terrain is tagged by policy as acceleration-deferred. |
| 2026-06-22 | Shared-scene bridge snapshot | Replaced the native bridge no-op with a deterministic snapshot packet shape carrying draw metadata, buffer handles, tag bits, view mask, and optional vanilla chunk-section uniform data. |
| 2026-06-22 | Submit-node semantic scope | Added submit-node capture/execution scopes and draw-tag merge so non-terrain feature renderer draws keep semantic tags after 26.2's prepare/execute split. |
| 2026-06-22 | Texture operation capture | Expanded texture records from write counts to structured write, buffer-copy, texture-copy, color-clear, and depth-clear operation metadata. |
| 2026-06-22 | Resource-backed frame snapshot | Native bridge snapshots now include buffer, texture, texture-view, pipeline, and render-pass tables alongside draw packets so handle references are inspectable before native ABI finalization. |
| 2026-06-22 | Texture readback capture | Added `COPY_TEXTURE_TO_BUFFER` operation records for full and regional readbacks. The Java backend invokes callbacks after registering readback metadata and writes zero-filled target buffer shadow bytes as a deterministic placeholder until native readback provides real pixels. |
| 2026-06-22 | Snapshot semantic dictionaries | Added frame-level tag and view dictionaries to the Java native-bridge snapshot so native shared_scene ingestion can decode compact `tagBits`/`viewMask` without relying on implicit Java-only ordering. |
| 2026-06-22 | Entity frame snapshot | Added an entity registry populated from `EntityRenderDispatcher#extractEntity/#submit` and exported frame-level entity records through the native bridge snapshot. |
| 2026-06-22 | Resource semantic tables | Native bridge snapshots now include material, atlas sprite, sprite contents, decoded image/sidecar, and glyph tables from the Java resource registries. |
| 2026-06-22 | Native backend bridge boundary | Added a 26.2-only `RadianceNativeBackendBridge`, routed device/surface/present through native handles, preserved capture-only fallback diagnostics, and declared the required JNI functions in `src/main/native/include/com_radiance_client_render_blaze3d_backend_RadianceNativeBackendBridge.h`. |
