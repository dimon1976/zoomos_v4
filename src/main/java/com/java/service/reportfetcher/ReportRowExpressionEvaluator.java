package com.java.service.reportfetcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Evaluates SpEL expressions against a single report row (Map&lt;columnHeader, value&gt;).
 * Column references use bracket indexing on the row map: ['Column Name'].
 */
@Slf4j
@Component
public class ReportRowExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Read-only evaluation context: no T() type references, no `new`, no bean references,
     * no assignment — reduces the SpEL attack surface for user-authored formulas.
     */
    private final SimpleEvaluationContext evaluationContext = SimpleEvaluationContext.forReadOnlyDataBinding()
            .withInstanceMethods()
            .build();

    /**
     * Evaluates a COMPUTED column formula. Returns null (empty cell) on any parse/evaluation error
     * instead of throwing, per spec: a broken formula must not abort the whole run.
     */
    public Object evaluateFormula(String formula, Map<String, Object> row) {
        try {
            Expression expression = parser.parseExpression(formula);
            return expression.getValue(evaluationContext, row);
        } catch (Exception e) {
            log.warn("Не удалось вычислить SpEL-выражение '{}': {}", formula, e.getMessage());
            return null;
        }
    }

    /**
     * Evaluates rowFilterExpression. Returns false (row excluded) on any parse/evaluation error,
     * per spec.
     */
    public boolean evaluateFilter(String filterExpression, Map<String, Object> row) {
        try {
            Expression expression = parser.parseExpression(filterExpression);
            Object result = expression.getValue(evaluationContext, row);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Не удалось вычислить SpEL-фильтр '{}': {}", filterExpression, e.getMessage());
            return false;
        }
    }
}
