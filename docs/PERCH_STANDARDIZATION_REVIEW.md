# Perch Standardisation Review

Reviewed 28 September 2026. Scope: retain the existing vanilla-style perch
system and retain the grace period's protection of the zero-cycle window.
Improve the transition to shared perch decisions, with shorter long waits a
secondary benefit; identical landing times are not the goal. Production
dragon behaviour is unchanged by this review; only the development benchmark
was corrected.

## Current behaviour

`HoldingPatternPhaseMixin` redirects the first `Random.nextInt(bound)` in
Minecraft 1.16.1's holding-pattern path-completion method. Inspection of the
installed mapped Minecraft bytecode confirms this is the landing decision:
`bound = alive crystals + 3`, and a zero roll enters `LANDING_APPROACH`.
This decision happens at a completed flight path, not once per second or tick.

`RngStandardization.nextDragonPerchRoll` consumes the original random draw on
every call. With Standardize Race RNG enabled, it replaces the result only at
dragon age 1,300 or later (65 seconds of dragon ticks at 20 TPS). Earlier perch
rolls remain vanilla; the grace period does not prohibit an early perch.

The replacement seed depends on the world seed and a global perch-check index.
Matching seeds, indexes and crystal counts give identical results, and other
standardised channels do not consume this sequence. The counter resets when
the race server is configured. It is not a timer or a maximum-wait guarantee.

Opening holding-pattern target heights have a separate deterministic sequence,
but only before age 1,300. Path-direction changes and strafe decisions retain
native randomness. Player position affects targeting, and live crystal counts
affect both flight routing and the chance of choosing a perch.

## Where unequal waits remain

1. The early perch decisions are not shared. One racer can perch during the
   first 65 seconds while another misses every early check.
2. Shared roll numbers do not imply shared times. Different paths or strafes
   reach the same indexed check at different dragon ages. The subsequent
   landing approach also takes time.
3. The deterministic sequence can still contain long failure streaks. There is
   no deadline or protection against exceptionally long waits.
4. Counters are global to the configured world session, not isolated to each
   dragon fight. In AA, different first-fight histories can leave racers at
   different indexes when they respawn the dragon. These counters are not
   persisted across restarting the integrated server.

## Grace-period tradeoff

Keep the existing roll substitution, native random draw consumption, crystal
dependence, and flight AI. Changing only the grace threshold changes when
seeded decisions start, not the configured per-check probability or the
dragon's landing mechanics. There is no forced perch at the threshold.

The 65-second threshold protects the zero-cycle window. Shortening or removing
it is not the requested solution. It is a mod policy rather than a vanilla age
gate, but its purpose must be retained. The no-grace measurements below are a
diagnostic comparison, not a proposed gameplay change.

The original grace-period commit (`1d9cfd8`) explicitly identifies protecting
the initial zero-cycle window as its purpose and deliberately leaves the shared
event counter untouched during grace.

The current shared perch counter does not advance during grace. The first
check after grace therefore starts at shared event zero, regardless of how
many vanilla checks already occurred. A candidate is to advance the shared
index at every enabled perch check while still returning the original vanilla
result during grace. This preserves those early roll outcomes and native RNG
consumption and removes the delayed start of the shared sequence.

That candidate does not imply a guaranteed reduction in perch time: racers
with different numbers of early checks would enter the shared portion at
different indexes, sacrificing the current common event-zero handoff. Shared
successes skipped during grace cannot be retroactively applied, and flights can
reach different numbers of checks. Changing early perch outcomes can itself
change the zero-cycle flight through the landing transition, even if movement
code is untouched. The desired scope of "vanilla zero-cycle behaviour" needs
to distinguish preserving early roll outcomes from merely preserving flight
mechanics before selecting an implementation. No production change has been
made. Keep the separate opening-height and optional fly-away assist unchanged.

## Validation and benchmark correction

The existing headless benchmark reset deterministic counters only once per
mode. Trial two therefore continued trial one's perch and height sequences,
although each trial was described as a fresh race. The corrected benchmark
resets both channels before every trial and logs each trial's spawn, decision,
touchdown, and timeout result.

The focused perch and opening-height unit tests passed (18 tests). They verify
stream isolation, native RNG consumption, grace boundaries, and height bounds;
they do not establish equal real flight durations.

The six-pair exploratory flight comparison uses one existing disposable End
world, a stationary fountain player, ten live crystals, and a 6,000-tick timeout.
It compares current grace with immediate roll standardisation. It does not
include the optional reduced-height assist, moving players, crystal destruction,
save/reload, or a respawned dragon. This sample cannot establish production tail
percentiles or establish an optimal grace duration.

| Grace | Mean actual perch after End entry | Observed range | Timeouts |
| --- | --- | --- | --- |
| Current: 1,300 ticks / 65 seconds | 137.91 seconds | 63.40–159.80 seconds | 0 / 6 |
| None | 120.66 seconds | 107.40–135.75 seconds | 0 / 6 |

No grace reduced the mean by 17.25 seconds and narrowed the observed range
in this particular sample. Five paired trials were faster; one was slower:
the current system's lucky 63.40-second perch became a 133.40-second perch.
Both modes retained flight-dependent variation. All six trials share the same
world seed and vary the dragon's native RNG; these are not six independent
samples of world-seeded perch sequences.

In particular, the 17.25-second sample difference is not an added grace-period
penalty. Per-check odds are unchanged on either side of the threshold. It is a
difference between particular roll sequences and the flights they induce.
The evidence does not establish that grace systematically makes perches slower.

The real target-selection preflight also passed 192 checks. The full command
completed successfully:

```powershell
.\gradlew.bat test --tests '*RngStandardizationDragon*' runPerchBenchmark `
  -PperchCompareGrace=true -PperchTrials=6 -PperchMaxTicks=6000 --offline
```

## Follow-up bug check

The production RNG implementation and holding-pattern redirects remain
unchanged. The full Java test suite passed: 485 passed, one intentionally
disabled test. A fresh headless run completed three paired vanilla/current
trials with zero crystals and no timeouts, and the mod build succeeded.

Added a development-only perch-hook preflight alongside the existing height
check. It passed 192 actual landing-decision cases across ages 0, 1299, and
1300, with zero and ten crystals, standardisation on/off, and varied event
indexes. Successful decisions also verify native RNG advancement. The existing
192 real opening-height checks passed as well.

One verification bug was fixed: benchmark exceptions were caught and stopped
the server, which could leave Gradle reporting success. The benchmark now logs
an explicit success marker only after both modes and cleanup finish; its Gradle
task requires a fresh marker and rejects logged benchmark failures.
An intentional `-PperchTrials=0` run confirmed that the server's validation
error now produces a failed Gradle task, rather than a false successful run.
