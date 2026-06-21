# Deferred RT Rasterization Module Architecture

> Status: design proposal  
> Scope: Radiance Java pipeline + MCVR native Vulkan renderer  
> Goal: add a new rasterized deferred module with ray-traced shading/GI/reflection, running in parallel with the existing path tracing module.  
> Non-goal: depend on Blaze3D today. Blaze3D is a future compatibility target for the scene/input boundary, not a shaderpack dependency.

Follow-up runtime flexibility, backend selection, diagnostics and baseline performance work is tracked in
[`deferred-rt-runtime-flexibility-and-diagnostics-plan.md`](deferred-rt-runtime-flexibility-and-diagnostics-plan.md).

---

## 1. Problem Statement

Radiance currently has a path tracing oriented world module:

- Java pipeline configuration lives in `Radiance/src/main/resources/modules/*.yaml`.
- Native modules implement `WorldModule` in `MCVR/src/core/render/modules/world/world_module.hpp`.
- Native module registration is done in `MCVR/src/core/render/pipeline.cpp`.
- The current path tracing implementation is `RayTracingModule`.
- User shaderpack support is implemented by `ShaderPack` / `ShaderPackLoader` under `MCVR/src/core/render/modules/world/shader_pack`.

The new module should support a modern hybrid renderer:

1. Primary visibility is rasterized into a G-buffer.
2. Ray tracing is used for expensive visibility/shading effects:
   - hard/soft shadows,
   - direct-light visibility,
   - one or limited-bounce GI,
   - reflections,
   - refraction if enabled,
   - optional SHARC/probe/cache based indirect lighting.
3. The module is not a full path tracer.
4. It coexists with the current PT module as a separate pipeline module.
5. Users can customize the deferred pipeline through shaderpacks.
6. The architecture keeps a clear adapter boundary so future Blaze3D support can feed scene geometry without rewriting shaderpacks or the renderer core.
7. VR/stereo rendering is a native requirement. All module resources, G-buffer passes, ray-query lighting passes and outputs must support image array layers and per-view execution from the first design.

Long-term mod compatibility is one of the reasons for the scene-provider boundary. Current MCVR captures and uploads its own world/entity geometry. Future active rendering mods are likely to move toward Mojang's Blaze3D abstractions as Minecraft transitions away from a purely OpenGL-facing renderer. The deferred module should therefore be able to consume Blaze3D-style buffers, render layers and draw declarations through an adapter, without exposing Blaze3D concepts to shaderpacks or to the lighting graph.

This does not mean every Blaze3D-using mod becomes automatically correct. Mods with custom instancing, GPU culling, shader/material systems or pass ordering may still need adapter work. The architectural requirement is that such work is isolated in `SceneProvider` implementations and backend mapping code, not in the deferred shaderpack contract.

---

## 2. Key Separation

The design must keep these concepts separate.

| Concept | Responsibility | Must Not Know About |
|---|---|---|
| Shaderpack | User-defined resources, passes, shader files, execution commands, attributes | Blaze3D, Java mixins, Minecraft loader internals |
| Scene input layer | Converts Minecraft/Radiance/Blaze3D scene data into stable native draw/trace packets | User shaderpack syntax |
| Execution backend | Builds Vulkan render/compute/ray tracing pipelines and records commands | Java pipeline UI, future Blaze3D APIs |
| Deferred RT module | Owns render contract, internal resources, G-buffer, RT lighting and output images | Concrete source of scene packets |

Blaze3D compatibility is a source/adaptor problem. Shaderpacks are a user pipeline language. They should meet only through the module's neutral internal IR. A `Blaze3DSceneProvider` may translate Blaze3D-owned buffers, render layers and draw calls into `SceneDrawPacket`s, but deferred shaderpacks must not name Blaze3D classes, Minecraft mixins or mod-specific renderer internals.

---

## 3. High-Level Architecture

```text
Radiance Java pipeline
  modules/deferred_rt.yaml
       |
       v
MCVR Pipeline::collectWorldModules()
       |
       v
WorldPipeline
       |
       +-- SharedSceneRuntime
       |     +-- ScenePrepare
       |     |     builds/updates BLAS/TLAS and GPU scene metadata
       |     |
       |     +-- SceneProvider
       |           +-- McvrSceneProvider       current chunk/entity/native world data
       |           +-- Blaze3DSceneProvider    future adapter
       |
       +-- DeferredRtModule : WorldModule
       |
       +-- DeferredShaderPackRuntime
       |     loads deferred shaderpack stage, resources, passes, execution
       |
      +-- GBufferGraph
      |     raster passes for opaque/cutout/entity geometry, writing view/layer targets
       |
      +-- LightingGraph
      |     ray-query + compute/fullscreen shading passes, dispatched per view/layer
       |
      +-- OutputContract
             exports layered images compatible with VR-aware NRD/DLSS/FSR/XeSS/Temporal/ToneMapping/PostRender
```

The module is independent from the existing `RayTracingModule`, but it should reuse these pieces:

- shaderpack loader and execution model,
- runtime texture/buffer support,
- shader compilation cache,
- descriptor table patterns,
- world uniforms and sky uniforms,
- scene acceleration structure preparation,
- stereo/VR image array layer conventions from `MCVR-custom`,
- post-render integration,
- NRD/upscaler/temporal-compatible output buffers where possible.

Current implementation note as of Step 41:

- `ShaderPackLoader` has a third metadata stage, `stage: deferred`.
- The loader accepts both `execution_deferred` and nested `execution.deferred`.
- A lightweight stage-contract helper rejects PT/SBT declarations for Deferred-stage passes before runtime setup.
- Java shaderpack selection now routes through the active shaderpack owner instead of being hard-coded to PT.
- PT keeps the historical default: an empty path means built-in `vanilla-pt.zip` and external pack failures may fall back to it.
- Deferred RT has its own `render_pipeline.module.deferred_rt.attribute.shader_pack_path`; an empty path now resolves to built-in `vanilla-deferred-rt.zip`, never to a PT pack.
- `WorldPipeline` loads a module-owned `ShaderPack` for Deferred RT whenever the module is present, using the deferred built-in path as the default.
- `DeferredRtModule` inspects the selected pack's Deferred stage, validates that Deferred execution pass commands reference known Deferred-stage passes, and exposes inspection plus runtime-plan summaries in diagnostics text.
- `DeferredRtModule` now records Deferred-stage runtime execution for:
  - `render` with `content: minecraft_gbuffer`,
  - `compute`,
  - `ray_query` when `VK_KHR_ray_query` is available, with optional `fallback_compute`,
  - `full_screen` with `backend: compute_3d`.
- The Deferred shaderpack runtime binds a module-owned scene descriptor set with world/sky uniforms, G-buffer draw/view buffers, classification/visibility masks, lighting queue buffers, texture mapping and TLAS. User-declared runtime textures/buffers and execution variables use the existing `ShaderPack` descriptor model.
- `vanilla-deferred-rt` is a real built-in pack under `MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt` and is installed as `shaders/world/deferred_rt/vanilla-deferred-rt.zip`.
- The old fixed Deferred `.spv` runtime path has been removed. G-buffer, classification, queue build, direct light, queued reflection/GI and compose are now declared by the pack and recorded by the hardened Deferred runtime.
- Deferred pass metadata has an explicit optional `semantic` field. `name` remains the execution-command reference and author-facing debug name; C++ fixed Deferred compute synchronization hooks use `semantic`, not pass names.
- The built-in `vanilla-deferred-rt` pack declares fixed ABI semantics for `clear_contract`, `gbuffer`, `classify`, `build_lighting_queues`, `direct_light`, `reflection`, `gi` and `compose`, so authors may rename pass `name` values without changing the hardened runtime sync points as long as the semantic tags stay correct.
- As of Step 38, the built-in `vanilla-deferred-rt` pack uses the public `slots` schema instead of author-written
  `execution.deferred.commands`. The loader injects `stage: deferred` and fixed `semantic` values for slot passes,
  validates slot type/schedule compatibility, and generates the internal Deferred command list in canonical backbone
  order.
- The first public schema version also supports custom Deferred `passes` inserted through named `insertions` phases.
  Custom passes are restricted to compute or full-screen `compute_3d`, must be inserted exactly once, and cannot declare
  fixed backbone semantics.
- As of Step 39, Deferred public packs can declare pack-owned logical resources through `resources.images`,
  `resources.textures`, and `resources.buffers`. These lower into the existing ShaderPack runtime image/buffer path, so
  there is no second Deferred-only Vulkan resource allocator.
- Custom pass `inputs` and `outputs` now form a validated logical resource graph. Pack-owned resources must use
  `custom.*`; protected built-in namespaces such as `gbuffer.*`, `visibility.*`, `queue.*`, `lighting.*`, `scene.*`, and
  `out.*` are read-only and phase-gated for custom passes. C++ validates custom read-before-write, protected writes,
  undeclared resources, duplicate writes, and too-early built-in reads before runtime setup.
- As of Step 40, Deferred public packs support root/common `define` / `defines` / `definitions`, plus root/pass/slot
  `requires` for `ray_query`, `multiview`, `half_precision`, and `storage_image_format:<format>`. Ray-query passes use
  `fallback_compute` when ray query is unavailable and a fallback shader is declared.
- As of Step 41, pack-owned runtime images and buffers support explicit `lifetime`: `per_frame`, `history`,
  `persistent`, and `imported_texture`. `history` resources allocate current/previous double-buffered instances per
  swapchain image, expose `.previous` as an input-only logical alias, receive generated previous descriptor binding
  macros, and are zero-cleared when runtime resources are rebuilt.
- C++ still owns Vulkan resources, descriptor layouts, TLAS binding, queue buffers, indirect dispatch offsets, barriers, push constants and pass scheduling semantics. The pack owns pass order, shader files, high-level schedules and optional runtime resources.
- Pack dispatch schedules are high-level: `screen`, `tile_grid`, `direct`, and `lighting_queue`. `lighting_queue` names a queue kind, but C++ resolves the indirect argument buffer and offset.
- Existing PT and PostRender shaderpacks continue to use the same loader and execution model.

---

## 4. Module Contract

### 4.1 Consumer-Driven Contract

The Deferred RT module must start from the downstream consumers, not from the current PT implementation names. Existing modules consume fixed input slots and formats. Compatibility therefore means two things:

1. the Java pipeline can connect the same formats,
2. the image contents match the downstream semantic expectation.

The module may use its own internal names. Public exports should be mapped to the existing legacy output names only at the export boundary.

```text
DeferredRtModule internals
  internal:primary_depth
  internal:primary_linear_depth
  internal:primary_normal_roughness
  internal:primary_motion_vector
  internal:lighting_direct_diffuse
  internal:lighting_indirect_diffuse
  internal:lighting_specular
  internal:gi_hit_distance
       |
       v
LegacyExportMap
       |
       v
  out:first_hit_depth
  out:linear_depth
  out:normal_roughness
  out:motion_vector
  out:first_hit_diffuse_direct_light
  out:first_hit_diffuse_indirect_light
  out:first_hit_specular
  out:gi_hit_distance
```

The `first_hit_*` prefix should be treated as a legacy external contract. Inside the Deferred RT module it means "primary visible surface" or "lighting associated with the primary visible surface". It must not leak into shaderpack-neutral internal graph names except in the final export mapping.

One important exception to strict PT output equivalence is diffuse/GI hit distance. The current PT presets connect `ray_tracing.first_hit_depth` to NRD's `diffuseHitDepthImage`, but NRD's prepare shader consumes that input as diffuse hit distance for `REBLUR_FrontEnd_GetNormHitDist`. Deferred RT should not copy that historical ambiguity. It should expose an explicit public `gi_hit_distance` output and connect it to NRD's `diffuseHitDepthImage`.

### 4.2 Internal Naming Convention

Internal names should describe renderer facts, not PT path structure.

| Internal name | Format | Legacy export | Meaning |
|---|---:|---|---|
| `primary_radiance` | `R16G16B16A16_SFLOAT` | `radiance` | Direct Deferred RT HDR output for non-denoiser presets. In NRD/upscaler presets, downstream modules may replace it with denoised or upscaled radiance; split lighting exports are the authoritative NRD inputs. |
| `primary_albedo_metallic` | `R8G8B8A8_UNORM` | `diffuse_albedo_metallic` | Primary surface base color in RGB, metallic or equivalent material value in A. |
| `primary_specular_albedo` | `R8G8B8A8_UNORM` | `specular_albedo` | Specular/F0 color or compatible packed specular material value. |
| `primary_normal_roughness` | `R16G16B16A16_SFLOAT` | `normal_roughness` | Primary surface normal plus roughness. Normal space must match NRD/upscaler expectations. |
| `primary_motion_vector` | `R16G16_SFLOAT` | `motion_vector` | Reprojection vector for the primary surface, including camera jitter policy. |
| `primary_linear_depth` | `R16_SFLOAT` | `linear_depth` | Linear view depth for the currently visible surface used by NRD, upscalers and volumetric/post effects. |
| `reflection_hit_distance` | `R16_SFLOAT` | `specular_hit_depth` | Distance to the secondary reflection/specular hit. Zero or invalid value means no usable hit. |
| `primary_depth` | `R16_SFLOAT` | `first_hit_depth` | Linear depth of the first primary surface. This is the Deferred RT equivalent of PT first-hit depth and is consumed by PostRender/upscaler scene-fact propagation. |
| `primary_direct_diffuse` | `R16G16B16A16_SFLOAT` | `first_hit_diffuse_direct_light` | Noisy direct diffuse lighting on the primary surface. |
| `primary_indirect_diffuse` | `R16G16B16A16_SFLOAT` | `first_hit_diffuse_indirect_light` | Noisy indirect diffuse/GI lighting on the primary surface. |
| `primary_specular` | `R16G16B16A16_SFLOAT` | `first_hit_specular` | Noisy reflection/specular lighting on the primary surface. |
| `primary_clear` | `R16G16B16A16_SFLOAT` | `first_hit_clear` | Clear coat or clear-layer contribution. May be zero-filled initially. |
| `primary_emission` | `R16G16B16A16_SFLOAT` | `first_hit_base_emission` | Emission from the primary surface. |
| `atmosphere_fog` | `R16G16B16A16_SFLOAT` | `fog_image` | Fog/atmosphere contribution used by denoisers/composition. May be zero-filled initially if unsupported. |
| `primary_refraction` | `R16G16B16A16_SFLOAT` | `first_hit_refraction` | Refraction/transmission contribution. May be zero-filled or sourced from a forward transparent pass initially. |
| `gi_hit_distance` | `R16_SFLOAT` | `gi_hit_distance` | Distance to diffuse/GI secondary hit. Deferred RT should expose this as a new public output and connect it to NRD's `diffuseHitDepthImage`. |

The first implementation should create this mapping explicitly in code. Do not scatter `first_hit_*` names through G-buffer, lighting or shaderpack graph internals.

For the first implementation, `primary_depth` and `primary_linear_depth` may alias the same image only if that matches the current consumer convention for the selected path. Existing PT shaders distinguish them: `first_hit_depth` stores `firstHitLinearDepth`, while `linear_depth` stores `viewDepth`. These are often the same for a simple opaque primary surface, but can differ for transparent, refractive or special primary paths. If a path needs those semantics, Deferred RT must generate separate images.

### 4.3 Downstream Input Consumers

#### ToneMapping

Native consumer: `ToneMappingModule::setOrCreateInputImages`, `inputImageNum = 1`.

| Slot | YAML input | Format | Native field | Deferred RT source | Consumer logic |
|---:|---|---|---|---|---|
| 0 | `denoised_radiance` | `R16G16B16A16_SFLOAT` | `hdrImages_` | `primary_radiance`, `NRD.denoised_radiance`, `TemporalAccumulation.accumulated_radiance`, or upscaler output | Tone mapping reads HDR radiance, computes exposure/mapping, and writes `mapped_output` as `R8G8B8A8_UNORM`. It does not need depth, motion or material data. |

Compatibility requirement: any Deferred RT path that bypasses denoising must still provide a valid HDR image to ToneMapping. This is the simplest bring-up path.

#### PostRender

Native consumer: `PostRenderModule::setOrCreateInputImages`, `inputImageNum = 5`.

| Slot | YAML input | Format | Native field | Deferred RT source | Consumer logic |
|---:|---|---|---|---|---|
| 0 | `ldr_input` | `R8G8B8A8_UNORM` | `ldrImages_` | `ToneMapping.mapped_output` | Base LDR image for final post-render composition. |
| 1 | `first_hit_depth` | `R16_SFLOAT` | `firstHitDepthImages_` | `primary_depth` exported as `first_hit_depth`, or upscaled `upscaled_first_hit_depth` after FSR/XeSS/DLSS | Depth for post-render geometry/composition and correct layering against final effects. |
| 2 | `hdr_input` | `R16G16B16A16_SFLOAT` | `hdrImages_` | HDR path used before tone mapping: denoised, accumulated, upscaled or direct Deferred RT radiance | HDR reference for post effects that need pre-tonemap color. |
| 3 | `motion_vector` | `R16G16_SFLOAT` | `motionVectorImages_` | `primary_motion_vector` exported as `motion_vector`, or upscaled motion after FSR/XeSS/DLSS | Motion data for temporal/post effects and VR-aware post paths. |
| 4 | `normal_roughness` | `R16G16B16A16_SFLOAT` | `normalRoughnessImages_` | `primary_normal_roughness` exported as `normal_roughness`, or upscaled normal/roughness after FSR/XeSS/DLSS | Surface orientation/material roughness for post effects that need scene facts. |

Compatibility requirement: PostRender should remain compatible. It is the final composition path and already follows the layered image contract. Deferred RT should not replace it unless there is a separate, explicit redesign of final composition.

#### TemporalAccumulation

Native consumer: `TemporalAccumulationModule::setOrCreateInputImages`, `inputImageNum = 3`.

| Slot | YAML input | Format | Native field | Deferred RT source | Consumer logic |
|---:|---|---|---|---|---|
| 0 | `color` | `R16G16B16A16_SFLOAT` | `hdrNoisyImages_` | `primary_radiance` or composed noisy HDR lighting | Accumulates HDR color over time. |
| 1 | `motion` | `R16G16_SFLOAT` | `motionVectorImages_` | `primary_motion_vector` | Reprojects previous color/history. |
| 2 | `normal_roughness` | `R16G16B16A16_SFLOAT` | `normalRoughnessImages_` | `primary_normal_roughness` | Rejects or weights history when surface normal/roughness changes. |

Compatibility requirement: motion vectors and normal/roughness must be stable before this module is useful. A placeholder color can run, but wrong motion will create ghosting.

#### NRD

Native consumer: `NrdModule::setOrCreateInputImages`, `inputImageNum = 14`.

| Slot | YAML input | Format | Native field | Deferred RT source | Consumer logic |
|---:|---|---|---|---|---|
| 0 | `diffuse_radiance` | `R16G16B16A16_SFLOAT` | `diffuseIndirectRadianceImages_` | `primary_indirect_diffuse` | Noisy diffuse indirect/GI signal for NRD Reblur. |
| 1 | `specular_radiance` | `R16G16B16A16_SFLOAT` | `specularIndirectRadianceImages_` | `primary_specular` | Noisy specular/reflection signal for NRD Reblur. |
| 2 | `direct_radiance` | `R16G16B16A16_SFLOAT` | `directRadianceImages_` | `primary_direct_diffuse` plus direct specular if the denoiser path expects it | Direct lighting contribution combined with denoised indirect/specular outputs. |
| 3 | `diffuse_albedo` | `R8G8B8A8_UNORM` | `diffuseAlbedoMetallicImages_` | `primary_albedo_metallic` | Material guide for diffuse denoising and final reconstruction. |
| 4 | `specular_albedo` | `R8G8B8A8_UNORM` | `specularAlbedoImages_` | `primary_specular_albedo` | Material guide for specular denoising. |
| 5 | `normal_roughness` | `R16G16B16A16_SFLOAT` | `normalRoughnessImages_` | `primary_normal_roughness` | Major edge-stopping and material guide. |
| 6 | `motion_vector` | `R16G16_SFLOAT` | `motionVectorImages_` | `primary_motion_vector` | Temporal reprojection for NRD history. |
| 7 | `linear_depth` | `R16_SFLOAT` | `linearDepthImages_` | `primary_linear_depth` | Depth guide and disocclusion detection. |
| 8 | `first_hit_clear` | `R16G16B16A16_SFLOAT` | `clearRadianceImages_` | `primary_clear` | Clear/clearcoat contribution. Can be zero initially, but the image must exist and be deterministic. |
| 9 | `first_hit_base_emission` | `R16G16B16A16_SFLOAT` | `baseEmissionImages_` | `primary_emission` | Emissive base contribution used during reconstruction/composition. |
| 10 | `fog_image` | `R16G16B16A16_SFLOAT` | `fogImages_` | `atmosphere_fog` | Fog/atmosphere contribution kept outside noisy GI where possible. |
| 11 | `diffuseHitDepthImage` | `R16_SFLOAT` | `diffuseHitDepthImages_` | `gi_hit_distance` public output | Hit distance for diffuse/GI rays. Used by NRD hit-distance reconstruction and blur heuristics. |
| 12 | `specularHitDepthImage` | `R16_SFLOAT` | `specularHitDepthImages_` | `reflection_hit_distance` | Hit distance for specular/reflection rays. Used by NRD specular denoising. |
| 13 | `first_hit_refraction` | `R16G16B16A16_SFLOAT` | `refractionRadianceImages_` | `primary_refraction` | Refraction/transmission contribution. Can be zero initially, but should become a real transparent/refraction path later. |

Compatibility requirement: NRD is the strictest downstream consumer. It is not enough to connect images with matching names and formats. The lighting split, material guides, depth, motion and hit distances must have denoiser-valid semantics before this path should be considered correct. Deferred RT should not connect `first_hit_depth` to `diffuseHitDepthImage`; use the explicit `gi_hit_distance` export.

#### DLSS

Native consumer: `DLSSModule::setOrCreateInputImages`, `inputImageNum = 8`.

| Slot | YAML input | Format | Native field | Deferred RT source | Consumer logic |
|---:|---|---|---|---|---|
| 0 | `radiance` | `R16G16B16A16_SFLOAT` | `hdrImages_` | `primary_radiance` or denoised HDR depending on preset | Input color for DLSS Ray Reconstruction/upscale. |
| 1 | `diffuse_albedo_metallic` | `R8G8B8A8_UNORM` | `diffuseAlbedoImages_` | `primary_albedo_metallic` | Material guide. |
| 2 | `specular_albedo` | `R8G8B8A8_UNORM` | `specularAlbedoImages_` | `primary_specular_albedo` | Material guide. |
| 3 | `normal_roughness` | `R16G16B16A16_SFLOAT` | `normalRoughnessImages_` | `primary_normal_roughness` | Normal/roughness guide. |
| 4 | `motion_vector` | `R16G16_SFLOAT` | `motionVectorImages_` | `primary_motion_vector` | Velocity input. |
| 5 | `linear_depth` | `R16_SFLOAT` | `linearDepthImages_` | `primary_linear_depth` | Depth input. |
| 6 | `specular_hit_depth` | `R16_SFLOAT` | `specularHitDepthImages_` | `reflection_hit_distance` | Specular hit distance input. |
| 7 | `first_hit_depth` | `R16_SFLOAT` | `firstHitDepthImages_` | `primary_depth` | Upscaled and exported as `upscaled_first_hit_depth` for PostRender. |

Consumer logic: the module passes color, albedo, specular albedo, normal/roughness, motion, linear depth and specular hit distance into the DLSS wrapper, then separately upscales first-hit depth, motion and normal/roughness for downstream PostRender.

Compatibility requirement: Deferred RT can connect to DLSS early, but quality depends on correct motion/depth/material semantics. If `reflection_hit_distance` is fake or zero, Ray Reconstruction quality is not validated.

#### FSR Upscaler

Native consumer: `FSRUpscalerModule::setOrCreateInputImages`, `inputImageNum = 5`.

| Slot | YAML input | Format | Native field | Deferred RT source | Consumer logic |
|---:|---|---|---|---|---|
| 0 | `color` | `R16G16B16A16_SFLOAT` | `inputImages_[frame][0]` | HDR color, normally denoised NRD output or `primary_radiance` | Render-resolution color input. |
| 1 | `depth` | `R16_SFLOAT` | `inputImages_[frame][1]` | `primary_linear_depth` | Converted to device depth internally for FSR. |
| 2 | `motion_vector` | `R16G16_SFLOAT` | `inputImages_[frame][2]` | `primary_motion_vector` | Velocity input and also upscaled for PostRender. |
| 3 | `first_hit_depth` | `R16_SFLOAT` | `inputImages_[frame][3]` | `primary_depth` | Upscaled for PostRender. |
| 4 | `normal_roughness` | `R16G16B16A16_SFLOAT` | `inputImages_[frame][4]` | `primary_normal_roughness` | Upscaled for PostRender. |

Consumer logic: FSR works at render resolution, creates display-resolution color, then upscales first-hit depth, motion and normal/roughness so the rest of the pipeline can stay display-resolution.

Compatibility requirement: Deferred RT must know whether it is producing render-resolution or display-resolution outputs. If FSR/XeSS/DLSS are downstream, exported images before the upscaler are render-resolution; images after the upscaler feed PostRender.

#### XeSS Upscaler

Native consumer: `XessSrModule::setOrCreateInputImages`, `inputImageNum = 5`.

| Slot | YAML input | Format | Native field | Deferred RT source | Consumer logic |
|---:|---|---|---|---|---|
| 0 | `color` | `R16G16B16A16_SFLOAT` | `inputColorImage` / `inputImages_[frame][0]` | HDR color, normally denoised NRD output or `primary_radiance` | Render-resolution color input. |
| 1 | `depth` | `R16_SFLOAT` | `inputDepthImage` / `inputImages_[frame][1]` | `primary_linear_depth` | Converted to device depth internally for XeSS. |
| 2 | `motion_vector` | `R16G16_SFLOAT` | `inputMotionVectorImage` / `inputImages_[frame][2]` | `primary_motion_vector` | Converted/scaled for XeSS velocity and upscaled for PostRender. |
| 3 | `first_hit_depth` | `R16_SFLOAT` | `inputFirstHitDepthImage` / `inputImages_[frame][3]` | `primary_depth` | Upscaled for PostRender. |
| 4 | `normal_roughness` | `R16G16B16A16_SFLOAT` | `inputNormalRoughnessImage` / `inputImages_[frame][4]` | `primary_normal_roughness` | Upscaled for PostRender. |

Consumer logic: XeSS mirrors the FSR shape: it consumes render-resolution color/depth/motion and exports display-resolution color plus scene-fact buffers for PostRender.

Compatibility requirement: same as FSR, plus the module requires valid layer count when VR is active.

#### SVGF

Native consumer: `SvgfModule::setOrCreateInputImages`, `inputImageNum = 10`, currently not exposed by a Java `svgf.yaml` module and commented out in native module registration.

| Slot | Native field | Expected format class | Deferred RT source | Consumer logic |
|---:|---|---|---|---|
| 0 | `diffuseRadianceImages_` | HDR radiance | `primary_indirect_diffuse` | Noisy diffuse signal. |
| 1 | `specularRadianceImages_` | HDR radiance | `primary_specular` | Noisy specular signal. |
| 2 | `directRadianceImages_` | HDR radiance | `primary_direct_diffuse` | Direct lighting signal. |
| 3 | `diffuseAlbedoImages_` | albedo/material | `primary_albedo_metallic` | Material guide. |
| 4 | `specularAlbedoImages_` | albedo/material | `primary_specular_albedo` | Material guide. |
| 5 | `normalRoughnessImages_` | normal/roughness | `primary_normal_roughness` | Edge-stopping guide. |
| 6 | `motionVectorImages_` | motion | `primary_motion_vector` | Temporal reprojection. |
| 7 | `linearDepthImages_` | depth | `primary_linear_depth` | Depth guide. |
| 8 | `clearRadianceImages_` | HDR radiance | `primary_clear` | Clear/clearcoat contribution. |
| 9 | `baseEmissionImages_` | HDR radiance | `primary_emission` | Emissive contribution. |

Compatibility requirement: keep this mapping in mind, but do not make SVGF drive the first Deferred RT contract until it is re-exposed and registered.

### 4.4 Minimum Viable Export Sets

The first module shell should allocate the 15 PT-compatible public outputs plus the Deferred RT-specific `gi_hit_distance` output, for 16 public outputs total. Different downstream paths require different semantic completeness.

| Path | Required valid exports | Optional or zero-fill initially |
|---|---|---|
| `Deferred RT -> ToneMapping -> PostRender` | `radiance`, `first_hit_depth`, `motion_vector`, `normal_roughness` and the tone-mapped LDR from ToneMapping | split lighting, hit distances, clear/refraction/fog may be placeholder if not connected to denoisers/upscalers |
| `Deferred RT -> TemporalAccumulation -> ToneMapping -> PostRender` | `radiance`, `motion_vector`, `normal_roughness`, `first_hit_depth` | split lighting and hit distances |
| `Deferred RT -> NRD -> ToneMapping -> PostRender` | full NRD 14-input semantic set, especially split radiance, albedo, normal, motion, linear depth, `gi_hit_distance` and `specular_hit_depth` | clear, refraction and fog may start as deterministic zero only if documented as unsupported |
| `Deferred RT -> NRD -> FSR/XeSS -> ToneMapping -> PostRender` | NRD set plus render-resolution color/depth/motion/first-hit-depth/normal for the upscaler | DLSS-specific specular hit distance quality still needs validation |
| `Deferred RT -> DLSS -> ToneMapping -> PostRender` | color, albedo, specular albedo, normal/roughness, motion, linear depth, specular hit distance, first-hit depth | `gi_hit_distance` not needed by DLSS |

This means the first coding step is not GI. The first step is:

1. create the module shell,
2. define and document the internal output names,
3. implement the legacy export mapping that allocates and clears every public output deterministically.

### 4.5 Deferred-Only Internal Resources

Rasterized Deferred RT needs internal resources that are not part of the Java public module contract. These should be owned by `DeferredRtModule`, shaderpack runtime or the deferred graph, and exported only when a downstream module has a real consumer.

| Internal resource | Typical format | Purpose | Public export? |
|---|---:|---|---|
| `gbuffer:depth_attachment` | depth format | Raster depth test and framebuffer depth. | No. Export `primary_depth`/`primary_linear_depth` as color images instead. |
| `gbuffer:albedo_metallic` | `R8G8B8A8_UNORM` | Primary material base color and metallic. | Yes, through `diffuse_albedo_metallic`. |
| `gbuffer:specular_albedo` | `R8G8B8A8_UNORM` | Primary F0/specular guide. | Yes, through `specular_albedo`. |
| `gbuffer:normal_roughness` | `R16G16B16A16_SFLOAT` | Primary normal and roughness. | Yes, through `normal_roughness`. |
| `gbuffer:motion` | `R16G16_SFLOAT` | Primary reprojection vector. | Yes, through `motion_vector`. |
| `gbuffer:linear_depth` | `R16_SFLOAT` or `R32_SFLOAT` internally | Primary visible surface view depth. | Yes, through `linear_depth`, possibly down-converted to public format. |
| `gbuffer:primary_depth` | `R16_SFLOAT` or alias | First primary surface depth. | Yes, through `first_hit_depth`. |
| `gbuffer:material_id` | `R32_UINT` | Shader/material classification, debugging, material-table lookup. | No initially. |
| `gbuffer:object_id` | `R32_UINT` | Motion validation, disocclusion, selection/debug, per-object history. | No initially. |
| `visibility:primary_mask` | `R8_UINT` or bitset | Marks valid primary-surface pixels after depth/G-buffer and OpenXR visibility-mask clipping. | No. |
| `classification:lighting_queues` | buffers | Per-view compacted tile/pixel queues plus indirect dispatch arguments for direct, GI, reflection and transparent/refraction work. | No. |
| `history:*` | varies | Temporal accumulation, reprojection and denoiser helper state. | No unless a downstream module explicitly consumes it. |
| `reactive_mask` / `transparency_mask` | `R8_UNORM` or `R16_SFLOAT` | Future upscaler quality signals for transparency/particles. | Not in first contract; add later only if FSR/XeSS/DLSS integration requires it. |
| `exposure` / luminance buffers | buffer or small image | Auto exposure and tone mapping analysis. | No; ToneMapping owns its own exposure path today. |

Do not add a public output just because the raster module needs an internal G-buffer target. Public outputs are for cross-module contracts. Internal resources can be richer, higher precision, differently packed or backend-specific.

### 4.6 Java Module Definition

Add:

```text
Radiance/src/main/resources/modules/deferred_rt.yaml
```

Initial YAML should expose the PT-compatible downstream outputs from `ray_tracing.yaml`, plus one explicit Deferred RT output for diffuse/GI hit distance. This makes the NRD connection semantically clean instead of reusing `first_hit_depth` as a diffuse hit-distance stand-in.

```yaml
name: "render_pipeline.module.deferred_rt.name"
inputImageConfigs: [ ]
outputImageConfigs:
  - name: "radiance"
    format: "R16G16B16A16_SFLOAT"
  - name: "diffuse_albedo_metallic"
    format: "R8G8B8A8_UNORM"
  - name: "specular_albedo"
    format: "R8G8B8A8_UNORM"
  - name: "normal_roughness"
    format: "R16G16B16A16_SFLOAT"
  - name: "motion_vector"
    format: "R16G16_SFLOAT"
  - name: "linear_depth"
    format: "R16_SFLOAT"
  - name: "specular_hit_depth"
    format: "R16_SFLOAT"
  - name: "first_hit_depth"
    format: "R16_SFLOAT"
  - name: "first_hit_diffuse_direct_light"
    format: "R16G16B16A16_SFLOAT"
  - name: "first_hit_diffuse_indirect_light"
    format: "R16G16B16A16_SFLOAT"
  - name: "first_hit_specular"
    format: "R16G16B16A16_SFLOAT"
  - name: "first_hit_clear"
    format: "R16G16B16A16_SFLOAT"
  - name: "first_hit_base_emission"
    format: "R16G16B16A16_SFLOAT"
  - name: "fog_image"
    format: "R16G16B16A16_SFLOAT"
  - name: "first_hit_refraction"
    format: "R16G16B16A16_SFLOAT"
  - name: "gi_hit_distance"
    format: "R16_SFLOAT"
attributeConfigs:
  - name: "render_pipeline.module.deferred_rt.attribute.shader_pack_path"
    type: "string"
    value: ""
  - name: "render_pipeline.module.deferred_rt.attribute.use_jitter"
    type: "bool"
    value: "render_pipeline.true"
  - name: "render_pipeline.module.deferred_rt.attribute.gi_mode"
    type: "enum:off-single_bounce-sharc"
    value: "single_bounce"
  - name: "render_pipeline.module.deferred_rt.attribute.reflection_mode"
    type: "enum:off-ray_traced-screen_space-hybrid"
    value: "ray_traced"
  - name: "render_pipeline.module.deferred_rt.attribute.debug_view"
    type: "enum:off-albedo-normal-roughness-metallic-depth-motion-direct-indirect-specular"
    value: "off"
```

### 4.7 Native Module Class

Add:

```text
MCVR/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp
MCVR/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp
```

Class shape:

```cpp
class DeferredRtModule : public WorldModule, public SharedObject<DeferredRtModule> {
public:
    constexpr static std::string_view NAME = "render_pipeline.module.deferred_rt.name";
    constexpr static uint32_t inputImageNum = 0;
    constexpr static uint32_t outputImageNum = 16;

    void init(std::shared_ptr<Framework> framework,
              std::shared_ptr<WorldPipeline> worldPipeline);
    bool setOrCreateInputImages(..., uint32_t frameIndex) override;
    bool setOrCreateOutputImages(..., uint32_t frameIndex) override;
    void setAttributes(int attributeCount, std::vector<std::string>& attributeKVs) override;
    void build() override;
    std::vector<std::shared_ptr<WorldModuleContext>>& contexts() override;
    void bindTexture(std::shared_ptr<vk::Sampler> sampler,
                     std::shared_ptr<vk::DeviceLocalImage> image,
                     int index) override;
    void preClose() override;

private:
    void loadShaderPack();
    void initInternalOutputNames();
    void initLegacyExportMap();
    void initDescriptorTables();
    void initGBufferImages();
    void initScenePrepare();
    void initPasses();
    void initContexts();
};
```

Add registration to `Pipeline::collectWorldModules()`:

```cpp
worldModuleConstructors.insert(std::make_pair(
    DeferredRtModule::NAME,
    [](std::shared_ptr<Framework> framework, std::shared_ptr<WorldPipeline> worldPipeline) {
        return DeferredRtModule::create(framework, worldPipeline);
    }));
worldModuleInOutImageNums.insert(std::make_pair(
    DeferredRtModule::NAME,
    std::make_pair(DeferredRtModule::inputImageNum, DeferredRtModule::outputImageNum)));
```

### 4.8 Pipeline Presets

Current Java presets include the existing PT paths and the Deferred RT paths as separate selectable entries:

- `Deferred RT -> ToneMapping -> PostRender`
- `Deferred RT -> NRD -> TemporalAccumulation -> ToneMapping -> PostRender`
- `Deferred RT -> NRD -> FSR -> ToneMapping/PostRender`
- `Deferred RT -> NRD -> XeSS -> ToneMapping/PostRender`
- `Deferred RT -> DLSSRR -> ToneMapping/PostRender`

The existing PT presets remain unchanged. That is intentional: PT still uses its historical `ray_tracing.first_hit_depth -> nrd.diffuseHitDepthImage` connection until a separate PT migration is planned. Deferred RT must not copy that ambiguity. Its NRD presets connect `deferred_rt.gi_hit_distance -> nrd.diffuseHitDepthImage`, while `deferred_rt.first_hit_depth` remains primary-surface depth for PostRender and upscaler scene-fact propagation.

The preset UI enumerates available `Presets` values through `Pipeline.isPresetAvailable(...)`; adding a new preset should require one enum entry, availability rules, graph assembly, and translation keys. The default/fallback order still prefers the stable PT presets first, then falls back to Deferred RT presets if PT paths are unavailable. This prevents existing configs from silently moving to the experimental raster path while still allowing explicit dynamic switching between PT and Deferred RT.

### 4.9 Shaderpack Selection Interface

Java and native shaderpack selection are shared structurally, but the module owner decides the default and fallback policy.

Current implemented owner policy as of Step 32:

| Owner module | Path attribute | Empty path means | Built-in fallback |
|---|---|---|---|
| `ray_tracing` | `render_pipeline.module.ray_tracing.attribute.shader_pack_path` | built-in `shaders/world/ray_tracing/vanilla-pt.zip` | yes, preserves existing PT behavior |
| `deferred_rt` | `render_pipeline.module.deferred_rt.attribute.shader_pack_path` | no Deferred shaderpack loaded | no, explicit Deferred pack errors must not silently become PT |
| `post_render` | `render_pipeline.module.post_render.attribute.shader_pack_path` | built-in `shaders/world/ray_tracing/vanilla-pt.zip` | only when the path is empty; explicit invalid packs fail or fall back through Java validation |

`Pipeline.getShaderPackModule()` still returns the active primary shaderpack owner for the current graph, meaning PT or Deferred RT. `ShaderPackSettingsScreen` is intentionally not redesigned yet. During this transition, selecting a PT or Deferred pack also writes that path to other shaderpack-capable modules in the graph only when the pack exposes the target module's required stage. When multiple shaderpack-capable modules resolve to the same pack path, Java synchronizes the primary owner's dynamic shaderpack attribute values into the other same-pack modules before saving/building. This preserves the current single shaderpack settings UI while allowing native runtime ownership to be module-scoped.

Native `WorldPipeline` builds one `ShaderPack` runtime per owning module. PT, Deferred RT and PostRender each retrieve their own runtime through `shaderPackForModule(...)`. A single physical shaderpack file may contain `ray_tracing`, `deferred` and `post_render` metadata, but each module consumes only its own stage. Runtime resources are scoped to the owning stage for non-PT modules so PostRender does not instantiate PT-only textures or buffers from a shared PT/PostRender pack.

This is a selection and validation shell, not the final Deferred shaderpack runtime. It intentionally keeps current fixed Deferred RT passes unchanged until resource binding, view/layer execution and pass recording are implemented.

Open follow-ups:

- Expand the preset system/UI so one preset can configure its pipeline and each shaderpack-capable module's pack independently, then remove the temporary current-owner sync behavior.
- Decide whether module runtimes that load the same physical pack should share immutable loader/cache data while keeping runtime resources separate.
- Add a built-in `vanilla-deferred-rt` pack only after the fixed G-buffer and lighting passes have metadata-backed runtime equivalents.

### 4.10 VR / Layered Output Contract

`MCVR-custom` already has a stereo execution model:

```cpp
enum class StereoMode {
    SingleInstance3DDispatch,
    SingleInstanceMultiDispatch,
    DualInstance
};
```

`DeferredRtModule` should use this model from day one.

Recommended default:

```cpp
StereoMode stereoMode() const override {
    return StereoMode::SingleInstance3DDispatch;
}
```

Layer contract:

- `eyeCount == 1`: all public and internal images may be normal 2D images or one-layer 2D-array images.
- `eyeCount > 1`: all public outputs and all internal G-buffer/lighting/history images must be `2D_ARRAY` images with `layerCount == eyeCount`.
- Per-layer views must be created for every image that may be consumed by a module using `DualInstance` or per-eye native SDK integration.
- Full-image views must remain available for single 3D dispatch compute passes.
- Public output names do not change for VR; only the image layer count changes.

Current `MCVR-custom` convention:

```text
image view index 0        -> whole image / full 2D-array view
image view index 1 + eye  -> per-eye 2D layer view
array layer eye           -> output layer for that eye
```

The deferred module must follow this convention so downstream modules such as NRD, temporal accumulation, tone mapping, post render, DLSS/FSR, and VR submission can consume its outputs without format rewiring.

---

## 5. Scene Input Layer

### 5.1 Why This Layer Exists

The biggest compatibility risk is geometry ownership. Today geometry is captured by Radiance/MCVR mixins and uploaded to native buffers. Future mod compatibility may require consuming Blaze3D-style geometry, render layers, or render pass declarations.

To avoid rewriting shaderpacks and lighting logic later, `DeferredRtModule` should depend on a neutral scene provider.

### 5.2 Interfaces

Add:

```text
MCVR/src/core/render/modules/world/common/scene_provider.hpp
MCVR/src/core/render/modules/world/common/scene_provider.cpp
```

Core data:

```cpp
enum class SceneGeometryDomain {
    Chunk,
    Entity,
    Weather,
    Particle,
    Text,
    NameTag,
    Star
};

enum class SceneGeometryClass {
    Opaque,
    Cutout,
    Translucent,
    Additive,
    Overlay
};

struct SceneDrawRange {
    uint32_t firstIndex = 0;
    uint32_t indexCount = 0;
    int32_t vertexOffset = 0;
};

struct SceneMaterialBinding {
    uint32_t textureId = 0;
    uint32_t materialFlags = 0;
    uint32_t geometryType = 0;
    std::string groupName;
    std::string contentName;
};

struct SceneDrawPacket {
    SceneGeometryDomain domain;
    SceneGeometryClass geometryClass;
    World::VertexFormats vertexFormat;
    World::DrawMode drawMode;

    std::shared_ptr<vk::DeviceLocalBuffer> vertexBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> indexBuffer;
    VkIndexType indexType = VK_INDEX_TYPE_UINT32;
    SceneDrawRange range;
    SceneMaterialBinding material;

    glm::mat4 objectToWorld = glm::mat4(1.0f);
    glm::mat4 previousObjectToWorld = glm::mat4(1.0f);
};

struct SceneAccelerationData {
    std::shared_ptr<vk::TLAS> tlas;
    std::shared_ptr<vk::DeviceLocalBuffer> blasOffsetsBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> indexBufferAddressBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> positionBufferAddressBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> materialBufferAddressBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> previousIndexBufferAddressBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> previousPositionBufferAddressBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> previousObjectToWorldBuffer;
};

struct SceneViewData {
    uint32_t viewIndex = 0;
    uint32_t arrayLayer = 0;
    glm::mat4 view = glm::mat4(1.0f);
    glm::mat4 projection = glm::mat4(1.0f);
    glm::mat4 previousView = glm::mat4(1.0f);
    glm::mat4 previousProjection = glm::mat4(1.0f);
    glm::vec2 jitter = glm::vec2(0.0f);
    glm::vec2 previousJitter = glm::vec2(0.0f);
};

class SceneProvider {
public:
    virtual ~SceneProvider() = default;
    virtual void beginFrame(uint32_t frameIndex) = 0;
    virtual std::span<const SceneViewData> views() const = 0;
    virtual std::span<const SceneDrawPacket> opaqueDraws() const = 0;
    virtual std::span<const SceneDrawPacket> cutoutDraws() const = 0;
    virtual std::span<const SceneDrawPacket> translucentDraws() const = 0;
    virtual const SceneAccelerationData& accelerationData() const = 0;
};
```

Current implementation:

```cpp
class McvrSceneProvider : public SceneProvider {
    // Reads Renderer::instance().world()->chunks()/entities().
};
```

Future implementation:

```cpp
class Blaze3DSceneProvider : public SceneProvider {
    // Converts Blaze3D-owned buffers/render layers into SceneDrawPacket.
};
```

`DeferredRtModule` does not care which provider is active.

VR rule:

- Scene geometry and TLAS can normally be shared across all views in a frame.
- Camera data, projection data, jitter, previous matrices, motion-vector reconstruction, visibility masks and foveated parameters are per view.
- `SceneProvider::views()` is the only source of truth for `viewIndex -> arrayLayer` mapping.
- The current `WorldUBO` stereo fields from `MCVR-custom` can be used as an initial implementation path, but the deferred module should internally normalize them into `SceneViewData`.

### 5.3 Required Data Changes

Current chunk/entity render data is strong enough for ray tracing, but raster G-buffer needs draw ranges. Add or expose:

- per geometry `indexOffset`,
- per geometry `indexCount`,
- per geometry `vertexOffset`,
- per geometry `vertexCount`,
- per geometry `drawMode`,
- per geometry material/texture id,
- per geometry group name,
- current and previous object transform.

This should be added to `ChunkRenderData`, `Entity`, and relevant build data, rather than recomputed during rendering.

### 5.4 Current Java/Native Payload Boundary

The current Radiance/MCVR Java-to-native scene payload was built around the existing PT/hybrid renderer. It is useful for Deferred RT, but it is not a complete Blaze3D or Minecraft raster draw-call stream.

Current payload strengths:

- `PBRVertexConsumer` records a stable 128-byte PBR vertex with position, normal, color layer, texture UV, overlay/light/glint UVs, base texture id and packed `alphaMode`.
- `ChunkProxy` and `EntityProxy` already pass per-geometry `geometryType`, texture id, vertex format, index/draw mode metadata, vertex count and vertex buffer addresses.
- Native build code already packs position and material streams used by both PT shaders and the Deferred RT G-buffer path.
- `EntityProxy` also carries group/content names and per-entity transform-like metadata that can be retained by the scene provider.

Current payload limitations for faithful deferred rasterization:

- `Constants.GeometryTypes.getGeometryType()` collapses many `RenderLayer` cases into `WORLD_TRANSPARENT`. This loses the distinction between cutout, true translucent, additive, overlay, text-like and other special layers.
- The first `McvrSceneProvider` maps `WORLD_TRANSPARENT` to `SceneGeometryClass::Cutout` so the fixed G-buffer can alpha-test block cutout geometry. This is a bring-up policy, not a complete transparency model.
- Full raster state is not preserved as a native scene contract: blend mode, depth test, depth write, cull mode, polygon offset, color/depth write masks, render target, sort key/order and shader/material semantic are not available as stable fields.
- `alphaMode` exists in the packed material vertex, but Deferred RT needs a draw/material-level transparency policy, not only a per-vertex packed PT material bit.
- Chunk capture currently assumes quad-derived indexing. This is adequate for vanilla chunk layers, but it is not a general mesh or modded draw-command representation.
- The payload exposes texture ids and LabPBR mappings, but not a full material model for water, glass, refraction, transmission, portal, weather, text, particles or custom mod materials.

This means the current payload is sufficient for the first opaque/cutout G-buffer milestone, direct-light/ray-query bring-up and shared TLAS reuse. It is not sufficient for a fully faithful deferred raster path with Minecraft translucent sorting and broad mod compatibility.

### 5.5 Deferred Raster Payload Extension Policy

Do not solve this by exposing Java `RenderLayer`, Blaze3D classes, PT hit groups or raw Vulkan state to deferred shaderpacks. The extension belongs behind `SceneProvider`.

Add neutral provider-side records before adding user shaderpack dependence on them:

```cpp
enum class SceneBlendMode {
    Opaque,
    AlphaBlend,
    Additive,
    Multiply,
    Overlay,
    Custom
};

enum class SceneDepthPolicy {
    TestWrite,
    TestOnly,
    Always,
    Disabled
};

enum class SceneCullMode {
    Back,
    Front,
    None
};

enum class SceneAlphaMode {
    Opaque,
    Cutout,
    Translucent,
    Text,
    SeeThroughText
};

struct SceneRenderState {
    SceneAlphaMode alphaMode = SceneAlphaMode::Opaque;
    SceneBlendMode blendMode = SceneBlendMode::Opaque;
    SceneDepthPolicy depthPolicy = SceneDepthPolicy::TestWrite;
    SceneCullMode cullMode = SceneCullMode::Back;
    bool polygonOffset = false;
    float depthBiasScaleFactor = 0.0f;
    float depthBiasConstant = 0.0f;
    uint32_t colorWriteMask = 0xf;
    bool writesDepth = true;
    bool useLightmap = false;
    bool useOverlay = false;
    bool affectsOutline = false;
    bool affectsCrumbling = false;
    uint32_t outputTarget = 0;
    uint32_t pipelineSortKey = 0;
    uint64_t sortKey = 0;
};

enum SceneMaterialSemanticFlags : uint32_t {
    SceneMaterialWater = 1u << 0u,
    SceneMaterialGlass = 1u << 1u,
    SceneMaterialRefraction = 1u << 2u,
    SceneMaterialTransmission = 1u << 3u,
    SceneMaterialPortal = 1u << 4u,
    SceneMaterialWeather = 1u << 5u,
    SceneMaterialText = 1u << 6u
};
```

Then extend `SceneDrawPacket` or `SceneMaterialBinding` with:

- `SceneRenderState renderState`,
- neutral material semantic flags,
- explicit topology/index mode and draw order/sort key,
- optional source diagnostics for logging only, such as original render-layer name or content name.

Reference mapping from current Minecraft/Blaze3D sources under `G:\cpp\radiance\mcsrc`:

- `RenderPipeline` has the state that must become neutral provider metadata: depth test function, polygon mode, cull flag, blend function, color/alpha/depth write flags, depth bias, vertex format mode, pipeline sort key and optional stencil test.
- `RenderSetup` adds draw-routing metadata: output target, texture transform, lightmap/overlay use, outline/crumbling behavior, `sortOnUpload`, buffer size and layering transform.
- `ChunkSectionLayer` separates `SOLID`, `CUTOUT`, `TRANSLUCENT` and `TRIPWIRE`; only `TRANSLUCENT` and `TRIPWIRE` sort on upload.
- `ChunkSectionLayerGroup` routes opaque layers to the main target, translucent layers to the translucent target, and tripwire to the weather target.
- `MultiBufferSource.BufferSource` may sort quads when `RenderType.sortOnUpload()` is true before draw submission.
- `LevelRenderer` runs main, translucent, particles, weather and clouds as distinct frame-graph passes/targets, and schedules translucent section resorting from camera motion.

Provider metadata must preserve enough of those facts to keep Deferred RT compatible with PT scene capture and with future Blaze3D-backed paths. It does not need to mirror Java classes one-for-one, and it must not expose those Java classes to shaderpack syntax.

Current implemented ABI, as of Step 24:

- Java realtime chunk/entity uploads use a versioned per-geometry `int` record:
  - `version = 1`,
  - `intStride = 14`,
  - fields: alpha mode, blend mode, depth policy, cull mode, render-state flags, write mask, output target class, layering class, semantic flags, pipeline sort key, polygon offset factor, polygon offset units, stencil flags and one reserved word.
- Old native upload entry points remain available. Replay and any old payload path that does not provide metadata is classified through the previous conservative fallback.
- `SceneMaterialBinding` carries the parsed neutral `SceneRasterMetadata`, and `McvrSceneProvider` uses it to classify opaque, cutout, translucent, additive and overlay packets.
- When no metadata is present, `WORLD_TRANSPARENT` still maps to `SceneGeometryClass::Cutout` for first G-buffer compatibility, and provider stats count this as `legacyTransparentCutoutFallbackPackets`.
- The current Java adapter derives metadata from the public `RenderLayer.MultiPhase` fields that are available in this Minecraft/Yarn version (`texture`, `transparency`, `cull`, `isTranslucent()`) plus layer-name conventions. It is a compatibility bridge, not a full Blaze3D `RenderPipeline` mirror.
- The compact record is per geometry/draw, not per vertex. It does not duplicate vertex buffers, material buffers, BLAS or TLAS preparation.

Remaining adapter work:

- Add a more exact Java-side adapter or mixin surface for depth test, depth write, write masks, output target, overlay/lightmap, outline/crumbling, layering and `sortOnUpload` instead of relying on layer-name inference.
- Extend replay capture if offline replay needs to test metadata-sensitive transparency behavior. Current replay stays on the old payload path and intentionally exercises fallback classification.
- `DeferredRtModule::latestDiagnosticsSnapshot()` now provides a native, frame-latency-aware snapshot combining provider metadata/fallback stats, classification stats and lighting pass stats.
- As of Step 26, `render_pipeline.module.deferred_rt.attribute.diagnostics_log` and `render_pipeline.module.deferred_rt.attribute.diagnostics_log_interval` expose a default-off native log path for that snapshot. The throttle is based on completed diagnostics snapshot sequence numbers rather than swapchain frame slots.
- As of Step 27, the same snapshot also has a compact native overlay formatter exposed through `Pipeline.getDeferredRtDiagnosticsOverlay()` and shown in the existing F3 debug text when a Deferred RT snapshot is valid.
- Remaining diagnostics work is to add a dedicated in-game diagnostics UI or configurable overlay, feed the snapshot into the future offline runner/frame dump, and add pass-specific transparent/refraction counters after `transparent_forward` exists.

Rules:

- Shaderpacks see neutral content streams such as `world_opaque`, `world_cutout`, `entity_cutout`, `transparent_forward`, `text`, `weather` and `particle`.
- Shaderpacks must not require Minecraft class names or Java `RenderLayer` names. Group/content names may be available for opt-in filtering and diagnostics, but they are not the core contract.
- Old Java/native payloads must map to conservative defaults: opaque/cutout G-buffer only, translucent content routed to an unsupported or forward-later bucket with debug counters.
- Any future `Blaze3DSceneProvider` must populate the same neutral fields from Blaze3D buffers and render-state declarations.
- Do not force PT to consume or produce the full raster metadata. PT should continue to use the shared geometry/material/AS data it needs, while Deferred RT consumes the additional render-state fields when present.

---

## 6. ScenePrepare Extraction

The existing `WorldPrepare` under the PT module is useful but owned by `RayTracingModule`. It should become a shared component:

```text
MCVR/src/core/render/modules/world/common/scene_prepare.hpp
MCVR/src/core/render/modules/world/common/scene_prepare.cpp
```

Responsibilities:

1. Schedule finished chunk BLAS builds.
2. Submit entity BLAS builders.
3. Build per-frame TLAS.
4. Upload metadata buffers:
   - BLAS offsets,
   - index buffer addresses,
   - position buffer addresses,
   - material buffer addresses,
   - previous frame addresses,
   - previous transforms.
5. Provide optional PT-only hit metadata for `RayTracingModule`.

The shared `ScenePrepare` may still carry data needed by the existing PT module, but `DeferredRtModule` must not expose SBT, hit groups, closest-hit shaders, any-hit shaders, or miss shaders in its public shaderpack contract.

The runtime owner should be the `WorldPipeline` or a pipeline-level scene runtime object, not individual lighting modules. Dynamic pipeline switching is supported by the Java pipeline configuration path, and future presets may switch between PT and Deferred RT. The renderer should therefore keep a single scene preparation service per `WorldPipeline` instance and let active modules reference it.

```text
WorldPipeline
  SharedSceneRuntime
    ScenePrepare
    optional McvrSceneProvider

RayTracingModule
  requests Acceleration
  uses SharedSceneRuntime.ScenePrepareContext
  keeps PT-only SBT/hit-group setup in the PT module

DeferredRtModule
  requests Acceleration | Provider
  uses SharedSceneRuntime.ScenePrepareContext
  uses SharedSceneRuntime.SceneProvider packets/views/AS metadata
```

This avoids duplicating the most fragile scene synchronization code and avoids duplicated BLAS/TLAS submission when a pipeline contains both PT and Deferred RT for debugging, transition, or A/B comparison.

Implementation note: Step 42 moves `ScenePrepare` ownership to `WorldPipeline` scope through `SharedSceneRuntime`.
`RayTracingModule` and `DeferredRtModule` no longer create their own `ScenePrepare` instances.

---

## 7. G-buffer Design

### 7.1 G-buffer Targets

Internal G-buffer targets should be separate from public module outputs. The module can export a subset or transformed version for downstream modules.

Recommended internal targets:

| Name | Format | Purpose |
|---|---|---|
| `gbuffer:albedo_metallic` | `R8G8B8A8_UNORM` | base color RGB, metallic A |
| `gbuffer:normal_roughness` | `R16G16B16A16_SFLOAT` | world/view normal XYZ, roughness A |
| `gbuffer:emission_flags` | `R16G16B16A16_SFLOAT` or `R8G8B8A8_UNORM` | emission RGB, flags/material class |
| `gbuffer:motion` | `R16G16_SFLOAT` | screen-space motion vectors |
| `gbuffer:linear_depth` | `R16_SFLOAT` initially, `R32_SFLOAT` optional | linear depth |
| `gbuffer:material_id` | `R32_UINT` optional | material/texture/debug id |
| `gbuffer:depth` | depth format | raster depth attachment |

Downstream exports:

```text
out:diffuse_albedo_metallic  <- gbuffer:albedo_metallic
out:normal_roughness         <- gbuffer:normal_roughness
out:motion_vector            <- gbuffer:motion
out:linear_depth             <- gbuffer:linear_depth
out:first_hit_depth          <- gbuffer:linear_depth or RT-corrected first hit depth
```

Layering:

- Every internal target above must support `layerCount == viewCount`.
- In mono mode, `viewCount == 1`.
- In VR mode, `viewCount == eyeCount`, normally 2.
- The same resource names are used in shaderpacks; shaders select the layer through the execution context.

Primary G-buffer fidelity rule:

- Inside the visible render area, primary G-buffer targets should remain full resolution in the first versions.
- Foveated rendering must not merge or downsample depth, normal, material, motion or object identity in the primary G-buffer path.
- The G-buffer stores geometric facts used by ray origin reconstruction, denoising, temporal reprojection, upscalers and VR stereo matching. Reducing it independently creates edge leaks, wrong normals, invalid material flags, unstable motion vectors and binocular mismatch.
- Variable-rate work should start after the G-buffer: lighting, RTGI, reflections, denoise sample count, checkerboard/half-rate passes and final upscale.

OpenXR visibility masks are different from foveated quality reduction. Pixels outside the headset visibility mask are never displayed, so they may be clipped deterministically:

- raster passes may scissor/stencil/discard masked-out pixels,
- G-buffer values outside the mask should be cleared to explicit invalid defaults,
- lighting, denoising and temporal passes must treat those pixels as invalid and avoid sampling them as neighbors,
- the mask is per view/layer and must not be shared blindly between eyes.
- The OpenXR bridge may expose the hidden-area mesh as per-eye vertices/indices from `XR_KHR_visibility_mask`. Deferred RT should consume that data by rasterizing a per-eye hidden/valid mask or stencil before G-buffer/classification. This belongs in Deferred RT and shared VR support, not in the PT module.

### 7.2 Raster Passes

Minimum first version:

1. `gbuffer_world_opaque`
2. `gbuffer_world_cutout`
3. `gbuffer_entities_opaque`
4. `gbuffer_entities_cutout`

Later:

5. `gbuffer_particles`
6. `transparent_forward`
7. `weighted_transparency`
8. `decal/material override pass`

Stereo execution options:

| Mode | Use when | Notes |
|---|---|---|
| Multiview raster | backend supports layered/multiview render pass | one draw stream writes both eyes/layers |
| Per-layer raster fallback | backend lacks multiview or pass is simpler per-eye | loop `viewIndex`, bind per-layer framebuffer/view |
| Dual-instance fallback | pass depends on external SDK per-eye execution | same compatibility path as existing VR-aware modules |

The shaderpack should not decide which mode is used. It declares a `render` pass; the backend decides whether to execute it through multiview or per-layer fallback.

Raster shaders must receive:

- `viewIndex`,
- `arrayLayer`,
- current view/projection,
- previous view/projection,
- per-view jitter and previous jitter.

Motion vectors must be generated per view. Reusing mono motion vectors across eyes is incorrect because asymmetric eye projections and view offsets change clip-space velocity.

### 7.3 Cutout and Translucency Policy

For compatibility and velocity:

- Opaque and cutout are part of deferred G-buffer.
- Translucent surfaces are not part of the first G-buffer path.
- Water/ice/glass should initially use either:
  - forward/post render path, or
  - a separate `transparent_rt` pass after opaque lighting.

Reason: Minecraft translucent sorting and modded transparent geometry are compatibility-heavy. Treating it separately makes the first module shippable.

---

## 8. Lighting Design

After G-buffer:

```text
G-buffer
   |
   +-- RT direct lighting/shadows
   +-- RT reflections
   +-- RT GI or SHARC query/update
   +-- volumetric/fog optional
   |
Compose
   |
NRD/DLSS/FSR/XeSS/Temporal/ToneMapping/PostRender
```

### 8.1 Direct Lighting

Initial:

- sun/moon directional light visibility through TLAS,
- emissive block direct-light sample if `collectChunkEmission` is enabled,
- sky/ambient fallback.

Outputs:

- `out:first_hit_diffuse_direct_light`
- `out:first_hit_specular`
- optionally direct component into `out:radiance` for non-NRD mode.

### 8.2 GI

Implement in stages:

1. `off`: ambient/skylight only.
2. `single_bounce`: one stochastic ray per pixel, temporally accumulated/denoised.
3. `sharc`: reuse current SHARC runtime concepts.
4. Optional future: probe grid or reservoir GI.

### 8.3 Reflections

Modes:

- `off`
- `screen_space`
- `ray_traced`
- `hybrid`

First implementation should use ray-traced reflections directly, because TLAS is already required for GI/shadows.

### 8.4 Denoising

Keep compatibility with existing NRD by preserving expected buffers:

- diffuse radiance,
- specular radiance,
- direct radiance if needed,
- albedo,
- normal roughness,
- motion,
- linear depth,
- hit distances.

If the new module cannot fill a buffer accurately in phase 1, fill a conservative fallback and mark the task explicitly. Do not silently change downstream assumptions.

Anti-aliasing and upscaling are downstream contracts, not a separate deferred-module AA implementation. Deferred RT should not add an internal TAA resolve on top of DLSS/FSR/XeSS/TemporalAccumulation unless a later design explicitly replaces those modules.

Deferred RT is responsible for providing stable inputs:

- radiance and lighting outputs with clear resolution semantics,
- linear depth and first-hit depth,
- normal/roughness,
- motion vectors generated per view,
- jitter and previous jitter metadata,
- material/reactive/disocclusion masks if downstream modules require them.

Downstream DLSS, FSR, XeSS, TemporalAccumulation, NRD, tone mapping and post-render modules remain responsible for temporal resolve, upscaling and final presentation.

### 8.5 Project Lighting Path

For this project, do not start from "every pixel fires every ray". Start from a staged lighting path:

```text
G-buffer
  -> classify pixels/materials
  -> direct visibility rays
  -> reflection rays for selected pixels
  -> GI rays/cache for selected pixels
  -> denoise/accumulate
  -> compose
  -> transparent/forward
```

RT is used as a secondary visibility query system:

| Signal | First implementation | When to fire rays | When to skip/fallback |
|---|---|---|---|
| Sun/moon shadow | yes | opaque/cutout pixels with valid depth | sky pixels, unlit materials, debug modes |
| Emissive/block direct light | later phase | pixels/tile near emissive candidates | no emissive candidates, low quality mode |
| Reflection | yes, simple | low roughness or metallic/specular pixels | high roughness, sky, diffuse-only blocks |
| Diffuse GI | yes, conservative | low-resolution or checkerboard selected pixels | GI off, stable probe/cache available |
| AO | optional | near-field contact only | if GI already supplies enough occlusion |
| Transparent refraction | not first version | water/glass specialized pass | particles, generic modded translucency |

The first usable version should implement:

1. `classify_deferred_pixels.comp`
   - reads depth, normal, roughness, material flags,
   - writes masks or compact counters for shadow/reflection/GI eligibility,
   - runs layered with `dispatchZ = viewCount`.

2. `direct_lighting.rq.comp`
   - one visibility query for sun/moon shadow where the pixel is eligible,
   - no per-light explosion,
   - early-out for sky/background/materials that do not receive direct light.

3. `reflection_lighting.rq.comp`
   - ray query only for selected low-roughness/specular pixels,
   - first version may run half resolution or checkerboard,
   - high roughness falls back to sky/probe/SSR.

4. `gi_lighting.rq.comp`
   - first version should be conservative: one ray, low resolution, temporally accumulated,
   - later replace or augment with SHARC/probes/reservoirs.

5. `compose.comp`
   - combines albedo, direct, indirect, specular, emission and fog per layer.

### 8.6 Workload Scheduling

Workload will be uneven. That is expected. The architecture should manage it explicitly.

Bring-up scheduling already implemented:

- Full-screen layered dispatch for each ray-query pass.
- Pixel-level early-out from the classification mask.
- Simple masks in G-buffer/material flags.
- Stable deterministic random sequence per pixel/view/frame.
- No `vkCmdDispatchIndirect` dependency.

This is acceptable only for first correctness bring-up. Do not add a new "tile early-out but still full dispatch" milestone as the main optimization path. Once work is classified per tile, the next useful step is to compact active work and dispatch only those queued entries. Pixel-level checks should remain as defensive validation for stale queue entries, bounds and debugging, not as the scheduling mechanism.

Production scheduling phase 1:

- Tile classification, for example 8x8 or 16x16.
- Per-view/per-layer queue builders classify and compact active tile records for:
  - has shadow receivers,
  - has reflective pixels,
  - has GI candidates,
  - has transparent/refraction candidates.
- Build one queue and indirect dispatch argument buffer per expensive path, at minimum:
  - `direct_light_tiles`,
  - `reflection_tiles`,
  - `gi_tiles`,
  - `refraction_tiles`.
- Pass shaders run over queued tile records and then iterate pixels inside that tile.
- Keep the primary G-buffer full resolution; tile classification controls secondary work only.

Production scheduling phase 2:

- Optionally refine from tile queues to pixel queues for very sparse work such as glossy reflections, emissive probes or refraction.
- Use indirect dispatch where the backend supports it; where it does not, use an explicit queued-count fallback path rather than returning to full-screen work as the normal route.
- Allow separate resolution scales:
  - shadows full resolution,
  - reflections half resolution or checkerboard,
  - GI half/quarter resolution plus temporal accumulation.
- Maintain per-view/tier lists so VR eyes, visibility masks and foveated rings can have different active work without changing the public output image contract.

OpenXR visibility masks feed this scheduler before queue construction. Masked-out pixels must not generate queue entries. Foveation should also be represented as queue density, resolution tier or sample-count policy for secondary work, not as reduced primary G-buffer fidelity.

### 8.7 Transparent Rendering Path

Transparency should not block the deferred module.

First version:

- Opaque blocks and entities: G-buffer + ray-query lighting.
- Cutout blocks and entities: G-buffer + alpha test + ray-query lighting.
- Generic translucent geometry: forward pass after opaque compose.
- Particles, text, weather, overlays: forward/post paths.
- Ray queries trace opaque and cutout geometry only.

Second version:

- Add `transparent_forward` shaderpack content.
- Add water/glass material classification.
- Add screen-space refraction for water/glass.
- Allow transparent surfaces to sample opaque lighting and depth.

Third version:

- Add optional `transparent_ray_query` pass for selected water/glass/refraction.
- Consider a separate transparent acceleration representation only if the material model and sorting rules are stable.

This keeps compatibility with Minecraft/modded translucent sorting. Full physically correct transparent RT should not be an early milestone.

---

## 9. Shaderpack Architecture

### 9.1 Relationship to Current MCVR Shaderpack

The existing MCVR shaderpack system already supports:

- attributes,
- language/translations,
- runtime textures,
- runtime buffers,
- full-screen passes,
- render passes,
- ray tracing passes,
- compute passes,
- execution commands,
- conditional execution,
- loop execution,
- shader compile definitions.

The new module should reuse this implementation. It should not introduce a second shaderpack format.

### 9.2 Add Deferred Stage

Extend:

```cpp
enum class ShaderPackLoader::Stage {
    RayTracing,
    Deferred,
    PostRender,
};
```

Add config key:

```json
"execution_deferred": {
  "global_variables": [],
  "commands": []
}
```

Keep existing keys:

- `attributes`,
- `textures`,
- `buffers`,
- `passes`.

Add stage filtering:

```json
{
  "type": "render",
  "stage": "deferred",
  "name": "gbuffer_world_opaque",
  "content": "world_opaque"
}
```

### 9.3 Deferred Pass Types

Supported pass types:

| Type | Required for first version | Purpose |
|---|---:|---|
| `render` | yes | G-buffer and forward transparent rendering |
| `ray_query` | yes | compute pass that can issue shadow, reflection and GI queries |
| `compute` | yes | classify, compose, temporal, filters, tile classification |
| `full_screen` | yes | debug views, simple compose, LUTs |

Do not add `ray_tracing`, `rgen`, `rchit`, `rahit`, `rmiss`, `hit_groups`, callable shaders, or SBT layout to the deferred shaderpack API. Those concepts belong to the existing PT module. A native backend may internally lower a ray-query pass to whatever the platform supports, but the user-facing deferred contract should stay compute/ray-query shaped.

### 9.4 Render Pass Content Values

Add content values for deferred render passes:

```text
world_opaque
world_cutout
entity_opaque
entity_cutout
transparent
particle
weather
text
name_tag
star
```

The shaderpack selects which geometry stream to render. The scene provider decides how those streams are populated.

### 9.5 Built-In Deferred Shaderpack

Add:

```text
MCVR/src/shader/world/deferred_rt/internal/vanilla-deferred-rt/configs.json
```

Minimum pass graph:

```json
{
  "radiance": {
    "shader_pack": true,
    "display_name": "Vanilla Deferred RT"
  },
  "attributes": [],
  "textures": [],
  "buffers": [],
  "execution_deferred": {
    "commands": [
      { "pass": "gbuffer_world_opaque" },
      { "pass": "gbuffer_world_cutout" },
      { "pass": "gbuffer_entities_opaque" },
      { "pass": "classify_deferred_pixels" },
      { "pass": "direct_lighting" },
      { "pass": "reflection_lighting" },
      { "pass": "gi_lighting" },
      { "pass": "compose" }
    ]
  },
  "passes": [
    {
      "stage": "deferred",
      "type": "render",
      "name": "gbuffer_world_opaque",
      "content": "world_opaque",
      "vertex": "gbuffer/gbuffer_world.vert",
      "fragment": "gbuffer/gbuffer_world.frag",
      "outputs": {
        "images": [
          "gbuffer:albedo_metallic",
          "gbuffer:normal_roughness",
          "gbuffer:emission_flags",
          "gbuffer:motion",
          "gbuffer:linear_depth"
        ]
      },
      "depth_test": true,
      "depth_write": true,
      "depth_compare": "less"
    },
    {
      "stage": "deferred",
      "type": "compute",
      "name": "classify_deferred_pixels",
      "inputs": {
        "images": [
          "gbuffer:albedo_metallic",
          "gbuffer:normal_roughness",
          "gbuffer:emission_flags",
          "gbuffer:linear_depth"
        ]
      },
      "outputs": {
        "buffers": [
          "runtime:deferred_pixel_mask",
          "runtime:deferred_tile_mask"
        ]
      },
      "compute": "lighting/classify_deferred_pixels.comp",
      "view_execution": "layered",
      "gx": "(RENDER_WIDTH + 7) / 8",
      "gy": "(RENDER_HEIGHT + 7) / 8",
      "gz": "VIEW_COUNT"
    },
    {
      "stage": "deferred",
      "type": "ray_query",
      "name": "direct_lighting",
      "inputs": {
        "images": [
          "gbuffer:albedo_metallic",
          "gbuffer:normal_roughness",
          "gbuffer:emission_flags",
          "gbuffer:motion",
          "gbuffer:linear_depth"
        ],
        "buffers": [
          "runtime:deferred_pixel_mask"
        ]
      },
      "outputs": {
        "images": [
          "out:first_hit_diffuse_direct_light",
          "out:fog_image"
        ]
      },
      "shader": "lighting/direct_lighting.rq.comp",
      "fallback_shader": "lighting/direct_lighting_fallback.comp",
      "queries": ["visibility"],
      "view_execution": "layered",
      "gx": "(RENDER_WIDTH + 7) / 8",
      "gy": "(RENDER_HEIGHT + 7) / 8",
      "gz": "VIEW_COUNT"
    },
    {
      "stage": "deferred",
      "type": "ray_query",
      "name": "reflection_lighting",
      "inputs": {
        "images": [
          "gbuffer:albedo_metallic",
          "gbuffer:normal_roughness",
          "gbuffer:linear_depth"
        ],
        "buffers": [
          "runtime:deferred_pixel_mask"
        ]
      },
      "outputs": {
        "images": [
          "out:first_hit_specular",
          "out:specular_hit_depth"
        ]
      },
      "shader": "lighting/reflection_lighting.rq.comp",
      "fallback_shader": "lighting/reflection_lighting_fallback.comp",
      "queries": ["closest_hit"],
      "view_execution": "layered",
      "gx": "(RENDER_WIDTH + 7) / 8",
      "gy": "(RENDER_HEIGHT + 7) / 8",
      "gz": "VIEW_COUNT"
    },
    {
      "stage": "deferred",
      "type": "ray_query",
      "name": "gi_lighting",
      "inputs": {
        "images": [
          "gbuffer:albedo_metallic",
          "gbuffer:normal_roughness",
          "gbuffer:linear_depth"
        ],
        "buffers": [
          "runtime:deferred_pixel_mask"
        ]
      },
      "outputs": {
        "images": [
          "out:first_hit_diffuse_indirect_light"
        ]
      },
      "shader": "lighting/gi_lighting.rq.comp",
      "fallback_shader": "lighting/gi_lighting_fallback.comp",
      "queries": ["closest_hit"],
      "view_execution": "layered",
      "gx": "(RENDER_WIDTH + 7) / 8",
      "gy": "(RENDER_HEIGHT + 7) / 8",
      "gz": "VIEW_COUNT"
    },
    {
      "stage": "deferred",
      "type": "compute",
      "name": "compose",
      "compute": "compose/compose.comp",
      "inputs": {
        "images": [
          "gbuffer:albedo_metallic",
          "out:first_hit_diffuse_direct_light",
          "out:first_hit_diffuse_indirect_light",
          "out:first_hit_specular",
          "out:fog_image"
        ]
      },
      "outputs": {
        "images": [
          "out:radiance",
          "out:diffuse_albedo_metallic",
          "out:specular_albedo",
          "out:normal_roughness",
          "out:motion_vector",
          "out:linear_depth",
          "out:first_hit_depth"
        ]
      },
      "view_execution": "layered",
      "gx": "(RENDER_WIDTH + 7) / 8",
      "gy": "(RENDER_HEIGHT + 7) / 8",
      "gz": "VIEW_COUNT"
    }
  ]
}
```

The important part is that `ray_query` is a compute-shaped pass. It is not the same thing as the existing PT module's ray tracing pass. The shader sees a stable query API, not platform-specific hit shader or SBT structures.

`view_execution: "layered"` means the pass is run for every active view/layer. For compute and ray-query passes, the default implementation is a 3D dispatch where `global.z` selects the view. For raster passes, the backend may use multiview render pass support or per-layer fallback. Shaderpack authors should write layer-aware shaders, but they should not write a separate left-eye/right-eye pipeline.

```text
Vulkan native backend  -> compile query shader to SPIR-V with ray query support when available
Metal native backend   -> compile/translate query shader to Metal compute + acceleration structure calls
DX12 backend           -> compile query shader to HLSL inline raytracing / RayQuery
WebGPU backend         -> run fallback/probe/screen-space path until WebGPU exposes usable RT queries
MoltenVK backend       -> prefer non-RT fallback unless the active MoltenVK/Metal stack exposes required features
```

### 9.6 Shaderpack Validation

Validation should reject:

- unknown pass names in `execution_deferred`,
- pass cycles if a static dependency order is computed,
- unknown input/output resources,
- imported textures used as outputs,
- missing shader files,
- render pass without content,
- `ray_query` pass without a query shader or fallback shader,
- `ray_query` pass that references hit shader, miss shader, callable shader, hit group, SBT, or ray generation concepts,
- `ray_query` pass that uses query kinds not supported by the selected backend,
- compute/fullscreen pass without dispatch size,
- output format mismatch against module contract,
- unsupported backend-specific keys for the selected runtime.

This validation must run offline in tests and at runtime during module build.

---

## 10. Backend Execution Model

### 10.1 Internal Pass IR

Add a module-local IR before Vulkan objects are created:

```cpp
enum class DeferredPassType {
    Render,
    FullScreen,
    Compute,
    RayQuery
};

enum class DeferredViewExecution {
    Mono,
    Layered,
    PerView
};

struct DeferredResourceRef {
    std::string name;
    bool writable = false;
};

struct DeferredPassDesc {
    std::string name;
    DeferredPassType type;
    std::vector<DeferredResourceRef> reads;
    std::vector<DeferredResourceRef> writes;
    std::string content; // for render passes
    std::vector<std::string> queryKinds; // for ray_query passes
    DeferredViewExecution viewExecution = DeferredViewExecution::Layered;
};
```

The IR has no Vulkan types and no Blaze3D types. It is what future compatibility work should target.

### 10.2 Current Vulkan Backend

The current backend maps IR to:

- `vk::RenderPass`
- `vk::Framebuffer`
- `vk::GraphicsPipeline`
- `vk::ComputePipeline`
- `vk::DescriptorTable`
- `vk::CommandBuffer` barriers and dispatch/draw calls

This can follow patterns from `PostRenderModule` and the shaderpack runtime. It should reuse scene acceleration data from the PT side, but it should not reuse the PT module's public ray tracing pass model.

The Vulkan backend for `DeferredPassType::RayQuery` should be implemented as a compute pipeline. If Vulkan ray query support is available, the shader can issue ray queries from compute. If it is unavailable, the same pass descriptor falls back to its declared fallback shader.

For `DeferredViewExecution::Layered`, the backend records one layered pass or one 3D dispatch:

```text
dispatchX = ceil(width / groupSizeX)
dispatchY = ceil(height / groupSizeY)
dispatchZ = viewCount
```

The shader derives:

```text
pixel.x    = globalInvocationId.x
pixel.y    = globalInvocationId.y
viewIndex  = globalInvocationId.z
arrayLayer = sceneViews[viewIndex].arrayLayer
```

This is the preferred path for ray-query lighting because it lets both eyes share descriptors, scene acceleration data, material buffers and pass scheduling.

### 10.3 Future Blaze3D Mapping

Only raster pass execution should be mapped to Blaze3D initially:

```text
DeferredPassDesc(Render, content=world_opaque)
  -> Blaze3D RenderPipeline + RenderPass draw packets
```

Ray-query compute passes remain native until Blaze3D exposes compatible compute/RT concepts or Radiance provides its own extension. Blaze3D compatibility is still primarily about feeding the same G-buffer draw packets.

---

## 11. Cross-Platform RT Compatibility

### 11.1 Why Deferred RT Should Standardize on Ray Query

The existing PT module is built around Vulkan ray tracing pipelines:

```text
rgen + rmiss + rchit/rahit + SBT + vkCmdTraceRaysKHR
```

This model should not be treated as the universal deferred-rendering abstraction. It maps well to desktop Vulkan/DXR-class path tracing, but it is a poor public contract for:

- Metal and Apple Silicon,
- MoltenVK on macOS,
- WebGPU,
- DX12 inline raytracing,
- future Blaze3D-facing compatibility layers.

A deferred renderer already has its primary hit from the rasterized G-buffer. It normally needs secondary visibility queries:

```text
Is this shadow ray blocked?
What surface did this reflection ray hit?
What surface did this GI ray hit?
```

That is exactly the shape of ray query / inline raytracing / compute intersection APIs. Therefore the public Deferred RT contract should be:

```text
Raster G-buffer
  -> compute ray-query passes for direct light, reflection and GI
  -> compute/fullscreen compose
```

It should not expose:

- `rgen`,
- `rchit`,
- `rahit`,
- `rmiss`,
- `hit_groups`,
- SBT layout,
- callable shaders,
- recursive path tracing shader organization.

Those concepts can remain in the existing PT module.

### 11.2 Backend Capability Tiers

Add an explicit RT capability model:

```cpp
enum class RtBackendTier {
    None,
    RayQuery
};

struct RtBackendCapabilities {
    RtBackendTier tier;
    bool supportsAccelerationStructures = false;
    bool supportsVisibilityQuery = false;
    bool supportsClosestHitQuery = false;
    bool supportsCommittedTriangleHit = false;
    bool supportsAlphaTestDuringQuery = false;
    bool supportsDynamicGeometryUpdate = false;
    bool supportsInlineRayQueryInCompute = false;
    bool supportsNativeRayPipelineInternally = false;
};
```

Recommended tiers:

| Tier | Backend examples | Supported module behavior |
|---|---|---|
| `None` | no RT support, old/limited GPU | screen-space reflections, SSAO/probe GI, shadow maps or no RT shadows |
| `RayQuery` | Metal compute RT, Vulkan ray query, DX12 inline raytracing, future compatible APIs | G-buffer-driven shadow/GI/reflection compute passes |

The new deferred module should target `RayQuery` as its only RT-capable public tier. The current PT module can remain Vulkan RT pipeline only.

### 11.3 Logical RT Passes

Do not model deferred RT lighting internally as "a Vulkan ray tracing pass". Model it as compute work that may issue ray queries:

```cpp
enum class DeferredQueryKind {
    Visibility,
    ClosestHit
};

struct DeferredRayQueryPassDesc {
    std::string name;
    std::vector<DeferredResourceRef> reads;
    std::vector<DeferredResourceRef> writes;
    std::string queryShader;
    std::string fallbackShader;
    std::vector<DeferredQueryKind> queryKinds;
    DeferredViewExecution viewExecution = DeferredViewExecution::Layered;
};
```

Backend mapping:

```text
DeferredRayQueryPassDesc
  -> VulkanRayQueryBackend   uses compute shader + ray query support
  -> MetalRayQueryBackend    uses Metal compute + acceleration structure intersection
  -> DX12RayQueryBackend     uses compute shader + inline RayQuery
  -> WebGpuFallbackBackend   uses screen-space/probe fallback until WebGPU has compatible RT
  -> ScreenSpaceBackend      uses SSR/SSAO/probes
```

The pass graph stays the same. Only the shader translation and query intrinsic layer changes.

### 11.4 Shaderpack Contract for Cross-Platform Ray Query

Shaderpacks should describe ray-query compute passes, not backend-specific ray pipeline objects.

Recommended config shape:

```json
{
  "stage": "deferred",
  "type": "ray_query",
  "name": "reflection_lighting",
  "inputs": {
    "images": [
      "gbuffer:albedo_metallic",
      "gbuffer:normal_roughness",
      "gbuffer:linear_depth"
    ]
  },
  "outputs": {
    "images": [
      "out:first_hit_specular",
      "out:specular_hit_depth"
    ]
  },
  "shader": "lighting/reflection_lighting.rq.comp",
  "fallback_shader": "lighting/reflection_lighting_fallback.comp",
  "queries": ["closest_hit"],
  "view_execution": "layered",
  "gx": "(RENDER_WIDTH + 7) / 8",
  "gy": "(RENDER_HEIGHT + 7) / 8",
  "gz": "VIEW_COUNT"
}
```

Selection rule:

1. Use the ray-query shader when the selected backend supports the required `queries`.
2. Use the fallback shader when the backend has no acceleration structure or no compatible query API.
3. Fail module build only if the pass is required, the selected quality mode requires true RT, and no compatible query path exists.

This keeps shaderpacks customizable while making platform requirements explicit.

### 11.5 Platform Notes

| Platform target | Deferred RT policy |
|---|---|
| Metal / Apple Silicon | Primary target for ray-query compute. Avoid SBT and hit shader concepts entirely. |
| MoltenVK | Treat as Vulkan raster/compute first. Use ray query only if the actual stack exposes the needed features; otherwise fallback. |
| WebGPU | Design for compute/fallback today. Keep the shaderpack API compatible with a future query layer, but do not assume WebGPU has RT. |
| DX12 | Map `ray_query` passes to HLSL inline raytracing / RayQuery where available. |
| Vulkan | Prefer `VK_KHR_ray_query`-style compute for Deferred RT. Keep `VK_KHR_ray_tracing_pipeline` for PT or private native experiments only. |

Implementation should be checked against the platform documents before coding:

- Vulkan: `VK_KHR_ray_query` / `SPV_KHR_ray_query`  
  `https://docs.vulkan.org/refpages/latest/refpages/source/VK_KHR_ray_query.html`
- DX12: DXR inline raytracing / HLSL `RayQuery`  
  `https://microsoft.github.io/DirectX-Specs/d3d/Raytracing.html#rayquery`
- Metal: acceleration structures and compute-driven ray intersection  
  `https://developer.apple.com/documentation/metal/ray-tracing-with-acceleration-structures`
- WebGPU: current WebGPU shader stages and feature model  
  `https://www.w3.org/TR/webgpu/`

### 11.6 What the Ray Query Path Should Render

The portable ray-query path should be designed around G-buffer-driven compute passes:

1. Raster G-buffer pass is unchanged.
2. Compute RT direct lighting:
   - read G-buffer,
   - reconstruct surface position,
   - issue visibility/intersection queries against acceleration structure,
   - output direct lighting and shadow terms.
3. Compute RT reflection/GI:
   - trace limited secondary rays,
   - shade secondary hits through material buffers,
   - avoid arbitrary per-material hit shader programs in the first version.
4. Compute compose pass.

Avoid depending on:

- SBT layout,
- arbitrary closest-hit shader variants,
- callable shaders,
- recursive path tracing style shader organization,
- Vulkan buffer device address semantics as shaderpack-visible concepts.

### 11.7 Material Model for Ray Query

For ray-query backends, secondary hit shading needs a data-driven material model:

```cpp
struct RtMaterialRecord {
    uint32_t textureId;
    uint32_t normalTextureId;
    uint32_t pbrTextureId;
    uint32_t flags;
    glm::vec4 baseColorFactor;
    float metallic;
    float roughness;
    float emissionStrength;
};
```

The shaderpack can customize lighting code, but hit material lookup should be stable and table-driven. This is less flexible than arbitrary hit shaders, but it is much more portable to Metal and still fits a deferred renderer.

### 11.8 Relationship to Existing PT Module

The existing PT module can stay Vulkan-RT-only:

```text
RayTracingModule
  requires RayTracingPipeline tier
  uses rgen/rmiss/rchit/rahit/SBT
  can be unavailable on macOS
```

The new module should be the compatibility path:

```text
DeferredRtModule
  primary RT target: RayQuery tier
  fallback path: screen-space/probe compute
  public shaderpack API: render + compute + ray_query
```

That means cross-platform compatibility is not a small porting detail. It is one of the main reasons to keep the new deferred RT module separate from the PT module.

---

## 12. VR, Array Layers and Multiview

VR is not an optional post-feature for this module. The module should be designed as layered from the start.

### 12.1 Existing MCVR-custom Model

`MCVR-custom` already carries the important pieces:

- `WorldModule::eyeCount()`
- `WorldModule::setEyeCount()`
- `StereoMode::SingleInstance3DDispatch`
- `StereoMode::SingleInstanceMultiDispatch`
- `StereoMode::DualInstance`
- `DeviceLocalImage` with `arrayLayers == eyeCount`
- `DeviceLocalImage::createPerLayerViews()`
- `DeviceLocalImage::perLayerView()`
- `WorldPipeline` scheduling that calls `render3D(eyeCount)` for `SingleInstance3DDispatch` and `renderEye(eyeIndex)` for per-eye modes
- stereo fields in `WorldUBO`:
  - `eyeViewOffsets[2]`
  - `eyeProjOffsets[2]`
  - `stereoEnabled`
  - `ipd`
  - foveated parameters

The current custom branch also shows the limit of the existing PT-style path: stereo/layered dispatch exists, but practical work submission is still fixed-size `vkCmdTraceRaysKHR` or `vkCmdDispatch` over the selected dimensions. It does not provide a general tail-compaction, tile-list or indirect-dispatch facility. Deferred RT should preserve the good layer contract from `MCVR-custom`, while moving expensive secondary work into compute-shaped passes driven by per-view tile queues and indirect dispatch.

The deferred module should follow the same conventions, but it should hide them behind a `SceneViewData` / `ViewContext` abstraction so future backends are not tied to the exact current `WorldUBO` layout.

### 12.2 View Context

Add a small per-frame view context:

```cpp
struct DeferredViewContext {
    uint32_t viewIndex = 0;
    uint32_t arrayLayer = 0;
    glm::mat4 view;
    glm::mat4 projection;
    glm::mat4 viewProjection;
    glm::mat4 inverseView;
    glm::mat4 inverseProjection;
    glm::mat4 previousViewProjection;
    glm::vec2 jitter;
    glm::vec2 previousJitter;
};

struct DeferredFrameViewSet {
    uint32_t viewCount = 1;
    DeferredViewContext views[2]; // first version: mono/stereo; keep type extensible later
};
```

This data is bound to all G-buffer, ray-query and compose passes. Shaders must never infer eye index from global options alone. They should use `viewIndex` / `arrayLayer` supplied by the pass.

### 12.3 Layered Resources

All these resources must be layered:

- internal G-buffer images,
- depth image,
- lighting outputs,
- public module outputs,
- temporal history buffers,
- denoiser input/output images,
- debug views,
- visibility mask images if used.

Creation rule:

```cpp
auto layerCount = std::max(1u, eyeCount_);
image = vk::DeviceLocalImage::create(device, vma, false, width, height, layerCount, format, usage);
if (layerCount > 1) {
    image->createPerLayerViews();
}
```

The whole-array view is used by 3D dispatches. Per-layer views are used by framebuffer fallback paths and modules that run `renderEye(eyeIndex)`.

Layering is also required for variable-rate metadata:

- visibility masks,
- foveation/tier maps,
- tile masks,
- active tile or pixel lists,
- indirect dispatch argument buffers when they are view-specific.

Do not use side-by-side stereo packing for these resources. Side-by-side packing makes per-eye SDK integration, denoisers, temporal history and VR submission harder to reason about.

### 12.4 Raster G-buffer in VR

The raster path should support two execution modes:

1. Multiview/layered raster:
   - one render pass,
   - `viewCount == eyeCount`,
   - vertex shader selects per-view matrices,
   - fragment outputs land in the correct array layer.

2. Per-layer fallback:
   - loop `viewIndex`,
   - bind per-layer framebuffer,
   - draw the same `SceneDrawPacket` streams,
   - write only `arrayLayer == viewIndex`.

The module should choose mode by backend capability. Shaderpacks should not fork their render graph for left and right eye.

### 12.5 Ray Query Lighting in VR

Ray-query lighting must run per view/layer.

Preferred execution:

```text
direct_lighting.rq.comp
reflection_lighting.rq.comp
gi_lighting.rq.comp

dispatch(xGroups, yGroups, viewCount)
```

Shader contract:

```text
pixel      = DispatchID.xy
viewIndex  = DispatchID.z
arrayLayer = ViewContext[viewIndex].arrayLayer
```

For each view:

1. read G-buffer values from `arrayLayer`,
2. reconstruct world position using that view's inverse projection/view,
3. compute ray origin/direction for that view,
4. query shared TLAS,
5. write lighting output to the same `arrayLayer`.

The TLAS, material buffers and texture tables are normally shared across views. Camera reconstruction and motion vectors are not shared.

Variable-rate VR policy:

- Visibility-mask clipping may affect G-buffer rasterization because masked pixels are outside the displayed area.
- Foveation should not reduce the primary G-buffer inside the visible area in early versions.
- Foveation should reduce secondary work first: reflection rays, GI rays, expensive direct-light queries, denoise sample count and checkerboard/upscale rate.
- Dynamic dispatch is an optimization layer on top of the layered compute contract. The current bring-up may use fixed `dispatch(xGroups, yGroups, viewCount)` with pixel early-out, but the next scheduling milestone should build per-view tile queues and issue indirect dispatch for selected expensive passes instead of adding a tile-level early-out-only stage.

### 12.6 Compose and Downstream Modules

Compose is also layered:

```text
compose.comp dispatchZ = viewCount
```

It reads all per-view lighting outputs and writes `out:radiance[arrayLayer]`, plus all other public outputs. Downstream modules can then choose their existing stereo mode:

- NRD/DLSS/FSR may run per eye if their SDK integration requires it.
- Tone mapping and post render can use 3D dispatch when possible.
- Temporal accumulation can run per eye or layered, but its history resources must remain per layer.
- Deferred RT should not run an extra internal anti-aliasing stage before these modules unless the pipeline explicitly disables downstream temporal/upscaler modules.

The deferred module must not collapse stereo into side-by-side images. Use array layers only.

### 12.7 VR Offline Tests

Offline tests must include stereo fixtures:

- `single_triangle_stereo.json`
- `mirror_plane_stereo.json`
- `motion_vector_stereo.json`

Required checks:

- all public outputs have `layerCount == 2` when `eyeCount == 2`,
- left and right depth/motion differ when eye projections differ,
- ray-query lighting writes both layers,
- fallback mode and layered mode produce equivalent results within tolerance,
- no pass accidentally reads layer 0 for both eyes.
- visibility-mask clipping clears or skips masked pixels per eye without leaking invalid neighbors into denoise/temporal passes,
- foveated or tile-based secondary lighting changes ray workload without changing full-resolution G-buffer geometry inside the visible mask.

---

## 13. Render Sequence

Per frame:

```text
DeferredRtModuleContext::render()
  1. refresh runtime buffers
  2. bind world/last-world/sky uniforms
  3. sceneProvider.beginFrame(frameIndex)
  4. scenePrepare.render()
  5. bind TLAS and scene metadata
  6. execute shaderpack execution_deferred commands:
       render pass    -> draw SceneDrawPacket streams into view/layer targets
       ray_query      -> compute dispatch with z = viewCount
       compute        -> dispatch, layered when declared
       full_screen    -> draw fullscreen triangle, layered or per-view fallback
  7. export/finalize public output images
```

G-buffer render pass pseudocode:

```cpp
void DeferredRtModule::renderGeometryPass(const DeferredRenderPass& pass,
                                          DeferredRtModuleContext& context) {
    auto draws = sceneProvider_->drawsForContent(pass.config.content);
    if (draws.empty()) return;

    transitionGBufferAttachmentsToColorDepth();
    commandBuffer->beginRenderPass(...);
    commandBuffer->bindDescriptorTable(context.descriptorTable, VK_PIPELINE_BIND_POINT_GRAPHICS);

    const PipelineVariant* bound = nullptr;
    for (const SceneDrawPacket& draw : draws) {
        const PipelineVariant* variant = pass.findVariant(draw.material.groupName);
        if (!variant) continue;
        if (variant != bound) {
            commandBuffer->bindGraphicsPipeline(variant->pipeline);
            bound = variant;
        }
        bindMaterial(draw.material);
        commandBuffer
            ->bindVertexBuffers(draw.vertexBuffer)
            ->bindIndexBuffer(draw.indexBuffer, draw.indexType)
            ->drawIndexed(draw.range.indexCount, 1, draw.range.firstIndex, draw.range.vertexOffset);
    }

    commandBuffer->endRenderPass();
}
```

---

## 14. Offline Testing Strategy

The current project has no first-party test target. Add one before implementing the full module.

### 14.1 CMake Test Harness

Add:

```text
MCVR/tests/CMakeLists.txt
MCVR/tests/main.cpp
MCVR/tests/shaderpack_tests.cpp
MCVR/tests/deferred_graph_tests.cpp
MCVR/tests/scene_packet_tests.cpp
```

Top-level option:

```cmake
option(MCVR_BUILD_TESTS "Build MCVR offline tests" ON)
if(MCVR_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
```

Use a tiny internal test harness first to avoid dependency churn:

```cpp
#define CHECK(expr) do { if (!(expr)) throw std::runtime_error(#expr); } while (0)
```

Later this can be replaced with Catch2/doctest. `extern/json` already vendors doctest internally, but it is not exposed as a project dependency today.

Current build caveat: `MCVR/src/core/CMakeLists.txt` builds one `core` shared library from every `*.cpp`. That is fine for the game, but too coarse for fast unit tests. Before adding many tests, split code into testable units:

```cmake
file(GLOB_RECURSE CORE_SOURCE_FILES CONFIGURE_DEPENDS "*.cpp")

add_library(core_objects OBJECT ${CORE_SOURCE_FILES})
target_include_directories(core_objects PUBLIC ...)
target_compile_definitions(core_objects PUBLIC VK_NO_PROTOTYPES CORE_LIB)

add_library(core SHARED $<TARGET_OBJECTS:core_objects>)
target_link_libraries(core PUBLIC ...)

if(MCVR_BUILD_TESTS)
    add_executable(mcvr_tests
        ${PROJECT_SOURCE_DIR}/tests/main.cpp
        ${PROJECT_SOURCE_DIR}/tests/shaderpack_tests.cpp
        ${PROJECT_SOURCE_DIR}/tests/deferred_graph_tests.cpp
        ${PROJECT_SOURCE_DIR}/tests/scene_packet_tests.cpp
    )
    target_link_libraries(mcvr_tests PRIVATE core_objects nlohmann_json::nlohmann_json tinyexpr)
    add_test(NAME mcvr_tests COMMAND mcvr_tests)
endif()
```

If `core_objects` still pulls too much Vulkan/JNI/runtime code for pure CPU tests, extract CPU-only files into:

```text
MCVR/src/core/offline/
  expression_evaluator.*
  shaderpack_loader.*
  deferred_graph.*
  scene_packet_classifier.*
```

Then build:

```text
mcvr_offline_core
mcvr_tests
mcvr_shaderpack_lint
```

This is the preferred long-term shape: renderer runtime remains large, but parser/graph/validation code is small and testable.

### 14.2 Pure CPU Tests

These should run without Minecraft, GLFW window, swapchain, or Vulkan device.

1. `ExpressionEvaluator`
   - identifiers,
   - bool/numeric expressions,
   - `if` helper,
   - invalid syntax.

2. `ShaderPackLoader`
   - load built-in PT packs,
   - load new `vanilla-deferred-rt`,
   - parse `stage: deferred`,
   - parse `execution_deferred`,
   - reject malformed packs.

3. Deferred graph validation
   - unknown pass,
   - duplicate pass,
   - missing resource,
   - imported output,
   - render pass without content,
   - invalid read/write ordering.

4. Scene packet classification
   - chunk opaque -> `world_opaque`,
   - chunk cutout -> `world_cutout`,
   - entity content name -> shader variant key,
   - unknown group falls back to `default`.

5. Output contract validation
   - exactly 16 outputs for Deferred RT: the 15 PT-compatible exports plus `gi_hit_distance`,
   - formats match YAML,
   - downstream NRD names can be connected,
   - `gi_hit_distance` is the source for NRD `diffuseHitDepthImage`.

6. View/layer contract validation
   - `eyeCount == 1` creates one layer,
   - `eyeCount == 2` creates two layers,
   - every public output is layer-compatible,
   - every G-buffer and lighting pass has a valid `view_execution`,
   - `VIEW_COUNT` expressions resolve to the active view count,
   - `SceneProvider::views()` maps every `viewIndex` to one valid `arrayLayer`.

7. RT backend implementation selection
   - Vulkan with ray query support selects the ray-query shader,
   - Metal/macOS with acceleration structure support selects the ray-query shader,
   - DX12 inline raytracing selects the ray-query shader,
   - WebGPU or unavailable RT support selects the fallback shader,
   - missing required fallback or query support reports a useful shaderpack error.

These tests give fast feedback and should be the default local development loop.

### 14.3 Shader Compile Tests

Run shaderc offline against shaderpack shaders.

Targets:

- all `stage: deferred` shaders compile,
- injected execution buffer source compiles,
- shared include directories resolve,
- required defines exist for each pass,
- shader variants compile.

This catches most shaderpack mistakes without launching Minecraft.

For macOS support, add a parallel Metal shader validation step later:

```text
mcvr_shaderpack_lint --pack path/to/vanilla-deferred-rt --stage deferred --backend metal_ray_query
```

This should validate that every required `ray_query` pass avoids PT-only concepts and has a fallback shader. Actual `.metal` compilation will need to run on macOS with Xcode tools, so keep it optional on non-Mac developer machines.

Implementation:

```text
MCVR/tools/shaderpack_lint/
```

or as a CMake test executable:

```text
mcvr_shaderpack_lint --pack path/to/vanilla-deferred-rt --stage deferred
```

### 14.4 Headless Vulkan Smoke Tests

After CPU tests, add an optional GPU test target.

Requirements:

- creates Vulkan instance/device using MCVR wrappers,
- no Minecraft,
- no swapchain,
- offscreen images only,
- synthetic scene data,
- one command buffer,
- save images to disk for inspection.

Executable:

```text
MCVR/tools/deferred_rt_offline/deferred_rt_offline.cpp
```

Invocation:

```text
deferred_rt_offline ^
  --shader-pack MCVR/src/shader/world/deferred_rt/internal/vanilla-deferred-rt ^
  --scene MCVR/tests/fixtures/scenes/cornell_box.json ^
  --width 128 ^
  --height 128 ^
  --views 1 ^
  --out build/offline/deferred_rt
```

Stereo invocation:

```text
deferred_rt_offline ^
  --shader-pack MCVR/src/shader/world/deferred_rt/internal/vanilla-deferred-rt ^
  --scene MCVR/tests/fixtures/scenes/single_triangle_stereo.json ^
  --width 128 ^
  --height 128 ^
  --views 2 ^
  --view-mode layered ^
  --out build/offline/deferred_rt_stereo
```

Outputs:

```text
gbuffer_albedo.png
gbuffer_albedo_layer0.png
gbuffer_albedo_layer1.png
gbuffer_normal.exr
gbuffer_depth.exr
lighting_direct.exr
lighting_indirect.exr
radiance.exr
metrics.json
```

This requires image readback support. If PNG/EXR writing is not available yet, start with raw float dumps and small JSON statistics:

- min/max/mean per image,
- NaN/Inf count,
- negative count,
- non-zero pixel count,
- depth monotonic sanity checks.

### 14.5 Synthetic Scene Fixtures

Keep scenes tiny and deterministic:

1. `single_triangle.json`
   - verifies raster output, depth, normal.

2. `two_planes.json`
   - verifies depth test and motion.

3. `cornell_box.json`
   - verifies shadow/GI basics.

4. `mirror_plane.json`
   - verifies reflection ray.

5. `alpha_cutout_quad.json`
   - verifies cutout policy.

6. `emissive_block.json`
   - verifies direct emissive sampling.

7. `single_triangle_stereo.json`
   - verifies two-layer G-buffer output.

8. `motion_vector_stereo.json`
   - verifies per-eye motion reconstruction.

9. `mirror_plane_stereo.json`
   - verifies ray-query reflection writes both layers.

The fixture format should describe native geometry packets, not Minecraft blocks. That makes tests independent from game code.

### 14.6 Trace Replay Tests

Add a debug export path in the real game later:

```text
--radiance-dump-scene-frame path
```

It should write:

- world uniforms,
- sky uniforms,
- scene draw packets,
- texture/material ids,
- BLAS/TLAS build metadata where possible,
- selected shaderpack and attributes.

The offline runner can replay this dump. This gives a much lighter loop:

1. capture once in Minecraft,
2. close Minecraft,
3. iterate renderer/shaders offline.

### 14.7 Golden Image Tests

Once output stabilizes:

- store small expected images for synthetic scenes,
- compare with tolerance,
- allow per-GPU tolerance for RT noise,
- keep deterministic random seeds in shaderpack execution.

Do not add golden tests too early. Start with statistics and shader compile tests.

### 14.8 RenderDoc Workflow

For GPU debugging:

1. offline runner supports `--capture-renderdoc` later,
2. capture a single frame without Minecraft,
3. inspect G-buffer and barriers.

This is much cheaper than launching the full mod.

---

## 15. Implementation Roadmap

This roadmap is intentionally implementation-oriented. Each phase must leave the repository in a runnable state, and each phase must state the contract it is introducing before the next phase depends on it.

Do not start with GI, SHARC, custom shaderpack graphs or Blaze3D. The first required deliverable is the module boundary: Java-visible module, native module shell, neutral internal names, 16 public exports, deterministic placeholder contents and tested downstream wiring.

### Phase 0: Baseline, Contract Tests and Local Loops

Goal: make the existing behavior measurable before changing renderer behavior.

#### 0.1 Freeze the existing downstream contracts

Implementation steps:

- Record the existing Java module YAML contracts from:
  - `Radiance-custom/src/main/resources/modules/ray_tracing.yaml`,
  - `Radiance-custom/src/main/resources/modules/nrd.yaml`,
  - `Radiance-custom/src/main/resources/modules/dlss.yaml`,
  - `Radiance-custom/src/main/resources/modules/fsr_upscaler.yaml`,
  - `Radiance-custom/src/main/resources/modules/xess_sr.yaml`,
  - `Radiance-custom/src/main/resources/modules/temporal_accumulation.yaml`,
  - `Radiance-custom/src/main/resources/modules/tone_mapping.yaml`,
  - `Radiance-custom/src/main/resources/modules/post_render.yaml`.
- Record the preset connections in `Radiance-custom/src/main/java/com/radiance/client/pipeline/Pipeline.java`.
- Explicitly document that current PT presets connect `ray_tracing.first_hit_depth` to `nrd.diffuseHitDepthImage`.
- Explicitly document that Deferred RT must not copy that ambiguous connection; Deferred RT connects `gi_hit_distance` to `nrd.diffuseHitDepthImage`.

Completion standard:

- There is a checked-in contract note or test fixture listing every downstream input name, format and expected producer.
- The fixture has 16 Deferred RT public outputs: the 15 PT-compatible exports plus `gi_hit_distance`.
- The fixture states that `first_hit_depth` remains scene primary depth and `gi_hit_distance` is diffuse/GI ray hit distance.

#### 0.2 Add CPU-level validation targets

Implementation steps:

- Add a native test entry under `MCVR-custom/tests` or the nearest existing CMake test location.
- Add CMake/CTest wiring so tests can run without launching Minecraft.
- Add a small `DeferredOutputContract` test that validates:
  - public output count is 16,
  - output names exactly match `deferred_rt.yaml`,
  - output formats exactly match this document,
  - `gi_hit_distance` is present and is `R16_SFLOAT`,
  - legacy public names remain stable.
- Add a `ViewLayerContract` test that validates:
  - `eyeCount == 1` maps to one array layer,
  - `eyeCount == 2` maps to two array layers,
  - every public output is layer-compatible.

Completion standard:

- `ctest --test-dir build --output-on-failure` can run the new contract tests.
- Contract tests fail if `gi_hit_distance` is removed, renamed, reordered unexpectedly or connected to the wrong downstream input.
- These tests do not require Vulkan device creation unless the existing test infrastructure already requires it.

#### 0.3 Add shaderpack/parser smoke tests

Implementation steps:

- Add tests for the current shaderpack loader with existing built-in PT packs to ensure no refactor breaks PT.
- Add malformed pack fixtures for:
  - unknown stage,
  - missing resource,
  - duplicate pass,
  - invalid execution dependency.
- Add a placeholder fixture for future `stage: deferred`, but keep it disabled or expected-fail until Phase 6.

Completion standard:

- Current PT shaderpacks still parse.
- Malformed packs fail with useful errors.
- No Deferred RT code path changes PT parsing behavior.

#### 0.4 Define local development commands

Implementation steps:

- Add a short developer note near the tests or in this document showing:

```text
cmake --build build --target mcvr_tests
ctest --test-dir build --output-on-failure
```

- Later phases may extend this with shaderpack lint and offline runner commands.

Completion standard:

- A developer can run the contract loop without opening Minecraft.

### Phase 1: Module Shell, Internal Names and Legacy Exports

Goal: Radiance can select Deferred RT, native MCVR can create it, and all downstream modules can connect to deterministic placeholder outputs.

This is the most important phase. It establishes the public contract that later G-buffer, lighting, denoising and upscaling work must preserve.

#### 1.1 Add Java module metadata

Implementation steps:

- Add `Radiance-custom/src/main/resources/modules/deferred_rt.yaml`.
- The output list must contain exactly these 16 outputs in this semantic order:
  - `radiance`: `R16G16B16A16_SFLOAT`,
  - `diffuse_albedo_metallic`: `R8G8B8A8_UNORM`,
  - `specular_albedo`: `R8G8B8A8_UNORM`,
  - `normal_roughness`: `R16G16B16A16_SFLOAT`,
  - `motion_vector`: `R16G16_SFLOAT`,
  - `linear_depth`: `R16_SFLOAT`,
  - `specular_hit_depth`: `R16_SFLOAT`,
  - `first_hit_depth`: `R16_SFLOAT`,
  - `first_hit_diffuse_direct_light`: `R16G16B16A16_SFLOAT`,
  - `first_hit_diffuse_indirect_light`: `R16G16B16A16_SFLOAT`,
  - `first_hit_specular`: `R16G16B16A16_SFLOAT`,
  - `first_hit_clear`: `R16G16B16A16_SFLOAT`,
  - `first_hit_base_emission`: `R16G16B16A16_SFLOAT`,
  - `fog_image`: `R16G16B16A16_SFLOAT`,
  - `first_hit_refraction`: `R16G16B16A16_SFLOAT`,
  - `gi_hit_distance`: `R16_SFLOAT`.
- Add attributes matching the PT module where they are still meaningful:
  - shader pack path,
  - jitter toggle,
  - optional debug output mode.
- Do not expose internal G-buffer resources as Java module outputs.

Completion standard:

- The UI can load the YAML.
- The module has no input image configs.
- Output names and formats match the contract test exactly.
- `gi_hit_distance` is visible to Java preset wiring as a normal public output.

#### 1.2 Add Java translations and preset entries

Implementation steps:

- Add translation keys for the module name and attributes.
- Add a `DEFERRED_RT_MODULE_NAME` constant to `Pipeline.java`.
- Add preset keys for the first practical combinations:
  - `Deferred RT -> ToneMapping -> PostRender`,
  - `Deferred RT -> NRD -> ToneMapping -> PostRender`,
  - `Deferred RT -> NRD -> FSR -> ToneMapping/PostRender`,
  - `Deferred RT -> NRD -> XeSS -> ToneMapping/PostRender`,
  - DLSS route only if local feature availability logic can already represent it.
- Keep existing PT presets unchanged.

Completion standard:

- Selecting a Deferred RT preset does not remove or mutate existing RT presets.
- Preset availability fallback still works if Deferred RT is unavailable.
- The simplest preset can connect `deferred_rt.radiance` to ToneMapping and PostRender without PT.

#### 1.3 Wire NRD and upscaler inputs explicitly

Implementation steps:

- For Deferred RT + NRD presets, connect:
  - `deferred_rt.first_hit_diffuse_indirect_light -> nrd.diffuse_radiance`,
  - `deferred_rt.first_hit_specular -> nrd.specular_radiance`,
  - `deferred_rt.first_hit_diffuse_direct_light -> nrd.direct_radiance`,
  - `deferred_rt.diffuse_albedo_metallic -> nrd.diffuse_albedo`,
  - `deferred_rt.specular_albedo -> nrd.specular_albedo`,
  - `deferred_rt.normal_roughness -> nrd.normal_roughness`,
  - `deferred_rt.motion_vector -> nrd.motion_vector`,
  - `deferred_rt.linear_depth -> nrd.linear_depth`,
  - `deferred_rt.first_hit_clear -> nrd.first_hit_clear`,
  - `deferred_rt.first_hit_base_emission -> nrd.first_hit_base_emission`,
  - `deferred_rt.fog_image -> nrd.fog_image`,
  - `deferred_rt.gi_hit_distance -> nrd.diffuseHitDepthImage`,
  - `deferred_rt.specular_hit_depth -> nrd.specularHitDepthImage`,
  - `deferred_rt.first_hit_refraction -> nrd.first_hit_refraction`.
- For Deferred RT + FSR/XeSS/DLSS presets, connect `linear_depth` to upscaler depth and `first_hit_depth` to the upscaler first-hit-depth propagation input.
- For PostRender, connect:
  - final LDR from ToneMapping/upscaler path,
  - final HDR from denoised/upscaled/direct HDR path,
  - `first_hit_depth` or upscaled first-hit depth,
  - `motion_vector` or upscaled motion,
  - `normal_roughness` or upscaled normal/roughness.

Completion standard:

- No Deferred RT preset connects `first_hit_depth` to NRD `diffuseHitDepthImage`.
- PT presets may keep their current behavior until separately migrated.
- Preset graph validation catches missing `gi_hit_distance`.

#### 1.4 Add native `DeferredRtModule`

Implementation steps:

- Add:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`,
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`,
  - `DeferredRtModuleContext` near the module implementation.
- Implement the same `WorldModule` lifecycle used by current modules:
  - construction from attributes,
  - `setOrCreateOutputImages`,
  - command recording,
  - resize/recreate handling,
  - frame index handling.
- Register the module in `MCVR-custom/src/core/render/pipeline.cpp`.
- Add `constexpr static uint32_t outputImageNum = 16`.
- Add strongly named native output indices. Do not use raw numeric indices outside the mapping table.

Completion standard:

- Native pipeline can instantiate Deferred RT by Java module name.
- If output image count is not 16, setup fails with a useful error.
- Existing modules still compile and run.

#### 1.5 Add neutral internal names and `LegacyExportMap`

Implementation steps:

- Add a single mapping table in Deferred RT code:

```text
primary_radiance              -> radiance
primary_albedo_metallic       -> diffuse_albedo_metallic
primary_specular_albedo       -> specular_albedo
primary_normal_roughness      -> normal_roughness
primary_motion_vector         -> motion_vector
primary_linear_depth          -> linear_depth
reflection_hit_distance       -> specular_hit_depth
primary_depth                 -> first_hit_depth
primary_direct_diffuse        -> first_hit_diffuse_direct_light
primary_indirect_diffuse      -> first_hit_diffuse_indirect_light
primary_specular              -> first_hit_specular
primary_clear                 -> first_hit_clear
primary_emission              -> first_hit_base_emission
atmosphere_fog                -> fog_image
primary_refraction            -> first_hit_refraction
gi_hit_distance               -> gi_hit_distance
```

- Internal code must use neutral names.
- Only Java YAML, preset wiring and the final export map may use `first_hit_*`.

Completion standard:

- A code search for `first_hit_` inside Deferred RT implementation only finds YAML, preset wiring, export map constants or comments explaining legacy exports.
- The mapping table is covered by the contract test.

#### 1.6 Allocate layered placeholder outputs

Implementation steps:

- Allocate every public output as a layer-compatible image.
- `layerCount` must equal active `eyeCount`.
- Create per-layer views if the existing module convention requires them.
- Return `StereoMode::SingleInstance3DDispatch` unless a backend explicitly selects per-view fallback.
- Deterministically clear every output every frame.
- Placeholder values must be documented and stable:
  - `radiance`: visible debug HDR color or copied clear color,
  - `diffuse_albedo_metallic`: black/non-metallic or neutral gray/non-metallic,
  - `specular_albedo`: black or neutral low-F0,
  - `normal_roughness`: invalid normal plus roughness `1.0`, or a documented view-space/world-space neutral,
  - `motion_vector`: `(0, 0)`,
  - `linear_depth`: far/invalid depth according to current consumer convention,
  - `first_hit_depth`: same as `linear_depth` only for the placeholder path,
  - `specular_hit_depth`: invalid or zero according to NRD-compatible hit-distance convention,
  - `gi_hit_distance`: invalid or zero according to NRD-compatible hit-distance convention,
  - split radiance, clear, emission, fog and refraction: zero.

Completion standard:

- Game launches with Deferred RT selected and no PT module.
- `ToneMapping -> PostRender` works on placeholder `radiance`.
- NRD can be connected for resource validation, but the path is not marked quality-correct until Phase 5 produces real denoiser signals.
- Mono and stereo runs allocate the same public names with different layer counts.
- RenderDoc or logging confirms every public output is cleared.

### Phase 2: Shared Scene Preparation

Goal: share acceleration structure and scene metadata preparation between PT and Deferred RT without making Deferred RT depend on `RayTracingModule`.

Current implementation target:

```text
Java / JNI current MCVR world upload
  chunks, entities, textures, uniforms, raster metadata
        |
        v
Renderer::world() native scene storage
        |
        v
WorldPipeline-owned SharedSceneRuntime
  - shared ScenePrepare and per-frame ScenePrepareContext
  - optional McvrSceneProvider packet/view/material cache
        |
        +-- RayTracingModule: TLAS/AS metadata plus PT-only SBT setup
        |
        +-- DeferredRtModule: SceneProvider draw streams, views and TLAS/AS metadata
```

`SharedSceneRuntime` is a world-pipeline infrastructure layer. It is not a Deferred RT submodule and it is not a PT
submodule. Future world modules that need scene facts should request them from this runtime rather than reading Java,
chunks, entities, Blaze3D objects, Vulkan layouts or shaderpack metadata directly.

The Java/JNI bridge remains the game-source boundary. The shared runtime consumes native world storage that Java has
already populated through existing proxy calls. It does not call Java and does not expose JNI record layouts to modules.
First-version shared runtime work must not require new JNI payloads; current geometry, texture, raster metadata,
world/sky uniform and texture-mapping data is sufficient for the PT + Deferred RT sharing milestone. Future Java/JNI
extensions should add missing scene facts to this boundary, then flow through `SharedSceneRuntime`.

Current implementation status:

- `WorldModule::sceneRuntimeRequirements()` advertises whether a module needs `Acceleration`, `Provider`, or neither.
- `WorldPipeline` aggregates requirements after module attributes are applied, builds `SharedSceneRuntime` only when at
  least one active module asks for scene runtime data, and calls `beginFrame()` before module contexts render.
- `SharedSceneRuntime` owns the shared `ScenePrepare`, one `ScenePrepareContext` per swapchain frame, and a lazy
  `McvrSceneProvider` per frame context.
- `SharedSceneFrameGuard` uses a monotonically increasing pipeline frame serial, not swapchain `frameIndex`, so reused
  swapchain images still record scene preparation once per rendered frame.
- `RayTracingModule` requests `Acceleration` only. It reuses the shared `ScenePrepareContext` and does not force provider
  draw-packet enumeration.
- `DeferredRtModule` requests `Acceleration | Provider`. It reads the same prepared AS metadata and the lazy provider
  packet/view/material streams after the pipeline has begun the shared runtime frame.

Java-side use remains unchanged for this milestone: game objects become renderable data when the current mod upload path
copies chunk/entity geometry, material/texture ids, raster metadata and uniforms into native world storage. When future
work needs data that the native runtime cannot infer, the Java mod and JNI ABI must add compact neutral fields at that
upload boundary first; modules then consume the normalized result from `SharedSceneRuntime`, not direct Java objects.

#### 2.1 Extract shared scene preparation

Implementation steps:

- Move reusable logic from the current PT world preparation code into:

```text
MCVR-custom/src/core/render/scene_prepare/
```

- Rename generic types to:
  - `ScenePrepare`,
  - `ScenePrepareContext`,
  - `SceneAccelerationResources`,
  - `SceneMaterialResources`,
  - `SceneTextureResources`.
- Keep PT-specific naming in an adapter layer only if some PT shaders still require it.
- Update `RayTracingModule` to consume the shared component.
- Add `DeferredRtModule` consumption of the same shared component.

Completion standard:

- PT output is unchanged.
- Deferred RT can build or reference the same TLAS/BLAS and scene metadata.
- There is no duplicated TLAS build path for PT and Deferred RT.

#### 2.1.1 Move scene runtime ownership to `WorldPipeline`

Implementation steps:

- Add a pipeline-owned shared scene runtime, for example:

```text
MCVR-custom/src/core/render/scene_runtime/
  shared_scene_runtime.hpp
  shared_scene_runtime.cpp
```

- Let `WorldPipeline` construct one shared runtime per native pipeline build.
- Move `ScenePrepare` ownership from individual modules into the shared runtime.
- Expose per-frame `ScenePrepareContext` references through the runtime.
- Let `RayTracingModule` and `DeferredRtModule` borrow the same prepared context instead of creating their own `ScenePrepare`.
- Add module-level scene runtime requirement flags:
  - `Acceleration` for TLAS/BLAS/scene metadata,
  - `Provider` for neutral draw packets, views and material/render-state metadata.
- Aggregate requirements in `WorldPipeline`, then call `SharedSceneRuntime::beginFrame(frameIndex, eyeCount, requirements)`
  before executing world module contexts.
- Ensure `ScenePrepareContext::render()` is recorded once per frame before modules that need AS/scene metadata consume it.
- Build provider packets lazily only when `Provider` is requested. PT-only pipelines should not enumerate Deferred draw
  packets.
- Keep PT-only SBT hit-group setup as a PT adapter step after the shared preparation pass.
- Add assertions or debug counters that detect multiple AS preparation submissions in one frame.

Completion standard:

- A pipeline containing only PT behaves the same as before.
- A pipeline containing only Deferred RT builds one TLAS per frame.
- A diagnostic pipeline containing both PT and Deferred RT still builds one shared TLAS per frame and both modules consume the same AS metadata.
- Provider packet enumeration runs at most once per frame and only when an active module requests it.
- Dynamic Java pipeline rebuild/switch keeps old runtime resources alive only through the existing frame-resource-retainer path and does not double-own scene history.

#### 2.2 Preserve PT shaderpack behavior

Implementation steps:

- Keep PT shaderpack SBT/hit-group resolution in the PT-specific path.
- Do not move PT-only terms into Deferred RT public APIs.
- Add smoke tests or logs proving existing internal PT packs still load.

Completion standard:

- Built-in PT packs still run.
- A Deferred RT module can exist in code without loading PT shader stages.

### Phase 3: SceneProvider and Raster Draw Packets

Goal: expose stable raster draw streams to Deferred RT while hiding the concrete source of scene data.

#### 3.1 Define provider interfaces

Implementation steps:

- Add common scene provider types under:

```text
MCVR-custom/src/core/render/modules/world/common/scene_provider/
```

- Define:
  - `SceneProvider`,
  - `SceneView`,
  - `SceneDrawPacket`,
  - `SceneGeometryClass`,
  - `SceneMaterialRef`,
  - `SceneTextureRef`.
- `SceneDrawPacket` must include:
  - vertex/index buffer handles,
  - offsets and counts,
  - geometry class,
  - material/texture reference,
  - transform or transform index,
  - previous transform or motion source,
  - flags for alpha test, emissive, translucent, double-sided,
  - neutral render state defaults for alpha mode, blend mode, depth policy, cull mode and sort key.
- `SceneView` must include:
  - `viewIndex`,
  - `arrayLayer`,
  - current view/projection,
  - previous view/projection,
  - jitter and previous jitter.
- Document which fields are populated from the current Java/native payload and which fields use conservative defaults.

Completion standard:

- Deferred RT code can request packets without knowing whether they came from current MCVR data or future Blaze3D data.
- Shaderpack-visible names are neutral geometry classes, not Minecraft or Blaze3D class names.
- Existing PT paths keep using their current scene data without requiring deferred render-state fields.

#### 3.2 Implement `McvrSceneProvider`

Implementation steps:

- Build `McvrSceneProvider` on top of current chunk/entity data.
- Preserve the existing first-version mapping where `WORLD_TRANSPARENT` is treated as cutout, but mark this as a legacy fallback in provider stats/logs.
- Classify at least:
  - `world_opaque`,
  - `world_cutout`,
  - `entity_opaque`,
  - `entity_cutout`,
  - `transparent_forward` or `world_translucent` as a deferred-later bucket.
- Include enough material data for albedo, normal, roughness/metallic or the closest current equivalent.
- Read packed `alphaMode` where available and expose it as neutral provider metadata even if the first G-buffer path still uses stream-level alpha test.
- Add classification logs or debug counters:
  - packet count per class,
  - triangle/index count per class,
  - skipped packet count with reason,
  - legacy `WORLD_TRANSPARENT` packets routed through cutout fallback,
  - translucent/forward-later packets excluded from the G-buffer.

Completion standard:

- Deferred RT can enumerate non-empty opaque/cutout packets in a real world.
- Packet ranges are valid and bounded.
- Translucent content is not silently lost; unsupported content is classified into an explicit future/forward bucket.
- The provider exposes enough diagnostics to show when current Java/native payload compression is limiting classification fidelity.

#### 3.2.1 Design Java/native render-state metadata extension

Implementation steps:

- Add a small versioned metadata record to the Java/native scene upload path without changing deferred shaderpack syntax.
- Capture, when available:
  - original render-layer name or stable layer id,
  - neutral alpha mode,
  - blend mode,
  - depth test/write policy,
  - cull mode,
  - polygon offset,
  - depth bias scale/constant,
  - color/depth write masks,
  - output target class,
  - layering transform class,
  - lightmap/overlay requirements,
  - outline/crumbling behavior,
  - `sortOnUpload`,
  - pipeline sort key and draw order,
  - optional stencil policy,
  - material semantic hints such as water, glass, portal, weather, text and particle.
- Keep existing fields (`geometryType`, texture id, PBR vertex data, group/content names) as the compatibility baseline.
- Map missing metadata to conservative defaults in `McvrSceneProvider`.
- Add native validation that mismatched metadata array sizes or unknown enum values fail early with useful diagnostics.
- Keep the extended metadata compact and per-geometry/per-draw, not per-vertex, to avoid bandwidth and memory growth on chunk meshes.

Completion standard:

- The Java/native ABI has a documented version or capability bit for extended raster metadata.
- Deferred RT can distinguish cutout from true translucent content without relying on `WORLD_TRANSPARENT` alone when the new metadata is present.
- Old Java payloads still render through the current opaque/cutout fallback behavior.
- PT remains source-compatible; PT modules may ignore the extended raster metadata.
- Performance tests or counters show that enabling metadata capture does not duplicate vertex buffers, material buffers or BLAS/TLAS builds.

#### 3.3 Add provider tests and fixtures

Implementation steps:

- Add synthetic provider fixtures for:
  - one opaque chunk mesh,
  - one cutout quad,
  - one entity mesh,
  - one translucent mesh,
  - stereo views.
- Add CPU tests for packet classification and view/layer mapping.

Completion standard:

- Classification tests fail if opaque/cutout/translucent buckets are mixed accidentally.
- Stereo fixture maps each view to one valid layer.

### Phase 4: Fixed G-buffer Raster Path

Goal: produce real primary visibility and scene facts with built-in shaders before adding custom deferred shaderpacks.

#### 4.1 Add G-buffer resources

Implementation steps:

- Add internal layered images:
  - `gbuffer_albedo_metallic`,
  - `gbuffer_specular_albedo`,
  - `gbuffer_normal_roughness`,
  - `gbuffer_motion_vector`,
  - `gbuffer_linear_depth`,
  - `gbuffer_primary_depth`,
  - optional `gbuffer_material_id`,
  - optional `gbuffer_object_id`.
- Keep material/object/classification masks internal unless a downstream public consumer requires them.
- Define exact normal space:
  - choose world-space or view-space,
  - document it,
  - keep it consistent for NRD/upscalers/PostRender.
- Define exact motion vector space:
  - render-resolution pixels, display-resolution pixels or normalized UV delta,
  - current-to-previous or previous-to-current,
  - jittered or unjittered policy.

Completion standard:

- G-buffer format and semantic decisions are written in code comments or constants near resource creation.
- Public exports copy/alias from G-buffer resources through the legacy map.

#### 4.2 Implement opaque/cutout raster passes

Implementation steps:

- Add built-in shaders:

```text
MCVR-custom/src/shader/world/deferred_rt/internal/gbuffer_world.vert
MCVR-custom/src/shader/world/deferred_rt/internal/gbuffer_world.frag
```

- Add descriptor bindings for:
  - world uniforms,
  - view uniforms,
  - material data,
  - texture arrays/samplers,
  - previous transform data if needed.
- Implement alpha test for cutout content.
- Use a real depth attachment for rasterization.
- Resolve/copy depth into `gbuffer_linear_depth` and `gbuffer_primary_depth` according to the contract.

Completion standard:

- Opaque world writes albedo, normal/roughness, linear depth and primary depth.
- Cutout geometry respects alpha test and depth test.
- Sky/background pixels keep documented invalid defaults.

#### 4.3 Implement stereo-layered execution

Implementation steps:

- Use Vulkan multiview/layered rendering if practical.
- If the initial backend uses per-layer fallback, keep the same public image array contract.
- Bind the correct `SceneView` for each `viewIndex`.
- Do not use side-by-side stereo images.

Completion standard:

- `eyeCount == 2` writes different left/right depth and motion layers when views differ.
- Layer 0 and layer 1 can be inspected independently.
- All public exports preserve the same layer count.

#### 4.4 Export real primary scene facts

Implementation steps:

- Fill these public outputs from G-buffer:
  - `diffuse_albedo_metallic`,
  - `specular_albedo`,
  - `normal_roughness`,
  - `motion_vector`,
  - `linear_depth`,
  - `first_hit_depth`.
- Continue zero-filling lighting outputs until Phase 5.
- If `primary_depth` and `primary_linear_depth` alias, add a local assertion/comment stating why this path satisfies current consumer convention.

Completion standard:

- PostRender receives real depth/motion/normal facts.
- FSR/XeSS/DLSS can be connected for resource and reprojection validation, even before quality tuning.
- G-buffer debug views show stable albedo, normal and depth.

### Phase 5: Direct Lighting, Reflections and NRD-Valid Signals

Goal: make Deferred RT a useful hybrid renderer and produce denoiser-valid split lighting.

#### 5.1 Add pixel classification

Implementation steps:

- Add `classify_deferred_pixels.comp`.
- Generate internal masks and compacted tile queues for:
  - valid primary surface,
  - sky/background,
  - direct-light visibility eligible,
  - diffuse GI eligible,
  - specular/reflection eligible,
  - transparent/refraction eligible.
- Apply the OpenXR visibility mask before queue generation so hidden-area pixels do not enter any queue.
- Dispatch reflection, GI and refraction from compacted per-view queues instead of full-screen dispatch once the queue path exists.
- Skip reflection rays for high-roughness or non-specular pixels according to a documented threshold.

Completion standard:

- Debug counters report how many pixels enter each lighting path.
- High-roughness diffuse surfaces do not run reflection queries.
- Reflection/GI/refraction queue counters report active tile counts per view/layer, and indirect dispatch arguments match those counts.

#### 5.2 Add ray-query direct lighting

Implementation steps:

- Add compute shader path for sun/moon/direct light visibility.
- Reconstruct world position from G-buffer depth and view data.
- Trace visibility rays through shared acceleration structures.
- Write:
  - `primary_direct_diffuse`,
  - optionally direct specular if the selected composition model needs it.
- Add fallback shader for devices without ray query support.

Completion standard:

- Shadows respond to world geometry.
- The pass uses ray queries or a declared fallback, not PT SBT/hit shader stages.
- Stereo dispatch writes all active layers.

#### 5.3 Add reflection/specular lighting

Implementation steps:

- Add ray-query closest-hit reflection path for eligible pixels.
- Use normal, roughness and specular albedo to generate reflection direction and weighting.
- Write:
  - `primary_specular`,
  - `reflection_hit_distance -> specular_hit_depth`.
- Define invalid specular hit distance and keep it consistent with NRD expectations.

Completion standard:

- Simple mirror/low-roughness test surfaces show reflections.
- `specular_hit_depth` is non-zero/valid only where a reflection ray has a usable hit.
- NRD specular input is not a fake copy of primary depth.

#### 5.4 Add conservative diffuse GI

Implementation steps:

- Add one-bounce or limited-bounce diffuse GI path.
- Start with low-resolution, checkerboard or sparse tracing if needed for performance.
- Write:
  - `primary_indirect_diffuse`,
  - `gi_hit_distance`.
- Define `gi_hit_distance` as diffuse/GI secondary-hit distance, not primary surface depth.

Completion standard:

- `gi_hit_distance` changes with GI ray hits and misses.
- NRD `diffuseHitDepthImage` receives `gi_hit_distance`.
- `first_hit_depth` remains primary scene depth for PostRender/upscalers.

#### 5.5 Compose direct HDR output

Implementation steps:

- Add a compose pass that writes `primary_radiance`.
- For non-denoiser presets, compose direct diffuse, indirect diffuse, specular, emission, fog, clear and refraction as available.
- For NRD presets, treat `primary_radiance` as preview/debug or non-authoritative pre-denoise HDR; the final ToneMapping input should be NRD/upscaler output.

Completion standard:

- `Deferred RT -> ToneMapping -> PostRender` produces lit HDR output without NRD.
- `Deferred RT -> NRD -> ToneMapping -> PostRender` uses denoised output as final HDR.
- Documentation and preset wiring do not imply that noisy `radiance` is final when NRD is active.

#### 5.6 Validate downstream quality gates

Implementation steps:

- Test NRD path with:
  - stable normal/roughness,
  - stable motion,
  - real linear depth,
  - real diffuse/specular hit distances.
- Test FSR/XeSS/DLSS paths with:
  - render-resolution inputs,
  - display-resolution outputs,
  - upscaled first-hit depth/motion/normal for PostRender.

Completion standard:

- NRD path is marked supported only after split lighting and hit-distance semantics are correct.
- Upscaler path is marked supported only after motion vector direction/scale and depth convention are verified.

### Phase 6: Deferred Shaderpack Stage

Goal: make the fixed pipeline user-customizable without exposing PT concepts or Blaze3D internals.

#### 6.1 Extend shaderpack metadata

Implementation status as of Step 36: the Deferred shaderpack runtime and built-in pack migration are complete for the
current raster + ray-query path. The shared loader understands `stage: deferred`, `execution_deferred` and nested
`execution.deferred`, and rejects Deferred-stage passes that try to use PT/SBT fields such as `rgen`, `hit_groups`,
`rchit`, `rahit` or `query_sharc`. The loader also accepts `ray_query` as a Deferred-only pass kind. Java and native
selection support Deferred RT as a shaderpack owner through
`render_pipeline.module.deferred_rt.attribute.shader_pack_path`. Native shaderpack runtimes are stored by owning module
so PT, Deferred RT and PostRender can use different packs in the same graph.

`DeferredRtModule` builds a Deferred runtime plan and executes Deferred `render`, `compute`, supported `ray_query`
with optional fallback compute, and `full_screen` `compute_3d` passes through the stage execution command list.
`render` currently supports the hardened `content: minecraft_gbuffer` backend. Unsupported runtime pass content fails
during module build with a clear runtime-plan error.

The module now builds a generated `DeferredRtGraphPlan` after active runtime selection. The graph plan expands active
concrete pass names into per-pass image/buffer intent, fixed semantic native resources, scene-set descriptor intent and
no-op reasons. It is used to prune classification/visibility/lighting-queue/G-buffer allocations, descriptor updates,
fixed direct/reflection/GI uniform uploads, TLAS bindings, pass stats bindings, pipeline creation for graph no-op fixed
passes and compute/full-screen pass barriers. M5 deliberately keeps the Vulkan descriptor set layout ABI fixed; the
generated descriptor plan decides which existing bindings are updated or left untouched, not which set-layout bindings
exist. G-buffer render-pass transitions still use dedicated render-pass pre/post barriers.

The fixed native shader-source path has been migrated into built-in `vanilla-deferred-rt`: G-buffer, classification,
queue build, direct lighting, queued reflection/GI and compose are declared by the pack and recorded by the hardened
Deferred runtime. Runtime synchronization is no longer keyed by pass `name`; fixed C++ hooks use the explicit optional
pass `semantic` field, while execution commands still reference `name`.

Deferred RT should not expose the old raw `execution.deferred.commands` list as the long-term authoring model. The
runtime can continue to consume an internal ordered command list, but Deferred pack parsing should normalize a safer
public schema into that list:

```text
C++ backbone: clear -> gbuffer -> classify -> queues -> lighting -> compose
slots: fixed semantic implementations selected by slot key / semantic
passes: custom extension passes without fixed semantic
insertions: phase -> [custom pass names] lists
resources: pack-owned logical resources
```

`execution.deferred.commands` is therefore a transition/runtime IR. The public Deferred schema should be `slots` +
`passes` + `insertions`, with `insertions` lists controlling extension pass order inside C++-defined phases such as
`after_gbuffer`, `after_classify`, `after_lighting_queues`, `before_compose` and `after_compose`.

Shaderpack selection is module-scoped:

- `Presets.java` only defines preset identities. A preset persists module attribute overrides in `presetModules`.
- Shaderpack path is a module attribute, not a global preset field.
- PT uses `render_pipeline.module.ray_tracing.attribute.shader_pack_path`.
- Deferred RT uses `render_pipeline.module.deferred_rt.attribute.shader_pack_path`.
- PostRender uses `render_pipeline.module.post_render.attribute.shader_pack_path`.
- A shaderpack selected for PT must expose `ray_tracing` stage content.
- A shaderpack selected for Deferred RT must expose `deferred` stage content.
- A shaderpack selected for PostRender must expose `post_render` stage content.
- A single pack may expose multiple stages, but each module consumes only its own stage.
- Empty PT path keeps the historical built-in `vanilla-pt` fallback.
- Empty Deferred RT path loads the built-in `vanilla-deferred-rt` pack.
- Empty PostRender path keeps the historical built-in `vanilla-pt` post-render fallback.
- The current UI still chooses the primary PT or Deferred pack. As a temporary bridge, Java writes that selected pack
  into compatible same-pipeline shaderpack modules and synchronizes dynamic attributes for modules that resolve to the
  same pack path. A later preset UI must expose per-module pack configuration directly.

Implementation steps:

- Add `stage: deferred`. Completed in Step 29.
- Add `execution_deferred`. Completed in Step 29.
- Add Deferred shaderpack selection and dynamic attribute routing. Completed in Step 30.
- Add Deferred shaderpack inspection/validation in `DeferredRtModule`. Completed in Step 30 for duplicate/unknown pass references.
- Make PostRender shaderpack ownership explicit. Completed in Step 32.
- Add pass kinds:
  - `render`, metadata accepted in Step 29,
  - `compute`, metadata accepted in Step 29,
  - `full_screen`, metadata accepted in Step 29 for graphics and `compute_3d` backends,
  - `ray_query`, metadata accepted in Step 31,
  - optional `copy/resolve`.
- Add resource kinds:
  - internal image,
  - imported public export,
  - sampled texture,
  - storage buffer,
  - scene draw stream.
- Add validation that Deferred RT packs cannot declare:
  - `rgen`, completed in Step 29,
  - `rmiss` / `miss`, completed in Step 29,
  - `rchit`, completed in Step 29,
  - `rahit`, completed in Step 29,
  - SBT-style shader/hit fields, completed in Step 29,
  - PT hit groups, completed in Step 29.

Remaining implementation steps after Step 41:

- Deferred fixed semantic order validation while the runtime still accepts raw `execution.deferred.commands` is complete
  in Step 37.
- Public-schema parsing for complete `slots`, custom `passes`, phase `insertions`, custom pass type limits and internal
  command-list generation is complete in Step 38.
- Deferred logical resources, `resources.images` / `resources.textures` / `resources.buffers` lowering, `custom.*`
  namespace validation, phase visibility and custom pass read/write validation are complete in Step 39.
- Root/common definitions and first-version `requires` capability validation are complete in Step 40. The current
  capability set is `ray_query`, `multiview`, `half_precision` and `storage_image_format:<format>`.
- Explicit resource lifetime policy is complete in Step 41 for `per_frame`, `history`, `persistent` and
  `imported_texture`. Runtime resize and shaderpack reload reset history through resource rebuild/pre-close paths.
  Explicit camera-cut, dimension/world-change and major render-scale reset signals still need engine hooks.
- Generated graph-plan resource expansion and first descriptor/barrier pruning are complete in the M5 continuation
  milestone:
  - fixed semantics expand to public outputs and native module resources,
  - declared `gbuffer.*`, `lighting.*`, `classification.*`, `visibility.*`, `queue.*` and `scene.*` aliases drive
    descriptor/barrier intent,
  - inactive graph resources skip descriptor writes, uploads, initialization and generated compute barriers,
  - `classify` and `build_lighting_queues` can become no-op nodes with no pipeline or dispatch.
- `ScenePrepare` lifetime is now `WorldPipeline` scoped through `SharedSceneRuntime`, and provider packet enumeration is lazy behind the `Provider` requirement flag.
- Expand preset storage/UI so a preset can configure the selected pipeline and each shaderpack-capable module's pack.
- Define whether cross-module shaderpack resource references are forbidden by validation or represented through explicit
  public pipeline resources. The current design assumes no implicit cross-module shaderpack runtime resources.
- Add generated descriptor binding map and barrier-plan reporting to the future shaderpack lint/offline parser tool so a
  Deferred pack can be validated without launching Minecraft.
- Add game, VR and preset switching tests for the built-in Deferred pack.

Completion standard:

- A deferred pack can declare G-buffer, lighting and compose passes.
- A pack using PT-only declarations fails before rendering with a clear error. The PT-only part is covered by Step 29,
  unknown Deferred execution pass references are covered by Step 30, and module/stage compatibility is covered by
  Step 31. Runtime-plan validation for `render`, `compute`, `ray_query`, fallback ray-query, `lighting_queue` schedule
  and `full_screen compute_3d` is covered by Steps 34-35. Explicit pass `semantic` separation is covered by Step 36.

#### 6.1.1 Deferred shaderpack authoring schema requirements

The first stable Deferred public schema should keep the C++ backbone fixed and expose controlled extension points:

- `slots`: map of fixed semantic slots. Slot key is the fixed semantic (`clear_contract`, `gbuffer`, `classify`,
  `build_lighting_queues`, `direct_light`, `reflection`, `gi`, `compose`). Pack authors can replace the shader
  implementation, but cannot create new fixed semantic names. Step 38 implements this parser and requires the complete
  eight-slot backbone in the first version; built-in-slot inheritance/overlay is not implemented yet.
- `resources`: pack-owned logical images/textures/buffers declared through the existing ShaderPack texture/buffer
  resource model where possible. Authors name resources such as `custom.ssao` and `custom.noise`; C++ owns the actual
  Vulkan image/buffer allocation, descriptor slots, layouts, barriers, resizing and VR layer handling. Step 39 implements
  the first logical registry and phase-aware resource validation by lowering `resources.images`, `resources.textures` and
  `resources.buffers` to the existing runtime texture/buffer configuration path. Existing root `textures` and `buffers`
  are preserved; when used in a Deferred public-schema pack, they are also treated as pack-owned logical resources and
  must use `custom.*`. Step 41 adds explicit `lifetime` to both wrapper resources and preserved root runtime resources.
- `passes`: custom extension passes. First stable version should allow compute/full-screen compute only. Custom passes
  must not declare fixed `semantic`; they are connected through `insertions` plus `inputs`/`outputs`. Step 38 enforces
  compute or full-screen `compute_3d` only, rejects `lighting_queue` schedules for custom passes, and requires
  `local_size.z == 1`. Step 39 validates custom pass resource reads/writes against declared logical resources and the
  phase-visible built-in namespaces.
- `insertions`: phase lists such as `{ "after_classify": ["custom_ssao"] }`. The list order is the phase-local
  execution order. Do not expose arbitrary `after: pass_name` dependencies in the first version. Step 38 implements
  phase parsing, rejects unknown phases and requires every custom pass to be inserted exactly once.

Step 38 also makes `execution.deferred.commands` and `execution_deferred` invalid in public-schema packs. C++ lowers
`slots` plus `insertions` into the existing internal `deferredExecution.commands` list, which remains the runtime IR.
Pack authors should not write that command list for Deferred public packs.

Step 39 keeps resources as logical data nodes, not Vulkan declarations. A pack author can write:

```json
"resources": {
  "images": [
    {
      "name": "custom.ssao",
      "type": "texture2d_array",
      "format": "r16f",
      "size": "render",
      "layers": "view_count",
      "usage": ["storage", "sampled"]
    }
  ],
  "textures": [
    {
      "name": "custom.noise",
      "type": "texture2d",
      "format": "rgba8",
      "width": 256,
      "height": 256,
      "source": "textures/noise.png",
      "usage": ["sampled"]
    }
  ],
  "buffers": [
    {
      "name": "custom.tiles",
      "type": "ssbo",
      "size": "TILE_COUNT_X * TILE_COUNT_Y * VIEW_COUNT * 16",
      "usage": ["storage"]
    }
  ]
}
```

Custom passes then declare logical edges:

```json
{
  "name": "custom_ssao",
  "type": "compute",
  "compute": "custom_ssao.comp",
  "schedule": "screen",
  "inputs": { "images": ["gbuffer.depth", "custom.noise"] },
  "outputs": { "images": ["custom.ssao"], "buffers": ["custom.tiles"] }
}
```

C++ lowers this into runtime resources, descriptor bindings, resource barriers and the generated internal command list.
Shader compilation receives stable binding macros such as `RADIANCE_RUNTIME_RESOURCE_SET`,
`RADIANCE_RESOURCE_CUSTOM_SSAO_STORAGE_BINDING`, `RADIANCE_RESOURCE_CUSTOM_SSAO_SAMPLED_BINDING`, and
`RADIANCE_RESOURCE_CUSTOM_TILES_BUFFER_BINDING`. Pack authors still should not write descriptor set numbers, binding
numbers, image layouts, buffer offsets or Vulkan barriers in JSON.

Required authoring features:

- Attributes/uniforms:
  - Reuse the existing PT-compatible root `attributes` array syntax. Do not add a Deferred-only compact object/map
    syntax unless a future requirement cannot be represented by the shared ShaderPack model.
  - Existing entries use `name`, `type`, `default_value`, and optional `define`. Range UI should continue to use the
    existing type strings such as `int_range:min-max`, `float_range:min-max`, and `enum:a-b-c`.
  - Attribute `define` already provides static shader definitions from configured values. Runtime tuning values needed by
    shaders should reuse that path first; if true runtime uniforms are needed later, add them as an explicit extension to
    the shared ShaderPack model rather than a Deferred-only syntax.
- Defines/permutations:
  - Reuse pass-level `define`, `defines`, or `definitions`, and attribute-level `define`, for simple compile-time
    switches such as `RADIANCE_HIGH_QUALITY_GI` and `RADIANCE_USE_BLUE_NOISE`.
  - Step 40 adds common/root definitions through the same existing aliases. Root definitions merge into every shader
    compile request before attribute definitions, pass/request definitions and generated runtime-resource definitions.
  - Variant/permutation support is still future work. The current schema preserves backend/pass `requires` metadata so a
    later `quality: low/medium/high/ultra` or backend variant model can be added without changing the logical resource
    graph.
- Feature requirements/fallbacks:
  - Step 40 implements first-version `requires` at pack/root, pass and slot level. Supported capabilities are
    `ray_query`, `multiview`, `half_precision` and `storage_image_format:<format>`.
  - Root requirements are hard pack requirements. Pass/slot requirements gate that runtime pass and produce a precise
    runtime-plan error if an executed pass is unsupported.
  - Ray-query passes use their ray-query shader when the device supports ray query, use `fallback_compute` when ray query
    is unavailable and a fallback is declared, and fail validation when neither path is available.
  - A generic fallback object/key and a full backend permutation matrix are not implemented yet.
- Resource lifetime policy:
  - Step 41 adds explicit `lifetime` values: `per_frame`, `history`, `persistent`, and `imported_texture`.
  - Compatibility defaults remain stable: imported file textures default to `imported_texture`, `shared: true` defaults
    to `persistent`, and non-shared runtime resources default to `per_frame`.
  - Explicit `lifetime` values that contradict `shared` are invalid. Buffers cannot use `imported_texture`, and file
    textures must use `imported_texture`.
  - `history` images must be writable storage resources in both wrapper `resources.images` syntax and preserved root
    `textures` syntax. They allocate two instances per swapchain frame, expose current bindings plus previous bindings,
    and use `custom.name.previous` as an input-only alias for the previous slot.
  - Generated shader definitions include previous binding macros such as
    `RADIANCE_RESOURCE_CUSTOM_SSAO_PREVIOUS_SAMPLED_BINDING`,
    `RADIANCE_RESOURCE_CUSTOM_SSAO_PREVIOUS_STORAGE_BINDING`, and
    `RADIANCE_RESOURCE_CUSTOM_TILES_PREVIOUS_BUFFER_BINDING`.
  - Runtime resize and shaderpack reload clear history resources to zero through resource rebuild/pre-close paths.
    Camera-cut, dimension/world-change and major render-scale reset signals still need explicit engine-level hooks.
- Resource graph diagnostics:
  - Step 39 validates the in-loader graph for custom resource declarations, phase visibility, protected namespace writes
    and read-before-write errors.
  - A developer-facing expanded report is still missing. It should show the generated pass order, resource reads/writes,
    descriptor binding map, barrier plan, invalid phase errors and unused resource warnings.
- Debug outputs:
  - Pack authors must be able to expose logical resources to debug views and offline tools, for example SSAO, wetness,
    masks or intermediate lighting.
  - Debug outputs should name the resource and display format (`grayscale`, `rgba`, false color, etc.).
- Offline lint / expanded graph report:
  - Provide a developer command such as `radiance-dev lint pack/`.
  - The report must include expanded pass order, resource reads/writes, generated barrier plan, generated descriptor
    binding map, invalid phase errors, unused resource warnings and unsupported feature warnings.
  - This tool is more important than exposing lower-level Vulkan controls; without it, shaderpack authors cannot debug
    resource graph mistakes efficiently.

#### 6.2 Add view/layer execution declarations

Implementation steps:

- Add `view_execution` to pass declarations.
- Support:
  - `single_instance_3d_dispatch`,
  - `per_view_dispatch`,
  - `layered_render_pass`,
  - `per_view_render_pass`.
- Add expressions such as `VIEW_COUNT`, but validate them before pipeline creation.

Completion standard:

- Every pass states how it executes across views/layers.
- Stereo behavior is determined by shaderpack metadata and backend capability, not by ad hoc code branches.

#### 6.3 Add ray-query backend selection

Implementation status:

- Step 40 adds first-version feature declarations:
  - `requires: ray_query`,
  - `requires: multiview`,
  - `requires: half_precision`,
  - `requires: storage_image_format:<format>`.
- Existing `ray_query` passes continue to use `fallback_compute` as the executable fallback shader path.
- `DeferredRtModule` builds the runtime plan from actual Vulkan device capabilities and reports unsupported packs or
  referenced passes before command recording.

Remaining implementation steps:

- Add a generic fallback object/key only if a future pass type needs more than the current `fallback_compute` path.
- Add backend tags for Vulkan/Metal/DX12/WebGPU future support.
- Validate query kinds:
  - `visibility`,
  - `closest_hit`.
- Keep closest-hit material evaluation in Deferred RT shader code or a backend-neutral material callback abstraction; do not require PT hit shaders.

Completion standard:

- Vulkan ray-query path selects ray-query shaders. Completed for Deferred ray-query compute passes.
- No-ray-query fallback selects fallback shaders or fails with a useful missing-feature error. Completed for
  `fallback_compute`.
- Backend selection is deterministic and covered by tests. First-version capability validation is covered by
  `deferred_rt_shaderpack_test`; full backend/permutation selection remains future work.

#### 6.4 Build `vanilla-deferred-rt`

Status: completed in Step 35.

Implementation steps:

- Add built-in pack under:

```text
MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt
```

- Move the fixed G-buffer, lighting, classification and compose passes into this pack.
- Keep module-owned resources and public exports stable.
- Install the pack as `shaders/world/deferred_rt/vanilla-deferred-rt.zip`.
- Exclude `world/deferred_rt/internal` from normal installed `.spv` compilation and remove stale old Deferred `.spv`
  artifacts during install.

Completion standard:

- Built-in Deferred RT rendering uses the same runtime path as user packs.
- Editing the built-in pack changes rendering without modifying C++ pass ordering.

### Phase 7: Offline GPU Runner and Replay

Goal: make renderer iteration possible without launching Minecraft.

#### 7.1 Add `deferred_rt_offline`

Implementation steps:

- Add a tool under:

```text
MCVR-custom/tools/deferred_rt_offline/
```

- The tool must:
  - create Vulkan instance/device using MCVR wrappers,
  - avoid swapchain creation,
  - create offscreen layered images,
  - load a deferred shaderpack,
  - load a synthetic scene fixture,
  - run one frame,
  - dump statistics and optional images.
- Add flags:
  - `--shader-pack`,
  - `--scene`,
  - `--width`,
  - `--height`,
  - `--views 1|2`,
  - `--view-mode layered|per_view`,
  - `--rt-mode ray_query|fallback`,
  - `--out`.

Completion standard:

- One command renders `single_triangle.json`.
- One command renders a stereo fixture and dumps both layers.
- The runner can validate placeholder, G-buffer and lighting phases without Minecraft.

#### 7.2 Add synthetic fixtures

Implementation steps:

- Add fixtures for:
  - `single_triangle.json`,
  - `two_planes.json`,
  - `cornell_box.json`,
  - `mirror_plane.json`,
  - `alpha_cutout_quad.json`,
  - `emissive_block.json`,
  - `single_triangle_stereo.json`,
  - `motion_vector_stereo.json`,
  - `mirror_plane_stereo.json`.
- Fixture format must describe `SceneDrawPacket`-level data, not Minecraft blocks.

Completion standard:

- Fixtures exercise raster, depth, motion, cutout, reflection, GI and stereo layer correctness.
- Output statistics include min/max/mean, NaN/Inf count, negative count and non-zero count.

#### 7.3 Add replay from captured game frames

Implementation steps:

- Add an optional debug export:

```text
--radiance-dump-scene-frame path
```

- Dump:
  - world uniforms,
  - sky uniforms,
  - scene draw packets,
  - material ids,
  - texture references,
  - acceleration structure metadata where possible,
  - selected shaderpack and attributes.
- Teach the offline runner to replay this dump.

Completion standard:

- A frame can be captured once in-game and replayed offline.
- Renderer and shaderpack fixes can be tested against the captured frame without relaunching Minecraft.

### Phase 8: Quality, Transparency and Performance

Goal: move from prototype to practical gameplay renderer.

#### 8.1 Implement transparent and refraction policy

Implementation steps:

- Require the provider-side render-state metadata from Phase 3.2.1 before treating true translucent content as supported.
- Add explicit `transparent_forward` content path.
- Decide which translucent surfaces:
  - write only color,
  - write refraction,
  - update primary depth,
  - stay excluded from G-buffer.
- Add water/glass screen-space refraction first.
- Add optional ray-query refraction later.
- Write `primary_refraction -> first_hit_refraction`.
- Preserve Minecraft/modded translucent ordering through provider sort keys or an equivalent ordered draw stream; do not infer order from G-buffer classification.

Completion standard:

- Transparent content has a documented path.
- Unsupported translucent behavior is visible in debug counters.
- `first_hit_depth` semantics do not change silently when transparency is enabled.
- `WORLD_TRANSPARENT` fallback packets are no longer the only source of transparent/cutout classification when extended metadata is available.

#### 8.2 Add temporal stability features

Implementation steps:

- Verify jitter policy and previous-frame matrices.
- Add hooks for `TemporalAccumulationModule`.
- Add history reset conditions:
  - resize,
  - shaderpack reload,
  - camera cut,
  - dimension/world change,
  - major render scale change.
- Keep history resources internal unless consumed by existing public modules.

Completion standard:

- Motion-vector direction and scale are validated by reprojection tests.
- Temporal accumulation does not smear across disocclusions in basic fixtures.

#### 8.3 Add SHARC/probe/cache modes

Implementation steps:

- Decide whether SHARC resources are shared with PT or owned separately by Deferred RT.
- Add quality attribute and debug mode.
- Feed cache from Deferred RT primary surfaces and GI rays.
- Keep SHARC outputs internal unless a downstream module needs them.

Completion standard:

- SHARC can be disabled without changing the public output contract.
- Enabling SHARC improves GI stability/performance without changing downstream wiring.

#### 8.4 Add performance instrumentation

Implementation steps:

- Add GPU markers for:
  - scene prepare,
  - G-buffer,
  - classification,
  - direct lighting,
  - reflections,
  - GI,
  - compose,
  - export/copy,
  - denoiser/upscaler handoff.
- Add counters:
  - draw packets,
  - rendered triangles,
  - ray-query pixels,
  - reflection rays,
  - GI rays,
  - skipped pixels by reason.

Completion standard:

- A developer can identify whether a frame is raster-bound, ray-query-bound, denoiser-bound or upscaler-bound.
- Debug counters work in mono and stereo.

### Phase 9: Long-Term Backend Alignment: Metal, DX12, WebGPU

Goal: keep the architecture portable even though the first implementation is Vulkan-focused.

This phase can stay high-level until backend constraints are known. The important early requirement is that Phases 1-8 do not hard-code Vulkan-only ideas into shaderpack contracts or public module contracts.

Implementation steps:

- Keep shaderpack declarations backend-neutral:
  - no Vulkan image layouts,
  - no Vulkan barrier names,
  - no Vulkan descriptor set numbers in user-facing metadata.
- Keep ray-query passes separate from PT SBT/hit shader passes.
- Add backend capability descriptors:
  - Vulkan ray query,
  - Vulkan fallback,
  - Metal ray query or closest available equivalent,
  - DX12 inline raytracing,
  - WebGPU fallback.
- Add optional Metal validation later:
  - shader translation or separate `.metal` variant,
  - acceleration structure mapping,
  - offscreen runner on macOS.

Completion standard:

- Deferred shaderpacks can declare required features and fallbacks without naming Vulkan internals.
- Unsupported backends fail at pack validation with a feature error, not during command recording.
- Existing PT can remain Vulkan-only while Deferred RT has a path to other backends.

### Phase 10: Long-Term Scene Input Alignment: Blaze3D Adapter

Goal: allow Blaze3D-style scene input later without changing Deferred RT shaderpacks or downstream module contracts.

This phase should not block the first renderer, but Phases 3 and 6 must leave room for it.

Implementation steps:

- Keep `SceneProvider` as the only boundary between game/render-source data and Deferred RT.
- Keep `SceneDrawPacket` independent from Minecraft classes, Blaze3D classes and loader-specific classes.
- Later add `Blaze3DSceneProvider` that maps:
  - Blaze3D render layers to `SceneGeometryClass`,
  - Blaze3D vertex formats to internal vertex declarations,
  - Blaze3D buffers to `SceneDrawPacket` buffers,
  - texture/material handles to internal material refs,
  - draw ordering into Deferred RT geometry streams.
- Add synthetic Blaze3D-like packet fixtures before testing real mods.

Completion standard:

- Deferred RT shaderpacks do not mention Blaze3D.
- Switching scene providers does not change public output names or downstream preset wiring.
- Compatibility work remains isolated to provider/backend mapping code.

---

## 16. Development Workflow

Recommended daily loop:

1. CPU tests:

```text
cmake --build build --target mcvr_tests
ctest --test-dir build --output-on-failure
```

2. Shaderpack lint:

```text
mcvr_shaderpack_lint --pack MCVR/src/shader/world/deferred_rt/internal/vanilla-deferred-rt --stage deferred
```

3. Offline GPU scene:

```text
deferred_rt_offline --scene MCVR/tests/fixtures/scenes/single_triangle.json --width 128 --height 128
```

4. Optional RenderDoc capture from offline runner.

5. Only then launch Minecraft for:

- Java UI/preset verification,
- actual mod compatibility,
- real world geometry coverage,
- input/resource lifecycle edge cases.

---

## 17. Design Rules

1. Do not make `DeferredRtModule` depend on `RayTracingModule`.
2. Do not duplicate TLAS/BLAS preparation; extract `ScenePrepare`.
3. Do not make shaderpacks aware of Blaze3D.
4. Do not expose Vulkan layouts/barriers in shaderpack JSON.
5. Do not make deferred RT depend on PT-style SBT/hit shader passes.
6. Treat `ray_query` compute passes as the portability baseline.
7. Treat VR array layers and `viewCount` as first-version requirements, not later integration work.
8. Do not collapse stereo output into side-by-side images; use array layers.
9. Do not block first version on perfect transparency.
10. Do not change NRD/PostRender contracts until the new module is stable.
11. Keep all pass/resource declarations backend-neutral before Vulkan pipeline creation.
12. Keep offline tests runnable without Minecraft.

---

## 17.1 Current Runtime Flexibility Baseline

The current Deferred RT native runtime now builds an active shaderpack plan before module build:

- `enabled_if` and selection profiles are static graph-selection inputs. They are evaluated on pack load, pack reload or
  attribute/native pipeline rebuild, not per frame.
- Shaderpack profiles can choose `ray_query`, `compute_3d` or `disabled` backends for direct light, reflection and GI.
- `auto` has no hidden engine priority beyond being the default selector string when no selector is specified.
- Disabled and unselected backends do not create Deferred runtime pipelines or dispatch work.
- Runtime resources declared by the shaderpack are scoped to active concrete pass names before descriptor layouts and
  resource allocation.
- Shaderpacks can declare named resolution domains, including the built-in `full`, `half` and `quarter` domains. Active
  Deferred passes and pack-owned runtime images can reference a domain; `screen` and `tile_grid` compute schedules use
  the selected domain's dispatch extent instead of launching full-resolution work.
- Deferred compute/ray pass push constants now expose `fullResolution`, `dispatchResolution`, `scale`, `invScale`,
  `fullToDispatch`, `dispatchToFull`, `layerCount` and `flags`. Shader helpers use pixel-center mapping between
  dispatch and full-resolution pixels, with nearest full-resolution G-buffer sampling as the first scaled-pass policy.
- Lighting queue schedules can now choose a queue domain independently from the pass output policy:
  `full_resolution`, `pass_resolution` or a named resolution domain. Active queue headers, record capacity and the
  queue-builder tile dispatch use that domain, and queued reflection/GI shaders map queue dispatch pixels back to
  full-resolution G-buffer pixels before sampling.
- The old blanket `clear_contract` compute pass is now a compatibility marker for native output defaults. The active
  runtime skips pipeline creation and dispatch for that marker, then records targeted public-output clears only for
  unwritten or partial-writer outputs. G-buffer defaults are suppressed only on frames that actually render G-buffer
  draw packets, and `compose` remains a partial writer because invalid-depth pixels are left to the default clear.
- The runtime now builds a generated graph plan from active pass resources. It prunes unused descriptor updates,
  classification/visibility/queue/G-buffer resource initialization, fixed lighting uniform uploads, TLAS bindings,
  pass stats bindings, compute/full-screen barriers and no-op fixed-pass pipelines/dispatches. The descriptor layout ABI
  remains fixed.
- Scaled passes are rejected if they directly write public full-resolution outputs without an explicit future upsample
  path.
- Acceleration/provider scene-runtime requirements are derived from the active plan instead of forced for every
  Deferred configuration.
- Deferred compute/ray `vr: view_agnostic` means layer-aware single dispatch; it does not mean mono. `per_view` and
  `mono_replicate` are reserved until explicit command-recording modes are implemented.
- Deferred diagnostics no longer depend on console IO; compact overlay text is pulled from native state, while stats
  buffers/readbacks/atomics are disabled unless diagnostics stats are enabled.

The next architecture work is diagnostics snapshots/file traces/timestamps, explicit non-layered VR execution modes,
compose variants, history/upsample policy, barrier merging and deeper scene/runtime optimization.

---

## 18. Open Questions

These should be answered during Phase 3-5:

1. Should first public output `first_hit_depth` be pure raster depth or RT-corrected depth for translucent/refraction?
2. Should chunk cutout use alpha test in G-buffer or be moved to a separate forward path?
3. How much of the current PBRVertex should be kept for raster G-buffer versus split into compact position/material streams?
4. Does NRD expect different hit distance semantics for raster-primary + RT-secondary compared to current PT?
5. Should SHARC be shared between PT and Deferred RT, or have separate runtime resources?
6. How should shaderpack variants select geometry group names for raster passes?
7. What is the minimum macOS target and Metal feature set for the `RayQuery` tier?
8. Should macOS shaderpacks use separate `.metal` files, generated MSL from a shared source, or a restricted GLSL dialect translated by tooling?
9. Which deferred RT features are mandatory on macOS, and which are quality-tier optional?
10. Should the first Vulkan raster implementation use multiview render passes immediately, or ship per-layer fallback first while preserving the same shaderpack contract?
11. What tile size should Deferred RT use for the first queue builder: 8x8 for tighter culling, 16x16 for lower queue overhead, or a runtime attribute?
12. Should foveated rendering influence secondary queue density immediately after OpenXR visibility-mask clipping lands, or only after layered queue correctness is proven? It must not reduce primary G-buffer fidelity inside the visible mask in the first versions.

---

## 19. Recommended First Milestone

The first meaningful milestone should be the contract shell, not G-buffer rendering:

```text
Deferred RT module selected in UI
  -> native module builds
  -> internal neutral output names are defined
  -> LegacyExportMap maps internal names to PT-compatible public output names
  -> every public export allocates layerCount = eyeCount
  -> every public export is cleared deterministically
  -> ToneMapping + PostRender can connect without PT
  -> CPU output-contract and view/layer tests exist
```

Do not start with GI, SHARC, G-buffer complexity or full user shaderpack customization. The first step is making the module boundary explicit and testable.

The second milestone should prove primary visibility:

```text
Contract shell
  -> fixed G-buffer world_opaque pass runs in mono and stereo-layered mode
  -> legacy exports receive real albedo/normal/depth/motion data
  -> debug view shows albedo/normal/depth
  -> PostRender consumes real first_hit_depth/motion/normal_roughness
```

The third milestone should prove the actual deferred RT role:

```text
Layered G-buffer
  -> classify_deferred_pixels
  -> OpenXR visibility mask marks hidden-area pixels invalid per eye when available
  -> compact per-view queues for reflection/GI/refraction candidates
  -> direct_lighting ray query for sun/moon shadows
  -> simple reflection ray query for low-roughness pixels via queued work
  -> compose
  -> transparent_forward fallback
  -> stereo offline fixture passes
```

At that point the module has the right shape: raster primary visibility, ray-query secondary visibility, queued expensive secondary work, transparent forward fallback, and VR layers. GI quality, SHARC and advanced transparent RT can then be added without changing the public architecture.
