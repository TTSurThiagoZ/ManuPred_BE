package com.fiec.br.back_end.kipper.features.solicitacao.service;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Movimentacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Solicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.MovimentacaoRepository;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.SolicitacaoRepository;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.features.user.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlaScheduler {

    private static final List<StatusSolicitacao> STATUS_ATIVOS = List.of(
            StatusSolicitacao.ABERTO,
            StatusSolicitacao.EM_ANDAMENTO,
            StatusSolicitacao.AGUARDANDO_ASSISTENCIA_EXTERNA
    );

    private final SolicitacaoRepository solicitacaoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final UserRepository userRepository;
    private final NotificacaoService notificacaoService;

    /** Roda a cada 15 minutos. */
    @Scheduled(fixedRate = 900_000)
    public void verificarSla() {
        log.debug("Verificando SLA...");

        // Busca só os chamados ativos cujo SLA ainda não foi tratado. Antes a busca era por
        // "urgente = false": se um supervisor tirasse a urgência, em 15 minutos o scheduler
        // marcava de novo, e a urgência nunca podia ser removida.
        List<Solicitacao> ativas = solicitacaoRepository.findAtivasParaSla(STATUS_ATIVOS);

        LocalDateTime agora = LocalDateTime.now();
        int marcados = 0;

        for (Solicitacao s : ativas) {
            if (s.getTipo() == null || s.getTipo().getSlaHoras() == null || s.getTipo().getSlaHoras() <= 0) continue;
            if (s.getCreatedAt() == null) continue;

            long horasDecorridas = Duration.between(s.getCreatedAt(), agora).toHours();
            if (horasDecorridas < s.getTipo().getSlaHoras()) continue;

            s.setSlaEstourado(true);

            // Se alguém já tinha marcado como urgente na mão, só registra que o SLA estourou
            if (Boolean.TRUE.equals(s.getUrgente())) {
                solicitacaoRepository.save(s);
                continue;
            }

            s.setUrgente(true);
            s.setPrioridade(PrioridadeSolicitacao.URGENTE);
            solicitacaoRepository.save(s);

            movimentacaoRepository.save(Movimentacao.builder()
                    .titulo("SLA estourado")
                    .descricao("Chamado excedeu " + s.getTipo().getSlaHoras()
                            + "h do SLA. Marcado como urgente automaticamente.")
                    .solicitacao(s)
                    .build());

            // Avisa o técnico responsável (se já tiver um) ou os supervisores (se estiver sem técnico)
            if (s.getTecnicoResponsavel() != null) {
                notificacaoService.enviar(s.getTecnicoResponsavel(),
                        "Chamado URGENTE: SLA estourado", s.getTitulo(), s.getId());
            } else {
                List<Users> supervisores = userRepository.findByRoleIn(List.of(UserRole.SUPERVISOR_ROLE, UserRole.ADMIN_ROLE, UserRole.SUPER_ADMIN_ROLE));
                for (Users sup : supervisores) {
                    notificacaoService.enviar(sup,
                            "Chamado sem técnico URGENTE: SLA estourado", s.getTitulo(), s.getId());
                }
            }

            marcados++;
            log.warn("SLA estourado: chamado {} ({})", s.getId(), s.getTitulo());
        }

        if (marcados > 0) {
            log.info("Verificação de SLA concluída. {} chamados marcados como urgentes.", marcados);
        }
    }
}
