package com.fiec.br.back_end.kipper.config;

import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.TipoChamado;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.TipoChamadoRepository;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TipoChamadoRepository tipoChamadoRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.nome:Administrador}")
    private String adminNome;

    @Value("${app.admin.email:admin@manupred.com}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.super-admin.nome:Super Administrador}")
    private String superAdminNome;

    @Value("${app.super-admin.email:}")
    private String superAdminEmail;

    @Value("${app.super-admin.password:}")
    private String superAdminPassword;

    @Override
    public void run(String... args) {
        criarTiposIniciais();
        criarAdminInicial();
        criarSuperAdminInicial();
    }

    private void criarTiposIniciais() {
        // Só cria os tipos se o banco estiver zerado, para não duplicar a cada vez que rodar
        if (tipoChamadoRepository.count() > 0) return;

        List<TipoChamado> tiposIniciais = List.of(
                TipoChamado.builder()
                        .nome("Elétrica").icone("💡").slaHoras(24).acaoAoEstourar("MARCAR_URGENTE").build(),
                TipoChamado.builder()
                        .nome("Hidráulica").icone("🚰").slaHoras(24).acaoAoEstourar("MARCAR_URGENTE").build(),
                TipoChamado.builder()
                        .nome("Ar-condicionado").icone("❄️").slaHoras(48).acaoAoEstourar("MARCAR_URGENTE").build(),
                TipoChamado.builder()
                        .nome("Marcenaria").icone("🪑").slaHoras(72).acaoAoEstourar("MARCAR_URGENTE").build(),
                TipoChamado.builder()
                        .nome("Alvenaria e Pintura").icone("🧱").slaHoras(120).acaoAoEstourar("MARCAR_URGENTE").build(),
                TipoChamado.builder()
                        .nome("Equipamentos / TI").icone("💻").slaHoras(48).acaoAoEstourar("MARCAR_URGENTE").build(),
                TipoChamado.builder()
                        .nome("Limpeza e Conservação").icone("🧹").slaHoras(24).acaoAoEstourar("MARCAR_URGENTE").build()
        );

        tipoChamadoRepository.saveAll(tiposIniciais);
        log.info("Tipos de chamado iniciais criados no MongoDB.");
    }

    /**
     * Garante que exista pelo menos um ADMIN, para não precisar promover alguém na mão a cada
     * banco novo. Só age quando não há nenhum admin e a variável ADMIN_PASSWORD está definida.
     */
    private void criarAdminInicial() {
        // Consulta direta no banco, sem carregar todos os usuários
        if (userRepository.existsByRole(UserRole.ADMIN_ROLE)) return;

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Nenhum ADMIN no banco e a variável ADMIN_PASSWORD não foi definida: "
                    + "admin inicial NÃO criado. Defina ADMIN_PASSWORD (e, se quiser, ADMIN_EMAIL) e reinicie.");
            return;
        }

        Optional<Users> existente = userRepository.findByEmail(adminEmail);
        if (existente.isPresent()) {
            // O e-mail já tem conta: só promove, sem mexer na senha dela
            Users u = existente.get();
            u.setRole(UserRole.ADMIN_ROLE);
            userRepository.save(u);
            log.info("Usuário {} promovido a ADMIN.", adminEmail);
            return;
        }

        userRepository.save(Users.builder()
                .name(adminNome)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN_ROLE)
                .build());
        log.info("Admin inicial criado: {}", adminEmail);
    }

    /**
     * O SUPER_ADMIN não pode ser concedido por um ADMIN comum, então o primeiro precisa vir daqui.
     * Só age quando não há nenhum SUPER_ADMIN e SUPER_ADMIN_EMAIL está definida: se o e-mail já tem
     * conta, ela é promovida (a senha não muda); senão, a conta é criada com SUPER_ADMIN_PASSWORD.
     */
    private void criarSuperAdminInicial() {
        if (superAdminEmail == null || superAdminEmail.isBlank()) return;
        if (userRepository.existsByRole(UserRole.SUPER_ADMIN_ROLE)) return;

        Optional<Users> existente = userRepository.findByEmail(superAdminEmail);
        if (existente.isPresent()) {
            Users u = existente.get();
            u.setRole(UserRole.SUPER_ADMIN_ROLE);
            userRepository.save(u);
            log.info("Usuário {} promovido a SUPER_ADMIN.", superAdminEmail);
            return;
        }

        if (superAdminPassword == null || superAdminPassword.isBlank()) {
            log.warn("SUPER_ADMIN_EMAIL={} não tem conta e SUPER_ADMIN_PASSWORD não foi definida: "
                    + "SUPER_ADMIN inicial NÃO criado.", superAdminEmail);
            return;
        }

        userRepository.save(Users.builder()
                .name(superAdminNome)
                .email(superAdminEmail)
                .password(passwordEncoder.encode(superAdminPassword))
                .role(UserRole.SUPER_ADMIN_ROLE)
                .build());
        log.info("SUPER_ADMIN inicial criado: {}", superAdminEmail);
    }
}
