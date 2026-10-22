package com.fiec.br.back_end.kipper.features.user.service.impl;

import com.fiec.br.back_end.kipper.exception.RecursoNaoEncontradoException;
import com.fiec.br.back_end.kipper.config.JwtUtil;
import com.fiec.br.back_end.kipper.features.auth.models.dto.TokenResponseDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.CreateUserRequestDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.UserResponseDTO;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.features.user.service.UserService;
import com.fiec.br.back_end.kipper.model.service.AuditService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado com E-mail: " + username));
    }

    @Override
    @Transactional
    public UserResponseDTO createUser(CreateUserRequestDTO dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado no sistema.");
        }
        Users user = Users.builder()
                .name(dto.name())
                .email(dto.email())
                .password(passwordEncoder.encode(dto.password()))
                .firebaseUid(dto.firebaseUid())
                .build();
        return UserResponseDTO.fromEntity(userRepository.save(user));
    }

    @Override @Transactional(readOnly = true)
    public UserResponseDTO findById(UUID id) {
        return UserResponseDTO.fromEntity(userRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado com ID: " + id)));
    }

    @Override @Transactional(readOnly = true)
    public UserResponseDTO findByEmail(String email) {
        return UserResponseDTO.fromEntity(userRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado com E-mail: " + email)));
    }

    @Override @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream().map(UserResponseDTO::fromEntity).toList();
    }

    @Override @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new RecursoNaoEncontradoException("Usuário não encontrado para deleção.");
        }
        userRepository.deleteById(id);
    }

    @Override @Transactional
    public TokenResponseDTO verifyAndAuthenticateFirebaseToken(String firebaseToken) {
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            String name = decodedToken.getName();

            Users user = userRepository.findByFirebaseUid(uid)
                    .orElseGet(() -> userRepository.findByEmail(email)
                            .map(existingUser -> {
                                existingUser.setFirebaseUid(uid);
                                return userRepository.save(existingUser);
                            })
                            .orElseGet(() -> userRepository.save(Users.builder()
                                    .name(name != null ? name : "Usuário Firebase")
                                    .email(email)
                                    .password("")
                                    .firebaseUid(uid)
                                    .build())));
            return new TokenResponseDTO(jwtUtil.generateToken(user));
        } catch (Exception e) {
            throw new BadCredentialsException("Falha na verificação do token Firebase: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public UserResponseDTO atualizarRole(UUID id, UserRole role) {
        Users user = userRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado com ID: " + id));
        UserRole antiga = user.getRole();
        user.setRole(role);
        Users salvo = userRepository.save(user);

        Users autor = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditService.registrar("ALTERAR_ROLE",
                "Alterou role de " + salvo.getName() + ": " + antiga + " → " + role,
                autor);

        return UserResponseDTO.fromEntity(salvo);
    }

    @Override
    @Transactional
    public void atualizarFcmToken(String email, String fcmToken) {
        // Remove o token de qualquer outro usuário (para o celular não receber notificação de outra conta)
        List<Users> usuariosComToken = userRepository.findAllByFcmToken(fcmToken);
        for (Users u : usuariosComToken) {
            if (!u.getEmail().equals(email)) {
                u.setFcmToken(null);
                userRepository.save(u);
            }
        }

        // Atualiza o token no usuário logado
        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }
}