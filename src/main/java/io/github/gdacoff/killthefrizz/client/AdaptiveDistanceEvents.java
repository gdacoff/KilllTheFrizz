package io.github.gdacoff.killthefrizz.client;

import io.github.gdacoff.killthefrizz.adaptive.AdaptiveDistanceManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client game-bus events for adaptive render distance.
 *
 * <p>Drives the {@link AdaptiveDistanceManager} once per second (every 20 ticks)
 * so the rolling FPS average can be evaluated and the view distance adjusted.</p>
 */
@EventBusSubscriber(modid = "killthefrizz", value = Dist.CLIENT)
public final class AdaptiveDistanceEvents {

    private static final int TICKS_PER_SECOND = 20;

    private static int tickCounter = 0;

    private AdaptiveDistanceEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < TICKS_PER_SECOND) {
            return;
        }
        tickCounter = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        AdaptiveDistanceManager.getInstance().tick(mc);
    }
}
