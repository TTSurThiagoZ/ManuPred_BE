package com.fiec.br.back_end.kipper.features.solicitacao.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Status possíveis de um chamado")
public enum StatusSolicitacao {
    @Schema(description = "Chamado aberto, aguardando atribuição de técnico") ABERTO,
    @Schema(description = "Técnico já atribuído e trabalhando no chamado") EM_ANDAMENTO,
    @Schema(description = "Aguardando peça ou técnico externo") AGUARDANDO_ASSISTENCIA_EXTERNA,
    @Schema(description = "Técnico finalizou, aguardando validação do solicitante") CONCLUIDO,
    @Schema(description = "Solicitante validou a conclusão do chamado") FINALIZADA,
    @Schema(description = "Chamado rejeitado") REJEITADA,
    @Schema(description = "Chamado cancelado") CANCELADO
}