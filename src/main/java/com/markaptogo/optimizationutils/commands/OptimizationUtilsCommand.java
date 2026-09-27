package com.markaptogo.optimizationutils.commands;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.config.PluginConfiguration;
import com.markaptogo.optimizationutils.config.model.PerformanceMetric;
import com.markaptogo.optimizationutils.manager.DynamicMobcapManager;
import com.markaptogo.optimizationutils.manager.EntityTickManager;
import com.markaptogo.optimizationutils.manager.NMSUtils;
import com.markaptogo.optimizationutils.manager.ThrottleUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class OptimizationUtilsCommand {

    public static final String PERMISSION = "optimizationutils.admin";

    private static final SimpleCommandExceptionType PLAYER_ONLY = new SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(Component.text("This command can only be used by players."))
    );

    private static final List<HelpEntry> HELP = List.of(
        new HelpEntry("analyzechunks", "", "Analyze loaded chunks for entity counts"),
        new HelpEntry("setsimulationdistance", "<distance>", "Sets simulation distance for all worlds while respecting despawn ranges"),
        new HelpEntry("setspawnlimit", "<spawn category> <limit>", "Sets mobcap for all worlds"),
        new HelpEntry("setticksperspawn", "<spawn category> <ticks>", "Sets ticks per spawn for all worlds (how often the server tries to spawn mobs)"),
        new HelpEntry("setvillagersensortickrate", "<ticks>", "Sets the villager secondary POI sensor tick rate for all worlds"),
        new HelpEntry("setvillagerbehaviortickrate", "<ticks>", "Sets the villager validate-nearby-POI behavior tick rate for all worlds"),
        new HelpEntry("setviewdistance", "<distance> [player]", "Sets view distance for all worlds or a single player"),
        new HelpEntry("resetviewdistance", "<player>", "Resets view distance for a player to server default"),
        new HelpEntry("reload", "", "Reloads the configuration"),
        new HelpEntry("info", "", "Displays server and plugin information")
    );

    private OptimizationUtilsCommand() {
    }

    public static void register(Commands commands) {
        commands.register(build(), "OptimizationUtils admin commands", List.of("ou", "opt"));
    }

    private static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("optimizationutils")
            .requires(source -> source.getSender().hasPermission(PERMISSION))
            .executes(ctx -> help(sender(ctx)))
            .then(Commands.literal("help")
                .executes(ctx -> help(sender(ctx))))
            .then(Commands.literal("analyzechunks")
                .executes(ctx -> analyzeChunks(player(ctx))))
            .then(Commands.literal("setsimulationdistance")
                .then(Commands.argument("distance", IntegerArgumentType.integer(2, 32))
                    .executes(ctx -> setSimulationDistance(sender(ctx), IntegerArgumentType.getInteger(ctx, "distance")))))
            .then(Commands.literal("setspawnlimit")
                .then(Commands.argument("category", new SpawnCategoryArgument())
                    .then(Commands.argument("limit", IntegerArgumentType.integer())
                        .executes(ctx -> setSpawnLimit(sender(ctx), ctx.getArgument("category", SpawnCategory.class), IntegerArgumentType.getInteger(ctx, "limit"))))))
            .then(Commands.literal("setticksperspawn")
                .then(Commands.argument("category", new SpawnCategoryArgument())
                    .then(Commands.argument("ticks", IntegerArgumentType.integer())
                        .executes(ctx -> setTicksPerSpawn(sender(ctx), ctx.getArgument("category", SpawnCategory.class), IntegerArgumentType.getInteger(ctx, "ticks"))))))
            .then(Commands.literal("setvillagersensortickrate")
                .then(Commands.argument("ticks", IntegerArgumentType.integer())
                    .executes(ctx -> setVillagerSensorTickRate(sender(ctx), IntegerArgumentType.getInteger(ctx, "ticks")))))
            .then(Commands.literal("setvillagerbehaviortickrate")
                .then(Commands.argument("ticks", IntegerArgumentType.integer())
                    .executes(ctx -> setVillagerBehaviorTickRate(sender(ctx), IntegerArgumentType.getInteger(ctx, "ticks")))))
            .then(Commands.literal("setviewdistance")
                .then(Commands.argument("distance", IntegerArgumentType.integer(2, 32))
                    .executes(ctx -> setViewDistance(sender(ctx), IntegerArgumentType.getInteger(ctx, "distance"), null))
                    .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ctx -> setViewDistance(sender(ctx), IntegerArgumentType.getInteger(ctx, "distance"), targetPlayer(ctx))))))
            .then(Commands.literal("resetviewdistance")
                .then(Commands.argument("player", ArgumentTypes.player())
                    .executes(ctx -> resetViewDistance(sender(ctx), targetPlayer(ctx)))))
            .then(Commands.literal("reload")
                .executes(ctx -> reload(sender(ctx))))
            .then(Commands.literal("info")
                .executes(ctx -> info(sender(ctx))))
            .build();
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    private static Player player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (ctx.getSource().getSender() instanceof Player player) {
            return player;
        }
        throw PLAYER_ONLY.create();
    }

    private static Player targetPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
    }

    private static int help(CommandSender sender) {
        Component message = Component.text("=== OptimizationUtils Commands ===", NamedTextColor.GREEN);
        for (HelpEntry entry : HELP) {
            String usage = "/ou " + entry.name() + (entry.syntax().isEmpty() ? "" : " " + entry.syntax());
            message = message.append(Component.newline())
                .append(Component.text(usage, NamedTextColor.AQUA))
                .append(Component.text(" - " + entry.description(), NamedTextColor.GRAY));
        }
        sender.sendMessage(message);
        return Command.SINGLE_SUCCESS;
    }

    private static int analyzeChunks(Player player) {
        player.sendMessage("Analyzing chunks...");

        Map<Chunk, Integer> chunkEntities = new HashMap<>();
        for (Chunk chunk : player.getWorld().getLoadedChunks()) {
            chunkEntities.put(chunk, chunk.getEntities().length);
        }

        chunkEntities.entrySet().stream()
            .sorted(Map.Entry.<Chunk, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> player.sendMessage(entry.getValue() + " -> Chunk " + entry.getKey().getX() + " " + entry.getKey().getZ()));
        return Command.SINGLE_SUCCESS;
    }

    private static int setSimulationDistance(CommandSender sender, int newSimulationDistance) {
        for (World world : Bukkit.getWorlds()) {
            world.setSimulationDistance(newSimulationDistance);
            NMSUtils.setNMSSimulationDistance(world, newSimulationDistance);
        }

        sender.sendMessage(Component.text("Successfully set simulation distance to " + newSimulationDistance + " for all worlds.").color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Make sure that \"/paper mobcaps\" will go to the max mobcap, or else use \"/ou setspawnlimit\" to lower mobcap.").color(NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int setSpawnLimit(CommandSender sender, SpawnCategory spawnCategory, int limit) {
        for (World world : Bukkit.getWorlds()) {
            world.setSpawnLimit(spawnCategory, limit);
        }

        sender.sendMessage(Component.text("Successfully set spawn limit for " + spawnCategory.name() + " to " + limit + " for all worlds.").color(NamedTextColor.GREEN));
        if (DynamicMobcapManager.currentPercent() < 100) {
            sender.sendMessage(Component.text("Dynamic mobcap is currently at " + DynamicMobcapManager.currentPercent() + "%, so this limit will be scaled down until the server recovers.").color(NamedTextColor.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int setTicksPerSpawn(CommandSender sender, SpawnCategory spawnCategory, int ticks) {
        for (World world : Bukkit.getWorlds()) {
            world.setTicksPerSpawns(spawnCategory, ticks);
        }

        sender.sendMessage(Component.text("Successfully set ticks per spawn for " + spawnCategory.name() + " to " + ticks + " for all worlds.").color(NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int setVillagerSensorTickRate(CommandSender sender, int ticks) {
        for (World world : Bukkit.getWorlds()) {
            NMSUtils.setNMSVillagerSensorTickRate(world, ticks);
        }

        sender.sendMessage(Component.text("Successfully set villager sensor tick rate to " + ticks + " for all worlds.").color(NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int setVillagerBehaviorTickRate(CommandSender sender, int ticks) {
        for (World world : Bukkit.getWorlds()) {
            NMSUtils.setNMSVillagerBehaviorTickRate(world, ticks);
        }

        sender.sendMessage(Component.text("Successfully set villager behavior tick rate to " + ticks + " for all worlds.").color(NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int setViewDistance(CommandSender sender, int newViewDistance, Player target) {
        if (target != null) {
            target.setViewDistance(newViewDistance);
            OptimizationUtils.instance().dataConfiguration().viewDistanceOverrides.put(target.getUniqueId(), newViewDistance);
            OptimizationUtils.instance().dataConfiguration().save();
            sender.sendMessage(Component.text("Successfully set view distance to " + newViewDistance + " for " + target.getName()).color(NamedTextColor.GREEN));
        } else {
            for (World world : Bukkit.getWorlds()) {
                world.setViewDistance(newViewDistance);
            }
            sender.sendMessage(Component.text("Successfully set view distance to " + newViewDistance + " for all worlds.").color(NamedTextColor.GREEN));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int resetViewDistance(CommandSender sender, Player target) {
        target.setViewDistance(-1);
        OptimizationUtils.instance().dataConfiguration().viewDistanceOverrides.remove(target.getUniqueId());
        OptimizationUtils.instance().dataConfiguration().save();
        sender.sendMessage(Component.text("Successfully reset view distance for " + target.getName()).color(NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandSender sender) {
        OptimizationUtils.instance().reloadConfiguration();

        sender.sendMessage(Component.text("Configuration reloaded successfully.").color(NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int info(CommandSender sender) {
        Component message = Component.text("=== OptimizationUtils Info ===").color(NamedTextColor.GREEN)
                .append(Component.newline())
                .append(Component.newline());

        // View Distance - per world
        message = message.append(Component.text("View Distance:").color(NamedTextColor.AQUA))
                .append(Component.newline());
        for (World world : Bukkit.getWorlds()) {
            int viewDistance = world.getViewDistance();
            message = message.append(Component.text("  " + world.getName() + ": " + viewDistance).color(NamedTextColor.WHITE))
                    .append(Component.newline());
        }
        message = message.append(Component.newline());

        // Simulation Distance - per world
        message = message.append(Component.text("Simulation Distance:").color(NamedTextColor.AQUA))
                .append(Component.newline());
        for (World world : Bukkit.getWorlds()) {
            int simulationDistance = world.getSimulationDistance();
            message = message.append(Component.text("  " + world.getName() + ": " + simulationDistance).color(NamedTextColor.WHITE))
                    .append(Component.newline());
        }
        message = message.append(Component.newline());

        // Player View Distance - grouping
        Map<Integer, Long> viewDistanceGroups = Bukkit.getOnlinePlayers().stream()
                .collect(Collectors.groupingBy(
                        player -> player.getViewDistance() != 0 ? player.getViewDistance() : player.getWorld().getViewDistance(),
                        Collectors.counting()
                ));

        message = message.append(Component.text("Player View Distance:").color(NamedTextColor.AQUA))
                .append(Component.newline());
        if (viewDistanceGroups.isEmpty()) {
            message = message.append(Component.text("  No players online").color(NamedTextColor.GRAY))
                    .append(Component.newline());
        } else {
            List<Map.Entry<Integer, Long>> sortedViewDistanceGroups = viewDistanceGroups.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
                    .toList();
            for (Map.Entry<Integer, Long> entry : sortedViewDistanceGroups) {
                message = message.append(Component.text("  " + entry.getKey() + " view distance - " + entry.getValue() + " players").color(NamedTextColor.WHITE))
                        .append(Component.newline());
            }
        }
        message = message.append(Component.newline());

        // View Distance Overrides - per player
        Map<UUID, Integer> viewDistanceOverrides = OptimizationUtils.instance().dataConfiguration().viewDistanceOverrides;
        message = message.append(Component.text("View Distance Overrides:").color(NamedTextColor.AQUA))
                .append(Component.newline());
        if (viewDistanceOverrides.isEmpty()) {
            message = message.append(Component.text("  No overrides set").color(NamedTextColor.GRAY))
                    .append(Component.newline());
        } else {
            for (Map.Entry<UUID, Integer> entry : viewDistanceOverrides.entrySet()) {
                Player overridePlayer = Bukkit.getPlayer(entry.getKey());
                String playerName = overridePlayer != null ? overridePlayer.getName() : entry.getKey().toString();
                message = message.append(Component.text("  " + playerName + ": " + entry.getValue()).color(NamedTextColor.WHITE))
                        .append(Component.newline());
            }
        }
        message = message.append(Component.newline());

        // Player Simulation Distance - grouping
        Map<Integer, Long> simulationDistanceGroups = Bukkit.getOnlinePlayers().stream()
                .collect(Collectors.groupingBy(
                        player -> player.getSimulationDistance() != 0 ? player.getSimulationDistance() : player.getWorld().getSimulationDistance(),
                        Collectors.counting()
                ));

        message = message.append(Component.text("Player Simulation Distance:").color(NamedTextColor.AQUA))
                .append(Component.newline());
        if (simulationDistanceGroups.isEmpty()) {
            message = message.append(Component.text("  No players online").color(NamedTextColor.GRAY))
                    .append(Component.newline());
        } else {
            List<Map.Entry<Integer, Long>> sortedSimulationDistanceGroups = simulationDistanceGroups.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
                    .toList();
            for (Map.Entry<Integer, Long> entry : sortedSimulationDistanceGroups) {
                message = message.append(Component.text("  " + entry.getKey() + " simulation distance - " + entry.getValue() + " players").color(NamedTextColor.WHITE))
                        .append(Component.newline());
            }
        }
        message = message.append(Component.newline());

        // Entity Count - sum and per world
        int totalEntities = 0;
        message = message.append(Component.text("Entity Count:").color(NamedTextColor.AQUA))
                .append(Component.newline());
        for (World world : Bukkit.getWorlds()) {
            int entityCount = world.getEntityCount();
            totalEntities += entityCount;
            message = message.append(Component.text("  " + world.getName() + ": " + entityCount).color(NamedTextColor.WHITE))
                    .append(Component.newline());
        }
        message = message.append(Component.text("Total Entities: " + totalEntities).color(NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.newline());

        // Loaded Chunks - sum and per world
        int totalChunks = 0;
        message = message.append(Component.text("Loaded Chunks:").color(NamedTextColor.AQUA))
                .append(Component.newline());
        for (World world : Bukkit.getWorlds()) {
            int chunkCount = world.getLoadedChunks().length;
            totalChunks += chunkCount;
            message = message.append(Component.text("  " + world.getName() + ": " + chunkCount).color(NamedTextColor.WHITE))
                    .append(Component.newline());
        }
        message = message.append(Component.text("Total Loaded Chunks: " + totalChunks).color(NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.newline());

        // Random Tick Speed - per world
        message = message.append(Component.text("Random Tick Speed:").color(NamedTextColor.AQUA))
                .append(Component.newline());
        for (World world : Bukkit.getWorlds()) {
            int randomTickSpeed = world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
            message = message.append(Component.text("  " + world.getName() + ": " + randomTickSpeed).color(NamedTextColor.WHITE))
                    .append(Component.newline());
        }
        message = message.append(Component.newline());

        // Villager Tick Rates - per world
        message = message.append(Component.text("Villager Tick Rates (sensor / behavior):").color(NamedTextColor.AQUA))
                .append(Component.newline());
        for (World world : Bukkit.getWorlds()) {
            Integer sensorTickRate = NMSUtils.getNMSVillagerSensorTickRate(world);
            Integer behaviorTickRate = NMSUtils.getNMSVillagerBehaviorTickRate(world);
            String sensorText = sensorTickRate != null ? sensorTickRate.toString() : "default";
            String behaviorText = behaviorTickRate != null ? behaviorTickRate.toString() : "default";
            message = message.append(Component.text("  " + world.getName() + ": " + sensorText + " / " + behaviorText).color(NamedTextColor.WHITE))
                    .append(Component.newline());
        }
        message = message.append(Component.newline());

        // Plugin Configuration Status
        message = message.append(Component.text("Plugin Configuration:").color(NamedTextColor.AQUA))
                .append(Component.newline());

        message = message.append(Component.text("  MSPT Calculation Mode: " + OptimizationUtils.instance().pluginConfiguration().msptCalculationMode).color(NamedTextColor.GRAY))
                .append(Component.newline());

        message = message.append(Component.text("  Current Performance: " + ThrottleUtils.format(PerformanceMetric.MSPT, ThrottleUtils.getMspt())
                + " / " + ThrottleUtils.format(PerformanceMetric.TPS, ThrottleUtils.getTps())).color(NamedTextColor.GRAY))
                .append(Component.newline());

        String dynamicMobcapStatus = OptimizationUtils.instance().pluginConfiguration().dynamicMobcap.enabled
            ? "Enabled (by " + OptimizationUtils.instance().pluginConfiguration().dynamicMobcap.metric + ", currently " + DynamicMobcapManager.currentPercent() + "% of normal mobcap"
                + (DynamicMobcapManager.shouldThrottleSpawners() ? ", spawners throttled" : "") + ")"
            : "Disabled";
        message = message.append(Component.text("  Dynamic Mobcap: " + dynamicMobcapStatus).color(NamedTextColor.GRAY))
                .append(Component.newline());

        String dynamicRandomTickStatus = OptimizationUtils.instance().pluginConfiguration().dynamicRandomTickSpeed.enabled
            ? "Enabled (threshold: " + OptimizationUtils.instance().pluginConfiguration().dynamicRandomTickSpeed.threshold + " " + OptimizationUtils.instance().pluginConfiguration().dynamicRandomTickSpeed.metric + ")"
            : "Disabled";
        message = message.append(Component.text("  Dynamic Random Tick Speed: " + dynamicRandomTickStatus).color(NamedTextColor.GRAY))
                .append(Component.newline());

        PluginConfiguration.DisableEntityTicking disableEntityTicking = OptimizationUtils.instance().pluginConfiguration().disableEntityTicking;
        String entityTickingStatus = EntityTickManager.isRunning()
            ? disableEntityTicking.mode + " for " + disableEntityTicking.filterMode + " " + disableEntityTicking.entities
            : "Disabled";
        message = message.append(Component.text("  Disable Entity Ticking: " + entityTickingStatus).color(NamedTextColor.GRAY));

        sender.sendMessage(message);
        return Command.SINGLE_SUCCESS;
    }

    private record HelpEntry(String name, String syntax, String description) {
    }

    // MISC is rejected because World#setSpawnLimit and World#setTicksPerSpawns throw for it
    private static final class SpawnCategoryArgument implements CustomArgumentType.Converted<SpawnCategory, String> {

        private static final List<SpawnCategory> CATEGORIES = Arrays.stream(SpawnCategory.values())
            .filter(category -> category != SpawnCategory.MISC)
            .toList();

        private static final DynamicCommandExceptionType UNKNOWN_CATEGORY = new DynamicCommandExceptionType(
            value -> MessageComponentSerializer.message().serialize(Component.text("Unknown spawn category: " + value))
        );

        @Override
        public SpawnCategory convert(String value) throws CommandSyntaxException {
            for (SpawnCategory category : CATEGORIES) {
                if (category.name().equalsIgnoreCase(value)) {
                    return category;
                }
            }
            throw UNKNOWN_CATEGORY.create(value);
        }

        @Override
        public ArgumentType<String> getNativeType() {
            return StringArgumentType.word();
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            for (SpawnCategory category : CATEGORIES) {
                String name = category.name().toLowerCase(Locale.ROOT);
                if (name.startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        }
    }
}
