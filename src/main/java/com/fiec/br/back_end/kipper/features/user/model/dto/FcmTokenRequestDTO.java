package com.fiec.br.back_end.kipper.features.user.model.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenRequestDTO(
        @NotBlank(message = "O fcmToken é obrigatório")
        String fcmToken
) {}