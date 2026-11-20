package com.fiec.br.back_end.kipper.config;

import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * TEMPORÁRIO: deixa os agentes de IA usarem o servidor MCP (/mcp) sem token.
 * <p>
 * Quando a requisição ao /mcp chega sem usuário autenticado (sem JWT ou com JWT inválido),
 * ela passa a agir como uma conta de serviço: o e-mail em {@code app.mcp.acesso-livre.email}
 * ou, se vazio, o primeiro SUPER_ADMIN do banco (senão, o primeiro ADMIN). Quem manda um JWT
 * válido continua sendo tratado como ele mesmo.
 * <p>
 * Ligado/desligado por {@code app.mcp.acesso-livre.habilitado} (variável MCP_ACESSO_LIVRE).
 * Qualquer pessoa que alcance o /mcp ganha poderes de administrador: não deixe ligado em produção.
 */
@Slf4j
public class McpAcessoLivreFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final String emailContaServico;

    public McpAcessoLivreFilter(UserRepository userRepository, String emailContaServico) {
        this.userRepository = userRepository;
        this.emailContaServico = emailContaServico;
        log.warn("MCP com ACESSO LIVRE: requisições ao /mcp sem token agem como administrador. "
                + "Defina MCP_ACESSO_LIVRE=false para exigir o JWT de novo.");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return !(caminho.equals("/mcp") || caminho.startsWith("/mcp/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<Users> conta = buscarContaServico();
            if (conta.isPresent()) {
                Users u = conta.get();
                var auth = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                log.warn("MCP com acesso livre, mas nenhuma conta de serviço encontrada "
                        + "(defina MCP_ACESSO_LIVRE_EMAIL ou crie um SUPER_ADMIN/ADMIN).");
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<Users> buscarContaServico() {
        if (emailContaServico != null && !emailContaServico.isBlank()) {
            return userRepository.findByEmail(emailContaServico.trim());
        }
        return userRepository.findByRole(UserRole.SUPER_ADMIN_ROLE).stream().findFirst()
                .or(() -> userRepository.findByRole(UserRole.ADMIN_ROLE).stream().findFirst());
    }
}
