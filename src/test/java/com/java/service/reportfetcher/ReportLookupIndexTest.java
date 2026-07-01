package com.java.service.reportfetcher;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReportLookupIndexTest {

    private final List<List<String>> lookupRows = List.of(
            List.of("ОГРН", "Юр. лицо", "Город"),
            List.of("111", "ООО Ромашка", "Минск"),
            List.of("222", "ООО Василёк", "Гродно")
    );

    @Test
    void shouldFindValueByKey() {
        ReportLookupIndex index = ReportLookupIndex.build(lookupRows, "ОГРН");

        assertEquals(Optional.of("ООО Ромашка"), index.getValue("111", "Юр. лицо"));
        assertEquals(Optional.of("Минск"), index.getValue("111", "Город"));
        assertEquals(Optional.of("Гродно"), index.getValue("222", "Город"));
    }

    @Test
    void shouldReturnEmptyWhenKeyNotFound() {
        ReportLookupIndex index = ReportLookupIndex.build(lookupRows, "ОГРН");

        assertEquals(Optional.empty(), index.getValue("999", "Юр. лицо"));
    }

    @Test
    void shouldReturnEmptyWhenKeyValueIsNull() {
        ReportLookupIndex index = ReportLookupIndex.build(lookupRows, "ОГРН");

        assertEquals(Optional.empty(), index.getValue(null, "Юр. лицо"));
    }

    @Test
    void shouldKeepFirstRowOnDuplicateKeys() {
        List<List<String>> withDuplicate = List.of(
                List.of("ОГРН", "Юр. лицо"),
                List.of("111", "Первый"),
                List.of("111", "Второй")
        );
        ReportLookupIndex index = ReportLookupIndex.build(withDuplicate, "ОГРН");

        assertEquals(Optional.of("Первый"), index.getValue("111", "Юр. лицо"));
    }

    @Test
    void shouldThrowWhenKeyColumnMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> ReportLookupIndex.build(lookupRows, "Несуществующая колонка"));
    }

    @Test
    void shouldHandleEmptyRows() {
        ReportLookupIndex index = ReportLookupIndex.build(List.of(), null);

        assertEquals(Optional.empty(), index.getValue("111", "Юр. лицо"));
    }
}
