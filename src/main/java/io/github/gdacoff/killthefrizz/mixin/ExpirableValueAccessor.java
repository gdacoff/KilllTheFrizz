package io.github.gdacoff.killthefrizz.mixin;

import net.minecraft.world.entity.ai.memory.ExpirableValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExpirableValue.class)
public interface ExpirableValueAccessor {

    @Accessor("timeToLive")
    long ktf$getTimeToLive();

    @Accessor("timeToLive")
    void ktf$setTimeToLive(long timeToLive);
}
