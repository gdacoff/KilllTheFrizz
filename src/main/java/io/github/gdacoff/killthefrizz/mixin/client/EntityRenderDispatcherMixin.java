package io.github.gdacoff.killthefrizz.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.gdacoff.killthefrizz.adaptive.AdaptiveDistanceManager;
import io.github.gdacoff.killthefrizz.culling.GpuCullingManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Shadow
    private Camera camera;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void killthefrizz$cullEntity(Entity entity, double x, double y, double z, float yaw,
                                         float partialTick, PoseStack poseStack,
                                         MultiBufferSource bufferSource, int packedLight,
                                         CallbackInfo ci) {
        if (AdaptiveDistanceManager.getInstance().shouldCullEntity(entity, this.camera)) {
            ci.cancel();
            return;
        }
        if (GpuCullingManager.shouldCull(entity, this.camera)) {
            ci.cancel();
        }
    }
}
