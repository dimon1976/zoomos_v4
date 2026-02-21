package com.java.dto.handbook;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BhStatsDto {
    private long products;
    private long names;
    private long urls;
    private long domains;
}
