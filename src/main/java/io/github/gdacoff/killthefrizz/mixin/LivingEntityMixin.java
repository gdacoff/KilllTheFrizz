package io.github.gdacoff.killthefrizz.mixin;

import io.github.gdacoff.killthefrizz.collision.CollisionThrottler;
import io.github.gdacoff.killthefrizz.profiling.KtfProfiler;
import io.github.gdacoff.killthefrizz.sleeping.SleepStateAccess;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Profiles {@link LivingEntity#pushEntities()} when the diagnostic profiler is active.
 * No-op when the profiler is inactive.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Unique
    private long ktf$pushStartNanos = 0L;

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void ktf$onPushEntitiesHead(CallbackInfo ci) {
        if ((Object) this instanceof SleepStateAccess sleep && sleep.ktf$isSleeping()) {
            LivingEntity entity = (LivingEntity) (Object) this;
            int cramming = entity.level().getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_MAX_ENTITY_CRAMMING);
            if (cramming > 0 && ((entity.tickCount + entity.getId()) & 15) == 0) {
                java.util.List<net.minecraft.world.entity.Entity> list = entity.level().getEntities(
                    entity, entity.getBoundingBox(), net.minecraft.world.entity.EntitySelector.pushableBy(entity)
                );
                if (list.size() > cramming - 1) {
                    int count = 0;
                    for (net.minecraft.world.entity.Entity e : list) {
                        if (!e.isPassenger()) {
                            count++;
                        }
                    }
                    if (count > cramming - 1) {
                        entity.hurt(entity.damageSources().cramming(), 6.0F);
                    }
                }
            }
            ci.cancel();
            return;
        }

        if (!CollisionThrottler.shouldPushEntities((LivingEntity) (Object) this)) {
            ci.cancel();
            return;
        }

        if (KtfProfiler.isRecording()) {
            this.ktf$pushStartNanos = System.nanoTime();
        } else {
            this.ktf$pushStartNanos = 0L;
        }
    }

    @Inject(method = "pushEntities", at = @At("RETURN"))
    private void ktf$onPushEntitiesReturn(CallbackInfo ci) {
        if (this.ktf$pushStartNanos != 0L) {
            long duration = System.nanoTime() - this.ktf$pushStartNanos;
            this.ktf$pushStartNanos = 0L;
            KtfProfiler.recordPushEntities(duration);
        }
    }
}
