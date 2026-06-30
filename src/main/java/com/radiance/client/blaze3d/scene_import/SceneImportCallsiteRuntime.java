package com.radiance.client.blaze3d.scene_import;

import com.radiance.client.blaze3d.backend.SceneImportClassifiedRenderPassBackend;

public final class SceneImportCallsiteRuntime {
    private SceneImportCallsiteRuntime() {
    }

    public static void setClassification(Object renderPassBackend, int sceneRouteMask,
        int formatId, int importKernelId, int rasterClass, int materialPolicy,
        int transformRecipeId) {
        if (renderPassBackend instanceof SceneImportClassifiedRenderPassBackend classified) {
            classified.radiance$setSceneImportClassification(sceneRouteMask, formatId,
                importKernelId, rasterClass, materialPolicy, transformRecipeId);
        }
    }

    public static void clearClassification(Object renderPassBackend) {
        if (renderPassBackend instanceof SceneImportClassifiedRenderPassBackend classified) {
            classified.radiance$clearSceneImportClassification();
        }
    }
}
