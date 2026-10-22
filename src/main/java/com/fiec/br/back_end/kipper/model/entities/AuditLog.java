package com.fiec.br.back_end.kipper.model.entities;

import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tb_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(of = "id", callSuper = false)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String acao;

    @Column(length = 500)
    private String detalhe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id")
    private Users autor;

    @Column(name = "criado_em", nullable = false)
    private java.time.LocalDateTime criadoEm;
}