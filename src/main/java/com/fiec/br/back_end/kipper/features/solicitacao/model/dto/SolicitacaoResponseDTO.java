package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Solicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados de um chamado retornado pela API")
public record SolicitacaoResponseDTO(
        @Schema(description = "ID único do chamado", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Título gerado automaticamente", example = "Hidráulico - Banheiro da Sala 12")
        String titulo,

        @Schema(description = "Descrição do problema", example = "Está vazando água embaixo da pia")
        String descricao,

        @Schema(description = "Status atual", example = "ABERTO")
        StatusSolicitacao status,

        @Schema(description = "Prioridade", example = "MEDIA")
        PrioridadeSolicitacao prioridade,

        @Schema(description = "ID do tipo de chamado")
        UUID tipoId,

        @Schema(description = "Nome do tipo", example = "Hidráulico")
        String tipoNome,

        @Schema(description = "Ícone do tipo", example = "🔧")
        String tipoIcone,

        @Schema(description = "ID do local")
        UUID localId,

        @Schema(description = "Nome do local", example = "Sala 12")
        String localNome,

        @Schema(description = "ID do setor")
        UUID setorId,

        @Schema(description = "Nome do setor", example = "Bloco A")
        String setorNome,

        @Schema(description = "Número de patrimônio", example = "4821")
        String numeroPatrimonio,

        @Schema(description = "Localização exata", example = "Banheiro da Sala 12")
        String localizacaoProblema,

        @Schema(description = "Se está marcado como urgente", example = "false")
        Boolean urgente,

        @Schema(description = "ID do solicitante")
        UUID usuarioSolicitanteId,

        @Schema(description = "Nome do solicitante", example = "Felipe Silva")
        String usuarioSolicitanteNome,

        @Schema(description = "ID do técnico atribuído")
        UUID tecnicoResponsavelId,

        @Schema(description = "Nome do técnico")
        String tecnicoResponsavelNome,

        @Schema(description = "Lista de ajudantes")
        List<AjudanteDTO> ajudantes,

        @Schema(description = "Data de abertura")
        LocalDateTime dataAbertura,

        @Schema(description = "Última atualização")
        LocalDateTime dataAtualizacao,

        @Schema(description = "Data de conclusão")
        LocalDateTime dataConclusao,

        @Schema(description = "Data de finalização")
        LocalDateTime dataFinalizacao,

        @Schema(description = "Motivo de rejeição (se houver)")
        String motivoRejeicao,

        @Schema(description = "Anexos do chamado")
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
                s.getTipo() != null ? s.getTipo().getId() : null,
                s.getTipo() != null ? s.getTipo().getNome() : null,
                s.getTipo() != null ? s.getTipo().getIcone() : null,
                s.getLocal() != null ? s.getLocal().getId() : null,
                s.getLocal() != null ? s.getLocal().getNome() : null,
                s.getLocal() != null && s.getLocal().getSetor() != null ? s.getLocal().getSetor().getId() : null,
                s.getLocal() != null && s.getLocal().getSetor() != null ? s.getLocal().getSetor().getNome() : null,
                s.getNumeroPatrimonio(),
                s.getLocalizacaoProblema(),
                s.getUrgente(),
                s.getUsuarioSolicitante() != null ? s.getUsuarioSolicitante().getId() : null,
                s.getUsuarioSolicitante() != null ? s.getUsuarioSolicitante().getName() : null,
                s.getTecnicoResponsavel() != null ? s.getTecnicoResponsavel().getId() : null,
                s.getTecnicoResponsavel() != null ? s.getTecnicoResponsavel().getName() : null,
                s.getAjudantes() != null ? s.getAjudantes().stream().map(AjudanteDTO::fromEntity).toList() : List.of(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getDataConclusao(),
                s.getDataFinalizacao(),
                s.getMotivoRejeicao(),
                anexos
        );
    }
}