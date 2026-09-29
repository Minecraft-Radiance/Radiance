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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.Monitor;
import net.minecraft.client.util.VideoMode;
import net.minecraft.client.util.Window;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoOptionsScreen.class)
public abstract class VideoOptionsScreenMixins extends GameOptionsScreen {

    @Unique
    private static final PotentialValuesBasedCallbacksNoValue<Boolean> BOOLEAN_NO_KEY =
        new PotentialValuesBasedCallbacksNoValue<>(ImmutableList.of(Boolean.TRUE, Boolean.FALSE),
            Codec.BOOL);

    @Unique
    private static PotentialValuesBasedCallbacksNoValue<Integer> valuesOf(List<Integer> values) {
        return new PotentialValuesBasedCallbacksNoValue<>(ImmutableList.copyOf(values), Codec.INT);
    }

    protected VideoOptionsScreenMixins(Screen parent, GameOptions gameOptions, Text title) {
        super(parent, gameOptions, title);
    }

    @Shadow
    private OptionListWidget list;

    @Inject(method = "init()V", at = @At("HEAD"), cancellable = true)
    private void redirectInit(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        Options.sanitizeFrameGenerationOptionsForAvailableHardware(true);
        List<Integer> frameGenerationModes = Options.getAvailableFrameGenerationModes();
        int multiplierReferenceMode = Options.getFrameGenerationMultiplierReferenceMode();
        List<Integer> frameGenerationMultipliers = Options.getAvailableFrameGenerationMultipliers(
            multiplierReferenceMode);

        this.list = new OptionListWidget(client, this.width, this.height, 32, this.height - 32,
            25);

        Window window = client.getWindow();
        Monitor monitor = window.getMonitor();
        int currentVideoMode;
        if (monitor == null) {
            currentVideoMode = -1;
        } else {
            Optional<VideoMode> optional = window.getVideoMode();
            currentVideoMode = optional.map(monitor::findClosestVideoModeIndex).orElse(-1);
        }

        SimpleOption<Integer> maxFps = new SimpleOption<>("options.framerateLimit",
            SimpleOption.emptyTooltip(),
            (optionText, value) -> value == 260
                ? getGenericValueText(optionText,
                Text.translatable("options.framerateLimit.max"))
                : getGenericValueText(optionText, Text.translatable("options.framerate", value)),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 26).withModifier(value -> value * 10,
                value -> value / 10),
            Codec.intRange(10, 260), Options.maxFps, value -> Options.setMaxFps(value, true));

        SimpleOption<Integer> fullScreenResolutionOption = new SimpleOption<>(
            "options.fullscreen.resolution", SimpleOption.emptyTooltip(),
            (optionText, value) -> {
                if (monitor == null) {
                    return Text.translatable("options.fullscreen.unavailable");
                }
                if (value == -1) {
                    return getGenericValueText(optionText,
                        Text.translatable("options.fullscreen.current"));
                }
                return getGenericValueText(optionText,
                    Text.literal(monitor.getVideoMode(value).toString()));
            },
            new SimpleOption.ValidatingIntSliderCallbacks(-1,
                monitor != null ? monitor.getVideoModeCount() - 1 : -1),
            currentVideoMode, value -> {
                if (monitor != null) {
                    window.setVideoMode(
                        value == -1 ? Optional.empty() : Optional.of(monitor.getVideoMode(value)));
                }
            });

        SimpleOption<Boolean> enableVsync = SimpleOption.ofBoolean("options.vsync", Options.vsync,
            value -> {
                if (client.getWindow() != null) {
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
                (optionText, value) -> getGenericValueText(optionText, Text.literal(value + "x")),
                valuesOf(frameGenerationMultipliers),
                Options.frameGenerationMultiplier,
                value -> Options.setFrameGenerationMultiplier(value, true));

        SimpleOption<Integer> chunkBuildingBatchSize = new SimpleOption<>(
            Options.CHUNK_BUILDING_BATCH_SIZE_KEY, SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(Options.CHUNK_BUILDING_BATCH_SIZE_MIN,
                Options.CHUNK_BUILDING_BATCH_SIZE_MAX), Codec.intRange(
                Options.CHUNK_BUILDING_BATCH_SIZE_MIN, Options.CHUNK_BUILDING_BATCH_SIZE_MAX),
            Options.chunkBuildingBatchSize, value -> Options.setChunkBuildingBatchSize(value,
            true));

        SimpleOption<Integer> chunkBuildingTotalBatches = new SimpleOption<>(
            Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY, SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(Options.CHUNK_BUILDING_TOTAL_BATCHES_MIN,
                Options.CHUNK_BUILDING_TOTAL_BATCHES_MAX), Codec.intRange(
                Options.CHUNK_BUILDING_TOTAL_BATCHES_MIN, Options.CHUNK_BUILDING_TOTAL_BATCHES_MAX),
            Options.chunkBuildingTotalBatches, value -> Options.setChunkBuildingTotalBatches(
            value, true));

        SimpleOption<Integer> chunkBuildingThreads = new SimpleOption<>(
            Options.CHUNK_BUILDING_THREADS_KEY, SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(Options.CHUNK_BUILDING_THREADS_MIN,
                Options.getMaxChunkBuildingThreads()),
            Codec.intRange(Options.CHUNK_BUILDING_THREADS_MIN, Options.getMaxChunkBuildingThreads()),
            Options.chunkBuildingThreads, value -> Options.setChunkBuildingThreads(
            value, true));

        SimpleOption<Boolean> collectChunkEmission = SimpleOption.ofBoolean(
            Options.COLLECT_CHUNK_EMISSION_KEY,
            Options.collectChunkEmission,
            value -> Options.setCollectChunkEmission(value, true));

        SimpleOption<Boolean> pipelineSettings = new SimpleOption<>(Options.PIPELINE_SETUP_KEY,
            SimpleOption.emptyTooltip(), (optionText, value) -> optionText, BOOLEAN_NO_KEY, false,
            value -> client.setScreen(new RenderPipelineScreen((VideoOptionsScreen) (Object) this)));

        this.list.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_GAMEPLAY), this.list));
        this.list.addSingleOptionEntry(this.gameOptions.getBiomeBlendRadius());
        this.list.addAll(new SimpleOption[]{
            this.gameOptions.getGraphicsMode(),
            this.gameOptions.getViewDistance(),
            this.gameOptions.getSimulationDistance(),
            this.gameOptions.getAo(),
            this.gameOptions.getBobView(),
            this.gameOptions.getGuiScale(),
            this.gameOptions.getAttackIndicator(),
            this.gameOptions.getGamma(),
            this.gameOptions.getCloudRenderMode(),
            this.gameOptions.getParticles(),
            this.gameOptions.getMipmapLevels(),
            this.gameOptions.getEntityShadows(),
            this.gameOptions.getDistortionEffectScale(),
            this.gameOptions.getEntityDistanceScaling(),
            this.gameOptions.getFovEffectScale(),
            this.gameOptions.getShowAutosaveIndicator(),
            this.gameOptions.getGlintSpeed(),
            this.gameOptions.getGlintStrength(),
        });

        this.list.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_WINDOW), this.list));
        this.list.addAll(new SimpleOption[]{
            maxFps,
            enableVsync,
            this.gameOptions.getFullscreen(),
        });
        this.list.addSingleOptionEntry(fullScreenResolutionOption);

        this.list.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_FRAME_GENERATION), this.list));
        this.list.addSingleOptionEntry(frameGenerationMethod);
        if (frameGenerationMultiplier != null) {
            this.list.addSingleOptionEntry(frameGenerationMultiplier);
        }

        this.list.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_TERRAIN), this.list));
        this.list.addSingleOptionEntry(chunkBuildingBatchSize);
        this.list.addSingleOptionEntry(chunkBuildingTotalBatches);
        this.list.addSingleOptionEntry(chunkBuildingThreads);
        this.list.addSingleOptionEntry(collectChunkEmission);

        this.list.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_PIPELINE), this.list));
        this.list.addSingleOptionEntry(pipelineSettings);

        this.addSelectableChild(this.list);
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
            client.options.write();
            window.applyVideoMode();
            client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 100, this.height - 27, 200, 20).build());

        ci.cancel();
    }
}
