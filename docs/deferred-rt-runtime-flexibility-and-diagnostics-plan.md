# Deferred RT Runtime Flexibility and Diagnostics Plan

Status: Step A/B/C/D/E/F/G foundation partially implemented; named resolution domains, the first coordinate contract, scaled queue domains, native output defaults, generated graph/barrier/descriptor plans and compact diagnostics transport are implemented
Scope: MCVR native Deferred RT runtime, shared ShaderPack schema, built-in `vanilla-deferred-rt`, Radiance Java diagnostics handoff  
Goal: make Deferred RT shaderpacks able to control real resource cost without exposing Vulkan details, while keeping the C++ pipeline efficient, VR-correct and diagnosable.

---

## 1. Problem Statement

The current Deferred RT implementation proves the renderer shape, but several costs are still tied to a fixed backbone:

- fixed slots are generally present once declared by the pack,
- `direct_light` is currently a full-resolution screen dispatch,
- `reflection` and `gi` use lighting-queue indirect dispatch, and M3 lets packs bind those queues to full, pass or named
  resolution domains instead of a fixed full-resolution tile domain,
- ray-query passes only fall back to compute when ray query is unavailable or TLAS is missing,
- there is no first-class pass/resource resolution domain for half-resolution or quarter-resolution ray work,
- diagnostics currently rely too much on console output, which is not reliable when running inside the JVM/Minecraft process,
- statistics and readbacks are useful during shader development but must be zero-cost, or near zero-cost, when disabled.

The next work should not add new visual features first. It should make the runtime graph flexible and cheap enough that shaderpack authors can define their own quality/performance controls, and so performance differences between shader changes are measurable.

---

## 2. Design Goals

1. Shaderpacks, not the engine, define quality controls.
2. The engine exposes backend, resolution, scheduling and diagnostics primitives.
3. Disabled or inactive work must be removed from the runtime graph instead of relying on shader early returns.
4. Ray query must be an optional backend, not a requirement for valid Deferred rendering.
5. Compute and disabled backends must be first-class paths.
6. VR execution must be explicit and compatible with the existing PostRender `backend` / `backends` / `vr` model.
7. Runtime diagnostics must work inside the JVM process without depending on console IO.
8. Timing/statistics must be off by default and must not allocate, dispatch, atomically count or read back when disabled.
9. The built-in pack must use the same public primitives expected of third-party packs.
10. C++ must keep owning command recording, barriers, descriptors, TLAS binding, output defaults and graph validation.

---

## 3. Current Constraints to Fix

### 3.1 Ray query selection is too implicit

Current behavior:

- built-in `direct_light`, `reflection` and `gi` are declared as `ray_query`,
- the runtime uses the ray-query pipeline when the device supports ray query and a TLAS is available,
- otherwise it uses `fallback_compute`.

Missing behavior:

- shaderpacks cannot force compute fallback when ray query is available,
- shaderpacks cannot disable a ray effect without removing or rewriting the slot,
- the runtime still tends to assume acceleration/provider requirements at the module level instead of deriving them from the active runtime plan.

### 3.2 Resolution scaling is not a runtime primitive

Current behavior:

- shaderpack resources have a `scale` field, but fixed Deferred lighting passes do not share a named execution-resolution model,
- half-resolution or quarter-resolution ray work is not a supported pass domain,
- shader early returns can reduce useful work but still launch workgroups.

Missing behavior:

- scaled dispatch dimensions,
- scaled queue generation,
- scaled intermediate resources,
- targeted output defaults and a future upsample/compose handoff for scaled public outputs,
- diagnostics that report actual dispatch resolution and ray-query pixel count.

### 3.3 Backend and VR selection should reuse PostRender concepts, but not hard-code `auto`

PostRender already has:

- `backend: auto`,
- `backends`,
- `graphics`,
- `compute_3d`,
- render backend choices such as `native_stereo` and `multiview`,
- `vr` policy values:
  - `per_view`,
  - `view_agnostic`,
  - `mono_replicate`,
  - `unsupported`.

Deferred should extend this model instead of introducing a conflicting schema. The new Deferred behavior should be a generalized version of PostRender backend selection.

However, the old meaning of `auto` is no longer enough. PostRender currently uses engine-defined `auto` priorities for a small backend set. Deferred backend selection must consider ray query, compute fallback, disabled paths, VR execution, resolution domains, queue domains, device capabilities and pack attributes. A hard-coded engine priority would hide important quality/performance policy from shaderpack authors.

New Deferred selection must therefore treat `auto` as just a pack-defined profile name. A pack may call the profile `auto`, `performance`, `buto`, `cuto` or any other identifier. The engine should execute the selected profile's rules; it should not assign hidden quality meaning to the profile name.

### 3.4 Diagnostics rely on the wrong output path

Console output is not a stable diagnostics transport for Minecraft/JVM integration. Even when it works in development, it is not a robust runtime API and can add avoidable overhead.

Diagnostics should be stored in module-owned native state and exposed through explicit consumers:

- F3/debug text,
- Java-visible diagnostics snapshots,
- optional file trace,
- optional in-game overlay later,
- optional RenderDoc/Nsight marker/timestamp paths.

---

## 4. Public Shaderpack Schema Extensions

### 4.1 Pass and slot `enabled_if`

Add `enabled_if` to Deferred slots and custom Deferred passes.

Example:

```json
{
  "attributes": [
    {
      "name": "enable_gi",
      "type": "bool",
      "default_value": "true"
    },
    {
      "name": "gi_scale",
      "type": "float_range:0.25-1.0",
      "default_value": "0.5"
    }
  ],
  "slots": {
    "gi": {
      "enabled_if": "enable_gi",
      "backend": "gi_backend",
      "selection_profiles": {
        "auto": [
          {
            "if": "capability.ray_query",
            "use": "ray"
          },
          {
            "use": "compute"
          }
        ],
        "compute": [
          {
            "use": "compute",
            "required": true
          }
        ]
      },
      "backends": {
        "ray": {
          "backend": "ray_query",
          "vr": "view_agnostic",
          "compute": "world/deferred_rt/gi_ray_query.comp"
        },
        "compute": {
          "backend": "compute_3d",
          "vr": "view_agnostic",
          "compute": "world/deferred_rt/gi_compute.comp"
        }
      }
    }
  }
}
```

Runtime requirement:

- inactive passes are removed before pipeline creation,
- inactive passes do not allocate pass-only runtime resources,
- inactive passes do not record barriers,
- inactive passes do not dispatch,
- inactive passes do not create or download statistics.

`enabled_if` is a static graph selection expression. It is evaluated only when building or rebuilding the runtime plan:

- shaderpack load,
- shaderpack reload,
- module attribute change,
- explicit native runtime rebuild.

It must not be evaluated every frame and it must not depend on per-frame values such as frame index, camera state, time, scene counters, queue sizes, visibility results or dynamic runtime statistics.

The first expression language must stay deliberately small:

```text
bool_attribute
!bool_attribute
enum_attribute == enum_token
enum_attribute != enum_token
numeric_attribute < numeric_literal
numeric_attribute <= numeric_literal
numeric_attribute > numeric_literal
numeric_attribute >= numeric_literal
expr && expr
expr || expr
(expr)
```

Allowed inputs:

- static shaderpack/module attributes,
- device capability booleans when used in backend selection rules,
- VR mode booleans when used in backend selection rules,
- numeric literals,
- enum tokens.

Explicitly disallowed:

- functions,
- string concatenation,
- arbitrary string comparisons,
- arithmetic expressions beyond simple numeric comparisons,
- array/object indexing,
- per-frame variables,
- scene-dependent values,
- resource sizes that can change without rebuilding the runtime plan.

### 4.2 First-class `disabled` backend

Add a public `disabled` backend for Deferred pass options.

Example:

```json
{
  "backend": "gi_backend",
  "selection_profiles": {
    "auto": [
      {
        "if": "use_rt_gi && capability.ray_query",
        "use": "ray"
      },
      {
        "if": "enable_gi",
        "use": "compute"
      },
      {
        "use": "off"
      }
    ]
  },
  "backends": {
    "ray": {
      "backend": "ray_query",
      "vr": "view_agnostic",
      "compute": "world/deferred_rt/gi_ray_query.comp"
    },
    "compute": {
      "backend": "compute_3d",
      "compute": "world/deferred_rt/gi_compute.comp"
    },
    "off": {
      "backend": "disabled"
    }
  }
}
```

Disabled is a valid rendering choice, not an error. The output policy decides how unwritten outputs are handled.

### 4.3 Explicit backend selection for ray-style effects

Deferred ray-style effects should support these backend families:

- `ray_query`: uses TLAS and hardware ray query.
- `compute_3d`: uses compute only, no TLAS required.
- `disabled`: no work.

Future backend labels may include:

- `screen_space`,
- `probe_cache`,
- `raster`,
- portable backend families for DX12/Metal/WebGPU.

First implementation should only add what is executable now:

- `ray_query`,
- `compute_3d`,
- `disabled`.

### 4.4 Pack-defined selection profiles

Deferred should keep the existing PostRender concept of concrete `backends`, but backend selection should become profile based.

Concrete implementations live under `backends` or `options`. Selection profiles are named rule lists that choose one implementation. The name `auto` is not special except for compatibility and convention.

Example:

```json
{
  "attributes": [
    {
      "name": "gi_backend",
      "type": "enum:auto-ray-compute-disabled",
      "default_value": "auto"
    },
    {
      "name": "enable_gi",
      "type": "bool",
      "default_value": "true"
    }
  ],
  "backend": "gi_backend",
  "selection_profiles": {
    "auto": [
      {
        "if": "enable_gi && capability.ray_query && vr.layered",
        "use": "ray_layered_half"
      },
      {
        "if": "enable_gi && capability.ray_query",
        "use": "ray_per_view_half"
      },
      {
        "if": "enable_gi",
        "use": "compute_half"
      },
      {
        "use": "disabled"
      }
    ],
    "ray": [
      {
        "use": "ray_layered_half",
        "required": true
      }
    ],
    "compute": [
      {
        "use": "compute_half",
        "required": true
      }
    ],
    "disabled": [
      {
        "use": "disabled",
        "required": true
      }
    ],
    "buto": [
      {
        "if": "enable_gi",
        "use": "compute_half"
      },
      {
        "use": "disabled"
      }
    ]
  },
  "backends": {
    "ray_layered_half": {
      "backend": "ray_query",
      "vr": "view_agnostic",
      "resolution": "gi_half",
      "compute": "world/deferred_rt/gi_ray_query.comp"
    },
    "ray_per_view_half": {
      "backend": "ray_query",
      "vr": "per_view",
      "resolution": "gi_half",
      "compute": "world/deferred_rt/gi_ray_query.comp"
    },
    "compute_half": {
      "backend": "compute_3d",
      "vr": "view_agnostic",
      "resolution": "gi_half",
      "compute": "world/deferred_rt/gi_compute.comp"
    },
    "disabled": {
      "backend": "disabled"
    }
  }
}
```

Selection rule behavior:

- rules are evaluated in order,
- a missing `if` means always true,
- the first rule whose expression is true and whose backend candidate is available is selected,
- unavailable non-required candidates are skipped and recorded in the graph report,
- unavailable `required: true` candidates fail the runtime plan with a clear error,
- if no rule selects a backend, runtime plan construction fails,
- concrete backend entries cannot use profile names as backend types.

The expression language for selection rules is the same intentionally small language used by `enabled_if`, plus capability and VR booleans:

```text
capability.ray_query
capability.multiview
capability.half_precision
vr.enabled
vr.layered
vr.per_view
```

These values are plan-build inputs. They are not per-frame shader variables.

Backward compatibility:

- old `backend: auto` plus `backends` may be lowered into a generated profile,
- generated legacy profiles must be reported in the graph report,
- new Deferred built-in packs should use explicit `selection_profiles`,
- PostRender should later move to the same model while preserving old packs through lowering.

### 4.5 Reuse `vr`

Do not add a separate public `view_execution` unless the existing `vr` policy cannot express a real requirement.

Interpretation for Deferred:

| `vr` value | Deferred meaning |
|---|---|
| `view_agnostic` | Shader handles array layers or `z` dispatch dimension correctly. Preferred for compute/ray passes. |
| `per_view` | Runtime must execute once per eye/view. Compatible but more expensive. |
| `mono_replicate` | Allowed only for effects where mono result is semantically valid. Runtime may copy/replicate to layers. |
| `unsupported` | Backend cannot be selected when `eyeCount > 1`. |
| unspecified | Warning for internal bring-up, error in strict mode for external packs. |

For raster render passes, keep existing backend vocabulary:

- `native_stereo`,
- `multiview`,
- `graphics`,
- `auto`.

For compute and ray-style passes, use `vr` to choose:

- single layered dispatch for `view_agnostic`,
- per-eye dispatch for `per_view`,
- mono replication for `mono_replicate` only when explicitly allowed.

Important Deferred-specific rule:

`view_agnostic` does not mean mono and does not mean the effect can ignore view state. For Deferred compute/ray passes it means the shader is layer-aware and can correctly process all views in one layered dispatch. Internally, the runtime should lower this to an explicit execution mode such as `LayeredSingleDispatch` to avoid carrying the misleading public name into command-recording logic.

A shader that computes one mono image and copies it to both eyes must use `mono_replicate`, and only for effects where that is visually and semantically valid. A shader that cannot support VR must use `unsupported`.

### 4.6 Named resolution domains

Add public named resolution domains.

Example:

```json
{
  "resolutions": {
    "full": {
      "scale": 1.0,
      "round_to": [16, 16]
    },
    "gi": {
      "scale": "gi_scale",
      "round_to": [16, 16],
      "min": [1, 1]
    },
    "reflection": {
      "scale": "reflection_scale",
      "round_to": [16, 16],
      "min": [1, 1]
    }
  }
}
```

Passes and resources can reference a domain:

```json
{
  "resolution": "gi",
  "schedule": {
    "mode": "lighting_queue",
    "queue": "gi",
    "tile_size": 16
  }
}
```

Runtime requirement:

- pass dispatch dimensions come from the selected resolution domain,
- pack-owned runtime resources can use the same domain,
- queue builders can emit work for the same domain, planned for M3,
- push constants include both full resolution and dispatch resolution, completed in M2,
- shader definitions expose the active resolution domain, completed in M2,
- diagnostics report selected domain, scale and dispatch dimensions; graph-level reporting exists for selected domains,
  compact per-frame dimensions remain planned for the diagnostics milestone.

Resolution domains must define a coordinate contract. The runtime must provide the same data to every scaled pass instead of letting shaders guess:

```text
DeferredResolutionParams {
  uvec2 fullResolution
  uvec2 dispatchResolution
  vec2 scale
  vec2 invScale
  vec2 fullToDispatch
  vec2 dispatchToFull
  uint layerCount
  uint flags
}
```

Rules:

- `fullResolution` is the G-buffer/public-output resolution.
- `dispatchResolution` is the actual pass work domain.
- `scale` is `dispatchResolution / fullResolution`.
- `invScale` is `fullResolution / dispatchResolution`.
- `fullToDispatch` maps full-resolution pixel coordinates into dispatch-domain coordinates.
- `dispatchToFull` maps dispatch-domain pixel coordinates into full-resolution coordinates.
- `layerCount` is the active view/layer count.
- M2 uses pixel-center mapping for integer helper functions:
  - dispatch -> full: `floor((dispatchPixel + 0.5) * dispatchToFull)`, clamped to full bounds,
  - full -> dispatch: `floor((fullPixel + 0.5) * fullToDispatch)`, clamped to dispatch bounds.
- The initial depth/normal/material sampling policy for scaled dispatch shaders is nearest full-resolution G-buffer
  sampling through the dispatch -> full mapping.
- A scaled pass cannot write a public full-resolution output directly until it declares an explicit upsample or compose
  handoff. The runtime rejects that graph shape.

Scaled effects must also declare or inherit these policies:

- G-buffer/depth/normal sampling policy, initially `nearest`; the first runtime contract is implemented in M2.
- output upsample policy, initially explicit compose or a named upsample pass; still planned for M4/M8.
- history resolution, either `dispatch` or `full`; M2 blocks unsafe scaled public writes until explicit history and
  upsample ownership are available.
- queue domain, either the pass resolution domain, full resolution or an explicitly named resolution domain; implemented
  in M3 for lighting queues.

Open-ended shader-side guessing is not allowed. A half-resolution GI shader must know how to map its dispatch pixel to full-resolution depth/normal, where its history lives and who upsamples the result.

### 4.7 Scaled queue domains

Lighting queue schedule must support full-resolution and scaled domains.

Example:

```json
{
  "schedule": {
    "mode": "lighting_queue",
    "queue": {
      "name": "gi",
      "domain": "pass_resolution"
    }
  }
}
```

This must reduce launched workgroups. It must not be implemented as full-resolution dispatch with shader early returns.
The legacy string form, such as `"queue": "gi"`, remains valid and defaults to `full_resolution`.

### 4.8 Output default policy

When a pass is disabled or inactive, downstream public outputs still need defined values.

Add an output policy layer:

```json
{
  "output_defaults": {
    "first_hit_diffuse_indirect_light": "zero",
    "gi_hit_distance": "zero",
    "first_hit_specular": "zero",
    "specular_hit_depth": "zero",
    "fog_image": {
      "value": [0.0, 0.0, 0.0, -1.0]
    }
  }
}
```

Runtime requirement:

- defaults are applied only for outputs not fully written by an active pass,
- defaults must avoid full-screen shader work when possible,
- options may include:
  - lazy clear,
  - alias to a zero image,
  - targeted clear,
  - preserved value for history resources when valid.

Implementation status:

- Milestone M4 replaces the `clear_contract` compute pass in the normal runtime graph with native targeted
  `vkCmdClearColorImage` clears derived from the active Deferred fixed semantics.
- `clear_contract` remains a compatibility marker in the built-in pack, but it is recorded as an output-default marker;
  the runtime does not build a pipeline for it and does not dispatch it.
- Active full writers can suppress their default clear. Direct light is treated as a full writer for
  `first_hit_diffuse_direct_light`; reflection, GI and compose remain partial writers and keep defaults where their
  shaders can early-return or only write queued/eligible pixels.
- G-buffer outputs are treated as fully covered only when the current frame actually has a G-buffer view and opaque or
  cutout draw packets. Empty-scene frames still default-clear G-buffer outputs so stale previous-frame contents cannot
  leak downstream.
- Defaults are layer-count aware and use the same public-output default values as the old clear contract, including
  `normal_roughness = (0, 0, 0, 1)` and `fog_image = (0, 0, 0, -1)`.
- This is a conservative targeted-clear implementation. Zero-image aliasing, explicit scaled-output upsample ownership
  and public-output export pruning remain later optimization work.

---

## 5. Runtime Plan Model

The Deferred runtime should lower the shaderpack into an explicit active graph.

Each node should contain:

```text
RuntimeNode {
  publicName
  semantic
  enabled
  selectedBackend
  selectedProfile
  selectedVrPolicy
  resolutionDomain
  scheduleDomain
  inputResources
  outputResources
  requiredCapabilities
  runtimeRequirements
  diagnosticsPolicy
}
```

Runtime plan construction order:

1. Parse pack attributes and static configured values.
2. Evaluate `enabled_if`.
3. Expand slots, insertions and custom passes.
4. Select backend:
   - resolve the selected profile name from the pass attribute or literal,
   - evaluate the selected profile's rules in order,
   - skip unavailable non-required backend candidates,
   - fail unavailable required candidates,
   - record selection and skip reasons in the graph report.
5. Select resolution domain and validate dimensions.
6. Select dispatch schedule and queue domain.
7. Validate logical resources after disabled nodes are removed.
8. Determine output default policy for unwritten outputs.
9. Determine runtime requirements.
10. Build descriptor, barrier and pipeline data only for active nodes.

Implementation status:

- Milestone M5 adds a CPU-testable generated graph plan built from active Deferred runtime pass names, shaderpack pass
  declarations, fixed semantic resource rules and active queue domains.
- The graph plan records per-pass logical image/buffer reads and writes, fixed native resource intent, scene-set
  descriptor intent, no-op fixed passes and summary counts for graph diagnostics.
- `classify` and `build_lighting_queues` become no-op runtime passes when the active graph has no classification or
  lighting-queue consumers. They do not create pipelines and do not dispatch.
- Descriptor update work is now pruned for unused classification masks/stats, visibility masks, lighting queue buffers,
  fixed direct/reflection/GI uniforms and TLAS bindings, scene-set resources and pass stats bindings.
- Native resource allocation and initialization for classification masks, visibility masks, lighting queues and
  G-buffer render-pass/depth resources are skipped when the active graph does not need them.
- Compute/full-screen pass barriers are generated from the active graph pass resource intent instead of hard-coded
  semantic branches for queue build, reflection, GI and compose.
- Public-output aliases such as `gbuffer.depth` and `lighting.gi` are resolved to the module-owned public outputs when
  generating barriers, while custom runtime resources continue through the shaderpack runtime resource path.
- The Vulkan descriptor layout ABI intentionally remains fixed in M5; the generated descriptor plan prunes updates and
  bindings, not set-layout shape. G-buffer render passes still use dedicated render-pass pre/post barriers.

Important requirement:

The module-level `sceneRuntimeRequirements()` should not force every runtime dependency for every Deferred pipeline forever. Deferred should derive effective frame requirements from the active runtime plan.

Use fine-grained requirement flags instead of only `Provider` and `Acceleration`:

| Requirement | Trigger |
|---|---|
| `ProviderDrawStreams` | Any active render content that consumes scene draw packets, such as the G-buffer scene pass. |
| `AccelerationStructure` | Any active selected backend that binds TLAS or uses ray query. |
| `MaterialResources` | Any active G-buffer/material pass needing material buffers, texture ids or texture mapping. |
| `ViewData` | Any active pass needing current/previous view/projection data. |
| `WorldUniforms` | Any active pass needing world/camera/global scene uniforms. |
| `SkyUniforms` | Any active pass needing sky/light/environment data. |
| `HistoryRuntime` | Any active history resource or pass. |
| `VisibilityMask` | Any active pass or queue builder that consumes visibility masks. |

Rules:

- If the active graph contains a G-buffer scene render slot, provider draw streams are required.
- If the active graph selects a ray-query backend, acceleration is required.
- If a ray-style effect selects compute or disabled backend, acceleration is not required for that effect.
- If a pack completely omits scene rendering and only runs imported/input compute passes, provider draw streams are not required.
- If no active pass uses history resources, history runtime state and clears are not required.
- If no active queue or classification pass uses visibility masks, visibility-mask upload and barriers are not required.

The first version can still keep provider for the built-in G-buffer path, but it should stop requiring acceleration when no active ray-query backend exists.

---

## 6. Default Built-in Pack Changes

The built-in `vanilla-deferred-rt` should be updated to exercise the new public model.

### 6.1 Attributes

Add pack-owned attributes, not engine-owned quality tiers:

```json
[
  {
    "name": "use_rt_direct_light",
    "type": "bool",
    "default_value": "true"
  },
  {
    "name": "enable_reflection",
    "type": "bool",
    "default_value": "true"
  },
  {
    "name": "enable_gi",
    "type": "bool",
    "default_value": "true"
  },
  {
    "name": "reflection_scale",
    "type": "float_range:0.25-1.0",
    "default_value": "0.5"
  },
  {
    "name": "gi_scale",
    "type": "float_range:0.25-1.0",
    "default_value": "0.5"
  }
]
```

These are examples. The engine must not hard-code their names beyond forwarding static attributes and evaluating expressions.

### 6.2 Direct light

Current direct light is full-screen ray query. New built-in pack should offer:

- ray-query backend,
- compute backend,
- optional disabled backend.

The shaderpack should choose defaults through selection profiles. The conventional `auto` profile may prefer ray query when enabled and available, but that preference must live in the pack's rule list, not in C++ hidden priority.

### 6.3 Reflection

Reflection should use:

- `enabled_if`,
- `lighting_queue`,
- named resolution domain,
- `ray_query` / `compute_3d` / `disabled` backends.

### 6.4 GI

GI should use:

- `enabled_if`,
- named resolution domain,
- `lighting_queue`,
- `ray_query` / `compute_3d` / `disabled` backends,
- history resources only when the selected backend needs them.

### 6.5 Clear and compose

The built-in pack should stop depending on `clear_contract` as a blanket full-contract clear.

Replacement policy:

- G-buffer pass clears G-buffer attachments.
- Disabled lighting outputs use output defaults.
- Active passes write their own outputs.
- Compose reads only the resources required by the active graph, or uses specialization/defines generated from the active graph.

---

## 7. Diagnostics and Logging Redesign

### 7.1 Requirements

Diagnostics must support:

- per-pass GPU time,
- per-pass CPU command-recording time if useful,
- dispatch dimensions,
- selected backend,
- selected VR policy,
- selected resolution domain,
- queue record counts,
- active pass list,
- disabled pass list and reason,
- ray-query mode and ray-query-capable device state,
- optional shaderpack graph report,
- optional statistics such as classification counts and pass counters.

Diagnostics must not depend on console output.

Diagnostics must be effectively free when disabled:

- no timestamp queries,
- no stats buffers,
- no atomic counters,
- no GPU readbacks,
- no string formatting every frame,
- no file IO,
- no Java callback every frame.

### 7.2 Diagnostic sinks

Add explicit diagnostic sinks:

| Sink | Purpose | Default |
|---|---|---|
| Native snapshot ring buffer | Low overhead data handoff from native runtime. | Enabled only when diagnostics are requested. |
| Java/F3 text | User-visible compact status. | Existing debug surface, pulled on demand. |
| File trace | Longer development logs. | Off by default. |
| GPU markers | RenderDoc/Nsight pass labels. | Optional, low overhead if supported. |
| GPU timestamps | Accurate per-pass timings. | Off by default. |
| Stats readback | Classification/pass/queue counters. | Off by default or sampled. |

Console output should be used only for exceptional startup errors or developer builds, not per-frame runtime diagnostics.

Implementation status:

- Milestone M6 adds explicit module attributes for `diagnostics_graph_report`, `diagnostics_stats`,
  `diagnostics_gpu_timing`, `diagnostics_trace_file` and `diagnostics_log_interval`.
- The legacy `diagnostics_log` switch now enables graph-report compatibility only. It no longer implicitly enables
  shader atomic statistics.
- File trace writes compact snapshot lines only when `diagnostics_trace_file` is non-empty. The sink is off by default
  and does not collect the string-heavy graph report.

### 7.3 Native snapshot model

Diagnostics must be split into two layers:

- frame snapshot: compact per-frame data for F3/debug UI and lightweight profiling,
- graph report: string-heavy expanded shaderpack/runtime graph for shaderpack authoring and debugging.

Native runtime should store a compact POD-like snapshot per completed frame when frame diagnostics are enabled:

```text
DeferredRtDiagnosticsSnapshot {
  frameSerial
  frameIndex
  width
  height
  eyeCount
  activePassCount
  passSummaries[]
  queueSummaries[]
  selectedBackendFlags
  sceneRequirementFlags
  warningFlags
}

DeferredRtPassSummary {
  semantic
  passName
  enabled
  disabledReason
  backend
  vrPolicy
  resolutionWidth
  resolutionHeight
  layerCount
  dispatchX
  dispatchY
  dispatchZ
  gpuTimeNs
  cpuRecordTimeNs
}
```

Frame snapshots may contain only:

- integer ids,
- enums,
- counters,
- timestamps,
- dimensions,
- selected backend/profile ids,
- selected resolution ids,
- flags.

Frame snapshots must not contain:

- pass-name strings,
- resource-name strings,
- expanded graph strings,
- validation note strings,
- descriptor summaries,
- barrier summaries.

String-heavy data belongs in the graph report.

Implementation status:

- Milestone M6 exposes the latest native snapshot to Java as a fixed-order `long[]`.
- The snapshot contains only numeric fields: validity, sequence, flags, frame index, dimensions, view/draw counts,
  active graph counts, descriptor/barrier/resource counts, requirement flags, provider counters, visibility counters,
  optional stats counters, frame GPU time and up to eight compact pass timing slots.
- The pass timing slots store stable hashed pass ids and microseconds. Pass names remain in graph reports rather than
  per-frame snapshots.
- The Java debug text path formats this compact array directly. It calls the old string-heavy native overlay only when
  the snapshot reports that a graph report is available.

### 7.4 Graph report model

The graph report is generated on pack load/reload, attribute change or explicit developer request. It is not generated or formatted every frame.

The graph report should include:

- selected profile name per pass,
- backend candidates,
- selected backend,
- skipped candidates and reasons,
- disabled passes and reasons,
- active pass order,
- logical resource reads/writes,
- resource lifetime and resolution domains,
- descriptor binding summary,
- barrier summary,
- queue domains,
- validation warnings/errors,
- legacy schema lowering notes,
- unused resources or passes.

The graph report can be exported to file or exposed through a developer UI. It should not be part of the default frame loop.

Implementation status:

- Milestone M6 makes runtime-plan graph report collection optional. It is collected only when
  `diagnostics_graph_report` or the legacy `diagnostics_log` compatibility flag is enabled.
- The active graph still builds its CPU graph plan for correctness and command recording, but selected/skipped-candidate
  strings, graph report lines and formatted native overlays are not produced on the default path.

### 7.5 Java handoff

Preferred model:

- Java pulls the latest diagnostics snapshot through an explicit JNI call or existing diagnostics text path.
- Native never writes to Java console each frame.
- Native never calls back into Java from render command recording.
- F3/debug text formats a small subset of the snapshot.

If file tracing is needed:

- file path is an explicit module attribute or developer config,
- file sink opens lazily,
- writes are sampled or buffered,
- file sink is disabled by default.

### 7.6 Timing implementation

Add GPU timestamp support behind a diagnostics flag:

- create timestamp query pools per swapchain frame,
- write timestamps before and after active pass nodes,
- resolve/read back from previous completed frames,
- associate results with pass node ids,
- skip entirely when timing is disabled.

Do not mix required rendering resources with timing resources. Timing allocation should happen only when enabled.

Implementation status:

- Milestone M6 adds a lazy Vulkan timestamp query pool behind `diagnostics_gpu_timing`.
- When disabled, no query pool is allocated and no reset/timestamp/result commands run.
- When enabled, the runtime records a frame timing scope plus up to eight active Deferred pass timing scopes per frame
  and exposes the completed results in the compact snapshot.

### 7.7 Statistics implementation

Existing shader atomic counters should become optional:

- compile-time define when stats are enabled,
- or separate stats variants/passes,
- or runtime branch only if it avoids atomics when disabled.

Preferred first step:

- generate `RADIANCE_DEFERRED_ENABLE_STATS` definition only when diagnostics stats are enabled,
- stats shader code compiles out when disabled,
- stats buffers are not allocated when disabled,
- stats download commands are not recorded when disabled.

Implementation status:

- Milestone M6 separates graph report, stats and timing controls. `diagnostics_stats` is the only public switch that
  enables stats buffers, stats descriptors, shader atomics, clears and readbacks.
- Compact snapshots are still maintained without stats enabled, but stats fields are marked valid only after stats
  readback has completed for a frame.

---

## 8. Pipeline Baseline Optimization

The schema changes above let shaderpacks remove work. That is necessary but not sufficient. The same selected feature set can still be slow if the C++ pipeline keeps fixed full-screen clears, broad barriers, unnecessary descriptor work, excessive readbacks or duplicate scene work.

This section covers optimizations that should improve the cost of the same active graph.

### 8.1 Remove fixed full-contract clear cost

Current issue:

- `clear_contract` writes all public outputs every frame.
- This is useful for bring-up but expensive at 1440p and especially expensive for stereo layers.
- Many images are later overwritten or not consumed by the active graph.

Required direction:

- G-buffer render pass clears G-buffer attachments.
- Active passes own their written outputs.
- Output defaults handle unwritten public outputs.
- Do not clear outputs that an active pass fully overwrites.
- Do not clear outputs that are not connected to downstream consumers or debug views.
- Prefer zero/default image aliasing where valid.
- Use targeted clears only for outputs that truly need a concrete default image.

Completion standard:

- The active graph determines which outputs receive clears.
- The blanket full-screen clear of all sixteen public outputs is not in the default performance path.
- Diagnostics can report output default actions separately from normal rendering passes.

### 8.2 Compile out statistics and readbacks from the normal path

Current issue:

- Classification/pass/queue statistics are useful during development but should not be part of the steady-state render path.
- Per-pixel atomics and GPU-to-CPU readbacks can distort pass timing and frame pacing.

Required direction:

- Stats buffers are allocated only when diagnostics stats are enabled.
- Stats shader code is compiled out or moved to stats variants when disabled.
- Stats clear/download commands are not recorded when disabled.
- Queue overflow counters and classification counters are sampled only when requested.
- File/log formatting is never performed from the render path unless the corresponding sink is enabled.

Completion standard:

- Diagnostics disabled means no stats buffers, atomics, staging copies, readback barriers or per-frame string formatting.

### 8.3 Generate a barrier plan instead of hand-written semantic barriers

Current issue:

- Deferred command recording contains many semantic-specific barrier helpers.
- As the active graph becomes configurable, hand-written barriers will over-synchronize inactive resources or miss new resource paths.

Required direction:

- Generate barriers from active node resource reads/writes.
- Merge adjacent compatible image and buffer barriers.
- Do not barrier inactive resources.
- Do not transition entire images when a narrower subresource range is known and useful.
- Keep public output layout compatibility with downstream modules.
- Keep manually audited escape hatches only for hard Vulkan constraints, and report them in graph diagnostics.

Completion standard:

- Adding or disabling a pass changes the generated barrier plan without adding semantic-specific C++ barrier branches.
- Graph report can show the generated barrier summary.

### 8.4 Reduce descriptor update work

Current issue:

- Descriptor binding is currently broad and tied to fixed module resources.
- Inactive backends should not force descriptor writes for TLAS, queue buffers, stats buffers or unused runtime images.

Required direction:

- Static image/sampler descriptors are written at build/rebuild time.
- Per-frame descriptors are updated only for resources that change per frame.
- Active graph determines which descriptor sets and bindings are needed.
- Inactive backend descriptors are omitted or bound to explicit safe defaults only when the selected pipeline layout requires them.
- Runtime resource rebuild updates only affected sets.

Completion standard:

- A compute-only profile does not bind TLAS descriptors.
- A stats-disabled profile does not bind stats descriptors.
- Disabled lighting queues do not bind unused queue buffers except where required by a shared layout and safe default.

### 8.5 Make scene runtime requirements precise

Current issue:

- SharedSceneRuntime avoids duplicate scene preparation, but Deferred can still request more scene state than the active graph needs.

Required direction:

- Use the fine-grained runtime requirements from Section 5.
- Skip acceleration prepare/bind/barriers when no active ray-query backend exists.
- Skip provider packet enumeration when no active scene draw pass exists.
- Skip material/texture mapping uploads when no active material pass needs them.
- Skip visibility-mask uploads when no active pass consumes them.

Completion standard:

- Runtime diagnostics can explain why each scene subsystem was or was not used for the current active graph.

### 8.6 Optimize G-buffer draw data and scene packet upload

Current issue:

- G-buffer setup uploads view and draw data each frame.
- Static chunk data and dynamic entity data have different update lifetimes.

Required direction:

- Upload only active/renderable packets.
- Separate static chunk packet metadata from dynamic entity packet metadata where practical.
- Cache stable packet ranges across frames and invalidate on scene changes.
- Keep previous-transform data localized to dynamic objects when possible.
- Avoid scanning vertex data to rebuild draw packet facts.

Completion standard:

- CPU packet enumeration and GPU upload scale with changed/active scene data rather than blindly with all retained data.

### 8.7 Keep G-buffer state changes and bandwidth under control

Required direction:

- Batch opaque and cutout packets to minimize pipeline and descriptor changes.
- Keep alpha-tested/cutout behavior explicit.
- Review internal G-buffer formats separately from public output formats.
- Delay or avoid public export writes when a downstream module does not consume that output.
- Avoid writing both `primary_depth` and `primary_linear_depth` if the active path can prove one valid shared representation, while preserving public contract semantics when they diverge.

Completion standard:

- Internal G-buffer formats and public export formats are documented separately.
- Downstream compatibility does not force every internal pass to write wide public-format images.

### 8.8 Make direct lighting scheduling less wasteful

Current issue:

- Built-in `direct_light` is full-screen.
- Even when ray query is enabled, not every pixel needs a direct-light ray.

Required direction:

- Support a direct-light queue or tile schedule in addition to screen schedule.
- Use classification, visibility mask and simple lighting tests to skip sky, invalid, hidden-area and back-facing pixels before ray query.
- Permit compute fallback and disabled backend through selection profiles.
- Report direct-light candidate counts and dispatch dimensions in diagnostics.

Completion standard:

- Direct ray-query work can be made proportional to eligible pixels/tiles rather than always full-screen.

### 8.9 Improve queue builder and queue granularity

Current issue:

- Lighting queues are indirect, but tile granularity can still launch work over many pixels that early-return.
- Queue building and queue stats should not update inactive queues.

Required direction:

- Build only active queues.
- Initialize only active queue headers and indirect args.
- Make queue record capacity match the active resolution domain.
- Add stricter material/roughness/depth eligibility before queue emission.
- Consider checkerboard or smaller work units for reflection/GI after baseline queue scaling is correct.
- Keep overflow and queue counters diagnostics-gated.

Completion standard:

- Disabled queues issue zero dispatch and do not record queue setup work.
- Scaled queues reduce indirect workgroup count, not just shader work inside the group.

### 8.10 Generate compose variants from the active graph

Current issue:

- Compose reads many potential lighting outputs even when some effects are disabled or not selected.

Required direction:

- Generate compose definitions from active graph features.
- Do not read GI, reflection, fog, refraction or clear-layer outputs when the active graph cannot write them.
- Provide a lightweight direct-only or ambient-only compose path.
- Keep output defaults compatible with the selected compose variant.

Completion standard:

- Disabling GI/reflection removes their compose reads as well as their pass dispatches.

### 8.11 Delay public output export when possible

Required direction:

- Keep internal resources separate from public contract exports.
- Export public outputs only when a downstream module, debug view or graph edge requires them.
- Preserve explicit public contract semantics for ToneMapping, PostRender, NRD, upscalers and temporal modules.
- Avoid writing debug-only public outputs in normal mode.

Completion standard:

- A minimal Deferred pipeline writes only the outputs required by its downstream graph plus required defaults.

### 8.12 Prefer layered/multiview VR paths and report fallback

Required direction:

- Compute/ray passes should prefer layer-aware single dispatch when the selected backend declares `vr: view_agnostic`.
- Raster passes should prefer multiview when available and declared.
- Per-eye execution is a compatibility fallback, not an invisible default.
- Diagnostics must report pass-level VR execution mode and fallback reason.

Completion standard:

- VR performance regressions caused by per-eye fallback are visible in frame snapshots and graph reports.

### 8.13 Use visibility mask before expensive work

Required direction:

- OpenXR visibility mask should influence classification and queue emission.
- Hidden-area pixels should be removed before expensive lighting dispatch where possible.
- Visibility-mask upload and barriers should be skipped when no active pass consumes it.

Completion standard:

- Hidden-area work is reduced in queue counts and dispatch dimensions where the schedule permits it.

### 8.14 Add pass timing before deep shader work

GPU timing is required to evaluate all optimization work.

Timing scopes should include at least:

- scene prepare,
- provider packet enumeration,
- G-buffer,
- classification,
- queue build,
- direct lighting,
- reflection,
- GI,
- compose,
- output defaults/clears,
- public export,
- downstream handoff.

Completion standard:

- The developer can compare the same shaderpack setting before and after a C++ pipeline optimization using per-pass GPU times.

---

## 9. C++ Implementation Work

### Step A: Runtime graph selection

Files likely involved:

- `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.hpp`
- `MCVR-custom/src/core/render/modules/world/shader_pack/shader_pack.cpp`
- `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.hpp`
- `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_shaderpack.cpp`
- `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.hpp`
- `MCVR-custom/src/core/render/modules/world/deferred_rt/deferred_rt_module.cpp`

Tasks:

- parse `enabled_if`,
- evaluate the intentionally small static expression language from attributes, capability booleans and VR booleans,
- add `disabled` backend,
- add Deferred backend options for ray-style slots,
- add pack-defined selection profiles,
- preserve old `backend` / `backends` / `vr` through lowering,
- build an active runtime node list,
- report disabled reasons.

Tests:

- pass disabled by bool attribute,
- pass disabled by enum/float expression,
- active pass keeps graph resources,
- inactive pass does not allocate pipelines,
- unavailable requested backend errors,
- selected profile can be named `auto`, `buto` or any other legal identifier,
- profile rule order is respected,
- unavailable non-required candidates are skipped,
- unavailable required candidates fail,
- legacy `backend: auto` lowers into a generated profile.

### Step B: Dynamic scene requirements

Tasks:

- compute fine-grained runtime requirements from the active runtime plan,
- request provider draw streams only if active render content needs scene draw packets,
- request acceleration only if an active selected backend needs TLAS,
- request material resources only if an active pass uses material buffers or texture mapping,
- request history runtime only if active resources/passes need history,
- request visibility mask only if active passes use visibility masks,
- skip TLAS binding and ray-query barriers when no active ray-query backend exists.

Tests:

- compute-only pack does not request acceleration,
- disabled lighting does not request acceleration,
- ray-query selected requests acceleration,
- G-buffer scene rendering requests provider draw streams,
- imported/input compute-only pack does not request provider draw streams,
- no active history pass allocates no history diagnostics/reset work.

### Step C: Resolution domains

Implementation status:

- Milestone M1 implements the first named-domain layer:
  - root `resolutions` parsing with built-in `full`, `half` and `quarter` domains,
  - shaderpack-defined domain overrides and custom names,
  - scale expression evaluation, minimum dimensions and round-up alignment,
  - `resolution` references on Deferred full-screen, render, compute and ray-query passes,
  - `resolution` references on pack-owned runtime images,
  - active runtime-plan validation for pass/resource domain references,
  - active runtime-plan diagnostics that record selected concrete pass -> resolution domain,
  - actual reduced dispatch dimensions for `screen` and `tile_grid` compute schedules,
  - runtime image allocation from the referenced domain.
- Built-in `vanilla-deferred-rt` declares `full`, `half` and `quarter`; all built-in passes still default to `full`
  until the coordinate contract and queue scaling milestones land.
- Milestone M2 implements the first coordinate contract:
  - a unified Deferred compute/ray push constant layout with full resolution, dispatch resolution, scale, inverse scale,
    full/dispatch mapping factors, layer count and flags,
  - shared shader helpers for dispatch bounds and pixel-center full/dispatch pixel mapping,
  - active resolution-domain shader definitions,
  - runtime validation that rejects scaled passes writing public full-resolution outputs without an explicit upsample
    path.

Tasks:

- parse `resolutions`, completed in M1,
- evaluate scale expressions, completed in M1,
- round dimensions, completed in M1,
- expose full and dispatch dimensions plus mapping constants in push constants, completed in M2,
- allocate pack-owned resources from resolution domains, completed in M1,
- add generated shader definitions for resolution domains, completed in M2,
- support full/half/quarter lighting target resources.

Tests:

- half scale with alignment produces the expected rounded dispatch extent, covered in M1,
- quarter scale with minimum dimensions produces the expected dispatch extent, covered in M1,
- invalid zero/negative scale errors, covered in M1,
- resource and pass using the same domain agree at schema/runtime-plan level, covered in M1,
- unknown pass/resource domains fail runtime-plan validation, covered in M1,
- full-to-dispatch and dispatch-to-full mapping constants are deterministic, covered in M2,
- history resolution policy is explicit for scaled history resources, planned after the queue/default-output milestones;
  M2 blocks graph shapes that would otherwise need this policy for public outputs.

### Step D: Scaled queues

Implementation status:

- Milestone M3 implements scaled lighting queue domains:
  - `schedule.queue` now accepts either the legacy string form or an object with `name` and `domain`,
  - `domain: "full_resolution"` maps to the full-resolution G-buffer domain,
  - `domain: "pass_resolution"` maps to the selected concrete pass resolution domain,
  - any other valid domain name is resolved against the shaderpack `resolutions` table,
  - active runtime plans record one active domain per queue kind and reject unknown or conflicting domains,
  - inactive or unselected backend candidates do not contribute queue domains, resource capacity or indirect work,
  - lighting queue headers carry full/dispatch extents, full/dispatch scale factors and enabled flags,
  - queue record buffer capacity is computed only for active queues and their selected dispatch domains,
  - the `build_lighting_queues` dispatch uses the maximum active queue dispatch extent instead of the full-resolution
    pass extent,
  - disabled queue headers have zero capacity and the build shader skips emission for them,
  - queued reflection/GI shaders map queued dispatch pixels back to full-resolution G-buffer pixels before sampling.
- Built-in reflection/GI public output passes still default to full pass resolution because explicit upsample/default
  ownership is not implemented yet. M3 enables scaled queue and indirect work; it does not enable unsafe scaled public
  output writes.

Tasks:

- extend lighting queue metadata to include resolution domain, completed in M3,
- build queue headers/records for selected domain, completed in M3,
- make indirect args reflect scaled dispatch count, completed in M3,
- update built-in queue shader or add variants for scaled queue generation, completed in M3,
- pass full-to-dispatch coordinate mapping to shaders, completed in M3.

Tests:

- full-res queue emits expected tile count, covered by existing queue tests,
- half/quarter queue domains have smaller record capacity, covered in M3,
- inactive queues have zero capacity and disabled flags, covered in M3,
- indirect dispatch offsets remain correct, covered by existing queue tests,
- queue object schema, unknown queue domains and conflicting active queue domains are covered in M3 runtime/schema tests.

### Step E: Output default policy and clear optimization

Implementation status:

- Milestone M4 implements the first native output-default plan:
  - active fixed Deferred semantics are recorded in the runtime plan,
  - `clear_contract` is lowered to an output-default marker instead of a runtime dispatch pass,
  - selected logical aliases that point at `clear_contract` are skipped during pipeline registration and execution,
  - a CPU-testable output-coverage helper classifies fixed semantics as full, partial or no writer,
  - the module records native targeted clears before runtime passes for outputs that are unwritten or only partially
    written,
  - the default plan is frame-aware for G-buffer outputs, so an active G-buffer semantic only suppresses G-buffer
    defaults when the current frame actually records G-buffer draws,
  - `compose` is intentionally partial because it returns without writing `radiance` when primary depth is invalid.

Tasks:

- identify active writers for public outputs, completed in M4 for fixed Deferred semantics,
- apply defaults only for unwritten or partial-writer outputs, completed in M4,
- stop running blanket `clear_contract` for all outputs by default, completed in M4,
- skip clears for active pass outputs that are fully overwritten, completed in M4 for direct-light and active
  G-buffer frames,
- prefer safe zero/default image aliasing where valid, planned,
- keep compatibility with downstream ToneMapping/PostRender/NRD/upscalers.

Tests:

- disabled GI writes or clears `gi_hit_distance` and indirect diffuse to valid defaults, covered by output-default
  coverage tests,
- disabled reflection writes or clears specular outputs, covered by output-default coverage tests,
- active full-writer output is not overwritten by default clear, covered for direct light and G-buffer coverage,
- outputs remain layer-count correct, covered in command recording through active-layer clear ranges; GPU validation
  remains a later offline/runtime test item.

### Step F: Pipeline baseline optimization

Implementation status:

- Milestone M5 implements the first generated graph/barrier/descriptor plan:
  - active runtime pass names are expanded into per-pass logical resource intent,
  - fixed Deferred semantics add native resource reads/writes for G-buffer outputs, classification/visibility masks,
    lighting queues, direct/reflection/GI uniforms and public outputs,
  - declared aliases such as `gbuffer.*`, `lighting.*`, `classification.*`, `visibility.*`, `queue.*` and `scene.*`
    contribute to descriptor and barrier intent,
  - unused `classify` and `build_lighting_queues` fixed passes become no-op graph nodes,
  - no-op graph nodes are skipped during pipeline creation and runtime execution,
  - descriptor updates, initialization and per-pass generated barriers are skipped for inactive resources,
  - graph reports include compact graph-plan summaries and no-op reasons.
- The implementation keeps a fixed Vulkan descriptor layout for compatibility. M5 removes unnecessary descriptor writes,
  uploads, clears, barriers, allocation and dispatch work where possible; it does not yet generate a new pipeline layout
  ABI per active graph.
- G-buffer render pass layout transitions remain dedicated render-pass pre/post barriers. Compute and full-screen
  compute passes use the generated graph pass barrier path.

Tasks:

- remove stats buffers, atomics and readbacks from the default path,
- generate barrier plans from active graph resource reads/writes, first compute/full-screen pass version completed in M5,
- merge compatible barriers, planned,
- skip descriptor writes and bindings for inactive backends/resources, first fixed-layout pruning completed in M5,
- derive fine-grained scene runtime requirements from active graph,
- skip TLAS binding/barriers when no ray-query backend is active,
- skip provider packet enumeration when no scene draw stream is active,
- cache or incrementally update G-buffer draw packet metadata where practical,
- review G-buffer internal formats separately from public output formats,
- add direct-light queue/tile scheduling support,
- build only active lighting queues,
- generate compose variants from active graph feature flags,
- delay public output export where downstream graph allows it,
- prefer layered/multiview VR execution and report per-eye fallback.

Tests:

- stats disabled path records no stats resources or readbacks,
- inactive backend descriptors are not updated or are bound only to explicit safe defaults, covered in M5 graph-plan tests
  for fixed and scene descriptors,
- disabling ray-query backends removes TLAS binding and ray-query barriers, covered in M5 graph-plan tests for TLAS
  descriptor intent,
- disabling scene render content removes provider draw-stream requirement,
- direct-light queue profile dispatches less work than full-screen profile on sparse scenes,
- compose variant omits disabled effect reads,
- VR fallback mode appears in diagnostics.

### Step G: Diagnostics transport

Implementation status:

- Milestone M6 implements compact native diagnostics snapshots and a Java-visible `long[]` pull path.
- The old string overlay is retained as the graph-report path and is skipped unless graph reporting is enabled.
- Optional file trace, optional stats and optional GPU timestamp timing scopes are controlled independently.
- The default diagnostics path does not allocate timestamp/stat buffers, record stats readbacks, perform file IO, or
  build per-frame graph-report strings.

Tasks:

- replace console-oriented per-frame logging with native diagnostics snapshots, completed in M6,
- add Java pull path or reuse existing diagnostics text bridge, completed in M6,
- keep file trace optional, completed in M6,
- add GPU timestamp infrastructure behind a flag, completed in M6 for frame and fixed-slot pass timings,
- compile out or skip stats when disabled, completed in M6.

Tests:

- diagnostics disabled allocates no timestamp/stat buffers,
- diagnostics disabled records no readbacks,
- diagnostics enabled produces latest snapshot, covered by diagnostics tests and Java pull-path build coverage,
- file sink off performs no file IO, covered by the explicit trace-path gate,
- F3 text can obtain selected backend/resolution/pass summary, implemented through compact Java formatting and optional
  graph report text.

### Step H: Built-in pack migration

Tasks:

- update `vanilla-deferred-rt/configs.json` to use attributes, backends, `enabled_if`, `vr` and resolution domains,
- add explicit selection profiles for direct light, reflection and GI,
- provide compute-only backend shaders for direct/reflection/GI where needed,
- keep ray-query shaders as high-quality options,
- add disabled backends where valid,
- update compose to match output default policy and active graph definitions.

Tests:

- built-in default renders,
- built-in with ray query disabled renders,
- built-in with GI disabled renders,
- built-in with reflection disabled renders,
- built-in half-res GI renders and dispatches fewer workgroups.

---

## 10. Completion Standards

The redesign is complete when:

1. A shaderpack can disable GI and the runtime records no GI dispatch.
2. A shaderpack can disable reflection and the runtime records no reflection dispatch.
3. A shaderpack can force compute fallback even on ray-query-capable hardware.
4. A shaderpack can set GI or reflection to half/quarter resolution and the runtime launches fewer workgroups.
5. A shaderpack can define a selection profile named `auto`, `buto`, `cuto` or any legal identifier, and runtime follows that profile's rule order.
6. A shaderpack can provide a VR-compatible backend profile deterministically without relying on hidden engine priority.
7. A compute-only Deferred pack does not require TLAS acceleration.
8. A pack without active scene render content does not require provider draw streams.
9. Diagnostics disabled means no per-frame console output, file IO, stats atomics, stats readback or timestamp query work.
10. Diagnostics enabled exposes compact pass timing and selected backend/resolution through a JVM-safe frame snapshot.
11. Graph report export contains string-heavy pass/resource/selection details only on pack reload, attribute change or explicit request.
12. The built-in pack uses the same public schema as external packs.
13. The blanket full-contract clear is not part of the default performance path.
14. Stats/readback code is absent from the normal path unless diagnostics are enabled.
15. Barrier and descriptor work are generated from the active graph and skip inactive resources.
16. Direct-light, reflection and GI scheduling can reduce launched work, not only shader-side work.
17. Compose reads only resources required by the active graph variant.
18. CPU tests cover graph selection, backend profile selection, VR filtering, resolution domains, queue scaling, output defaults, baseline optimization gates and diagnostics gating.

---

## 11. Recommended Immediate Milestone

The first practical milestone should be the graph-culling and diagnostics foundation:

```text
enabled_if
  -> disabled backend
  -> pack-defined backend selection profiles choose ray_query / compute_3d / disabled
  -> active runtime node list
  -> no pipeline/dispatch/barrier/stat for inactive nodes
  -> compact diagnostics snapshot reports active graph ids without console IO
  -> graph report explains selected/skipped candidates when requested
```

Do not start by implementing half-resolution ray query. That work depends on the same runtime node and diagnostics model. Once pass culling and backend selection are correct, resolution domains and scaled queues can be added with clear measurements.

### 11.1 Implementation Status

Implemented in the current native milestone:

- Deferred pass `enabled_if` is parsed and evaluated only while building the runtime plan.
- The expression language is deliberately small: bool attributes, enum equality/inequality, numeric comparisons,
  `&&`, `||`, `!` and parentheses. Unknown static tokens are runtime-plan errors instead of silently evaluating false.
- Deferred selection groups support pack-defined profile names. `auto` is only a default selector string when no selector
  is specified; selecting an undefined profile fails instead of falling back to hidden engine policy.
- `ray_query`, `compute_3d` and `disabled` are first-class Deferred backend choices.
- Active runtime pass names contain only selected concrete passes. Disabled logical passes remain valid execution entries
  but are skipped without pipeline creation or dispatch.
- Runtime resource descriptor definitions and runtime resource allocation are scoped to active Deferred pass names before
  descriptor-table and pipeline creation.
- Deferred runtime requirements are derived from the active plan: acceleration is requested only for selected ray-query
  backends, and provider draw streams are requested only when active scene draw content needs them.
- Built-in `vanilla-deferred-rt` exposes `direct_light_backend`, `reflection_backend` and `gi_backend` attributes with
  `auto`, `ray_query`, `compute_3d` and `disabled` profiles.
- Shaderpacks can declare named resolution domains. Passes and pack-owned runtime images can reference those domains,
  and active `screen` / `tile_grid` compute dispatches use the reduced domain dimensions instead of full-resolution
  launches.
- Deferred compute/ray shaders receive a unified resolution push constant block with full and dispatch dimensions plus
  deterministic pixel-center mapping helpers.
- Lighting queue schedules can bind queue work to `full_resolution`, `pass_resolution` or a named resolution domain.
  Active queue headers, record capacity, the queue-builder tile dispatch and queued reflection/GI full-resolution
  G-buffer pixel mapping are derived from that active queue domain.
- `clear_contract` is now a compatibility/output-default marker instead of a normal compute dispatch in the active
  runtime path. Native targeted clears initialize only outputs that are unwritten or only partially written by active
  fixed semantics, with G-buffer coverage checked against the current frame's draw availability.
- Per-frame console diagnostics output has been removed from Deferred RT. The JVM-facing diagnostics path now pulls a
  compact native `long[]` snapshot and formats it in Java.
- Graph-report strings are collected only when the graph-report compatibility switch is enabled. The old string overlay
  is a heavy report path, not the default frame snapshot.
- Stats buffers, stats descriptor binding, atomics, clears and readbacks are gated behind `diagnostics_stats` and the
  compile-time shader definition `RADIANCE_DEFERRED_ENABLE_STATS`.
- Optional file trace writes compact snapshot lines only when `diagnostics_trace_file` is configured.
- Optional GPU timestamp timing records a frame scope plus fixed compact pass scopes only when
  `diagnostics_gpu_timing` is enabled.

Still planned after these milestones:

- Unified resolution-coordinate push constants, depth/normal sampling policy, upsample ownership, history-resolution
  policy, motion-vector reprojection coordinates and queue coordinate rules. The push constant ABI, initial nearest
  full-resolution G-buffer sampling contract, queue dispatch/full pixel mapping and compact full-resolution diagnostics
  dimensions are complete; upsample ownership, rich history policy and motion-vector reprojection remain.
- Zero-image aliasing, explicit scaled-output upsample ownership and delayed public-output export.
- Generated barrier and descriptor plans for all active compute/full-screen graph resources; render-pass G-buffer
  barriers, barrier merging and descriptor-layout ABI generation remain planned.
- More detailed named/per-pass timing report exports beyond the current compact fixed-slot pass timing fields.
- Compose variants and delayed public-output export.
