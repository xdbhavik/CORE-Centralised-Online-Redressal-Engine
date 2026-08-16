package com.SIH.mark1.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Serves uploaded complaint media (photos/videos/PDFs) over HTTP.
 * URL: /media/{complaintId}/{filename} → file: {app.upload.dir}/{complaintId}/{filename}
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadDir).toAbsolutePath().toString().replace('\\', '/') + "/";
        registry.addResourceHandler("/media/**").addResourceLocations(location);
    }
}
