# Radiance 26.2 Current Status And Clean Migration Plan

This document records the current 26.2 state, the remaining work before Radiance
can run as a real peer Blaze3D backend, and the rules for migrating without
leaving legacy renderer-replacement behavior behind.

## Current Truth

The 26.2 build is now an active build path, not only a staged source tree.

Verified:

| Area | Current state |
|---|---|
| Minecraft version | `26.2` |
| Java | `25` |
| Gradle wrapper | `9.5.0` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.152.2+26.2` |
| Loom | `1.16.1`, matching the proven Sodium 26.2 line |
| Mappings | local identity Tiny mapping, because 26.2 has no Yarn mapping and forced official mappings fail in Loom |
| Active mixin config | `radiance-26_2.mixins.json` only |
| Legacy mixin config | kept in source for ledger verification, excluded from the 26.2 jar |
| Active Java source | minimal common mod entrypoint, Fabric loader adapter, and `src/minecraft26_2/java` |
| Legacy Java source | kept in repo as migration reference, excluded from active 26.2 compile |
| Output jar | `build/libs/Radiance-0.1.5-alpha-fabric-26.2.jar` |

Passing commands:

```powershell
.\gradlew.bat --no-daemon processResources verifyMinecraft26_2Migration compileJava remapJar --rerun-tasks
```

Known non-blocking warning:

```text
(2.5) is not valid semver for dependency org.yaml:snakeyaml:2.5
```

This comes from Fabric jar-in-jar metadata. It does not block `remapJar`, but it
should be cleaned later by replacing the embedded dependency metadata strategy or
relocating/parsing policy JSON without jar-in-jar if needed.

## Runtime Status

The default 26.2 client path is proven runnable with the vanilla backend selected.

Verified:

| Check | Current result |
|---|---|
| Fabric metadata parse | only the 26.2 mixin config is active; the empty 26.2 access widener is no longer loaded at runtime |
| Mixin apply | all active 26.2 mixin targets resolve |
| Client entrypoint | `com.radiance.loader.fabric.RadianceFabricClient` initializes |
| Default backend | vanilla backend remains selected unless Radiance opt-in property is set |
| Common bootstrap | `run/radiance` is created and `shaders`, `modules`, `core.dll`, `core.lib`, and XeSS DLLs are copied |

Default runtime command:

```powershell
.\gradlew.bat --no-daemon runClient --info
```

Current opt-in Radiance backend status:

```powershell
$env:JAVA_TOOL_OPTIONS='-Dradiance.blaze3d.backend=true'
.\gradlew.bat --no-daemon runClient --info
```

The opt-in path selects the Radiance backend, creates the Java capture backend,
loads `libxess.dll`, loads `core.dll`, reaches surface acquire/present, and then
stops at the expected native ABI gap:

```text
Radiance native surface presentation is unavailable: core native library does not export the 26.2 backend device ABI:
'long com.radiance.client.render.blaze3d.backend.RadianceNativeBackendBridge.createDeviceNative(java.lang.String, long)'
```

That means the next blocker is no longer Fabric metadata, access widener loading,
mixin application, bootstrap copying, or DLL dependency order. The next blocker is
adding the new 26.2 backend JNI exports to the native `core` library.

Required new native entrypoints are declared in:

```text
src/main/native/include/com_radiance_client_render_blaze3d_backend_RadianceNativeBackendBridge.h
```

Current runtime boundary:

| Path | Current result |
|---|---|
| Default backend | runnable |
| Opt-in Radiance backend | reaches first frame present and fails only because native 26.2 backend ABI is absent |
| Old proxy native ABI | still present in headers/resources, but not called by active 26.2 code |

## Why The Old Mod Logic Cannot Be Carried Forward

The old 1.21.x Radiance path was a renderer replacement and extraction system
built around Minecraft internals before the 26.2 Blaze3D backend split.

Old responsibilities:

| Old component | What it did | 26.2 replacement direction |
|---|---|---|
| `RendererProxy` | Global native renderer lifecycle, frame orchestration, old render replacement entrypoints, module dispatch | Common bootstrap plus a real `GpuBackend`/`GpuDeviceBackend`; native execution receives backend/shared-scene snapshots |
| `BufferProxy` | Native buffer handles and uploads outside Blaze3D's resource model | `GpuBuffer`/`CommandEncoderBackend` capture and native buffer payload ABI |
| `TextureProxy`/`TextureTracker` | GL id and texture object tracking, auxiliary sidecar texture binding | Backend texture handles, resource ids, atlas/sprite/material registries, no GL id assumptions |
| `EntityProxy` | Direct entity mesh/state packets from old renderer mixins | 26.2 submit/extraction observers, entity frame records, draw tags, later draw-to-entity linkage |
| `ChunkProxy` | Chunk rebuild capture and native chunk mesh packets | Backend draw capture, `ChunkSection` dynamic uniform decoding, shared-scene filtered views |
| Old `vulkan_render_integration` mixins | Replaced or short-circuited large chunks of vanilla rendering | 26.2 mixins are shims only; backend draw stream is the compatibility surface |
| Old options/UI mixins | Fabric/Yarn-specific screen and option patches | Loader adapters and 26.2 Mojang-named UI code, outside backend/mixin core |

Keeping old logic in the active 26.2 path would create hidden bugs:

- It depends on Yarn names that do not exist in the 26.2 build path.
- It depends on GL ids, but Radiance must be a backend beside GL/Vulkan, not a GL
  observer.
- It bypasses the new Blaze3D resource and command model, so mods using plain
  Blaze3D would be invisible or misclassified.
- It mixes loader-specific code into core paths, blocking future Fabric/NeoForge
  compatibility.
- It duplicates responsibility between old proxy packets and new shared-scene
  records, making it impossible to prove which data path is authoritative.

The clean rule is: old code is reference material only. It can be mined for
business intent, but not called from new 26.2 mixins or backend code.

## Clean Migration Boundaries

### Mixin Boundary

26.2 mixins must remain loader-neutral shims.

Allowed:

- observe 26.2 method parameters;
- call `RadianceBlaze3DHooks` or other client services;
- scope semantic tags around submit/draw phases;
- forward resource reload/texture/atlas/glyph events.

Forbidden:

- Fabric/NeoForge/modloader API;
- native calls;
- renderer policy;
- old proxy calls;
- GL id logic;
- direct module execution.

Machine check:

```powershell
.\gradlew.bat --no-daemon verifyMinecraft26_2Migration
```

### Loader Boundary

Loader-specific code belongs under loader adapter packages, such as:

```text
com.radiance.loader.fabric
```

Future NeoForge code should have an equivalent adapter. Both adapters should call
shared common bootstrap services and should not change backend capture semantics.

### Backend Boundary

Radiance must become a peer backend with the same conceptual surface as GL/Vulkan:

- backend selection;
- device/surface creation;
- buffer/texture/sampler/query/fence ownership;
- command encoder execution;
- render pass draw stream;
- pipeline compilation/cache;
- debug/device info;
- present/swapchain behavior.

The current Java backend skeleton records calls and snapshots but does not yet
execute native GPU work. That is intentional while the native ABI is unsettled.

### Shared Scene Boundary

Shared scene is the bridge between Java Blaze3D capture and native render
modules. It must own canonical records and expose filtered views.

Important rule:

- PT/ray-query acceleration views are strict whitelist views.
- Raster geometry should be conservative and compatible; unknown Blaze3D draws
  should remain raster-visible unless explicitly excluded.
- DH LOD is raster-visible but excluded from acceleration by default.
- Post/UI/debug have their own views and must not leak into world acceleration.

The policy JSON has two independent jobs:

| Part | Job |
|---|---|
| caller/call rules | detect caller/object/pass hints and inject tags into captured calls |
| view rules | define which tags each backend/native view includes or excludes |

Runtime filtering must use compact tag ids/bitsets, not repeated string matching.

## Current Remaining Gaps

| Priority | Gap | Why it matters | Done when |
|---|---|---|---|
| Done | Startup validation | Build success is not runtime success | `runClient` reaches client startup with default vanilla backend and no AW/mixin errors |
| Done | Native bootstrap split | Old `RadianceClient` copied native libs, shaders, modules, loaded options/pipeline | loader adapter calls a common bootstrap that performs required resource/native setup without old renderer proxies |
| P0 | Native 26.2 backend JNI ABI | `core.dll` loads, but it only exposes the old proxy ABI | native exports from `RadianceNativeBackendBridge.h` exist and `tryCreateDevice` returns a real device handle |
| P0 | Native backend execution | Current Radiance backend records packets but does not present or execute GPU commands | opt-in Radiance backend creates native device/surface, executes command encoder work, and presents |
| P0 | Native shared-scene ABI | Java snapshots are self-describing but native shared_scene does not ingest them yet | native side consumes frame/resource/entity/world/material dictionaries and builds requested views |
| P1 | Texture/buffer payload ABI | Java records operation metadata but native needs real upload/readback data | buffer and texture uploads/copies have stable native payload descriptors |
| P1 | Resource/PBR native ingestion | Java exports atlas/sprite/image/glyph/material tables | native material system consumes base/specular/normal/emissive metadata without GL ids |
| P1 | Draw-to-entity linkage | Entity frame records and entity draw tags exist, but draw packets do not carry exact entity handles | entity submit path associates concrete draw packets with entity handles |
| P1 | Sodium terrain compatibility | Sodium chunk draws differ from vanilla `ChunkSection` dynamic-uniform path | Sodium terrain is classified raster-visible and only acceleration-visible after safe geometry/distance policy |
| P1 | Distant Horizons acceleration policy | DH LOD is excluded, but generic DH objects still need safe distance/source handling | DH raster/acceleration behavior is validated with real DH 26.2 draws |
| P2 | UI/options migration | Old UI/options are Yarn/Fabric-specific and excluded from active compile | 26.2 Mojang-named UI/options are rebuilt outside mixin/backend core |
| P2 | SnakeYAML jar-in-jar semver warning | Not a blocker, but noisy and may become loader-strict later | warning is gone or dependency is replaced/relocated cleanly |

## Recommended Work Order

1. Treat the current Java/runtime boundary as the commit-worthy baseline.
   - Default backend startup passes.
   - Common bootstrap is active.
   - Opt-in Radiance backend reaches the expected missing native ABI.

2. Add the 26.2 native backend ABI to `core`.
   - Implement the functions declared in
     `src/main/native/include/com_radiance_client_render_blaze3d_backend_RadianceNativeBackendBridge.h`.
   - Start with device/surface handles, configure/acquire/present/destroy.
   - Keep this separate from old `RendererProxy` JNI symbols.

3. Implement native backend execution in layers.
   - Device/surface/present first.
   - Command encoder resource operations second.
   - Render pass draw execution third.
   - Fences/query/timestamps/debug after basic frame execution.

4. Finalize shared-scene native ABI.
   - Start from `RadianceNativeSceneBridge.RadianceSceneFrameSnapshot`.
   - Define ownership, frame lifetime, resource handle stability, and view
     construction responsibilities.

5. Wire module views.
   - PT requests acceleration view.
   - Raster GBuffer requests raster geometry view.
   - Raster ray-query pass requests the same acceleration view as PT.
   - Post modules request post view.

6. Validate vanilla, then mods.
   - Vanilla world/chunk/entity/resource reload first.
   - Distant Horizons LOD classification second.
   - Sodium chunk path third.
   - Unknown mod fallback behavior last.

## Migration Rules To Avoid Legacy Debris

- Do not make compatibility wrappers around old proxy classes.
- Do not call old renderer-replacement mixins from 26.2 code.
- Do not let mixins contain business logic.
- Do not let loader adapters decide scene classification or backend policy.
- Do not use GL ids as stable texture/material identity.
- Do not add a new shared-scene view unless a consumer module explicitly needs it.
- Do not include unknown geometry in acceleration/PT by fallback.
- Do not exclude unknown geometry from raster by default.
- Do not delete old source only to make the build look clean; replace behavior
  first, then remove old code when the ledger says it is covered.

## Current Build Commands

Full current verification:

```powershell
.\gradlew.bat --no-daemon processResources verifyMinecraft26_2Migration compileJava remapJar --rerun-tasks
```

Jar inspection:

```powershell
jar tf build\libs\Radiance-0.1.5-alpha-fabric-26.2.jar
```

Expected 26.2 runtime metadata in the jar:

```text
fabric.mod.json
radiance-26_2.mixins.json
radiance-26_2.accesswidener
com/radiance/loader/fabric/RadianceFabricClient.class
com/radiance/client/render/blaze3d/backend/RadianceGpuBackend.class
```

The jar must not contain:

```text
radiance.mixins.json
radiance.accesswidener
com/radiance/mixins/vulkan_render_integration/
com/radiance/client/RadianceClient.class
```
