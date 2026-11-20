package com.fiec.br.back_end.kipper.features.user.model.dto;

import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserStatus;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserType;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDTO(
        UUID id,
        String name,
        String email,
        String firebaseUid,
        UserRole role,
        UserStatus status,
        String rm,
        String setor,
        UserType userType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserResponseDTO fromEntity(Users user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getFirebaseUid(),
                user.getRole(),
                user.getStatus(),
                user.getRm(),
                user.getSetor(),
                user.getUserType(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
