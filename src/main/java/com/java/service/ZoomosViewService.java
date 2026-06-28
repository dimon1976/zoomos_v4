package com.java.service;

import com.java.dto.zoomos.SchedulePageDto;
import com.java.model.entity.ZoomosCityId;
import com.java.model.entity.ZoomosCheckRun;
import com.java.model.entity.ZoomosKnownSite;
import com.java.model.entity.ZoomosShop;
import com.java.model.entity.ZoomosShopSchedule;
import com.java.repository.ZoomosCityIdRepository;
import com.java.repository.ZoomosCheckRunRepository;
import com.java.repository.ZoomosKnownSiteRepository;
import com.java.repository.ZoomosParsingStatsRepository;
import com.java.repository.ZoomosShopScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ARCH-002: возвращаем типизированный SchedulePageDto вместо Map<String,Object>

/**
 * Сервис для агрегации данных, отображаемых на страницах Zoomos.
 * ARCH-001: выделен из ZoomosAnalysisController для соблюдения слоёной архитектуры.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZoomosViewService {

    private final ZoomosParserService parserService;
    private final ZoomosShopScheduleRepository scheduleRepository;
    private final ZoomosCheckRunRepository checkRunRepository;
    private final ZoomosParsingStatsRepository parsingStatsRepository;
    private final ZoomosKnownSiteRepository knownSiteRepository;
    private final ZoomosCityIdRepository cityIdRepository;

    private static final DateTimeFormatter SCHEDULE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * PERF-001: batch-загрузка данных для страницы расписаний (/zoomos/schedule).
     * Заменяет N+1 (по одному запросу на каждый магазин) двумя батч-запросами.
     * ARCH-002: возвращает типизированный SchedulePageDto.
     */
    @Transactional(readOnly = true)
    public SchedulePageDto buildScheduleModel() {
        List<ZoomosShop> shops = parserService.getAllShops();
        List<Long> shopIds = shops.stream().map(ZoomosShop::getId).toList();

        // Batch: все расписания — 1 запрос вместо N
        Map<Long, List<ZoomosShopSchedule>> schedules = scheduleRepository
                .findAllByShopIdIn(shopIds)
                .stream()
                .collect(Collectors.groupingBy(ZoomosShopSchedule::getShopId, LinkedHashMap::new, Collectors.toList()));
        // Гарантируем наличие всех магазинов в Map (иначе schedules[shop.id] = null в шаблоне → NPE в SpEL)
        shops.forEach(s -> schedules.putIfAbsent(s.getId(), List.of()));

        // Batch: последние запуски — 1 запрос вместо N, JOIN FETCH исключает lazy N+1
        Map<Long, ZoomosCheckRun> lastRuns = checkRunRepository
                .findLastRunsForShops(shopIds)
                .stream()
                .collect(Collectors.toMap(r -> r.getShop().getId(), r -> r));

        Map<Long, String> lastRunFormatted = new LinkedHashMap<>();
        Map<Long, Long> lastRunIds = new LinkedHashMap<>();

        for (ZoomosShop shop : shops) {
            List<ZoomosShopSchedule> list = schedules.getOrDefault(shop.getId(), List.of());
            ZoomosCheckRun lastRun = lastRuns.get(shop.getId());
            if (lastRun != null) {
                list.forEach(s -> lastRunIds.put(s.getId(), lastRun.getId()));
            }
            for (ZoomosShopSchedule s : list) {
                if (s.getLastRunAt() != null) {
                    lastRunFormatted.put(s.getId(),
                            s.getLastRunAt().withZoneSameInstant(java.time.ZoneId.systemDefault()).format(SCHEDULE_FMT));
                }
            }
        }

        return new SchedulePageDto(shops, schedules, lastRunFormatted, lastRunIds);
    }

    /**
     * ARCH-001: @Transactional перенесён из контроллера в сервис.
     * Удаляет историю проверок вместе со статистикой парсинга.
     */
    @Transactional
    public int deleteCheckRuns(List<Long> ids) {
        for (Long id : ids) {
            parsingStatsRepository.deleteByCheckRunId(id);
            checkRunRepository.deleteById(id);
        }
        log.info("Удалено {} записей истории проверок", ids.size());
        return ids.size();
    }

    /**
     * ARCH-001: @Transactional перенесён из контроллера в сервис.
     * Удаляет известный сайт вместе с привязанными cityId-записями.
     *
     * @return количество удалённых cityId-записей, или -1 если сайт не найден
     */
    @Transactional
    public int deleteKnownSite(Long id) {
        return knownSiteRepository.findById(id).map(site -> {
            List<ZoomosCityId> cityIds = cityIdRepository.findAllBySiteName(site.getSiteName());
            cityIdRepository.deleteAll(cityIds);
            knownSiteRepository.delete(site);
            log.info("Удалён сайт {} с {} cityId-записями", site.getSiteName(), cityIds.size());
            return cityIds.size();
        }).orElse(-1);
    }
}
