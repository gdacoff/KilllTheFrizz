package io.github.gdacoff.killthefrizz.util;

import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.stats.KtfStats;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Monitors server tick time (MSPT) and auto-disables the mod when the server is
 * consistently slow, re-enabling it once performance recovers.
 *
 * <p>Also publishes per-tick statistics: {@link KtfStats#SLEEPING_ENTITIES} is
 * refreshed from {@link KtfStats#CURRENT_TICK_SLEEPING} each tick, and
 * {@link KtfStats#SAVED_MS} is derived from the total skipped AI ticks.</p>
 */
public final class SlowdownMonitor {

    /** MSPT above which we consider the server slow. */
    public static final double SLOW_MSPT = 45.0;
    /** MSPT below which we consider the server recovered. */
    public static final double RECOVER_MSPT = 30.0;
    /** Consecutive slow ticks required before auto-disable. */
    public static final int SLOW_TICKS_THRESHOLD = 100;
    /** Consecutive fast ticks required before auto re-enable. */
    public static final int RECOVER_TICKS_THRESHOLD = 200;
    /** Estimated ms saved per skipped AI tick. */
    public static final double MS_PER_SKIPPED_TICK = 0.08;

    private static volatile long tickStartNanos = 0L;
    private static int slowTicks = 0;
    private static int fastTicks = 0;

    private SlowdownMonitor() {
    }

    /**
     * Records the start of a server tick. Called from {@link ServerTickEvent.Pre}.
     * The elapsed time between Pre and Post is the true MSPT for that tick.
     */
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        tickStartNanos = System.nanoTime();
    }

    /**
     * Measures the elapsed time since {@link #onServerTickPre(ServerTickEvent.Pre)}
     * and evaluates it against the slowdown thresholds. Also publishes per-tick stats.
     */
    public static void onServerTickPost(ServerTickEvent.Post event) {
        long start = tickStartNanos;
        tickStartNanos = 0L;
        if (start != 0L) {
            long elapsed = System.nanoTime() - start;
            if (elapsed > 0 && elapsed < 10_000_000_000L) { // sanity bound < 10s
                double mspt = elapsed / 1_000_000.0;
                evaluate(mspt);
            }
        }
        publishStats();
    }

    /**
     * Alias for {@link #onServerTickPost(ServerTickEvent.Post)} kept for backwards
     * compatibility with existing event registrations.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        onServerTickPost(event);
    }

    public static void publishStats() {
        KtfStats.SLEEPING_ENTITIES.set(KtfStats.CURRENT_TICK_SLEEPING.getAndSet(0));
        KtfStats.SAVED_MS.set((long) (KtfStats.SKIPPED_AI_TICKS.get() * MS_PER_SKIPPED_TICK));
    }

    public static void evaluateForTest(double mspt) {
        evaluate(mspt);
    }

    public static void resetForTest() {
        slowTicks = 0;
        fastTicks = 0;
        tickStartNanos = 0L;
        KtfConfig.setAutoDisabled(false);
    }

    private static void evaluate(double mspt) {
        if (!KtfConfig.getEffectiveCommon().autoDisableOnSlowdown()) {
            slowTicks = 0;
            fastTicks = 0;
            return;
        }

        if (mspt > SLOW_MSPT) {
            slowTicks++;
            fastTicks = 0;
            if (slowTicks >= SLOW_TICKS_THRESHOLD && !KtfConfig.isAutoDisabled()) {
                KtfConfig.setAutoDisabled(true);
                KtfLogger.warnRateLimited("auto_disable",
                        "Auto-disabled KilllTheFrizz: MSPT %.1f > %.1f for %d ticks",
                        mspt, SLOW_MSPT, slowTicks);
            }
        } else if (mspt < RECOVER_MSPT) {
            fastTicks++;
            slowTicks = 0;
            if (fastTicks >= RECOVER_TICKS_THRESHOLD && KtfConfig.isAutoDisabled()) {
                KtfConfig.setAutoDisabled(false);
                KtfLogger.info("KilllTheFrizz auto re-enabled: MSPT recovered to %.1f", mspt);
            }
        } else {
            // In the dead zone: reset both counters to avoid flapping.
            slowTicks = 0;
            fastTicks = 0;
        }
    }
}
