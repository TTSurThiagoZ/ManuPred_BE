package com.fiec.br.back_end.kipper.features.solicitacao.repositories;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Anexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnexoRepository extends JpaRepository<Anexo, UUID> {
}
