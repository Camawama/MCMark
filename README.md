# MCMark

**The community benchmark for Minecraft mods and modpacks.**

MCMark runs a standardized, deterministic benchmark suite inside Minecraft and produces a
comparable performance score. It's not a profiler (see spark/Observable for that) — it's a
*benchmark*: the same workload, every time, for everyone, so players can compare mods and
modpacks, and mod developers can compete on performance.

- **Minecraft**: 1.20.1 · **Loader**: Forge 47.4+
- **Package**: `net.camacraft.mcmark` · **Mod id**: `mcmark`

## How it works

1. Click **MCMark** on the title screen → **Create Benchmark World**. This creates a world with
   the official MCMark seed and settings; the standard run starts automatically a few seconds
   after loading (`/mcmark abort` to cancel).
2. The suite runs ~5 minutes through 8 phases: warmup, idle tick, chunk generation, entity
   stress, item stress, redstone, fluids, and a client render (FPS) test. A HUD shows progress.
3. You get an **overall score** (plus server and client sub-scores), printed in chat and saved
   as JSON under `<instance>/mcmark/results/`.

Higher is better; ~1000 ≈ the reference machine running vanilla + MCMark. Scores are most
meaningful compared **on the same machine** — e.g. run a vanilla `baseline`, then your modpack,
and compare.

### Commands

| Command | Description |
|---|---|
| `/mcmark run [standard\|quick] [mod\|modpack\|baseline]` | Start a run (quick = short, never leaderboard-eligible) |
| `/mcmark abort` | Abort the active run (or cancel auto-start) |
| `/mcmark status` | Engine status |
| `/mcmark results` | List recent local results |
| `/mcmark label <name>` | Name the latest result (e.g. your modpack) |
| `/mcmark upload` | Upload the latest result (once the leaderboard launches) |
| `/mcmark mods` | Show detected performance mods and run classification |
| `/mcmark env` | Show the environment fingerprint |

Every `/mcmark` command asks for operator level 2 (in singleplayer: a world with cheats on, as the benchmark
world is).

Dedicated servers can run the suite headless (server phases only; flagged accordingly). For
CI/automation, launch with `-Dmcmark.autorun=standard|quick` (or env `MCMARK_AUTORUN`) and a
run starts automatically shortly after server start — results land in `mcmark/results/` and
the summary is printed to the server log.

## Fairness by design

The credibility of a community benchmark lives or dies on trust. MCMark's answers:

- **The workload is code, not config.** Seeds, coordinates, entity counts, and measurement
  windows are constants in `BenchmarkSpec`, versioned as the *spec version* embedded in every
  result. No config tweak can inflate a score. Any workload change bumps the spec version, and
  cross-spec scores are never comparable.
- **Determinism.** Official-seed worlds generate identical terrain; arena workloads use fixed
  populations, geometry, and seeded RNG; measurement windows are tick-counted; medians (not
  means) resist stutter outliers.
- **Standard flags.** Anything that hurts comparability — quick suite, wrong seed, reused
  chunk-gen region, non-standard video settings, opening menus during the render test, more
  than one player, dedicated server — is recorded as a flag. Flagged runs are saved but not
  leaderboard-eligible.
- **Full transparency.** Every result embeds an environment fingerprint (OS, CPU, GPU, Java,
  JVM args, video settings) and the complete mod list with versions, plus a content hash for
  tamper evidence.
- **Performance-mod detection.** Known optimization mods (Embeddium, FerriteCore, ModernFix,
  Canary, …) are recognized and categorized; runs are classified **PURE** or **OPTIMIZED** so
  leaderboards can keep "with performance mods" and "without" separate. Profilers (spark, …)
  are noted informationally.
- **Level playing field for the render test.** MCMark temporarily applies standard video
  settings (render distance 12, vsync off, uncapped FPS, fancy graphics) and restores yours
  afterwards — nothing is ever written to your options file.

## The website (coming soon)

The MCMark companion website — community leaderboards sortable by mod/modpack, PURE/OPTIMIZED
category, hardware class, and spec version — is under construction. The mod is fully prepared:

- Results are stored locally in the exact upload schema (see `docs/SPEC.md`).
- The upload client (`/mcmark upload`, the Upload button) is implemented and versioned
  (`POST /v1/results`), gated behind `uploadEnabled` (default **false**) until launch.
- Nothing ever leaves your machine until you upload.

## Building

```
./gradlew build        # jar in build/libs/
./gradlew runClient    # dev client
```

## License

All Rights Reserved (see LICENSE.txt).
