package com.fiec.br.back_end.kipper.features.user.service.impl;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.fiec.br.back_end.kipper.features.user.model.dto.CreateUserRequestDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.UserResponseDTO;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.features.user.service.UserService;
import lombok.RequiredArgsConstructor;
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

    // --- Implementação do UserDetailsService para o Spring Security ---
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
                .password(passwordEncoder.encode(dto.password())) // Senha criptografada com BCrypt
                .firebaseUid(dto.firebaseUid())
                .build();

        Users savedUser = userRepository.save(user);
        return UserResponseDTO.fromEntity(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO findById(UUID id) {
        Users user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado com ID: " + id));
        return UserResponseDTO.fromEntity(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO findByEmail(String email) {
        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado com E-mail: " + email));
        return UserResponseDTO.fromEntity(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream()
                .map(UserResponseDTO::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("Usuário não encontrado para deleção.");
        }
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public UserResponseDTO verifyAndAuthenticateFirebaseToken(String firebaseToken) {
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
                                    .password("") // Autenticado via provedor OAuth/Firebase
                                    .firebaseUid(uid)
                                    .build())));

            return UserResponseDTO.fromEntity(user);
        } catch (Exception e) {
            throw new RuntimeException("Falha na verificação do token Firebase: " + e.getMessage(), e);
        }
    }
}
