package com.java.dto.zoomos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public record SparklinePoint(
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate date,
    Integer inStock
) {}
