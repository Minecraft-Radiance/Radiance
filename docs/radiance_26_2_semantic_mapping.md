# Radiance 26.2 Semantic Mapping

Goal: find the narrowest, most stable Minecraft 26.2 rendering path that can feed a Radiance backend/shared scene with maximum vanilla and mod coverage.

Working hypothesis:

- The best narrow path is not raw `GpuDeviceBackend` alone.
- The likely stable layer is `RenderPipeline` + `RenderPass` + `PreparedRenderType` + terrain-specific `ChunkSectionsToRender`.
- Use this path for vanilla-equivalent coverage first. Extra Radiance semantics such as LabPBR, emissive metadata, or custom ray tracing flags can be added through smaller targeted hooks.

## Candidate Narrow Channels

| Channel | What It Provides | Coverage | Stability Guess | Notes |
|---|---|---|---|---|
| `GpuDeviceBackend` / `CommandEncoderBackend` | Resource creation, copies, render pass creation | All backend GPU work | High for backend implementation, low for scene semantics | Too low-level alone. Good place to implement Radiance as a backend, not enough for semantic scene reconstruction. |
| `RenderPassBackend` | Pipeline, textures, uniforms, vertex/index buffers, draw ranges | All draws that use Blaze3D render passes | High | Main command interception point. Semantics available indirectly through current `RenderPipeline`, bound texture names, uniforms, and draw parameters. |
| `RenderPipeline` | Pipeline id, shaders, vertex format, topology, cull, depth/stencil, blend/color target | Vanilla/mods using new pipeline API | High | Strong semantic signal via `location`, e.g. `pipeline/solid_terrain`, `pipeline/entity_solid`, `pipeline/weather_no_depth_write`. |
| `PreparedRenderType` | Prepared pipeline, output target, dynamic transform slice, textures, draw buffer/range | Immediate/entity/features using `RenderType` | Medium-high | Important for non-terrain geometry. Contains pipeline and textures before draw. |
| `ChunkSectionsToRender` | Chunk layer, section dynamic uniforms, terrain draw groups, block atlas/lightmap | Terrain chunks | Medium-high | Best terrain-specific narrow channel. Keeps chunk section origin and layer semantics. |

## Radiance Scene Needs

| Radiance Need | Current/Native Consumer | 26.2 Vanilla Source Candidate | Direct? | Hook Needed? | Notes |
|---|---|---|---|---|---|
| Geometry domain: chunk/entity/weather/particle/text/name tag/star | `SceneDrawPacket.domain` | `ChunkSectionsToRender`, `SubmitNodeCollection` phases, `WeatherRenderState`, `RenderPipeline.location` | Mostly | Small hooks for high confidence | Terrain direct. Feature submit phases classify entity/text/name tag/particle/outline much better than backend-only inference. |
| Geometry class: opaque/cutout/translucent/additive/overlay | `SceneDrawPacket.class`, raster metadata | `ChunkSectionLayer`, `RenderPipeline.getColorTargetState()`, depth/cull/blend states, feature phases | Mostly | Maybe | Terrain direct. Other classes can be derived from pipeline state and submit phase. |
| Vertex/index GPU buffers and draw range | `SceneDrawPacket` buffers and counts | `RenderPass.setVertexBuffer`, `setIndexBuffer`, `drawIndexed`, `drawMultipleIndexed`, `StagedVertexBuffer.ExecuteInfo` | Yes | Backend implementation | Available at backend layer. Radiance backend should own/mirror buffers so device addresses and BLAS input are available. |
| Vertex format/topology | `SceneDrawPacket.vertexFormat`, draw mode | `RenderPipeline.getVertexFormatBinding(0)`, `getPrimitiveTopology()` | Yes | No if intercepting pipeline | Maps to Radiance vertex conversion rules. Need conversion from vanilla vertex formats to Radiance PBR/material buffer format. |
| Texture/material binding | `SceneDrawPacket.materialBinding.textureId`, raster metadata | `RenderPass.bindTexture`, `PreparedRenderType.textures`, `RenderType.prepare()`, texture manager/resource hooks | Mostly | Maybe | Vanilla texture binding direct. LabPBR/extra material textures still need resource-pack/material hooks. |
| Raster metadata | `SceneRasterMetadata` | `RenderPipeline`: blend/color target/depth/cull/topology/location/sort key; `OutputTarget`; submit phase | Mostly | Maybe | New pipeline state can replace much of old `RenderLayer.MultiPhase` parsing. Some semantic flags still easier from submit phase or pipeline name. |
| Chunk section origin/transform | Chunk object-to-world, TLAS instance transform | `DynamicUniforms.ChunkSectionInfo` written in `LevelRenderer.prepareChunkRenders` | Yes for terrain | Maybe | Exposed as dynamic uniform slice in `drawMultipleIndexed` via `"ChunkSection"`. Backend can mirror uniform writes or parse backend-owned uniform buffer contents. |
| Entity transform | Entity object-to-world/current transform | `ModelFeatureRenderer.Submit.pose`, `DynamicTransforms` uniform in `PreparedRenderType`, `EntityRenderState.x/y/z` | Mostly | Small hook recommended | Draw sees matrix slice; submit layer gives state+pose before batching. |
| Previous transform / temporal identity | Motion vectors / temporal accumulation / TLAS history | Section origin for terrain; `EntityRenderState` plus extraction side map for entities | Partial | Yes | Terrain identity can be section origin/node. Entity identity likely requires storing original entity id/object identity at extraction or submit. |
| Ray tracing inclusion flags | TLAS masks / Radiance flags | No vanilla equivalent | No | Yes if needed | For vanilla-equivalent raster, not required. For Radiance-specific path tracing classification, needs custom policy/hook. |
| PBR/LabPBR material metadata | Radiance material system | Resource-pack textures/material parser | No vanilla direct | Yes | Independent from official backend split. |
| Acceleration structures | `SceneAccelerationData`, BLAS/TLAS | RadianceBackend-owned buffers and scene translator | No direct vanilla concept | Backend/native implementation | Official backend does not build ray tracing AS. Radiance must build BLAS/TLAS from intercepted/mirrored geometry. |

## Terrain Path Findings

| Semantic | 26.2 Code Source | Details | Radiance Use |
|---|---|---|---|
| Terrain layers | `net/minecraft/client/renderer/chunk/ChunkSectionLayer.java` | `SOLID`, `CUTOUT`, `TRANSLUCENT`, each has fixed `RenderPipeline` and label. | Direct geometry class mapping. |
| Terrain pipeline names | `net/minecraft/client/renderer/RenderPipelines.java` | `pipeline/solid_terrain`, `pipeline/cutout_terrain`, `pipeline/translucent_terrain`. | Stable classifier for terrain render state. |
| Section mesh draw metadata | `CompiledSectionMesh.java`, `SectionMesh.java` | Per layer `SectionDraw(indexCount, indexType, hasCustomIndexBuffer)`. | Draw range and index format. |
| Terrain GPU buffer slices | `SectionRenderDispatcher.getRenderSectionSlice` | Returns vertex buffer, vertex offset, optional index buffer, index offset. | Backend-owned buffers can map directly to Radiance buffers or scene packets. |
| Section origin and atlas size | `LevelRenderer.prepareChunkRenders` | Creates `DynamicUniforms.ChunkSectionInfo(modelView, origin x/y/z, visibility, atlas width/height)`. | Object transform/section identity for TLAS and scene tracking. |
| Batched terrain draw | `ChunkSectionsToRender.renderGroup` | Binds block atlas `Sampler0`, lightmap `Sampler2`, sets layer pipeline, then `drawMultipleIndexed(..., List.of("ChunkSection"), chunkSectionInfos)`. | Very strong narrow channel for chunk scene extraction. |
| Per-draw section uniform binding | `RenderPass.Draw<T>` and `GlCommandEncoder.executeDrawMultiple` / `VulkanRenderPass.drawMultipleIndexed` | Each `Draw` can carry `uniformUploaderConsumer`. Terrain uses it to upload `"ChunkSection"` with the exact `GpuBufferSlice` for that section before issuing the draw. | Radiance backend can associate each terrain draw with its section origin/visibility by observing the uniform upload inside `drawMultipleIndexed`. |
| Chunk section UBO layout | `DynamicUniforms.ChunkSectionInfo` | Std140 data: `modelView`, `visibility`, `textureAtlasWidth/Height`, `x/y/z`. Size is `CHUNK_SECTION_UBO_SIZE`. | Enough for vanilla terrain transform and section identity. Backend can mirror written uniform data or parse backend-owned uniform buffer contents. |

## Terrain Extraction Confidence

| Item | Status | Reason |
|---|---|---|
| Layer/classification | Strong | `ChunkSectionLayer` and `RenderPipeline.location` directly identify solid/cutout/translucent terrain. |
| Draw range | Strong | `RenderPass.Draw` carries `firstIndex`, `indexCount`, `baseVertex`, index type/buffer fallback. |
| Vertex/index buffers | Strong | `RenderPass.Draw` carries vertex buffer and optional custom index buffer; default sequential index buffer is supplied to `drawMultipleIndexed`. |
| Section origin | Strong | `Draw.uniformUploaderConsumer` binds `"ChunkSection"` to one `GpuBufferSlice`; `DynamicUniforms.ChunkSectionInfo` contains x/y/z. |
| Texture atlas/lightmap | Strong | `ChunkSectionsToRender.renderGroup` binds block atlas as `Sampler0` and lightmap as `Sampler2`. |
| Per-block identity | Not present | Official terrain draw path has baked mesh by section/layer, not block IDs. Needed only for Radiance extras such as custom material policies/emissive extraction if not derivable from textures. |

## Resolved Questions

| Question | Why It Matters | Next Files |
|---|---|---|
| Can `RenderPassBackend.drawMultipleIndexed` associate each draw with the correct `ChunkSectionInfo`? | Needed to build chunk scene packets without a `LevelRenderer` mixin. | Yes. `RenderPass.Draw` carries `uniformUploaderConsumer`; terrain draw records upload `"ChunkSection"` before each draw. Official GL/Vulkan backends execute that callback per draw. |
| Can backend read or mirror `DynamicUniforms.ChunkSectionInfo` contents without GPU readback? | Needed for section origin/transform. | Yes if Radiance owns the backend. `DynamicUniformStorage.writeUniform(s)` writes through `GpuBufferSlice.map(false, true)`. A Radiance `GpuBuffer.map` implementation can keep CPU shadow bytes for every mapped write. |
| Where do entities aggregate before `PreparedRenderType.drawFromBuffer`? | Needed for entity identity and previous transform. | `LevelExtractor -> EntityRenderDispatcher -> SubmitNodeCollection -> FeatureRenderDispatcher -> RenderTypeFeatureRenderer -> PreparedRenderType`. Semantic identity is strongest at extract/submit, final GPU coverage is strongest at backend draw. |
| How are particles/weather/text submitted in 26.2? | Needed for post/raster-only Radiance domains. | Particles/text/name tags go through submit nodes. Weather has a dedicated `WeatherRenderState`/`WeatherEffectRenderer`, but still draws through standard `RenderPass`. |

## Purpose-Oriented Semantic Map

This table is not limited to old Radiance hooks. It maps what Radiance wants to the narrowest 26.2 source that appears able to provide it.

| Goal | Best 26.2 Source | What You Get | Missing / Still Needs | Recommendation |
|---|---|---|---|---|
| Vanilla-equivalent final drawing | `GpuBackend`, `GpuDeviceBackend`, `CommandEncoderBackend`, `RenderPassBackend` | All official backend commands: buffers, textures, pipelines, render targets, uniforms, draw calls. | No high-level entity/block identity; no BLAS/TLAS; no LabPBR semantics. | Implement Radiance as a real backend first. This gives broadest future mod coverage for anything using Blaze3D. |
| Terrain scene packets | `ChunkSectionsToRender.renderGroup`, `RenderPass.Draw`, `"ChunkSection"` uniform | Layer, pipeline, atlas/lightmap, vertex/index buffers, draw range, section origin, visibility. | Per-block IDs are not in final terrain draw. PBR/emission must come from baked vertex data or texture/material metadata. | Use backend draw tracking plus CPU-shadowed `ChunkSectionInfo`. Avoid rebuilding vanilla chunks yourself unless custom PBR vertex baking is still required. |
| Entity/model/block-entity/item classification | `SubmitNodeCollection`, `ModelFeatureRenderer.Submit`, `BlockModelFeatureRenderer.Submit`, `ItemFeatureRenderer.Submit` | RenderType, pose, model/state object, light/overlay/tint, phase classification. | Final batched vertex buffer loses individual submit identity unless you track it before batching. | Add a small observer/wrapper around submit collection or feature preparation. Do not hook every renderer. |
| Entity temporal identity | `LevelExtractor.extractVisibleEntities`, `EntityRenderDispatcher.extractEntity/submit` | Entity type, position, render state, camera-relative pose. | `EntityRenderState` does not reliably carry original entity id/object identity. | Keep a side map from original entity to render state/submits. This is one of the small remaining mixins. |
| Dynamic transforms | `RenderType.prepare`, `RenderSystem.getDynamicUniforms().writeTransform`, `PreparedRenderType.dynamicTransforms` | ModelView and texture transform for non-terrain RenderType draws. | Backend draw only sees a UBO slice unless it mirrors uniform bytes. | Decode from Radiance CPU shadow of dynamic uniform buffers. |
| Pipeline/raster metadata | `RenderPipeline`, `RenderSetup`, `RenderType.prepare` | Pipeline location, vertex format, topology, cull, depth test/write, blend, output target, textures, lightmap/overlay use. | Some Radiance-specific geometry flags are policy decisions, not vanilla state. | Replace old `RenderLayer.MultiPhase` parsing with pipeline + setup state where possible. |
| Texture/material binding | `RenderPass.bindTexture`, `PreparedRenderType.textures`, `RenderSetup.prepareTextures`, backend texture creation/upload | Actual texture views/samplers used by vanilla; atlas/lightmap/overlay bindings. | LabPBR normal/specular/emission are not official vanilla semantics. | Backend handles base textures. Keep resource hooks for auxiliary PBR textures and emission tracking. |
| Particles | `ParticlesRenderState`, `QuadParticleRenderState`, `SubmitNodeCollection.submitQuadParticleGroup`, `QuadParticleFeatureRenderer` | Particle layer, texture atlas, per-particle position/UV/color/light before batching. | If path tracing wants individual particle policy, backend draw is too late. | Use submit/state hook only if particles need semantic treatment; otherwise backend draw is enough for vanilla-equivalent raster. |
| Weather | `WeatherRenderState`, `WeatherEffectRenderer.render` | Rain/snow column instances, intensity/radius, UV/light, weather pipelines. | It bypasses submit node phases. | Optional small state hook for semantic weather; backend RenderPass still covers final draw. |
| Text/name tags/UI-like labels | `SubmitNodeCollection.submitText/submitNameTag`, `TextFeatureRenderer`, `NameTagFeatureRenderer` | Explicit text/name-tag domains and transforms. | Backend draw only sees glyph buffers and textures. | Hook submit nodes if Radiance needs labels as classified post/overlay geometry. |
| Sky/sun/moon/cloud/world effects | `SkyRenderer`, `CloudRenderer`, render states/pipelines, global settings uniforms | Mostly standard pipeline draws and global uniforms. | Some Radiance world lighting inputs, e.g. sun direction, weather mood, may be easier from game state than draw commands. | Use backend for drawing; use a small world-uniform/game-state hook for lighting inputs if path tracing needs them. |
| Mod compatibility | Official `RenderType`, `SubmitNodeCollector`, `RenderPass`, resource APIs | Future mods using Mojang's Blaze3D path should naturally hit the backend. | Mods doing direct native/GL/Vulkan bypasses are outside this channel. | Target the official backend API plus submit observer. This is the widest stable channel available in 26.2. |

## Entity / Feature Path Findings

| Semantic | 26.2 Code Source | Details | Radiance Use |
|---|---|---|---|
| Visible entity list and identity source | `LevelExtractor.extractVisibleEntities` | Iterates `level.entitiesForRendering()`, computes partial tick, calls `EntityRenderDispatcher.extractEntity(entity, partialTick)`, stores `EntityRenderState` in `LevelRenderState.entityRenderStates`. | Stable high-level hook candidate for entity identity, current position, entity type, and temporal tracking. |
| Entity render state | `EntityRenderState.java` | Contains `entityType`, `x/y/z`, age, dimensions, light, outline, name tag data, shadow pieces, etc. It does not store original entity id by default. | Enough for vanilla rendering semantics. Radiance previous-transform/identity likely needs a small hook or side map from extraction. |
| Entity submit path | `EntityRenderDispatcher.submit` | Takes `EntityRenderState`, camera-relative x/y/z, `PoseStack`, `SubmitNodeCollector`; renderer emits submit nodes. | High-level point where pose and render state meet. Good semantic hook if backend draw layer is too late. |
| Unified submit interface | `OrderedSubmitNodeCollector` / `SubmitNodeCollection` / `SubmitNodeStorage` | Single interface for `submitModel`, `submitBlockModel`, `submitItem`, `submitCustomGeometry`, `submitText`, `submitNameTag`, `submitQuadParticleGroup`, shadows, leash, shape outline, moving blocks. | Very strong candidate for broad coverage with fewer hooks than per-renderer interception. |
| Model submit packet | `ModelFeatureRenderer.Submit` | Contains `RenderType`, `PoseStack.Pose`, `Model`, generic `state`, light/overlay/tint/sprite/crumbling overlay. For entities, `state` is often an `EntityRenderState`. | Good source for entity/model geometry domain, transform, render type, and identity via state. |
| Generic RenderType execution | `RenderTypeFeatureRenderer` | Builds `StagedVertexBuffer.Draw` per `PreparedRenderType`; then executes via `PreparedRenderType.drawFromBuffer`. | Low-level fallback for mod/custom geometry using `RenderType`. Gives pipeline/textures/buffers but less semantic identity. |
| Prepared feature draw | `PreparedRenderType.drawFromBuffer` | Binds pipeline, `DynamicTransforms`, vertex buffer, textures, index buffer, then `drawIndexed`. | Backend draw interception can cover final raster-equivalent rendering for many non-terrain features. |
| Feature phase classification | `SubmitNodeCollection` fields | Phases: solid, shadows, nameTags, seeThroughNameTags, texts, translucentBlocksAndItems, translucentModels, breakingOverlay, waterMask, afterTerrain, alwaysOnTop, outline, etc. | High-level geometry class/domain classification without parsing pipeline names only. |

## Entity / Feature Extraction Confidence

| Item | Status | Reason |
|---|---|---|
| Vanilla-equivalent model/entity rendering | Strong | `SubmitNodeCollection -> FeatureRenderDispatcher -> RenderTypeFeatureRenderer -> PreparedRenderType` centralizes most model/render type draws. |
| Entity transform | Medium-strong | Pose is available in submit nodes; backend sees `DynamicTransforms` matrix. Identity association is easier at submit/state layer than backend layer. |
| Entity identity / temporal history | Medium | `EntityRenderState` has type and position but not guaranteed original entity id. A side map at `LevelExtractor.extractEntity` or `EntityRenderDispatcher.submit` may be needed. |
| Feature classification | Strong at submit layer, medium at backend layer | Submit phases explicitly classify name tags/text/water mask/breaking/outline/etc.; backend draw layer can infer from pipeline but loses submit category. |
| Mod coverage | Good if mods use official `SubmitNodeCollector`/`RenderType`/`Blaze3D` paths | Raw custom native/GL bypasses still need special handling, but future Blaze3D API users should pass through these paths. |

## Particle / Weather / Text Findings

| Semantic | 26.2 Code Source | Details | Radiance Use |
|---|---|---|---|
| Particle extraction state | `ParticleEngine.extract`, `ParticlesRenderState`, `QuadParticleRenderState` | Particles are extracted into render state. `QuadParticleRenderState` groups by `SingleQuadParticle.Layer` and stores per-particle position, rotation, scale, UV, color, light. | High-level particle scene data is available before GPU draw. |
| Particle submit path | `ParticlesRenderState.submit`, `SubmitNodeCollection.submitQuadParticleGroup` | Particle groups enter the same feature dispatcher via `QuadParticleFeatureRenderer.Submit`. | Stable high-level hook candidate. |
| Particle draw path | `QuadParticleFeatureRenderer` | Builds staged vertex buffers per particle layer, sets layer pipeline, binds layer texture atlas as `Sampler0`, lightmap as `Sampler2`, then `drawIndexed`. | Backend path can reproduce vanilla; submit/state path preserves particle grouping. |
| Weather extraction state | `WeatherEffectRenderer.extractRenderState`, `WeatherRenderState` | Stores rain/snow column instances with x/z, bottom/top y, UV offset, light coords, intensity/radius. | High-level weather domain data is available in one state object. |
| Weather draw path | `WeatherEffectRenderer.render` | Independent from feature dispatcher, but still uses standard `RenderPass`: `WEATHER_DEPTH_WRITE` or `WEATHER_NO_DEPTH_WRITE`, `DynamicTransforms`, `Sampler0`, `Sampler2`, vertex/index buffers. | Backend draw path covers vanilla. Optional small hook on `WeatherRenderState` gives semantic rain/snow columns. |
| Text/name tags | `SubmitNodeCollection.submitText`, `submitNameTag`; `TextFeatureRenderer`, `NameTagFeatureRenderer` | Text and name tags are explicit submit phases and feature types. | Better hook point than parsing text pipelines at backend layer. |

## Current Best Narrow-Layer Split

| Layer | Use For | Why |
|---|---|---|
| `RenderPassBackend` / Radiance backend implementation | Universal draw/resource compatibility | Handles all official backend work and mod Blaze3D calls. |
| `ChunkSectionsToRender.renderGroup` / `RenderPass.Draw` dynamic `"ChunkSection"` uniform | Terrain scene extraction | Gives terrain layer, buffers, draw ranges, texture atlas, section origin with minimal high-level dependency. |
| `SubmitNodeCollection` / `OrderedSubmitNodeCollector` | Entity/block entity/item/text/name tag/particle high-level classification | Central official submit interface, much narrower than per-renderer mixins. |
| `EntityRenderDispatcher.extractEntity` or `submit` side map | Entity identity and previous transform | Needed because backend draw only knows matrices/buffers; Radiance temporal/TLAS wants stable identity. |

## Recommended Minimal Hook / Implementation Set

| Priority | Point | Type | Purpose | Expected Blast Radius |
|---|---|---|---|---|
| 1 | `GpuBackend` / `GpuDeviceBackend` implementation | New backend plus small backend selection patch/mixin | Make Radiance a first-class backend beside GL/Vulkan. Own buffers/textures/render passes. | Small but unavoidable because backend selection is currently enum/static. |
| 2 | `RenderPassBackend` state tracker | Backend implementation, not Minecraft mixin | Track current pipeline, textures, uniforms, vertex/index buffers, and draw ranges. Produce vanilla-equivalent draw packets/fallback raster. | Low once backend exists. |
| 3 | `RenderPass.Draw.uniformUploaderConsumer` handling for `"ChunkSection"` | Backend implementation | Associate terrain draws with section origin/visibility via dynamic uniform binding. | Low. This is already how official GL/Vulkan backends process terrain draws. |
| 4 | Dynamic uniform mirror | Backend implementation or tiny wrapper | Keep CPU-side copy of `DynamicUniforms.Transform` and `ChunkSectionInfo` slices, so backend can decode transforms without GPU readback. | Low-medium. Best done in Radiance backend buffer write/map paths. |
| 5 | `SubmitNodeCollection` / `OrderedSubmitNodeCollector` observation | Small targeted hook or wrapper | Preserve high-level domain/phase for model, block model, item, custom geometry, text, name tags, particles, outlines. | Medium but centralized. Much smaller than per-renderer mixins. |
| 6 | `EntityRenderDispatcher.extractEntity` or `submit` side map | Small targeted hook | Attach stable entity identity and previous transform/history to later submit/draw packets. | Medium. Needed for temporal/TLAS quality, not for plain vanilla-equivalent drawing. |
| 7 | Resource-pack/PBR material parser | Existing Radiance-style resource hook | LabPBR/PBR/emissive material data. | Separate from backend split. Still needed for Radiance-specific quality. |

## What Can Probably Disappear From Old Radiance Mixins

| Old Mixin Category | 26.2 Replacement |
|---|---|
| GL/window/backend creation interception | Implement `GpuBackend`/`GpuDeviceBackend`; patch backend enum/selection once. |
| `GlStateManager`, `RenderSystem`, shader program, buffer renderer low-level replacement | Backend `RenderPassBackend`, `CommandEncoderBackend`, `GpuBuffer`, `GpuTexture`, `RenderPipeline` handling. |
| Many texture upload GL-id tracking hooks | Backend texture objects and `RenderPass.bindTexture`; resource hooks only for semantic/PBR metadata. |
| Per-renderer entity capture for most model geometry | `SubmitNodeCollection.submitModel` and `ModelFeatureRenderer.Submit`. |
| Particle/weather/text post-capture spread across renderers | `ParticlesRenderState`/`submitQuadParticleGroup`, `WeatherRenderState`, `submitText`, `submitNameTag`. |

## Old Radiance Mixin Migration Table

This is based on `Radiance-custom/src/main/resources/radiance.mixins.json` and spot checks of representative old mixins. It is a migration judgment, not a compile-ready port plan.

| Old Mixin / Group | Old Purpose | 26.2 Narrow Replacement | Likely Status |
|---|---|---|---|
| `GLXMixins`, `WindowMixins`, `MinecraftClientMixins`, backend/window setup hooks | Own swapchain/context and suppress vanilla GL presentation. | Implement `GpuBackend`/`GpuDeviceBackend`; patch `PreferredGraphicsApi.getBackendsToTry()` or equivalent selection. | Replace with one backend-registration patch plus Radiance backend implementation. |
| `GlStateManagerMixins`, `GlUniformMixins`, `ShaderProgramMixins`, `CompiledShaderMixins`, `ShaderLoaderMixins` | Translate old GL state/shader/uniform flow into native renderer. | `RenderPipeline`, `RenderPassBackend.setPipeline/setUniform/bindTexture`, backend shader/pipeline creation. | Mostly removable for official draw path. |
| `BufferRendererMixins`, `BuiltBufferMixins`, `BufferProxy` upload interception | Convert built buffers to native vertex/index buffers and draw. | `GpuDeviceBackend.createBuffer`, `GpuBuffer.map`, `CommandEncoderBackend.writeToBuffer/copyToBuffer`, `RenderPassBackend.drawIndexed/drawMultipleIndexed`. | Replace with backend buffer ownership and CPU shadow. |
| `RenderSystemMixins` | Max texture size, swap suppression, global matrices/crosshair behavior. | Device limits through Radiance `GpuDeviceBackend`; presentation through backend surface/swapchain; dynamic uniforms through `RenderSystem.getDynamicUniforms()`. | Mostly replace. Some UI/crosshair policy may stay if Radiance intentionally renders UI differently. |
| `RenderLayerMixins`, `RenderPhaseMixins`, `RenderPhaseTargetMixins`, `RenderPhaseLightmapMixins`, old `Constants.RasterMetadata.write(RenderLayer...)` | Extract alpha/blend/cull/texture/lightmap/output target metadata from old RenderLayer phases. | `RenderPipeline` state, `RenderSetup` fields, `PreparedRenderType.textures`, `OutputTarget`, `RenderType.hasBlending()/isOutline()/affectsCrumbling()`. | Replace with new pipeline/setup metadata mapper. |
| `SectionBuilderMixins`, `ChunkBuilderMixins`, `ChunkBuilderBuiltChunkMixins`, `BuiltChunkStorageMixins`, `ClientChunkManagerMixins` | Rebuild chunks into Radiance/PBR vertex buffers and manage chunk readiness. | `ChunkSectionsToRender`, `CompiledSectionMesh`, backend-owned terrain buffers, `"ChunkSection"` dynamic uniform. | Large reduction possible. Keep custom chunk baking only if Radiance still requires PBR vertices at bake time rather than converting backend buffers. |
| `WorldRendererMixins` full render replacement | Cancel vanilla world render and manually queue entities, chunks, particles, weather, clouds, uniforms. | Let vanilla extraction/render issue Blaze3D commands; observe backend draw plus `SubmitNodeCollection`/state hooks; optional world lighting uniform hook. | Should shrink dramatically. Do not fully replace world render unless Radiance must skip vanilla passes entirely. |
| `EntityRenderDispatcherMixins`, `EntityRendererMixins`, `ItemRendererMixins`, `HeldItemRendererMixins`, `BannerBlockEntityRendererMixins`, `LightningEntityRendererMixins`, block entity hooks | Manually capture entity/block entity/item geometry and identity. | `LevelExtractor`, `EntityRenderDispatcher.submit`, `SubmitNodeCollection.submitModel/submitItem/submitBlockModel/submitCustomGeometry`, feature renderer submits. | Replace per-renderer hooks with one or two centralized submit/extract hooks. |
| `ParticleManagerMixins`, `ParticleMixins`, `BillboardParticleMixins` | Rebuild/capture particles. | `ParticlesRenderState`, `QuadParticleRenderState`, `SubmitNodeCollection.submitQuadParticleGroup`, `QuadParticleFeatureRenderer`. | Replace with particle state/submit hook only if semantic particles are needed. |
| `CloudRendererMixins`, weather/world-border logic in old `WorldRendererMixins` | Capture clouds/weather/world effects. | Backend RenderPass for final draw; `WeatherRenderState` for weather semantics; cloud/sky renderer state or world uniforms for lighting. | Partial replace. Keep small hooks only for Radiance lighting/environment inputs. |
| `DrawContextMixins`, `ScreenMixins`, `ScreenshotRecorderMixins`, options screen mixins | GUI, screenshot, options integration. | Backend final output/screenshot commands plus normal mod screens/options. | Not directly solved by scene extraction. Evaluate separately as UI/product features. |
| `AbstractTextureMixins`, `NativeImageMixins`, `TextureUtilMixins`, atlas source mixins, glyph/font texture mixins, `TextureManagerMixins`, `NamespaceResourceManagerMixins` | Track GL texture ids, upload base textures, find normal/specular/emissive auxiliary images. | Base texture creation/upload through backend texture objects; auxiliary PBR through resource-pack hooks such as `AuxiliaryTextures`/`EmissionRecorder`. | Split: GL-id tracking removable; PBR/resource semantic hooks still needed. |
| `BlockColorsMixins`, `FluidRendererMixins`, `BlockModelRendererMixins` | Affect baked block/fluid vertex/material data. | Vanilla terrain mesh buffers plus `ChunkSectionLayer`; resource/material parser; optional custom vertex conversion. | Depends on PBR strategy. If backend converts vanilla buffers, many can go; if per-vertex PBR is baked in Java, some replacement hook remains. |
| VR/debug mixins | VR input/debug overlay. | Outside Blaze3D backend semantic channel. | Keep or port separately. |

## Recommended 26.2 Integration Shape

| Component | Responsibility | Mixin Count Expectation |
|---|---|---|
| `RadianceBackend` implementing `GpuBackend` and backend interfaces | Surface/device/resource/render-pass implementation, buffer/texture ownership, final draw coverage. | One backend registration/selection patch; most work is normal Java classes, not mixins. |
| CPU-shadowed `RadianceGpuBuffer` / `RadianceGpuTexture` | Preserve buffer/texture data from `createBuffer(ByteBuffer)`, `GpuBuffer.map`, `writeToBuffer`, `copyToBuffer`, texture writes. | No Minecraft mixin if implemented as backend classes. |
| Backend draw recorder | Convert `RenderPassBackend` state into `SceneDrawPacket` candidates: pipeline, buffers, indices, textures, uniforms, draw ranges. | No Minecraft mixin if inside backend. |
| Terrain semantic decoder | Decode `"ChunkSection"` UBO slice and `RenderPipeline.location`/`ChunkSectionLayer` into chunk domain/class/origin. | No or tiny hook. Backend callback handling is enough if CPU shadow is complete. |
| Submit-node observer | Record high-level phase/domain for models, items, block models, text, name tags, particles, outlines, custom geometry. | Small centralized hook/wrapper around `SubmitNodeCollection` or `SubmitNodeStorage`/feature preparation. |
| Entity identity side map | Attach stable entity id/object identity and previous transform to later submit/draws. | Small hook at `LevelExtractor.extractVisibleEntities` or `EntityRenderDispatcher.extractEntity/submit`. |
| Material/PBR resource layer | Load LabPBR/specular/normal/emissive metadata and map it to backend texture/material ids. | Existing resource hooks likely still needed, but should be independent from renderer replacement. |

## Remaining Non-Negotiable Work

| Need | Why It Remains |
|---|---|
| Radiance-native buffer format conversion | Vanilla vertex formats are not Radiance `PBRVertex` / split position-material buffers. RadianceBackend must convert or mirror data into native-friendly buffers. |
| BLAS/TLAS build policy | Official Blaze3D does not expose ray tracing acceleration structures. Radiance still builds these from captured scene packets. |
| Entity temporal identity | Backend draw commands do not carry stable entity id. Need a high-level side map if temporal history matters. |
| PBR/LabPBR metadata | Not part of vanilla rendering semantics. Must come from resource-pack/material parsing. |
| Backend registration/selection | 26.2 has `PreferredGraphicsApi` enum and hardcoded GL/Vulkan construction. A small patch/mixin remains necessary unless Mojang adds plugin backend discovery. |
