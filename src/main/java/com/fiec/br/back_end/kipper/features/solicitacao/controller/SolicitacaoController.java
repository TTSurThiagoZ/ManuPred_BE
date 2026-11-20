package com.fiec.br.back_end.kipper.features.solicitacao.controller;

import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.*;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.service.SolicitacaoService;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/solicitacoes")
@RequiredArgsConstructor
@Tag(name = "Solicitações", description = "Gerenciamento de chamados de manutenção")
public class SolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    private Users getUsuarioLogado() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Users u) {
            return u;
        }
        throw new AccessDeniedException("Usuário não autenticado.");
    }

    private boolean isAdmin(Users u) { return u.getRole().ehAdmin(); }
    private boolean isSupervisor(Users u) {
        return u.getRole().ehSupervisor();
    }
    private boolean isFuncionario(Users u) {
        return u.getRole().ehFuncionario();
    }

    /**
     * Quem pode VER um chamado (dados, comentários e histórico): a equipe (funcionário,
     * supervisor, admin), o solicitante, o técnico responsável e os ajudantes.
     */
    private void verificarAcesso(Users u, SolicitacaoResponseDTO r) {
        boolean ehEquipe = isFuncionario(u);
        boolean ehDono = u.getId().equals(r.usuarioSolicitanteId());
        boolean ehTecnico = u.getId().equals(r.tecnicoResponsavelId());
        boolean ehAjudante = r.ajudantes() != null && r.ajudantes().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(a -> a.id() != null && a.id().equals(u.getId()));

        if (!ehEquipe && !ehDono && !ehTecnico && !ehAjudante) {
            throw new AccessDeniedException("Sem permissão para acessar este chamado.");
        }
    }

    private UUID converterUuid(String valor, String campo, boolean obrigatorio) {
        if (valor == null || valor.isBlank()) {
            if (obrigatorio) throw new IllegalArgumentException("O campo '" + campo + "' é obrigatório.");
            return null;
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("O campo '" + campo + "' não é um UUID válido: " + valor);
        }
    }

    @Operation(summary = "Criar chamado", description = "Cria um novo chamado. Preencha os campos de texto e, se quiser, anexe imagens.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitacaoResponseDTO create(
            @RequestParam("tipoId") String tipoId,
            @RequestParam(value = "localId", required = false) String localId,
            @RequestParam("localizacaoProblema") String localizacaoProblema,
            @RequestParam("descricao") String descricao,
            @RequestParam(value = "numeroPatrimonio", required = false) String numeroPatrimonio,
            @RequestParam(value = "anexos", required = false) List<MultipartFile> anexos
    ) {
        // Como os campos chegam soltos (multipart), as anotações do DTO não são aplicadas
        // automaticamente; por isso a validação é feita aqui.
        if (localizacaoProblema == null || localizacaoProblema.isBlank()) {
            throw new IllegalArgumentException("A localização do problema é obrigatória.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("A descrição é obrigatória.");
        }

        CreateSolicitacaoRequestDTO dto = new CreateSolicitacaoRequestDTO(
                converterUuid(tipoId, "tipoId", true),
                converterUuid(localId, "localId", false),
                localizacaoProblema.trim(),
                descricao.trim(),
                numeroPatrimonio != null && !numeroPatrimonio.isBlank() ? numeroPatrimonio.trim() : null
        );

        Users u = getUsuarioLogado();
        return solicitacaoService.create(dto, u.getId(), anexos);
    }

    /**
     * Versão de {@link #create} para agentes de IA (MCP). Não é um endpoint HTTP: o MCP não
     * trafega arquivos multipart, então o chamado é criado sem anexos.
     */
    @McpTool(name = "criar_chamado",
            description = "Abre um novo chamado de manutenção em nome do usuário logado (sem anexos). "
                    + "Use 'listar_tipos_chamado' para obter o tipoId e 'listar_locais' para o localId. "
                    + "Se a descrição vier confusa, 'organizar_descricao_chamado' pode melhorá-la antes.",
            annotations = @McpTool.McpAnnotations(title = "Criar chamado",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public SolicitacaoResponseDTO criarChamado(
            @McpToolParam(description = "ID (UUID) do tipo de chamado") String tipoId,
            @McpToolParam(description = "ID (UUID) do local cadastrado, se houver", required = false) String localId,
            @McpToolParam(description = "Onde está o problema, em texto livre (ex.: 'Bloco B, sala 12, perto da janela')") String localizacaoProblema,
            @McpToolParam(description = "Descrição do problema") String descricao,
            @McpToolParam(description = "Número de patrimônio do equipamento, se houver", required = false) String numeroPatrimonio
    ) {
        return create(tipoId, localId, localizacaoProblema, descricao, numeroPatrimonio, null);
    }

    @Operation(summary = "Buscar chamados", description = "Busca paginada com filtros. Por padrão, os mais recentes primeiro.")
    @GetMapping("/search")
    public Page<SolicitacaoResponseDTO> search(
            @RequestParam(required = false) UUID id,
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusSolicitacao status,
            @RequestParam(required = false) PrioridadeSolicitacao prioridade,
            @RequestParam(required = false) String numeroPatrimonio,
            @RequestParam(required = false) String localizacaoProblema,
            @RequestParam(required = false) List<UUID> usuarioSolicitanteIds,
            @RequestParam(required = false) List<UUID> tecnicoResponsavelIds,
            @RequestParam(required = false) UUID tipoId,
            @RequestParam(required = false) UUID localId,
            @RequestParam(required = false) UUID setorId,
            @RequestParam(required = false) Boolean urgente,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataAberturaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataAberturaFim,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFinalizacaoInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFinalizacaoFim,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Users u = getUsuarioLogado();

        // Se não for da equipe, só vê os próprios chamados
        List<UUID> solicitanteFiltro = isFuncionario(u) ? usuarioSolicitanteIds : List.of(u.getId());

        SolicitacaoSearchFilterDTO filtro = new SolicitacaoSearchFilterDTO(
                id, termo, status, prioridade, numeroPatrimonio, localizacaoProblema,
                solicitanteFiltro, tecnicoResponsavelIds,
                tipoId, localId, setorId, urgente,
                dataAberturaInicio, dataAberturaFim, dataFinalizacaoInicio, dataFinalizacaoFim);

        return solicitacaoService.search(filtro, pageable);
    }

    /**
     * Versão de {@link #search} para agentes de IA (MCP). Não é um endpoint HTTP: troca o
     * {@link Pageable} por página/tamanho simples e devolve um JSON de página estável.
     */
    @McpTool(name = "buscar_chamados",
            description = "Busca chamados de manutenção com filtros, paginado, mais recentes primeiro. "
                    + "Todos os filtros são opcionais e combinados com E. Usuários comuns (USER_ROLE) só veem os "
                    + "próprios chamados. Datas no formato ISO, ex.: 2026-11-01T00:00:00.",
            annotations = @McpTool.McpAnnotations(title = "Buscar chamados",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public PagedModel<SolicitacaoResponseDTO> buscarChamados(
            @McpToolParam(description = "Texto buscado no título e na descrição (sem diferenciar maiúsculas)", required = false) String termo,
            @McpToolParam(description = "Status do chamado", required = false) StatusSolicitacao status,
            @McpToolParam(description = "Prioridade do chamado", required = false) PrioridadeSolicitacao prioridade,
            @McpToolParam(description = "Número de patrimônio do equipamento", required = false) String numeroPatrimonio,
            @McpToolParam(description = "Trecho da localização do problema", required = false) String localizacaoProblema,
            @McpToolParam(description = "IDs (UUID) dos solicitantes", required = false) List<UUID> usuarioSolicitanteIds,
            @McpToolParam(description = "IDs (UUID) dos técnicos responsáveis", required = false) List<UUID> tecnicoResponsavelIds,
            @McpToolParam(description = "ID (UUID) do tipo de chamado", required = false) UUID tipoId,
            @McpToolParam(description = "ID (UUID) do local", required = false) UUID localId,
            @McpToolParam(description = "ID (UUID) do setor", required = false) UUID setorId,
            @McpToolParam(description = "true para só urgentes, false para só não urgentes", required = false) Boolean urgente,
            @McpToolParam(description = "Aberto a partir desta data/hora", required = false) LocalDateTime dataAberturaInicio,
            @McpToolParam(description = "Aberto até esta data/hora", required = false) LocalDateTime dataAberturaFim,
            @McpToolParam(description = "Finalizado a partir desta data/hora", required = false) LocalDateTime dataFinalizacaoInicio,
            @McpToolParam(description = "Finalizado até esta data/hora", required = false) LocalDateTime dataFinalizacaoFim,
            @McpToolParam(description = "Número da página, começando em 0 (padrão 0)", required = false) Integer pagina,
            @McpToolParam(description = "Itens por página, de 1 a 50 (padrão 10)", required = false) Integer tamanho
    ) {
        int p = pagina == null || pagina < 0 ? 0 : pagina;
        int t = tamanho == null ? 10 : Math.clamp(tamanho, 1, 50);
        Pageable pageable = PageRequest.of(p, t, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PagedModel<>(search(null, termo, status, prioridade, numeroPatrimonio, localizacaoProblema,
                usuarioSolicitanteIds, tecnicoResponsavelIds, tipoId, localId, setorId, urgente,
                dataAberturaInicio, dataAberturaFim, dataFinalizacaoInicio, dataFinalizacaoFim, pageable));
    }

    @Operation(summary = "Buscar por ID")
    @McpTool(name = "buscar_chamado",
            description = "Retorna todos os dados de um chamado pelo ID: status, prioridade, tipo, local, solicitante, "
                    + "técnico, ajudantes, anexos e prazos de SLA.",
            annotations = @McpTool.McpAnnotations(title = "Buscar chamado por ID",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @GetMapping("/{id}")
    public SolicitacaoResponseDTO buscarPorId(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id) {
        SolicitacaoResponseDTO r = solicitacaoService.buscarPorId(id);
        verificarAcesso(getUsuarioLogado(), r);
        return r;
    }

    @Operation(summary = "Excluir chamado",
            description = "Apaga o chamado de vez, com comentários, histórico e anexos. "
                    + "ADMIN exclui qualquer chamado; o solicitante só exclui o próprio enquanto estiver ABERTO.")
    @McpTool(name = "excluir_chamado",
            description = "Apaga um chamado de forma permanente, junto com comentários, histórico e anexos. "
                    + "ADMIN exclui qualquer chamado; o solicitante só o próprio enquanto estiver ABERTO. "
                    + "Ação irreversível: confirme com o usuário antes. Para encerrar sem apagar, use 'atualizar_status_chamado' com CANCELADO.",
            annotations = @McpTool.McpAnnotations(title = "Excluir chamado",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id) {
        Users u = getUsuarioLogado();
        solicitacaoService.deletar(id, u.getId());
    }

    @Operation(summary = "Atualizar status",
            description = "Funcionário só altera chamados em que é técnico responsável ou ajudante. "
                    + "FINALIZADA só pode ser definida aqui por ADMIN; o fluxo normal é o solicitante usar /validar.")
    @McpTool(name = "atualizar_status_chamado",
            description = "Muda o status de um chamado. Funcionário só altera chamados em que é técnico responsável ou "
                    + "ajudante. FINALIZADA só pode ser definida aqui por ADMIN; o fluxo normal é 'concluir_chamado' "
                    + "(técnico) seguido de 'validar_chamado' (solicitante).",
            annotations = @McpTool.McpAnnotations(title = "Atualizar status do chamado",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FUNCIONARIO','USER')")
    @PatchMapping("/{id}/status")
    public SolicitacaoResponseDTO atualizarStatus(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id,
            @McpToolParam(description = "Novo status") @RequestParam StatusSolicitacao status,
            @McpToolParam(description = "Motivo da mudança (recomendado para REJEITADA, CANCELADO e "
                    + "AGUARDANDO_ASSISTENCIA_EXTERNA)", required = false)
            @RequestParam(required = false) String motivo) {
        Users u = getUsuarioLogado();
        return solicitacaoService.atualizarStatus(id, status, motivo, u.getId());
    }

    @Operation(summary = "Atualizar prioridade")
    @McpTool(name = "atualizar_prioridade_chamado",
            description = "Altera a prioridade de um chamado. Apenas ADMIN e SUPERVISOR.",
            annotations = @McpTool.McpAnnotations(title = "Atualizar prioridade do chamado",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @PatchMapping("/{id}/prioridade")
    public SolicitacaoResponseDTO atualizarPrioridade(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id,
            @McpToolParam(description = "Nova prioridade") @RequestParam PrioridadeSolicitacao prioridade) {
        Users u = getUsuarioLogado();
        return solicitacaoService.atualizarPrioridade(id, prioridade, u.getId());
    }

    @Operation(summary = "Atribuir técnico")
    @McpTool(name = "atribuir_tecnico_chamado",
            description = "Define o técnico responsável por um chamado. Use 'listar_usuarios' para achar o ID do técnico. "
                    + "Apenas ADMIN e SUPERVISOR.",
            annotations = @McpTool.McpAnnotations(title = "Atribuir técnico",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @PatchMapping("/{id}/tecnico")
    public SolicitacaoResponseDTO atribuirTecnico(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id,
            @McpToolParam(description = "ID (UUID) do usuário técnico") @RequestParam UUID tecnicoId) {
        Users u = getUsuarioLogado();
        return solicitacaoService.atribuirTecnico(id, tecnicoId, u.getId());
    }

    @Operation(summary = "Marcar urgência")
    @McpTool(name = "marcar_urgencia_chamado",
            description = "Marca ou desmarca um chamado como urgente. Apenas ADMIN e SUPERVISOR.",
            annotations = @McpTool.McpAnnotations(title = "Marcar urgência",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @PatchMapping("/{id}/urgente")
    public SolicitacaoResponseDTO marcarUrgente(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id,
            @McpToolParam(description = "true para marcar como urgente, false para desmarcar") @RequestParam boolean urgente) {
        Users u = getUsuarioLogado();
        return solicitacaoService.marcarUrgente(id, urgente, u.getId());
    }

    @Operation(summary = "Adicionar ajudante")
    @McpTool(name = "adicionar_ajudante_chamado",
            description = "Adiciona um usuário como ajudante do técnico em um chamado. Apenas ADMIN, SUPERVISOR e FUNCIONARIO.",
            annotations = @McpTool.McpAnnotations(title = "Adicionar ajudante",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FUNCIONARIO')")
    @PostMapping("/{id}/ajudantes/{ajudanteId}")
    public SolicitacaoResponseDTO adicionarAjudante(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id,
            @McpToolParam(description = "ID (UUID) do usuário ajudante") @PathVariable UUID ajudanteId) {
        Users u = getUsuarioLogado();
        return solicitacaoService.adicionarAjudante(id, ajudanteId, u.getId());
    }

    @Operation(summary = "Remover ajudante")
    @McpTool(name = "remover_ajudante_chamado",
            description = "Remove um ajudante de um chamado. Apenas ADMIN, SUPERVISOR e FUNCIONARIO.",
            annotations = @McpTool.McpAnnotations(title = "Remover ajudante",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FUNCIONARIO')")
    @DeleteMapping("/{id}/ajudantes/{ajudanteId}")
    public SolicitacaoResponseDTO removerAjudante(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id,
            @McpToolParam(description = "ID (UUID) do usuário ajudante") @PathVariable UUID ajudanteId) {
        Users u = getUsuarioLogado();
        return solicitacaoService.removerAjudante(id, ajudanteId, u.getId());
    }

    @Operation(summary = "Concluir chamado", description = "Técnico informa que terminou. Fica aguardando validação do solicitante.")
    @McpTool(name = "concluir_chamado",
            description = "O técnico informa que terminou o serviço. O chamado vai para CONCLUIDO e fica aguardando "
                    + "o solicitante validar ('validar_chamado'). Apenas ADMIN, SUPERVISOR e FUNCIONARIO.",
            annotations = @McpTool.McpAnnotations(title = "Concluir chamado",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FUNCIONARIO')")
    @PatchMapping("/{id}/concluir")
    public SolicitacaoResponseDTO concluir(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id) {
        Users u = getUsuarioLogado();
        return solicitacaoService.concluir(id, u.getId());
    }

    @Operation(summary = "Validar finalização", description = "Solicitante (ou ADMIN) confirma um chamado que está CONCLUIDO.")
    @McpTool(name = "validar_chamado",
            description = "O solicitante (ou um ADMIN) confirma que o serviço de um chamado CONCLUIDO ficou bom; "
                    + "o chamado passa a FINALIZADA.",
            annotations = @McpTool.McpAnnotations(title = "Validar finalização",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PatchMapping("/{id}/validar")
    public SolicitacaoResponseDTO validar(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id) {
        Users u = getUsuarioLogado();
        SolicitacaoResponseDTO r = solicitacaoService.buscarPorId(id);
        if (!isAdmin(u) && !u.getId().equals(r.usuarioSolicitanteId())) {
            throw new AccessDeniedException("Só o solicitante pode validar.");
        }
        return solicitacaoService.validarFinalizacao(id, u.getId());
    }

    @Operation(summary = "Rejeitar chamado")
    @McpTool(name = "rejeitar_chamado",
            description = "Rejeita um chamado informando o motivo. Permitido a ADMIN, SUPERVISOR e ao próprio solicitante.",
            annotations = @McpTool.McpAnnotations(title = "Rejeitar chamado",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    @PatchMapping("/{id}/rejeitar")
    public SolicitacaoResponseDTO rejeitar(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id,
            @McpToolParam(description = "Motivo da rejeição") @RequestParam String motivo) {
        Users u = getUsuarioLogado();
        SolicitacaoResponseDTO r = solicitacaoService.buscarPorId(id);
        if (!isSupervisor(u) && !u.getId().equals(r.usuarioSolicitanteId())) {
            throw new AccessDeniedException("Sem permissão para rejeitar.");
        }
        return solicitacaoService.rejeitar(id, motivo, u.getId());
    }

    @Operation(summary = "Listar comentários")
    @McpTool(name = "listar_comentarios_chamado",
            description = "Lista os comentários de um chamado, do mais antigo ao mais recente.",
            annotations = @McpTool.McpAnnotations(title = "Listar comentários",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @GetMapping("/{id}/comentarios")
    public List<ComentarioResponseDTO> listarComentarios(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id) {
        // Antes qualquer usuário logado lia os comentários de qualquer chamado só trocando o ID
        verificarAcesso(getUsuarioLogado(), solicitacaoService.buscarPorId(id));
        return solicitacaoService.listarComentarios(id);
    }

    @Operation(summary = "Adicionar comentário")
    @McpTool(name = "comentar_chamado",
            description = "Adiciona um comentário a um chamado em nome do usuário logado. Os envolvidos são notificados.",
            annotations = @McpTool.McpAnnotations(title = "Comentar chamado",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PostMapping("/{id}/comentarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ComentarioResponseDTO comentar(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id,
            @McpToolParam(description = "Texto do comentário") @RequestBody @Valid ComentarioRequestDTO dto) {
        Users u = getUsuarioLogado();
        verificarAcesso(u, solicitacaoService.buscarPorId(id));
        return solicitacaoService.adicionarComentario(id, dto.texto(), u.getId());
    }

    @Operation(summary = "Listar histórico/movimentações")
    @McpTool(name = "historico_chamado",
            description = "Lista o histórico de movimentações de um chamado (mudanças de status, prioridade, técnico etc.).",
            annotations = @McpTool.McpAnnotations(title = "Histórico do chamado",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @GetMapping("/{id}/historico")
    public List<MovimentacaoResponseDTO> historico(
            @McpToolParam(description = "ID (UUID) do chamado") @PathVariable UUID id) {
        verificarAcesso(getUsuarioLogado(), solicitacaoService.buscarPorId(id));
        return solicitacaoService.listarMovimentacoes(id);
    }

    @Operation(summary = "Gerar relatórios", description = "Apenas ADMIN. Início e fim são opcionais (pode informar só um deles).")
    @McpTool(name = "gerar_relatorio_chamados",
            description = "Gera indicadores dos chamados num período: total, tempo médio de resolução em dias, "
                    + "tipo mais frequente e quantidade/percentual de rejeitados. "
                    + "Início e fim são opcionais. Apenas ADMIN.",
            annotations = @McpTool.McpAnnotations(title = "Relatório de chamados",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/relatorios")
    public RelatorioDTO relatorio(
            @McpToolParam(description = "Início do período, ISO (ex.: 2026-11-01T00:00:00)", required = false)
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @McpToolParam(description = "Fim do período, ISO (ex.: 2026-11-30T23:59:59)", required = false)
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        return solicitacaoService.gerarRelatorio(inicio, fim);
    }
}
