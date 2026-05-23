package com.example.genprofileimage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.comfyui")
public record ComfyUiProperties(
        String baseUrl,
        boolean mock,
        boolean fallbackToMock,
        String workflowPath,
        String checkpointName,
        String positivePrompt,
        String negativePrompt,
        long seed,
        int steps,
        double cfg,
        double denoise,
        String samplerName,
        String scheduler,
        String outputPrefix,
        int pollingTimeoutSeconds,
        int pollingIntervalMillis
) {
}
