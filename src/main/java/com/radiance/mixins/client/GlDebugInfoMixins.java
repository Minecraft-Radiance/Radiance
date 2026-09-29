package com.radiance.mixins.client;

import com.mojang.blaze3d.platform.GlUtil;
import java.util.Locale;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlUtil.class)
public abstract class GlDebugInfoMixins {

    private static final String RADIANCE_CPU_INFO = queryCpuInfo();

    @Inject(method = "getCpuInfo()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void useInitializedCpuInfo(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(RADIANCE_CPU_INFO);
    }

    private static String queryCpuInfo() {
        CentralProcessor processor = new SystemInfo().getHardware().getProcessor();
        return String.format(Locale.ROOT, "%dx %s", processor.getLogicalProcessorCount(),
            processor.getProcessorIdentifier().getName()).replaceAll("\\s+", " ").trim();
    }
}
