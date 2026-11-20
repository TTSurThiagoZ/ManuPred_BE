package com.fiec.br.back_end.kipper.features.user.model.entities;

public enum UserRole {
    /** Tudo do ADMIN + importar usuários por CSV e gerenciar outros SUPER_ADMIN. */
    SUPER_ADMIN_ROLE,
    ADMIN_ROLE,
    SUPERVISOR_ROLE,
    FUNCIONARIO_ROLE,
    USER_ROLE;

    /** ADMIN ou SUPER_ADMIN: o SUPER_ADMIN pode tudo o que o ADMIN pode. */
    public boolean ehAdmin() {
        return this == SUPER_ADMIN_ROLE || this == ADMIN_ROLE;
    }

    /** ADMIN, SUPER_ADMIN ou SUPERVISOR. */
    public boolean ehSupervisor() {
        return ehAdmin() || this == SUPERVISOR_ROLE;
    }

    /** Qualquer membro da equipe: ADMIN, SUPER_ADMIN, SUPERVISOR ou FUNCIONARIO. */
    public boolean ehFuncionario() {
        return ehSupervisor() || this == FUNCIONARIO_ROLE;
    }
}
