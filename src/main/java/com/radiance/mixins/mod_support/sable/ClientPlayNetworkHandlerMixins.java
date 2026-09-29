package com.radiance.mixins.mod_support.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixins {
    @WrapOperation(method = "onBundle", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private <T extends PacketListener> void radiance$dispatchPacket(Packet<T> packet, T listener,
        Operation<Void> original) {
        try {
            original.call(packet, listener);
        } catch (OffThreadException ignored) {
        }
    }
}
