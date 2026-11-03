package io.github.gdacoff.killthefrizz.mixin;

import io.github.gdacoff.killthefrizz.profiling.KtfProfiler;
import io.github.gdacoff.killthefrizz.sleeping.IdleChecker;
import io.github.gdacoff.killthefrizz.sleeping.SleepStateAccess;
import io.github.gdacoff.killthefrizz.stats.KtfStats;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to Mob.serverAiStep to skip AI goal and navigation evaluations when idle.
 * If disabled or when mob is active, serverAiStep runs normally as in vanilla.
 *
 * <p>When a mob is put to sleep we increment {@code noActionTime} so that vanilla's
 * despawn logic still sees the mob as "idle" and can despawn it normally. Without
 * this, sleeping mobs would never despawn because their AI never runs.</p>
 *
 * <p>Profiling hooks are guarded by {@link KtfProfiler#isRecording()} so that when
 * the profiler is inactive there are no {@code System.nanoTime()} calls and no
 * allocations on the hot path.</p>
 */
@Mixin(Mob.class)
public abstract class MobMixin implements SleepStateAccess {

    @Unique
    private boolean ktf$wasSleeping = false;

    @Override
    public boolean ktf$isSleeping() {
        return this.ktf$wasSleeping;
    }

    @Unique
    private long ktf$aiStartNanos = 0L;

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void ktf$onServerAiStep(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        this.ktf$aiStartNanos = 0L;
        boolean profiling = KtfProfiler.isRecording();
        long t0 = profiling ? System.nanoTime() : 0L;
        boolean sleep = IdleChecker.shouldSleep(mob, this.ktf$wasSleeping);
        if (profiling) {
            KtfProfiler.recordIdleCheck(System.nanoTime() - t0);
        }
        this.ktf$wasSleeping = sleep;
        if (sleep) {
            if (profiling) {
                KtfProfiler.recordSkippedAi();
            }
            mob.setNoActionTime(mob.getNoActionTime() + 1);
            KtfStats.recordSkippedAiTick();
            ci.cancel();
            return;
        }
        if (profiling) {
            this.ktf$aiStartNanos = System.nanoTime();
        }
    }

    @Inject(method = "serverAiStep", at = @At("RETURN"))
    private void ktf$onServerAiStepReturn(CallbackInfo ci) {
        if (this.ktf$aiStartNanos != 0L) {
            long duration = System.nanoTime() - this.ktf$aiStartNanos;
            this.ktf$aiStartNanos = 0L;
            KtfProfiler.recordServerAiStep(duration);
        }
    }
}
