# Radiance 26.2 Migration Plan

Goal: make Radiance a first-class Blaze3D backend with parity against the vanilla
GL/Vulkan backends, while preserving vanilla/mod compatibility and feeding the new
shared scene runtime.

This document is the live migration log. Every implemented step must add a log row
immediately. Temporary omissions are recorded in the omissions table and closed
when a later step resolves them.

## Ground Rules

- Do not port old renderer-replacement mixin logic into new mixins.
- New mixins are loader-neutral shims only. They forward state into `client`
  services and contain no renderer policy, native calls, or mod-loader checks.
- Replay tooling is out of scope for this migration.
- Radiance backend must implement the same Blaze3D backend surface expected from
  GL/Vulkan: window hints, surface/device creation, buffers, textures, samplers,
  render targets, command encoder, render pass, pipeline compilation, queries,
  timestamps, debug messages, and device info.
- Scene classification must be done before shared scene view construction.
- Shared scene views must be independent. A module requests the view it needs; a
  raster-only path must not force TLAS/BLAS construction.

## 26.2 Blaze3D Semantic Model

26.2 separates high-level render extraction/submission from low-level backend
execution:

| Layer | Stable semantic value for Radiance |
|---|---|
| `GpuBackend` | Backend registration/selection and device creation beside GL/Vulkan. |
| `GpuDeviceBackend` | Owns buffers, textures, samplers, surfaces, pipelines, query pools, and command encoders. |
| `CommandEncoderBackend` | Copies, clears, texture writes, render pass creation, fences, and presentation-facing work. |
| `RenderPassBackend` | Final universal draw stream: pipeline, texture bindings, uniforms, vertex/index buffers, draw ranges. |
| `RenderPipeline` | Pipeline id, shaders, vertex format, topology, blend/depth/cull/color target metadata. |
| `RenderPass.Draw<T>` | Terrain and batched draw metadata, including per-draw uniform upload callback. |
| `DynamicUniforms` | CPU-written transform and chunk-section uniform slices that Radiance can mirror in backend buffers. |
| `SubmitNodeCollection` | High-level domains for models, items, block models, text, name tags, particles, outlines, water masks, custom geometry. |
| `EntityRenderDispatcher` | Entity state identity and submit lifetime association. |
| Resource/texture managers | Resource id, atlas, sprite, LabPBR/emissive sidecar, and reload lifecycle metadata. |

The backend is the compatibility baseline. Submit/resource hooks add semantic
classification; they must not become an alternate renderer.

## Shared Scene And View Filtering Design

The new shared scene is the bridge between Java Blaze3D capture and native render
modules. The important design change is that shared scene stores canonical records
and exposes filtered views to modules.

Required behavior:

| Consumer | Required view | Must include | Must exclude |
|---|---|---|---|
| PT/path tracing | acceleration view | nearby vanilla chunks, nearby entities, explicitly whitelisted geometry | Distant Horizons LOD, far raster-only mod geometry, UI/post/debug unless explicitly whitelisted |
| Raster GBuffer | geometry view | vanilla chunks/entities and compatible raster LOD such as Distant Horizons terrain | acceleration-only data is optional; no forced BLAS/TLAS |
| Raster ray-query pass | geometry view + acceleration view | same acceleration whitelist as PT | LOD excluded from acceleration even if visible in GBuffer |
| Post processing | post view | post/overlay/screen-space records and resources | world acceleration records |

Classification is tag-based, but the JSON has two distinct responsibilities:

1. Caller/call-rule detection and tag injection.
   - Rules identify a caller source such as Minecraft, Sodium, Distant Horizons,
     or an unknown mod.
   - Rules can narrow by class, method, stack frame, pipeline id, shader id,
     resource label, render target, vertex format, draw shape, or submit phase.
   - Matching rules produce compact tags that are injected into actual captured
     calls and stable backend objects.
   - This is the semantic discovery layer.

2. View tag lists for backend/shared-scene filtering.
   - View configs define include/exclude tag logic for each consumer view.
   - These compiled view rules are passed to the backend/native shared scene.
   - The backend filters captured records using compact numeric tag sets, not
     repeated string matching.
   - This is the consumption/filtering layer.

The design must allow conservative raster compatibility. Unknown draws should be
renderable by raster unless explicitly excluded, while acceleration/PT should be
strict and include only stable whitelisted tags.

High-performance rule model:

| Stage | Matching cost target | Notes |
|---|---|---|
| Rule load | String parsing is fine | Parse JSON, validate tag names, build integer ids. |
| Stable object creation | Moderate matching is acceptable | Classify pipelines, shaders, buffers, textures, render targets, resource ids, and caller method hints once. |
| Draw capture | Cheap combining only | Combine cached object tags plus current call-scope tags and submit/context tags. |
| View construction | Numeric filtering only | Include/exclude by bit sets or compact tag id arrays. |

Caller rule JSON shape is not final, but it should be able to express this:

```json
{
  "callerRules": [
    {
      "id": "minecraft.chunk_sections",
      "match": {
        "callerClass": "net.minecraft.client.renderer.ChunkSectionsToRender",
        "callerMethod": "renderGroup",
        "pipeline": [
          "minecraft:pipeline/solid_terrain",
          "minecraft:pipeline/cutout_terrain",
          "minecraft:pipeline/translucent_terrain"
        ]
      },
      "injectTags": ["minecraft", "terrain", "chunk_section"]
    },
    {
      "id": "distant_horizons.lod",
      "match": {
        "modId": "distanthorizons",
        "anyCallerPackage": "com.seibel.distanthorizons",
        "resourceOrPipelineContains": ["lod", "distant", "horizon"]
      },
      "injectTags": ["distant_horizons", "terrain", "lod"]
    }
  ],
  "views": {
    "pt_acceleration": {
      "includeAny": [["minecraft", "terrain"], ["minecraft", "entity"]],
      "excludeAny": ["lod", "ui", "post", "debug"]
    },
    "raster_geometry": {
      "includeFallback": true,
      "excludeAny": ["post"]
    }
  }
}
```

Rules need to be robust against mods using plain Blaze3D without high-level submit
hooks. Backend classification therefore uses multiple independent hints:

| Hint source | Why it matters |
|---|---|
| Current call scope / stack caller | Identifies mod or vanilla subsystem when object labels are generic. |
| `RenderPipeline.location` | Stable vanilla semantic signal and common mod label point. |
| Shader ids/source ids | Useful for Distant Horizons/Sodium/custom renderers. |
| Buffer/texture labels and resource ids | Useful for LOD buffers, atlases, lightmaps, post targets, glyphs. |
| Submit phase | Strong vanilla/entity/text/particle classification. |
| Dynamic uniform names | `"ChunkSection"` identifies vanilla chunk-section terrain. |
| Render target/pass descriptor | Separates world, UI, post, shadow, weather, and offscreen passes. |
| Vertex format/topology/index type | Useful fallback for unknown geometry class and compatibility heuristics. |

Example required distinction:

| Source | Tags | Raster geometry view | Acceleration/PT view |
|---|---|---|---|
| Vanilla chunk section | `minecraft`, `terrain`, `chunk_section`, layer tag | Include | Include when near/within PT budget |
| Vanilla entity model | `minecraft`, `entity`, render type tag | Include | Include when policy allows |
| Distant Horizons LOD terrain | `distant_horizons`, `terrain`, `lod` | Include | Exclude by default |
| Unknown mod Blaze3D draw | `unknown_mod`, pipeline/resource hints | Conservative forward/raster include | Exclude by default |

## Migration Order

1. Documentation and ledger
   - Keep `mixin_migration_ledger_26_2.md` exhaustive.
   - Record shared scene/tag design in this plan.

2. Backend parity investigation
   - Compare `GpuBackend`, `GpuDeviceBackend`, `CommandEncoderBackend`,
     `RenderPassBackend`, `GpuSurfaceBackend`, `GpuBuffer`, `GpuTexture`,
     and query/device info behavior across GL/Vulkan.
   - Produce a parity checklist before implementing the backend.

3. Distant Horizons classification investigation
   - Find its 26.2 Blaze3D draw path.
   - Identify stable caller/pipeline/resource names usable for tag rules.
   - Add default policy that includes DH LOD in raster geometry and excludes it
     from acceleration/PT.

4. Java scene policy foundation
   - Add JSON schema/resource defaults for caller/call rules and view tag rules.
   - Add compiled policy objects in `client` layer.
   - Add observer APIs to receive source hints and return compact classification.
   - Add call-scope/tag injection plumbing so rules attach tags to actual
     captured calls, not only to final views.

5. Backend capture foundation
   - Implement Radiance backend classes or a temporary backend wrapper scaffold.
   - Mirror buffers/textures/uniform slices on CPU.
   - Record render pass state and draw packets with policy tags.

6. Terrain semantics
   - Decode `ChunkSection` dynamic uniform slices.
   - Map terrain pipeline/layer to chunk tags and raster/PT policies.

7. Submit semantics
   - Route `SubmitNodeCollection` and `EntityRenderDispatcher` observations into
     scene classification side maps.
   - Preserve entity identity/previous transform association.

8. Resource/PBR migration
   - Port retained resource hooks to 26.2 names.
   - Remove GL id assumptions.
   - Attach auxiliary material metadata to backend texture/material ids.

9. Loader adapters
   - Keep UI/input/debug/options loader-specific registration outside mixins.
   - Do not block backend parity on replay tooling.

10. Backend parity verification
   - Compare Radiance backend method coverage against GL/Vulkan implementations.
   - Record missing behavior and close omissions.

## Implementation Log

| Date | Step | Implementation | Verification |
|---|---|---|---|
| 2026-06-22 | Plan/shared scene design | Created this migration plan and recorded tag/view filtering requirements from `radiance_shared_scene_design.md`. | Manual read-through of shared scene design, 26.2 semantic mapping, and current mixin ledger. |
| 2026-06-22 | Caller rules/view rules clarification | Updated the plan to split JSON into caller/call-rule tag injection and backend view tag-list filtering. Added performance targets and robust hint sources. | Aligned with user clarification before implementation. |
| 2026-06-22 | Backend parity checklist | Added `radiance_26_2_backend_parity.md` documenting the required Blaze3D backend surface, old proxy retirement reason, exact feature-bit requirements, resource/copy coverage, and draw path coverage. | Compared 26.2 GL/Vulkan backend interfaces and implementations from `26.2_decompiled_java`. |
| 2026-06-22 | Classification investigation | Added `radiance_26_2_classification_investigation.md` with vanilla, Distant Horizons, and Sodium signals plus initial view policy. | Read DH Blaze3D renderers/wrappers and Sodium chunk renderer/mixins/draw backends. |
| 2026-06-22 | Scene policy foundation | Added loader-neutral policy classes under `client.render.blaze3d.policy` plus default JSON policy. Submit observer now enters the client policy layer by submit phase. | Static review only; `src/minecraft26_2` is not wired into Gradle yet. |
| 2026-06-22 | Backend capture model | Added `client.render.blaze3d.capture` records and registry for buffers, textures, texture views, pipelines, render passes, draw packets, CPU-shadowed buffer writes, and native shared-scene bridge placeholder. | Static review; this is the backend-facing packet layer that will replace old `ChunkProxy`/`EntityProxy` direct mesh rebuild packets. |
| 2026-06-22 | Render pass capture recorder | Added `RadianceRenderPassCapture`, covering indexed/direct/multi/indirect/`drawMultipleIndexed` paths and dynamic uniform tag combination. | Static review against 26.2 `RenderPassBackend` draw entry points. |
| 2026-06-22 | Resource/material registry foundation | Added 26.2 material registry keyed by resource id/backend texture handle for base/specular/normal/flag textures. `RadianceResourceObserver` now records texture registration without GL ids. | Static review against old `TextureTracker`/`AuxiliaryTextures`; upload/atlas hooks still deferred. |
| 2026-06-22 | Resource/PBR port plan | Added `radiance_26_2_resource_pbr_plan.md` mapping retained resource mixins to 26.2 texture/atlas/glyph targets and GL-id-free responsibilities. | Compared old texture tracker/auxiliary code with 26.2 texture package names. |
| 2026-06-22 | Radiance backend skeleton | Added opt-in `RadianceGpuBackend` plus `GpuDeviceBackend`, surface, command encoder, render pass, buffer, texture, texture view, sampler, query pool, fence, transient memory, and compiled-pipeline skeletons. `RadianceBackendFactory` now returns the backend only when `-Dradiance.blaze3d.backend=true` is set. | Static review against 26.2 backend interfaces; no loader API references found under `src/minecraft26_2`. Native present remains explicit unsupported. |
| 2026-06-22 | Backend capture integration | Wired buffer creation/writes/copies, texture creation/write markers, pipeline precompile/cache, render pass creation, all render pass draw calls, timestamps, fences, and command submit packet draining into `RadianceCaptureRegistry`/`RadianceNativeSceneBridge`. | Static review against `CommandEncoderBackend`, `RenderPassBackend`, `GpuBuffer`, `GpuTexture`, `GpuSurfaceBackend`, and `TransientMemory` signatures. |
| 2026-06-22 | Atlas/PBR source filters | Ported retained atlas-source mixins to 26.2 `DirectoryLister`, `SingleFile`, `PalettedPermutations`, and `Unstitcher` shims. Added `TextureAtlas#upload` observer and atlas sprite registry. | Static review against 26.2 `SpriteSource.Output#add` call sites and `TextureAtlas#upload(SpriteLoader.Preparations)`. |
| 2026-06-22 | Resource reload invalidation | Added 26.2 `ReloadableResourceManager#createReload` observer that clears material and atlas registries in client resource service. | Static review against 26.2 `ReloadableResourceManager#createReload` signature. |
| 2026-06-22 | Glyph atlas metadata | Added 26.2 `FontTexture#add` observer and glyph registry; old glyph bake/upload overwrites are not ported because 26.2 uses backend texture upload/copy paths. | Static review against 26.2 `FontTexture`, `GlyphStitcher`, bitmap/TTF/Unihex providers, and `GlyphBitmap#upload`. |
| 2026-06-22 | Vanilla chunk-section uniform capture | Added `RadianceUniformSliceRecord` and `RadianceChunkSectionUniform`. `RenderPassBackend#setUniform("ChunkSection", slice)` now decodes std140 chunk position, visibility, atlas size, and model-view bytes; `drawMultipleIndexed` records after each per-draw uniform uploader so each vanilla chunk draw is tied to its own section uniform. | Static review against 26.2 `DynamicUniforms.ChunkSectionInfo`, `DynamicUniformStorage`, `LevelRenderer#prepareChunkRenders`, and `ChunkSectionsToRender#renderGroup`. |
| 2026-06-22 | Pass binding classification | `RadianceRenderPassCapture` now tracks current uniform names and texture binding names. Draw packets re-run scene policy with pipeline, shaders, render pass label, uniforms, texture bindings, and dynamic uniform so rules such as Sodium `u_Globals`/`u_SectionTimeInfo` + `u_BlockTex`/`u_LightTex` can tag raster terrain as acceleration-deferred. | Static review against policy fields `uniformAll` and `textureBindingAll`; no Sodium classes are referenced from Radiance core. |
| 2026-06-22 | Submit-node semantic scopes | Added `RadianceSemanticScope` plus feature-phase and feature-renderer mixins. Submit/entity phases are attached to concrete `SubmitNode` instances during collection and scoped around final backend draw execution, then merged into `RadianceRenderPassCapture` tags. | Static review against 26.2 `SubmitNodeCollection`, `SimpleFeatureRenderPhase`, `TranslucentFeatureRenderPhase`, `RenderTypeFeatureRenderer`, and `QuadParticleFeatureRenderer`; mixins remain loader-neutral and contain no backend policy. |
| 2026-06-22 | Texture operation capture | `RadianceTextureRecord` now stores structured write, buffer-to-texture copy, texture-to-texture copy, color clear, and depth clear metadata. `RadianceCommandEncoderBackend` records all corresponding 26.2 command encoder calls. | Static review against 26.2 `CommandEncoderBackend` texture/clear/copy methods; full native byte payload ABI remains deferred. |
| 2026-06-22 | Resource-backed frame snapshot | `RadianceCaptureRegistry` now exposes immutable resource snapshots, and `RadianceNativeSceneBridge` snapshots include buffers, textures, texture views, pipelines, render passes, and draws. | Static review; native shared_scene ABI is still deferred, but Java-side handle references are now self-describing. |
| 2026-06-22 | Policy fallback and override | Added `fallbackTags` support to scene policy JSON and `-Dradiance.scene.policy=<path>` override loading. Final draw classification now applies fallback tags only when no render pass, pipeline, resource, dynamic uniform, or submit-scope tag matched. | Validates the intended compatibility rule: unknown Blaze3D draws enter raster fallback as `unknown_mod` while PT/ray-query acceleration excludes them by policy. |
| 2026-06-22 | 26.2 staging verification task | Added an isolated `minecraft26_2` source set and `verifyMinecraft26_2Migration` Gradle task. The task does not attach 26.2 sources to the default 1.21.4 jar; it validates the 26.2 mixin config, referenced mixin files, ledger coverage, policy JSON, and absence of loader/old-proxy/GL-id references. | Passed with `JAVA_HOME=C:\Program Files\Zulu\zulu-21`; the default shell JDK 25 cannot configure this Gradle/Groovy stack and fails before task execution. |
| 2026-06-22 | Texture readback path capture | `copyTextureToBuffer` now records full and regional texture readback operations as `COPY_TEXTURE_TO_BUFFER` metadata and updates the destination buffer shadow with deterministic zero-filled placeholder bytes before invoking the callback. | Static review against 26.2 `CommandEncoderBackend#copyTextureToBuffer`; real readback pixels remain deferred to native command execution. |
| 2026-06-22 | Native snapshot semantic dictionaries | `RadianceNativeSceneBridge` frame snapshots now include ordered tag and view dictionaries alongside compact `tagBits` and `viewMask` fields. `ScenePolicy` preserves ordered tag/view names for ABI export. | Static review; this removes an implicit Java-only bit ordering dependency from future native shared_scene ingestion. |
| 2026-06-22 | Resource texture lifetime invalidation | `TextureManagerMixin` now observes `release` and `close`, forwarding only resource ids to client services. `RadianceImageRegistry` and `RadianceMaterialRegistry` remove per-resource texture/material records on release and clear on close/reload. | Static review against 26.2 `TextureManager#release`/`#close`; mixin remains a loader-neutral shim with no material policy. |
| 2026-06-22 | Entity extraction frame snapshot | Added `RadianceEntityRegistry` and wired `RadianceSceneObserver#onEntityExtracted/#beforeEntitySubmit`. Native frame snapshots now export entity id, UUID, type, name, extracted position, previous position, bounds, visibility/glow flags, partial tick, and submit-relative position. | Static review against 26.2 `EntityRenderState`, `Entity`, and `EntityRenderDispatcher`; draw-level exact entity-handle linkage remains a tracked omission. |
| 2026-06-22 | Semantic scope cleanup guard | Added `RadianceSemanticScope.clear()` and call it after backend command submit and resource reload/texture-manager close paths. This prevents stale submit/entity scopes from leaking across frames or reloads if a 26.2 submit/execute path aborts before paired RETURN injections run. | Static review; exact same-method try/finally around feature execution would require a wrapper injection or runtime-specific mixin extras and remains a smoke-test item. |
| 2026-06-22 | World-state frame snapshot | Added `LevelRendererMixin` and `RadianceWorldStateRegistry`. Native frame snapshots now include camera position/rotation/far depth, fog type/color, game time, skybox/sun/moon/star/rain state, cloud status/color/height/range, and weather intensity/radius/column counts. | Static review against 26.2 `LevelRenderer`, `LevelRenderState`, `SkyRenderState`, `WeatherRenderState`, and `CameraRenderState`; mixin only forwards state to client services. |
| 2026-06-22 | Omission table cleanup | Removed stale omissions for initial Distant Horizons and Sodium classification rules now covered by the default scene policy and classification investigation. Kept the real remaining gaps: DH generic-object acceleration policy, Sodium acceleration decode, and Sodium backend compatibility. | Cross-checked `radiance_26_2_classification_investigation.md` and `default_scene_policy.json`. |
| 2026-06-22 | Policy matcher expansion | `SceneMatchCriteria` now supports `callerPackageAny` and `resourceOrPipelineContains` fields. Default policy adds DH generic-object detection and marks those records `acceleration_deferred` so they remain raster-visible but excluded from PT/ray-query acceleration. | JSON parse check plus `verifyMinecraft26_2Migration`; this closes the raster classification part of the DH generic-object omission while keeping acceleration inclusion deferred. |
| 2026-06-22 | Resource semantic tables in frame snapshot | `RadianceNativeSceneBridge` snapshots now include Java resource registry tables for materials, atlas sprites, sprite contents, decoded images/sidecar candidates, and glyphs. | Static review; native material/resource ingestion ABI is still deferred, but Java snapshot data is now self-contained. |
| 2026-06-22 | Old mixin ledger verification | Extended `verifyMinecraft26_2Migration` to parse `src/main/resources/radiance.mixins.json` and fail if any old mixin is absent from `mixin_migration_ledger_26_2.md`. | Passed with JDK 21; this makes the "no omitted mixin" requirement machine-checked. |
| 2026-06-22 | Fabric 26.2 build config | Updated the active Fabric build to Minecraft `26.2`, Fabric Loader `0.19.3`, Fabric API `0.152.2+26.2`, Fabric Loom `1.16.1`, Gradle wrapper `9.5.0`, Java target `25`, and local 26.2 identity mappings because no 26.2 Yarn mapping is published and 26.1+ is no longer obfuscated in the DH/UniMined setup. Wired `src/minecraft26_2/java` and `src/minecraft26_2/resources` into the main source set so the active `fabric.mod.json` mixin list can resolve `radiance-26_2.mixins.json` and its classes. | `processResources` passes. Gradle `8.14.1` could not consume Loom `1.17.12`, and both Loom `1.17.12` and `1.16.1` failed when forced to resolve official Mojang mappings for 26.2. Sodium's 26.2 branch uses Loom `1.16.1`, Java 25, and Fabric API `0.152.1+26.2`; Radiance follows that proven Loom line while keeping the newer resolved API `0.152.2+26.2`. A Mercury source-remap warning about `IMixinConfigPlugin` remains non-blocking during resource processing. |
| 2026-06-22 | Active 26.2 source boundary | Added a 26.2 Fabric client adapter and changed the active source set to compile the 26.2 Blaze3D migration sources plus the minimal common `Radiance` entrypoint. The 26.2 runtime mixin list now enables only `radiance-26_2.mixins.json`; legacy `radiance.mixins.json` stays in source for migration-ledger coverage but is excluded from the 26.2 jar. | `processResources`, `verifyMinecraft26_2Migration`, `compileJava`, `classes`, `jar`, and `remapJar` pass. This removes old Yarn/1.21.4 GUI, proxy, and renderer-replacement mixins from the active 26.2 compile path without deleting them as migration reference material. |
| 2026-06-22 | 26.2 access widener boundary | Added an empty `radiance-26_2.accesswidener` and pointed Loom/Fabric metadata at it. The legacy Yarn/1.21 `radiance.accesswidener` remains in source but is excluded from the 26.2 jar. | Prevents Fabric from parsing stale `net/minecraft/client/render` and other old named targets during 26.2 startup. Verification follows with remapped jar inspection. |

## Omissions And Deferred Items

| Item | Reason deferred | Revisit step |
|---|---|---|
| Native surface presentation | Java backend skeleton records calls but `GpuSurfaceBackend#present` intentionally throws until native swapchain/present is wired. Factory is opt-in to avoid selecting an incomplete backend by default. | Native backend execution |
| Distant Horizons generic-object acceleration policy | DH generic objects are now raster-classified and acceleration-deferred by default, but acceleration eligibility still needs source/distance policy so far approximation or non-world objects do not enter PT accidentally. | Distant Horizons compatibility / scene policy |
| Native shared scene view API changes | Java now has self-describing frame snapshots, but native shared_scene still needs a final ABI and ingestion path. | Backend capture foundation / terrain semantics |
| Texture byte shadow / native texture payload descriptors | Current backend records texture operation metadata, including readback requests, but native texture upload/readback payload format still needs final ABI. | Resource/PBR migration / native backend execution |
| Clear/blit execution in native backend | Java skeleton records clear/copy metadata but does not execute GPU work. | Native backend execution |
| Native material/resource ingestion ABI | Java frame snapshots now export image, atlas, sprite, glyph, sidecar, and material tables, but native modules still need final ingestion structs and ownership rules. | Resource/PBR migration / native shared scene |
| Draw-level entity handle linkage | Frame-level entity identity/state records are exported, and entity draws are tagged via submit scopes, but each draw packet does not yet carry a specific entity handle. Requires deeper submit-node-to-entity association through 26.2 feature node construction/execution. | Submit semantics / native shared scene |
| Same-method exception-safe submit scope cleanup | Frame/reload cleanup now clears stale semantic scopes, but HEAD/RETURN mixins still cannot run same-method finally logic if a feature renderer throws before RETURN. A runtime smoke test should verify whether frame-end cleanup is sufficient or whether a wrapper injection dependency is needed. | Build migration / runtime validation |
| Sodium acceleration inclusion | Sodium replaces vanilla `ChunkSection` dynamic-uniform draws with region multi-draw/indirect batches; vanilla chunk-section uniform capture does not classify Sodium terrain for acceleration. Geometry decoding or a compat adapter is required before safe PT/ray-query inclusion. | Sodium compatibility / terrain semantics |
| Sodium Radiance backend compatibility | Sodium currently distinguishes Vulkan by direct `VulkanDevice` type and otherwise uses the OpenGL path with backend accessors. Radiance needs a loader-side compat adapter or an upstream-compatible backend capability path. | Backend parity verification |
| Policy compile test | 26.2 source set is staged but not active in the current 1.21.4 Gradle build. | Build migration |
