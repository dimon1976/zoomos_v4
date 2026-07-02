package com.java.dto.reportfetcher;

import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies real Spring MVC request-parameter binding for {@link ReportConfigDto#outputColumns},
 * in particular the "included" checkbox. Plain unit tests that build the DTO directly in Java
 * (as ReportConfigServiceTest does) never exercise this path and previously missed a real bug:
 * with multiple request parameters sharing the same name, Spring's ServletRequestDataBinder
 * (backed by a ConversionService, exactly as Spring Boot wires it for @ModelAttribute) binds
 * the FIRST value in submission order — not the checked/unchecked state. The report-fetcher-form.html
 * template originally emitted the hidden "false" fallback BEFORE the checkbox, so "included"
 * always bound to false regardless of whether the checkbox was checked.
 */
class ReportConfigDtoBindingTest {

    private ReportConfigDto bind(MockHttpServletRequest request) {
        ReportConfigDto dto = new ReportConfigDto();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(dto);
        binder.setConversionService(new DefaultFormattingConversionService());
        binder.bind(request);
        return dto;
    }

    @Test
    void shouldBindIncludedTrueWhenCheckboxComesBeforeHiddenFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("outputColumns[0].type", "SOURCE");
        request.addParameter("outputColumns[0].outputHeaderName", "Цена");
        // Correct order (matches the current template): checkbox value first, hidden "false" second.
        request.addParameter("outputColumns[0].included", new String[]{"true", "false"});

        ReportConfigDto dto = bind(request);

        assertEquals(1, dto.getOutputColumns().size());
        assertTrue(dto.getOutputColumns().get(0).getIncluded(),
                "Checked checkbox must bind to true when it precedes the hidden fallback field");
    }

    @Test
    void shouldBindIncludedFalseWhenCheckboxUnchecked() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("outputColumns[0].type", "SOURCE");
        request.addParameter("outputColumns[0].outputHeaderName", "Цена");
        // Unchecked: browser omits the checkbox entirely, only the hidden fallback is submitted.
        request.addParameter("outputColumns[0].included", "false");

        ReportConfigDto dto = bind(request);

        assertEquals(1, dto.getOutputColumns().size());
        assertEquals(Boolean.FALSE, dto.getOutputColumns().get(0).getIncluded());
    }

    @Test
    void reversedFieldOrderWouldHaveBoundFalseRegardlessOfCheckboxState() {
        // Documents the actual bug that was in report-fetcher-form.html: hidden "false" emitted
        // BEFORE the checkbox meant a CHECKED checkbox still bound to false, because Spring binds
        // the first value among duplicate-named parameters.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("outputColumns[0].type", "SOURCE");
        request.addParameter("outputColumns[0].outputHeaderName", "Цена");
        request.addParameter("outputColumns[0].included", new String[]{"false", "true"});

        ReportConfigDto dto = bind(request);

        assertEquals(Boolean.FALSE, dto.getOutputColumns().get(0).getIncluded(),
                "This reproduces the bug — kept as documentation of why field order matters");
    }
}
