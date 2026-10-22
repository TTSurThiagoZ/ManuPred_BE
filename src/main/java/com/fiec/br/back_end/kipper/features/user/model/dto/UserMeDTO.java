package com.fiec.br.back_end.kipper.features.user.model.dto;

import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import java.util.UUID;

public record UserMeDTO(UUID id, String email, String nome, UserRole role) {}