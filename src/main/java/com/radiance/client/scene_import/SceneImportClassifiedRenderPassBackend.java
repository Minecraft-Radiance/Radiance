package com.radiance.client.scene_import;

public interface SceneImportClassifiedRenderPassBackend {
    void radiance$setSceneImportClassification(int sceneRouteMask, int formatId,
        int importKernelId, int rasterClass, int materialPolicy, int transformRecipeId);

    void radiance$clearSceneImportClassification();
}
