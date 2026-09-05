package com.fiec.br.back_end.kipper.features.auth.service.impl;

import com.fiec.br.back_end.kipper.config.JwtUtil;
import com.fiec.br.back_end.kipper.features.auth.models.dto.LoginRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.RegisterRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.TokenResponseDTO;
import com.fiec.br.back_end.kipper.features.auth.service.AuthService;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
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
        Users user = new Users();
        user.setName(request.nome());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFcmToken(request.fcmToken());

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
