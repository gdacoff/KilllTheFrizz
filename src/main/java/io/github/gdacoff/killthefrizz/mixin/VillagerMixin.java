package io.github.gdacoff.killthefrizz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.gdacoff.killthefrizz.profiling.KtfProfiler;
import io.github.gdacoff.killthefrizz.villager.VillagerBrainThrottler;
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
 * Diagnostic instrumentation for {@link Villager#customServerAiStep()}.
 *
 * <p>All timing is guarded by {@link KtfProfiler#isRecording()} so that when the
 * profiler is inactive there are no {@code System.nanoTime()} calls and no
 * allocations on the hot path.</p>
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {

    @Unique
    private long ktf$customAiStartNanos = 0L;

    @Unique
    private int ktf$skippedTicks = 0;

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void ktf$onCustomServerAiStepHead(CallbackInfo ci) {
        this.ktf$customAiStartNanos = KtfProfiler.isRecording() ? System.nanoTime() : 0L;
    }

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void ktf$onCustomServerAiStepReturn(CallbackInfo ci) {
        long start = this.ktf$customAiStartNanos;
        if (start != 0L) {
            this.ktf$customAiStartNanos = 0L;
            KtfProfiler.recordVillagerCustomAi(System.nanoTime() - start);
        }
    }

    @WrapOperation(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void ktf$wrapBrainTick(Brain<Villager> brain, ServerLevel level, LivingEntity entity, Operation<Void> original) {
        if (!VillagerBrainThrottler.shouldTickBrain((Villager) (Object) this)) {
            this.ktf$skippedTicks++;
            KtfProfiler.recordSkippedVillagerBrain();
            return;
        }
        int skipped = this.ktf$skippedTicks;
        this.ktf$skippedTicks = 0;
        if (skipped > 0) {
            VillagerBrainThrottler.compensateSkippedTicks(brain, skipped);
        }
        if (KtfProfiler.isRecording()) {
            long start = System.nanoTime();
            original.call(brain, level, entity);
            KtfProfiler.recordVillagerBrain(System.nanoTime() - start);
        } else {
            original.call(brain, level, entity);
        }
    }
}
