package com.fiec.br.back_end.kipper.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Campos de auditoria comuns a todas as entidades do sistema.
 * Quem estende esta classe ganha createdAt/updatedAt/createdBy/updatedBy
 * preenchidos automaticamente pelo Spring Data JPA a cada persist/update
 * (ver JpaAuditingConfig, que registra o AuditorAware responsável por
 * informar o usuário autenticado no momento da operação).
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditoria {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 150)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 150)
    private String updatedBy;
}