package com.fiec.br.back_end.kipper.features.solicitacao.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Prioridade de um chamado")
public enum PrioridadeSolicitacao {
    @Schema(description = "Baixa prioridade - pode esperar") BAIXA,
    @Schema(description = "Prioridade média - padrão ao criar") MEDIA,
    @Schema(description = "Alta prioridade - deve ser resolvido primeiro") ALTA,
    @Schema(description = "Urgente - marcado manualmente ou por SLA estourado") URGENTE
}