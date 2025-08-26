package com.java.service.file;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import org.junit.jupiter.api.Test;

/**
 * Тест для исследования обработки escape символов в OpenCSV
 */
public class OpenCsvEscapeTest {

    @Test
    public void testEscapeCharacterConstants() {
        System.out.println("CSVParser.NULL_CHARACTER: " + (int)CSVParser.NULL_CHARACTER);
        System.out.println("CSVParser.DEFAULT_ESCAPE_CHARACTER: " + (int)CSVParser.DEFAULT_ESCAPE_CHARACTER);
        System.out.println("CSVParser.DEFAULT_QUOTE_CHARACTER: " + (int)CSVParser.DEFAULT_QUOTE_CHARACTER);
        System.out.println("CSVParser.DEFAULT_SEPARATOR: " + (int)CSVParser.DEFAULT_SEPARATOR);
    }

    @Test 
    public void testParsingWithDifferentEscapeChars() throws Exception {
        String testData = "Цена акционная\\по карте";
        
        // С обратным слэшем как escape символом (по умолчанию)
        CSVParser parserWithBackslash = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .withEscapeChar('\\')
                .build();
        
        String[] resultWithBackslash = parserWithBackslash.parseLine(testData);
        System.out.println("С escape='\\\\': " + java.util.Arrays.toString(resultWithBackslash));
        
        // Без escape символа
        CSVParser parserNoEscape = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .withEscapeChar(CSVParser.NULL_CHARACTER)
                .build();
        
        String[] resultNoEscape = parserNoEscape.parseLine(testData);
        System.out.println("С escape=NULL: " + java.util.Arrays.toString(resultNoEscape));
    }
}