package com.markaptogo.optimizationutils.commands;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.analysis.ChunkReport;
import com.markaptogo.optimizationutils.analysis.ChunkReportMessages;
import com.markaptogo.optimizationutils.analysis.ChunkScanner;
import com.markaptogo.optimizationutils.analysis.ChunkStats;
import com.markaptogo.optimizationutils.analysis.CostWeights;
import com.markaptogo.optimizationutils.analysis.Format;
import com.markaptogo.optimizationutils.analysis.Query;
import com.markaptogo.optimizationutils.analysis.ReportView;
import com.markaptogo.optimizationutils.analysis.ScheduledTickSampler;
import com.markaptogo.optimizationutils.analysis.Sort;
import com.markaptogo.optimizationutils.config.PluginConfiguration;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.registries.BuiltInRegistries;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongPredicate;
import java.util.function.ToIntFunction;

/**
 * /ou analyzechunks: ranks the loaded chunks by their estimated cost, with details, filters, pages and export.
 */
final class ChunkAnalysisCommand {

    /**
     * How long the last result of a sender is kept for paging, sorting and exporting.
     */
    private static final long KEEP_RESULT_MILLIS = 15 * 60 * 1000;

    /**
     * Blocks with scheduled ticks that are worth searching for, besides those seen in the last analyses.
     */
    private static final List<String> TICK_TYPES = List.of("repeater", "comparator", "observer", "redstone_torch", "redstone_wall_torch",
        "dispenser", "dropper", "water", "flowing_water", "lava", "flowing_lava");

    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * The last result per sender.
     */
    private static final Map<String, ReportView> RESULTS = new HashMap<>();

    private ChunkAnalysisCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("analyzechunks")
            .executes(ctx -> analyze(sender(ctx), Query.top(Sort.SCORE, null)));

        // /ou analyzechunks [score|entities|blockentities|ticks] [world]
        for (Sort sort : List.of(Sort.SCORE, Sort.ENTITIES, Sort.BLOCK_ENTITIES, Sort.TICKS)) {
            node.then(Commands.literal(sort.argument)
                .executes(ctx -> analyze(sender(ctx), Query.top(sort, null)))
                .then(Commands.argument("world", ArgumentTypes.world())
                    .executes(ctx -> analyze(sender(ctx), Query.top(sort, worldKey(ctx))))));
        }

        return node
            // /ou analyzechunks type <type> [world]
            .then(Commands.literal("type")
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests(ChunkAnalysisCommand::suggestTypes)
                    .executes(ctx -> analyze(sender(ctx), Query.type(type(ctx), null)))
                    .then(Commands.argument("world", ArgumentTypes.world())
                        .executes(ctx -> analyze(sender(ctx), Query.type(type(ctx), worldKey(ctx)))))))
            // /ou analyzechunks player <player>
            .then(Commands.literal("player")
                .then(Commands.argument("player", ArgumentTypes.player())
                    .executes(ctx -> analyze(sender(ctx), Query.player(targetPlayer(ctx).getName())))))
            // /ou analyzechunks here
            .then(Commands.literal("here")
                .requires(source -> source.getSender() instanceof Player)
                .executes(ctx -> {
                    Player player = (Player) sender(ctx);
                    Chunk chunk = player.getLocation().getChunk();
                    return detail(player, player.getWorld(), chunk.getX(), chunk.getZ());
                }))
            // /ou analyzechunks chunk <world> <x> <z>
            .then(Commands.literal("chunk")
                .then(Commands.argument("world", ArgumentTypes.world())
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .suggests((ctx, builder) -> suggestOwnChunk(ctx, builder, chunk -> chunk.getX()))
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .suggests((ctx, builder) -> suggestOwnChunk(ctx, builder, chunk -> chunk.getZ()))
                            .executes(ctx -> detail(sender(ctx), ctx.getArgument("world", World.class),
                                IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z")))))))
            // /ou analyzechunks page [page]
            .then(Commands.literal("page")
                .executes(ctx -> page(sender(ctx), -1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> page(sender(ctx), IntegerArgumentType.getInteger(ctx, "page")))))
            // /ou analyzechunks sort <score|entities|blockentities|ticks|type>
            .then(sortNode())
            // /ou analyzechunks export
            .then(Commands.literal("export")
                .executes(ctx -> export(sender(ctx))))
            // /ou analyzechunks refresh
            .then(Commands.literal("refresh")
                .executes(ctx -> refresh(sender(ctx))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sortNode() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("sort");
        for (Sort sort : Sort.values()) {
            node.then(Commands.literal(sort.argument)
                .executes(ctx -> sort(sender(ctx), sort)));
        }
        return node;
    }

    private static int analyze(CommandSender sender, Query query) {
        List<World> worlds;
        if (query.world() != null) {
            NamespacedKey key = NamespacedKey.fromString(query.world());
            World world = key != null ? Bukkit.getWorld(key) : null;
            if (world == null) {
                sender.sendMessage(Component.text("The world " + query.world() + " is not loaded.", NamedTextColor.RED));
                return 0;
            }
            worlds = List.of(world);
        } else {
            worlds = Bukkit.getWorlds();
        }

        int duration = tickSampleDuration();
        int loadedChunks = worlds.stream().mapToInt(World::getChunkCount).sum();
        sender.sendMessage(Component.text("Analyzing " + Format.count(loadedChunks) + " loaded chunks, counting scheduled ticks for "
            + Format.decimal(duration / 20.0) + "s...", NamedTextColor.GRAY));

        LongPredicate allChunks = key -> true;
        ScheduledTickSampler.sample(worlds, allChunks, duration, samples -> {
            ChunkReport report = ChunkScanner.scan(query, worlds, allChunks, samples, CostWeights.from(config()));
            ReportView view = new ReportView(report, query.sort(), 1, config().chunksPerPage);
            remember(sender, view);
            send(sender, ChunkReportMessages.list(view, sender instanceof Player, System.currentTimeMillis()));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int detail(CommandSender sender, World world, int x, int z) {
        String place = "chunk " + x + " " + z + " of " + world.getName();
        if (!world.isChunkLoaded(x, z)) {
            sender.sendMessage(Component.text("The " + place + " is not loaded.", NamedTextColor.RED));
            return 0;
        }

        int duration = tickSampleDuration();
        sender.sendMessage(Component.text("Analyzing " + place + ", counting scheduled ticks for " + Format.decimal(duration / 20.0) + "s...", NamedTextColor.GRAY));

        long chunkKey = Chunk.getChunkKey(x, z);
        LongPredicate onlyThisChunk = key -> key == chunkKey;
        List<World> worlds = List.of(world);
        ScheduledTickSampler.sample(worlds, onlyThisChunk, duration, samples -> {
            ChunkReport report = ChunkScanner.scan(Query.top(Sort.SCORE, world.getKey().asString()), worlds, onlyThisChunk, samples, CostWeights.from(config()));
            ChunkStats chunk = report.find(world.getKey().asString(), x, z);
            if (chunk == null) {
                send(sender, Component.text("The " + place + " has no entities, block entities or scheduled ticks.", NamedTextColor.GRAY));
                return;
            }

            send(sender, ChunkReportMessages.detail(report, chunk, sender instanceof Player, result(sender) != null));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int page(CommandSender sender, int page) {
        ReportView view = result(sender);
        if (view == null) return noResult(sender);

        view = view.withPage(page < 1 ? view.page() : page);
        remember(sender, view);
        sender.sendMessage(ChunkReportMessages.list(view, sender instanceof Player, System.currentTimeMillis()));
        return Command.SINGLE_SUCCESS;
    }

    private static int sort(CommandSender sender, Sort sort) {
        ReportView view = result(sender);
        if (view == null) return noResult(sender);

        if (sort == Sort.TYPE && view.report().query.type() == null) {
            sender.sendMessage(Component.text("Only analyses of a type (" + ChunkReportMessages.COMMAND + " type <type>) can be ranked by type.", NamedTextColor.RED));
            return 0;
        }

        view = view.withSort(sort);
        remember(sender, view);
        sender.sendMessage(ChunkReportMessages.list(view, sender instanceof Player, System.currentTimeMillis()));
        return Command.SINGLE_SUCCESS;
    }

    private static int refresh(CommandSender sender) {
        ReportView view = result(sender);
        if (view == null) return noResult(sender);

        return analyze(sender, view.report().query.withSort(view.sort()));
    }

    private static int export(CommandSender sender) {
        ReportView view = result(sender);
        if (view == null) return noResult(sender);

        OptimizationUtils plugin = OptimizationUtils.instance();
        Path file = plugin.getDataFolder().toPath().resolve("exports").resolve("chunk-analysis-" + LocalDateTime.now().format(EXPORT_TIME) + ".csv");
        plugin.fileWriter().write(file, ChunkReportMessages.csv(view));

        sender.sendMessage(Component.text("Saving " + Format.count(view.ranked().size()) + " chunks to "
            + plugin.getDataFolder().getName() + "/exports/" + file.getFileName(), NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int noResult(CommandSender sender) {
        sender.sendMessage(Component.text("Nothing analyzed yet (or too long ago), run " + ChunkReportMessages.COMMAND + " first.", NamedTextColor.RED));
        return 0;
    }

    /**
     * Sends the result, unless the player left while it was worked out.
     */
    private static void send(CommandSender sender, Component message) {
        if (sender instanceof Player player && !player.isOnline()) return;
        sender.sendMessage(message);
    }

    private static void remember(CommandSender sender, ReportView view) {
        long now = System.currentTimeMillis();
        RESULTS.values().removeIf(result -> now - result.report().createdAt > KEEP_RESULT_MILLIS);
        RESULTS.put(resultKey(sender), view);
    }

    private static ReportView result(CommandSender sender) {
        ReportView view = RESULTS.get(resultKey(sender));
        if (view == null || System.currentTimeMillis() - view.report().createdAt > KEEP_RESULT_MILLIS) return null;
        return view;
    }

    private static String resultKey(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : sender.getName();
    }

    private static CompletableFuture<Suggestions> suggestTypes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> types = new TreeSet<>(TICK_TYPES);
        for (var key : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (!key.getPath().equals("player")) types.add(key.getPath());
        }
        for (var key : BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()) {
            types.add(key.getPath());
        }
        // The blocks and fluids that scheduled ticks lately, there are too many blocks to suggest all
        for (ReportView view : RESULTS.values()) {
            types.addAll(view.report().tickTotals.typeNames());
        }

        String remaining = builder.getRemainingLowerCase();
        types.stream().filter(type -> type.startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    /**
     * Suggests the coordinate of the chunk the player stands in.
     */
    private static CompletableFuture<Suggestions> suggestOwnChunk(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, ToIntFunction<Chunk> coordinate) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            builder.suggest(coordinate.applyAsInt(player.getLocation().getChunk()));
        }
        return builder.buildFuture();
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    private static String worldKey(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument("world", World.class).getKey().asString();
    }

    private static String type(CommandContext<CommandSourceStack> ctx) {
        return CostWeights.normalize(StringArgumentType.getString(ctx, "type"));
    }

    private static Player targetPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
    }

    private static int tickSampleDuration() {
        return Math.clamp(config().tickSampleDuration, 1, 200);
    }

    private static PluginConfiguration.ChunkAnalysis config() {
        return OptimizationUtils.instance().pluginConfiguration().chunkAnalysis;
    }
}
