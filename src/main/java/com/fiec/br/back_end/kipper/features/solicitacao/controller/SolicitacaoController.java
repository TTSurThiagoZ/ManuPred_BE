package com.fiec.br.back_end.kipper.features.solicitacao.controller;

import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.CreateSolicitacaoRequestDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoResponseDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoSearchFilterDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.service.SolicitacaoService;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
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
public class SolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SolicitacaoResponseDTO> create(
            @RequestPart("dados") @Valid CreateSolicitacaoRequestDTO dto,
            @RequestPart(value = "anexos", required = false) List<MultipartFile> anexos
    ) {
        Users usuarioLogado = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SolicitacaoResponseDTO response = solicitacaoService.create(dto, usuarioLogado.getId(), anexos);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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
        Users usuarioLogado = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Provisório: só existe ADMIN_ROLE e USER_ROLE hoje.
        // Quando criarem funcionário/supervisor, incluir eles aqui também.
        UUID solicitanteFiltro = usuarioLogado.getRole() == UserRole.ADMIN_ROLE
                ? usuarioSolicitanteId
                : usuarioLogado.getId();

        SolicitacaoSearchFilterDTO filtro = new SolicitacaoSearchFilterDTO(
                id, termo, status, prioridade, numeroPatrimonio, localizacaoProblema,
                solicitanteFiltro, tecnicoResponsavelId,
                dataAberturaInicio, dataAberturaFim, dataFinalizacaoInicio, dataFinalizacaoFim
        );
        return ResponseEntity.ok(solicitacaoService.search(filtro, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SolicitacaoResponseDTO> buscarPorId(@PathVariable UUID id) {
        Users usuarioLogado = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SolicitacaoResponseDTO response = solicitacaoService.buscarPorId(id);

        boolean ehAdmin = usuarioLogado.getRole() == UserRole.ADMIN_ROLE;
        boolean ehDoProprioUsuario = usuarioLogado.getId().equals(response.usuarioSolicitanteId());

        if (!ehAdmin && !ehDoProprioUsuario) {
            throw new AccessDeniedException("Você não tem permissão para ver este chamado.");
        }
        return ResponseEntity.ok(response);
    }

    // Restrito a ADMIN por ora; se o time criar um papel específico de técnico,
    // trocar para hasAnyRole('ADMIN', 'TECNICO').
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<SolicitacaoResponseDTO> atualizarStatus(
            @PathVariable UUID id,
            @RequestParam StatusSolicitacao status
    ) {
        return ResponseEntity.ok(solicitacaoService.atualizarStatus(id, status));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/prioridade")
    public ResponseEntity<SolicitacaoResponseDTO> atualizarPrioridade(
            @PathVariable UUID id,
            @RequestParam PrioridadeSolicitacao prioridade
    ) {
        return ResponseEntity.ok(solicitacaoService.atualizarPrioridade(id, prioridade));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/tecnico")
    public ResponseEntity<SolicitacaoResponseDTO> atribuirTecnico(
            @PathVariable UUID id,
            @RequestParam UUID tecnicoId
    ) {
        return ResponseEntity.ok(solicitacaoService.atribuirTecnico(id, tecnicoId));
    }
}