package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

public record RelatorioDTO(
        long totalChamados,
        double tempoMedioResolucaoDias,
        String tipoMaisFrequente,
        long tipoMaisFrequenteQtd,
        long rejeitados,
        double percentualRejeitados
) {}