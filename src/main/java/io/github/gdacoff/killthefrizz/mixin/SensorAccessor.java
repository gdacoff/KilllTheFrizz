package io.github.gdacoff.killthefrizz.mixin;

import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Sensor.class)
public interface SensorAccessor {

    @Accessor("timeToTick")
    long ktf$getTimeToTick();

    @Accessor("timeToTick")
    void ktf$setTimeToTick(long timeToTick);
}
