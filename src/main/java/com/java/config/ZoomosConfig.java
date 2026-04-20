package com.java.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "zoomos")
@Data
public class ZoomosConfig {

    private String baseUrl = "https://export.zoomos.by";
    private String username;
    private String password;
    private int timeoutSeconds = 30;
    private int retryAttempts = 3;
    private int retryDelaySeconds = 3;

    public String getBaseUrlNormalized() {
        if (baseUrl == null) return "";
        String s = baseUrl.stripTrailing();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
