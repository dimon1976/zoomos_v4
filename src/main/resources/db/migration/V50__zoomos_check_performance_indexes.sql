-- V50: Индексы производительности для Zoomos Check

-- A1: Фильтрация check runs по статусу (findAllByStatus, ZoomosSchedulerService init)
CREATE INDEX IF NOT EXISTS idx_check_runs_status ON zoomos_check_runs(status);

-- A2: Расписания по магазину (findAllByShopId, findAllByShopIdIn)
CREATE INDEX IF NOT EXISTS idx_shop_schedules_shop_id ON zoomos_shop_schedules(shop_id);

-- A3: Быстрый поиск включённых расписаний при старте планировщика
CREATE INDEX IF NOT EXISTS idx_shop_schedules_is_enabled
    ON zoomos_shop_schedules(is_enabled)
    WHERE is_enabled = TRUE;

-- A4: Составной индекс для baseline-запросов (findForBaseline: site_name + is_baseline + start_time)
CREATE INDEX IF NOT EXISTS idx_parsing_stats_baseline_lookup
    ON zoomos_parsing_stats(site_name, is_baseline, start_time DESC)
    WHERE is_baseline = TRUE;

-- A5: Индекс для поиска завершённых выкачек по сайту и адресу (findLatestFinished*)
CREATE INDEX IF NOT EXISTS idx_parsing_stats_site_addr_completion
    ON zoomos_parsing_stats(site_name, address_id, completion_percent, start_time DESC)
    WHERE completion_percent >= 100;
