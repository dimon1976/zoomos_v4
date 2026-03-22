package com.java.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZoomosScheduleConfigDto {

    private String label;
    @Builder.Default
    private String cronExpression = "0 0 8 * * *";
    /** При импорте всегда false — требует явного включения на новом сервере */
    @Builder.Default
    private boolean isEnabled = false;
    private String timeFrom;
    private String timeTo;
    @Builder.Default
    private int dropThreshold = 10;
    @Builder.Default
    private int errorGrowthThreshold = 30;
    @Builder.Default
    private int baselineDays = 7;
    @Builder.Default
    private int minAbsoluteErrors = 5;
    @Builder.Default
    private int trendDropThreshold = 30;
    @Builder.Default
    private int trendErrorThreshold = 100;
    @Builder.Default
    private int dateOffsetFrom = -1;
    @Builder.Default
    private int dateOffsetTo = 0;
}
