package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.IdentifierInputStream;
import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallbackResourceManager.class)
public abstract class NamespaceResourceManagerMixins {

    @Shadow
    private static IoSupplier<InputStream> wrapForDebug(ResourceLocation id, PackResources pack,
        IoSupplier<InputStream> supplier) {
        return null;
    }

    @Inject(method = "createResource(Lnet/minecraft/server/packs/PackResources;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/server/packs/resources/IoSupplier;Lnet/minecraft/server/packs/resources/IoSupplier;)Lnet/minecraft/server/packs/resources/Resource;", at = @At(value = "HEAD"),
        cancellable = true)
    private static void addIdentifierToInputStream(PackResources pack,
        ResourceLocation id,
        IoSupplier<InputStream> supplier,
        IoSupplier<ResourceMetadata> metadataSupplier,
        CallbackInfoReturnable<Resource> cir) {
        cir.setReturnValue(new Resource(pack, () -> {
            IoSupplier<InputStream> inputStreamInputSupplier = wrapForDebug(id, pack, supplier);
            if (inputStreamInputSupplier == null) {
                return null;
            }
            InputStream inputStream = inputStreamInputSupplier.get();
            return new IdentifierInputStream(inputStream, id);
        }, metadataSupplier));
    }
}
