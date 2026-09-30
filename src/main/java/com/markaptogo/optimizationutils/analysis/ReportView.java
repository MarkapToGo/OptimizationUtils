package com.markaptogo.optimizationutils.analysis;

import java.util.List;

/**
 * One page of a report, ranked by one sort.
 */
public record ReportView(ChunkReport report, Sort sort, int page, int perPage) {

    public ReportView {
        perPage = Math.max(1, perPage);
    }

    public List<ChunkStats> ranked() {
        return report.rank(sort);
    }

    public int pages() {
        return Math.max(1, (ranked().size() + perPage - 1) / perPage);
    }

    /**
     * The page, moved into the range of pages.
     */
    public int clampedPage() {
        return Math.clamp(page, 1, pages());
    }

    public List<ChunkStats> rows() {
        List<ChunkStats> ranked = ranked();
        int from = (clampedPage() - 1) * perPage;
        return ranked.subList(Math.min(from, ranked.size()), Math.min(from + perPage, ranked.size()));
    }

    /**
     * The rank of the first row of the page, starting at 1.
     */
    public int firstRank() {
        return (clampedPage() - 1) * perPage + 1;
    }

    /**
     * The value of the highest ranked chunk, to scale the bars to.
     */
    public double maxValue() {
        List<ChunkStats> ranked = ranked();
        return ranked.isEmpty() ? 0 : sort.value(ranked.getFirst(), report.query.type());
    }

    public ReportView withPage(int page) {
        return new ReportView(report, sort, page, perPage);
    }

    public ReportView withSort(Sort sort) {
        return new ReportView(report, sort, 1, perPage);
    }
}
