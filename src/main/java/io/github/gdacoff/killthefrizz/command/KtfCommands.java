package io.github.gdacoff.killthefrizz.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.config.Preset;
import io.github.gdacoff.killthefrizz.profiling.KtfProfiler;
import io.github.gdacoff.killthefrizz.stats.KtfStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class KtfCommands {

    private KtfCommands() {
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("killthefrizz")
                .then(Commands.literal("stats")
                        .executes(KtfCommands::runStats))
                .then(Commands.literal("config")
                        .then(Commands.literal("dump")
                                .executes(KtfCommands::runConfigDump))
                        .then(Commands.literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(KtfCommands::runConfigReload)))
                .then(Commands.literal("preset")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .executes(KtfCommands::runPreset)))
                .then(Commands.literal("toggle")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("feature", StringArgumentType.word())
                                .executes(KtfCommands::runToggle)))
                .then(Commands.literal("profile")
                        .then(Commands.literal("start")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> runProfileStart(ctx, 100))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .executes(ctx -> runProfileStart(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))
                        .then(Commands.literal("stop")
                                .requires(src -> src.hasPermission(2))
                                .executes(KtfCommands::runProfileStop))
                        .then(Commands.literal("report")
                                .executes(KtfCommands::runProfileReport))
                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(2))
                                .executes(KtfCommands::runProfileReset))));
    }

    private static int runStats(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(KtfStats.formatStats()), false);
        return 1;
    }

    private static int runConfigDump(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(KtfConfig.dumpEffective()), false);
        return 1;
    }

    private static int runConfigReload(CommandContext<CommandSourceStack> ctx) {
        KtfConfig.recomputeEffective();
        ctx.getSource().sendSuccess(() -> Component.literal("KilllTheFrizz config reloaded."), true);
        return 1;
    }

    private static int runPreset(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "preset");
        Preset preset;
        try {
            preset = Preset.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("Unknown preset: " + raw));
            return 0;
        }
        KtfConfig.common().preset.set(preset);
        KtfConfig.recomputeEffective();
        ctx.getSource().sendSuccess(() -> Component.literal("Preset set to " + preset.name() + "."), true);
        return 1;
    }

    private static int runProfileStart(CommandContext<CommandSourceStack> ctx, int ticks) {
        KtfProfiler.start(ticks);
        ctx.getSource().sendSuccess(() -> Component.literal("Profiler started for " + ticks + " ticks."), true);
        return 1;
    }

    private static int runProfileStop(CommandContext<CommandSourceStack> ctx) {
        KtfProfiler.stop();
        ctx.getSource().sendSuccess(() -> Component.literal("Profiler stopped."), true);
        return 1;
    }

    private static int runProfileReport(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(KtfProfiler.formatReport()), false);
        return 1;
    }

    private static int runProfileReset(CommandContext<CommandSourceStack> ctx) {
        KtfProfiler.reset();
        ctx.getSource().sendSuccess(() -> Component.literal("Profiler reset."), true);
        return 1;
    }

    private static int runToggle(CommandContext<CommandSourceStack> ctx) {
        String feature = StringArgumentType.getString(ctx, "feature");
        boolean nowEnabled = KtfConfig.toggleFeature(feature);
        KtfConfig.recomputeEffective();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Feature '" + feature + "' is now " + (nowEnabled ? "enabled" : "disabled") + "."),
                true);
        return 1;
    }
}
