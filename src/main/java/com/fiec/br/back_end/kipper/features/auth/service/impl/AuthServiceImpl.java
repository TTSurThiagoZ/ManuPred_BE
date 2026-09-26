package com.fiec.br.back_end.kipper.features.auth.service.impl;

import com.fiec.br.back_end.kipper.config.JwtUtil;
import com.fiec.br.back_end.kipper.features.auth.models.dto.LoginRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.RegisterRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.TokenResponseDTO;
import com.fiec.br.back_end.kipper.features.auth.service.AuthService;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public void register(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado no sistema.");
        }

        Users user = Users.builder()
                .name(request.nome())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fcmToken(request.fcmToken())
                .build();

        userRepository.save(user);
    }

    @Override
    public TokenResponseDTO login(LoginRequestDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        Users user = userRepository.findByEmail(request.email()).orElseThrow();
        String jwtToken = jwtUtil.generateToken(user);

        return new TokenResponseDTO(jwtToken);
    }
}