package com.fiec.br.back_end.kipper.config;

import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Liga a auditoria automática do Spring Data JPA (campos definidos em
 * Auditoria) ao usuário autenticado da requisição atual.
 *
 * O JwtAuthFilter coloca o próprio objeto Users como principal do
 * Authentication, então createdBy/updatedBy são gravados com o e-mail
 * de quem fez a requisição. Em endpoints públicos (registro/login), não
 * há usuário autenticado ainda, então os campos ficam nulos — o que é o
 * comportamento esperado.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null
                    || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                return Optional.empty();
            }

            if (authentication.getPrincipal() instanceof Users user) {
                return Optional.ofNullable(user.getEmail());
            }

            return Optional.of(authentication.getName());
        };
    }
}