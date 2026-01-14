package com.sateno_b.www.controller;

import com.sateno_b.www.model.CustomUserDetails;
import com.sateno_b.www.model.dto.AuthRequest;
import com.sateno_b.www.model.dto.AuthResponse;
import com.sateno_b.www.model.dto.UserInfo;
import com.sateno_b.www.model.entity.UserEntity;
import com.sateno_b.www.model.repository.UserRepository;
import com.sateno_b.www.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest authRequest) {

        UserEntity user = userRepository.findByUsername(authRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if(!passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Incorrect password or username");
        }

        String token = jwtService.generateToken(user.getEmail());

        return new AuthResponse(token);
    }

    @PostMapping("/me")
    public UserInfo me(Authentication auth) {
        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
        Optional<UserEntity> byEmail = userRepository.findByEmail(user.getEmail());
        if(byEmail.isPresent()) {
            UserInfo userInfo = modelMapper.map(byEmail.get(), UserInfo.class);
            return userInfo;
        }
        return null;
    }
}
