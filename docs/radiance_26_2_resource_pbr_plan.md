# Radiance 26.2 Resource And PBR Migration Plan

Scope: migrate retained resource/PBR mixins without GL ids and without renderer
business logic in mixins.

## Old Model

The 1.21 path stores texture/material relations through GL ids:

| Old map/path | Old meaning | 26.2 replacement |
|---|---|---|
| `TextureTracker.textureID2GLID` | resource id to GL texture id | resource id to backend texture handle/material record |
| `TextureTracker.GLID2Texture` | GL texture id to dimensions/format | `RadianceTextureRecord` dimensions/format/usage |
| `GLID2SpecularGLID` | base GL texture to LabPBR specular GL texture | material record `BASE -> SPECULAR` |
| `GLID2NormalGLID` | base GL texture to normal GL texture | material record `BASE -> NORMAL` |
| `GLID2FlagGLID` | base GL texture to flag GL texture | material record `BASE -> FLAG` |
| `TextureProxy` upload/prepare | direct native texture id lifecycle | backend texture creation/write/copy plus native bridge packet |

## 26.2 Stable Classes

26.2 texture/resource classes are under `net.minecraft.client.renderer.texture`:

| Class | New responsibility |
|---|---|
| `TextureManager` | texture resource registration and reload ownership. |
| `AbstractTexture` | logical texture object; no GL id assumptions. |
| `DynamicTexture` | dynamic/native-image texture replacement for old native image backed texture. |
| `ReloadableTexture` / `SimpleTexture` | reload lifecycle and decoded texture content. |
| `TextureAtlas` | atlas texture and sprite upload owner. |
| `TextureAtlasSprite` | sprite uv/material identity. |
| `SpriteContents` / `SpriteLoader` | decoded atlas sprite contents and mip chain. |
| `atlas.sources.*` | directory/single/paletted/unstitch source discovery for auxiliary sidecars. |

## New Client Registry

Implemented foundation:

| File | Purpose |
|---|---|
| `RadianceMaterialKind` | `BASE`, `SPECULAR`, `NORMAL`, `FLAG`. |
| `RadianceMaterialTexture` | resource id + backend texture handle + dimensions + mip count. |
| `RadianceMaterialRecord` | groups base and auxiliary textures for one material. |
| `RadianceMaterialRegistry` | resource-id/backend-handle registry replacing old GL id maps. |
| `RadianceAtlasRegistry` | atlas sprite id to atlas/uv/size registry, sprite contents/mip metadata, and sidecar atlas filtering policy. |
| `RadianceGlyphRegistry` | glyph atlas insert metadata recorded after 26.2 font texture upload/copy. |
| `RadianceImageRegistry` | decoded image/resource metadata for texture contents, dynamic textures, and filtered sidecar candidates. |

Current behavior:

- `TextureManagerMixin#register` forwards to `RadianceResourceObserver`.
- `RadianceResourceObserver` records the resource id without reading GL ids.
- `RadianceCaptureRegistry#registerTexture` links backend texture labels that look
  like resource ids into `RadianceMaterialRegistry`.
- `DirectoryListerMixin`, `SingleFileMixin`, `PalettedPermutationsMixin`, and
  `UnstitcherMixin` call the client resource service to skip LabPBR/auxiliary
  sidecar ids before they are stitched as regular sprites.
- `TextureAtlasMixin#upload` records sprite atlas location, uv window, xy, size,
  and material id after Mojang creates the atlas preparations.
- `SpriteContentsMixin` records sprite source size, frame size, animation flag,
  transparency, texture metadata presence, and final mip level after vanilla
  mipmap generation.
- `ReloadableResourceManagerMixin#createReload` clears resource/material/atlas
  caches before resource packs reload.
- `FontTextureMixin#add` records glyph pixel size, color mode, oversample, and
  advance after `GlyphBitmap` has uploaded into a backend `GpuTexture`.
- `TextureContentsMixin#load` records decoded image size, format, blur/clamp
  metadata, and material sidecar identity without intercepting upload.
- `TextureManagerMixin#register` records dynamic texture image dimensions when
  the registered texture exposes a `NativeImage`.
- `TextureManagerMixin#release` and `#close` now invalidate logical texture,
  decoded image, and material records without inspecting GL/backend ids.
- Atlas sidecar filtering now records skipped `_n`, `_s`, and flag candidates as
  material records so LabPBR resources do not disappear just because they are not
  stitched as base sprites.
- `RadianceNativeSceneBridge` frame snapshots now export material, atlas sprite,
  sprite contents, decoded image, sidecar candidate, and glyph records alongside
  backend texture/resource tables.

## Required Hook Ports

| Old retained mixin | 26.2 target | New role |
|---|---|---|
| `TextureManagerMixins` | `TextureManager#register` | register resource id to material registry. |
| `AbstractTextureMixins` | `AbstractTexture` | texture lifetime only if backend texture labels are insufficient. |
| `NativeImageBackedTextureMixins` | `DynamicTexture` | Covered by `TextureManagerMixin#register` dynamic texture inspection; no upload hook. |
| `ReloadableTextureMixins` | `ReloadableTexture` / `SimpleTexture` | Covered by `TextureContents#load`, backend resource-id texture labels in `ReloadableTexture#doLoad`, and `TextureManager#release/#close` invalidation. No dedicated mixin needed. |
| `NativeImageMixins` | `TextureContents#load` plus dynamic texture registration | CPU image sidecar/resource correlation. No pixel mutation/default generation in mixin. |
| `TextureUtilMixins` | backend texture writes or texture content loader | raw upload metadata only; no GL upload replacement. |
| `SpriteAtlasTextureMixins` | `TextureAtlas#upload` | Ported as `TextureAtlasMixin`; atlas texture to sprite/material handle mapping. |
| `SpriteContentsMixins` | `SpriteContents` | Ported as `SpriteContentsMixin`; records source/frame/mip/animation metadata only. |
| `SpriteMixins` | `TextureAtlasSprite` | Covered by `TextureAtlasMixin#upload`; sprite uv and atlas material id association are recorded from final stitched sprites. |
| `DirectoryAtlasSourceMixins` | `atlas.sources.DirectoryLister#run` | Ported as `DirectoryListerMixin`; sidecar filtering. |
| `SingleAtlasSourceMixins` | `atlas.sources.SingleFile#run` | Ported as `SingleFileMixin`; sidecar filtering. |
| `PalettedPermutationsAtlasSourceMixins` | `atlas.sources.PalettedPermutations#run` | Ported as `PalettedPermutationsMixin`; generated sidecar filtering with discard. |
| `UnstitchAtlasSourceMixins` | `atlas.sources.Unstitcher#run` | Ported as `UnstitcherMixin`; unstitch sidecar filtering with discard. |
| glyph mixins | `FontTexture#add` | Ported as `FontTextureMixin`; records glyph atlas metadata. Old bake overwrites are not ported because 26.2 glyph pixels already upload through Blaze3D backend texture operations. |
| `ReloadableResourceManagerImplMixins` | `ReloadableResourceManager#createReload` | Ported as `ReloadableResourceManagerMixin`; resource/material/atlas cache invalidation. |

## Rules

- Mixins only forward arguments to `RadianceResourceObserver` or future resource
  services.
- No GL id reads or GL upload interception in 26.2 mixins.
- Texture upload/prepare is captured through Radiance backend texture creation and
  command encoder write/copy paths.
- LabPBR sidecars are material metadata. They are not separate world geometry and
  must not affect scene view inclusion by themselves.

## Deferred Items

| Item | Reason |
|---|---|
| Atlas sprite material id mapping | Implemented for `TextureAtlas#upload`; still needs native material packet ABI to expose this table to native modules. |
| Glyph material mapping | Implemented for glyph atlas insert metadata; exact source provider id per glyph remains deferred unless text modules require source-pack attribution. |
| Native material packet ABI | Java frame snapshots now expose material/resource tables; native shared_scene still needs final ingestion structs and ownership rules. |

## Implementation Log

| Date | Step | Result |
|---|---|---|
| 2026-06-22 | Resource/PBR investigation | Identified old GL-id texture maps and 26.2 texture class targets. Added material registry foundation to staged 26.2 source. |
| 2026-06-22 | Atlas source filtering and sprite registry | Added loader-neutral 26.2 atlas source mixins for sidecar filtering and `TextureAtlas#upload` observer to populate atlas sprite metadata. |
| 2026-06-22 | Resource reload invalidation | Added `ReloadableResourceManagerMixin` and client observer path to clear material and atlas registries before reload. |
| 2026-06-22 | Glyph atlas metadata | Added `FontTextureMixin` and glyph registry; confirmed bitmap/TTF/Unihex glyph pixel uploads now go through backend texture writes/copies. |
| 2026-06-22 | Texture contents and image metadata | Added `TextureContentsMixin` and `RadianceImageRegistry`; resource images, dynamic textures, and filtered LabPBR sidecar candidates are now recorded without GL id or upload interception. |
| 2026-06-22 | Sprite contents metadata | Added `SpriteContentsMixin`; sprite contents now record source/frame size, animation/texture metadata, transparency, and generated mip level without propagating old GL target ids. |
| 2026-06-22 | Texture lifetime invalidation | Extended `TextureManagerMixin` to forward `release` and `close` to client resource services. Material/image registries now remove per-resource entries on release and clear on manager close/reload. |
| 2026-06-22 | Resource tables in frame snapshot | Added immutable snapshots from material, atlas, image, and glyph registries into `RadianceNativeSceneBridge` frame snapshots so native ABI work can consume self-contained resource metadata. |
