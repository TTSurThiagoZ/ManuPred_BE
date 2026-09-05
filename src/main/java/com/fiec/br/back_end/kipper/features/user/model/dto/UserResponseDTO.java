package com.fiec.br.back_end.kipper.features.user.model.dto;

import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDTO(
        UUID id,
        String name,
        String email,
        String firebaseUid,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserResponseDTO fromEntity(Users user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getFirebaseUid(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}