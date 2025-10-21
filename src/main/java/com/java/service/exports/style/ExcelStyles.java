package com.java.service.exports.style;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.poi.ss.usermodel.CellStyle;

@Data
@AllArgsConstructor
public class ExcelStyles {
    private final CellStyle headerStyle;
    private final CellStyle dateStyle;
    private final CellStyle numberStyle;
    private final CellStyle textStyle;

}
