package io.github.gdacoff.killthefrizz.mixin.client;

import io.github.gdacoff.killthefrizz.adaptive.AdaptiveDistanceManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Inject(
            method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void killthefrizz$cullParticle(ParticleOptions options, double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed,
                                           CallbackInfoReturnable<Particle> cir) {
        if (AdaptiveDistanceManager.getInstance().shouldCullParticle(x, y, z)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = "add(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void killthefrizz$limitParticles(Particle particle, CallbackInfo ci) {
        if (AdaptiveDistanceManager.getInstance().isParticleLimitReached()) {
            ci.cancel();
        }
    }
}
