package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.TipoSolicitacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSolicitacaoRequestDTO(
        @NotNull(message = "O ID do solicitante é obrigatório")
        UUID usuarioSolicitanteId,

        @NotNull(message = "O tipo do chamado é obrigatório")
        TipoSolicitacao tipo,

        @NotBlank(message = "A localização do problema é obrigatória")
        String localizacaoProblema,

        @NotBlank(message = "A descrição é obrigatória")
        String descricao,

        String numeroPatrimonio
) {}
