# Deferred RT Implementation Log

Status: in progress

## Environment

- Workspace: `G:\cpp\radiance`
- Java project: `G:\cpp\radiance\Radiance-custom`
- Native renderer project: `G:\cpp\radiance\MCVR-custom`
- Architecture document: `G:\cpp\radiance\Radiance-custom\docs\deferred-rt-module-architecture.md`
- Visual Studio 2026: `D:\Program Files\Microsoft Visual Studio\18\Community`
- Vulkan SDK: `D:\VulkanSDK\1.4.335.0`
- Python: `C:\ProgramData\miniconda3`
- JDK: `C:\Program Files\Zulu\zulu-21`
- Current date: 2026-06-20
- Shell: PowerShell

## Implementation Requirements

- Work step by step and keep this log updated after each meaningful step.
- Do not rely on temporary shortcuts that make the final architecture harder to reach.
- If a piece cannot be fully implemented in the current phase, record the reason and the intended follow-up here.
- Verify compilation after each step where the codebase is expected to compile.
- Commit code and documentation when a phase milestone is reached.
- Keep the Deferred RT module independent from `RayTracingModule`.
- Preserve downstream contracts for ToneMapping, PostRender, NRD, DLSS, FSR, XeSS and TemporalAccumulation.
- Treat VR array layers and `eyeCount` as first-version requirements.
- Do not expose Blaze3D, Java mixins, Vulkan layouts, or PT SBT concepts in the Deferred RT public contract.

## Current Plan

The original contract-shell plan below is complete enough for bring-up, but it is not the final runtime shape.
The next Deferred RT phase is planned in `docs/deferred-rt-runtime-flexibility-and-diagnostics-plan.md` and covers:

- pack-defined backend selection profiles instead of hidden engine `auto` priority,
- first-class `ray_query`, `compute_3d` and `disabled` backends,
- static `enabled_if` graph selection,
- named resolution and queue domains,
- precise scene/runtime requirements,
- JVM-safe diagnostics without per-frame console IO,
- baseline pipeline optimization for clears, barriers, descriptors, G-buffer work, queue scheduling, compose variants and VR fallback reporting.

1. Add a Deferred RT contract shell native module.
2. Add the Java-side module YAML so the existing pipeline can instantiate it manually.
3. Allocate all public output images with `layerCount = eyeCount`.
4. Deterministically clear all outputs in `render3D`.
5. Build native code, then fix integration issues.
6. Commit the first milestone once the contract shell builds.

## Progress

### Step 1: Implementation Log

- Status: completed.
- Notes:
  - `MCVR-custom` already has unrelated modified middleware files. These must not be reverted or included in milestone commits.
  - `Radiance-custom/docs/deferred-rt-module-architecture.md` is currently untracked and was provided as design context.

### Step 2: Native Contract Shell

- Status: completed.
- Files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/clear_contract.comp`
  - `MCVR-custom/src/core/render/pipeline.cpp`
- Implemented behavior:
  - Added `DeferredRtModule` as a standalone `WorldModule`.
  - Registered the module in `Pipeline::collectWorldModules()`.
  - Defined zero input images and sixteen public output images.
  - Added a single `PublicOutputContract` table mapping internal neutral names to public legacy exports and formats.
  - `setOrCreateOutputImages()` validates Java/native output formats against that table.
  - Allocates output images with `layerCount = eyeCount`.
  - Creates per-layer views for stereo outputs.
  - Uses a compute clear pass over `image2DArray` outputs, dispatching `z = active eye/layer count`.
  - Keeps all output images in `VK_IMAGE_LAYOUT_GENERAL` after the clear pass for downstream storage/sampled consumers.
- Contract notes:
  - The first fifteen outputs mirror the legacy RT public contract.
  - `gi_hit_distance` is explicit and must be connected to NRD `diffuseHitDepthImage` in future Deferred RT presets.
  - The output contract table is the first `LegacyExportMap` seed. G-buffer and lighting code must write internal graph resources and export through this mapping instead of using `first_hit_*` names internally.
- Deferred work:
  - No scene provider, G-buffer, ray-query lighting, NRD-valid hit distances, deferred shaderpack runtime, or offline runner yet.
  - No Java preset wiring yet; manual pipeline construction should be used for this milestone.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core shaders -- /m`
  - First run reconfigured CMake after glob changes, then linked against the old project graph and failed with unresolved `DeferredRtModule` symbols.
  - Second run succeeded after generated project files included `deferred_rt_module.cpp`.

### Step 3: Java Module YAML

- Status: completed.
- Files:
  - `Radiance-custom/src/main/resources/modules/deferred_rt.yaml`
- Notes:
  - No Java JNI ABI changes were needed.
  - No Java preset changes were made.
- Verification:
  - `gradlew.bat classes` completed successfully using `JAVA_HOME=C:\Program Files\Zulu\zulu-21`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m` completed successfully.
  - Installed `clear_contract_comp.spv` to:
    - `MCVR-custom/bin/res/world/deferred_rt/clear_contract_comp.spv`
    - `Radiance-custom/src/main/resources/shaders/world/deferred_rt/clear_contract_comp.spv`

### Step 4: Current Manual Test Contract

- Status: documented.
- A manual graph can use:
  - `DeferredRtModule.radiance -> ToneMapping.denoised_radiance`
  - `ToneMapping.mapped_output -> PostRender.ldr_input`
  - `DeferredRtModule.first_hit_depth -> PostRender.first_hit_depth`
  - `DeferredRtModule.radiance -> PostRender.hdr_input`
  - `DeferredRtModule.motion_vector -> PostRender.motion_vector`
  - `DeferredRtModule.normal_roughness -> PostRender.normal_roughness`
  - `PostRender.post_rendered -> final output`
- Expected visual result:
  - Deterministic black output.
  - No real scene visibility or lighting yet.
- Known limitation:
  - Preset mode still uses `RayTracingModule`; Deferred RT must be added manually in pipeline mode for this milestone.
  - Shared shaderpack loading remains tied to `RayTracingModule` for PT/post shaderpack workflows. The contract shell does not depend on it. Deferred shaderpack support must redesign this rather than silently reusing PT stage semantics.

### Step 5: CPU Contract Test Harness

- Status: completed.
- Files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_contract.hpp`
  - `MCVR-custom/tests/CMakeLists.txt`
  - `MCVR-custom/tests/deferred_rt_contract_test.cpp`
  - `MCVR-custom/CMakeLists.txt`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
- Implemented behavior:
  - Moved Deferred RT public output contract constants into a pure contract header shared by production code and tests.
  - Added layer-count helper functions for mono/stereo output rules.
  - Added `mcvr_tests` executable and CTest registration.
  - Added tests for output count, output order, public/internal names, formats, explicit `gi_hit_distance`, output usage flags and stereo layer helper behavior.
- Design note:
  - The test target intentionally does not link or initialize the Vulkan renderer. This keeps first-milestone contract tests runnable without Minecraft or GPU runtime state, while still testing the same contract constants consumed by `DeferredRtModule`.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target mcvr_tests -- /m` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 1/1 tests passed.

### Step 6: Native Build Compatibility Fix

- Status: completed.
- Files:
  - `MCVR-custom/CMakeLists.txt`
- Issue found:
  - After CMake reconfiguration, full native build re-ran NRD shader generation and failed in the Windows DXBC/FXC backend with `forced to unroll loop, but unrolling failed`.
  - NRD SPIR-V generation completed before the failure, and Radiance's Vulkan NRD wrapper creates pipelines from `pDesc.computeShaderSPIRV`.
  - After disabling DXBC, MSVC then failed compiling NRD `Wrapper.cpp` with `C1060: compiler is out of heap space`, caused by embedding another unused shader container.
  - After disabling both DXBC and DXIL, full native build still failed compiling NRD's SPIR-V-embedded generated headers with `C1060`.
- Implemented behavior:
  - Explicitly set `NRD_EMBEDS_DXBC_SHADERS=OFF` in the project NRD configuration.
  - Explicitly set `NRD_EMBEDS_DXIL_SHADERS=OFF` so the Vulkan build embeds only NRD SPIR-V shaders.
- Design note:
  - This keeps Vulkan NRD SPIR-V support enabled and avoids requiring unused D3D11/D3D12 shader containers to compile on the current toolchain.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target NRD -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core shaders mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - On this Windows toolchain, NRD's generated shader headers should be built with low MSBuild/CL concurrency after a clean CMake regeneration to avoid `C1060`.

### Step 7: First-Milestone Verification

- Status: completed.
- Verification:
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 1/1 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `gradlew.bat classes` completed successfully with `JAVA_HOME=C:\Program Files\Zulu\zulu-21`.
- Installed artifacts observed:
  - `Radiance-custom/src/main/resources/core.dll`
  - `Radiance-custom/src/main/resources/shaders/world/deferred_rt/clear_contract_comp.spv`
  - `MCVR-custom/bin/core.dll`
  - `MCVR-custom/bin/res/world/deferred_rt/clear_contract_comp.spv`
- Milestone status:
  - Deferred RT contract shell is buildable and has CPU contract coverage.
  - Ready for first milestone commit after staged diff review.

### Step 8: Shared Scene Prepare Extraction

- Status: completed.
- Files:
  - `MCVR-custom/src/core/render/scene_prepare/scene_prepare.hpp`
  - `MCVR-custom/src/core/render/scene_prepare/scene_prepare.cpp`
  - `MCVR-custom/src/core/render/modules/world/ray_tracing/ray_tracing_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/ray_tracing/ray_tracing_module.cpp`
  - Removed old path: `MCVR-custom/src/core/render/modules/world/ray_tracing/submodules/world_prepare.*`
- Implemented behavior:
  - Moved PT-owned `WorldPrepare` into renderer-owned `ScenePrepare`.
  - Removed the back-reference from scene preparation to `RayTracingModule`.
  - Exposed `ScenePrepare::contexts()` through const and non-const accessors so later modules can consume prepared scene contexts without friend/private coupling.
  - Kept RayTracing as the first consumer of the same TLAS/BLAS metadata, buffer-address tables, previous-position tables and hit-group list.
- Design note:
  - This is intentionally a no-rendering-behavior-change extraction. Deferred RT should consume `ScenePrepareContext` next, but the contract shell does not run it until a G-buffer/ray-query pass has a real use for the prepared scene.
  - Existing scene inputs are enough for the next Deferred RT stages: world/entity geometry buffers, material buffer addresses, texture mapping buffer, world/last-world/sky uniforms, TLAS/BLAS metadata and previous-object transforms. Future adapter work is still needed for Blaze3D-style draw packets and raster draw ordering.
- Verification:
  - First `core` build after the file move reconfigured CMake, then MSBuild still tried to compile the removed old path `ray_tracing/submodules/world_prepare.cpp` from the stale project graph.
  - Second `cmake --build MCVR-custom/build-radiance-custom --config Release --target core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after regenerated project files took effect.
  - After adding the const context accessor, `cmake --build MCVR-custom/build-radiance-custom --config Release --target core shaders mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 1/1 tests passed.

### Step 9: Scene Packet Metadata Retention

- Status: completed.
- Files:
  - `MCVR-custom/src/core/render/chunks.hpp`
  - `MCVR-custom/src/core/render/chunks.cpp`
  - `MCVR-custom/src/core/render/entities.hpp`
  - `MCVR-custom/src/core/render/entities.cpp`
- Implemented behavior:
  - Preserved per-geometry `geometryType`, `textureId`, vertex count and index count from existing native build tasks.
  - Extended `ChunkRenderData`, `Chunk1` and `Entity` so a neutral scene provider can build real `SceneDrawPacket` ranges and material bindings without scanning vertex data or reaching back into Java.
- Design note:
  - This does not change the JNI input ABI. Java already passes these values; native code previously used part of them for BLAS/material packing and then dropped the metadata needed by raster G-buffer passes.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after the data-model step.

### Step 10: Scene Provider Boundary

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/scene_provider/scene_provider.hpp`
  - `MCVR-custom/src/core/render/scene_provider/mcvr_scene_provider.hpp`
  - `MCVR-custom/src/core/render/scene_provider/mcvr_scene_provider.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/tests/deferred_rt_scene_provider_test.cpp`
- Intended behavior:
  - Add a neutral `SceneProvider` contract that exposes views, opaque/cutout/translucent draw packets and acceleration metadata without leaking Java, Blaze3D, Vulkan layout, PT SBT or shaderpack concepts.
  - Add an `McvrSceneProvider` that adapts the current chunk/entity data retained in Step 9.
  - Connect `DeferredRtModule` to begin a scene-provider frame before its current deterministic clear pass, without starting G-buffer rendering yet.
- Design notes:
  - Existing JNI inputs remain sufficient for this phase; the provider consumes native-side scene/geometry/material/texture metadata already supplied by Java.
  - `WORLD_TRANSPARENT` is classified as cutout for first G-buffer bring-up because the current Java input collapses true translucent and alpha-tested block layers into one geometry type.
  - True translucent/water/glass layer identity still requires a later Java/native metadata extension; the provider boundary leaves that extension localized.
- Partial implementation:
  - Added the neutral `SceneProvider` contract and draw/view/range helper declarations.
  - Kept the neutral contract free of `World`, Blaze3D and full Vulkan wrapper includes; concrete source classification lives in an MCVR adapter helper.
  - Added CPU tests for MCVR geometry classification, draw-range accumulation, stereo view/layer mapping and packet stats.
- Verification:
  - First `mcvr_tests` build after adding tests failed because the test target did not include all headers required by `all_extern.hpp`.
  - After reducing the provider header dependencies and fixing test include paths, CMake reconfiguration reached third-party dependency setup and failed updating `zlib-ng` from GitHub: `Failed to connect to github.com port 443`.
  - Reconfigured with Zulu 21 JNI paths and `FETCHCONTENT_UPDATES_DISCONNECTED*` enabled so local third-party dependency state is used without network updates.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 2/2 tests passed.
- Next step:
  - Implement runtime `McvrSceneProvider` packet enumeration and connect `DeferredRtModule` to begin a provider frame before the clear pass.
- Runtime provider implementation:
  - Added `McvrSceneProvider` that reads current chunk/entity render data and emits neutral `SceneDrawPacket`s.
  - Tightened the neutral `SceneProvider` header to depend only on GLM and Vulkan core scalar types; the MCVR adapter keeps the heavier renderer/framework includes.
  - Packet ranges are built from retained per-geometry vertex/index counts rather than scanning vertices.
  - Chunks and entities are bucketed into opaque/cutout/translucent streams with packet/index counters and explicit skipped-packet counters.
  - Views are derived from current and last `WorldUBO`, including per-eye view/projection offsets, layer indices and jitter.
  - Acceleration metadata is exposed from `ScenePrepareContext` without exposing PT SBT or hit-group concepts.
  - `DeferredRtModule` now owns `ScenePrepare`; each `DeferredRtModuleContext` owns an `McvrSceneProvider`.
  - `render3D` runs scene preparation and provider `beginFrame()` before the placeholder clear pass.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after connecting Deferred RT to `ScenePrepare` and `McvrSceneProvider`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core shaders mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 2/2 tests passed.
- Deferred work:
  - No G-buffer draw recording yet; the provider now exposes the draw streams required for that next phase.
  - Previous entity object transforms currently mirror current provider transforms unless `ScenePrepareContext` metadata is consumed by a later motion-vector implementation.
  - True translucent/cutout separation still needs a future Java/native metadata extension because current MCVR inputs collapse several render layers into `WORLD_TRANSPARENT`.

## Open Issues

- The first milestone does not implement G-buffer rasterization, ray-query lighting, scene provider extraction, deferred shaderpack runtime, or offline runner.
- Java preset support is intentionally not part of the first manual-test contract shell unless needed for compilation.
- Shader clear defaults are placeholders. They are deterministic, but only valid for contract/resource testing, not rendering quality.
- Manual pipeline outputs must be connected into the final output dependency graph; otherwise Java's topological build will omit unreferenced modules, as designed.

### Step 11: Fixed G-buffer Plan Layer

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_gbuffer.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_gbuffer.cpp`
  - `MCVR-custom/tests/deferred_rt_gbuffer_test.cpp`
  - `MCVR-custom/tests/CMakeLists.txt`
- Intended behavior:
  - Add a CPU-testable plan for the first fixed G-buffer pass before touching Vulkan command recording.
  - Define which Deferred RT public outputs are written by the opaque/cutout G-buffer pass.
  - Define which outputs remain clear-pass placeholders until lighting, reflection, fog, refraction and GI passes are implemented.
  - Keep layer/view rules compatible with the existing stereo array-output contract.
- Notes:
  - This layer is meant to be consumed by the Vulkan pass directly; it should prevent attachment order, output format and stereo layer decisions from becoming implicit in command recording code.
- Implemented behavior:
  - Added a first fixed G-buffer attachment contract for:
    - `primary_albedo_metallic`
    - `primary_specular_albedo`
    - `primary_normal_roughness`
    - `primary_motion_vector`
    - `primary_linear_depth`
    - `primary_depth`
    - `primary_emission`
  - Left radiance, reflection hit distance, direct/indirect/specular lighting, clear layer, fog, refraction and GI hit distance as explicit clear-only placeholders for later passes.
  - Added mono, per-layer stereo and multiview layer planning helpers.
  - Added draw-stream classification so opaque and cutout draw packets participate in the fixed G-buffer, while translucent/additive/overlay packets stay excluded.
  - Cutout is marked as the only first-pass alpha-test draw stream.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 3/3 tests passed.
- Deferred work:
  - No Vulkan render pass, framebuffer, graphics pipeline or shader recording is connected yet; the new plan layer is ready for that implementation.

### Step 12: Fixed Vulkan G-buffer Pass

- Status: in progress.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/gbuffer.vert`
  - `MCVR-custom/src/shader/world/deferred_rt/gbuffer.frag`
- Intended behavior:
  - Add a fixed graphics G-buffer pass after the deterministic clear pass.
  - Use `SceneProvider` draw packets only; do not read chunks/entities directly from the module.
  - Write real primary albedo/specular/normal/depth/motion/emission exports for opaque and cutout geometry.
  - Preserve array-layer output compatibility for mono, per-layer stereo fallback and multiview-capable devices.
  - Keep lighting, reflection, fog, refraction and GI outputs as clear-only placeholders until later passes are implemented.
- Notes:
  - The first Vulkan implementation may keep the existing compute clear before G-buffer rendering. That is extra work but behaviorally correct because the render pass overwrites all first-stage G-buffer attachments and leaves later-pass exports deterministic.
- Resume status:
  - Native repository still only has unrelated middleware edits outside Deferred RT ownership.
  - Step 12 code was not yet committed or partially applied; implementation resumes from the clear-only module plus Step 11 CPU G-buffer plan.
- Current substep:
  - Add the native prerequisites needed by indexed raster G-buffer rendering without changing the Java/JNI ABI:
    - depth-image per-layer views must use depth aspect rather than color aspect.
    - packed chunk/entity index buffers must be created with `VK_BUFFER_USAGE_INDEX_BUFFER_BIT` so G-buffer draw calls can bind them directly.
- Implemented prerequisites:
  - `DeviceLocalImage::createPerLayerViews()` now uses the same usage-derived aspect mask as normal image views, so depth images can safely create per-layer 2D views.
  - Chunk packed index buffers now include `VK_BUFFER_USAGE_INDEX_BUFFER_BIT` for both single-build and batched-build paths.
  - Entity packed index buffers now include `VK_BUFFER_USAGE_INDEX_BUFFER_BIT`.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after the prerequisite changes.
- G-buffer data-path decision:
  - The fixed raster pass will extend the existing Deferred RT descriptor table instead of creating a second pipeline layout. Set 0 remains the compute clear storage-image set.
  - Set 1 will be the bound texture array that mirrors the existing world texture input contract.
  - Set 2 will be a per-frame storage buffer containing provider-derived view/draw records. This avoids Java/JNI changes and avoids oversized push constants.
  - The first fixed pass will use provider view matrices and draw packet material data directly; texture mapping and deeper PBR map interpretation remain a later lighting/material refinement because this milestone only needs primary albedo, normal, depth, motion and emission exports.
- Layer-plan correction:
  - Updated the Step 11 `GBufferLayerPlan` multiview framebuffer rule before creating real framebuffers: framebuffer count stays 1, but framebuffer layer count is 1 for Vulkan multiview render passes. Array-layer selection comes from the subpass view mask, not from framebuffer layers.
  - Updated the CPU G-buffer test expectation accordingly.
- Frame-data layout correction:
  - Split the planned set 2 frame-data binding into two storage buffers:
    - binding 0: `DeferredRtGBufferViewData[]`
    - binding 1: `DeferredRtGBufferDrawData[]`
  - Reason: GLSL cannot naturally expose two unsized runtime arrays from one SSBO. Keeping view and draw records in separate bindings makes the C++/GLSL contract direct and avoids offset-packing shortcuts.
  - No Java/JNI change is required; both buffers are built from the existing native `SceneProvider` view and draw packets.
- Vulkan command path:
  - Added per-frame upload of provider-derived G-buffer view records and draw records.
  - Added storage-buffer descriptor binding for those records in Deferred RT set 2.
  - Added clear-to-G-buffer barriers: public G-buffer attachments transition from compute `GENERAL` clear into `COLOR_ATTACHMENT_OPTIMAL`; clear-only outputs stay in `GENERAL`.
  - Added internal depth transition and fixed G-buffer render pass execution for multiview and per-layer fallback.
  - Added indexed draw recording from `SceneDrawPacket` streams only. Opaque and cutout streams are recorded; cutout enables shader alpha test.
- Shader path:
  - Added fixed `gbuffer.vert` and `gbuffer.frag`.
  - The shader reads provider SSBOs plus existing packed position/material vertex buffers through BDA.
  - The shader writes primary albedo/metallic, specular albedo, normal/roughness, motion vector, linear depth, primary depth and base emission.
  - Motion vectors use the legacy pixel-space convention: previous pixel minus current pixel.
  - Normal/specular texture mapping and full PBR material interpretation remain deferred material-refinement work; the first pass consumes the existing base texture, vertex color, alpha test and provider material flags.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after adding the G-buffer pass and shaders.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 3/3 tests passed.
- Current follow-up before milestone commit:
  - Improve provider `previousObjectToWorld` so static geometry motion vectors include camera movement without requiring a Java/JNI input extension.
- Provider motion-vector refinement:
  - `McvrSceneProvider` now derives `previousObjectToWorld` from the last world UBO camera position for chunks and WORLD-space entities.
  - CAMERA and CAMERA_SHIFT entity transforms now use the last world UBO when it is available.
  - This keeps motion-vector inputs native-side and avoids a Java/JNI contract extension for the first G-buffer milestone.
- Texture descriptor safety:
  - Added a native `DeferredRtModule` fallback texture path: a 1x1 white `R8G8B8A8_UNORM` image plus nearest-repeat sampler.
  - The fallback image is uploaded during module build and transitioned to `SHADER_READ_ONLY_OPTIMAL`.
  - Every frame descriptor table now initializes all 4096 texture-array slots to the fallback image before real `Textures::initializeTexture()` / sampler updates overwrite loaded slots.
  - This avoids undefined sampling from unbound combined-image-sampler descriptors while preserving the current Java/JNI texture input contract.
- Multiview fallback correction:
  - Split the G-buffer vertex shader into a per-layer wrapper (`gbuffer.vert`) and a multiview wrapper (`gbuffer_multiview.vert`) over a shared `gbuffer_vertex_common.glsl`.
  - The per-layer shader uses the push-constant `viewIndex`; the multiview shader uses `gl_ViewIndex` and is only attached to the multiview pipeline.
  - Removed the unused multiview extension requirement from the fragment shader.
  - Added `src/shader` to the shader include path and tracked deferred RT `.glsl` includes as shader dependencies.
  - This keeps the non-multiview fallback path buildable and avoids making all G-buffer raster pipelines require multiview support.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after the fallback descriptor initialization change.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core -- /m:1 /p:CL_MPCount=1 /v:minimal` initially failed after the shader split due to missing include-extension and shader-root include path declarations; both issues were fixed, then the same command completed successfully.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 3/3 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed `gbuffer_vert.spv`, `gbuffer_multiview_vert.spv` and `gbuffer_frag.spv` to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.

### Step 13: Fixed Compose Bring-up

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/compose.comp`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Add a deterministic compute compose pass after the fixed G-buffer pass.
  - Read primary G-buffer outputs and write a valid HDR `primary_radiance` export for ToneMapping/PostRender bring-up.
  - Fill `primary_direct_diffuse` with the same conservative direct lighting signal used by radiance.
  - Preserve split indirect/specular/reflection/fog/refraction/GI outputs as explicit clear-only placeholders until the ray-query lighting stages are implemented.
- Design constraint:
  - This step is not the final RT lighting path. It is the first stable composed output stage so downstream modules no longer receive only black radiance from Deferred RT.
  - RT shadow, reflection and GI classification remain separate later stages; do not encode them as hidden approximations here.
- Implemented:
  - Added a module-owned `compose_comp.spv` compute shader and `composePipeline_` using the existing Deferred RT descriptor table/pipeline layout.
  - Added a post-G-buffer image barrier from raster color-attachment writes and clear-only compute writes into `VK_IMAGE_LAYOUT_GENERAL` for compute read/write.
  - Added `renderComposePass()` after the fixed G-buffer pass.
  - The compose shader reads:
    - `primary_albedo_metallic`,
    - `primary_normal_roughness`,
    - `primary_linear_depth`,
    - `primary_depth`,
    - `primary_emission`.
  - The compose shader writes:
    - `primary_radiance -> radiance`,
    - `primary_direct_diffuse -> first_hit_diffuse_direct_light`.
  - Invalid/sky pixels remain at the deterministic values produced by `clear_contract.comp`.
  - The direct diffuse signal is intentionally conservative and deterministic: fixed ambient plus a fixed directional term from G-buffer normal/albedo. It is a bring-up signal only, not final world lighting.
- Preserved placeholders:
  - `primary_indirect_diffuse`, `primary_specular`, `primary_clear`, `atmosphere_fog`, `primary_refraction`, `reflection_hit_distance` and `gi_hit_distance` remain clear-only until ray-query lighting/classification stages are implemented.
  - No Java/JNI input contract change was needed for this step.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and generated `shaders/world/deferred_rt/compose_comp.spv`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 3/3 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed `compose_comp.spv` to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.
- Remaining work:
  - Add pixel classification before ray-query passes.
  - Add direct-light visibility/shadow ray queries against the shared AS.
  - Add reflection/specular ray queries and valid `reflection_hit_distance`.
  - Add diffuse GI ray queries and valid `gi_hit_distance`.
  - Decide final compose behavior for NRD-enabled presets once denoiser-valid split lighting is produced.

### Step 14: Pixel Classification Foundation

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_classification.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_classification.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/classify.comp`
  - `MCVR-custom/tests/deferred_rt_classification_test.cpp`
  - `MCVR-custom/tests/CMakeLists.txt`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Add an internal `R32_UINT image2DArray` classification mask resource.
  - Generate one mask value per active pixel/layer after the G-buffer and before lighting/compose.
  - Define explicit eligibility bits for valid primary surface, sky/background, direct-light visibility, diffuse GI, specular/reflection and transparent/refraction.
  - Keep the classification mask native-private so the public output contract remains stable.
  - Keep Java/JNI unchanged; all inputs come from existing G-buffer outputs and native scene state.
- Design notes:
  - This step creates the stable input contract for later direct-light, reflection and GI ray-query passes.
  - Debug counters/compacted queues are not implemented in this substep because there is no GPU readback/instrumentation path yet. They remain required before marking Phase 5.1 fully complete.
- Implemented:
  - Added `deferred_rt_classification.hpp/.cpp` with the native-private classification mask contract:
    - format: `VK_FORMAT_R32_UINT`,
    - descriptor set: 3,
    - binding: 0,
    - bits for valid primary, sky/background, direct-light eligibility, diffuse GI eligibility, specular/reflection eligibility and transparent/refraction eligibility.
  - Added CPU-side helper logic and tests for invalid pixels, direct/GI eligibility, reflection roughness/specular thresholds and refraction eligibility.
  - Added a module-owned `classificationMasks_` image per frame, allocated as layered `R32_UINT` storage image.
  - Added descriptor set 3 to the existing Deferred RT pipeline layout and bound the classification mask for every frame.
  - Added `classify_comp.spv` and `classifyPipeline_`.
  - Added `renderClassificationPass()` after fixed G-buffer and before compose.
  - The shader reads primary G-buffer depth/material outputs and writes one mask value per pixel/layer.
  - The first material implementation marks valid opaque/cutout primary surfaces as direct-light and diffuse-GI eligible. Reflection is skipped for high-roughness pixels based on the documented threshold.
- Current limitations:
  - Runtime debug counters and compacted queues are still missing; classification is present as a full-resolution mask only.
  - Refraction eligibility is defined in the CPU contract, but the current fixed G-buffer does not yet rasterize translucent/refraction primaries, so the GPU mask does not set the refraction bit yet.
  - The current fixed G-buffer writes roughness as 1.0 and metallic as 0.0, so ordinary surfaces will not enter reflection until material refinement writes real roughness/specular data.
  - There is still no GPU readback fixture for validating mask contents in captured frames.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after adding the CPU classification contract test.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after adding the internal classification image and compute pass.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed `classify_comp.spv` to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.
- Remaining work:
  - Add GPU classification counters or a readback/debug path.
  - Add direct-light ray-query pass that consumes the classification mask and shared AS.
  - Feed reflection eligibility with real material roughness/specular data.
  - Add transparent/refraction primary handling before enabling the GPU refraction bit.

### Step 15: Direct-Light Ray-Query Foundation

- Status: in progress.
- Target files:
  - `MCVR-custom/src/core/vulkan/device.hpp`
  - `MCVR-custom/src/core/vulkan/device.cpp`
  - `MCVR-custom/src/core/vulkan/physical_device.hpp`
  - `MCVR-custom/src/core/vulkan/physical_device.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/direct_light_ray_query.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/direct_light_fallback.comp`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Add device-side `VK_KHR_ray_query` capability probing and conditional enablement.
  - Add a deterministic direct-light compute pass after classification and before final compose.
  - Use the shared TLAS/AS input when ray query is available; otherwise run an explicit non-ray fallback pass.
  - Consume the classification mask and primary G-buffer outputs, then write `primary_direct_diffuse`.
  - Keep Java/JNI unchanged for this milestone; all required inputs must come from the existing native scene provider, G-buffer, classification mask and world/render context.
- Design constraints:
  - Do not write a shader that assumes ray query support before Vulkan feature/extension plumbing exists.
  - Keep the fallback path explicit and documented, so unsupported devices still get deterministic direct-light output without pretending RT visibility was evaluated.
  - Compose should consume the direct-light output rather than duplicating hidden direct-light computation once this pass exists.
- Current input-contract finding:
  - `SceneProvider::accelerationData()` already exposes the per-frame TLAS plus AS metadata buffers from `ScenePrepareContext`.
  - Step 15 can use the native scene provider boundary; no Java/JNI input change is required for direct-light bring-up.
- Substep 15.1:
  - Add optional `VK_KHR_ray_query` extension selection and `VkPhysicalDeviceRayQueryFeaturesKHR` enablement.
  - Expose `vk::Device::hasRayQuery()` for module runtime selection between real ray-query visibility and fallback direct light.
- Substep 15.1 implemented:
  - `vk::Device` now includes `VK_KHR_ray_query` in the filtered extension candidate list.
  - `VkPhysicalDeviceRayQueryFeaturesKHR` is queried and enabled only when the extension, feature and acceleration-structure feature are available.
  - `vk::Device::hasRayQuery()` reports the enabled runtime capability for Deferred RT.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after a first timeout-only retry.
- Substep 15.2:
  - Add Deferred RT direct-light descriptor set/pipelines.
  - Bind the per-frame TLAS when available.
  - Dispatch either the ray-query shader or the explicit fallback shader between classification and compose.
- Substep 15.2 implementation notes:
  - Added module-private `DeferredRtDirectLightData` with light direction/radiance, ambient terms and capability flags.
  - The direct-light pass derives current-frame sky direction from native `SkyUBO` when available and falls back to a deterministic sun-like direction otherwise.
  - Added descriptor set 4:
    - binding 0: TLAS for ray-query direct lighting,
    - binding 1: `DeferredRtDirectLightData` UBO.
  - Expanded `DeferredRtGBufferViewData` with inverse view/projection matrices so compute passes can reconstruct primary positions from the fixed G-buffer depth.
  - Added explicit pass order target: `clear -> fixed G-buffer -> classification -> direct_light -> compose`.
  - Compose now consumes `primary_direct_diffuse` instead of writing a duplicated fixed direct-light signal.
- Current Step 15 limitations:
  - The first ray-query shader is a hard-shadow visibility foundation. It does not yet evaluate transparent/alpha-test any-hit transmission.
  - Direct light only covers the single sky-derived directional light. Area/restir light reservoirs, moon selection and volumetric cloud shadows remain later work.
  - The fallback shader is intentionally unshadowed and is only used when ray query or a TLAS is unavailable.
- Verification progress:
  - First `shaders core` build re-ran CMake because shader globs changed, generated the new shader targets, then failed in C++ due to unqualified `DeferredRtDirectLightData` declarations in the module header.
  - The type qualification was fixed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after that fix.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and generated `direct_light_fallback_comp.spv` plus `direct_light_ray_query_comp.spv`.
  - After making the direct-light UBO shader layout explicitly `std140`, the same `shaders core mcvr_tests` build completed successfully again.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed `direct_light_fallback_comp.spv` plus `direct_light_ray_query_comp.spv` to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.
  - A pre-commit self-review found that sky-derived direct-light intensity was being applied twice; CPU-side direct-light data was corrected so `lightRadiance` stores color while `lightDirectionIntensity.w` stores intensity.
  - After that correction, `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully again: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully again after the final CPU-side intensity correction.

### Step 16: G-Buffer Material Refinement

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/gbuffer.frag`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Bind the existing native `vk::Data::TextureMapping` buffer into the Deferred RT G-buffer path.
  - Decode LabPBR specular/normal auxiliary textures when Java/resource-pack texture mapping provides them.
  - Replace placeholder G-buffer roughness, metallic and specular-albedo output with material-derived values.
  - Keep the base albedo, cutout alpha test and existing direct-light/compose pass order unchanged.
- Input-contract conclusion:
  - No Java/JNI change is required for this step.
  - The required inputs already exist in native form:
    - sampled texture array through Deferred RT descriptor set 1,
    - `Renderer::buffers()->textureMappingBuffer()` through the existing buffer upload path,
    - `MaterialVertex.textureID`, `MaterialVertex.textureUV`, `MaterialVertex.packedData`, and draw material fallback texture ID through the current scene provider/G-buffer draw stream.
- Descriptor plan:
  - Extend Deferred RT descriptor set 2 with binding 2 as a read-only storage buffer for `TextureMapping`.
  - Keep set 1 dedicated to the combined-image-sampler texture array.
  - Bind the buffer in `bindGBufferResources()` so the per-frame upload cadence follows the existing buffer subsystem.
- Known limitations to preserve explicitly:
  - The first material refinement can use LabPBR normal maps for shading normal only when a stable screen-space derivative tangent basis is valid in the fragment shader.
  - Height-map/parallax tracing is not part of this step; it remains owned by the later ray/reflection path.
  - Transparent/refraction primaries are still not enabled just by material decoding.
- Substep 16.1 implemented:
  - Added a module-private fallback `TextureMapping` storage buffer with all entries initialized to `-1`.
  - Extended Deferred RT descriptor set 2 with binding 2 for the read-only texture mapping storage buffer.
  - `bindGBufferResources()` now binds the real `Renderer::buffers()->textureMappingBuffer()` when Java/native upload has provided one, otherwise binds the fallback buffer so the G-buffer descriptor is never left unbound.
  - `gbuffer.frag` now samples mapped LabPBR specular and normal textures, converts them through `convertLabPBRMaterial()`, writes material roughness/metallic/F0, and applies a derivative-built tangent frame for normal-map shading normals.
  - Base emission now uses LabPBR specular alpha when available while preserving the existing native emissive material flag behavior.
  - A self-review corrected `primary_albedo_metallic.rgb` back to the contract-compatible base albedo output instead of zeroing metallic diffuse. Metallic behavior is now represented through the metallic channel plus F0/specular albedo, matching the existing PT material-guide convention.
- Verification progress:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and regenerated `gbuffer_frag.spv`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after the material-output correction.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed the regenerated `gbuffer_frag.spv` to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.
- Remaining work:
  - Add a runtime GPU readback/debug fixture for material/classification masks.
  - Validate tangent-frame normal-map orientation against representative LabPBR resource packs in a captured frame.
  - Reflection and GI passes still need to consume the now-populated roughness/F0/metallic data.

### Step 17: Specular Reflection Ray-Query Foundation

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_reflection.hpp`
  - `MCVR-custom/src/shader/world/deferred_rt/reflection_common.glsl`
  - `MCVR-custom/src/shader/world/deferred_rt/reflection_fallback.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/reflection_ray_query.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/compose.comp`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Add a compute reflection pass after direct light and before compose.
  - Consume `primary_normal_roughness`, `primary_specular_albedo`, `primary_albedo_metallic`, `primary_depth`, the classification mask and the shared TLAS.
  - Use ray query when runtime support and TLAS are available, otherwise use an explicit deterministic environment fallback.
  - Write `primary_specular` and `reflection_hit_distance` so downstream denoisers/upscalers have real specular guide outputs instead of permanent clear values.
  - Keep Java/JNI unchanged; the pass only uses existing native G-buffer, classification, AS and sky/view data.
- Initial scope and limitations:
  - This step is a foundation for visibility and material-driven eligibility. It does not yet shade the reflected hit material, evaluate recursive bounces, SSR, rough reflection sampling, or transparent reflection.
  - Ray-query hit radiance starts as a conservative visibility-aware environment/proxy term; exact secondary-hit shading will need scene material lookup or a compact hit-shading path in a later step.
  - Reflection work is still full-resolution with early-out from the classification mask; tile queues/counters remain later work.
- Substep 17.1 implemented:
  - Added `DeferredRtReflectionData` and reflection descriptor set 5:
    - binding 0: TLAS,
    - binding 1: module-private reflection UBO.
  - Added reflection fallback and ray-query compute pipelines.
  - Added pass order target: `clear -> fixed G-buffer -> classification -> direct_light -> reflection -> compose`.
  - Compose now consumes `primary_specular` in addition to direct diffuse, ambient diffuse and emission.
  - The reflection pass consumes the classification mask, so only Step 16 material-refined low-roughness/specular/metallic pixels run reflection work.
  - The fallback path writes deterministic environment specular and zero hit distance. The ray-query path writes the committed reflection hit distance and applies a conservative visibility-aware scale to the environment/proxy specular term.
  - The shared TLAS memory barrier now covers both direct-light and reflection ray-query pipelines instead of depending on direct light alone.
- Verification progress:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after CMake regenerated shader targets for `reflection_fallback.comp` and `reflection_ray_query.comp`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and generated `reflection_fallback_comp.spv` plus `reflection_ray_query_comp.spv`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed `reflection_fallback_comp.spv` plus `reflection_ray_query_comp.spv` to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.
  - After tightening the TLAS memory-barrier condition, `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully again: 4/4 tests passed.
- Remaining work:
  - Replace the proxy/environment reflected-hit radiance with real secondary-hit material shading.
  - Add rough-reflection sampling and denoiser-friendly stochastic controls.
  - Add transparent/alpha-aware reflection visibility and any-hit transmission.
  - Add tile/queue scheduling so reflection is not full-resolution for all eligible pixels.

### Step 18: Diffuse GI Ray-Query Foundation

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_gi.hpp`
  - `MCVR-custom/src/shader/world/deferred_rt/gi_common.glsl`
  - `MCVR-custom/src/shader/world/deferred_rt/gi_fallback.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/gi_ray_query.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/compose.comp`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Add a compute diffuse-GI pass after reflection and before compose.
  - Consume the G-buffer, classification mask, view data, sky-derived environment parameters and shared TLAS.
  - Write `primary_indirect_diffuse` and `gi_hit_distance` so the remaining public output contract is no longer permanently clear for diffuse indirect lighting.
  - Keep Java/JNI unchanged; all inputs already exist in native buffers/images/AS.
- Initial scope and limitations:
  - The first pass is a deterministic foundation, not a final GI solution.
  - Ray-query mode records first-hit distance along a normal-oriented diffuse probe ray and modulates a simple environment/proxy indirect term. It does not yet shade secondary materials, sample multiple directions, use reservoirs, denoise, or accumulate history.
  - Fallback mode writes sky/ground ambient diffuse with zero GI hit distance.
  - Full-resolution early-out remains acceptable for bring-up; tile/queue scheduling remains later work.
- Substep 18.1 implemented:
  - Added `DeferredRtGiData` and GI descriptor set 6:
    - binding 0: TLAS,
    - binding 1: module-private GI UBO.
  - Added GI fallback and ray-query compute pipelines.
  - Added pass order target: `clear -> fixed G-buffer -> classification -> direct_light -> reflection -> gi -> compose`.
  - Compose now consumes `primary_indirect_diffuse` in addition to direct diffuse, specular, ambient diffuse and emission.
  - The GI pass consumes the classification mask and writes only pixels marked `PIXEL_CLASSIFICATION_DIFFUSE_GI_ELIGIBLE`.
  - The fallback path writes deterministic sky/ground diffuse indirect and zero hit distance. The ray-query path writes committed probe hit distance and modulates the proxy diffuse indirect term.
- Verification progress:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully after CMake regenerated shader targets for `gi_fallback.comp` and `gi_ray_query.comp`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and generated `gi_fallback_comp.spv` plus `gi_ray_query_comp.spv`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed `gi_fallback_comp.spv` plus `gi_ray_query_comp.spv` to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.
  - Pre-commit self-review checked GI descriptor set 6, pass order, output barriers before compose, TLAS read barriers, and shader-side GI hit-distance semantics.
- Remaining work:
  - Replace proxy sky/ground GI radiance with real secondary-hit material shading.
  - Add multi-sample, temporal, denoiser-friendly stochastic GI controls instead of a single deterministic probe ray.
  - Add transparent/alpha-aware GI visibility and any-hit transmission.
  - Add tile/queue scheduling so diffuse GI is not full-resolution for every eligible pixel.
  - Validate `gi_hit_distance` against NRD in an actual denoiser preset after the Java preset wiring selects the explicit `gi_hit_distance` output.

### Step 19: Classification GPU Statistics Foundation

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_classification.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_classification.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/classify.comp`
  - `MCVR-custom/tests/deferred_rt_classification_test.cpp`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Add a module-owned GPU storage buffer for classification counters.
  - Count total classified pixels and per-path masks for valid primary, sky/background, direct light, diffuse GI, reflection and transparent/refraction eligibility.
  - Clear counters at the start of the classification pass and update them from `classify.comp` with atomic adds.
  - Keep Java/JNI unchanged; this is native debug/instrumentation data for later readback, debug UI and offline runner work.
- Initial scope and limitations:
  - This step creates and writes the GPU statistics source of truth but does not expose a Java UI or blocking CPU readback path yet.
  - Counter reads for gameplay/debug overlay should be added after a safe frame-latency readback policy is chosen.
  - The statistics are classification-path counters, not final dispatched-ray counters; later tile/queue scheduling should add pass-specific ray counters.
- Substep 19.1 implemented:
  - Added `DeferredRtClassificationStats` with eight packed `uint32_t` counters:
    - total classified pixels,
    - valid primary,
    - sky/background,
    - direct-light eligible,
    - diffuse-GI eligible,
    - specular/reflection eligible,
    - transparent/refraction eligible,
    - reserved.
  - Added CPU helper functions to reset and accumulate the same counter layout used by the shader.
  - Added one module-private device-local classification stats buffer per swapchain frame.
  - Extended classification descriptor set 3 with binding 1 as a storage buffer for statistics.
  - `render3D()` now clears the stats buffer with `vkCmdFillBuffer` before classification and synchronizes transfer writes before shader atomic writes.
  - `classify.comp` now performs atomic counter increments when it writes each pixel's classification mask.
  - Extended `deferred_rt_classification_test` to cover descriptor binding, counter layout, reset behavior and accumulation behavior.
- Verification progress:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and regenerated `classify_comp.spv`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed the updated `classify_comp.spv` to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.
- Remaining work:
  - Add a safe frame-latency CPU readback path for the classification stats buffer.
  - Surface the counters through a debug overlay/logging path without requiring Java/JNI changes unless a Java UI is explicitly needed.
  - Add pass-specific counters for actual direct-light, reflection and GI ray-query work after tile/queue scheduling exists.

### Step 20: Classification Statistics Readback Snapshot

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Copy the classification stats GPU buffer to its persistent staging buffer after the classification pass.
  - Read the completed staging data when the same swapchain frame slot is acquired again after its fence has completed.
  - Expose the latest native `DeferredRtClassificationStats` snapshot through a module API for future debug overlay/offline-runner use.
  - Keep Java/JNI unchanged.
- Initial scope and limitations:
  - This step exposes a native CPU snapshot only; it does not add UI text, Java bindings or an overlay.
  - The snapshot is frame-latency based and may represent the latest completed frame slot rather than the frame currently being recorded.
  - Pass-specific ray counters remain separate future work.
- Substep 20.1 implemented:
  - Added `DeferredRtModule::latestClassificationStats()` as a native accessor for the most recent completed stats snapshot.
  - Added `captureCompletedClassificationStats(frameIndex)` to read a frame slot's persistent staging buffer after the framework has waited for that slot's fence.
  - `render3D()` now captures the completed snapshot before reusing the frame slot.
  - After `renderClassificationPass()`, the command stream synchronizes classification shader writes to transfer read and copies the stats device buffer into its staging buffer.
  - The copy is non-blocking for the current frame; CPU reads happen only when that frame slot is acquired again after completion.
- Verification progress:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed the updated `core.dll` to both `MCVR-custom/bin` and `Radiance-custom/src/main/resources`.
- Remaining work:
  - Add a native debug overlay/log line or offline-runner report that consumes `latestClassificationStats()`.
  - Add pass-specific ray counters once direct-light/reflection/GI scheduling becomes queue/tile based.

### Step 21: Compose Uses Deferred Lighting Only

- Status: completed.
- Target files:
  - `MCVR-custom/src/shader/world/deferred_rt/compose.comp`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Align the compose pass with the Phase 5.5 contract: `primary_radiance` is composed from direct diffuse, indirect diffuse/GI, specular/reflection and emission outputs produced by the deferred lighting passes.
  - Remove the fixed shader-local ambient term now that `primary_indirect_diffuse` is populated by the GI pass.
  - Keep Java/JNI and the public output contract unchanged.
- Substep 21.1 implemented:
  - Removed the hard-coded normal-weighted ambient contribution from `compose.comp`.
  - `radiance` is now `directDiffuse + indirectDiffuse + specular + emission`.
  - Kept the shared descriptor table contract unchanged; the compose shader now simply does not depend on albedo/normal for ambient reconstruction.
- Verification progress:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and regenerated `compose_comp.spv`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed the updated `compose_comp.spv` to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.
- Remaining work:
  - Add fog, clear coat and refraction contributions to compose once those passes write non-placeholder outputs.

### Step 22: Compose Clear/Fog/Refraction Contract Inputs

- Status: completed.
- Target files:
  - `MCVR-custom/src/shader/world/deferred_rt/compose.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/clear_contract.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/gbuffer.frag`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Complete the non-denoiser `primary_radiance` compose contract for the public split outputs that already exist: direct diffuse, indirect diffuse/GI, specular/reflection, emission, fog, clear and refraction.
  - Keep zero-placeholder clear/refraction outputs harmless until their real passes exist.
  - Preserve compatibility with existing NRD compose semantics for fog disabling and emissive alpha.
- Substep 22.1 implemented:
  - Added `first_hit_clear`, `fog_image` and `first_hit_refraction` reads to `compose.comp`.
  - Clear contribution now overrides the normal direct/indirect/specular path when `first_hit_clear.a > 0`.
  - Refraction contribution is added only when `first_hit_refraction.a > 0.5`.
  - Fog contribution uses the existing compose sentinel: `fog_image.a < 0` disables fog; otherwise alpha is treated as transmittance and RGB as additive fog.
  - `clear_contract.comp` now clears `fog_image` to `(0, 0, 0, -1)` instead of `(0, 0, 0, 0)` so zero-placeholder fog does not black out compose paths that follow the sentinel rule.
  - `gbuffer.frag` now writes `primary_emission.a = 1.0` for valid G-buffer surfaces, matching the existing NRD compose path that multiplies emission RGB by alpha.
- Verification progress:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and regenerated `clear_contract_comp.spv`, `compose_comp.spv` and `gbuffer_frag.spv`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 4/4 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed the updated Deferred RT shader SPIR-V files to both `MCVR-custom/bin/res/world/deferred_rt` and `Radiance-custom/src/main/resources/shaders/world/deferred_rt`.
- Remaining work:
  - Implement real fog, clear/clearcoat and transparent/refraction producers.
  - Validate NRD quality after Deferred RT presets connect `primary_emission`, `atmosphere_fog`, `primary_clear` and `primary_refraction` through the Java graph.

### Step 23: Lighting Pass GPU Statistics

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_pass_stats.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_pass_stats.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/pass_stats_common.glsl`
  - `MCVR-custom/src/shader/world/deferred_rt/direct_light_common.glsl`
  - `MCVR-custom/src/shader/world/deferred_rt/direct_light_fallback.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/direct_light_ray_query.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/reflection_common.glsl`
  - `MCVR-custom/src/shader/world/deferred_rt/reflection_fallback.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/reflection_ray_query.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/gi_common.glsl`
  - `MCVR-custom/src/shader/world/deferred_rt/gi_fallback.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/gi_ray_query.comp`
  - `MCVR-custom/tests/deferred_rt_pass_stats_test.cpp`
  - `MCVR-custom/tests/CMakeLists.txt`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Intended behavior:
  - Add a native-only GPU statistics buffer for actual direct-light, reflection and GI pass work.
  - Count in-bounds pass pixels, eligible pixels, ray queries, ray hits/misses or occlusions, lit direct-light pixels and fallback-produced reflection/GI pixels.
  - Reuse the existing frame-latency staging readback policy used by classification stats.
  - Keep Java/JNI unchanged.
- Initial scope and limitations:
  - This step exposes native CPU snapshots only; no Java debug overlay or UI is added.
  - These are pass-level counters, not compacted/tiled queue counters. Later tile/queue scheduling can either refine these counters or add queue-specific counters.
- Implemented:
  - Added `DeferredRtPassStats` with eighteen packed `uint32_t` counters:
    - direct-light in-bounds, eligible, ray queries, occluded and lit pixels,
    - reflection in-bounds, eligible, ray queries, ray hits, ray misses and fallback pixels,
    - GI in-bounds, eligible, ray queries, ray hits, ray misses and fallback pixels,
    - one reserved counter for ABI-compatible extension.
  - Added CPU helpers and `deferred_rt_pass_stats_test` to lock the packed layout, reset behavior and accumulation semantics.
  - Added one module-owned per-frame pass-stats `DeviceLocalBuffer` with persistent staging, following the classification-stats frame-latency readback pattern.
  - Added `DeferredRtModule::latestPassStats()` as a native CPU snapshot accessor. No Java/JNI ABI change was made.
  - Bound the same pass-stats SSBO into the lighting descriptor sets:
    - set 4 binding 2 for direct light,
    - set 5 binding 2 for reflection,
    - set 6 binding 2 for GI.
  - Added `pass_stats_common.glsl` and atomic counter writes in direct-light, reflection and GI fallback/ray-query shaders.
  - Ray-query counters only increment when the shader has a TLAS flag and actually enters the trace path; fallback counters only increment for eligible fallback-produced reflection/GI pixels.
  - Pass stats are cleared after classification and before lighting, made visible to compute shaders, then copied to staging after the GI pass for next-frame CPU readback.
- Verification:
  - First build attempt failed before compiling project changes because `glslangValidator.exe` was not on the PowerShell `PATH`.
  - Retried with `D:\VulkanSDK\1.4.335.0\Bin` prepended to `PATH`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 5/5 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed updated Deferred RT lighting shaders plus `core.dll` to the native bin and Java resource outputs.
- Remaining work:
  - Add a debug overlay, native log export or offline-runner report that consumes `latestPassStats()`.
  - Replace or gate per-pixel atomic instrumentation if it shows measurable runtime cost in normal gameplay; queue/tile scheduling should eventually provide lower-overhead counters.
  - Add an in-game smoke test or RenderDoc capture check once Deferred RT preset wiring can exercise the full path without manual graph setup.

### Step 24: Java/Native Raster Metadata Extension Design

- Status: completed.
- Target files:
  - `Radiance-custom/src/main/java/com/radiance/client/constant/Constants.java`
  - `Radiance-custom/src/main/java/com/radiance/client/proxy/world/ChunkProxy.java`
  - `Radiance-custom/src/main/java/com/radiance/client/proxy/world/EntityProxy.java`
  - `MCVR-custom/src/core/middleware/com_radiance_client_proxy_world_ChunkProxy.cpp`
  - `MCVR-custom/src/core/middleware/com_radiance_client_proxy_world_EntityProxy.cpp`
  - `MCVR-custom/src/core/render/chunks.hpp`
  - `MCVR-custom/src/core/render/chunks.cpp`
  - `MCVR-custom/src/core/render/entities.hpp`
  - `MCVR-custom/src/core/render/entities.cpp`
  - `MCVR-custom/src/core/render/scene_provider/scene_provider.hpp`
  - `MCVR-custom/src/core/render/scene_provider/scene_provider.cpp`
  - `MCVR-custom/src/core/render/scene_provider/mcvr_scene_classification.hpp`
  - `MCVR-custom/src/core/render/scene_provider/mcvr_scene_classification.cpp`
  - `MCVR-custom/src/core/render/scene_provider/mcvr_scene_provider.cpp`
  - `MCVR-custom/tests/deferred_rt_scene_provider_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
- Problem statement:
  - The current Java-to-native scene payload has PT/hybrid-renderer history. It carries PBR vertices, texture ids, LabPBR mappings, geometry types and enough range metadata for the first opaque/cutout G-buffer path.
  - It is not a complete raster draw-state stream. `WORLD_TRANSPARENT` currently collapses cutout, true translucent and several special render-layer cases, and the fixed Deferred RT provider maps that value to cutout only as a bring-up fallback.
  - Minecraft/Blaze3D reference sources under `G:\cpp\radiance\mcsrc` show that the raster path needs more than transparency: `RenderPipeline` carries blend, depth test/write, cull, write masks, depth bias, vertex mode, stencil and pipeline sort key; `RenderSetup` adds output target, lightmap/overlay, outline/crumbling, layering and `sortOnUpload`.
  - The Java pipeline already supports dynamic native pipeline rebuilds/switches. Future presets may switch between PT and Deferred RT, so duplicated AS/scene preparation would be a performance and ownership problem.
  - Full deferred raster support needs neutral render-state and material-semantic metadata behind `SceneProvider`, not Minecraft/Blaze3D/PT concepts exposed to shaderpacks.
- Intended behavior:
  - Define a versioned Java/native metadata extension for raster classification.
  - Preserve compatibility with old payloads by keeping the current opaque/cutout fallback path.
  - Extend `SceneDrawPacket` or `SceneMaterialBinding` with neutral render-state fields:
    - alpha mode,
    - blend mode,
    - depth test/write policy,
    - cull mode,
    - polygon offset,
    - depth bias,
    - color/depth write masks,
    - output target class,
    - layering class,
    - lightmap/overlay requirements,
    - outline/crumbling flags,
    - sort-on-upload flag,
    - pipeline sort key or ordered stream index,
    - optional stencil policy.
  - Add neutral material semantic flags for water, glass, refraction/transmission, portal, weather, text, particle and overlay-like content.
  - Keep the metadata per draw/geometry rather than per vertex so chunk meshes do not grow unnecessarily.
  - Define a pipeline-level shared scene runtime plan so PT and Deferred RT consume the same `ScenePrepare`/TLAS metadata when both paths are present or when users dynamically switch presets.
  - Keep PT source-compatible; PT modules can ignore the extended raster metadata.
- Initial scope and limitations:
  - This step is a design/ABI step. It should not implement full transparent rendering, refraction or forward transparent compose.
  - Shaderpack syntax remains neutral. Do not expose Java `RenderLayer`, Blaze3D classes, PT SBT concepts or raw Vulkan state in deferred shaderpacks.
  - If metadata is missing, the provider must keep routing content through conservative defaults and emit diagnostics/counters for fallback classification.
- Implemented:
  - Added native neutral raster metadata ABI:
    - `SceneRasterMetadataCurrentVersion = 1`,
    - `SceneRasterMetadataIntStride = 14`,
    - alpha mode,
    - blend mode,
    - depth policy,
    - cull mode,
    - render-state flags,
    - write mask,
    - output target class,
    - layering class,
    - material semantic flags,
    - pipeline sort key,
    - polygon offset factor/units,
    - stencil flags,
    - one reserved word in the Java record.
  - Extended `SceneMaterialBinding` with parsed `SceneRasterMetadata`.
  - Added native helpers to parse fixed-stride Java `int` records and classify metadata as opaque, cutout, translucent, additive or overlay.
  - Added overloaded MCVR classification helpers that prefer metadata when present and preserve the old `McvrGeometryType` fallback when missing.
  - Added provider stats for:
    - metadata packets,
    - metadata translucent packets,
    - legacy fallback packets,
    - legacy `WORLD_TRANSPARENT -> Cutout` fallback packets.
  - Extended `ChunkBuildTask`, `ChunkBuildData`, `ChunkRenderData`, `Chunk1`, `EntitiesBuildTask`, `EntityBuildData` and `Entity` to carry per-geometry metadata without touching vertex layout, material buffers or acceleration-structure buffers.
  - Added new JNI realtime upload entry points:
    - `ChunkProxy.rebuildSingleWithRasterMetadata(...)`,
    - `EntityProxy.queueBuildWithRasterMetadata(...)`.
  - Kept old native upload entry points for replay and old payload fallback:
    - `ChunkProxy.rebuildSingle(...)`,
    - `EntityProxy.queueBuild(...)`.
  - Added `Constants.RasterMetadata` on the Java side as the single record writer and enum mirror for native metadata values.
  - Realtime chunk/entity uploads now allocate one compact metadata int buffer and pass it beside existing geometry arrays.
  - Current Java metadata mapping uses public `RenderLayer.MultiPhase` fields available in this Yarn version plus layer-name conventions. This is enough to distinguish common cutout, translucent, additive, overlay, weather/cloud/particle/text and water/glass/portal-like cases without exposing Java classes to shaderpacks.
  - `McvrSceneProvider` now uses metadata to place true translucent/additive/overlay packets in the deferred-later translucent stream instead of treating all `WORLD_TRANSPARENT` as cutout.
  - Extended `deferred_rt_scene_provider_test` to cover:
    - old-payload `WORLD_TRANSPARENT -> Cutout` fallback,
    - fallback diagnostics,
    - metadata record unpacking,
    - metadata opaque/cutout/translucent/additive/overlay classification,
    - metadata material flags,
    - metadata/fallback stats accumulation.
  - Updated the architecture document with the implemented ABI, current Java adapter limitations and remaining adapter/replay/debug-overlay work.
- Verification:
  - `gradlew.bat classes` completed successfully in `Radiance-custom` and regenerated ignored JNI headers under `src/main/native/include`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 5/5 tests passed.
- Remaining work:
  - Add a more exact Java adapter or mixin surface for depth test, depth write, write masks, output target, overlay/lightmap, outline/crumbling, layering and `sortOnUpload`; the current implementation still infers several of these from layer names.
  - Extend replay capture if metadata-sensitive transparency behavior must be tested offline. Current replay intentionally remains on the old payload path and therefore exercises fallback classification.
  - Surface provider stats in a debug overlay, log export or offline runner so real-frame fallback classification is visible.
  - Implement the actual `transparent_forward` / refraction path. Step 24 only makes classification accurate enough to route true translucent content out of the opaque/cutout G-buffer.

### Step 25: Deferred RT Native Diagnostics Snapshot

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_diagnostics.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_diagnostics.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/tests/deferred_rt_diagnostics_test.cpp`
  - `MCVR-custom/tests/CMakeLists.txt`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
- Reason for this step:
  - Step 20 exposed classification GPU stats, Step 23 exposed lighting pass GPU stats, and Step 24 exposed provider metadata/fallback stats.
  - Those counters need a single native snapshot before any debug overlay, log export or offline runner consumes them.
  - The snapshot must respect frame-slot latency: provider stats are collected during the current frame, while GPU stats become valid when the same swapchain frame slot is reused after its fence completes.
- Implemented:
  - Added `DeferredRtDiagnosticsSnapshot` with:
    - frame index,
    - requested view count,
    - uploaded G-buffer view count,
    - uploaded G-buffer draw count,
    - `SceneProviderStats`,
    - `DeferredRtClassificationStats`,
    - `DeferredRtPassStats`.
  - Added CPU helpers to:
    - build a snapshot,
    - compute total provider packets,
    - compute total provider indices,
    - compute fallback packet count without double-counting `legacyTransparentCutoutFallbackPackets`,
    - detect whether a snapshot contains provider fallback,
    - format one compact native diagnostics line.
  - Added `DeferredRtModule::latestDiagnosticsSnapshot()` as the native module accessor for future overlay/log/offline-runner use.
  - Added per-frame pending diagnostics snapshots in `DeferredRtModule`.
  - `render3D()` now:
    - reads completed classification/pass stats for the current frame slot,
    - publishes the completed diagnostics snapshot for that same frame slot,
    - begins the new provider frame,
    - stores a new pending snapshot after G-buffer upload has determined view/draw counts.
  - Added `deferred_rt_diagnostics_test` covering provider totals, fallback count semantics, snapshot construction and formatted output.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 6/6 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed `core.dll` plus current shader resources.
- Remaining work:
  - Wire `latestDiagnosticsSnapshot()` to a native log path, an in-game debug overlay or the future offline runner. Step 25 deliberately only adds the shared native data surface.
  - Add a runtime policy for when diagnostics formatting is emitted so normal gameplay does not log every frame by default.
  - Extend diagnostics after `transparent_forward` exists so translucent/refraction work has pass-specific counters instead of only provider routing counts.

### Step 26: Deferred RT Diagnostics Log Control

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_diagnostics.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_diagnostics.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/tests/deferred_rt_diagnostics_test.cpp`
  - `Radiance-custom/src/main/resources/modules/deferred_rt.yaml`
  - `Radiance-custom/src/main/resources/assets/radiance/lang/en_us.json`
  - `Radiance-custom/src/main/resources/assets/radiance/lang/zh_cn.json`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
- Reason for this step:
  - Step 25 created the shared diagnostics snapshot, but there was still no controlled runtime path to inspect it during real frames.
  - The first visible diagnostics surface should be opt-in and throttled so normal gameplay does not format or print diagnostics every frame.
  - The throttle must not use `frameIndex`, because that value is the swapchain frame slot and wraps every few frames.
- Implemented:
  - Added Deferred RT module attributes:
    - `render_pipeline.module.deferred_rt.attribute.diagnostics_log`, default `render_pipeline.false`,
    - `render_pipeline.module.deferred_rt.attribute.diagnostics_log_interval`, default `120`, range `1-600`.
  - Added English and Chinese translations for the Deferred RT module name and both diagnostics attributes.
  - Implemented `DeferredRtModule::setAttributes(...)` handling for the new diagnostics attributes.
  - Added a module-level completed diagnostics snapshot sequence and last-log sequence.
  - Added `DeferredRtModule::maybeLogDiagnosticsSnapshot()`:
    - default-off,
    - logs the first completed snapshot immediately after the attribute is enabled,
    - then logs only after the configured number of completed snapshots,
    - prints the compact Step 25 diagnostics format with a `[DeferredRT]` prefix.
  - Added `deferredRtDiagnosticsShouldLog(...)` as a small CPU helper and extended `deferred_rt_diagnostics_test` to cover first-log and interval behavior, including defensive zero-interval clamping.
  - Updated the architecture document to reflect that native log export now exists, while overlay/offline-runner export remains future work.
- Verification:
  - `gradlew.bat classes` completed successfully in `Radiance-custom`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 6/6 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed `core.dll` plus current shader resources.
- Remaining work:
  - Add an in-game diagnostics/debug overlay that reads the same snapshot without requiring log output.
  - Feed diagnostics into the future offline runner or scene-frame dump path so replay captures can preserve selected shaderpack, attributes and fallback classification.
  - Extend diagnostics after `transparent_forward` / refraction is implemented so routed translucent content has pass-specific counters instead of only provider routing counts.

### Step 27: Deferred RT F3 Diagnostics Text

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_diagnostics.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_diagnostics.cpp`
  - `MCVR-custom/src/core/middleware/com_radiance_client_pipeline_Pipeline.cpp`
  - `MCVR-custom/tests/deferred_rt_diagnostics_test.cpp`
  - `Radiance-custom/src/main/java/com/radiance/client/pipeline/Pipeline.java`
  - `Radiance-custom/src/main/java/com/radiance/mixins/vr_debug/DebugHudVRMixin.java`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
- Reason for this step:
  - Step 26 made diagnostics visible only through an opt-in native log path.
  - Debugging real pipeline switching and provider fallback classification is easier if the current Deferred RT snapshot can be read from Java without enabling log spam.
  - The first Java-visible diagnostics path should be read-only, lightweight and independent of PT modules.
- Implemented:
  - Added `formatDeferredRtDiagnosticsOverlay(...)`, a compact multi-line formatter for F3/debug text.
  - Extended `deferred_rt_diagnostics_test` to cover overlay formatting and invalid-snapshot empty output.
  - Added native JNI `Pipeline.getDeferredRtDiagnosticsOverlay()`:
    - finds the active `DeferredRtModule` in the current native `WorldPipeline`,
    - returns the compact overlay text for the latest valid snapshot,
    - returns an empty string when native is not initialized, no Deferred RT module is active, or no snapshot is valid yet.
  - Added Java wrapper `Pipeline.getDeferredRtDiagnosticsDebugText()` that catches native/runtime failures and returns an empty string for non-Deferred-RT paths.
  - Extended the existing F3 debug text mixin to append a `Deferred RT` section only when diagnostics text is non-empty.
- Verification:
  - `gradlew.bat classes` completed successfully in `Radiance-custom` and regenerated ignored JNI headers under `src/main/native/include`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 6/6 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed `core.dll` plus current shader resources.
- Remaining work:
  - Add a dedicated diagnostics UI or configurable overlay if the F3 text becomes too dense.
  - Feed diagnostics into the future offline runner or scene-frame dump path.
  - Extend diagnostics after `transparent_forward` / refraction exists so translucent work has pass-specific counters.

### Step 28: Deferred RT Java Preset Wiring

- Status: completed.
- Target files:
  - `Radiance-custom/src/main/java/com/radiance/client/pipeline/Presets.java`
  - `Radiance-custom/src/main/java/com/radiance/client/pipeline/Pipeline.java`
  - `Radiance-custom/src/main/java/com/radiance/client/gui/RenderPipelineScreen.java`
  - `Radiance-custom/src/main/resources/assets/radiance/lang/en_us.json`
  - `Radiance-custom/src/main/resources/assets/radiance/lang/zh_cn.json`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Earlier milestones made Deferred RT renderable through manual graph construction, but preset mode still exposed only PT routes.
  - The Java pipeline already supports dynamic preset switching, so Deferred RT needs first-class preset entries before normal UI smoke testing and before shaderpack runtime work can be evaluated in the same workflow as PT.
  - NRD wiring must honor the Deferred RT hit-distance contract: `gi_hit_distance` is diffuse/GI secondary-hit distance, while `first_hit_depth` remains primary surface depth.
- Implemented:
  - Added Deferred RT preset enum entries:
    - `DEFERRED_RT`,
    - `DEFERRED_RT_NRD`,
    - `DEFERRED_RT_NRD_FSR`,
    - `DEFERRED_RT_NRD_XESS`,
    - `DEFERRED_RT_DLSSRR`.
  - Added `DEFERRED_RT_MODULE_NAME` and availability checks for Deferred RT, NRD, FSR, XeSS and DLSSRR routes.
  - Added Java graph assembly for:
    - `Deferred RT -> ToneMapping -> PostRender`,
    - `Deferred RT -> NRD -> TemporalAccumulation -> ToneMapping -> PostRender`,
    - `Deferred RT -> NRD -> FSR -> ToneMapping/PostRender`,
    - `Deferred RT -> NRD -> XeSS -> ToneMapping/PostRender`,
    - `Deferred RT -> DLSSRR -> ToneMapping/PostRender`.
  - Added `connectDeferredRtToNrd(...)` so the Deferred RT NRD semantic exception is centralized:
    - `deferred_rt.gi_hit_distance -> nrd.diffuseHitDepthImage`,
    - `deferred_rt.specular_hit_depth -> nrd.specularHitDepthImage`,
    - `deferred_rt.first_hit_depth` is not connected to NRD diffuse hit depth.
  - Added `connectDeferredRtToUpscaler(...)` for FSR/XeSS primary scene-fact propagation:
    - `linear_depth -> depth`,
    - `first_hit_depth -> first_hit_depth`,
    - `motion_vector -> motion_vector`,
    - `normal_roughness -> normal_roughness`.
  - Kept existing PT presets unchanged, including their historical `ray_tracing.first_hit_depth -> nrd.diffuseHitDepthImage` wiring.
  - Updated `processPresetName(...)` to recognize every `Presets` enum value instead of only the four old PT presets.
  - Updated the preset UI registration to enumerate available `Presets` values rather than hard-coding four PT entries.
  - Added English and Chinese translation keys for the new Deferred RT presets.
  - Kept the fallback/default order PT-first, then Deferred RT, so existing configs do not silently move to the experimental raster path. Explicit user selection and stored Deferred RT presets still work if modules are available.
- Verification:
  - `gradlew.bat classes` completed successfully with `JAVA_HOME=C:\Program Files\Zulu\zulu-21`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully. This step did not change native source, but the Deferred RT CPU regression suite was rebuilt.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 6/6 tests passed.
  - Static diff review confirmed Deferred RT NRD presets connect `gi_hit_distance` to `diffuseHitDepthImage`; only existing PT preset code still connects `first_hit_depth` to that NRD input.
- Remaining work:
  - Add a Java-side graph/preset unit test harness if the project later grows test infrastructure for pipeline assembly; currently this is covered by compile-time checks and runtime graph validation.
  - Run an in-game smoke test after selecting the simplest Deferred RT preset once the user is ready to launch Minecraft.
  - Deferred shaderpack runtime is still not implemented. This step only makes Deferred RT selectable through the same Java preset structure as PT.
  - The PT and Deferred RT preset families now coexist, but shared shaderpack discovery still targets PT packs only. Deferred shaderpack metadata/runtime remains a later planned step.

### Step 29: Deferred Shaderpack Stage Metadata Foundation

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack_stage_contract.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack_stage_contract.cpp`
  - `MCVR-custom/tests/shader_pack_stage_contract_test.cpp`
  - `MCVR-custom/tests/CMakeLists.txt`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Step 28 made Deferred RT selectable through the same Java preset family as PT, but shaderpack metadata still only modeled PT and PostRender stages.
  - The project should keep one shaderpack structure instead of adding a separate Deferred-only configuration language.
  - Deferred RT must not inherit PT/SBT concepts such as `rgen`, hit groups, miss shaders or SHARC update-pass metadata.
- Implemented:
  - Added `ShaderPackLoader::Stage::Deferred`.
  - Added `ShaderPackLoader::KEY_DEFERRED = "deferred"` and `KEY_EXECUTION_DEFERRED = "execution_deferred"`.
  - Added `ShaderPackLoader::ShaderPack::deferredExecution` and `ShaderPack::deferredStageRuntime_`.
  - Extended stage parsing so shaderpack JSON can use `stage: deferred`.
  - Extended execution parsing so Deferred-stage execution can be declared as either:
    - top-level `execution_deferred`, or
    - nested `execution.deferred`.
  - Added duplicate guards so `execution_deferred` cannot silently override `execution.deferred`, and `execution_post` cannot silently override `execution.post_render`.
  - Added default execution-command generation for Deferred-stage passes, matching the existing PT/PostRender behavior.
  - Changed `ShaderPack::stageRuntime(...)` and `ShaderPack::execution(...)` from two-way fallback logic to explicit three-way switches so Deferred never accidentally aliases RayTracing state.
  - Added `shader_pack_stage_contract` as a lightweight, reusable contract helper for:
    - stage-name parsing,
    - pass-kind allow/deny matrix,
    - Deferred-stage PT/SBT field rejection.
  - Allowed Deferred-stage metadata for `render`, `compute` and `full_screen` passes. `full_screen` `compute_3d` now accepts PostRender or Deferred stages.
  - Rejected Deferred-stage use of PT/SBT-only fields including `rgen`, `default_hit_group`, `hit_groups`, `query_sharc`, `miss`, `rmiss`, `sbt`, `shaders`, `rchit`, `rahit` and `rint`.
  - Added `shader_pack_stage_contract_test` and included it in the `mcvr_tests` custom target.
  - Updated the architecture document's Phase 6.1 status to mark the metadata/parser foundation complete while keeping runtime migration as future work.
- Verification:
  - `shader_pack_stage_contract_test.exe` completed successfully.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 7/7 tests passed.
- Remaining work:
  - Add a real Deferred shaderpack runtime inside `DeferredRtModule` that consumes `stage: deferred` passes.
  - Add a distinct `ray_query` pass kind instead of treating first Deferred lighting metadata as generic compute.
  - Add Deferred resource validation for internal G-buffer images, public exports, scene draw streams and ray-query resources.
  - Add a shaderpack lint/offline parser tool so Deferred packs can be validated without launching Minecraft.
  - Add a minimal built-in `vanilla-deferred-rt` pack once fixed G-buffer and lighting runtime passes are ready to move into shaderpack metadata.

### Step 30: Deferred Shaderpack Selection and Validation Shell

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/core/render/pipeline.cpp`
  - `MCVR-custom/src/core/middleware/com_radiance_client_pipeline_Pipeline.cpp`
  - `MCVR-custom/tests/deferred_rt_shaderpack_test.cpp`
  - `MCVR-custom/tests/CMakeLists.txt`
  - `Radiance-custom/src/main/resources/modules/deferred_rt.yaml`
  - `Radiance-custom/src/main/java/com/radiance/client/pipeline/Pipeline.java`
  - `Radiance-custom/src/main/java/com/radiance/client/gui/ShaderPackSettingsScreen.java`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Step 29 added Deferred shaderpack metadata, but the runtime still had no way to choose a Deferred pack from Java or validate the selected pack inside `DeferredRtModule`.
  - The shaderpack UI and dynamic attribute path were still PT-oriented. Deferred RT needs the same structure without inheriting PT's built-in `vanilla-pt` fallback.
  - Default Deferred RT presets must not load or parse the PT pack when no Deferred shaderpack is selected.
- Implemented:
  - Added `render_pipeline.module.deferred_rt.attribute.shader_pack_path` to `deferred_rt.yaml`.
  - Generalized Java shaderpack-owner helpers:
    - `getShaderPackModule()`,
    - `getShaderPackAttributes()`,
    - `isShaderPackAttribute(...)`,
    - module-aware path/default handling for PT vs Deferred RT.
  - Updated `ShaderPackSettingsScreen` to query the active shaderpack owner instead of hard-coding the PT module.
  - Preserved PT behavior: empty PT path still means built-in `shaders/world/ray_tracing/vanilla-pt.zip`, and built-in fallback remains enabled for PT.
  - Added Deferred behavior: empty Deferred path means no shaderpack, no native dynamic attribute query, and no PT fallback.
  - Added `ShaderPack::BuildConfig::allowBuiltInFallback` and extended `ShaderPackLoader::load(...)` so external Deferred pack failures do not silently become `vanilla-pt`.
  - Added `ShaderPack::buildConfigFromDeferredRtAttributes(...)` with `shouldUseSharc = false` and `allowBuiltInFallback = false`.
  - Changed `WorldPipeline` shared shaderpack selection to load from the first shaderpack-owning module:
    - PT always participates through its existing default,
    - Deferred RT participates only when its path attribute is non-empty.
  - Added `DeferredRtShaderPackInspection` and `inspectDeferredShaderPack(...)`:
    - counts Deferred-stage render, compute and full-screen passes,
    - counts execution commands, pass commands and variables,
    - rejects duplicate/empty Deferred pass names,
    - rejects Deferred execution pass commands that reference unknown Deferred-stage passes.
  - `DeferredRtModule::loadShaderPack()` now inspects the shared shaderpack during build, throws on invalid Deferred metadata, and logs a compact `[DeferredRT] deferred shaderpack: ...` summary when a Deferred stage is present.
  - `Pipeline.getDeferredRtDiagnosticsOverlay()` now appends the Deferred shaderpack inspection summary when available.
  - Added native `DeferredRtModule::getAttributes(...)` so Java can read dynamic attributes/translations from an explicit Deferred pack.
  - Added `deferred_rt_shaderpack_test` and wired it into `mcvr_tests`.
  - Updated the architecture document with the Step 30 shaderpack owner policy and remaining runtime work.
- Verification:
  - `gradlew.bat classes` completed successfully with `JAVA_HOME=C:\Program Files\Zulu\zulu-21`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 8/8 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed the updated `core.dll` plus shader resources.
- Remaining work:
  - Implement real Deferred-stage runtime pass recording. Current fixed G-buffer, classification, lighting and compose passes still run from native code outside shaderpack execution.
  - Add descriptor/resource binding and validation for Deferred shaderpack resources, including internal G-buffer images, public exports, scene draw streams and ray-query resources.
  - Add `ray_query` as a distinct Deferred pass kind instead of treating first metadata as generic compute.
  - Define the conflict strategy for graphs that contain both PT and Deferred RT with different explicit shaderpack paths.
  - Add a built-in `vanilla-deferred-rt` pack after fixed native passes have shaderpack-backed equivalents.
  - Add a lint/offline parser path so Deferred packs can be validated without launching Minecraft.

### Step 31: Module-Scoped Shaderpack Compatibility

- Status: completed.
- Target files:
  - `Radiance-custom/src/main/java/com/radiance/client/pipeline/Pipeline.java`
  - `Radiance-custom/src/replayCli/java/com/radiance/replay/cli/ReplayDaemon.java`
  - `Radiance-custom/src/main/resources/assets/radiance/lang/en_us.json`
  - `Radiance-custom/src/main/resources/assets/radiance/lang/zh_cn.json`
  - `MCVR-custom/src/core/render/pipeline.hpp`
  - `MCVR-custom/src/core/render/pipeline.cpp`
  - `MCVR-custom/src/core/render/modules/world/ray_tracing/ray_tracing_module.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.cpp`
  - `MCVR-custom/src/core/render/modules/world/post_render/post_render_module.cpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack_stage_contract.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack_stage_contract.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Step 30 made Deferred RT selectable as a shaderpack owner, but Java selection still treated shaderpacks too globally and native still kept one primary shared shaderpack runtime.
  - A PT-only pack must not be assignable to Deferred RT, and a Deferred-only pack must not silently become the PT pack.
  - Future graphs may contain both PT and Deferred RT or dynamically switch between them, so shaderpack compatibility has to be module/stage scoped before adding real Deferred pass execution.
- Implemented:
  - Added Java shaderpack stage inspection from `configs.json`:
    - pass `stage` values,
    - legacy PT default stage for untagged `full_screen`, `compute` and `ray_tracing` passes,
    - `execution`, `execution_post`, `execution_deferred`,
    - nested `execution.ray_tracing`, `execution.post_render` and `execution.deferred`.
  - Extended `Pipeline.ShaderPackChoice` with immutable `stages`.
  - Changed shaderpack list sorting so packs compatible with the current shaderpack owner appear before incompatible packs.
  - Changed `isShaderPackSelectable(...)`, active detection and unavailability tooltips to evaluate the current shaderpack owner:
    - PT requires `ray_tracing`,
    - Deferred RT requires `deferred`,
    - chunk-emission availability remains module-aware.
  - Changed `setShaderPack(...)` so selection mutates only the current shaderpack owner module instead of writing the same path to every shaderpack-owning module.
  - Added fallback for stored module shaderpack paths that no longer support the module's required stage.
  - Added `stages` to replay daemon shaderpack listing output.
  - Added English and Chinese UI tooltip strings for module-incompatible shaderpacks.
  - Added native `ShaderPack::BuildConfig::requiredStage` and build-time validation:
    - PT-loaded packs must contain `ray_tracing` content,
    - Deferred-loaded packs must contain `deferred` content,
    - Deferred empty path still means no shaderpack and is handled before loading.
  - Changed `WorldPipeline` to store shaderpack runtimes by owning module and added `shaderPackForModule(...)`.
  - Changed `RayTracingModule` and `DeferredRtModule` to read their own shaderpack runtime instead of whichever shared pack was loaded first.
  - Kept `WorldPipeline::shaderPack()` as the primary runtime compatibility path for `PostRenderModule`; this preserves existing PT packs whose `post_render` stage is bundled with the PT pack.
  - Completed the metadata shell for `ray_query`:
    - added `PassConfig::Type::RayQuery`,
    - added parser support for `type: ray_query`,
    - allowed `ray_query` only in Deferred stage contract,
    - counted `ray_query` in Deferred shaderpack inspection,
    - made PT and PostRender reject `ray_query` clearly.
- Verification:
  - `gradlew.bat classes` completed successfully with `JAVA_HOME=C:\Program Files\Zulu\zulu-21`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 8/8 tests passed.
- Remaining work:
  - Implement actual Deferred-stage command recording and execution. `ray_query` is only parsed/validated in this step.
  - Add descriptor/resource binding and validation for Deferred shaderpack resources, including G-buffer images, public exports, scene draw streams and ray-query resources.
  - Make PostRender shaderpack ownership explicit instead of relying on the primary world shaderpack runtime.
  - Add tests or fixtures for Java shaderpack stage inspection once a Java-side test harness exists.
  - Add a built-in `vanilla-deferred-rt` pack after fixed native Deferred passes are represented as shaderpack passes.
  - Add an offline shaderpack lint/parser path for Deferred packs.

### Step 32: Explicit Shaderpack Ownership for PostRender

- Status: completed.
- Target files:
  - `Radiance-custom/src/main/java/com/radiance/client/pipeline/Pipeline.java`
  - `Radiance-custom/src/main/java/com/radiance/client/gui/ShaderPackSettingsScreen.java`
  - `Radiance-custom/src/main/resources/modules/post_render.yaml`
  - `Radiance-custom/src/main/resources/assets/radiance/lang/en_us.json`
  - `Radiance-custom/src/main/resources/assets/radiance/lang/zh_cn.json`
  - `MCVR-custom/src/core/middleware/com_radiance_client_pipeline_Pipeline.cpp`
  - `MCVR-custom/src/core/render/pipeline.cpp`
  - `MCVR-custom/src/core/render/modules/world/post_render/post_render_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/post_render/post_render_module.cpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Dynamic pipeline switching needs shaderpack ownership to belong to modules, not to one implicit world-level primary pack.
  - PostRender is shaderpack-backed today, so leaving it attached to whichever PT/Deferred runtime happens to be primary makes it difficult to support one pipeline using different packs for different shaderpack-capable modules.
  - Current UI still exposes a single Shader Pack screen for the primary PT/Deferred owner, so this step needed a compatibility bridge instead of a full UI redesign.
- Implemented:
  - Added `render_pipeline.module.post_render.attribute.shader_pack_path` to `post_render.yaml`.
  - Made Java treat PostRender as a shaderpack-capable module with required stage `post_render`.
  - Kept `Pipeline.getShaderPackModule()` focused on the primary PT/Deferred owner so the existing Shader Pack screen behavior stays unchanged.
  - Changed `setShaderPack(...)` so choosing a PT or Deferred pack also writes that pack to other shaderpack-capable modules in the graph only when the pack supports the target module's stage.
  - Added a temporary same-pack dynamic attribute sync: if PT/Deferred and PostRender resolve to the same physical pack, the primary owner's dynamic shaderpack attribute values are copied to the other module before saving/building. This preserves current single-screen settings behavior until the preset UI can configure per-module packs directly.
  - Split shaderpack dynamic attribute storage by module stage for non-PT owners:
    - PT keeps the historical `<pack>.txt` file.
    - PostRender uses `<pack>.post_render.txt`.
    - Deferred uses `<pack>.deferred.txt`.
    - Reading still falls back to the legacy `<pack>.txt` file when no module-scoped file exists.
  - Added native PostRender dynamic attribute discovery through the existing `Pipeline.getAttributes(...)` JNI bridge.
  - Added `ShaderPack::buildConfigFromPostRenderAttributes(...)` with `requiredStage = PostRender`.
  - Changed `WorldPipeline` to build a PostRender-owned shaderpack runtime.
  - Changed `PostRenderModule` to consume `worldPipeline->shaderPackForModule(PostRenderModule::NAME)` instead of the primary compatibility runtime.
  - Scoped non-PT runtime resources to the owning stage's pass graph so PostRender does not instantiate PT-only global runtime textures/buffers from a combined PT/PostRender pack.
  - Updated the architecture document with the Step 32 owner policy and temporary UI bridge.
- Verification:
  - `gradlew.bat classes` completed successfully with `JAVA_HOME=C:\Program Files\Zulu\zulu-21`.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 8/8 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully and installed the updated `core.dll` plus shader resources.
- Remaining work:
  - Replace the temporary single-screen sync with a preset UI/storage model that explicitly configures the pipeline and each shaderpack-capable module's pack.
  - Define and validate the cross-module shaderpack resource rule. Current runtime assumes no implicit cross-module shaderpack resources; shared data must move through explicit pipeline resources.
  - Add Java-side tests for module-stage discovery and compatible sync behavior when a suitable test harness exists.
  - Implement actual Deferred-stage command recording and resource binding.
  - Add a built-in `vanilla-deferred-rt` pack after fixed native Deferred passes are represented as shaderpack passes.
  - Add an offline shaderpack lint/parser path for Deferred packs.

### Step 33: Deferred RT Visibility Mask and Queue Scheduling

- Status: completed.
- Reason for this step:
  - Reflection and GI ray-query passes currently use full-resolution layered dispatch with pixel-level early-out from the classification mask. That was acceptable for bring-up, but it is not the intended performance path.
  - A tile-only early-out stage is not worth making a milestone: once the renderer classifies work per tile, it should compact active work and dispatch queued entries instead of returning to full-screen dispatch.
  - OpenXR hidden-area pixels are never displayed and should be removed before G-buffer/classification/secondary-ray scheduling. This is distinct from foveated quality reduction.
  - Commit `a6dc2a371edd902211cd77f27162dc5e839fdaba` already added the useful OpenXR-side foundation: `XR_KHR_visibility_mask` discovery, `xrGetVisibilityMaskKHR` query, and per-eye hidden-area mesh storage. That should be used as reference for Deferred RT mask ingestion. Do not re-add PT-module mask early-out; PT is ray-tracing-pipeline shaped and does not benefit enough to justify touching it now.
- Updated architecture policy:
  - `visibility:primary_mask` now represents valid primary pixels after G-buffer and OpenXR visibility-mask clipping.
  - `classification:lighting_queues` replaces the old loose `classification:lighting_tiles` wording and means compacted per-view tile/pixel queues plus indirect dispatch arguments.
  - Pixel-level early-out remains a defensive validation path for stale queue entries, bounds and debugging. It is not the main scheduling mechanism after queues exist.
- Implemented:
  - Added `deferred_rt_lighting_queue` as the CPU/GPU ABI contract for the first queue scheduler.
  - Chose a fixed 16x16 tile size for the first implementation.
  - Added Descriptor Set 7 for visibility and queue resources:
    - binding 0: `R32_UINT` visibility mask image,
    - binding 1: compacted tile record buffer,
    - binding 2: queue header/counter buffer,
    - binding 3: `VkDispatchIndirectCommand` argument buffer.
  - Added queue kinds for direct light, reflection, GI and refraction. Each kind has a fixed record partition, a header with offset/capacity/count/overflow and one indirect dispatch command.
  - Added per-frame visibility mask images and CPU upload buffers.
  - When OpenXR hidden-area mesh data is available, Deferred RT rasterizes the per-eye hidden mesh into the visibility mask. If OpenXR data is not available yet, the mask uploads as all-valid and reuploads once the hidden mesh becomes available.
  - `classify.comp` now reads the visibility mask and writes `PixelClassificationNone` for hidden pixels.
  - Added `build_lighting_queues.comp` after classification. It reads classification plus visibility, emits compacted tile records and increments indirect group counts.
  - Reflection and GI now dispatch through `vkCmdDispatchIndirect` from the reflection/GI queue partitions. Their common shader code resolves the invocation tile record before processing pixels inside the tile.
  - Direct light remains full-resolution dispatch for now, but the direct-light queue is built and reported in diagnostics so empty-tile rates can drive the later decision.
  - Refraction queue records can be emitted by the queue ABI, but the transparent/refraction pass does not consume them yet because the transparent path is still a later phase.
  - Deferred RT diagnostics now include visibility mask stats and per-kind queue active/overflow tile counts.
  - Added CPU tests for the queue ABI, dimensions, indirect offsets, stats conversion and OpenXR-style hidden mesh rasterization.
  - Extended classification and diagnostics tests for hidden visibility pixels and queue reporting.
- Synchronization notes:
  - Queue headers and indirect args are initialized through persistent staging each frame.
  - Queue-builder writes are synchronized before shader reads and indirect dispatch.
  - Queue headers are copied back for next-frame diagnostics and then transitioned back to compute shader reads before reflection/GI consume them.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully with `D:\VulkanSDK\1.4.335.0\Bin` prepended to `PATH` for `glslangValidator.exe`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 9/9 tests passed.
- Remaining work:
  - G-buffer rasterization still does not use stencil/scissor/hidden-area clipping directly. Hidden OpenXR pixels are removed at classification/queue scheduling, but the earliest G-buffer-stage clipping policy still needs a later pass-level implementation.
  - Direct light still uses full dispatch plus pixel classification checks; the direct queue exists for stats and future migration.
  - Refraction/transparent queue consumption is not implemented until the dedicated transparent/refraction path.
  - First queue records store tile coordinates plus flags only. Packed active pixel bounds can be added later if diagnostics show sparse-tile waste.
  - Foveated secondary-work density is not implemented yet. It should map onto queue emission, resolution tier or sample-count policy without reducing primary G-buffer fidelity inside the OpenXR visible area.

### Step 34: Deferred Shaderpack Runtime Execution

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/core/middleware/com_radiance_client_pipeline_Pipeline.cpp`
  - `MCVR-custom/tests/deferred_rt_shaderpack_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Steps 30-32 made Deferred RT a shaderpack owner and Step 33 made the expensive native lighting path queue-shaped, but Deferred-stage shaderpack passes still could not execute.
  - The next `vanilla-deferred-rt` migration needs a real runtime for compute/ray-query/full-screen compute passes before fixed native passes can be mirrored in pack metadata.
  - This step intentionally does not move fixed native G-buffer/classification/lighting/compose into shaderpack yet. It creates the execution substrate those passes will use in the next step.
- Implemented:
  - Added `DeferredRtShaderPackRuntimePlan` with validation and diagnostics.
  - Runtime-supported Deferred pass kinds are:
    - `compute`,
    - `ray_query` when the device reports ray-query support,
    - `full_screen` with `backend: compute_3d`, including `backend: auto` selecting a compute backend.
  - Render passes and graphics full-screen passes remain metadata-pending. They are allowed to exist, but the module rejects a Deferred execution command that tries to run them.
  - Added Deferred shaderpack scene descriptor set 8:
    - world, last-world and sky uniform buffers,
    - G-buffer view and draw buffers,
    - classification and visibility mask storage images,
    - lighting queue record/header buffers,
    - texture mapping buffer,
    - TLAS.
  - Added runtime-resource descriptor set and execution-variable descriptor set after the fixed Deferred sets using the existing `ShaderPack` set-index convention.
  - Added runtime resource initialization, buffer refresh and descriptor binding for Deferred-owned shaderpacks.
  - Added compute pipeline construction for Deferred `compute`, `ray_query` and full-screen `compute_3d` passes.
  - Added Deferred execution command recording after native compose and before final output barriers.
  - Added per-pass execution-variable upload, expression-based compute dispatch dimensions, full-screen compute dispatch by target dimensions and layer count, and TLAS readiness checks for ray-query passes.
  - Added resource barriers for runtime textures/buffers and Deferred public outputs used by shaderpack pass resource lists. Sampled-only/imported runtime textures use shader-read layouts; imported textures cannot be pass outputs.
  - Extended diagnostics overlay output with the Deferred shaderpack runtime plan summary.
  - Extended `deferred_rt_shaderpack_test` for:
    - compute plus supported ray query,
    - executed ray query without device support,
    - pending render pass not executed,
    - executed render pass rejection,
    - full-screen compute and auto-selected compute backend.
- Synchronization notes:
  - Deferred shaderpack runtime runs after the fixed native compose pass in this step, so user passes can post-process or augment current Deferred public outputs/runtime resources.
  - Runtime pass barriers cover declared pass `inputs` and `outputs`. Fixed scene/G-buffer/queue resources bound through scene set 8 are synchronized by the native Deferred frame barriers that prepare classification, queues and compose.
  - Ray-query runtime passes insert a TLAS read barrier when ray-query support and a frame TLAS are available; passes skip execution if the frame has no TLAS.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully with `D:\VulkanSDK\1.4.335.0\Bin` prepended to `PATH`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 9/9 tests passed.
- Remaining work:
  - `vanilla-deferred-rt` pack migration is next: mirror the fixed native Deferred path in pack metadata, then decide pass-by-pass when native fixed ordering is removed or kept as fallback.
  - Fixed native G-buffer/classification/direct-light/reflection/GI/compose still run outside shaderpack execution. The Step 34 runtime currently executes after native compose.
  - Render/graphics full-screen Deferred shaderpack passes are still pending. First migration should prefer compute/ray-query/full-screen compute until the raster pass backend is ready.
  - Deferred shaderpack pass resource lists currently manage runtime textures/buffers and public output images. Scene set 8 exposes fixed scene/G-buffer/queue resources directly to shaders; stronger metadata validation and named resource linting should be added with the built-in pack.
  - G-buffer still lacks earliest-stage OpenXR hidden-area stencil/scissor clipping.
  - Transparent/refraction queue consumption remains deferred to the dedicated transparent/refraction path.

### Step 35: Built-in `vanilla-deferred-rt` Shaderpack Migration

- Status: completed.
- Target files:
  - `MCVR-custom/CMakeLists.txt`
  - `MCVR-custom/src/shader/CMakeLists.txt`
  - `MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt/configs.json`
  - `MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt/world/deferred_rt/*`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/core/render/pipeline.cpp`
  - `MCVR-custom/tests/deferred_rt_shaderpack_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Keeping the fixed Deferred shader sources as installed `.spv` files would make every later shaderpack feature harder to migrate and would continue expanding the old C++-owned pass path.
  - Deferred RT should match the PT module's pack-owned shader model: pack controls pass list, shader files and execution order; C++ owns hardened Vulkan resources, descriptors, barriers, TLAS binding, queue buffers and indirect dispatch offsets.
- Implemented:
  - Moved all Deferred RT GLSL sources from `src/shader/world/deferred_rt/` into `src/shader/world/deferred_rt/internal/vanilla-deferred-rt/world/deferred_rt/`.
  - Added `vanilla-deferred-rt/configs.json` with the full built-in pass sequence:
    - `clear_contract`,
    - `gbuffer` render pass with `content: minecraft_gbuffer`,
    - `classify`,
    - `build_lighting_queues`,
    - `direct_light`,
    - queued `reflection`,
    - queued `gi`,
    - `compose`.
  - Added high-level dispatch schedules:
    - `screen` for full-screen compute/ray-query passes,
    - `tile_grid` for the queue builder,
    - `lighting_queue` for reflection and GI indirect dispatch.
  - Added shaderpack schema/runtime support for:
    - `fallback_compute` on compute/ray-query passes,
    - `schedule`,
    - `local_size`,
    - lighting queue enums,
    - render shader variants such as `native_stereo` and `multiview` without requiring a verbose `backends` block.
  - Added `ShaderPack::builtInDeferredRtShaderPackPath()` and made Deferred RT's empty shaderpack path load `shaders/world/deferred_rt/vanilla-deferred-rt.zip`.
  - Changed shaderpack zip extraction staging from the PT-specific `temp/shaders/world/ray_tracing` directory to the shared `temp/shader_packs` directory.
  - Changed shared shaderpack config creation so Deferred RT always builds a module-owned deferred pack runtime, even when the configured path is empty.
  - Removed the old fixed Deferred compute/G-buffer pipeline members and the old fixed pass recording functions.
  - Added Deferred shaderpack render-pass runtime support for `minecraft_gbuffer`; C++ selects native-stereo or multiview render backend, creates the graphics pipelines, records draw streams and consumes pack-owned vertex/fragment shaders.
  - Changed compute/ray-query runtime dispatch:
    - `screen` schedule is calculated by C++ from image size, layer count and pass `local_size`,
    - `direct` schedule uses validated expression dimensions,
    - `tile_grid` dispatches the lighting queue tile grid,
    - `lighting_queue` uses `vkCmdDispatchIndirect` with C++-owned queue offsets.
  - Kept pixel-level shader early-outs as defensive checks only; reflection/GI now use queue dispatch as the main scheduling path.
  - Made `depth_compare` and `color_blend` fields affect the Deferred G-buffer graphics pipeline.
  - Excluded `world/deferred_rt/internal` from ordinary shader `.spv` installation.
  - Added install-time creation of `shaders/world/deferred_rt/vanilla-deferred-rt.zip`, staged with `common/` and `util/`.
  - Added install-time removal of stale old Deferred `.spv` artifacts from both Radiance resources and MCVR bin resources.
  - Updated `deferred_rt_shaderpack_test` so render pass execution is accepted for `minecraft_gbuffer`, unsupported render content is rejected, ray-query fallback is accepted without device ray-query support, and lighting-queue schedules are counted/validated.
- Synchronization and ownership notes:
  - Pack authors do not choose Vulkan descriptor set numbers, queue buffer offsets, indirect argument offsets, TLAS binding or barriers.
  - Pack authors declare pass order, shader files and high-level schedules. `lighting_queue` takes a semantic queue kind (`direct`, `reflection`, `gi`, `refraction`), not an arbitrary indirect offset.
  - Scene set 8 remains a C++ ABI between the hardened runtime and the Deferred shaders. It is not a public JSON resource namespace yet.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target shaders core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully with `D:\VulkanSDK\1.4.335.0\Bin` prepended to `PATH`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 9/9 tests passed.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target INSTALL -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - Manual pack shader compile check with `glslangValidator.exe -V --target-env vulkan1.4 -I<vanilla-deferred-rt-root>` completed successfully for all Deferred pack `.comp`, `.vert` and `.frag` files.
  - Installed `Radiance-custom/src/main/resources/shaders/world/deferred_rt/vanilla-deferred-rt.zip`.
  - Verified `Radiance-custom/src/main/resources/shaders/world/deferred_rt` contains the zip and no old fixed Deferred `.spv` files after install.
- Remaining work:
  - Move `ScenePrepare` and `SceneProviderFactory` lifetime to `WorldPipeline` scope so PT and Deferred share scene runtime cleanly.
  - Add a dedicated shaderpack lint/offline parser executable; current verification is build plus tests plus manual pack shader compilation.
  - Add in-game, VR and preset switching tests before considering the framework frozen.
  - G-buffer still needs earliest-stage OpenXR hidden-area clipping through mask/stencil/scissor policy. Current visibility mask still removes hidden pixels at classification/queue scheduling.
  - Direct light remains full-screen `screen` dispatch in `vanilla-deferred-rt`; the direct queue is built for diagnostics and future migration.
  - Transparent/refraction queue consumption remains deferred to the dedicated transparent/refraction path.
  - Stronger named resource validation for Deferred scene/G-buffer/queue resources should be added with the diagnostics/offline report chain.

### Step 36: Deferred Shaderpack Explicit Pass Semantics

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt/configs.json`
  - `MCVR-custom/tests/deferred_rt_shaderpack_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Step 35 moved pass list, shader files and execution order into `vanilla-deferred-rt`, but the hardened Deferred runtime still identified some fixed sync/barrier hooks by pass `name`.
  - That made custom pass names unsafe: a pack author could rename `classify`, `build_lighting_queues`, `reflection`, `gi` or `compose` and accidentally disable required C++ synchronization.
  - The runtime needs two separate concepts:
    - `name`: the pack-local execution reference and debug label,
    - `semantic`: the fixed ABI tag consumed by C++ for hardened synchronization and diagnostics.
- Implemented:
  - Added optional `semantic` parsing to `full_screen`, `render`, `ray_tracing`, `compute` and Deferred `ray_query` pass configs.
  - Changed Deferred fixed synchronization hooks to check `pass.semantic`, not `pass.name`.
  - Updated built-in `vanilla-deferred-rt/configs.json` to declare explicit semantics for:
    - `clear_contract`,
    - `gbuffer`,
    - `classify`,
    - `build_lighting_queues`,
    - `direct_light`,
    - `reflection`,
    - `gi`,
    - `compose`.
  - Kept execution command lookup keyed by pass `name`; `semantic` is not an alias and cannot be used in `execution.deferred.commands`.
  - Renamed the Deferred full-screen compute runtime's internal top-level pass reference from `semanticName` to `executionName` so it cannot be confused with the new JSON `semantic`.
  - Added a runtime-plan test showing that a pass can be named `custom_classification` with `semantic: classify`, and that execution must still reference `custom_classification`.
- Synchronization notes:
  - Pass-name-based sync hazards are resolved for the existing Deferred fixed hooks.
  - A pack that omits a fixed semantic will not trigger that hardened hook. This is intentional; the built-in pack declares all required semantics and future lint/offline validation should report missing required semantics for packs that target the full built-in pipeline contract.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully with `D:\VulkanSDK\1.4.335.0\Bin` prepended to `PATH`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 9/9 tests passed.
- Remaining work:
  - Add shaderpack lint/offline validation for required Deferred ABI semantics, duplicate semantic tags and semantic/pass type compatibility.
  - Move `ScenePrepare` and `SceneProviderFactory` lifetime to `WorldPipeline` scope so PT and Deferred share scene runtime cleanly.
  - Add in-game, VR and preset switching tests before freezing the framework.

### Step 37: Deferred Semantic Order Validation and Public Schema Requirements

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.cpp`
  - `MCVR-custom/tests/deferred_rt_shaderpack_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Step 36 separated `name` and `semantic`, but raw `execution.deferred.commands` can still express invalid Deferred
    main-pipeline order during the transition period.
  - Deferred RT cannot safely expose an arbitrary global execution graph like PT because the backbone has real content
    dependencies: G-buffer before classification, classification before queue build, queues before queued reflection/GI,
    lighting before compose.
  - The long-term public schema should be C++ backbone + fixed semantic slots + phase insertion lists + logical
    resources, while the current C++ recorder can continue consuming an internally generated ordered command list.
- Implemented:
  - Added runtime-plan validation for fixed Deferred semantics:
    - known fixed semantic enum,
    - duplicate fixed semantic rejection,
    - fixed semantic type checks,
    - fixed semantic schedule checks,
    - top-level fixed semantic ordering checks.
  - Fixed semantic pass order is validated against:
    - `clear_contract`,
    - `gbuffer`,
    - `classify`,
    - `build_lighting_queues`,
    - `direct_light`,
    - `reflection`,
    - `gi`,
    - `compose`.
  - Fixed semantic passes cannot be hidden inside conditional or looped execution commands.
  - Extended `deferred_rt_shaderpack_test` for valid built-in-style semantic order, G-buffer-after-classify order
    rejection and fixed semantic type mismatch rejection.
- Public schema requirements recorded:
  - `execution.deferred.commands` is a transition/runtime IR, not the desired long-term Deferred authoring model.
  - Deferred public schema should become `slots` + custom `passes` + phase `insertions`, plus the existing ShaderPack
    runtime texture/buffer declarations, normalized by C++ into the internal command list.
  - Pack author requirements now tracked explicitly:
    - attributes/uniforms should reuse the existing ShaderPack `attributes` array schema,
    - simple defines should reuse attribute `define` and pass `define`/`defines`/`definitions`,
    - missing feature requirements and fallback validation,
    - missing resource lifetime policy (`per_frame`, `history`, `persistent`, `imported_texture`) on top of existing
      imported/intermediate/shared resource handling,
    - missing debug outputs for logical resources,
    - missing offline lint / expanded graph report.
- Verification:
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target deferred_rt_shaderpack_test -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `MCVR-custom/build-radiance-custom/tests/Release/deferred_rt_shaderpack_test.exe` completed successfully.
  - `cmake --build MCVR-custom/build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully with `D:\VulkanSDK\1.4.335.0\Bin` prepended to `PATH`.
  - `ctest --test-dir MCVR-custom/build-radiance-custom -C Release --output-on-failure` completed successfully: 9/9 tests passed.
- Remaining work:
  - Build the new Deferred public schema parser/normalizer for `slots`, custom `passes`, phase `insertions` and
    pack-owned logical resources declared through the existing ShaderPack texture/buffer resource syntax where possible.
  - Lower the normalized schema to the existing internal runtime pass registry and ordered command list.
  - Add resource visibility rules per phase and enforce them in validation/lint.
  - Do not add a Deferred-only compact `attributes` object/map. Reuse the PT-compatible root `attributes` array:
    `name`, `type`, `default_value`, and `define`.
  - Make Deferred built-in empty-path attribute discovery match PT behavior: load `vanilla-deferred-rt` and return its
    attributes instead of returning an empty list.
  - Reuse pass-level `define`/`defines`/`definitions` and attribute `define` for simple defines. Add common/root
    definitions only if they merge into the same existing definitions model without a new competing syntax.
  - Implement the missing parts: device capability requirements/fallback validation, explicit resource lifetime policy,
    debug output registry, and offline lint/report command.

### Step 37.1: ShaderPack Authoring Feature Reuse Audit

- Status: completed documentation audit.
- Scope:
  - Compared the new Deferred authoring requirements against the existing PT/ShaderPack loader and Java attribute UI.
  - No runtime code was changed in this audit.
- Existing capabilities to reuse:
  - Root `attributes` is already an array parsed by `ShaderPackLoader::parseAttributeConfig`.
  - Attribute entries already use `name`, `type`, `default_value`, and `define`.
  - Existing useful attribute `type` forms include scalar/bool/range/enum forms such as `bool`, `int`, `float`,
    `int_range:min-max`, `float_range:min-max`, and `enum:a-b-c`. Java UI also has generic string/vector widgets.
  - Attribute `define` already emits shader definitions from configured values and supports expression-style mappings.
  - Pass-level shader definitions already accept `define`, `defines`, or `definitions` through the same parser.
  - `fallback_compute` already exists for Deferred compute/ray-query style pass configs.
  - ShaderPack runtime resources already support imported file textures, intermediate textures, runtime buffers and
    the existing `shared` lifetime-like flag.
- Missing capabilities to implement later:
  - Deferred `getAttributes()` currently returns an empty attribute list when the shaderpack path is empty; PT loads its
    built-in pack in that case. Deferred should load built-in `vanilla-deferred-rt` so built-in pack attributes appear
    in UI/preset flows.
  - No explicit device-capability requirement model exists for ray query, multiview, storage image format support,
    half precision, etc.
  - No explicit resource lifetime enum exists for `per_frame`, `history`, `persistent`, or `imported_texture`.
    Existing `type: file`, `type: intermediate`, and `shared` should be preserved and extended rather than replaced.
  - No debug output registry exists for exposing pack-owned logical resources to debug views/offline reports.
  - No offline lint / expanded graph report command exists yet.
- Design correction:
  - Deferred should not introduce a second attribute/uniform syntax. If a new Deferred public schema needs to normalize
    data internally, the author-facing syntax should stay compatible with the existing PT ShaderPack schema unless there
    is no existing equivalent.

### Step 38: Deferred Public Schema Parser/Normalizer

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt/configs.json`
  - `MCVR-custom/tests/CMakeLists.txt`
  - `MCVR-custom/tests/shader_pack_deferred_schema_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Step 37 established that raw `execution.deferred.commands` is a runtime IR, not the long-term Deferred authoring
    surface.
  - Shaderpack authors need stable extension points: fixed semantic slots for the backbone, custom passes inserted at
    named phases, and logical resources validated by C++ instead of hand-written command lists, descriptors or barriers.
  - This step implements the parser/normalizer part first so the built-in pack and tests use the public schema shape
    before resource graph validation is layered on top.
- Implemented:
  - Added root `slots` parsing for the fixed Deferred backbone:
    - `clear_contract`,
    - `gbuffer`,
    - `classify`,
    - `build_lighting_queues`,
    - `direct_light`,
    - `reflection`,
    - `gi`,
    - `compose`.
  - Slot keys are normalized as fixed semantics; each slot is parsed as a normal pass with `stage: deferred` and
    `semantic` injected from the slot key.
  - A slot that explicitly declares a mismatched `semantic` now fails during load.
  - First-version public schema requires a complete 8-slot backbone. There is no overlay/inherit-built-in-slot mode yet.
  - Slot validation now checks semantic-specific pass type and schedule:
    - `gbuffer` must be `render` with `content: minecraft_gbuffer` and the supported Deferred render backends,
    - `clear_contract`, `classify` and `compose` must be `compute` with `screen` schedule,
    - `build_lighting_queues` must be `compute` with `tile_grid` schedule,
    - `direct_light` must be `ray_query` with `screen` schedule,
    - `reflection` and `gi` must be `ray_query` with their matching `lighting_queue` queue.
  - Added root `insertions` parsing for named phase lists:
    - `after_clear` / `after_clear_contract`,
    - `after_gbuffer`,
    - `after_classify` / `after_classification`,
    - `after_lighting_queues` / `after_queue` / `after_queues`,
    - `before_compose`,
    - `after_compose`.
  - Public-schema root `passes` is now treated as the custom Deferred pass list. Missing custom pass `stage` defaults to
    `deferred`.
  - First-version custom passes are restricted to:
    - `compute`, or
    - `full_screen` with only `compute_3d` backends.
  - Custom passes cannot declare a fixed backbone `semantic`, cannot use `lighting_queue` schedule, must have
    `local_size.z == 1`, and must be inserted exactly once.
  - Unknown insertion phase names, unknown custom pass references, duplicate insertion references and uninserted custom
    passes now fail at shaderpack load time.
  - Public schema rejects author-written `root.execution.deferred` and `root.execution_deferred`; C++ generates the
    internal `deferredExecution.commands` list in canonical backbone order plus phase insertions.
  - Converted built-in `vanilla-deferred-rt/configs.json` from raw `passes` plus `execution.deferred.commands` to the
    new `slots` schema.
  - Added `shader_pack_deferred_schema_test` as a loader-only schema test target. It compiles `shader_pack.cpp` with a
    `MCVR_SHADER_PACK_LOADER_ONLY` definition so parser tests do not link the Vulkan runtime half of `ShaderPack`.
  - The new test uses explicit runtime checks instead of `assert`, so Release builds still exercise the schema failures.
- Validation covered by tests:
  - Complete slots generate the canonical internal Deferred command order.
  - Custom compute and full-screen compute insertions are placed at their requested phases.
  - Manual Deferred execution is rejected when public schema is used.
  - Missing/unknown slots, slot type mismatches, slot schedule mismatches and explicit slot semantic mismatches fail.
  - Unsupported custom render passes fail.
  - Custom passes that are not inserted, inserted more than once, use layered `local_size.z`, or include a graphics
    full-screen backend variant fail.
  - The source-tree built-in `vanilla-deferred-rt` pack loads through `slots` and expands to the 8-pass backbone.
- Deliberately deferred:
  - The `resources` wrapper and logical resource registry are not implemented in this step. Existing root `textures`
    and `buffers` remain the runtime resource syntax until Step 39 adds the Deferred logical registry on top of the
    existing model.
  - `inputs` / `outputs` are parsed and preserved, but phase visibility, read/write dependency checks, custom namespace
    validation and generated descriptor/barrier reports are still Step 39 work.
  - Root/common definitions and explicit feature requirements/fallback validation remain Step 40 work.
  - Resource lifetime policy, including `history`, remains Step 41 work.
  - Debug output registry and offline lint/expanded graph report are still future tooling work.
  - Deferred built-in empty-path `getAttributes()` parity with PT remains open: the UI/preset attribute path should
    load `vanilla-deferred-rt` instead of returning an empty list when the configured Deferred shaderpack path is empty.
- Verification:
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target shader_pack_deferred_schema_test -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Release\shader_pack_deferred_schema_test.exe` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully with `D:\VulkanSDK\1.4.335.0\Bin` prepended to `PATH`.
  - `D:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\ctest.exe --test-dir G:\cpp\radiance\MCVR-custom\build-radiance-custom -C Release --output-on-failure` completed successfully: 10/10 tests passed.
- Remaining work:
  - Step 39 should implement Deferred logical resources and phase visibility validation using the existing
    texture/buffer runtime path where possible. This was completed in Step 39.
  - Step 40 should implement common/root definitions plus pack/pass/slot `requires` capability validation and
    ray-query fallback selection errors.
  - Step 41 should implement explicit resource lifetime policy, including real history resources and reset rules.
  - Only after these public schema gaps are closed should work resume on the larger shared scene runtime route.

### Step 39: Deferred Logical Resources and Phase Visibility Validation

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/tests/shader_pack_deferred_schema_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Step 38 gave Deferred packs stable `slots`, custom `passes`, and phase `insertions`, but custom pass resources were
    only parsed metadata.
  - Shaderpack authors need to declare logical data nodes such as `custom.ssao`, `custom.noise`, `gbuffer.depth`,
    `visibility.primary_mask`, and `lighting.specular`; they should not author Vulkan descriptor sets, bindings, image
    layouts, barriers, or command buffers.
  - The implementation should reuse the existing ShaderPack runtime texture/buffer path instead of creating a second
    Deferred-only resource allocator.
- Implemented:
  - Added Deferred public-schema `resources` parsing. Supported first-version groups are:
    - `resources.images` for writable or sampled runtime images,
    - `resources.textures` for sampled/imported texture-style resources,
    - `resources.buffers` for SSBO-style runtime buffers.
  - Lowered `resources.images` and `resources.textures` into existing `ShaderPackLoader::TextureConfig` entries, and
    lowered `resources.buffers` into existing `ShaderPackLoader::BufferConfig` entries.
  - Added authoring aliases needed by the public schema:
    - image types: `texture2d`, `texture2d_array`, `texture3d`,
    - buffer type: `ssbo`,
    - formats such as `rgba8`, `r16f`, `rg16f`, `rgba16f`, and `r32f`,
    - `size: "render"` for `RENDER_WIDTH` / `RENDER_HEIGHT`,
    - `layers: "view_count"` for `VIEW_COUNT`,
    - `usage: ["storage"]`, `["sampled"]`, or both.
  - Enforced pack-owned logical resource names under `custom.*` when a pack uses the Deferred public schema. Existing
    root `textures` and `buffers` are still supported, but in a public Deferred pack they are treated as the same logical
    custom registry and must also use `custom.*`.
  - Added automatic runtime binding allocation for `resources` wrapper declarations after any explicit legacy
    `textures`/`buffers` bindings.
  - Added shader compile definitions for runtime resources:
    - `RADIANCE_RUNTIME_RESOURCE_SET`,
    - `RADIANCE_RESOURCE_<SANITIZED_NAME>_SAMPLED_BINDING`,
    - `RADIANCE_RESOURCE_<SANITIZED_NAME>_STORAGE_BINDING`,
    - `RADIANCE_RESOURCE_<SANITIZED_NAME>_BUFFER_BINDING`.
  - Extended `inputs` / `outputs` parsing to support the compact image-name array form:
    - `"inputs": ["custom.ssao", "gbuffer.depth"]`,
    - while preserving the explicit object form with `images` and `buffers`.
  - Added Deferred custom pass graph validation over the generated internal command list:
    - custom pass outputs must be declared `custom.*` resources,
    - custom image outputs must be writable,
    - custom passes cannot write protected namespaces such as `gbuffer.*`, `classification.*`, `queue.*`, `scene.*`,
      `lighting.*`, or `out.*`,
    - custom resources cannot be read before an earlier inserted pass writes them, except imported/initial-readable
      textures,
    - a custom resource can only be written once in the first version,
    - unknown namespaces in custom pass inputs fail,
    - built-in namespace reads are phase-gated against the fixed backbone.
  - Added phase visibility rules for first-version built-in logical resources:
    - `scene.*` is always visible,
    - `gbuffer.*` after `gbuffer`,
    - `classification.*` and `visibility.*` after `classify`,
    - `queue.*` after `build_lighting_queues`,
    - `lighting.direct` after `direct_light`,
    - `lighting.reflection` / `lighting.specular` after `reflection`,
    - `lighting.gi` / `lighting.indirect` after `gi`,
    - `lighting.composed`, `lighting.primary`, and `out.*` after `compose`.
  - Kept runtime execution on the existing generated `deferredExecution.commands` IR. Pack authors still do not write
    `execution.deferred.commands`; C++ builds it from `slots` plus `insertions`.
  - Kept runtime resource transitions on the existing Deferred runtime path. Compute and full-screen compute custom
    passes already call `DeferredRtModule::addShaderPackPassResourceBarriers()` with their declared `inputs` and
    `outputs`; this step adds schema validation for that graph rather than exposing barriers to authors.
  - Added schema tests for:
    - wrapper resource lowering and valid read/write flow,
    - protected namespace output rejection,
    - read-before-write rejection,
    - too-early built-in resource reads,
    - undeclared custom outputs,
    - duplicate custom writes,
    - non-`custom.*` resource declarations.
- Deliberately deferred:
  - Root/common definitions and the explicit `requires` capability model remain Step 40. Attribute `define` and pass
    `define` / `defines` / `definitions` are still the current shader option path.
  - Ray-query capability validation with fallback selection remains Step 40. Existing `fallback_compute` is not yet a
    complete pack/pass/slot requirement system.
  - Explicit resource lifetimes (`per_frame`, `history`, `persistent`, `imported_texture`) remain Step 41. Existing
    imported texture, intermediate runtime resource and `shared` behavior is preserved but not yet promoted to the new
    lifetime enum.
  - `history` resources are not implemented by this step; temporal resources still need current/previous descriptor
    naming, resize/reload/world-change resets and tests.
  - Debug outputs and offline lint / expanded graph reports remain future tooling work. Runtime descriptor binding macros
    exist, but there is no standalone report yet for expanded pass order, binding map, barrier plan or unused resources.
  - Custom render passes remain intentionally unsupported in the first public-schema version; first-version custom
    extension passes are still compute or full-screen `compute_3d`.
  - Deferred built-in empty-path `getAttributes()` parity with PT remains open.
- Verification:
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target shader_pack_deferred_schema_test -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Release\shader_pack_deferred_schema_test.exe` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully with `D:\VulkanSDK\1.4.335.0\Bin` prepended to `PATH`.
  - `ctest --test-dir G:\cpp\radiance\MCVR-custom\build-radiance-custom -C Release --output-on-failure` completed successfully: 10/10 tests passed.
- Remaining work:
  - Step 40 should implement common/root definitions plus pack/pass/slot `requires` capability validation and
    ray-query fallback selection errors.
  - Step 41 should implement explicit resource lifetime policy, including real history resources and reset rules.
  - Only after these public schema gaps are closed should work resume on the larger shared scene runtime route.

### Step 40: Common Definitions and Feature Requirements

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/core/vulkan/device.hpp`
  - `MCVR-custom/src/core/vulkan/device.cpp`
  - `MCVR-custom/tests/shader_pack_deferred_schema_test.cpp`
  - `MCVR-custom/tests/deferred_rt_shaderpack_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Step 38 and Step 39 made Deferred packs author passes, insertions and logical resources without exposing raw command
    lists, descriptor bindings or barriers.
  - Shaderpack authors still needed a common macro layer for all shaders and a device-capability model for feature-gated
    packs and passes.
  - Ray-query lighting slots already had `fallback_compute`, but unsupported devices still needed validation errors that
    speak in public schema terms instead of failing later during command recording or pipeline creation.
- Implemented:
  - Added root/common shader definitions using the existing definitions aliases:
    - `define`,
    - `defines`,
    - `definitions`.
  - Root definitions are stored as common shader definitions and merged into all shader compile requests before
    attribute definitions, pass/request definitions and runtime resource binding definitions.
  - Existing pass definitions still work and can override root/common definitions for that pass.
  - Added first-version capability requirements parsing from root, pass and slot declarations through public JSON
    `requires`.
  - Supported capability spellings:
    - `"ray_query"`,
    - `"multiview"`,
    - `"half_precision"`,
    - `"storage_image_format:<format>"`, such as `storage_image_format:r16f`,
    - object form such as `{ "capability": "storage_image_format", "format": "r16f" }`.
  - Slot-level `requires` works because slots are normalized into normal Deferred pass configs with the slot semantic
    injected by the public-schema parser.
  - Backend blocks merge parent and backend `requires` arrays during parsing so backend-specific metadata is preserved
    for later variant work.
  - Added native device capability queries used by Deferred runtime validation:
    - ray query support,
    - multiview support,
    - shader float16 support,
    - storage image format support via physical-device format properties.
  - Added a Deferred runtime capability object and kept the older bool ray-query overload for existing tests and callers.
  - Deferred runtime-plan validation now checks:
    - root/pack requirements as hard pack requirements,
    - pass/slot requirements as pass availability requirements,
    - unsupported referenced passes as runtime-plan errors with public capability names.
  - Ray-query pass fallback policy is now explicit:
    - if the device supports ray query, use the ray-query compute shader,
    - if the device lacks ray query and the pass declares `fallback_compute`, the runtime pass remains valid and uses the
      fallback pipeline,
    - if the device lacks ray query and the pass has no fallback, the plan fails with a clear missing
      `ray_query`/`fallback_compute` error.
  - Root-level `requires: ray_query` remains a hard pack requirement. It is not satisfied by individual pass fallback
    shaders.
  - Added loader tests for root common definitions, pass definition override, root/pass `requires` parsing and unknown
    capability rejection.
  - Added runtime-plan tests for unsupported root requirements, unsupported pass requirements, storage image format
    requirements and ray-query pass fallback with an explicit `requires: ray_query`.
- Deliberately deferred:
  - A full variant/permutation matrix is still not implemented. Step 40 keeps the schema extensible by preserving
    `requires` metadata on backend configs, but it does not choose among arbitrary quality/backend variants.
  - A generic `fallback` object/key is not implemented. Current executable fallback behavior remains the existing
    `fallback_compute` field on `ray_query` compute-style passes.
  - Backend-specific requirement selection is not a general policy yet. First-version runtime validation is pack/pass/slot
    level; richer backend selection belongs with the future permutation/variant model.
  - Resource lifetime policy remains Step 41. `per_frame`, `history`, `persistent` and `imported_texture` are not
    promoted to an explicit schema in this step.
  - Debug output registry and offline lint/expanded graph report remain future tooling work, including unsupported
    feature warning reports outside module initialization.
  - Deferred built-in empty-path `getAttributes()` parity with PT remains open.
- Verification:
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target deferred_rt_shaderpack_test -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target shader_pack_deferred_schema_test -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Release\deferred_rt_shaderpack_test.exe` completed successfully.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Release\shader_pack_deferred_schema_test.exe` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully with `D:\VulkanSDK\1.4.335.0\Bin` prepended to `PATH`.
  - `ctest --test-dir G:\cpp\radiance\MCVR-custom\build-radiance-custom -C Release --output-on-failure` completed successfully: 10/10 tests passed.
- Remaining work:
  - Step 41 should implement explicit resource lifetime policy, including real history resources and reset rules.
  - Full variants/permutations, generic fallback policy, debug outputs and offline lint/expanded graph reporting remain
    future public-schema/tooling work.
  - Only after Step 41 is closed should work resume on the larger shared scene runtime route.

### Step 41: Deferred Resource Lifetime Policy

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/tests/shader_pack_deferred_schema_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Step 39 made custom resources logical graph nodes, but resource lifetime was still implicit through imported file
    textures, per-frame runtime resources and the older `shared` flag.
  - Deferred public packs need a temporal history model that shader authors can use without writing descriptor sets,
    binding numbers, image layouts, barriers or manual ping-pong state.
- Implemented:
  - Added explicit resource lifetimes:
    - `per_frame`,
    - `history`,
    - `persistent`,
    - `imported_texture`.
  - Applied lifetime parsing to both preserved root `textures` / `buffers` syntax and public `resources.images` /
    `resources.textures` / `resources.buffers` wrapper syntax.
  - Preserved compatibility defaults:
    - file/imported textures default to `imported_texture`,
    - `shared: true` defaults to `persistent`,
    - non-shared runtime resources default to `per_frame`.
  - Added validation for invalid combinations:
    - imported textures must use `imported_texture`,
    - buffers cannot use `imported_texture`,
    - explicit `lifetime` cannot contradict an explicit `shared` flag,
    - `history` images cannot be imported and must include storage usage, including preserved root `textures` entries.
  - Added history resource registry tracking for custom images and buffers.
  - Added `.previous` as an input-only logical alias for custom `history` resources:
    - `custom.temporal.previous` reads the previous slot,
    - outputs to `.previous` are rejected,
    - `.previous` on non-history resources is rejected,
    - `.previous` is not supported for protected built-in namespaces in the first version.
  - Runtime resources now allocate by lifetime:
    - `persistent`: one instance,
    - `per_frame`: one instance per swapchain frame,
    - `history`: two instances per swapchain frame.
  - Added previous descriptor bindings and generated shader definitions:
    - `RADIANCE_RESOURCE_<NAME>_PREVIOUS_SAMPLED_BINDING`,
    - `RADIANCE_RESOURCE_<NAME>_PREVIOUS_STORAGE_BINDING`,
    - `RADIANCE_RESOURCE_<NAME>_PREVIOUS_BUFFER_BINDING`.
  - Added per-frame history slot management:
    - `beginRuntimeFrame(frameIndex)` flips the current slot after the frame has valid history,
    - `.previous` always points to the opposite slot, so first-frame previous reads the zero-cleared slot instead of
      aliasing the current output slot,
    - `completeRuntimeFrame(frameIndex)` marks that frame's history valid.
  - Added history reset and clear paths:
    - runtime resource rebuild resets history slots and clears all history images/buffers to zero,
    - shaderpack pre-close clears the runtime history state,
    - history buffer expression-size rebuilds reset and clear history,
    - history images/buffers are created with transfer-destination usage so the zero-clear path is valid.
  - Deferred runtime resource barriers now normalize `.previous` names and barrier previous images/buffers separately.
  - Full-screen compute targets are treated as implicit runtime outputs for barrier recording, matching Step 39 schema
    validation.
  - Added schema tests for:
    - default lifetimes for wrapper resources,
    - explicit `history`, `persistent` and `imported_texture`,
    - root `textures` / `buffers` history lifetimes,
    - previous binding allocation,
    - invalid `shared` / `lifetime` combinations,
    - root `history` image rejection when `storage_binding` is missing,
    - `.previous` output rejection,
    - `.previous` on non-history resources.
- Deliberately deferred:
  - Engine-level reset events for camera cuts, dimension/world changes and major render-scale changes still need a
    dedicated signal path. Step 41 implements the resource-side reset behavior and hooks it into runtime rebuild,
    shaderpack reload/pre-close and history buffer resize paths.
  - Full variant/permutation selection, generic fallback objects, debug output registry and offline lint / expanded graph
    reports remain future public-schema/tooling work.
  - Deferred built-in empty-path `getAttributes()` parity with PT remains open.
- Verification:
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target shader_pack_deferred_schema_test -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Release\shader_pack_deferred_schema_test.exe` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target deferred_rt_shaderpack_test -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Release\deferred_rt_shaderpack_test.exe` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target core mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir G:\cpp\radiance\MCVR-custom\build-radiance-custom -C Release --output-on-failure` completed successfully: 10/10 tests passed.

### Step 42: Pipeline-Owned Shared Scene Runtime

- Status: completed.
- Target files:
  - `MCVR-custom/src/core/render/scene_runtime/shared_scene_runtime.hpp`
  - `MCVR-custom/src/core/render/scene_runtime/shared_scene_runtime.cpp`
  - `MCVR-custom/src/core/render/pipeline.hpp`
  - `MCVR-custom/src/core/render/pipeline.cpp`
  - `MCVR-custom/src/core/render/modules/world/world_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/ray_tracing/ray_tracing_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/ray_tracing/ray_tracing_module.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/tests/shared_scene_runtime_test.cpp`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Earlier work extracted `ScenePrepare` and added a neutral `SceneProvider`, but `RayTracingModule` and
    `DeferredRtModule` still each owned their own `ScenePrepare`.
  - A pipeline containing both PT and Deferred RT would duplicate BLAS/TLAS preparation and scene metadata work.
  - Future world modules should consume scene facts through a pipeline-level runtime rather than reading chunks,
    entities, Java payloads, Blaze3D objects, Vulkan layouts or shaderpack concepts directly.
- Design:
  - `WorldPipeline` owns one `SharedSceneRuntime` per native pipeline build.
  - `SharedSceneRuntime` owns the shared `ScenePrepare` and exposes one `SharedSceneFrameContext` per swapchain frame.
  - `SharedSceneFrameContext` provides:
    - the shared `ScenePrepareContext`,
    - a lazily-built `McvrSceneProvider` for neutral draw packets, views, material/render-state metadata and
      acceleration metadata,
    - per-frame guards so scene preparation and provider packet enumeration happen at most once for that frame.
  - `WorldModule` exposes scene-runtime requirement flags. First-version requirements are:
    - `Acceleration`: module needs TLAS/BLAS scene metadata,
    - `Provider`: module needs neutral draw/view/material packets.
  - `RayTracingModule` requests `Acceleration` only. It consumes the shared `ScenePrepareContext`, then performs PT-only
    SBT/hit-group setup in its own module path.
  - `DeferredRtModule` requests `Acceleration | Provider`. It consumes the shared provider and never owns scene
    preparation directly.
  - `WorldPipelineContext::render()` calls the shared runtime before executing world module contexts, using the aggregate
    requirement flags from the active modules.
- Implementation notes:
  - Added `SceneRuntimeRequirement` flags to `WorldModule`:
    - `Acceleration`: shared `ScenePrepareContext` / TLAS / BLAS scene metadata,
    - `Provider`: neutral draw/view/material packet stream.
  - Added `SharedSceneRuntime` and `SharedSceneFrameContext` under `MCVR-custom/src/core/render/scene_runtime/`.
  - Added `SharedSceneFrameGuard` with a pipeline frame serial. The guard intentionally does not use swapchain
    `frameIndex` as its per-frame token because the same swapchain image is reused across later rendered frames.
  - `WorldPipeline` now:
    - creates modules and applies module attributes,
    - aggregates scene runtime requirements,
    - creates `WorldPipelineContext` objects before module context construction,
    - builds `SharedSceneRuntime` only if some active module requests scene runtime data,
    - builds modules and then binds their contexts back into `WorldPipelineContext`,
    - calls `SharedSceneRuntime::beginFrame(frameIndex, eyeCount, requirements)` before module rendering.
  - `RayTracingModule` no longer owns `ScenePrepare`. It requests `Acceleration`, obtains the shared
    `ScenePrepareContext`, and keeps PT-only SBT/hit-group setup local.
  - `DeferredRtModule` no longer owns `ScenePrepare` or constructs its own `McvrSceneProvider`. It requests
    `Acceleration | Provider`, refreshes the shared provider each render frame after pipeline begin-frame, and consumes
    the provider's draw streams, view data and AS metadata.
  - Added `shared_scene_runtime_test` to cover requirement flag behavior and frame-serial guard semantics.
- JNI/game bridge policy:
  - This step does not add new JNI payloads. Current Java bridge data is already sufficient for the milestone:
    chunk/entity geometry, texture ids, group/content names, vertex/index counts, raster metadata, world/sky uniforms
    and texture mapping.
  - Future JNI extensions should add missing scene facts to the Java proxy/native world storage boundary, then flow
    through `SharedSceneRuntime`. Modules should not call Java or depend on JNI record layouts.
  - Blaze3D remains a future scene-source adapter. It should feed the shared runtime rather than Deferred RT directly.
- Completion standard:
  - PT-only pipelines still build and render with the same shaderpack behavior.
  - Deferred-only pipelines still build and render with the same G-buffer/ray-query shaderpack path.
  - A diagnostic pipeline containing both PT and Deferred RT shares one `ScenePrepare` and one per-frame
    `ScenePrepareContext`.
  - `ScenePrepareContext::render()` is submitted at most once per frame before modules consume TLAS/scene metadata.
  - Provider packet enumeration is skipped for PT-only pipelines and runs at most once for Deferred RT frames.
  - Native tests cover requirement flag aggregation and per-frame guard behavior without requiring Minecraft runtime
    state.
- Verification:
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target shared_scene_runtime_test core -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --config Release --target mcvr_tests -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir G:\cpp\radiance\MCVR-custom\build-radiance-custom -C Release --output-on-failure` completed successfully: 11/11 tests passed.
  - `G:\cpp\radiance\Radiance-custom\gradlew.bat classes` completed successfully. Gradle reported existing `sun.misc.Unsafe` warnings only.

### Step 43: Deferred Runtime Graph Selection and Diagnostics Gating

- Status: completed for the graph-culling and diagnostics-foundation milestone.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt/configs.json`
  - `MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt/world/deferred_rt/classify.comp`
  - `MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt/world/deferred_rt/pass_stats_common.glsl`
  - `MCVR-custom/tests/deferred_rt_shaderpack_test.cpp`
  - `MCVR-custom/tests/shader_pack_deferred_schema_test.cpp`
  - `Radiance-custom/src/main/resources/modules/deferred_rt.yaml`
  - `Radiance-custom/docs/deferred-rt-runtime-flexibility-and-diagnostics-plan.md`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Shaderpack authors need to choose `ray_query`, compute-only or disabled paths without editing native C++.
  - Disabled or inactive paths must disappear from pipeline creation and dispatch rather than relying on shader early
    returns.
  - Deferred RT must not request TLAS/provider work when the active pack profile does not use those scene subsystems.
  - JVM/Minecraft integration cannot rely on console IO for Deferred runtime diagnostics.
- Implemented:
  - Added `enabled_if` to Deferred full-screen, render, compute and ray-query pass configs.
  - Added `DeferredBackendKind` with `compute_3d`, `ray_query` and `disabled`.
  - Added Deferred selection groups and `selection_profiles`; profile rule order is evaluated at runtime-plan build time.
  - Kept `auto` as only a pack-defined/default selector name. Selecting an undefined profile now fails explicitly.
  - Implemented a small static expression evaluator for graph selection:
    - bool attributes,
    - enum equality/inequality,
    - numeric comparisons,
    - `&&`, `||`, `!` and parentheses,
    - static capability and VR booleans,
    - unknown static tokens fail the plan.
  - Built active runtime plans with:
    - selected concrete pass names,
    - logical-to-concrete pass aliases,
    - disabled logical pass names,
    - graph report entries for selected, skipped and disabled backends,
    - active provider/acceleration/material/history/lighting-queue flags.
  - Deferred runtime now builds pipelines only for active concrete passes and skips disabled logical commands during
    execution.
  - ShaderPack runtime resources can be scoped to active pass names before Deferred descriptor layouts and runtime
    resources are created, so unselected candidates do not force runtime resource descriptors or allocations.
  - `sceneRuntimeRequirements()` now derives acceleration/provider needs from the active plan and fails early if the
    plan is invalid while module attributes are applied.
  - Deferred compute/ray passes parse `vr`; `view_agnostic` means the existing layer-aware single dispatch path,
    `unsupported` is rejected for stereo, and unimplemented `per_view` / `mono_replicate` policies fail instead of
    silently running as layered.
  - Removed Deferred RT startup/runtime plan `std::cout` logging and per-frame diagnostics console output.
  - Gated classification/pass/queue stats allocation, descriptor binding, clears, readbacks and shader atomics behind
    diagnostics stats enablement.
  - Updated built-in `vanilla-deferred-rt` with backend attributes for direct light, reflection and GI:
    - `auto`,
    - `ray_query`,
    - `compute_3d`,
    - `disabled`.
  - Updated the Radiance module YAML so those backend attributes are visible to the Java/UI side.
- Deliberately deferred:
  - Named resolution domains, half/quarter dispatch domains and scaled queue builders.
  - Output default policy and removal of the blanket full-contract clear from the normal path.
  - Generated barrier/descriptor plans beyond active-pass resource scoping.
  - GPU timestamp timing scopes and file trace sinks.
  - True `per_view` and `mono_replicate` Deferred compute execution modes.
- Verification:
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --target deferred_rt_shaderpack_test shader_pack_deferred_schema_test --config Debug` completed successfully.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Debug\deferred_rt_shaderpack_test.exe` completed successfully.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Debug\shader_pack_deferred_schema_test.exe` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --target core mcvr_tests --config Release -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully.
  - `ctest --test-dir G:\cpp\radiance\MCVR-custom\build-radiance-custom -C Release --output-on-failure` completed successfully: 11/11 tests passed.
  - `G:\cpp\radiance\Radiance-custom\gradlew.bat classes` completed successfully. Gradle reported existing `sun.misc.Unsafe` and deprecation warnings.
  - Debug `core` link was not used for final verification because the existing build tree links Release `shaderc_combined.lib`
    into Debug `core`, producing MSVC `_ITERATOR_DEBUG_LEVEL` / runtime-library mismatch errors unrelated to this step.

### Step 44: Deferred Named Resolution Domains

- Status: completed.
- Milestone: M1 of the eight-part Deferred RT runtime continuation.
- Target files:
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
  - `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.cpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
  - `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`
  - `MCVR-custom/src/shader/world/deferred_rt/internal/vanilla-deferred-rt/configs.json`
  - `MCVR-custom/tests/deferred_rt_shaderpack_test.cpp`
  - `MCVR-custom/tests/shader_pack_deferred_schema_test.cpp`
  - `Radiance-custom/docs/deferred-rt-runtime-flexibility-and-diagnostics-plan.md`
  - `Radiance-custom/docs/deferred-rt-module-architecture.md`
  - `Radiance-custom/docs/deferred-rt-implementation-log.md`
- Reason for this step:
  - Shaderpack authors need a named resolution primitive so half/quarter-resolution work can reduce real dispatch and
    resource cost instead of relying on shader early returns.
  - Later coordinate, queue and output-default work needs pass/resource resolution to already be part of the active
    runtime plan.
- Implemented:
  - Added root `resolutions` parsing with built-in `full`, `half` and `quarter` domains.
  - Allowed shaderpacks to override built-in domain scale/round/min values and define additional named domains.
  - Added `resolution` to Deferred full-screen, render, compute and ray-query pass configs.
  - Added `resolution` to pack-owned runtime images in both preserved root texture syntax and Deferred
    `resources.images` wrapper syntax. A resolution-domain image cannot also declare explicit width/height or legacy
    `size`.
  - Added shared shaderpack helpers for resolution-domain lookup and extent evaluation. These live in the generic
    shaderpack layer so runtime resources do not depend on Deferred RT internals.
  - Runtime images can now allocate from a named resolution domain using the same scale/min/round logic as Deferred
    passes.
  - Deferred runtime-plan validation now checks pass and runtime-image domain references and records the selected
    concrete pass -> resolution domain mapping.
  - Deferred graph report selection lines include the selected pass resolution domain.
  - `screen` and `tile_grid` compute dispatch schedules use the active pass domain's width/height. This reduces launched
    workgroups for scaled domains.
  - Full-screen compute keeps target-image dispatch dimensions for `full` domains and uses the selected domain for
    non-`full` domains.
  - Built-in `vanilla-deferred-rt` declares `full`, `half` and `quarter` domains. All built-in passes remain `full`
    until the coordinate-contract and queue-domain milestones land.
- Deliberately deferred:
  - The unified resolution push constant ABI is M2. M1 still passes the legacy width/height push constants, so scaled
    shaders must not be enabled in built-in rendering yet.
  - Full/dispatch coordinate mapping, half/quarter G-buffer sampling, upsample ownership, history-resolution policy,
    motion-vector reprojection coordinates and queue tile coordinate rules are M2.
  - Lighting queue indirect dispatch scaling is M3. `screen` and `tile_grid` schedules scale in M1, but
    `lighting_queue` indirect arguments are still the existing queue path.
  - Output default policy, generated barriers/descriptors, diagnostics snapshots, true `per_view`/`mono_replicate` and
    deeper compose/direct-light optimization remain later milestones.
- Tests added:
  - Schema parsing for custom `resolutions`, resource `resolution`, pass `resolution`, built-in default domains and
    invalid resource size/resolution mixes.
  - Selection backend resolution inheritance and per-backend override parsing.
  - Runtime extent evaluation for half/quarter/min/round behavior and invalid scale rejection.
  - Runtime-plan recording of pass resolution domains.
  - Runtime-plan errors for unknown pass/resource domains.
  - Graph report coverage for selected backend resolution.
- Verification:
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --target deferred_rt_shaderpack_test shader_pack_deferred_schema_test --config Debug` completed successfully using the Visual Studio bundled CMake path because `cmake` was not on the shell `PATH`.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Debug\deferred_rt_shaderpack_test.exe` completed successfully.
  - `G:\cpp\radiance\MCVR-custom\build-radiance-custom\tests\Debug\shader_pack_deferred_schema_test.exe` completed successfully.
  - `cmake --build G:\cpp\radiance\MCVR-custom\build-radiance-custom --target core mcvr_tests --config Release -- /m:1 /p:CL_MPCount=1 /v:minimal` completed successfully using the Visual Studio bundled CMake path.
  - `ctest --test-dir G:\cpp\radiance\MCVR-custom\build-radiance-custom -C Release --output-on-failure` completed successfully: 11/11 tests passed.
  - `G:\cpp\radiance\Radiance-custom\gradlew.bat classes` completed successfully. Gradle reported existing `sun.misc.Unsafe` and deprecation warnings.
  - MSVC reported existing C4819 code-page warnings while compiling headers such as `as.hpp` and `vr_system.hpp`; no new build failure was introduced.
