package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Anexo;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnexoResponseDTO(
        UUID id,
        String nomeArquivo,
        String url,
        LocalDateTime createdAt
) {
    public static AnexoResponseDTO fromEntity(Anexo anexo) {
        return new AnexoResponseDTO(
                anexo.getId(),
                anexo.getNomeArquivo(),
                "/uploads/" + anexo.getCaminhoArmazenado(),
                anexo.getCreatedAt()
        );
    }
}