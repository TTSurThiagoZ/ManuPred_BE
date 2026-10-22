package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSolicitacaoRequestDTO(
        @NotNull(message = "O tipo do chamado é obrigatório")
        UUID tipoId,

        UUID localId,

        @NotBlank(message = "A localização do problema é obrigatória")
        String localizacaoProblema,

        @NotBlank(message = "A descrição é obrigatória")
        String descricao,

        String numeroPatrimonio
) {}