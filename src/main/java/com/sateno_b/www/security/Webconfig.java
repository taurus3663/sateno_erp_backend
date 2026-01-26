package com.sateno_b.www.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class Webconfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/erp",
                HandlerTypePredicate.forAnnotation(RestController.class));
    }

//    @Override
//    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        // Дефинираме пътя до папката uploads
//        String rootPath = System.getProperty("user.home") + "/uploads/sateno_pim/";
//
//        registry.addResourceHandler("/media/**")
//                .addResourceLocations("file:" + rootPath);
//    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String rootPath = System.getProperty("user.home") + "/uploads/sateno_pim/";
        // За всеки случай нормализирай и добави наклонена черта накрая
        rootPath = rootPath.replace("\\", "/");
        if (!rootPath.endsWith("/")) {
            rootPath += "/";
        }

        registry.addResourceHandler("/media/**")
                .addResourceLocations("file:" + rootPath);
    }
}
