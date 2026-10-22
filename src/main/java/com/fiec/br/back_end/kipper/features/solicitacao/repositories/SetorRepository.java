package com.fiec.br.back_end.kipper.features.solicitacao.repositories;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Setor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SetorRepository extends JpaRepository<Setor, UUID> {
    boolean existsByNome(String nome);
}