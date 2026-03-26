package com.radiance.mixins.vulkan_options;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.PotentialValuesBasedCallbacksNoValue;
import com.radiance.client.gui.RenderPipelineScreen;
import com.radiance.client.option.Options;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoOptionsScreen.class)
public abstract class VideoOptionsScreenMixins {

    @Unique
    private static final PotentialValuesBasedCallbacksNoValue<Boolean> BOOLEAN_NO_KEY =
        new PotentialValuesBasedCallbacksNoValue<>(List.of(Boolean.TRUE, Boolean.FALSE),
            Codec.BOOL);

    @Shadow
    @Final
    private OptionListWidget list;

    @Inject(method = "init", at = @At("TAIL"))
    private void addRadianceOptions(CallbackInfo ci) {
        SimpleOption<Integer> chunkBuildingBatchSize = new SimpleOption<>(
            Options.CHUNK_BUILDING_BATCH_SIZE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
            Codec.intRange(1, 32),
            Options.chunkBuildingBatchSize,
            value -> Options.setChunkBuildingBatchSize(value, true)
        );

        SimpleOption<Integer> chunkBuildingTotalBatches = new SimpleOption<>(
            Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
            Codec.intRange(1, 32),
            Options.chunkBuildingTotalBatches,
            value -> Options.setChunkBuildingTotalBatches(value, true)
        );

        SimpleOption<Boolean> pipelineSettings = new SimpleOption<>(
            Options.PIPELINE_SETUP_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> optionText,
            BOOLEAN_NO_KEY,
            false,
            value -> MinecraftClient.getInstance()
                .setScreen(new RenderPipelineScreen((Screen) (Object) this))
        );

        this.list.addSingleOptionEntry(chunkBuildingBatchSize);
        this.list.addSingleOptionEntry(chunkBuildingTotalBatches);
        this.list.addSingleOptionEntry(pipelineSettings);
    }
}
