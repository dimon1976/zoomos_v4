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
}
