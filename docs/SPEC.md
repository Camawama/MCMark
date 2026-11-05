# MCMark Benchmark Specification 1.0.0

This document defines benchmark spec **1.0.0** and result schema **1** — the contract between
the MCMark mod and the future MCMark website. Field names and workload definitions here are
frozen; any workload change requires a spec version bump, and scores are never comparable
across spec versions.

## World

| Property | Value |
|---|---|
| Seed | `85035089581643` (`0x4D434D41524B`, ASCII "MCMARK") |
| Generation | Normal overworld preset, structures on, no bonus chest |
| Game rules | daylight/weather cycle off, mob/patrol/trader spawning off, insomnia off, randomTickSpeed 3, fireTick on, mobGriefing on |
| Time / weather / difficulty | noon (6000), clear, Normal |
| World name | must start with `MCMark` for auto-start |

The engine enforces gamerules/time/weather/difficulty at run start in any world (and restores
the previous values afterwards), but only the official seed produces a standard chunk-gen and
render result.

## Phases (STANDARD suite)

All durations are server ticks. The QUICK suite scales durations ×0.4 (min 60) and workload
counts ×0.5 and is always flagged `QUICK_SUITE` (never leaderboard-eligible).

| # | id | Workload | Measure | Metric |
|---|---|---|---|---|
| 1 | `warmup` | 40 husks + 20 cows, 300 ticks | — | unscored (JIT warmup) |
| 2 | `idle` | empty loaded arena | 300 ticks | median MSPT |
| 3 | `chunk_gen` | 15×15 chunks at chunk (200,200), 2/tick, synchronous FULL generation | per-chunk wall time | chunks/s |
| 4 | `entity_stress` | 100 husks + 50 villagers + 60 cows sealed in 51×51 arena | 600 ticks | median MSPT |
| 5 | `item_stress` | 2000 items (70% non-stackable) + 250 stackables every 40 ticks | 600 ticks | median MSPT |
| 6 | `redstone` | 144 observer-pair clocks (2gt) each driving 2 redstone lamps | 600 ticks | median MSPT |
| 7 | `fluids` | 9×9 water sources flooding a 3-tier stone terrace pyramid | 400 ticks | median MSPT |
| 8 | `render` | 2×15s 360° camera sweeps over the chunk-gen terrain (surface+35 @ pitch 25°, surface+90 @ pitch 55°), standard video settings; a 27×27-chunk area is pre-generated (unmeasured) first so no chunk generation/streaming occurs during measurement | frame times | 0.6·avgFPS + 0.4·1%-low FPS |

The arena is at (10000, 195..204, 10000), the redstone platform east of it; the region is
force-loaded during the run and every placed block/entity is removed afterwards. Each world's
standard chunk-gen region is single-use (tracked in saved data); reruns use offset regions and
are flagged `CHUNKGEN_REGION_REUSED`.

### Standard video settings (render phase)

Render distance 12, simulation distance 12, vsync off, FPS limit unlimited (260), graphics
Fancy, clouds Fancy, ambient occlusion on, entity shadows on, biome blend 5×5. Applied
temporarily (config permitting) and restored afterwards; never persisted.

## Scoring

Per-phase: `score = 1000 × ratio`, ratio clamped to [0.01, 100]:

- time metrics (MSPT): `ratio = baseline / measured`
- rate metrics (chunks/s, FPS blend): `ratio = measured / baseline`

Reference baselines (spec 1.0.0): idle 2.0 ms, chunk_gen 40 chunks/s, entity 9.0 ms,
items 8.0 ms, redstone 5.0 ms, fluids 6.0 ms, render 200 FPS.

- **Server score** = 1000 × weighted geometric mean (weights: idle 0.10, chunk_gen 0.20,
  entity 0.25, items 0.15, redstone 0.15, fluids 0.15)
- **Client score** = render phase score
- **Overall** = 1000 × (server/1000)^0.65 × (client/1000)^0.35, or server score alone when no
  client phases ran

Geometric means prevent any single phase from dominating. Absolute scores compare hardware +
software together; same-machine comparisons (modpack vs. vanilla baseline) cancel hardware out,
and the website will normalize across machines using submitted baseline runs.

## Standard flags

`QUICK_SUITE`, `NON_STANDARD_SEED`, `CHUNKGEN_REGION_REUSED`, `NO_CLIENT_PHASES`,
`NON_STANDARD_VIDEO_SETTINGS`, `RENDER_INTERRUPTED`, `RENDER_TIMEOUT`, `DEDICATED_SERVER`,
`MULTIPLE_PLAYERS` break standard eligibility; `PROFILER_ACTIVE` is informational.
`uploadEligible = standard AND suite == STANDARD`.

## Result schema (upload payload, schema 1)

One JSON object per run (see `net.camacraft.mcmark.result.BenchmarkResult`):

```jsonc
{
  "schemaVersion": 1,
  "specVersion": "1.0.0",
  "modVersion": "0.1.0",
  "resultId": "uuid",
  "createdAt": "2026-07-19T12:34:56Z",
  "suite": "STANDARD",
  "target": { "type": "MODPACK", "name": "All The Mods 9" },
  "scores": { "overall": 812.4, "server": 790.1, "client": 856.2 },
  "phases": [
    { "id": "idle", "display": "Idle Tick", "metric": "median_mspt", "unit": "ms",
      "lowerIsBetter": true, "value": 2.41, "score": 829.9,
      "stats": { "mean_ms": 2.5, "p95_ms": 3.1, "p99_ms": 4.0, "max_ms": 9.2,
                 "std_dev_ms": 0.4, "ticks": 300 } }
  ],
  "standard": true,
  "uploadEligible": true,
  "flags": [],
  "flagDescriptions": [],
  "videoDeviations": [],
  "environment": {
    "os": "Windows 11 10.0", "arch": "amd64",
    "javaVersion": "17.0.1", "javaVendor": "Oracle Corporation",
    "jvmArgs": ["-Xmx8G"], "cpuThreads": 16, "maxHeapMb": 8192,
    "minecraftVersion": "1.20.1", "forgeVersion": "47.4.16", "dist": "CLIENT",
    "cpuName": "…", "gpuRenderer": "…", "gpuVendor": "…", "openGlVersion": "…",
    "clientSettings": { "renderDistance": "12", "vsync": "false" }
  },
  "mods": [ { "id": "embeddium", "name": "Embeddium", "version": "0.3.31", "perfCategory": "RENDERING" } ],
  "perfMods": {
    "optimizersPresent": true, "profilersPresent": false,
    "classification": "OPTIMIZED",
    "byCategory": { "RENDERING": ["embeddium"] }
  },
  "integrity": { "algorithm": "SHA-256", "contentHash": "…" }
}
```

`integrity.contentHash` is the SHA-256 of the compact JSON with `contentHash` set to the empty
string. It is tamper *evidence*, not tamper *proof* — authoritative validation (recomputation,
plausibility checks, baseline cross-checks) happens server-side after upload.

## Upload API (v1, placeholder)

- `GET  {base}/v1/status` → 200 when live
- `POST {base}/v1/results` with the payload above; headers `Content-Type: application/json`,
  `User-Agent: MCMark/<version>`, `X-MCMark-Schema: 1`

Gated behind `uploadEnabled=false` in `mcmark-common.toml` until the website launches.
