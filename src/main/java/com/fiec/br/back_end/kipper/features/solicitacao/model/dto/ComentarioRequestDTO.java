package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ComentarioRequestDTO(
        @NotBlank(message = "O texto é obrigatório")
        String texto
) {}