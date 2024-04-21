package dev.webfx.cli.commands;

import dev.webfx.cli.util.stopwatch.StopWatch;
import dev.webfx.cli.util.texttable.TextTable;

/**
 * @author Bruno Salmon
 */
final class ExecutionTimesReport {

    private final TextTable timeTable = new TextTable();
    private final long totalTime;
    private boolean firstRow = true;
    private long cumulativeTime;
    private int totalCount;

    public ExecutionTimesReport(String firstColumnHeader, long totalTime) {
        timeTable.addCell(firstColumnHeader).addCell("Count").addCell("Time ms").addCell("Time %");
        this.totalTime = totalTime;
    }

    public ExecutionTimesReport addRow(String firstColumnValue, StopWatch stopWatch) {
        return addRow(firstColumnValue, stopWatch.getRunCount(), stopWatch);
    }

    public ExecutionTimesReport addRow(String firstColumnValue, int count, StopWatch stopWatch) {
        return addRow(firstColumnValue, count, stopWatch.getStopWatchElapsedTime());
    }

    public ExecutionTimesReport addRow(String firstColumnValue, int count, long time) {
        long timeMillis = time / 1_000_000;
        if (count == 0 && timeMillis == 0)
            return this;
        if (firstRow || time == totalTime)
            timeTable.addRowSeparator();
        else
            timeTable.newRow();
        timeTable
                .addCell(firstColumnValue)
                .addCell(count >= 0 ? formatNumber(count): "")
                .addCell(formatNumber(timeMillis) + " ms")
                .addCell((time * 100) / totalTime + " %");
        cumulativeTime += time;
        if (count > 0)
            totalCount += count;
        firstRow = false;
        return this;
    }

    public ExecutionTimesReport addComplementRow(String firstColumnValue) {
        return addRow(firstColumnValue, -1, totalTime - cumulativeTime);
    }

    public ExecutionTimesReport addTotalRow() {
        return addRow("Total", totalCount, cumulativeTime);
    }

    public String generateReport() {
        return timeTable.format();
    }

    private static String formatNumber(long number) {
        String text = String.valueOf(number);
        if (number > 1000)
            text = formatNumber(number / 1000) + " " + text.substring(text.length() - 3);
        return text;
    }

}
