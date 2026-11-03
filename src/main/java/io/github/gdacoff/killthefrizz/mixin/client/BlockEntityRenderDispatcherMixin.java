package io.github.gdacoff.killthefrizz.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.gdacoff.killthefrizz.adaptive.AdaptiveDistanceManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @Shadow
    private Camera camera;

    @Shadow
    private HitResult cameraHitResult;

    @Inject(
            method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void killthefrizz$cullBlockEntity(BlockEntity blockEntity, float partialTick,
                                              PoseStack poseStack, MultiBufferSource bufferSource,
                                              CallbackInfo ci) {
        if (AdaptiveDistanceManager.getInstance().shouldCullBlockEntity(blockEntity, this.camera)) {
            ci.cancel();
        }
    }
}
