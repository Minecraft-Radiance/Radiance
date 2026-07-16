# MCVR migration layout notes

This file records layout checks done while migrating Radiance-custom onto the
MCVR/upstream-0.1.5 Java surface.

## WorldPipelineBuildParams

MCVR native `src/core/render/pipeline.hpp` currently expects:

```cpp
struct WorldPipelineBuildParams {
    int moduleCount;
    int eyeCount;
    char **moduleNames;
    int *imageFormats;
    int **inputIndices;
    int **outputIndices;
    int *attributeCounts;
    char*** attributeKVs;
};
```

Java `Pipeline.buildNative()` therefore writes:

- `moduleCount` at byte 0;
- `eyeCount` at byte 4;
- six 64-bit pointers from byte 8 through byte 55.

The Java parameter block size is 56 bytes.

## WorldUBO

`BufferProxy.updateWorldUniform()` allocates 896 bytes and clears the whole
temporary block before writing the Java-owned prefix. This prevents stale stack
padding and keeps the Java allocation large enough for the current VR stereo and
foveated fields.

During migration, a C++ probe against `MCVR-custom/src/common/shared.hpp` with
the repository GLM headers reported:

```text
sizeof(vk::Data::WorldUBO)=880
pad5=592
eyeViewOffsets=596
eyeProjOffsets=724
ipd=852
stereoEnabled=856
foveatedInnerRadius=860
foveatedOuterBlockSize=864
foveatedCenter=868
```

SPIR-V generated from `src/shader/world/post_render/world_post.vert` reported
std140 offsets:

```text
pad5=592
eyeViewOffsets=608
eyeProjOffsets=736
ipd=864
stereoEnabled=868
foveatedInnerRadius=872
foveatedOuterBlockSize=876
foveatedCenter=880
```

So the Java allocation is intentionally sized to the shader-visible std140 range
ending at 896 bytes, while native C++ currently uses packed GLM offsets before
the stereo matrices. If stereo/foveated UBO fields render incorrectly, fix the
native `WorldUBO` layout to match std140 rather than shrinking Java's allocation.
