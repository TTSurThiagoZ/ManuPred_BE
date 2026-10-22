package com.fiec.br.back_end.kipper.features.auth.models.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleTokenRequestDTO(
        @NotBlank(message = "O idToken do Google é obrigatório")
        String idToken
) {}