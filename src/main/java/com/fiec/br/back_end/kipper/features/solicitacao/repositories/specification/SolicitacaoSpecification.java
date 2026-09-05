package com.fiec.br.back_end.kipper.features.solicitacao.repositories.specification;

import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoSearchFilterDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Solicitacao;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SolicitacaoSpecification {

    private SolicitacaoSpecification() {}

    public static Specification<Solicitacao> comFiltros(SolicitacaoSearchFilterDTO filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filtro.id() != null) {
                predicates.add(cb.equal(root.get("id"), filtro.id()));
            }

            if (filtro.termo() != null && !filtro.termo().isBlank()) {
                String termoBusca = "%" + filtro.termo().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("titulo")), termoBusca),
                        cb.like(cb.lower(root.get("descricao")), termoBusca)
                ));
            }

            if (filtro.status() != null) {
                predicates.add(cb.equal(root.get("status"), filtro.status()));
            }

            if (filtro.prioridade() != null) {
                predicates.add(cb.equal(root.get("prioridade"), filtro.prioridade()));
            }

            if (filtro.numeroPatrimonio() != null && !filtro.numeroPatrimonio().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("numeroPatrimonio")), "%" + filtro.numeroPatrimonio().toLowerCase() + "%"));
            }

            if (filtro.localizacaoProblema() != null && !filtro.localizacaoProblema().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("localizacaoProblema")), "%" + filtro.localizacaoProblema().toLowerCase() + "%"));
            }

            if (filtro.usuarioSolicitanteId() != null) {
                predicates.add(cb.equal(root.get("usuarioSolicitante").get("id"), filtro.usuarioSolicitanteId()));
            }

            if (filtro.tecnicoResponsavelId() != null) {
                predicates.add(cb.equal(root.get("tecnicoResponsavel").get("id"), filtro.tecnicoResponsavelId()));
            }

            if (filtro.dataAberturaInicio() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), filtro.dataAberturaInicio()));
            }

            if (filtro.dataAberturaFim() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDateTime>get("createdAt"), filtro.dataAberturaFim()));
            }

            if (filtro.dataFinalizacaoInicio() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("dataFinalizacao"), filtro.dataFinalizacaoInicio()));
            }

            if (filtro.dataFinalizacaoFim() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDateTime>get("dataFinalizacao"), filtro.dataFinalizacaoFim()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}