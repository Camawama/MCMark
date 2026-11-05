package net.camacraft.mcmark.client;

import com.mojang.blaze3d.platform.GlUtil;
import net.camacraft.mcmark.ClientHook;
import net.camacraft.mcmark.MCMarkConfig;
import net.camacraft.mcmark.metrics.FrameStats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Physical-client implementation of {@link ClientHook}, installed during client setup. */
public class MCMarkClientHooks implements ClientHook {

    @Override
    public boolean hasClient() {
        return true;
    }

    @Override
    public void beginRenderSegment(double seconds, float pitch, float startYaw) {
        RenderTestController.beginSegment(seconds, pitch, startYaw);
    }

    @Override
    public boolean isRenderSegmentDone() {
        return RenderTestController.isSegmentDone();
    }

    @Override
    public FrameStats takeRenderStats() {
        return RenderTestController.takeStats();
    }

    @Override
    public List<String> videoSettingsDeviations() {
        return VideoSettingsManager.deviations();
    }

    @Override
    public boolean applyStandardVideoSettings() {
        if (!MCMarkConfig.CLIENT.autoApplyStandardVideoSettings.get()) return false;
        VideoSettingsManager.apply();
        return true;
    }

    @Override
    public void restoreVideoSettings() {
        VideoSettingsManager.restore();
    }

    @Override
    public Map<String, String> captureClientEnvironment() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            map.put("cpu", GlUtil.getCpuInfo());
            map.put("gpuRenderer", GlUtil.getRenderer());
            map.put("gpuVendor", GlUtil.getVendor());
            map.put("openGlVersion", GlUtil.getOpenGLVersion());
        } catch (Exception ignored) {
            // GL info unavailable (e.g. headless data runs)
        }
        map.putAll(VideoSettingsManager.settingsSnapshot());
        return map;
    }
}
