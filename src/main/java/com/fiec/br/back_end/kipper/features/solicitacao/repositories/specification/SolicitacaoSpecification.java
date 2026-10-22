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
            List<Predicate> p = new ArrayList<>();
            if (filtro.id() != null) p.add(cb.equal(root.get("id"), filtro.id()));
            if (filtro.termo() != null && !filtro.termo().isBlank()) {
                String t = "%" + filtro.termo().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("titulo")), t), cb.like(cb.lower(root.get("descricao")), t)));
            }
            if (filtro.status() != null) p.add(cb.equal(root.get("status"), filtro.status()));
            if (filtro.prioridade() != null) p.add(cb.equal(root.get("prioridade"), filtro.prioridade()));
            if (filtro.numeroPatrimonio() != null && !filtro.numeroPatrimonio().isBlank())
                p.add(cb.like(cb.lower(root.get("numeroPatrimonio")), "%" + filtro.numeroPatrimonio().toLowerCase() + "%"));
            if (filtro.localizacaoProblema() != null && !filtro.localizacaoProblema().isBlank())
                p.add(cb.like(cb.lower(root.get("localizacaoProblema")), "%" + filtro.localizacaoProblema().toLowerCase() + "%"));
            if (filtro.usuarioSolicitanteId() != null)
                p.add(cb.equal(root.get("usuarioSolicitante").get("id"), filtro.usuarioSolicitanteId()));
            if (filtro.tecnicoResponsavelId() != null)
                p.add(cb.equal(root.get("tecnicoResponsavel").get("id"), filtro.tecnicoResponsavelId()));
            if (filtro.dataAberturaInicio() != null)
                p.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), filtro.dataAberturaInicio()));
            if (filtro.dataAberturaFim() != null)
                p.add(cb.lessThanOrEqualTo(root.<LocalDateTime>get("createdAt"), filtro.dataAberturaFim()));
            if (filtro.dataFinalizacaoInicio() != null)
                p.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("dataFinalizacao"), filtro.dataFinalizacaoInicio()));
            if (filtro.dataFinalizacaoFim() != null)
                p.add(cb.lessThanOrEqualTo(root.<LocalDateTime>get("dataFinalizacao"), filtro.dataFinalizacaoFim()));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}