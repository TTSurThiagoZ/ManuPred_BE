package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Movimentacao;

import java.time.LocalDateTime;
import java.util.UUID;

public record MovimentacaoResponseDTO(
        UUID id,
        String titulo,
        String descricao,
        UUID autorId,
        String autorNome,
        LocalDateTime criadoEm
) {
    public static MovimentacaoResponseDTO fromEntity(Movimentacao m) {
        return new MovimentacaoResponseDTO(
                m.getId(),
                m.getTitulo(),
                m.getDescricao(),
                m.getAutor() != null ? m.getAutor().getId() : null,
                m.getAutor() != null ? m.getAutor().getName() : null,
                m.getCreatedAt()
        );
    }
}