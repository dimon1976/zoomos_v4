package com.java.model.utils;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class RedirectUrlData {
    private String id;
    private String url;
    private String model;
}