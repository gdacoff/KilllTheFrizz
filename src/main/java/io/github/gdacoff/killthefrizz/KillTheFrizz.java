package io.github.gdacoff.killthefrizz;

import io.github.gdacoff.killthefrizz.command.KtfCommands;
import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.profiling.KtfProfiler;
import io.github.gdacoff.killthefrizz.sleeping.SleepingWakeupEvents;
import io.github.gdacoff.killthefrizz.util.KtfLogger;
import io.github.gdacoff.killthefrizz.util.SlowdownMonitor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(KillTheFrizz.MODID)
public class KillTheFrizz {
    public static final String MODID = "killthefrizz";

    public KillTheFrizz(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, KtfConfig.COMMON_SPEC, "killthefrizz-common.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, KtfConfig.CLIENT_SPEC, "killthefrizz-client.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, KtfConfig.SERVER_SPEC, "killthefrizz-server.toml");

        modEventBus.addListener(ModConfigEvent.Loading.class, event -> KtfConfig.onConfigLoad(event));
        modEventBus.addListener(ModConfigEvent.Reloading.class, event -> KtfConfig.onConfigReload(event));

        NeoForge.EVENT_BUS.addListener(KtfCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(SleepingWakeupEvents::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(SleepingWakeupEvents::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(SleepingWakeupEvents::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(SleepingWakeupEvents::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(SlowdownMonitor::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(SlowdownMonitor::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(KtfProfiler::onServerTickPost);

        KtfLogger.info("KilllTheFrizz initialized successfully.");
    }
}
