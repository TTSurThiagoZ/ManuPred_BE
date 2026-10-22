package com.fiec.br.back_end.kipper.features.solicitacao.repositories;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Solicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, UUID>, JpaSpecificationExecutor<Solicitacao> {

    long countByStatus(StatusSolicitacao status);

    // Query nova para o SlaScheduler (busca só os ativos e não urgentes)
    @Query("SELECT s FROM Solicitacao s WHERE s.status IN :statuses AND s.urgente = false")
    List<Solicitacao> findAtivasParaSla(@Param("statuses") List<StatusSolicitacao> statuses);

    @Query("SELECT s FROM Solicitacao s WHERE s.createdAt BETWEEN :inicio AND :fim")
    List<Solicitacao> findByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}