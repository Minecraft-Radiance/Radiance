package com.radiance.mixins.vulkan_options;

import static net.minecraft.client.Options.genericValueLabel;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import com.mojang.serialization.Codec;
import com.radiance.client.gui.PotentialValuesBasedCallbacksNoValue;
import com.radiance.client.gui.RenderPipelineScreen;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoOptionsScreenMixins extends OptionsSubScreen {

    @Unique
    private static final PotentialValuesBasedCallbacksNoValue<Boolean> BOOLEAN_NO_KEY =
        new PotentialValuesBasedCallbacksNoValue<>(ImmutableList.of(Boolean.TRUE, Boolean.FALSE),
            Codec.BOOL);

    @Unique
    private static PotentialValuesBasedCallbacksNoValue<Integer> valuesOf(List<Integer> values) {
        return new PotentialValuesBasedCallbacksNoValue<>(ImmutableList.copyOf(values), Codec.INT);
    }

    protected VideoOptionsScreenMixins(Screen parent, net.minecraft.client.Options gameOptions, Component title) {
        super(parent, gameOptions, title);
    }

    @Shadow
    private OptionsList list;

    @Inject(method = "init()V", at = @At("HEAD"), cancellable = true)
    private void redirectInit(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Options.sanitizeFrameGenerationOptionsForAvailableHardware(true);
        List<Integer> frameGenerationModes = Options.getAvailableFrameGenerationModes();
        int multiplierReferenceMode = Options.getFrameGenerationMultiplierReferenceMode();
        List<Integer> frameGenerationMultipliers = Options.getAvailableFrameGenerationMultipliers(
            multiplierReferenceMode);

        this.list = new OptionsList(client, this.width, this.height, 32, this.height - 32,
            25);

        Window window = client.getWindow();
        Monitor monitor = window.findBestMonitor();
        int currentVideoMode;
        if (monitor == null) {
            currentVideoMode = -1;
        } else {
            Optional<VideoMode> optional = window.getPreferredFullscreenVideoMode();
            currentVideoMode = optional.map(monitor::getVideoModeIndex).orElse(-1);
        }

        OptionInstance<Integer> maxFps = new OptionInstance<>("options.framerateLimit",
            OptionInstance.noTooltip(),
            (optionText, value) -> value == 260
                ? genericValueLabel(optionText,
                Component.translatable("options.framerateLimit.max"))
                : genericValueLabel(optionText, Component.translatable("options.framerate", value)),
            new OptionInstance.IntRange(1, 26).xmap(value -> value * 10,
                value -> value / 10),
            Codec.intRange(10, 260), Options.maxFps, value -> Options.setMaxFps(value, true));

        OptionInstance<Integer> fullScreenResolutionOption = new OptionInstance<>(
            "options.fullscreen.resolution", OptionInstance.noTooltip(),
            (optionText, value) -> {
                if (monitor == null) {
                    return Component.translatable("options.fullscreen.unavailable");
                }
                if (value == -1) {
                    return genericValueLabel(optionText,
                        Component.translatable("options.fullscreen.current"));
                }
                return genericValueLabel(optionText,
                    Component.literal(monitor.getMode(value).toString()));
            },
            new OptionInstance.IntRange(-1,
                monitor != null ? monitor.getModeCount() - 1 : -1),
            currentVideoMode, value -> {
                if (monitor != null) {
                    window.setPreferredFullscreenVideoMode(
                        value == -1 ? Optional.empty() : Optional.of(monitor.getMode(value)));
                }
            });

        OptionInstance<Boolean> enableVsync = OptionInstance.createBoolean("options.vsync", Options.vsync,
            value -> {
                if (client.getWindow() != null) {
                    Options.setVsync(value, true);
                }
            });

        OptionInstance<Integer> frameGenerationMethod = new OptionInstance<>(
            Options.FRAME_GENERATION_METHOD_KEY,
            OptionInstance.noTooltip(),
            (optionText, value) -> genericValueLabel(optionText,
                Component.translatable(Options.frameGenerationModeTranslationKey(value))),
            valuesOf(frameGenerationModes),
            Options.frameGenerationMode,
            value -> {
                Options.setFrameGenerationMode(value, true);
                int maxMultiplier = Options.getMaxFrameGenerationMultiplier(value);
                if (maxMultiplier >= 2 && Options.frameGenerationMultiplier > maxMultiplier) {
                    Options.setFrameGenerationMultiplier(maxMultiplier, true);
                }
            });

        OptionInstance<Integer> frameGenerationMultiplier = frameGenerationMultipliers.isEmpty() ? null :
            new OptionInstance<>(
                Options.FRAME_GENERATION_MULTIPLIER_KEY,
                OptionInstance.noTooltip(),
                (optionText, value) -> genericValueLabel(optionText, Component.literal(value + "x")),
                valuesOf(frameGenerationMultipliers),
                Options.frameGenerationMultiplier,
                value -> Options.setFrameGenerationMultiplier(value, true));

        OptionInstance<Integer> chunkBuildingBatchSize = new OptionInstance<>(
            Options.CHUNK_BUILDING_BATCH_SIZE_KEY, OptionInstance.noTooltip(),
            (optionText, value) -> genericValueLabel(optionText,
                Component.literal(Integer.toString(value))),
            new OptionInstance.IntRange(Options.CHUNK_BUILDING_BATCH_SIZE_MIN,
                Options.CHUNK_BUILDING_BATCH_SIZE_MAX), Codec.intRange(
                Options.CHUNK_BUILDING_BATCH_SIZE_MIN, Options.CHUNK_BUILDING_BATCH_SIZE_MAX),
            Options.chunkBuildingBatchSize, value -> Options.setChunkBuildingBatchSize(value,
            true));

        OptionInstance<Integer> chunkBuildingTotalBatches = new OptionInstance<>(
            Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY, OptionInstance.noTooltip(),
            (optionText, value) -> genericValueLabel(optionText,
                Component.literal(Integer.toString(value))),
            new OptionInstance.IntRange(Options.CHUNK_BUILDING_TOTAL_BATCHES_MIN,
                Options.CHUNK_BUILDING_TOTAL_BATCHES_MAX), Codec.intRange(
                Options.CHUNK_BUILDING_TOTAL_BATCHES_MIN, Options.CHUNK_BUILDING_TOTAL_BATCHES_MAX),
            Options.chunkBuildingTotalBatches, value -> Options.setChunkBuildingTotalBatches(
            value, true));

        OptionInstance<Integer> chunkBuildingThreads = new OptionInstance<>(
            Options.CHUNK_BUILDING_THREADS_KEY, OptionInstance.noTooltip(),
            (optionText, value) -> genericValueLabel(optionText,
                Component.literal(Integer.toString(value))),
            new OptionInstance.IntRange(Options.CHUNK_BUILDING_THREADS_MIN,
                Options.getMaxChunkBuildingThreads()),
            Codec.intRange(Options.CHUNK_BUILDING_THREADS_MIN, Options.getMaxChunkBuildingThreads()),
            Options.chunkBuildingThreads, value -> Options.setChunkBuildingThreads(
            value, true));

        OptionInstance<Boolean> collectChunkEmission = OptionInstance.createBoolean(
            Options.COLLECT_CHUNK_EMISSION_KEY,
            Options.collectChunkEmission,
            value -> Options.setCollectChunkEmission(value, true));

        OptionInstance<Boolean> pipelineSettings = new OptionInstance<>(Options.PIPELINE_SETUP_KEY,
            OptionInstance.noTooltip(), (optionText, value) -> optionText, BOOLEAN_NO_KEY, false,
            value -> client.setScreen(new RenderPipelineScreen((VideoSettingsScreen) (Object) this)));

        this.list.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_GAMEPLAY), this.list));
        this.list.addBig(this.options.biomeBlendRadius());
        this.list.addSmall(new OptionInstance[]{
            this.options.graphicsMode(),
            this.options.renderDistance(),
            this.options.simulationDistance(),
            this.options.ambientOcclusion(),
            this.options.bobView(),
            this.options.guiScale(),
            this.options.attackIndicator(),
            this.options.gamma(),
            this.options.cloudStatus(),
            this.options.particles(),
            this.options.mipmapLevels(),
            this.options.entityShadows(),
            this.options.screenEffectScale(),
            this.options.entityDistanceScaling(),
            this.options.fovEffectScale(),
            this.options.showAutosaveIndicator(),
            this.options.glintSpeed(),
            this.options.glintStrength(),
        });

        this.list.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_WINDOW), this.list));
        this.list.addSmall(new OptionInstance[]{
            maxFps,
            enableVsync,
            this.options.fullscreen(),
        });
        this.list.addBig(fullScreenResolutionOption);

        this.list.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_FRAME_GENERATION), this.list));
        this.list.addBig(frameGenerationMethod);
        if (frameGenerationMultiplier != null) {
            this.list.addBig(frameGenerationMultiplier);
        }

        this.list.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_TERRAIN), this.list));
        this.list.addBig(chunkBuildingBatchSize);
        this.list.addBig(chunkBuildingTotalBatches);
        this.list.addBig(chunkBuildingThreads);
        this.list.addBig(collectChunkEmission);

        this.list.addEntry(
            new CategoryVideoOptionEntry(Component.translatable(Options.CATEGORY_PIPELINE), this.list));
        this.list.addBig(pipelineSettings);

        this.addWidget(this.list);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            client.options.save();
            window.changeFullscreenVideoMode();
            client.setScreen(this.lastScreen);
        }).bounds(this.width / 2 - 100, this.height - 27, 200, 20).build());

        ci.cancel();
    }
}
