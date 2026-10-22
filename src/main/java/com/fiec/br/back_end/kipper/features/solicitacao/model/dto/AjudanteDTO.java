package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import com.fiec.br.back_end.kipper.features.user.model.entities.Users;

import java.util.UUID;

public record AjudanteDTO(UUID id, String nome) {
    public static AjudanteDTO fromEntity(Users u) {
        return new AjudanteDTO(u.getId(), u.getName());
    }
}