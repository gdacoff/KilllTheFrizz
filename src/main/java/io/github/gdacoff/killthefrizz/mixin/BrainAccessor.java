package io.github.gdacoff.killthefrizz.mixin;

import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Brain.class)
public interface BrainAccessor {

    @Accessor("sensors")
    Map<SensorType<? extends Sensor<?>>, Sensor<?>> ktf$getSensors();

    @Accessor("memories")
    Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> ktf$getMemories();
}
