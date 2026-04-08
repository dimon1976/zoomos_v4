package com.java.dto.zoomos;

import com.java.model.entity.ZoomosCheckRun;
import com.java.model.entity.ZoomosShop;
import com.java.model.entity.ZoomosShopSchedule;

import java.util.List;
import java.util.Map;

/**
 * ARCH-002: типизированный DTO для страницы расписаний /zoomos/schedule.
 * Заменяет Map&lt;String, Object&gt; — ошибки рефакторинга теперь выявляются на этапе компиляции.
 */
public record SchedulePageDto(
        List<ZoomosShop> shops,
        Map<Long, List<ZoomosShopSchedule>> schedules,
        Map<Long, String> lastRunFormatted,
        Map<Long, Long> lastRunIds
) {}
