package com.java.service.reportfetcher;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportRowExpressionEvaluatorTest {

    private final ReportRowExpressionEvaluator evaluator = new ReportRowExpressionEvaluator();

    private Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Цена", 150);
        row.put("РРЦ", 100);
        row.put("Цена конкурента", 120);
        row.put("ОГРН", "1234567890");
        row.put("Название", "Товар со скидкой");
        return row;
    }

    @Test
    void shouldEvaluateArithmeticFormula() {
        Object result = evaluator.evaluateFormula("['Цена'] - ['РРЦ']", row());
        assertEquals(50, result);
    }

    @Test
    void shouldEvaluateFormulaWithSpacedColumnName() {
        Object result = evaluator.evaluateFormula("['Цена'] - ['Цена конкурента']", row());
        assertEquals(30, result);
    }

    @Test
    void shouldReturnNullOnBrokenFormula() {
        Object result = evaluator.evaluateFormula("['Цена'] +++ ", row());
        assertNull(result);
    }

    @Test
    void shouldEvaluateFilterToTrueWhenConditionMatches() {
        boolean result = evaluator.evaluateFilter("['ОГРН'] != null and ['ОГРН'] != ''", row());
        assertTrue(result);
    }

    @Test
    void shouldEvaluateFilterToFalseWhenConditionDoesNotMatch() {
        boolean result = evaluator.evaluateFilter("['Цена'] > 1000", row());
        assertFalse(result);
    }

    @Test
    void shouldEvaluateFilterToFalseOnBrokenExpression() {
        boolean result = evaluator.evaluateFilter("this is not spel", row());
        assertFalse(result);
    }

    @Test
    void shouldSupportContainsAndTernary() {
        assertTrue((boolean) evaluator.evaluateFormula("['Название'].contains('скидкой')", row()));
        assertEquals("Дороже", evaluator.evaluateFormula("['Цена'] > ['РРЦ'] ? 'Дороже' : 'Дешевле или равно'", row()));
    }
}
