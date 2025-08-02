package com.java.service.exports.strategies;

import com.java.model.enums.ExportStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * Фабрика для получения стратегий экспорта
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExportStrategyFactory {

    private final ApplicationContext applicationContext;
    private final Map<ExportStrategy, com.java.service.exports.strategies.ExportStrategy> strategies = new HashMap<>();

    @PostConstruct
    public void init() {
        // Регистрируем стратегии
        strategies.put(ExportStrategy.DEFAULT,
                applicationContext.getBean("defaultExportStrategy",
                        com.java.service.exports.strategies.ExportStrategy.class));

        strategies.put(ExportStrategy.TASK_REPORT,
                applicationContext.getBean("taskReportExportStrategy",
                        com.java.service.exports.strategies.ExportStrategy.class));

        log.info("Инициализировано {} стратегий экспорта", strategies.size());
    }

    /**
     * Получить стратегию по типу
     */
    public com.java.service.exports.strategies.ExportStrategy getStrategy(ExportStrategy strategyType) {
        com.java.service.exports.strategies.ExportStrategy strategy = strategies.get(strategyType);

        if (strategy == null) {
            log.warn("Стратегия {} не найдена, используется DEFAULT", strategyType);
            return strategies.get(ExportStrategy.DEFAULT);
        }

        return strategy;
    }
}