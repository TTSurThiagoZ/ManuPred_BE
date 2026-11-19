package com.fiec.br.back_end.kipper.features.ia.controller;

import com.fiec.br.back_end.kipper.features.ia.model.dto.*;
import com.fiec.br.back_end.kipper.features.ia.service.IaService;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ia")
@RequiredArgsConstructor
@Tag(name = "Inteligência Artificial", description = "Endpoints do assistente de IA")
public class IaController {

    private final IaService iaService;

    private Users getUsuarioLogado() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Users u) {
            return u;
        }
        throw new AccessDeniedException("Usuário não autenticado.");
    }

    // Liberado sem login (também está permitAll no SecurityConfig)
    @Operation(summary = "Verificar status da IA")
    @McpTool(name = "verificar_status_ia",
            description = "Informa se o assistente de IA do sistema está configurado e disponível.",
            annotations = @McpTool.McpAnnotations(title = "Status da IA",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("permitAll()")
    @GetMapping("/status")
    public IaStatusDTO status() {
        return iaService.verificarStatus();
    }

    @Operation(summary = "Busca em linguagem natural", description = "Apenas ADMIN e SUPERVISOR.")
    @McpTool(name = "buscar_chamados_linguagem_natural",
            description = "Busca chamados de manutenção a partir de uma pergunta em português "
                    + "(ex.: 'chamados urgentes do bloco B abertos esta semana'). A IA converte a pergunta em filtros "
                    + "e devolve os filtros usados e os chamados encontrados (paginado). Apenas ADMIN e SUPERVISOR.",
            annotations = @McpTool.McpAnnotations(title = "Busca de chamados em linguagem natural",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @PostMapping("/busca")
    public IaBuscaResponseDTO buscar(
            @McpToolParam(description = "Pergunta (3 a 500 caracteres) e, opcionalmente, page (a partir de 0) e size (1 a 50)")
            @RequestBody @Valid IaBuscaRequestDTO request) {
        Users u = getUsuarioLogado();
        return iaService.buscarComLinguagemNatural(request, u.getEmail());
    }

    @Operation(summary = "Organizar descrição", description = "Qualquer usuário logado.")
    @McpTool(name = "organizar_descricao_chamado",
            description = "Reescreve um texto livre do usuário como uma descrição de chamado de manutenção clara e "
                    + "organizada, sugere o tipo de chamado e indica se parece urgente. Útil antes de chamar "
                    + "'criar_chamado'. Não grava nada.",
            annotations = @McpTool.McpAnnotations(title = "Organizar descrição de chamado",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = false, openWorldHint = true))
    @PostMapping("/organizar-descricao")
    public OrganizarDescricaoResponseDTO organizar(
            @McpToolParam(description = "Texto a organizar (5 a 2000 caracteres) e, opcionalmente, a localização do problema")
            @RequestBody @Valid OrganizarDescricaoRequestDTO request) {
        Users u = getUsuarioLogado();
        return iaService.organizarDescricao(request, u.getEmail());
    }

    @Operation(summary = "Gerar resumo do chamado", description = "Apenas ADMIN, SUPERVISOR e FUNCIONARIO.")
    @McpTool(name = "resumir_chamado",
            description = "Gera com IA um resumo de um chamado (situação, histórico e comentários). "
                    + "Apenas ADMIN, SUPERVISOR e FUNCIONARIO.",
            annotations = @McpTool.McpAnnotations(title = "Resumo do chamado",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = false, openWorldHint = true))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FUNCIONARIO')")
    @GetMapping("/solicitacoes/{id}/resumo")
    public ResumoSolicitacaoResponseDTO resumo(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id) {
        Users u = getUsuarioLogado();
        return iaService.gerarResumoSolicitacao(id, u.getEmail());
    }
}
