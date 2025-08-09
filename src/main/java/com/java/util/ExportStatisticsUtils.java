package com.java.util;

import com.java.dto.ExportStatisticsDto;

import java.util.List;

public class ExportStatisticsUtils {
    private ExportStatisticsUtils() {
    }

    /**
     * Calculates total count for a session across all rows.
     *
     * @param rows      statistics rows
     * @param sessionId session identifier
     * @return total count for specified session
     */
    public static long getTotal(List<ExportStatisticsDto.StatisticsRow> rows, Long sessionId) {
        if (rows == null || sessionId == null) {
            return 0L;
        }
        return rows.stream()
                .flatMap(row -> row.getMetrics().stream())
                .filter(metric -> sessionId.equals(metric.getSessionId()))
                .mapToLong(metric -> metric.getCount() != null ? metric.getCount() : 0L)
                .sum();
    }

    /**
     * Determines CSS class for change metrics based on type and percent.
     *
     * @param changeType       type of change
     * @param changePercent    percent of change
     * @param thresholdPercent deviation threshold in percent
     * @return CSS class representing the change
     */
    public static String getChangeClass(ExportStatisticsDto.ChangeType changeType, Double changePercent, Integer thresholdPercent) {
        if (changeType == null || changeType == ExportStatisticsDto.ChangeType.NO_CHANGE || changeType == ExportStatisticsDto.ChangeType.NO_DATA) {
            return "no-change";
        }

        double absPercent = Math.abs(changePercent != null ? changePercent : 0.0);
        int threshold = thresholdPercent != null ? thresholdPercent : 0;

        if (changeType == ExportStatisticsDto.ChangeType.INCREASE) {
            return absPercent >= threshold ? "significant-increase" : "increase";
        }
        if (changeType == ExportStatisticsDto.ChangeType.DECREASE) {
            return absPercent >= threshold ? "significant-decrease" : "decrease";
        }
        return "no-change";
    }
}
