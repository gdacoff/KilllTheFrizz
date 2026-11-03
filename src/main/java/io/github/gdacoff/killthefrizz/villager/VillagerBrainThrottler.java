package io.github.gdacoff.killthefrizz.villager;

import io.github.gdacoff.killthefrizz.config.EffectiveServer;
import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.mixin.BrainAccessor;
import io.github.gdacoff.killthefrizz.mixin.ExpirableValueAccessor;
import io.github.gdacoff.killthefrizz.mixin.SensorAccessor;
import io.github.gdacoff.killthefrizz.util.KtfLogger;
import java.util.Optional;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Adaptive brain throttling for villagers.
 *
 * <p>Stationary villagers (trading booths, sleeping in beds) do not need a full
 * 20 Hz brain tick. This class decides, per villager per tick, whether the vanilla
 * {@code Brain.tick} should run. Urgent states (trading, damaged, panicking,
 * navigating, moving, unemployed) always run at full rate.</p>
 *
 * <p>Fail-open: any unexpected exception returns {@code true} so vanilla behavior
 * is preserved. The error is logged at most once per rate-limit window.</p>
 */
public final class VillagerBrainThrottler {

    private static final double MOVING_VELOCITY_SQR = 0.0009;

    private VillagerBrainThrottler() {
    }

    public static boolean shouldTickBrain(Villager villager) {
        if (!KtfConfig.getEffectiveCommon().enabled() || KtfConfig.isAutoDisabled()) {
            return true;
        }
        EffectiveServer cfg = KtfConfig.getEffectiveServer();
        return shouldTickBrain(villager, cfg.villagerThrottlingEnabled(),
                cfg.villagerStallInterval(), cfg.villagerBedInterval());
    }

    public static boolean shouldTickBrain(Villager villager, boolean enabled, int stallInterval, int bedInterval) {
        if (!enabled) {
            return true;
        }
        try {
            if (villager.isTrading()) {
                return true;
            }
            if (villager.hurtTime > 0 || villager.isOnFire()) {
                return true;
            }
            if (villager.getBrain().isActive(Activity.PANIC)
                    || villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)
                    || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)) {
                return true;
            }
            if (!villager.getNavigation().isDone()) {
                return true;
            }
            if (villager.getDeltaMovement().horizontalDistanceSqr() > MOVING_VELOCITY_SQR) {
                return true;
            }

            long phase = (long) villager.tickCount + villager.getId();

            if (villager.isSleeping()) {
                return Math.floorMod(phase, Math.max(1, bedInterval)) == 0;
            }

            return Math.floorMod(phase, Math.max(1, stallInterval)) == 0;
        } catch (Throwable t) {
            KtfLogger.warnRateLimited("villager-brain-throttler",
                    "VillagerBrainThrottler failed; falling back to vanilla 20 Hz brain tick: {}", t.toString());
            return true;
        }
    }

    public static void compensateSkippedTicks(Brain<?> brain, int skipped) {
        if (skipped <= 0) {
            return;
        }
        try {
            BrainAccessor ba = (BrainAccessor) brain;
            for (Sensor<?> sensor : ba.ktf$getSensors().values()) {
                SensorAccessor sa = (SensorAccessor) sensor;
                sa.ktf$setTimeToTick(sa.ktf$getTimeToTick() - skipped);
            }
            for (Optional<? extends ExpirableValue<?>> opt : ba.ktf$getMemories().values()) {
                if (opt.isPresent()) {
                    ExpirableValue<?> exp = opt.get();
                    if (exp.canExpire()) {
                        ExpirableValueAccessor ea = (ExpirableValueAccessor) exp;
                        ea.ktf$setTimeToLive(Math.max(0L, ea.ktf$getTimeToLive() - skipped));
                    }
                }
            }
        } catch (Throwable t) {
            // fail-open
        }
    }
}
