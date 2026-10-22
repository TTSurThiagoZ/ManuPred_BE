package com.fiec.br.back_end.kipper.features.solicitacao.service;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Movimentacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Solicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.MovimentacaoRepository;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.SolicitacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlaScheduler {

    private final SolicitacaoRepository solicitacaoRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    // Roda a cada 15 minutos
    @Scheduled(fixedRate = 900_000)
    @Transactional
    public void verificarSla() {
        log.info("Verificando SLA...");

        // Busca no banco apenas os que precisam ser verificados
        List<StatusSolicitacao> statusesAtivos = List.of(
                StatusSolicitacao.ABERTO,
                StatusSolicitacao.EM_ANDAMENTO,
                StatusSolicitacao.AGUARDANDO_ASSISTENCIA_EXTERNA
        );

        List<Solicitacao> ativas = solicitacaoRepository.findAtivasParaSla(statusesAtivos);

        LocalDateTime agora = LocalDateTime.now();
        int marcados = 0;

        for (Solicitacao s : ativas) {
            if (s.getTipo() == null || s.getTipo().getSlaHoras() == null) continue;
            if (s.getCreatedAt() == null) continue;

            long horasDecorridas = Duration.between(s.getCreatedAt(), agora).toHours();
            if (horasDecorridas >= s.getTipo().getSlaHoras()) {
                s.setUrgente(true);
                s.setPrioridade(PrioridadeSolicitacao.URGENTE);
                solicitacaoRepository.save(s);

                movimentacaoRepository.save(Movimentacao.builder()
                        .titulo("SLA estourado")
                        .descricao("Chamado excedeu " + s.getTipo().getSlaHoras()
                                + "h do SLA. Marcado como urgente automaticamente.")
                        .solicitacao(s)
                        .build());

                marcados++;
                log.warn("SLA estourado: chamado {} ({})", s.getId(), s.getTitulo());
            }
        }

        log.info("Verificação de SLA concluída. {} chamados marcados como urgentes.", marcados);
    }
}