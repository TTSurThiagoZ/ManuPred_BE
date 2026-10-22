package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Comentario;

import java.time.LocalDateTime;
import java.util.UUID;

public record ComentarioResponseDTO(
        UUID id,
        String texto,
        UUID autorId,
        String autorNome,
        LocalDateTime criadoEm
) {
    public static ComentarioResponseDTO fromEntity(Comentario c) {
        return new ComentarioResponseDTO(
                c.getId(),
                c.getTexto(),
                c.getAutor() != null ? c.getAutor().getId() : null,
                c.getAutor() != null ? c.getAutor().getName() : null,
                c.getCreatedAt()
        );
    }
}