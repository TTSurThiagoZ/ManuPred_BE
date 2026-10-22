package com.fiec.br.back_end.kipper.features.solicitacao.repositories;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.TipoChamado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TipoChamadoRepository extends JpaRepository<TipoChamado, UUID> {
    boolean existsByNome(String nome);
}