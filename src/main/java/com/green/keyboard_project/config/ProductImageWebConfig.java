package com.green.keyboard_project.config;

import com.green.keyboard_project.service.ProductImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class ProductImageWebConfig implements WebMvcConfigurer {
    private final ProductImageStorageService imageStorageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = imageStorageService.getUploadDirectory().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/products/**").addResourceLocations(location);
    }
}
