package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Solicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.TipoSolicitacao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SolicitacaoResponseDTO(
        UUID id,
        String titulo,
        String descricao,
        StatusSolicitacao status,
        PrioridadeSolicitacao prioridade,
        TipoSolicitacao tipo,
        String numeroPatrimonio,
        String localizacaoProblema,
        UUID usuarioSolicitanteId,
        String usuarioSolicitanteNome,
        UUID tecnicoResponsavelId,
        String tecnicoResponsavelNome,
        LocalDateTime dataAbertura,
        LocalDateTime dataAtualizacao,
        LocalDateTime dataFinalizacao,
        List<AnexoResponseDTO> anexos
) {
    public static SolicitacaoResponseDTO fromEntity(Solicitacao s) {
        return fromEntity(s, List.of());
    }

    public static SolicitacaoResponseDTO fromEntity(Solicitacao s, List<AnexoResponseDTO> anexos) {
        return new SolicitacaoResponseDTO(
                s.getId(),
                s.getTitulo(),
                s.getDescricao(),
                s.getStatus(),
                s.getPrioridade(),
                s.getTipo(),
                s.getNumeroPatrimonio(),
                s.getLocalizacaoProblema(),
                s.getUsuarioSolicitante() != null ? s.getUsuarioSolicitante().getId() : null,
                s.getUsuarioSolicitante() != null ? s.getUsuarioSolicitante().getName() : null,
                s.getTecnicoResponsavel() != null ? s.getTecnicoResponsavel().getId() : null,
                s.getTecnicoResponsavel() != null ? s.getTecnicoResponsavel().getName() : null,
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getDataFinalizacao(),
                anexos
        );
    }
}