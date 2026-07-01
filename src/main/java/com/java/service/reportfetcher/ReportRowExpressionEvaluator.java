package com.java.service.reportfetcher;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Evaluates SpEL expressions against a single report row (Map&lt;columnHeader, value&gt;).
 * Column references use bracket indexing on the row map: ['Column Name'].
 */
@Component
public class ReportRowExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Evaluates a COMPUTED column formula. Returns null (empty cell) on any parse/evaluation error
     * instead of throwing, per spec: a broken formula must not abort the whole run.
     */
    public Object evaluateFormula(String formula, Map<String, Object> row) {
        try {
            Expression expression = parser.parseExpression(formula);
            return expression.getValue(row);
        } catch (Exception e) {
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
            Object result = expression.getValue(row);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
