package com.radiance.mixins.vulkan_options;

import static net.minecraft.client.option.GameOptions.getGenericValueText;
import static net.minecraft.client.option.InactivityFpsLimit.AFK;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.gui.PotentialValuesBasedCallbacksNoValue;
import com.radiance.client.gui.RenderPipelineScreen;
import com.radiance.client.gui.ScenarioColorGradingScreen;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.option.InactivityFpsLimit;
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
    private static final Text INACTIVITY_FPS_LIMIT_MINIMIZED_TOOLTIP = Text.translatable(
        "options.inactivityFpsLimit.minimized.tooltip");
    @Unique
    private static final Text INACTIVITY_FPS_LIMIT_AFK_TOOLTIP = Text.translatable(
        "options.inactivityFpsLimit.afk.tooltip");

    @Unique
    private static final PotentialValuesBasedCallbacksNoValue<Boolean> BOOLEAN_NO_KEY = new PotentialValuesBasedCallbacksNoValue<>(
        ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL
    );

    @Inject(method = "addOptions()V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectAddOptions(CallbackInfo ci) {
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
                value -> {
                    MinecraftClient.getInstance()
                        .getInactivityFpsLimiter()
                        .setMaxFps(value);
                    Options.setMaxFps(value, true);
                });

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

        SimpleOption<InactivityFpsLimit> inactivityFpsLimit = new SimpleOption<>(
            "options.inactivityFpsLimit",
            option -> {
                return switch (option) {
                    case MINIMIZED -> Tooltip.of(
                        INACTIVITY_FPS_LIMIT_MINIMIZED_TOOLTIP);
                    case AFK -> Tooltip.of(INACTIVITY_FPS_LIMIT_AFK_TOOLTIP);
                };
            },
            SimpleOption.enumValueText(),
            new SimpleOption.PotentialValuesBasedCallbacks<>(Arrays.asList(
                InactivityFpsLimit.values()),
                InactivityFpsLimit.Codec),
            AFK,
            inactivityLimit -> {
                Options.setInactivityFpsLimit(
                    inactivityLimit == AFK ? 30 : 9, true);
            });

        SimpleOption<Boolean> enableVsync = SimpleOption.ofBoolean("options.vsync", Options.vsync,
            value -> {
                if (MinecraftClient.getInstance()
                    .getWindow() != null) {
                    Options.setVsync(value, true);
                }
            });

        SimpleOption<Boolean> enableHdr = SimpleOption.ofBoolean(Options.HDR_ENABLED_KEY,
            Options.hdrEnabled,
            value -> {
                if (MinecraftClient.getInstance()
                    .getWindow() != null) {
                    Options.setHdrEnabled(value, true);
                }
            });

        SimpleOption<Integer> hdrMinLuminance = new SimpleOption<>(Options.HDR_MIN_LUMINANCE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(String.format("%.2f nits", value / 100.0f))),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 1000),
            Codec.intRange(0, 1000),
            Math.round(Options.hdrMinLuminance * 100.0f),
            value -> Options.setHdrMinLuminance(value / 100.0f, true));

        SimpleOption<Integer> hdrMaxLuminance = new SimpleOption<>(Options.HDR_MAX_LUMINANCE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(value + " nits")),
            new SimpleOption.ValidatingIntSliderCallbacks(100, 4000),
            Codec.intRange(100, 4000),
            Math.round(Options.hdrMaxLuminance),
            value -> Options.setHdrMaxLuminance(value, true));

        SimpleOption<Integer> hdrRollOff = new SimpleOption<>(Options.HDR_ROLL_OFF_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(String.format("%.2f", value / 100.0f))),
            new SimpleOption.ValidatingIntSliderCallbacks(25, 400),
            Codec.intRange(25, 400),
            Math.round(Options.hdrRollOff * 100.0f),
            value -> Options.setHdrRollOff(value / 100.0f, true));

        SimpleOption<Integer> sdrBrightness = new SimpleOption<>(Options.SDR_BRIGHTNESS_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(value + " nits")),
            new SimpleOption.ValidatingIntSliderCallbacks(80, 500),
            Codec.intRange(80, 500),
            Math.round(Options.sdrBrightness),
            value -> Options.setSdrBrightness(value, true));

        SimpleOption<Integer>
            chunkBuildingBatchSize =
            new SimpleOption<>(Options.CHUNK_BUILDING_BATCH_SIZE_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(Integer.toString(value))),
                new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
                Codec.intRange(1, 32),
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
                new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
                Codec.intRange(1, 32),
                Options.chunkBuildingTotalBatches,
                value -> {
                    Options.setChunkBuildingTotalBatches(value, true);
                });

        SimpleOption<Integer>
            chunkBuildingThreads =
            new SimpleOption<>(Options.CHUNK_BUILDING_THREADS_KEY, SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(Integer.toString(value))),
                new SimpleOption.ValidatingIntSliderCallbacks(1,
                    Options.getMaxChunkBuildingThreads()),
                Codec.intRange(1, Options.getMaxChunkBuildingThreads()),
                Options.chunkBuildingThreads,
                value -> Options.setChunkBuildingThreads(value, true));

        SimpleOption<Boolean> collectChunkEmission = SimpleOption.ofBoolean(
            Options.COLLECT_CHUNK_EMISSION_KEY,
            Options.collectChunkEmission,
            value -> Options.setCollectChunkEmission(value, true));

        SimpleOption<Integer> sunPathTilt = new SimpleOption<>(
            Options.SUN_PATH_TILT_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(value + "°")),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 45),
            Codec.intRange(0, 45),
            Options.sunPathTilt,
            value -> Options.setSunPathTilt(value, true));

        SimpleOption<Integer> sunSize = new SimpleOption<>(
            Options.SUN_SIZE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(value + "%")),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 100),
            Codec.intRange(1, 100),
            Options.sunSize,
            value -> Options.setSunSize(value, true));

        SimpleOption<Boolean> pipelineSettings = new SimpleOption<>(Options.PIPELINE_SETUP_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> optionText,
            BOOLEAN_NO_KEY,
            false,
            value -> {
                MinecraftClient.getInstance()
                    .setScreen(new RenderPipelineScreen((VideoOptionsScreen) (Object) this));
            });

        SimpleOption<Boolean> scenarioColorGrading = new SimpleOption<>("options.video.scenario_color_grading",
            SimpleOption.emptyTooltip(),
            (optionText, value) -> optionText,
            BOOLEAN_NO_KEY,
            false,
            value -> {
                MinecraftClient.getInstance()
                    .setScreen(new ScenarioColorGradingScreen((VideoOptionsScreen) (Object) this));
            });

        // Adding categories and options
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_GAMEPLAY), body));
        SimpleOption[] optionsGameplay = new SimpleOption[]{ //
            gameOptions.getGraphicsMode(), //
            gameOptions.getViewDistance(), //
            gameOptions.getSimulationDistance(), //
            gameOptions.getGuiScale(), //
            gameOptions.getAttackIndicator(), //
            gameOptions.getGamma(), //
            gameOptions.getCloudRenderMode(), //
            gameOptions.getParticles(), //
            gameOptions.getDistortionEffectScale(), //
            gameOptions.getEntityDistanceScaling(), //
            gameOptions.getFovEffectScale(), //
            gameOptions.getShowAutosaveIndicator(), //
            gameOptions.getGlintSpeed(), //
            gameOptions.getGlintStrength(), //
            gameOptions.getMenuBackgroundBlurriness(), //
            gameOptions.getBobView(), //
        };
        this.body.addSingleOptionEntry(gameOptions.getBiomeBlendRadius());
        this.body.addSingleOptionEntry(gameOptions.getMipmapLevels());
        this.body.addAll(optionsGameplay);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_WINDOW), body));
        SimpleOption[] optionsWindow = new SimpleOption[]{ //
            maxFps, //
            inactivityFpsLimit, //
            enableVsync, //
            gameOptions.getFullscreen(), //
        };
        this.body.addAll(optionsWindow);
        this.body.addSingleOptionEntry(fullScreenResolutionOption);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_HDR), body));
        this.body.addSingleOptionEntry(enableHdr);
        this.body.addSingleOptionEntry(hdrMinLuminance);
        this.body.addSingleOptionEntry(hdrMaxLuminance);
        this.body.addSingleOptionEntry(hdrRollOff);
        this.body.addSingleOptionEntry(sdrBrightness);
        this.body.addSingleOptionEntry(scenarioColorGrading);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_TERRAIN), body));
        this.body.addSingleOptionEntry(chunkBuildingBatchSize);
        this.body.addSingleOptionEntry(chunkBuildingTotalBatches);
        this.body.addSingleOptionEntry(chunkBuildingThreads);
        this.body.addSingleOptionEntry(collectChunkEmission);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_SUN), body));
        this.body.addSingleOptionEntry(sunSize);
        this.body.addSingleOptionEntry(sunPathTilt);

        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_PIPELINE), body));
        this.body.addSingleOptionEntry(pipelineSettings);

        ci.cancel();
    }
}
