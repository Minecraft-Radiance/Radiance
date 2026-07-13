package com.radiance.client.texture.compat;

import com.radiance.client.scene_import.SceneImportAbi;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TextureMaterialCompat {

    private static final Map<String, MaterialRecord> SPRITE_MATERIALS = new ConcurrentHashMap<>();
    private static final Map<Long, Map<String, AtlasSpriteRecord>> ATLAS_SPRITES =
        new ConcurrentHashMap<>();

    private TextureMaterialCompat() {
    }

    public static void clearPbrMaterials() {
        SPRITE_MATERIALS.clear();
        ATLAS_SPRITES.clear();
        clearPbrMaterialsNative();
    }

    public static void registerPbrMaterial(Object spriteId, Object albedoPath, Object normalPath,
        Object specularPath, Object flagPath) {
        String spriteKey = String.valueOf(spriteId);
        String albedo = String.valueOf(albedoPath);
        String normal = normalPath == null ? "" : String.valueOf(normalPath);
        String specular = specularPath == null ? "" : String.valueOf(specularPath);
        String flag = flagPath == null ? "" : String.valueOf(flagPath);

        int materialId = registerPbrMaterialNative(spriteKey, albedo, normal, specular, flag);
        SPRITE_MATERIALS.put(spriteKey,
            new MaterialRecord(spriteKey, albedo, normal, specular, flag, materialId));
    }

    public static int materialId(Object spriteId) {
        MaterialRecord record = SPRITE_MATERIALS.get(String.valueOf(spriteId));
        return record == null ? 0 : record.materialId();
    }

    public static void clearAtlas(long atlasTexture) {
        if (atlasTexture == 0L) {
            return;
        }
        ATLAS_SPRITES.remove(atlasTexture);
        clearAtlasNative(atlasTexture);
    }

    public static void syncAtlasSprite(long atlasTexture, long atlasTextureView, Object atlasId,
        Object spriteId, float u0, float v0, float u1, float v1, int pixelX, int pixelY,
        int pixelWidth, int pixelHeight) {
        if (atlasTexture == 0L || spriteId == null) {
            return;
        }

        String spriteKey = String.valueOf(spriteId);
        int materialId = materialId(spriteKey);
        AtlasSpriteRecord record = new AtlasSpriteRecord(atlasTexture, atlasTextureView,
            String.valueOf(atlasId), spriteKey, u0, v0, u1, v1, pixelX, pixelY, pixelWidth,
            pixelHeight, materialId);
        ATLAS_SPRITES.computeIfAbsent(atlasTexture, key -> new ConcurrentHashMap<>())
            .put(spriteKey, record);
        syncAtlasSpriteNative(record);
    }

    private static int registerPbrMaterialNative(String spriteId, String albedoPath,
        String normalPath, String specularPath, String flagPath) {
        return SceneImportAbi.registerPbrMaterial(spriteId, albedoPath, normalPath,
            specularPath, flagPath);
    }

    private static void clearPbrMaterialsNative() {
        SceneImportAbi.clearPbrMaterials();
    }

    private static void clearAtlasNative(long atlasTexture) {
        SceneImportAbi.clearAtlas(atlasTexture);
    }

    private static void syncAtlasSpriteNative(AtlasSpriteRecord record) {
        SceneImportAbi.syncAtlasSprite(record.atlasTexture(), record.atlasTextureView(),
            record.atlasId(), record.spriteId(), record.u0(), record.v0(), record.u1(),
            record.v1(), record.pixelX(), record.pixelY(), record.pixelWidth(),
            record.pixelHeight(), record.materialId());
    }

    public record MaterialRecord(String spriteId, String albedoPath, String normalPath,
                                 String specularPath, String flagPath, int materialId) {}

    public record AtlasSpriteRecord(long atlasTexture, long atlasTextureView, String atlasId,
                                    String spriteId, float u0, float v0, float u1, float v1,
                                    int pixelX, int pixelY, int pixelWidth, int pixelHeight,
                                    int materialId) {}
}
