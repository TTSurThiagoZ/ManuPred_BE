package com.fiec.br.back_end.kipper.features.user.model.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fiec.br.back_end.kipper.model.entities.Auditoria;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;

// Esta entidade acaba indo para o JSON dentro de outras (Setor.responsavel em /admin/setores,
// AuditLog.autor em /admin/auditoria). Sem isto, o hash da senha e o token FCM eram devolvidos.
// Afeta só o JSON das respostas; o que é gravado no Mongo continua igual.
@JsonIgnoreProperties({
        "password", "fcmToken", "firebaseUid", "authorities", "username",
        "accountNonExpired", "accountNonLocked", "credentialsNonExpired", "enabled"
})
@Document(collection = "tb_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(of = "id", callSuper = false)
public class Users extends Auditoria implements UserDetails {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    private String name;

    // Índices: o login e a validação do token buscam o usuário por e-mail em toda requisição
    @Indexed
    private String email;

    private String password;

    private  UserStatus status;

    @Indexed
    private String firebaseUid;

    @Builder.Default
    private UserRole role = UserRole.USER_ROLE;

    private String fcmToken;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (this.role == UserRole.ADMIN_ROLE) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
            authorities.add(new SimpleGrantedAuthority("ROLE_FUNCIONARIO"));
        } else if (this.role == UserRole.SUPERVISOR_ROLE) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
            authorities.add(new SimpleGrantedAuthority("ROLE_FUNCIONARIO"));
        } else if (this.role == UserRole.FUNCIONARIO_ROLE) {
            authorities.add(new SimpleGrantedAuthority("ROLE_FUNCIONARIO"));
        }
        return authorities;
    }

    @Override public String getUsername() { return this.email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
