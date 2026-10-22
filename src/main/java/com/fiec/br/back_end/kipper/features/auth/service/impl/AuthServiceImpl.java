package com.fiec.br.back_end.kipper.features.auth.service.impl;

import com.fiec.br.back_end.kipper.config.JwtUtil;
import com.fiec.br.back_end.kipper.features.auth.models.dto.GoogleTokenRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.LoginRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.RegisterRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.TokenResponseDTO;
import com.fiec.br.back_end.kipper.features.auth.service.AuthService;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           AuthenticationManager authenticationManager,
                           GoogleIdTokenVerifier googleIdTokenVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
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

    @Override
    @Transactional
    public TokenResponseDTO loginComGoogle(GoogleTokenRequestDTO request) {
        GoogleIdToken idToken;
        try {
            idToken = googleIdTokenVerifier.verify(request.idToken());
        } catch (Exception e) {
            log.error("Erro ao verificar idToken do Google: {}", e.getMessage());
            throw new BadCredentialsException("Token do Google inválido.");
        }

        if (idToken == null) {
            throw new BadCredentialsException("Token do Google inválido.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String nome = (String) payload.get("name");
        String sub = payload.getSubject(); // id único do Google (equivalente ao firebaseUid)

        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("Token do Google não contém e-mail.");
        }

        // Procura usuário por email OU por firebaseUid (que reusamos para o Google sub)
        Optional<Users> userOpt = userRepository.findByFirebaseUid(sub)
                .or(() -> userRepository.findByEmail(email));

        Users user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            // Garante que o firebaseUid está preenchido com o sub do Google
            if (user.getFirebaseUid() == null || !user.getFirebaseUid().equals(sub)) {
                user.setFirebaseUid(sub);
                userRepository.save(user);
            }
        } else {
            // Auto-cadastro
            user = Users.builder()
                    .name(nome != null ? nome : "Usuário Google")
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString())) // senha aleatória
                    .firebaseUid(sub)
                    .build();
            userRepository.save(user);
            log.info("Auto-cadastro via Google criado: {}", email);
        }

        String jwtToken = jwtUtil.generateToken(user);
        return new TokenResponseDTO(jwtToken);
    }
}