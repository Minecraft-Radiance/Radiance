package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.IdentifierInputStream;
import java.io.InputStream;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.metadata.ResourceMetadata;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NamespaceResourceManager.class)
public abstract class NamespaceResourceManagerMixins {

    @Shadow
    private static InputSupplier<InputStream> wrapForDebug(Identifier id, ResourcePack pack,
        InputSupplier<InputStream> supplier) {
        return null;
    }

    @Inject(method = "createResource(Lnet/minecraft/resource/ResourcePack;Lnet/minecraft/util/Identifier;Lnet/minecraft/resource/InputSupplier;Lnet/minecraft/resource/InputSupplier;)Lnet/minecraft/resource/Resource;", at = @At(value = "HEAD"),
        cancellable = true)
    private static void addIdentifierToInputStream(ResourcePack pack,
        Identifier id,
        InputSupplier<InputStream> supplier,
        InputSupplier<ResourceMetadata> metadataSupplier,
        CallbackInfoReturnable<Resource> cir) {
        cir.setReturnValue(new Resource(pack, () -> {
            InputSupplier<InputStream> inputStreamInputSupplier = wrapForDebug(id, pack, supplier);
            if (inputStreamInputSupplier == null) {
                return null;
            }
            InputStream inputStream = inputStreamInputSupplier.get();
            return new IdentifierInputStream(inputStream, id);
        }, metadataSupplier));
    }
}
