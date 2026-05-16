package com.example.genprofileimage;

import com.example.genprofileimage.config.ComfyUiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ComfyUiProperties.class)
public class GenProfileImageApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenProfileImageApplication.class, args);
    }
}
