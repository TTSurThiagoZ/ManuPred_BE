package com.fiec.br.back_end.kipper.features.solicitacao.model.dto;

import java.util.UUID;

public record TipoChamadoDTO(String nome, String icone, Integer slaHoras, String acaoAoEstourar) {}