package com.sateno_b.www.security;

import com.sateno_b.www.model.CustomUserDetails;
import com.sateno_b.www.model.entity.UserEntity;
import com.sateno_b.www.model.repository.UserRepository;
import io.jsonwebtoken.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;

@Component
@AllArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException, java.io.IOException {

        String authToken = request.getHeader("Authorization");

//        System.out.println("authToken: " + authToken);
        if(authToken != null && authToken.startsWith("Bearer ")) {
            String token = authToken.substring(7);

            try {
                String email = jwtService.extractEmail(token);
                UserEntity user = userRepository.findByEmail(email).orElse(null);

                if(user != null && !jwtService.isTokenExpired(token)) {

                    CustomUserDetails customUserDetails = new CustomUserDetails(
                            user.getId(),
                            user.getEmail(),
                            user.getFirstName(),
                            user.getMiddleName(),
                            user.getLastName(),
                            user.getUsername(),
                            List.of(new SimpleGrantedAuthority("ROLE_" + "USER")),
                            user.getPhone(),
                            user.getImg()
                    );
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(customUserDetails, null, List.of(new SimpleGrantedAuthority("ROLE_" + "USER")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                System.out.println("Invalid JWT Token" + e.getMessage());
            }
        }
        filterChain.doFilter(request,response);

    }
}
