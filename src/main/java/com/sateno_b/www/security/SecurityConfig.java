package com.sateno_b.www.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
//                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated()
//                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, resp, e) -> {
                            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            resp.setContentType("application/json;charset=UTF-8");
                            resp.setCharacterEncoding("UTF-8");
                            resp.getWriter().write("{\"error\":\"Unauthorized\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/erp/auth/login",
                                "/error",
                                "/media/**",
                                "/erp/wp_order/create",
                                "/ws/**",
                                "/erp/checkout/**"
                        ).permitAll()
                        .anyRequest().authenticated());

        return http.build();
    }
}
