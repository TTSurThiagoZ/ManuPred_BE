package com.fiec.br.back_end.kipper.features.auth.service;

import com.fiec.br.back_end.kipper.features.auth.models.dto.LoginRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.RegisterRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.TokenResponseDTO;

public interface AuthService {
    void register(RegisterRequestDTO request);
    TokenResponseDTO login(LoginRequestDTO request);
}
