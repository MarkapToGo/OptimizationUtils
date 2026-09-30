package com.markaptogo.optimizationutils.analysis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Turns reports into messages: compact and clickable for players, written out in full for the console.
 */
public final class ChunkReportMessages {

    public static final String COMMAND = "/ou analyzechunks";

    private static final int BAR_WIDTH = 10;
    private static final String BAR = "▮";
    private static final int ROW_TYPE_LENGTH = 16;
    private static final int HOVER_TYPES = 5;
    private static final int DETAIL_TYPES = 6;
    private static final int CONSOLE_TYPES = 3;
    private static final List<Sort> SORTS = List.of(Sort.SCORE, Sort.ENTITIES, Sort.BLOCK_ENTITIES, Sort.TICKS);

    private ChunkReportMessages() {
    }

    // ---- List ----

    public static Component list(ReportView view, boolean chat, long now) {
        return chat ? chatList(view, now) : consoleList(view);
    }

    private static Component chatList(ReportView view, long now) {
        ChunkReport report = view.report();
        List<ChunkStats> rows = view.rows();

        TextComponent.Builder message = Component.text()
            .append(Component.text("━━━ Chunk Analysis ━━━ ", NamedTextColor.GREEN))
            .append(Component.text(Format.count(report.loadedChunks) + " chunks · " + report.worlds + (report.worlds == 1 ? " world" : " worlds"), NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(totals(report))));
        long age = now - report.createdAt;
        if (age >= 5000) {
            message.append(Component.text(" · " + Format.duration(age) + " ago", NamedTextColor.DARK_GRAY));
        }

        String filters = report.query.describeFilters();
        if (!filters.isEmpty()) {
            message.append(Component.newline())
                .append(Component.text("Only: ", NamedTextColor.GRAY))
                .append(Component.text(filters, NamedTextColor.WHITE));
        }

        message.append(Component.newline()).append(sortButtons(view));

        if (rows.isEmpty()) {
            return message.append(Component.newline())
                .append(Component.text(emptyMessage(view), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(footer(view))
                .build();
        }

        double max = view.maxValue();
        for (int i = 0; i < rows.size(); i++) {
            message.append(Component.newline()).append(chatRow(view, view.firstRank() + i, rows.get(i), max));
        }

        return message.append(Component.newline()).append(footer(view)).build();
    }

    private static Component sortButtons(ReportView view) {
        List<Sort> sorts = new ArrayList<>();
        if (view.report().query.type() != null) sorts.add(Sort.TYPE);
        sorts.addAll(SORTS);

        TextComponent.Builder buttons = Component.text().append(Component.text("Sort:", NamedTextColor.GRAY));
        for (Sort sort : sorts) {
            String label = sort == Sort.TYPE ? Format.shorten(view.report().query.type(), 12) : sort.label;
            boolean current = sort == view.sort();
            buttons.append(Component.space())
                .append(Component.text("[" + label + "]", current ? NamedTextColor.AQUA : NamedTextColor.GRAY)
                    .decoration(TextDecoration.BOLD, current)
                    .hoverEvent(HoverEvent.showText(Component.text("Rank by " + (sort == Sort.TYPE ? "count of " + view.report().query.type() : sort.description), NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand(COMMAND + " sort " + sort.argument)));
        }
        return buttons.build();
    }

    private static Component chatRow(ReportView view, int rank, ChunkStats chunk, double max) {
        double value = view.sort().value(chunk, view.report().query.type());
        TextColor color = severity(chunk.score());

        TextComponent.Builder bar = Component.text();
        int filled = Format.filled(value, max, BAR_WIDTH);
        bar.append(Component.text(BAR.repeat(filled), color));
        bar.append(Component.text(BAR.repeat(BAR_WIDTH - filled), NamedTextColor.DARK_GRAY));

        Component left = Component.text()
            .append(Component.text("#" + rank + " ", NamedTextColor.GRAY))
            .append(bar)
            .append(Component.text(" " + valueText(view, chunk), color))
            .append(Component.text("  " + Format.shorten(mainType(view, chunk), ROW_TYPE_LENGTH), NamedTextColor.WHITE))
            .hoverEvent(HoverEvent.showText(chunkHover(view.report(), chunk).append(Component.newline()).append(Component.newline())
                .append(Component.text("Click for details", NamedTextColor.YELLOW))))
            .clickEvent(ClickEvent.runCommand(detailCommand(chunk)))
            .build();

        Component coordinates = Component.text(chunk.worldLabel + " " + chunk.centerX() + " " + chunk.centerZ(), NamedTextColor.GRAY)
            .hoverEvent(HoverEvent.showText(Component.text(chunk.worldName + " " + chunk.centerX() + " " + chunk.centerZ(), NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text("Click to teleport there", NamedTextColor.YELLOW))))
            .clickEvent(ClickEvent.runCommand(teleportCommand(chunk, chunk.teleportTarget())));

        return Component.text().append(left).append(Component.text("  ")).append(coordinates).build();
    }

    private static Component footer(ReportView view) {
        int page = view.clampedPage();
        int pages = view.pages();

        return Component.text()
            .append(pageButton("◀", page - 1, page > 1))
            .append(Component.text(" " + page + "/" + pages + " ", NamedTextColor.WHITE))
            .append(pageButton("▶", page + 1, page < pages))
            .append(Component.text("   "))
            .append(Component.text("[Export]", NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Save all " + Format.count(view.ranked().size()) + " chunks as a CSV file", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(COMMAND + " export")))
            .append(Component.space())
            .append(Component.text("[↻ Refresh]", NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Analyze again: " + view.report().query.withSort(view.sort()).command(), NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(COMMAND + " refresh")))
            .build();
    }

    private static String emptyMessage(ReportView view) {
        Query query = view.report().query;
        String ranking = view.sort() == Sort.TYPE ? query.type() : view.sort().description;
        return query.describeFilters().isEmpty()
            ? "No loaded chunk has any " + ranking + "."
            : "No loaded chunk matches (" + query.describeFilters() + ") and has any " + ranking + ".";
    }

    private static Component pageButton(String arrow, int page, boolean enabled) {
        if (!enabled) return Component.text(arrow, NamedTextColor.DARK_GRAY);

        return Component.text(arrow, NamedTextColor.AQUA)
            .hoverEvent(HoverEvent.showText(Component.text("Page " + page, NamedTextColor.GRAY)))
            .clickEvent(ClickEvent.runCommand(COMMAND + " page " + page));
    }

    private static Component consoleList(ReportView view) {
        ChunkReport report = view.report();
        List<ChunkStats> rows = view.rows();

        TextComponent.Builder message = Component.text()
            .append(Component.text("=== Chunk Analysis ===", NamedTextColor.GREEN))
            .append(Component.newline())
            .append(Component.text(Format.count(report.loadedChunks) + " loaded chunks in " + report.worlds + (report.worlds == 1 ? " world" : " worlds")
                + ", scheduled ticks counted for " + Format.decimal(report.tickWindow / 20.0) + "s", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(consoleTotals(report));

        String filters = report.query.describeFilters();
        if (!filters.isEmpty()) {
            message.append(Component.newline()).append(Component.text("Only: " + filters, NamedTextColor.GRAY));
        }

        String sort = view.sort() == Sort.TYPE ? "count of " + report.query.type() : view.sort().description;
        message.append(Component.newline())
            .append(Component.text("Ranked by " + sort + ", page " + view.clampedPage() + "/" + view.pages() + ":", NamedTextColor.AQUA));

        if (rows.isEmpty()) {
            message.append(Component.newline()).append(Component.text(emptyMessage(view), NamedTextColor.GRAY));
        }

        for (int i = 0; i < rows.size(); i++) {
            ChunkStats chunk = rows.get(i);
            message.append(Component.newline())
                .append(Component.text("#" + (view.firstRank() + i) + " " + valueText(view, chunk) + " ", severity(chunk.score())))
                .append(Component.text(chunk.worldName + " " + chunk.centerX() + " " + chunk.centerZ() + " (chunk " + chunk.x + " " + chunk.z + ") - ", NamedTextColor.WHITE))
                .append(Component.text(consoleSummary(report, chunk), NamedTextColor.GRAY));
        }

        return message.append(Component.newline())
            .append(Component.text("More: " + COMMAND + " page <page> | sort <" + SORTS.stream().map(s -> s.argument).collect(Collectors.joining("|"))
                + "> | chunk <world> <x> <z> | export | refresh", NamedTextColor.YELLOW))
            .build();
    }

    private static String consoleSummary(ChunkReport report, ChunkStats chunk) {
        List<String> parts = new ArrayList<>();
        parts.add("score " + Format.decimal(chunk.score()));
        if (!chunk.entities.isEmpty()) {
            parts.add(chunk.entities.total() + " entities (" + types(report, chunk.entities, ChunkStats.Kind.ENTITY, CONSOLE_TYPES) + ")");
        }
        if (!chunk.blockEntities.isEmpty()) {
            parts.add(chunk.blockEntities.total() + " block entities, " + chunk.tickingBlockEntities() + " ticking ("
                + types(report, chunk.blockEntities, ChunkStats.Kind.BLOCK_ENTITY, CONSOLE_TYPES) + ")");
        }
        if (!chunk.ticks.isEmpty()) {
            parts.add(Format.perSecond(report.perSecond(chunk.ticks.total())) + " scheduled ticks (" + types(report, chunk.ticks, ChunkStats.Kind.TICK, CONSOLE_TYPES) + ")");
        }
        parts.add(loadedBy(chunk));
        return String.join(", ", parts);
    }

    // ---- Detail ----

    public static Component detail(ChunkReport report, ChunkStats chunk, boolean chat, boolean backToList) {
        TextComponent.Builder message = Component.text()
            .append(Component.text(chat ? "━━━ Chunk " + chunk.x + " " + chunk.z + " ━━━ " : "=== Chunk " + chunk.x + " " + chunk.z + " === ", NamedTextColor.GREEN))
            .append(Component.text(chunk.worldName + " " + chunk.centerX() + " " + chunk.centerZ(), NamedTextColor.GRAY))
            .append(Component.newline())
            .append(scoreLine(chunk))
            .append(Component.newline())
            .append(Component.text("Status: ", NamedTextColor.GRAY))
            .append(Component.text(chunk.loadStatus().label, NamedTextColor.WHITE))
            .append(Component.text(" (" + chunk.loadStatus().meaning + ")", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("Loaded: ", NamedTextColor.GRAY))
            .append(Component.text(loadedBy(chunk), NamedTextColor.WHITE));

        for (ChunkStats.Kind kind : ChunkStats.Kind.values()) {
            TypeCounts counts = chunk.of(kind);
            if (counts.isEmpty()) continue;

            message.append(Component.newline()).append(Component.text(kindTitle(report, chunk, kind), NamedTextColor.AQUA));
            List<String> types = counts.byCost();
            for (String type : types.stream().limit(DETAIL_TYPES).toList()) {
                message.append(Component.newline()).append(detailTypeLine(report, chunk, kind, type, chat));
            }
            if (types.size() > DETAIL_TYPES) {
                List<String> rest = types.subList(DETAIL_TYPES, types.size());
                Component more = Component.text("  … " + rest.size() + " more", NamedTextColor.GRAY);
                message.append(Component.newline()).append(chat
                    ? more.hoverEvent(HoverEvent.showText(Component.text(rest.stream().map(type -> typeAmount(report, chunk, kind, type)).collect(Collectors.joining("\n")), NamedTextColor.WHITE)))
                    : more.append(Component.text(": " + rest.stream().map(type -> typeAmount(report, chunk, kind, type)).collect(Collectors.joining(", ")), NamedTextColor.GRAY)));
            }
        }

        if (chunk.entities.isEmpty() && chunk.blockEntities.isEmpty() && chunk.ticks.isEmpty()) {
            message.append(Component.newline()).append(Component.text("Nothing that could cost anything is in this chunk.", NamedTextColor.GRAY));
        }

        if (!chat) return message.build();

        Position target = chunk.teleportTarget();
        String coordinates = String.format(Locale.ROOT, "%d %d %d", (int) Math.floor(target.x()), (int) Math.floor(target.y()), (int) Math.floor(target.z()));
        message.append(Component.newline())
            .append(Component.text("[Teleport]", NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text("Teleport to " + coordinates, NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(teleportCommand(chunk, target))))
            .append(Component.space())
            .append(Component.text("[Copy coordinates]", NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text("Copy \"" + coordinates + "\"", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.copyToClipboard(coordinates)))
            .append(Component.space())
            .append(Component.text("[↻]", NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Analyze this chunk again", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(detailCommand(chunk))));
        if (backToList) {
            message.append(Component.space())
                .append(Component.text("[◀ Back]", NamedTextColor.GRAY)
                    .hoverEvent(HoverEvent.showText(Component.text("Back to the list", NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand(COMMAND + " page")));
        }

        return message.build();
    }

    private static Component detailTypeLine(ChunkReport report, ChunkStats chunk, ChunkStats.Kind kind, String type, boolean chat) {
        TypeCounts counts = chunk.of(kind);
        Component line = Component.text("  " + typeAmount(report, chunk, kind, type), NamedTextColor.WHITE)
            .append(Component.text(counts.cost(type) > 0 ? " · cost " + Format.decimal(counts.cost(type)) : "", NamedTextColor.GRAY));

        Position position = counts.position(type);
        if (position == null) return line;

        String coordinates = String.format(Locale.ROOT, "%d %d %d", (int) Math.floor(position.x()), (int) Math.floor(position.y()), (int) Math.floor(position.z()));
        if (!chat) return line.append(Component.text(" (at " + coordinates + ")", NamedTextColor.DARK_GRAY));

        return line.hoverEvent(HoverEvent.showText(Component.text("Click to teleport to " + (kind == ChunkStats.Kind.TICK ? "a " + type + " tick" : "a " + type), NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(Component.text(coordinates, NamedTextColor.GRAY))))
            .clickEvent(ClickEvent.runCommand(teleportCommand(chunk, position)));
    }

    // ---- Export ----

    /**
     * All ranked chunks of the view as CSV.
     */
    public static String csv(ReportView view) {
        ChunkReport report = view.report();
        StringBuilder csv = new StringBuilder("rank,world,chunk_x,chunk_z,block_x,block_z,score,entity_cost,block_entity_cost,tick_cost,"
            + "entities,idle_entities,block_entities,ticking_block_entities,scheduled_ticks_per_second,load_status,loaded_by,entity_types,block_entity_types,tick_types\n");

        List<ChunkStats> ranked = view.ranked();
        for (int i = 0; i < ranked.size(); i++) {
            ChunkStats chunk = ranked.get(i);
            List<String> cells = List.of(
                String.valueOf(i + 1), chunk.worldName, String.valueOf(chunk.x), String.valueOf(chunk.z),
                String.valueOf(chunk.centerX()), String.valueOf(chunk.centerZ()),
                csvNumber(chunk.score()), csvNumber(chunk.entities.cost()), csvNumber(chunk.blockEntities.cost()), csvNumber(chunk.ticks.cost()),
                String.valueOf(chunk.entities.total()), String.valueOf(chunk.idleEntities()),
                String.valueOf(chunk.blockEntities.total()), String.valueOf(chunk.tickingBlockEntities()),
                csvNumber(report.perSecond(chunk.ticks.total())), chunk.loadStatus().label, loadedBy(chunk),
                csvTypes(chunk.entities), csvTypes(chunk.blockEntities), csvTypes(chunk.ticks)
            );
            csv.append(cells.stream().map(ChunkReportMessages::csvCell).collect(Collectors.joining(","))).append('\n');
        }

        return csv.toString();
    }

    static String csvCell(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String csvNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String csvTypes(TypeCounts counts) {
        return counts.byCount().stream().map(type -> type + ":" + counts.count(type)).collect(Collectors.joining(" "));
    }

    // ---- Shared ----

    private static Component chunkHover(ChunkReport report, ChunkStats chunk) {
        TextComponent.Builder hover = Component.text()
            .append(Component.text(chunk.worldName + " · chunk " + chunk.x + " " + chunk.z, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(scoreLine(chunk))
            .append(Component.newline())
            .append(Component.text(chunk.loadStatus().label + " (" + chunk.loadStatus().meaning + ")", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("Loaded: " + loadedBy(chunk), NamedTextColor.GRAY));

        for (ChunkStats.Kind kind : ChunkStats.Kind.values()) {
            TypeCounts counts = chunk.of(kind);
            if (counts.isEmpty()) continue;

            hover.append(Component.newline()).append(Component.text(kindTitle(report, chunk, kind), NamedTextColor.AQUA));
            List<String> types = counts.byCost();
            for (String type : types.stream().limit(HOVER_TYPES).toList()) {
                hover.append(Component.newline()).append(Component.text("  " + typeAmount(report, chunk, kind, type), NamedTextColor.WHITE));
            }
            if (types.size() > HOVER_TYPES) {
                hover.append(Component.newline()).append(Component.text("  … " + (types.size() - HOVER_TYPES) + " more", NamedTextColor.GRAY));
            }
        }

        return hover.build();
    }

    private static Component scoreLine(ChunkStats chunk) {
        return Component.text("Score " + Format.decimal(chunk.score()), severity(chunk.score()))
            .append(Component.text(" = " + Format.decimal(chunk.entities.cost()) + " entities + " + Format.decimal(chunk.blockEntities.cost())
                + " block entities + " + Format.decimal(chunk.ticks.cost()) + " ticks", NamedTextColor.GRAY));
    }

    private static Component totals(ChunkReport report) {
        TextComponent.Builder hover = Component.text()
            .append(Component.text("Scheduled ticks counted for " + Format.decimal(report.tickWindow / 20.0) + "s", NamedTextColor.GRAY));

        addTotals(hover, "Entities: " + Format.count(report.entityTotals.total()), report.entityTotals, type -> Format.count(report.entityTotals.count(type)) + " " + type);
        addTotals(hover, "Block entities: " + Format.count(report.blockEntityTotals.total()), report.blockEntityTotals, type -> Format.count(report.blockEntityTotals.count(type)) + " " + type);
        addTotals(hover, "Scheduled ticks: " + Format.perSecond(report.perSecond(report.tickTotals.total())), report.tickTotals,
            type -> Format.perSecond(report.perSecond(report.tickTotals.count(type))) + " " + type);

        return hover.build();
    }

    private static void addTotals(TextComponent.Builder hover, String title, TypeCounts counts, java.util.function.Function<String, String> line) {
        hover.append(Component.newline()).append(Component.text(title, NamedTextColor.AQUA));
        List<String> types = counts.byCount();
        for (String type : types.stream().limit(HOVER_TYPES).toList()) {
            hover.append(Component.newline()).append(Component.text("  " + line.apply(type), NamedTextColor.WHITE));
        }
        if (types.size() > HOVER_TYPES) {
            hover.append(Component.newline()).append(Component.text("  … " + (types.size() - HOVER_TYPES) + " more", NamedTextColor.GRAY));
        }
    }

    private static Component consoleTotals(ChunkReport report) {
        return Component.text("Entities: " + Format.count(report.entityTotals.total()) + " (" + totalsTypes(report.entityTotals, type -> Format.count(report.entityTotals.count(type)) + " " + type) + ")"
                + " | Block entities: " + Format.count(report.blockEntityTotals.total()) + " (" + totalsTypes(report.blockEntityTotals, type -> Format.count(report.blockEntityTotals.count(type)) + " " + type) + ")"
                + " | Scheduled ticks: " + Format.perSecond(report.perSecond(report.tickTotals.total()))
                + " (" + totalsTypes(report.tickTotals, type -> Format.perSecond(report.perSecond(report.tickTotals.count(type))) + " " + type) + ")",
            NamedTextColor.GRAY);
    }

    private static String totalsTypes(TypeCounts counts, java.util.function.Function<String, String> line) {
        return counts.byCount().stream().limit(HOVER_TYPES).map(line).collect(Collectors.joining(", "))
            + (counts.types() > HOVER_TYPES ? ", ..." : "");
    }

    private static String kindTitle(ChunkReport report, ChunkStats chunk, ChunkStats.Kind kind) {
        return switch (kind) {
            case ENTITY -> "Entities: " + chunk.entities.total() + (chunk.idleEntities() > 0 ? " (" + chunk.idleEntities() + " not ticking or without AI)" : "");
            case BLOCK_ENTITY -> "Block entities: " + chunk.blockEntities.total() + " (" + chunk.tickingBlockEntities() + " ticking)";
            case TICK -> "Scheduled ticks: " + Format.perSecond(report.perSecond(chunk.ticks.total()));
        };
    }

    /**
     * Like "villager×180" or, for scheduled ticks, "repeater 120/s".
     */
    private static String typeAmount(ChunkReport report, ChunkStats chunk, ChunkStats.Kind kind, String type) {
        return kind == ChunkStats.Kind.TICK
            ? type + " " + Format.perSecond(report.perSecond(chunk.ticks.count(type)))
            : type + "×" + chunk.of(kind).count(type);
    }

    private static String types(ChunkReport report, TypeCounts counts, ChunkStats.Kind kind, int limit) {
        return counts.byCost().stream().limit(limit)
            .map(type -> kind == ChunkStats.Kind.TICK ? type + " " + Format.perSecond(report.perSecond(counts.count(type))) : counts.count(type) + " " + type)
            .collect(Collectors.joining(", "))
            + (counts.types() > limit ? ", ..." : "");
    }

    /**
     * The number the chunk is ranked by.
     */
    private static String valueText(ReportView view, ChunkStats chunk) {
        ChunkReport report = view.report();
        return switch (view.sort()) {
            case SCORE -> Format.decimal(chunk.score());
            case ENTITIES -> Format.count(chunk.entities.total());
            case BLOCK_ENTITIES -> Format.count(chunk.blockEntities.total());
            case TICKS -> Format.perSecond(report.perSecond(chunk.ticks.total()));
            case TYPE -> chunk.ticks.count(report.query.type()) > 0 && chunk.entities.count(report.query.type()) + chunk.blockEntities.count(report.query.type()) == 0
                ? Format.perSecond(report.perSecond(chunk.ticks.count(report.query.type())))
                : Format.count(chunk.count(report.query.type()));
        };
    }

    /**
     * What stands out in the chunk for the sort, like "villager×180".
     */
    private static String mainType(ReportView view, ChunkStats chunk) {
        ChunkReport report = view.report();
        return switch (view.sort()) {
            case SCORE -> {
                ChunkStats.TypeRef costliest = chunk.costliestType();
                yield costliest == null ? "" : typeAmount(report, chunk, costliest.kind(), costliest.type());
            }
            case ENTITIES -> firstType(report, chunk, ChunkStats.Kind.ENTITY);
            case BLOCK_ENTITIES -> firstType(report, chunk, ChunkStats.Kind.BLOCK_ENTITY);
            case TICKS -> firstType(report, chunk, ChunkStats.Kind.TICK);
            case TYPE -> "score " + Format.decimal(chunk.score());
        };
    }

    private static String firstType(ChunkReport report, ChunkStats chunk, ChunkStats.Kind kind) {
        List<String> types = chunk.of(kind).byCount();
        return types.isEmpty() ? "" : typeAmount(report, chunk, kind, types.getFirst());
    }

    private static String loadedBy(ChunkStats chunk) {
        if (chunk.responsible() != null) {
            return "by " + chunk.responsible() + (chunk.responsibleDistance() == 0 ? " (in this chunk)" : " (" + chunk.responsibleDistance() + (chunk.responsibleDistance() == 1 ? " chunk away)" : " chunks away)"));
        }
        return chunk.loadReason() != null ? chunk.loadReason() : "unknown";
    }

    /**
     * From green (cheap) to red (expensive), by the score.
     */
    static TextColor severity(double score) {
        if (score >= 150) return NamedTextColor.RED;
        if (score >= 50) return NamedTextColor.GOLD;
        if (score >= 10) return NamedTextColor.YELLOW;
        return NamedTextColor.GREEN;
    }

    public static String detailCommand(ChunkStats chunk) {
        return COMMAND + " chunk " + chunk.worldKey + " " + chunk.x + " " + chunk.z;
    }

    private static String teleportCommand(ChunkStats chunk, Position position) {
        return String.format(Locale.ROOT, "/execute in %s run tp @s %.1f %.1f %.1f", chunk.worldKey, position.x(), position.y(), position.z());
    }
}
