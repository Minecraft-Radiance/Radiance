#ifndef SHARED_HPP
#define SHARED_HPP

#include "common/mapping.hpp"

#define INF_DISTANCE 65504.0
#define PI 3.14159265358979323
#define INV_PI 0.31830988618379067
#define TWO_PI 6.28318530717958648
#define INV_TWO_PI 0.15915494309189533
#define INV_4_PI 0.07957747154594766

#ifdef __cplusplus
namespace vk {
#endif
#ifdef __cplusplus
namespace VertexFormat {
#endif
    struct Triangle {
        T_VEC3 pos;
        T_VEC3 color;
    };

    struct TexturedTriangle {
        T_VEC3 pos;
        T_VEC2 uv;
    };

    struct ArrayTexturedTriangle {
        T_VEC3 pos;
        T_FLOAT metallic;
        T_VEC3 norm;
        T_FLOAT roughness;
        T_VEC2 uv;
        T_FLOAT textureLayer;
        T_FLOAT pad0;
        T_VEC3 color;
        T_FLOAT intensity;
    };

    struct PositionOnly {
        T_VEC3 position;
    };

    struct PositionTexColor {
        T_VEC3 position;
        T_VEC2 uv;
        T_UINT color;
    };

    struct PositionColor {
        T_VEC3 position;
        T_UINT color;
    };

    struct PositionColorNormal {
        T_VEC3 position;
        T_UINT color;
        T_UINT normal; // first 3 bytes
    };

    struct PositionTex {
        T_VEC3 position;
        T_VEC2 uv;
    };

    struct PositionColorTexLight {
        T_VEC3 position;
        T_UINT color;
        T_VEC2 uv0;
        T_UINT uv2;
    };

    struct PositionColorLight {
        T_VEC3 position;
        T_UINT color;
        T_UINT uv2;
    };

    struct PositionTexColorLight {
        T_VEC3 position;
        T_VEC2 uv0;
        T_UINT color;
        T_UINT uv2;
    };

    struct PositionTexColorNormal {
        T_VEC3 position;
        T_VEC2 uv0;
        T_UINT color;
        T_UINT normal; // first 3 bytes
    };

    struct PositionTexLightColor {
        T_VEC3 position;
        T_VEC2 uv0;
        T_UINT uv2;
        T_UINT color;
    };

    struct PositionColorTexLightNormal {
        T_VEC3 position;
        T_UINT color;
        T_VEC2 uv0;    // texture
        T_UINT uv2;    // lightmap
        T_UINT normal; // first 3 bytes
    };

    struct PositionColorTexOverlayLightNormal {
        T_VEC3 position;
        T_UINT color;
        T_VEC2 uv0;    // texture
        T_UINT uv1;    // overlay
        T_UINT uv2;    // lightmap
        T_UINT normal; // first 3 bytes
    };

    struct PBRVertex {
        T_VEC3 pos;
        T_UINT useNorm;

        T_VEC3 norm;
        T_UINT useColorLayer;

        T_VEC4 colorLayer;

        T_UINT useTexture;
        T_UINT useOverlay;
        T_VEC2 textureUV;

        T_IVEC2 overlayUV;
        T_UINT useGlint;
        T_UINT textureID;

        T_VEC2 glintUV;
        T_UINT glintTexture;
        T_UINT useLight;

        T_IVEC2 lightUV;
        T_UINT coordinate;
        T_FLOAT albedoEmission;

        T_VEC3 postBase;
        T_UINT alphaMode;
    };

    struct PositionVertex {
        T_VEC3 pos;
        T_UINT pad0;
    };

    struct MaterialVertex {
        T_VEC3 norm;
        T_UINT textureID;

        T_VEC4 colorLayer;

        T_VEC2 textureUV;
        T_IVEC2 overlayUV;

        T_VEC2 glintUV;
        T_UINT glintTexture;
        T_FLOAT albedoEmission;

        T_IVEC2 lightUV;
        T_UINT packedData;
        T_UINT pad0;
    };
#ifdef __cplusplus
}; // namespace VertexFormat

static_assert(sizeof(VertexFormat::MaterialVertex) == 80);
static_assert(offsetof(VertexFormat::MaterialVertex, lightUV) == 64);
static_assert(offsetof(VertexFormat::MaterialVertex, packedData) == 72);
#endif

#ifdef __cplusplus
namespace Data {
#endif
    struct Camera {
        T_MAT4 viewMatrix;
        T_MAT4 projMatrix;
        T_MAT4 viewMatrixInv;
        T_MAT4 projMatrixInv;
        T_VEC2 jitter;
        T_VEC2 pad0;
    };

    struct DirectionalLight {
        T_VEC3 direction;  // 12 bytes
        T_FLOAT pad0;      // 4 bytes
        T_VEC3 color;      // 12 bytes
        T_FLOAT intensity; // 4 bytes
    };

    struct World {
        DirectionalLight directionalLight; // 32 bytes
        T_FLOAT time;                      // 4 bytes
        T_UINT seed;                       // 4 bytes
    };

    struct OverlayPostUBO {
        T_MAT4 projectionMat;
        T_VEC2 inSize;
        T_VEC2 outSize;
        T_VEC2 blurDir;
        T_FLOAT radius;
        T_FLOAT radiusMultiplier;
    };

    struct WorldUBO {
        T_MAT4 cameraViewMat;

        T_MAT4 cameraEffectedViewMat;

        T_MAT4 cameraProjMat;

        T_MAT4 cameraViewMatInv;

        T_MAT4 cameraEffectedViewMatInv;

        T_MAT4 cameraProjMatInv;

        T_VEC2 cameraJitter;
        T_FLOAT gameTime;
        T_UINT seed;

        T_MAT4 textureMat;

        T_UINT overlayTextureID;
        T_UINT isFirstPerson;
        T_FLOAT fogStart;
        T_FLOAT fogEnd;

        T_VEC4 fogColor;

        T_UINT fogType;
        T_UINT skyType;
        T_UINT pad2;
        T_UINT pad3;

        T_DVEC4 cameraPos; // w for padding
        T_IVEC4 chunkGridInfo; // x=sizeX, y=sizeY, z=sizeZ, w=bottomSectionCoord
        T_IVEC4 chunkStorageSectionPos; // xyz=BuiltChunkStorage.sectionPos

        T_UINT endSkyTextureID;
        T_UINT endPortalTextureID;
        T_UINT lightMapTextureID;
        T_UINT pad4;
        T_UINT pad5;
        T_UINT pad6;
        T_UINT pad7;
        T_UINT pad8;

        T_MAT4 eyeViewOffsets[2];
        T_MAT4 eyeProjOffsets[2];
        T_FLOAT ipd;
        T_UINT stereoEnabled;
        T_FLOAT foveatedInnerRadius;
        T_UINT foveatedOuterBlockSize;
        T_VEC2 foveatedCenter;
        T_UINT pad9;
        T_UINT pad10;
    };

#ifdef __cplusplus
    static_assert(sizeof(WorldUBO) == 896);
    static_assert(offsetof(WorldUBO, pad5) == 592);
    static_assert(offsetof(WorldUBO, eyeViewOffsets) == 608);
    static_assert(offsetof(WorldUBO, eyeProjOffsets) == 736);
    static_assert(offsetof(WorldUBO, ipd) == 864);
    static_assert(offsetof(WorldUBO, stereoEnabled) == 868);
    static_assert(offsetof(WorldUBO, foveatedInnerRadius) == 872);
    static_assert(offsetof(WorldUBO, foveatedOuterBlockSize) == 876);
    static_assert(offsetof(WorldUBO, foveatedCenter) == 880);
#endif

    struct SkyUBO {
        T_VEC3 baseColor;
        T_UINT skyType;

        T_VEC4 horizonColor;

        T_VEC3 sunDirection;
        T_UINT isSunRisingOrSetting;

        T_UINT isSkyDark;
        T_UINT hasBlindnessOrDarkness;
        T_UINT cameraSubmersionType;
        T_UINT moonPhase;
        T_FLOAT rainGradient;

        T_UINT sunTextureID;
        T_UINT moonTextureID;
        T_UINT pad0;
    };

    struct TextureMapEntry {
        T_INT specular;
        T_INT normal;
        T_INT flag;
    };

    struct TextureMapping {
        TextureMapEntry entries[4096];
    };

    struct ExposureData {
        T_INT width;
        T_INT height;
        T_INT stride;
        T_FLOAT exposure;

        T_FLOAT minL;
        T_FLOAT maxL;
        T_UINT total;
        T_UINT pad0;

        T_UINT bins[256];
    };

    struct LightMapUBO {
        T_FLOAT ambientLightFactor;
        T_FLOAT skyFactor;
        T_FLOAT blockFactor;
        T_INT useBrightLightmap;

        T_VEC3 skyLightColor;
        T_FLOAT nightVisionFactor;

        T_FLOAT darknessScale;
        T_FLOAT darkenWorldFactor;
        T_FLOAT brightnessFactor;
        T_FLOAT pad0;
    };
#ifdef __cplusplus
}; // namespace Data
#endif
#ifdef __cplusplus
}; // namespace vk
#endif

#endif
