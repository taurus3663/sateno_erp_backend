package com.sateno_b.www.security;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.concurrent.Executor;

@Configuration
@RequiredArgsConstructor
public class Beans {

    // Взима списъка от пропъртитата. Ако няма нищо, по подразбиране е localhost
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public ModelMapper modelMapper() {

        ModelMapper mapper = new ModelMapper();

        mapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setAmbiguityIgnored(true)
                .setSkipNullEnabled(true)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE)
                .setPropertyCondition(ctx -> ctx.getSource() != null);

        return mapper;
    }
//    @Bean
//    public ModelMapper modelMapper() {
//        ModelMapper modelMapper = new ModelMapper();
//
//        modelMapper.getConfiguration()
//                .setAmbiguityIgnored(true)
//                .setMatchingStrategy(MatchingStrategies.STRICT)
//                .setSkipNullEnabled(true);
//
//        return modelMapper;
//    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

//        config.setAllowedOrigins(List.of(
//                "http://localhost:4200",   // Angular dev
//                "http://192.168.31.232",  // ако браузър е директно на IP
//                "http://192.168.31.232:8080",  // ако браузър е директно на IP
//                "http://192.168.31.232:4200"
//        ));
        config.setAllowedOrigins(allowedOrigins);
        System.out.println(allowedOrigins);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public RestClient restClient() {

//        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
//        factory.setConnectTimeout(10000); // 10 секунди за свързване
//        factory.setReadTimeout(30000);    // 30 секунди за четене на данни

        return RestClient.builder()
//                .requestFactory(factory)
                .baseUrl("") // Може да остане празно, понеже взимаме URL от базата
                .build();
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Колко нишки да работят постоянно (напр. 5 продукта едновременно)
        executor.setCorePoolSize(10);
        // Максимален брой нишки (напр. 10)
        executor.setMaxPoolSize(20);
        // Колко задачи да чакат на опашка, преди да почнат да се отхвърлят
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("WooSync-");
        executor.initialize();
        return executor;
    }
}
