package com.sateno_b.www.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.springframework.web.servlet.resource.TransformedResource;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

@Configuration
public class Webconfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/erp",
                HandlerTypePredicate.forAnnotation(RestController.class));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String rootPath = System.getProperty("user.home") + "/uploads/sateno_pim/";
        rootPath = rootPath.replace("\\", "/");
        if (!rootPath.endsWith("/")) {
            rootPath += "/";
        }

        registry.addResourceHandler("/media/**")
                .addResourceLocations("file:" + rootPath)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    public Resource resolveResource(
                            HttpServletRequest request,
                            String resourcePath,
                            List<? extends Resource> locations,
                            ResourceResolverChain chain) {

                        Resource original = super.resolveResource(request, resourcePath, locations, chain);

                        if (original == null || !original.exists()) {
                            return original;
                        }

                        String filename = original.getFilename();
                        if (filename != null && (filename.toLowerCase().endsWith(".jpg") ||
                                filename.toLowerCase().endsWith(".jpeg") ||
                                filename.toLowerCase().endsWith(".png"))) {
                            try {
                                BufferedImage originalImage = ImageIO.read(original.getInputStream());
                                if (originalImage != null) {

                                    // 1. Създаваме ново изображение СЪС СЪЩИТЕ РАЗМЕРИ, но без прозрачност (RGB)
                                    BufferedImage rgbImage = new BufferedImage(
                                            originalImage.getWidth(),
                                            originalImage.getHeight(),
                                            BufferedImage.TYPE_INT_RGB);

                                    // 2. Запълваме фона с бяло (спасява PNG-тата от черен фон) и рисуваме оригинала отгоре
                                    Graphics2D g2d = rgbImage.createGraphics();
                                    g2d.setColor(Color.WHITE);
                                    g2d.fillRect(0, 0, originalImage.getWidth(), originalImage.getHeight());
                                    g2d.drawImage(originalImage, 0, 0, null);
                                    g2d.dispose();

                                    // 3. Подготвяме мачкането в JPEG формат
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

                                    if (writers.hasNext()) {
                                        ImageWriter writer = writers.next();
                                        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                                        writer.setOutput(ios);

                                        // 4. Задаваме нивото на компресия
                                        ImageWriteParam param = writer.getDefaultWriteParam();
                                        if (param.canWriteCompressed()) {
                                            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                                            // Стойност между 0.0f (най-малко KB, най-лошо качество) и 1.0f (големи KB, перфектно качество)
                                            // 0.35f е идеалният баланс за уеб без да си личат пиксели
                                            param.setCompressionQuality(0.35f);
                                        }

                                        // Записваме компресираната снимка в паметта
                                        writer.write(null, new IIOImage(rgbImage, null, null), param);

                                        ios.close();
                                        writer.dispose();

                                        return new TransformedResource(original, baos.toByteArray());
                                    }
                                }
                            } catch (IOException e) {
                                return original;
                            }
                        }
                        return original;
                    }
                });
    }
}

//package com.sateno_b.www.security;
//
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.method.HandlerTypePredicate;
//import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
//import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//@Configuration
//public class Webconfig implements WebMvcConfigurer {
//
//    @Override
//    public void configurePathMatch(PathMatchConfigurer configurer) {
//        configurer.addPathPrefix("/erp",
//                HandlerTypePredicate.forAnnotation(RestController.class));
//    }
//
/// /    @Override
/// /    public void addResourceHandlers(ResourceHandlerRegistry registry) {
/// /        // Дефинираме пътя до папката uploads
/// /        String rootPath = System.getProperty("user.home") + "/uploads/sateno_pim/";
/// /
/// /        registry.addResourceHandler("/media/**")
/// /                .addResourceLocations("file:" + rootPath);
/// /    }
//
////    @Override
////    public void addResourceHandlers(ResourceHandlerRegistry registry) {
////        String rootPath = System.getProperty("user.home") + "/uploads/sateno_pim/";
////        // За всеки случай нормализирай и добави наклонена черта накрая
////        rootPath = rootPath.replace("\\", "/");
////        if (!rootPath.endsWith("/")) {
////            rootPath += "/";
////        }
////
////        registry.addResourceHandler("/media/**")
////                .addResourceLocations("file:" + rootPath);
////    }
//
//
//}