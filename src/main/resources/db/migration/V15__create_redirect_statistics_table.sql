-- V15__create_redirect_statistics_table.sql
-- Создаем таблицу для статистики редиректов

CREATE TABLE redirect_statistics (
    id BIGSERIAL PRIMARY KEY,
    original_url VARCHAR(2000) NOT NULL,
    final_url VARCHAR(2000) NOT NULL,
    strategy_name VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    redirect_count INTEGER NOT NULL DEFAULT 0,
    processing_time_ms BIGINT NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    error_message VARCHAR(1000),
    domain VARCHAR(255),
    http_status_code INTEGER,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Индексы для аналитики
CREATE INDEX idx_redirect_statistics_strategy_name ON redirect_statistics(strategy_name);
CREATE INDEX idx_redirect_statistics_domain ON redirect_statistics(domain);
CREATE INDEX idx_redirect_statistics_success ON redirect_statistics(success);
CREATE INDEX idx_redirect_statistics_is_blocked ON redirect_statistics(is_blocked);
CREATE INDEX idx_redirect_statistics_created_at ON redirect_statistics(created_at);
CREATE INDEX idx_redirect_statistics_status ON redirect_statistics(status);

-- Композитные индексы для сложных запросов
CREATE INDEX idx_redirect_statistics_strategy_success ON redirect_statistics(strategy_name, success);
CREATE INDEX idx_redirect_statistics_domain_success ON redirect_statistics(domain, success);
CREATE INDEX idx_redirect_statistics_created_strategy ON redirect_statistics(created_at, strategy_name);

-- Комментарии для документации
COMMENT ON TABLE redirect_statistics IS 'Статистика работы стратегий обхода блокировок';
COMMENT ON COLUMN redirect_statistics.strategy_name IS 'Название стратегии: SimpleHttp, EnhancedHttp, ProxyHttp, SeleniumBrowser, PlaywrightBrowser';
COMMENT ON COLUMN redirect_statistics.status IS 'Статус обработки: SUCCESS, ERROR, BLOCKED_HTTP_403, TIMEOUT, etc.';
COMMENT ON COLUMN redirect_statistics.processing_time_ms IS 'Время обработки в миллисекундах';
COMMENT ON COLUMN redirect_statistics.is_blocked IS 'Признак блокировки (403, 429, и т.д.)';