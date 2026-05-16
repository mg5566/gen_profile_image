package com.example.genprofileimage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.comfyui")
public record ComfyUiProperties(
        String baseUrl,
        boolean mock,
        String workflowPath,
        int pollingTimeoutSeconds,
        int pollingIntervalMillis
) {
}
