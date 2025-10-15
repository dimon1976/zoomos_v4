package com.java.service.statistics;

import com.java.dto.StatisticsHistoryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис для анализа трендов в статистических данных
 * Использует метод линейной регрессии (наименьших квадратов)
 */
@Service
@Slf4j
public class TrendAnalysisService {

    /**
     * Анализирует тренд на основе исторических данных
     *
     * @param dataPoints список точек данных (должен быть отсортирован по дате)
     * @return информация о тренде
     */
    public StatisticsHistoryDto.TrendInfo analyzeTrend(List<StatisticsHistoryDto.DataPoint> dataPoints) {
        if (dataPoints == null || dataPoints.size() < 2) {
            log.debug("Недостаточно данных для анализа тренда: {}", dataPoints != null ? dataPoints.size() : 0);
            return createDefaultTrend();
        }

        // Конвертируем данные для регрессии (x = индекс, y = значение)
        int n = dataPoints.size();
        double[] x = new double[n];
        double[] y = new double[n];

        for (int i = 0; i < n; i++) {
            x[i] = i;  // Индекс точки (0, 1, 2, ...)
            y[i] = dataPoints.get(i).getValue().doubleValue();
        }

        // Вычисляем линейную регрессию y = mx + b
        LinearRegressionResult regression = calculateLinearRegression(x, y);

        // Вычисляем процент изменения между первой и последней точкой
        double firstValue = y[0];
        double lastValue = y[n - 1];
        double changePercentage = firstValue != 0
            ? ((lastValue - firstValue) / firstValue) * 100.0
            : 0.0;

        // Определяем направление тренда на основе наклона и процента изменения
        StatisticsHistoryDto.TrendDirection direction = determineTrendDirection(
            regression.slope, changePercentage, regression.rSquared);

        // Формируем текстовое описание
        String description = generateTrendDescription(direction, changePercentage, regression.rSquared);

        log.debug("Анализ тренда: direction={}, slope={}, R²={}, change={}%",
            direction, regression.slope, regression.rSquared, String.format("%.2f", changePercentage));

        return StatisticsHistoryDto.TrendInfo.builder()
                .direction(direction)
                .slope(regression.slope)
                .confidence(regression.rSquared)
                .changePercentage(changePercentage)
                .description(description)
                .build();
    }

    /**
     * Вычисляет линейную регрессию методом наименьших квадратов
     */
    private LinearRegressionResult calculateLinearRegression(double[] x, double[] y) {
        int n = x.length;

        // Вычисляем средние значения
        double sumX = 0.0, sumY = 0.0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
        }
        double meanX = sumX / n;
        double meanY = sumY / n;

        // Вычисляем наклон (slope) и свободный член (intercept)
        double numerator = 0.0;   // числитель
        double denominator = 0.0; // знаменатель
        for (int i = 0; i < n; i++) {
            numerator += (x[i] - meanX) * (y[i] - meanY);
            denominator += (x[i] - meanX) * (x[i] - meanX);
        }

        double slope = denominator != 0 ? numerator / denominator : 0.0;
        double intercept = meanY - slope * meanX;

        // Вычисляем коэффициент детерминации R² (качество подгонки)
        double ssTotal = 0.0;  // Total Sum of Squares
        double ssResidual = 0.0;  // Residual Sum of Squares
        for (int i = 0; i < n; i++) {
            double predictedY = slope * x[i] + intercept;
            ssTotal += (y[i] - meanY) * (y[i] - meanY);
            ssResidual += (y[i] - predictedY) * (y[i] - predictedY);
        }

        double rSquared = ssTotal != 0 ? 1.0 - (ssResidual / ssTotal) : 0.0;
        // Ограничиваем R² диапазоном [0, 1]
        rSquared = Math.max(0.0, Math.min(1.0, rSquared));

        return new LinearRegressionResult(slope, intercept, rSquared);
    }

    /**
     * Определяет направление тренда на основе наклона и процента изменения
     */
    private StatisticsHistoryDto.TrendDirection determineTrendDirection(
            double slope, double changePercentage, double rSquared) {

        // Если R² слишком низкий (< 0.3), данные слишком нестабильны
        if (rSquared < 0.3) {
            return StatisticsHistoryDto.TrendDirection.STABLE;
        }

        // Определяем на основе процента изменения
        if (changePercentage > 10.0) {
            return StatisticsHistoryDto.TrendDirection.STRONG_GROWTH;
        } else if (changePercentage > 5.0) {
            return StatisticsHistoryDto.TrendDirection.GROWTH;
        } else if (changePercentage < -10.0) {
            return StatisticsHistoryDto.TrendDirection.STRONG_DECLINE;
        } else if (changePercentage < -5.0) {
            return StatisticsHistoryDto.TrendDirection.DECLINE;
        } else {
            return StatisticsHistoryDto.TrendDirection.STABLE;
        }
    }

    /**
     * Генерирует текстовое описание тренда
     */
    private String generateTrendDescription(
            StatisticsHistoryDto.TrendDirection direction,
            double changePercentage,
            double rSquared) {

        String confidenceText = rSquared > 0.7 ? "Устойчивый" : rSquared > 0.5 ? "Умеренный" : "Слабый";
        String changeText = String.format("%.1f%%", Math.abs(changePercentage));

        return switch (direction) {
            case STRONG_GROWTH -> confidenceText + " сильный рост на " + changeText;
            case GROWTH -> confidenceText + " рост на " + changeText;
            case STABLE -> "Стабильные показатели (изменение в пределах ±5%)";
            case DECLINE -> confidenceText + " спад на " + changeText;
            case STRONG_DECLINE -> confidenceText + " сильный спад на " + changeText;
        };
    }

    /**
     * Создает тренд по умолчанию (для случаев недостаточных данных)
     */
    private StatisticsHistoryDto.TrendInfo createDefaultTrend() {
        return StatisticsHistoryDto.TrendInfo.builder()
                .direction(StatisticsHistoryDto.TrendDirection.STABLE)
                .slope(0.0)
                .confidence(0.0)
                .changePercentage(0.0)
                .description("Недостаточно данных для анализа тренда")
                .build();
    }

    /**
     * Вспомогательный класс для хранения результатов регрессии
     */
    private record LinearRegressionResult(double slope, double intercept, double rSquared) {}
}
