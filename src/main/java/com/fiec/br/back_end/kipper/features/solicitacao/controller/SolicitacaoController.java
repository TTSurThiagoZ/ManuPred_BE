package com.fiec.br.back_end.kipper.features.solicitacao.controller;

import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.CreateSolicitacaoRequestDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoResponseDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoSearchFilterDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.service.SolicitacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
        SolicitacaoResponseDTO response = solicitacaoService.create(dto, anexos);
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
        SolicitacaoSearchFilterDTO filtro = new SolicitacaoSearchFilterDTO(
                id, termo, status, prioridade, numeroPatrimonio, localizacaoProblema,
                usuarioSolicitanteId, tecnicoResponsavelId,
                dataAberturaInicio, dataAberturaFim, dataFinalizacaoInicio, dataFinalizacaoFim
        );
        return ResponseEntity.ok(solicitacaoService.search(filtro, pageable));
    }
}