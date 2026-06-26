# Radiance 26.2 Classification Investigation

Scope: identify stable 26.2 Blaze3D signals for tagging captured calls and
building shared-scene views. This document is the source for the initial default
scene-policy JSON.

## Policy Model

The JSON policy has two independent parts:

| Part | Responsibility | Hot-path representation |
|---|---|---|
| Caller/call rules | Detect source and semantic domain, then inject tags into captured calls or stable backend objects. | Compiled matchers plus numeric tag bits. |
| View rules | Define which tags are visible to each shared-scene consumer view. | Numeric include/exclude filters passed to backend/native shared scene. |

Raster compatibility is conservative: unknown geometry should still reach raster
unless explicitly excluded. PT/ray-query acceleration is strict: only stable,
near, whitelisted geometry should enter acceleration views.

## Vanilla 26.2 Signals

| Source | Stable signal | Proposed tags |
|---|---|---|
| Terrain pipelines | `minecraft:pipeline/solid_terrain`, `minecraft:pipeline/cutout_terrain`, `minecraft:pipeline/translucent_terrain` | `minecraft`, `terrain`, `chunk_section`, layer tag |
| Terrain draw path | `RenderPassBackend#drawMultipleIndexed` with dynamic uniform `"ChunkSection"` | `chunk_section`, `dynamic_uniform_chunk_section` |
| Terrain textures | block atlas bound as `Sampler0`, lightmap as `Sampler2` in vanilla path | `block_atlas`, `lightmap` |
| Entity extraction | `EntityRenderDispatcher#extractEntity` and `#submit` | `minecraft`, `entity`, entity-type tags later |
| Submit domains | `SubmitNodeCollection#submitModel`, `submitBlockModel`, `submitItem`, `submitText`, `submitNameTag`, `submitQuadParticleGroup`, `submitCustomGeometry` | `model`, `block_model`, `item`, `text`, `name_tag`, `particle`, `custom_geometry` |
| Render pass descriptor | labels, color/depth targets, render area | `world`, `ui`, `post`, `shadow`, or `offscreen` once labels/targets are enumerated |

Vanilla terrain is the highest-confidence acceleration source. It must enter
raster geometry and can enter PT/ray-query acceleration subject to distance and
budget rules.

## Distant Horizons 26.2 Signals

Distant Horizons has a 26.2+ Blaze3D backend path under
`common/render/blaze`. It calls `RenderSystem.getDevice()`, creates normal
`RenderPipeline`, `GpuBuffer`, `GpuTexture`, `RenderPass`, and draw calls.

| Source | Stable signal | Proposed tags | View default |
|---|---|---|---|
| LOD terrain pipelines | `distanthorizons:opaque_terrain`, `distanthorizons:transparent_terrain` | `distant_horizons`, `terrain`, `lod`, layer tag | Raster include, acceleration exclude |
| LOD shaders | `distanthorizons:lod/blaze/vert`, `distanthorizons:lod/blaze/frag` | `distant_horizons`, `lod` | Raster include, acceleration exclude |
| LOD render pass | descriptor label `distantHorizons:TerrainRenderer` | `distant_horizons`, `terrain`, `lod` | Raster include, acceleration exclude |
| LOD index buffer | label `distantHorizons:LodIndexBuffer` | `distant_horizons`, `lod`, `index_buffer` | Classification hint only |
| LOD uniform names | `vertUniqueUniformBlock`, `vertSharedUniformBlock`, `fragUniformBlock` | `distant_horizons`, `lod` | Classification hint only |
| DH render targets | texture labels `DhColorTexture`, `DhDepthTexture` | `distant_horizons`, `offscreen`, `lod_target` | Used by DH apply/post classification |
| Apply/post pipelines | names like `distanthorizons:dh_apply_to_mc`, fog/SSAO/fade names from post-processing classes | `distant_horizons`, `post` | Raster/post include, acceleration exclude |
| Debug/test pipelines | `distanthorizons:debug_wireframe_renderer`, `distantHorizons:DebugRenderer`, `distantHorizons:DhTestRenderer` | `distant_horizons`, `debug` or `test` | Acceleration exclude |
| Generic objects | `distanthorizons:generic_objects`, pass `distantHorizons:GenericObjectRenderer` | `distant_horizons`, `generic_object` | Raster include, acceleration exclude until API object policy exists |

DH's own chunk mixin injects at `ChunkSectionsToRender#renderGroup` with order
800 and renders LODs around vanilla chunk phases. For Radiance, no DH-specific
render mixin is required for LOD terrain classification because backend-level
pipeline/pass/buffer labels are stable enough.

Default rule: DH LOD terrain must never enter PT/ray-query acceleration. It is
far approximation geometry and can overlap nearby vanilla chunk sections. It
belongs in raster geometry or post/composite views only.

## Sodium 26.2 Signals

Sodium replaces vanilla terrain extraction/build/rendering while still using
Mojang `RenderPass` draw entry points for final draw submission.

Observed path:

1. `LevelRendererMixin#prepareChunkRenders` returns a dummy `ChunkSectionsToRender`.
2. `ChunkSectionsToRenderMixin#renderGroup` cancels vanilla render group when
   Sodium renderer is attached.
3. `SodiumWorldRenderer#drawChunkLayer` calls `renderLayer` for
   `DefaultTerrainRenderPasses.SOLID`, `CUTOUT`, and `TRANSLUCENT`.
4. `DefaultChunkRenderer#render` creates a `RenderPass` labeled `"Terrain"`.
5. It sets a Sodium pipeline and binds `u_Globals`, `u_SectionTimeInfo`,
   `u_LightTex`, and `u_BlockTex`.
6. It draws region batches through `multiDrawIndexed` or `drawIndexedIndirect`
   depending on selected backend.

| Source | Stable signal | Proposed tags | View default |
|---|---|---|---|
| Sodium pipelines | namespace `sodium`, path copied from vanilla chunk layer pipeline path | `sodium`, `terrain`, `chunk_section`, layer tag | Raster include, acceleration include if native geometry decode is available |
| Sodium shaders | `sodium:blocks/block_layer_opaque` vertex/fragment | `sodium`, `terrain` | Classification hint |
| Render pass label | `"Terrain"` combined with Sodium pipeline namespace and uniforms | `sodium`, `terrain` | Classification hint |
| Uniforms | `u_Globals`, `u_SectionTimeInfo` | `sodium`, `chunk_region` | Needed for region/section metadata correlation |
| Textures | `u_BlockTex`, `u_LightTex` | `block_atlas`, `lightmap` | Classification hint |
| Draw methods | GL separate `multiDrawIndexed`, VK interleaved `multiDrawIndexed`, VK indirect `drawIndexedIndirect` | `terrain_batch` | Backend capture must support all paths |

Compatibility risk: Sodium's `DrawBackend.chooseBackend()` checks whether
`GpuDeviceAccessor.sodium$getBackend()` is a `VulkanDevice`; otherwise it chooses
the OpenGL path. Sodium GL/VK draw contexts then directly access GL program ids
or Vulkan command buffers/pipeline layouts through mixin accessors. A Radiance
backend is neither vanilla GL nor vanilla Vulkan, so Sodium may choose the wrong
path or fail casts/accessors.

Required future work:

| Item | Reason |
|---|---|
| Sodium compatibility adapter | Radiance needs a path Sodium can identify as supported without direct GL/VK internals, or a loader-specific compat shim outside core mixins. |
| Sodium terrain geometry decoder | Acceleration inclusion requires decoding Sodium region geometry buffers and per-section draw commands, not just seeing final multi-draw packets. |
| Conservative fallback | Until decoder exists, Sodium terrain should raster correctly through backend draw execution but be excluded from acceleration or marked `acceleration_deferred`. |

## Initial View Policy

| View | Include | Exclude |
|---|---|---|
| `raster_geometry` | fallback unknown geometry, vanilla terrain/entities, Sodium terrain, DH LOD terrain, DH generic objects | `post`, `ui`, `debug`, `test` unless explicitly routed |
| `pt_acceleration` | vanilla terrain chunk sections, vanilla entities after distance/budget checks | `lod`, `post`, `ui`, `debug`, `test`, `unknown_mod`, `acceleration_deferred` |
| `raster_ray_query_acceleration` | same as `pt_acceleration` | same as `pt_acceleration` |
| `post_processing` | `post`, offscreen apply/composite passes | world acceleration-only records |
| `debug` | `debug`, `test`, diagnostics | excluded from production views |

## Default Caller/Call Rule Seeds

```json
{
  "callerRules": [
    {
      "id": "minecraft.chunk_sections",
      "match": {
        "pipeline": [
          "minecraft:pipeline/solid_terrain",
          "minecraft:pipeline/cutout_terrain",
          "minecraft:pipeline/translucent_terrain"
        ],
        "dynamicUniformAny": ["ChunkSection"]
      },
      "injectTags": ["minecraft", "terrain", "chunk_section"]
    },
    {
      "id": "distant_horizons.lod_terrain",
      "match": {
        "pipelineNamespace": "distanthorizons",
        "pipelinePathAny": ["opaque_terrain", "transparent_terrain"],
        "renderPassLabelAny": ["distantHorizons:TerrainRenderer"]
      },
      "injectTags": ["distant_horizons", "terrain", "lod"]
    },
    {
      "id": "sodium.chunk_terrain",
      "match": {
        "pipelineNamespace": "sodium",
        "shaderAny": ["sodium:blocks/block_layer_opaque"],
        "uniformAll": ["u_Globals", "u_SectionTimeInfo"],
        "textureBindingAll": ["u_BlockTex", "u_LightTex"]
      },
      "injectTags": ["sodium", "terrain", "chunk_section", "acceleration_deferred"]
    }
  ]
}
```

## Open Items

| Item | Status |
|---|---|
| Exact vanilla render pass labels/targets for world/post/UI | Deferred until backend capture scaffold can log descriptors. |
| Sodium acceleration eligibility | Deferred until geometry decode or compat adapter exists. |
| DH generic object acceleration eligibility | Default policy now tags DH generic objects as raster-visible and `acceleration_deferred`; actual acceleration inclusion remains deferred until object source/distance policy is designed. |
| Loader-provided mod id/source hints | Deferred to Fabric/NeoForge adapter layer; core policy must accept hints but not depend on loader APIs. |

## Vanilla Terrain Uniform Capture Status

Implemented in the 26.2 staging backend:

| Signal | Capture behavior |
|---|---|
| `RenderPassBackend#setUniform("ChunkSection", GpuBufferSlice)` | Decodes the CPU-shadowed std140 `DynamicUniforms.ChunkSectionInfo` slice. |
| `drawMultipleIndexed` | Executes the per-draw uniform uploader and records the draw immediately, so each draw packet carries its own chunk section uniform instead of only the last uploaded slice. |
| Decoded fields | `x`, `y`, `z`, `visibility`, `textureAtlasWidth`, `textureAtlasHeight`, and 16 model-view floats. |
| Pass binding state | Draw classification also sees current uniform and texture binding names, enabling Sodium terrain tagging through `u_Globals`, `u_SectionTimeInfo`, `u_BlockTex`, and `u_LightTex` without importing Sodium classes. |

This closes the vanilla terrain part of the old `ChunkProxy` responsibility
without reintroducing a chunk rebuild mixin. Sodium remains separate because its
terrain path uses `u_SectionTimeInfo`, multi-draw, and indirect batches instead
of the vanilla `"ChunkSection"` dynamic uniform.

## Implementation Log

| Date | Step | Result |
|---|---|---|
| 2026-06-22 | Vanilla/DH/Sodium classification investigation | Recorded stable signals and default view policy for vanilla terrain/entities, DH LOD/post/debug/generic objects, and Sodium terrain replacement path. |
| 2026-06-22 | Vanilla terrain uniform capture | Recorded implemented backend capture for `ChunkSection` uniform slices and noted Sodium remains acceleration-deferred. |
| 2026-06-22 | Pass binding classification | Backend draw packets now combine policy tags from uniform and texture binding names, making the existing Sodium default rule effective for raster tagging while acceleration remains deferred. |
| 2026-06-22 | Java shared-scene view mask bridge | Added compact numeric view-mask generation from policy tags for raster geometry, PT acceleration, raster ray-query acceleration, post, and debug views. Bridge snapshots now carry draw ranges, buffer handles, tag bits, view mask, and optional chunk-section data without native string matching. |
| 2026-06-22 | Submit-node semantic scope | Submit collection now records active submit/entity phase tags on each `SubmitNode`; feature execution scopes those tags around backend draw calls so model, block model, item, text, name tag, particle, custom geometry, and entity-derived draws reach draw packet classification. |
| 2026-06-22 | Rule matcher expansion | Policy matching now supports caller package groups and combined resource/pipeline substring hints. Default DH generic-object rules classify generic objects as raster-visible but acceleration-deferred. |
