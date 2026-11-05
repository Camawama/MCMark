package net.camacraft.mcmark;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * MCMark configuration.
 *
 * <p>Deliberately limited to integration/cosmetic options: benchmark workloads, durations, and
 * scoring constants live in {@link net.camacraft.mcmark.spec.BenchmarkSpec} and are NOT
 * configurable, so no config change can produce an unfairly inflated score.</p>
 */
public final class MCMarkConfig {
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        COMMON = new Common(common);
        COMMON_SPEC = common.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        CLIENT = new Client(client);
        CLIENT_SPEC = client.build();
    }

    private MCMarkConfig() {}

    public static final class Common {
        public final ForgeConfigSpec.ConfigValue<String> apiBaseUrl;
        public final ForgeConfigSpec.BooleanValue uploadEnabled;
        public final ForgeConfigSpec.BooleanValue autoStartInBenchmarkWorlds;
        public final ForgeConfigSpec.ConfigValue<String> defaultTargetName;

        Common(ForgeConfigSpec.Builder b) {
            b.push("online");
            apiBaseUrl = b
                    .comment("Base URL of the MCMark leaderboard API.",
                            "Placeholder until the MCMark website launches — leave as-is.")
                    .define("apiBaseUrl", "https://api.mcmark.net");
            uploadEnabled = b
                    .comment("Whether online result upload is enabled.",
                            "The MCMark leaderboard is NOT live yet; this stays false until launch.",
                            "Results are always saved locally regardless of this setting.")
                    .define("uploadEnabled", false);
            b.pop();

            b.push("runs");
            autoStartInBenchmarkWorlds = b
                    .comment("Automatically start a standard run when loading an official MCMark benchmark world.")
                    .define("autoStartInBenchmarkWorlds", true);
            defaultTargetName = b
                    .comment("Default label recorded on results (e.g. your modpack name).",
                            "Can be changed per-result with /mcmark label <name>.")
                    .define("defaultTargetName", "Unnamed");
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue titleScreenButton;
        public final ForgeConfigSpec.BooleanValue showHud;
        public final ForgeConfigSpec.BooleanValue autoApplyStandardVideoSettings;

        Client(ForgeConfigSpec.Builder b) {
            b.push("ui");
            titleScreenButton = b
                    .comment("Show the MCMark button on the title screen.")
                    .define("titleScreenButton", true);
            showHud = b
                    .comment("Show the benchmark progress HUD while a run is active.")
                    .define("showHud", true);
            b.pop();

            b.push("fairness");
            autoApplyStandardVideoSettings = b
                    .comment("Temporarily apply MCMark's standard video settings during the render phase",
                            "(render distance 12, vsync off, unlimited FPS, fancy graphics...).",
                            "Your settings are restored afterwards and never saved over.",
                            "If disabled and your settings deviate, the run is flagged non-standard.")
                    .define("autoApplyStandardVideoSettings", true);
            b.pop();
        }
    }
}
