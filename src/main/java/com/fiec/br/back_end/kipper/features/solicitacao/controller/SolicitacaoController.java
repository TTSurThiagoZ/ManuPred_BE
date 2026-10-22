package com.fiec.br.back_end.kipper.features.solicitacao.controller;

import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.*;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.service.SolicitacaoService;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
        return (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private boolean isAdmin(Users u) { return u.getRole() == UserRole.ADMIN_ROLE; }
    private boolean isSupervisor(Users u) {
        return u.getRole() == UserRole.ADMIN_ROLE || u.getRole() == UserRole.SUPERVISOR_ROLE;
    }
    private boolean isFuncionario(Users u) {
        return u.getRole() == UserRole.ADMIN_ROLE || u.getRole() == UserRole.SUPERVISOR_ROLE
                || u.getRole() == UserRole.FUNCIONARIO_ROLE;
    }

    @Operation(summary = "Criar chamado", description = "Cria um novo chamado. Preencha os campos de texto e, se quiser, anexe imagens.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SolicitacaoResponseDTO> create(
            @RequestParam("tipoId") String tipoId,
            @RequestParam(value = "localId", required = false) String localId,
            @RequestParam("localizacaoProblema") String localizacaoProblema,
            @RequestParam("descricao") String descricao,
            @RequestParam(value = "numeroPatrimonio", required = false) String numeroPatrimonio,
            @RequestParam(value = "anexos", required = false) List<MultipartFile> anexos
    ) {
        try {
            CreateSolicitacaoRequestDTO dto = new CreateSolicitacaoRequestDTO(
                    tipoId != null && !tipoId.isBlank() ? UUID.fromString(tipoId) : null,
                    localId != null && !localId.isBlank() ? UUID.fromString(localId) : null,
                    localizacaoProblema,
                    descricao,
                    numeroPatrimonio
            );
            Users u = getUsuarioLogado();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(solicitacaoService.create(dto, u.getId(), anexos));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Erro nos dados enviados: verifique se tipoId e localId são UUIDs válidos. Detalhe: " + e.getMessage());
        }
    }

    @Operation(summary = "Buscar chamados", description = "Busca paginada com filtros. Funcionários e supervisores veem tudo; usuários comuns veem só os próprios.")
    @GetMapping("/search")
    public ResponseEntity<Page<SolicitacaoResponseDTO>> search(
            @RequestParam(required = false) UUID id,
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusSolicitacao status,
            @RequestParam(required = false) PrioridadeSolicitacao prioridade,
            @RequestParam(required = false) String numeroPatrimonio,
            @RequestParam(required = false) String localizacaoProblema,
            @RequestParam(required = false) UUID usuarioSolicitanteId,
            @RequestParam(required = false) UUID tecnicoResponsavelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataAberturaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataAberturaFim,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFinalizacaoInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFinalizacaoFim,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable
    ) {
        Users u = getUsuarioLogado();
        UUID solicitanteFiltro = isFuncionario(u) ? usuarioSolicitanteId : u.getId();
        SolicitacaoSearchFilterDTO filtro = new SolicitacaoSearchFilterDTO(
                id, termo, status, prioridade, numeroPatrimonio, localizacaoProblema,
                solicitanteFiltro, tecnicoResponsavelId,
                dataAberturaInicio, dataAberturaFim, dataFinalizacaoInicio, dataFinalizacaoFim);
        return ResponseEntity.ok(solicitacaoService.search(filtro, pageable));
    }

    @Operation(summary = "Buscar por ID")
    @GetMapping("/{id}")
    public ResponseEntity<SolicitacaoResponseDTO> buscarPorId(@PathVariable UUID id) {
        Users u = getUsuarioLogado();
        SolicitacaoResponseDTO r = solicitacaoService.buscarPorId(id);

        boolean ehAdmin = isAdmin(u);
        boolean ehFuncionario = isFuncionario(u);
        boolean ehDono = u.getId().equals(r.usuarioSolicitanteId());
        boolean ehTecnico = u.getId().equals(r.tecnicoResponsavelId());
        boolean ehAjudante = r.ajudantes() != null && r.ajudantes().stream().anyMatch(a -> a.id().equals(u.getId()));

        if (!ehAdmin && !ehFuncionario && !ehDono && !ehTecnico && !ehAjudante) {
            throw new AccessDeniedException("Sem permissão para ver este chamado.");
        }
        return ResponseEntity.ok(r);
    }

    @Operation(summary = "Atualizar status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FUNCIONARIO')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<SolicitacaoResponseDTO> atualizarStatus(
            @PathVariable UUID id,
            @RequestParam StatusSolicitacao status,
            @RequestParam(required = false) String motivo) {
        Users u = getUsuarioLogado();
        return ResponseEntity.ok(solicitacaoService.atualizarStatus(id, status, motivo, u.getId()));
    }

    @Operation(summary = "Atualizar prioridade")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @PatchMapping("/{id}/prioridade")
    public ResponseEntity<SolicitacaoResponseDTO> atualizarPrioridade(
            @PathVariable UUID id,
            @RequestParam PrioridadeSolicitacao prioridade) {
        Users u = getUsuarioLogado();
        return ResponseEntity.ok(solicitacaoService.atualizarPrioridade(id, prioridade, u.getId()));
    }

    @Operation(summary = "Atribuir técnico")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @PatchMapping("/{id}/tecnico")
    public ResponseEntity<SolicitacaoResponseDTO> atribuirTecnico(
            @PathVariable UUID id, @RequestParam UUID tecnicoId) {
        Users u = getUsuarioLogado();
        return ResponseEntity.ok(solicitacaoService.atribuirTecnico(id, tecnicoId, u.getId()));
    }

    @Operation(summary = "Marcar urgência")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @PatchMapping("/{id}/urgente")
    public ResponseEntity<SolicitacaoResponseDTO> marcarUrgente(
            @PathVariable UUID id, @RequestParam boolean urgente) {
        Users u = getUsuarioLogado();
        return ResponseEntity.ok(solicitacaoService.marcarUrgente(id, urgente, u.getId()));
    }

    @Operation(summary = "Adicionar ajudante")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FUNCIONARIO')")
    @PostMapping("/{id}/ajudantes/{ajudanteId}")
    public ResponseEntity<SolicitacaoResponseDTO> adicionarAjudante(
            @PathVariable UUID id, @PathVariable UUID ajudanteId) {
        Users u = getUsuarioLogado();
        return ResponseEntity.ok(solicitacaoService.adicionarAjudante(id, ajudanteId, u.getId()));
    }

    @Operation(summary = "Remover ajudante")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FUNCIONARIO')")
    @DeleteMapping("/{id}/ajudantes/{ajudanteId}")
    public ResponseEntity<SolicitacaoResponseDTO> removerAjudante(
            @PathVariable UUID id, @PathVariable UUID ajudanteId) {
        Users u = getUsuarioLogado();
        return ResponseEntity.ok(solicitacaoService.removerAjudante(id, ajudanteId, u.getId()));
    }

    @Operation(summary = "Concluir chamado")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FUNCIONARIO')")
    @PatchMapping("/{id}/concluir")
    public ResponseEntity<SolicitacaoResponseDTO> concluir(@PathVariable UUID id) {
        Users u = getUsuarioLogado();
        return ResponseEntity.ok(solicitacaoService.concluir(id, u.getId()));
    }

    @Operation(summary = "Validar finalização")
    @PatchMapping("/{id}/validar")
    public ResponseEntity<SolicitacaoResponseDTO> validar(@PathVariable UUID id) {
        Users u = getUsuarioLogado();
        SolicitacaoResponseDTO r = solicitacaoService.buscarPorId(id);
        if (!isAdmin(u) && !u.getId().equals(r.usuarioSolicitanteId())) {
            throw new AccessDeniedException("Só o solicitante pode validar.");
        }
        return ResponseEntity.ok(solicitacaoService.validarFinalizacao(id, u.getId()));
    }

    @Operation(summary = "Rejeitar chamado")
    @PatchMapping("/{id}/rejeitar")
    public ResponseEntity<SolicitacaoResponseDTO> rejeitar(
            @PathVariable UUID id, @RequestParam String motivo) {
        Users u = getUsuarioLogado();
        SolicitacaoResponseDTO r = solicitacaoService.buscarPorId(id);
        if (!isAdmin(u) && !isSupervisor(u) && !u.getId().equals(r.usuarioSolicitanteId())) {
            throw new AccessDeniedException("Sem permissão para rejeitar.");
        }
        return ResponseEntity.ok(solicitacaoService.rejeitar(id, motivo, u.getId()));
    }

    @Operation(summary = "Listar comentários")
    @GetMapping("/{id}/comentarios")
    public ResponseEntity<List<ComentarioResponseDTO>> listarComentarios(@PathVariable UUID id) {
        return ResponseEntity.ok(solicitacaoService.listarComentarios(id));
    }

    @Operation(summary = "Adicionar comentário")
    @PostMapping("/{id}/comentarios")
    public ResponseEntity<ComentarioResponseDTO> comentar(
            @PathVariable UUID id, @RequestBody @Valid ComentarioRequestDTO dto) {
        Users u = getUsuarioLogado();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(solicitacaoService.adicionarComentario(id, dto.texto(), u.getId()));
    }

    @Operation(summary = "Listar histórico/movimentações")
    @GetMapping("/{id}/historico")
    public ResponseEntity<List<MovimentacaoResponseDTO>> historico(@PathVariable UUID id) {
        return ResponseEntity.ok(solicitacaoService.listarMovimentacoes(id));
    }

    @Operation(summary = "Gerar relatórios", description = "Apenas ADMIN. Se início/fim forem omitidos, calcula sobre tudo.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/relatorios")
    public ResponseEntity<RelatorioDTO> relatorio(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        return ResponseEntity.ok(solicitacaoService.gerarRelatorio(inicio, fim));
    }
}