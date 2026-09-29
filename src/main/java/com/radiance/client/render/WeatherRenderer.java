package com.radiance.client.render;

import com.radiance.client.constant.Constants;
import com.radiance.client.constant.Constants.PostRenderFlags;
import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public final class WeatherRenderer {

    private static final Identifier RAIN_TEXTURE = new Identifier("minecraft",
        "textures/environment/rain.png");
    private static final Identifier SNOW_TEXTURE = new Identifier("minecraft",
        "textures/environment/snow.png");
    private static final String WEATHER_RAIN_CONTENT = "/weather/rain";
    private static final String WEATHER_SNOW_CONTENT = "/weather/snow";
    private static final RenderLayer WEATHER_RAIN_ALL_MASK = RenderLayers.createWeatherLayer(
        "weather_rain_all", RAIN_TEXTURE, true);
    private static final RenderLayer WEATHER_RAIN_COLOR_MASK = RenderLayers.createWeatherLayer(
        "weather_rain_color", RAIN_TEXTURE, false);
    private static final RenderLayer WEATHER_SNOW_ALL_MASK = RenderLayers.createWeatherLayer(
        "weather_snow_all", SNOW_TEXTURE, true);
    private static final RenderLayer WEATHER_SNOW_COLOR_MASK = RenderLayers.createWeatherLayer(
        "weather_snow_color", SNOW_TEXTURE, false);

    private static WeatherRenderer active;

    private EntityProxy.EntityRenderDataList renderDataList;
    private Identifier activeTexture;

    public void begin() {
        this.renderDataList = new EntityProxy.EntityRenderDataList();
        this.activeTexture = null;
        active = this;
    }

    public void end() {
        if (active == this) {
            active = null;
        }
        this.activeTexture = null;

        if (this.renderDataList == null || this.renderDataList.isEmpty()) {
            this.renderDataList = null;
            return;
        }

        EntityProxy.queueBuild(null, this.renderDataList, 0.0f,
            Constants.Coordinates.CAMERA_SHIFT, false);
        this.renderDataList = null;
    }

    public static boolean captureActive(BuiltBuffer buffer) {
        WeatherRenderer weatherRenderer = active;
        return weatherRenderer != null && weatherRenderer.capture(buffer);
    }

    public static boolean isCaptureActive() {
        WeatherRenderer weatherRenderer = active;
        return weatherRenderer != null && weatherRenderer.renderDataList != null;
    }

    public static void setActiveTexture(int textureSlot, Identifier texture) {
        WeatherRenderer weatherRenderer = active;
        if (textureSlot == 0 && weatherRenderer != null) {
            weatherRenderer.activeTexture = texture;
        }
    }

    private boolean capture(BuiltBuffer buffer) {
        CapturedWeatherLayer capturedLayer = getCapturedWeatherLayer(this.activeTexture);
        if (capturedLayer == null || this.renderDataList == null) {
            return false;
        }

        EntityProxy.EntityRenderData entityRenderData = new EntityProxy.EntityRenderData(0, 0, 0,
            0, 0, PostRenderFlags.WEATHER.getValue(), -1, true);
        entityRenderData.add(new EntityProxy.EntityRenderLayer(capturedLayer.renderLayer(), buffer,
            false, capturedLayer.contentName()));
        this.renderDataList.add(entityRenderData);
        return true;
    }

    private static CapturedWeatherLayer getCapturedWeatherLayer(Identifier texture) {
        boolean allMask = MinecraftClient.isFabulousGraphicsOrBetter();
        if (RAIN_TEXTURE.equals(texture)) {
            return new CapturedWeatherLayer(
                allMask ? WEATHER_RAIN_ALL_MASK : WEATHER_RAIN_COLOR_MASK,
                WEATHER_RAIN_CONTENT);
        }
        if (SNOW_TEXTURE.equals(texture)) {
            return new CapturedWeatherLayer(
                allMask ? WEATHER_SNOW_ALL_MASK : WEATHER_SNOW_COLOR_MASK,
                WEATHER_SNOW_CONTENT);
        }
        return null;
    }

    private record CapturedWeatherLayer(RenderLayer renderLayer, String contentName) {
    }
}
