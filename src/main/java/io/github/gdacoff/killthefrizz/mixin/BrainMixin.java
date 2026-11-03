package io.github.gdacoff.killthefrizz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.gdacoff.killthefrizz.profiling.KtfProfiler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic instrumentation for {@link Brain} sub-phases when the brain belongs
 * to a {@link Villager}.
 *
 * <p>All timing is guarded by {@link KtfProfiler#isRecording()} so that when the
 * profiler is inactive there are no {@code System.nanoTime()} calls and no
 * allocations on the hot path.</p>
 */
@Mixin(Brain.class)
public abstract class BrainMixin {

    @Unique
    private long ktf$scheduleStartNanos = 0L;

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;tickSensors(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void ktf$wrapTickSensors(Brain<LivingEntity> brain, ServerLevel level, LivingEntity entity, Operation<Void> original) {
        if (KtfProfiler.isRecording() && entity instanceof Villager) {
            long start = System.nanoTime();
            original.call(brain, level, entity);
            KtfProfiler.recordVillagerSensors(System.nanoTime() - start);
        } else {
            original.call(brain, level, entity);
        }
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;startEachNonRunningBehavior(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void ktf$wrapStartBehaviors(Brain<LivingEntity> brain, ServerLevel level, LivingEntity entity, Operation<Void> original) {
        if (KtfProfiler.isRecording() && entity instanceof Villager) {
            long start = System.nanoTime();
            original.call(brain, level, entity);
            KtfProfiler.recordVillagerStartBehaviors(System.nanoTime() - start);
        } else {
            original.call(brain, level, entity);
        }
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;tickEachRunningBehavior(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void ktf$wrapRunningBehaviors(Brain<LivingEntity> brain, ServerLevel level, LivingEntity entity, Operation<Void> original) {
        if (KtfProfiler.isRecording() && entity instanceof Villager) {
            long start = System.nanoTime();
            original.call(brain, level, entity);
            KtfProfiler.recordVillagerRunningBehaviors(System.nanoTime() - start);
        } else {
            original.call(brain, level, entity);
        }
    }

    @Inject(method = "updateActivityFromSchedule", at = @At("HEAD"))
    private void ktf$onUpdateActivityFromScheduleHead(long dayTime, long gameTime, CallbackInfo ci) {
        this.ktf$scheduleStartNanos = KtfProfiler.isRecording() ? System.nanoTime() : 0L;
    }

    @Inject(method = "updateActivityFromSchedule", at = @At("RETURN"))
    private void ktf$onUpdateActivityFromScheduleReturn(long dayTime, long gameTime, CallbackInfo ci) {
        long start = this.ktf$scheduleStartNanos;
        if (start != 0L) {
            this.ktf$scheduleStartNanos = 0L;
            KtfProfiler.recordVillagerSchedule(System.nanoTime() - start);
        }
    }
}
