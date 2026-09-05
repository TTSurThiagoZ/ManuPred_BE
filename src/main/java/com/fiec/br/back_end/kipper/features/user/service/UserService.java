package com.fiec.br.back_end.kipper.features.user.service;

import com.fiec.br.back_end.kipper.features.user.model.dto.CreateUserRequestDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.UserResponseDTO;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.UUID;

public interface UserService extends UserDetailsService {
    UserResponseDTO createUser(CreateUserRequestDTO dto);
    UserResponseDTO findById(UUID id);
    UserResponseDTO findByEmail(String email);
    List<UserResponseDTO> findAll();
    void deleteUser(UUID id);
    UserResponseDTO verifyAndAuthenticateFirebaseToken(String firebaseToken);
}