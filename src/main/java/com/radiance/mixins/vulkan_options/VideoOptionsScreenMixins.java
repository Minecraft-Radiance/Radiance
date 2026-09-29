package com.radiance.mixins.vulkan_options;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.gui.PotentialValuesBasedCallbacksNoValue;
import com.radiance.client.gui.RenderPipelineScreen;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.Monitor;
import net.minecraft.client.util.VideoMode;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoOptionsScreen.class)
public class VideoOptionsScreenMixins extends GameOptionsScreenMixins {

    @Unique
    private static final PotentialValuesBasedCallbacksNoValue<Boolean> BOOLEAN_NO_KEY = new PotentialValuesBasedCallbacksNoValue<>(
        ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL
    );
    @Unique
    private static PotentialValuesBasedCallbacksNoValue<Integer> valuesOf(List<Integer> values) {
        return new PotentialValuesBasedCallbacksNoValue<>(ImmutableList.copyOf(values), Codec.INT);
    }

    @Inject(method = "addOptions()V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectAddOptions(CallbackInfo ci) {
        Options.sanitizeFrameGenerationOptionsForAvailableHardware(true);
        List<Integer> frameGenerationModes = Options.getAvailableFrameGenerationModes();
        int multiplierReferenceMode = Options.getFrameGenerationMultiplierReferenceMode();
        List<Integer> frameGenerationMultipliers = Options.getAvailableFrameGenerationMultipliers(
            multiplierReferenceMode);

        SimpleOption<Integer>
            maxFps =
            new SimpleOption<>("options.framerateLimit",
                SimpleOption.emptyTooltip(),
                (optionText, value) -> value == 260 ?
                    getGenericValueText(optionText, Text.translatable("options.framerateLimit.max"))
                    :
                        getGenericValueText(optionText,
                            Text.translatable("options.framerate", value)),
                new SimpleOption.ValidatingIntSliderCallbacks(1, 26).withModifier(
                    value -> value * 10, value -> value / 10),
                Codec.intRange(10, 260),
                Options.maxFps,
                value -> Options.setMaxFps(value, true));

        int i = -1;
        Window
            window =
            MinecraftClient.getInstance()
                .getWindow();
        Monitor monitor = window.getMonitor();
        int j;
        if (monitor == null) {
            j = -1;
        } else {
            Optional<VideoMode> optional = window.getFullscreenVideoMode();
            j =
                optional.map(monitor::findClosestVideoModeIndex)
                    .orElse(-1);
        }

        SimpleOption<Integer>
            fullScreenResolutionOption =
            new SimpleOption<>("options.fullscreen.resolution", SimpleOption.emptyTooltip(),
                (optionText, value) -> {
                    if (monitor == null) {
                        return Text.translatable("options.fullscreen.unavailable");
                    } else if (value == -1) {
                        return getGenericValueText(optionText,
                            Text.translatable("options.fullscreen.current"));
                    } else {
                        VideoMode videoMode = monitor.getVideoMode(value);
                        return getGenericValueText(optionText,
                            Text.translatable("options.fullscreen.entry",
                                videoMode.getWidth(),
                                videoMode.getHeight(),
                                videoMode.getRefreshRate(),
                                videoMode.getRedBits() + videoMode.getGreenBits() +
                                    videoMode.getBlueBits()));
                    }
                }, new SimpleOption.ValidatingIntSliderCallbacks(-1,
                monitor != null ? monitor.getVideoModeCount() - 1 : -1), j, value -> {
                if (monitor != null) {
                    window.setFullscreenVideoMode(
                        value == -1 ? Optional.empty() : Optional.of(monitor.getVideoMode(value)));
                }
            });

        SimpleOption<Boolean> enableVsync = SimpleOption.ofBoolean("options.vsync", Options.vsync,
            value -> {
                if (MinecraftClient.getInstance()
                    .getWindow() != null) {
                    Options.setVsync(value, true);
                }
            });

        SimpleOption<Integer> frameGenerationMethod = new SimpleOption<>(
            Options.FRAME_GENERATION_METHOD_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(Options.frameGenerationModeTranslationKey(value))),
            valuesOf(frameGenerationModes),
            Options.frameGenerationMode,
            value -> {
                Options.setFrameGenerationMode(value, true);
                int maxMultiplier = Options.getMaxFrameGenerationMultiplier(value);
                if (maxMultiplier >= 2 && Options.frameGenerationMultiplier > maxMultiplier) {
                    Options.setFrameGenerationMultiplier(maxMultiplier, true);
                }
            });

        SimpleOption<Integer> frameGenerationMultiplier = frameGenerationMultipliers.isEmpty() ? null :
            new SimpleOption<>(
                Options.FRAME_GENERATION_MULTIPLIER_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(value + "x")),
                valuesOf(frameGenerationMultipliers),
                Options.frameGenerationMultiplier,
                value -> Options.setFrameGenerationMultiplier(value, true));

        SimpleOption<Integer>
            chunkBuildingBatchSize =
            new SimpleOption<>(Options.CHUNK_BUILDING_BATCH_SIZE_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(Integer.toString(value))),
                new SimpleOption.ValidatingIntSliderCallbacks(Options.CHUNK_BUILDING_BATCH_SIZE_MIN,
                    Options.CHUNK_BUILDING_BATCH_SIZE_MAX),
                Codec.intRange(Options.CHUNK_BUILDING_BATCH_SIZE_MIN,
                    Options.CHUNK_BUILDING_BATCH_SIZE_MAX),
                Options.chunkBuildingBatchSize,
                value -> {
                    Options.setChunkBuildingBatchSize(value, true);
                });

        SimpleOption<Integer>
            chunkBuildingTotalBatches =
            new SimpleOption<>(Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(Integer.toString(value))),
                new SimpleOption.ValidatingIntSliderCallbacks(Options.CHUNK_BUILDING_TOTAL_BATCHES_MIN,
                    Options.CHUNK_BUILDING_TOTAL_BATCHES_MAX),
                Codec.intRange(Options.CHUNK_BUILDING_TOTAL_BATCHES_MIN,
                    Options.CHUNK_BUILDING_TOTAL_BATCHES_MAX),
                Options.chunkBuildingTotalBatches,
                value -> {
                    Options.setChunkBuildingTotalBatches(value, true);
                });

        SimpleOption<Integer>
            chunkBuildingThreads =
            new SimpleOption<>(Options.CHUNK_BUILDING_THREADS_KEY, SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(Integer.toString(value))),
                new SimpleOption.ValidatingIntSliderCallbacks(Options.CHUNK_BUILDING_THREADS_MIN,
                    Options.getMaxChunkBuildingThreads()),
                Codec.intRange(Options.CHUNK_BUILDING_THREADS_MIN, Options.getMaxChunkBuildingThreads()),
                Options.chunkBuildingThreads,
                value -> Options.setChunkBuildingThreads(value, true));

        SimpleOption<Boolean> collectChunkEmission = SimpleOption.ofBoolean(
            Options.COLLECT_CHUNK_EMISSION_KEY,
            Options.collectChunkEmission,
            value -> Options.setCollectChunkEmission(value, true));

        SimpleOption<Boolean> pipelineSettings = new SimpleOption<>(Options.PIPELINE_SETUP_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> optionText,
            BOOLEAN_NO_KEY,
            false,
            value -> {
                MinecraftClient.getInstance()
                    .setScreen(new RenderPipelineScreen((VideoOptionsScreen) (Object) this));
            });


        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_GAMEPLAY), body));
        SimpleOption[] optionsGameplay = new SimpleOption[]{
            gameOptions.getGraphicsMode(),
            gameOptions.getViewDistance(),
            gameOptions.getSimulationDistance(),
            gameOptions.getGuiScale(),
            gameOptions.getAttackIndicator(),
            gameOptions.getGamma(),
            gameOptions.getCloudRenderMode(),
            gameOptions.getParticles(),
            gameOptions.getDistortionEffectScale(),
            gameOptions.getEntityDistanceScaling(),
            gameOptions.getFovEffectScale(),
            gameOptions.getShowAutosaveIndicator(),
            gameOptions.getGlintSpeed(),
            gameOptions.getGlintStrength(),
            gameOptions.getMenuBackgroundBlurriness(),
            gameOptions.getBobView(),
        };
        this.body.addSingleOptionEntry(gameOptions.getBiomeBlendRadius());
        this.body.addSingleOptionEntry(gameOptions.getMipmapLevels());
        this.body.addAll(optionsGameplay);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_WINDOW), body));
        SimpleOption[] optionsWindow = new SimpleOption[]{
            maxFps,
            enableVsync,
            gameOptions.getFullscreen(),
        };
        this.body.addAll(optionsWindow);
        this.body.addSingleOptionEntry(fullScreenResolutionOption);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_FRAME_GENERATION), body));
        this.body.addSingleOptionEntry(frameGenerationMethod);
        if (frameGenerationMultiplier != null) {
            this.body.addSingleOptionEntry(frameGenerationMultiplier);
        }

        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_TERRAIN), body));
        this.body.addSingleOptionEntry(chunkBuildingBatchSize);
        this.body.addSingleOptionEntry(chunkBuildingTotalBatches);
        this.body.addSingleOptionEntry(chunkBuildingThreads);
        this.body.addSingleOptionEntry(collectChunkEmission);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_PIPELINE), body));
        this.body.addSingleOptionEntry(pipelineSettings);

        ci.cancel();
    }
}
