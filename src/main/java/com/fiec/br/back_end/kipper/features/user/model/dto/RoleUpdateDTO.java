package com.fiec.br.back_end.kipper.features.user.model.dto;

import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateDTO(@NotNull UserRole role) {}