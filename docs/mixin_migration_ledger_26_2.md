# Radiance 26.2 Mixin Migration Ledger

Scope: every entry from `src/main/resources/radiance.mixins.json` is accounted for.

Rule: migrated mixins are loader-neutral shims only. They may observe Minecraft state and forward arguments to `com.radiance.client.*`; they must not contain renderer business logic, native calls, resource policy, or mod-loader checks.

## Status Categories

| Status | Meaning |
|---|---|
| New backend covered | Replaced by a Radiance Blaze3D `GpuBackend` / `GpuDeviceBackend` / `RenderPassBackend` implementation. No Minecraft mixin should recreate this logic. |
| 26.2 hook covered | Replaced by a narrow 26.2 semantic hook, currently staged under `src/minecraft26_2/java/com/radiance/mixins/minecraft26_2`. |
| Loader adapter retained | Not part of Blaze3D scene extraction. Keep as loader-facing UI/input/debug integration, but isolate Fabric/NeoForge-specific registration outside mixins. |
| Resource/PBR retained | Still needed for LabPBR, emissive, auxiliary texture, atlas, glyph, or resource-pack semantics. Mixin remains narrow and forwards to client resource services. |
| Deprecated | Old hook has no 26.2 responsibility after backend and semantic hooks are in place. Do not port unless a new verified requirement appears. |

## New 26.2 Mixin Surface

| New file | Target | Purpose |
|---|---|---|
| `src/minecraft26_2/resources/radiance-26_2.mixins.json` | 26.2-only mixin config | Keeps 26.2 migration separate from current 1.21.4 source set until the build is switched. |
| `minecraft26_2/blaze3d/PreferredGraphicsApiMixin` | `net.minecraft.client.PreferredGraphicsApi#getBackendsToTry` | Adds Radiance backend candidate without old GL/window suppression logic. |
| `minecraft26_2/extract/EntityRenderDispatcherMixin` | `EntityRenderDispatcher#extractEntity`, `#submit` | Captures frame-level entity identity/state records and submit lifetime. |
| `minecraft26_2/extract/LevelRendererMixin` | `LevelRenderer#render` | Captures frame-level world, camera, sky, cloud, fog, and weather state for shared_scene consumers. |
| `minecraft26_2/submit/SubmitNodeCollectionMixin` | `SubmitNodeCollection` submit methods | Central semantic observation for models, block models, items, text, name tags, particles, and custom geometry. |
| `minecraft26_2/submit/SimpleFeatureRenderPhaseMixin` / `TranslucentFeatureRenderPhaseMixin` | feature phase `submit` | Attaches active submit/entity tags to concrete `SubmitNode` instances during collection. |
| `minecraft26_2/submit/RenderTypeFeatureRendererMixin` / `QuadParticleFeatureRendererMixin` | feature renderer `executeGroup` | Scopes captured submit-node tags around final backend draw calls. |
| `minecraft26_2/resources/TextureManagerMixin` | `TextureManager#register`, `#release`, `#close` | Narrow resource/PBR texture registration and lifetime observer. |
| `minecraft26_2/resources/DirectoryListerMixin` | `atlas.sources.DirectoryLister#run` | Filters LabPBR/auxiliary sidecar sprites through client resource policy before atlas stitching. |
| `minecraft26_2/resources/SingleFileMixin` | `atlas.sources.SingleFile#run` | Filters single-file sidecar sprites through client resource policy. |
| `minecraft26_2/resources/PalettedPermutationsMixin` | `atlas.sources.PalettedPermutations#run` | Filters generated sidecar permutations and discards skipped loaders. |
| `minecraft26_2/resources/UnstitcherMixin` | `atlas.sources.Unstitcher#run` | Filters unstitch sidecar regions and discards skipped loaders. |
| `minecraft26_2/resources/TextureAtlasMixin` | `TextureAtlas#upload` | Records atlas/sprite uv and material mapping after Mojang stitching/upload. |
| `minecraft26_2/resources/TextureContentsMixin` | `TextureContents#load` | Records decoded texture image metadata and material sidecar identity without upload interception. |
| `minecraft26_2/resources/SpriteContentsMixin` | `SpriteContents` constructor and `increaseMipLevel` | Records sprite source/frame/mip/animation metadata without propagating old GL target ids. |
| `minecraft26_2/resources/ReloadableResourceManagerMixin` | `ReloadableResourceManager#createReload` | Clears client resource/material/atlas caches before resource packs reload. |
| `minecraft26_2/resources/FontTextureMixin` | `FontTexture#add` | Records glyph atlas insert metadata after 26.2 backend texture upload/copy handles the pixels. |

Client-side receivers:

| Client file | Responsibility |
|---|---|
| `RadianceBlaze3DHooks` | Public shim API called by mixins only. |
| `RadianceBackendSelector` / `RadianceBackendFactory` | Backend candidate insertion; actual backend implementation still pending. |
| `RadianceSceneObserver` | Entity identity, temporal association, and later draw correlation. |
| `RadianceSubmitObserver` | Submit-node semantic classification. |
| `RadianceResourceObserver` | Texture/resource registration handoff. |

## Full Old Mixin Ledger

| Old mixin | Status | 26.2 replacement / confirmation |
|---|---|---|
| `vanilla_resource_tracker.AbstractTextureMixins` | Resource/PBR retained | Covered by `TextureManagerMixin#register/#release/#close` and backend texture close ownership. No GL id logic or dedicated `AbstractTexture` hook is needed. |
| `vanilla_resource_tracker.BitmapFontGlyphMixins` | Resource/PBR retained | 26.2 glyph pixels are uploaded by `GlyphBitmap#upload(..., GpuTexture)` through the backend; metadata is covered by `FontTextureMixin`. No overwrite port. |
| `vanilla_resource_tracker.BuiltinEmptyGlyphMixins` | Resource/PBR retained | 26.2 special glyphs bake through `FontTexture#add`; metadata is covered by `FontTextureMixin`. No overwrite port. |
| `vanilla_resource_tracker.FontStorageMixins` | Resource/PBR retained | 26.2 `FontSet`/`GlyphStitcher` uses `FontTexture#add`; metadata is covered by `FontTextureMixin`. No redirect/overwrite port. |
| `vanilla_resource_tracker.GlyphAtlasTextureMixins` | Resource/PBR retained | Replaced by 26.2 `FontTextureMixin`; backend covers actual glyph texture writes/copies. |
| `vanilla_resource_tracker.NamespaceResourceManagerMixins` | Resource/PBR retained | Keep for auxiliary resource lookup (`_n`, `_s`, emissive, flags). Loader-neutral resource service only. |
| `vanilla_resource_tracker.NativeImageBackedTextureMixins` | Resource/PBR retained | Covered by `TextureManagerMixin#register` dynamic texture inspection and backend texture labels; no GL/native upload port. |
| `vanilla_resource_tracker.NativeImageMixins` | Resource/PBR retained | Covered by `TextureContentsMixin#load` for decoded resource image metadata and sidecar correlation; backend covers GPU upload. |
| `vanilla_resource_tracker.OverlayTextureMixins` | New backend covered | Overlay texture binding is visible through `RenderPass.bindTexture`; retain only if resource observer needs a named texture id. |
| `vanilla_resource_tracker.ReloadableTextureMixins` | Resource/PBR retained | Covered by `TextureContents#load`, `ReloadableTexture#doLoad` backend labels, `TextureManager#release/#close`, and reload invalidation; no upload hook needed. |
| `vanilla_resource_tracker.SpriteAtlasTextureMixins` | Resource/PBR retained | Keep for atlas sprite to material mapping. |
| `vanilla_resource_tracker.SpriteContentsMixins` | Resource/PBR retained | Ported as `SpriteContentsMixin`; old GL target-id propagation is obsolete because 26.2 uploads through backend textures. |
| `vanilla_resource_tracker.SpriteMixins` | Resource/PBR retained | Covered by `TextureAtlasMixin#upload`; final sprite uv/material id association is recorded from `TextureAtlasSprite`. |
| `vanilla_resource_tracker.TextureManagerMixins` | Resource/PBR retained | Replaced by 26.2 `TextureManagerMixin#register` forwarding to `RadianceResourceObserver`. |
| `vanilla_resource_tracker.TextureUtilMixins` | Resource/PBR retained | Keep only for raw resource byte/image decode metadata; not for GPU upload. |
| `vanilla_resource_tracker.TtfGlyphMixins` | Resource/PBR retained | 26.2 TrueType glyphs are `GlyphBitmap` uploads to backend textures; metadata is covered by `FontTextureMixin`. No overwrite port. |
| `vanilla_resource_tracker.UnicodeTextureGlyphMixins` | Resource/PBR retained | 26.2 Unihex glyphs upload via backend command encoder; metadata is covered by `FontTextureMixin`. No native upload port. |
| `vulkan_options.GameOptionsScreenMixins` | Loader adapter retained | Port separately as options UI integration; no Blaze3D backend logic. |
| `vulkan_options.VideoOptionsScreenMixins` | Loader adapter retained | Port separately as options UI integration; expose backend choice through client service. |
| `vulkan_render_integration.AbstractTextureMixins` | New backend covered | GPU texture ids/uploads are owned by Radiance backend; logical texture lifetime is handled by `TextureManagerMixin#release/#close`. |
| `vulkan_render_integration.BannerBlockEntityRendererMixins` | 26.2 hook covered | Replaced by `SubmitNodeCollection#submitModel` / block-entity submit state coverage. |
| `vulkan_render_integration.BillboardParticleMixins` | 26.2 hook covered | Replaced by `SubmitNodeCollection#submitQuadParticleGroup` and backend draw coverage. |
| `vulkan_render_integration.BlockColorsMixins` | Resource/PBR retained | Keep only if block color affects emissive/material policy; otherwise terrain backend buffers cover final color. |
| `vulkan_render_integration.BlockModelRendererMixins` | 26.2 hook covered | Replaced by terrain backend path and `submitBlockModel` for non-terrain block models. |
| `vulkan_render_integration.BufferRendererMixins` | New backend covered | Replaced by `GpuBuffer`, `CommandEncoderBackend`, and `RenderPassBackend` ownership. |
| `vulkan_render_integration.BuiltBufferMixins` | New backend covered | Replaced by backend buffer creation/copy/map CPU shadowing. |
| `vulkan_render_integration.BuiltChunkStorageMixins` | New backend covered | Terrain visibility/draw data comes from 26.2 chunk render path and backend draw records. |
| `vulkan_render_integration.ChunkBuilderBuiltChunkMixins` | New backend covered | Replaced by `ChunkSectionsToRender` draw metadata plus backend terrain buffer ownership. |
| `vulkan_render_integration.ChunkBuilderMixins` | New backend covered | Replaced by official section mesh build/upload path; no custom Java chunk rebuild in mixin. |
| `vulkan_render_integration.ClientChunkManagerMixins` | New backend covered | Chunk load/unload is derived from 26.2 level extraction and backend resource lifetime. |
| `vulkan_render_integration.CloudRendererMixins` | New backend covered | Cloud draws pass through Blaze3D render passes; cloud color/height/range/status are exported by `LevelRendererMixin` world-state snapshot. |
| `vulkan_render_integration.CompiledShaderMixins` | New backend covered | Replaced by `RenderPipeline`/backend shader compilation. |
| `vulkan_render_integration.DirectoryAtlasSourceMixins` | Resource/PBR retained | Ported as 26.2 `DirectoryListerMixin`; sidecar skip policy lives in client resource service. |
| `vulkan_render_integration.DrawContextMixins` | Loader adapter retained | GUI integration/screenshot policy only; world scene extraction is backend/submit based. |
| `vulkan_render_integration.EntityRenderDispatcherMixins` | 26.2 hook covered | Replaced by 26.2 `EntityRenderDispatcherMixin` extraction/submit observer plus `RadianceEntityRegistry` frame snapshot export. |
| `vulkan_render_integration.EntityRendererMixins` | 26.2 hook covered | Replaced by centralized `SubmitNodeCollection#submitModel` and entity state side map. |
| `vulkan_render_integration.FluidRendererMixins` | New backend covered | Terrain/fluid geometry is covered by chunk section layers and backend draw packets. |
| `vulkan_render_integration.GameRendererMixins` | New backend covered | Global render state is captured through backend state plus `LevelRendererMixin` camera/fog/sky/world snapshot; no render-loop replacement. |
| `vulkan_render_integration.GlStateManagerMixins` | New backend covered | Deprecated GL state interception; Radiance backend owns state translation. |
| `vulkan_render_integration.GlUniformMixins` | New backend covered | Replaced by `RenderPassBackend#setUniform` and CPU-shadowed `GpuBufferSlice`. |
| `vulkan_render_integration.GLXMixins` | New backend covered | Replaced by backend selection mixin plus `GpuBackend#setWindowHints`. |
| `vulkan_render_integration.HeldItemRendererMixins` | 26.2 hook covered | Replaced by item/model submit hooks and backend draw coverage. |
| `vulkan_render_integration.ItemRendererMixins` | 26.2 hook covered | Replaced by `SubmitNodeCollection#submitItem`. |
| `vulkan_render_integration.LightmapTextureManagerMixins` | New backend covered | Lightmap texture binding is visible through backend texture binding; world-state snapshot exports sky/weather context. Dedicated lightmap value extraction remains native/material ABI work if required. |
| `vulkan_render_integration.LightningEntityRendererMixins` | 26.2 hook covered | Replaced by entity submit/custom geometry/model submit observation plus backend draw. |
| `vulkan_render_integration.MinecraftClientMixins` | New backend covered | Old render loop/backend setup replacement is replaced by official backend selection and device creation path. |
| `vulkan_render_integration.MipmapHelperMixins` | Resource/PBR retained | Covered by `SpriteContentsMixin#increaseMipLevel`; old NativeImage identifier propagation is obsolete in 26.2. |
| `vulkan_render_integration.NativeImageMixins` | Resource/PBR retained | Covered by 26.2 `TextureContentsMixin` and dynamic texture registration; no GL upload. |
| `vulkan_render_integration.OverlayTextureMixins` | New backend covered | Overlay binding is backend-visible; do not port GL id access. |
| `vulkan_render_integration.PalettedPermutationsAtlasSourceMixins` | Resource/PBR retained | Ported as 26.2 `PalettedPermutationsMixin`; skipped generated loaders are discarded. |
| `vulkan_render_integration.ParticleManagerMixins` | 26.2 hook covered | Replaced by particle extraction/render state and `submitQuadParticleGroup`. |
| `vulkan_render_integration.ParticleMixins` | 26.2 hook covered | Replaced by `QuadParticleRenderState` group hook; per-particle policy belongs in client observer. |
| `vulkan_render_integration.ReloadableResourceManagerImplMixins` | Resource/PBR retained | Ported as 26.2 `ReloadableResourceManagerMixin`; clears material/atlas caches before resource reload. |
| `vulkan_render_integration.RenderLayerMixins` | New backend covered | Replaced by `RenderPipeline`, `PreparedRenderType`, and submit phase metadata. |
| `vulkan_render_integration.RenderPhaseLightmapMixins` | New backend covered | Replaced by pipeline/setup and backend texture binding state. |
| `vulkan_render_integration.RenderPhaseMixins` | New backend covered | Replaced by `RenderPipeline`/`RenderType.prepare`/submit phase metadata. |
| `vulkan_render_integration.RenderPhaseTargetMixins` | New backend covered | Replaced by `RenderPassDescriptor` output targets and pipeline color target state. |
| `vulkan_render_integration.RenderSystemMixins` | New backend covered | Dynamic uniforms/device limits/presentation move to backend implementation. |
| `vulkan_render_integration.ScreenMixins` | Loader adapter retained | UI integration only; keep loader-neutral and outside world rendering path. |
| `vulkan_render_integration.ScreenshotRecorderMixins` | Loader adapter retained | Screenshot/output integration; should call client output service, not renderer logic. |
| `vulkan_render_integration.SectionBuilderMixins` | New backend covered | Replaced by official section mesh path and backend terrain buffer conversion. |
| `vulkan_render_integration.ShaderLoaderMixins` | New backend covered | Replaced by backend shader/pipeline compilation and `ShaderSource`. |
| `vulkan_render_integration.ShaderProgramMixins` | New backend covered | Replaced by `RenderPipeline` and backend pipeline objects. |
| `vulkan_render_integration.SingleAtlasSourceMixins` | Resource/PBR retained | Ported as 26.2 `SingleFileMixin`; sidecar skip policy lives in client resource service. |
| `vulkan_render_integration.TextureUtilMixins` | Resource/PBR retained | Covered by `TextureContents#load`, dynamic texture registration, backend texture write/copy/readback metadata, and texture lifetime invalidation. GPU work belongs to backend. |
| `vulkan_render_integration.UnstitchAtlasSourceMixins` | Resource/PBR retained | Ported as 26.2 `UnstitcherMixin`; skipped generated loaders are discarded. |
| `vulkan_render_integration.VideoWarningManagerWarningPatternLoaderMixins` | Deprecated | Old GPU warning suppression is not part of Radiance scene/backend migration. Revisit only for product UX. |
| `vulkan_render_integration.WindowMixins` | New backend covered | Replaced by `GpuBackend#setWindowHints`, `GpuSurfaceBackend`, and backend device creation. |
| `vulkan_render_integration.WorldRendererMixins` | 26.2 hook covered | Old full world render replacement is removed; covered by backend draw interception, entity submit hooks, resource hooks, and `LevelRendererMixin` world-state observer. |
| `vr_input.VRMouseSyncMixin` | Loader adapter retained | Keep as VR input adapter; no direct mod-loader calls inside mixin. |
| `vr_debug.DebugHudVRMixin` | Loader adapter retained | Keep as debug UI adapter; no renderer/backend logic. |

## Implementation Log

| Date | Change |
|---|---|
| 2026-06-21 | Created 26.2-only mixin source/resource tree under `src/minecraft26_2`. |
| 2026-06-21 | Added backend selection shim for `PreferredGraphicsApi#getBackendsToTry`. |
| 2026-06-21 | Added entity extraction/submit observer mixin. |
| 2026-06-21 | Added centralized `SubmitNodeCollection` observer mixin for model, block model, item, text, name tag, particle, and custom geometry submits. |
| 2026-06-21 | Added narrow `TextureManager#register` resource observer mixin. |
| 2026-06-21 | Established this ledger and classified every old mixin entry. |
| 2026-06-22 | Ported atlas source sidecar filtering for `DirectoryLister`, `SingleFile`, `PalettedPermutations`, and `Unstitcher`; added `TextureAtlas#upload` atlas/sprite observer. |
| 2026-06-22 | Ported resource reload invalidation to `ReloadableResourceManager#createReload`; client resource service clears material and atlas registries. |
| 2026-06-22 | Ported glyph atlas metadata observation to `FontTexture#add`; old glyph bake overwrites are confirmed unnecessary because 26.2 glyph uploads use Blaze3D backend textures. |
| 2026-06-22 | Added Java native-bridge shared-scene draw snapshots with compact view masks; this keeps migrated mixins free of backend/native routing logic. |
| 2026-06-22 | Ported decoded texture image metadata via `TextureContents#load` and dynamic texture registration; filtered atlas sidecars are now recorded as material candidates. |
| 2026-06-22 | Ported sprite contents metadata via `SpriteContents` constructor and `increaseMipLevel`; old GL target-id propagation is confirmed obsolete under Blaze3D texture writes. |
| 2026-06-22 | Added submit-node semantic scopes so non-terrain feature renderer draws retain model, entity, item, text, particle, and custom-geometry tags through 26.2 prepare/execute splitting. |
| 2026-06-22 | Added structured texture operation capture in the backend layer; retained texture mixins still do not contain upload or native logic. |
| 2026-06-22 | Extended Java native-bridge frame snapshots with resource tables so draw handles resolve without adding logic to mixins. |
| 2026-06-22 | Extended `TextureManagerMixin` to cover texture release and manager close; resource/PBR lifetime invalidation stays in client registries, not in mixin logic. |
| 2026-06-22 | Implemented frame-level entity identity/state capture through `EntityRenderDispatcherMixin` and `RadianceEntityRegistry`; exact per-draw entity handle linkage remains tracked in the migration plan. |
| 2026-06-22 | Added client-layer semantic scope cleanup after backend submit and resource reload/texture-manager close; submit mixins remain narrow observers and do not contain cleanup policy. |
| 2026-06-22 | Added `LevelRendererMixin` world-state observer and native snapshot export for camera, fog, sky, cloud, and weather state. |

## Next Required Work

| Work item | Owner layer |
|---|---|
| Implement real `RadianceBackendFactory.tryCreateBackend()` and backend classes. | `client.render.blaze3d` / native bridge |
| Implement CPU-shadowed buffers/textures and `RenderPassBackend` draw recorder. | New backend |
| Decode `ChunkSection` dynamic uniform slices for terrain origin/visibility. | New backend |
| Move observer no-op bodies into real client services. | `client` layer |
| Port retained resource/PBR hooks to 26.2 names with no GL id assumptions. | Resource/PBR layer |
| Add Fabric/NeoForge-specific registration wrappers outside mixins. | Loader adapter layer |
