package com.fiec.br.back_end.kipper.features.solicitacao.service;

import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.CreateSolicitacaoRequestDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoResponseDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoSearchFilterDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface SolicitacaoService {
    Page<SolicitacaoResponseDTO> search(SolicitacaoSearchFilterDTO filtro, Pageable pageable);
    SolicitacaoResponseDTO create(CreateSolicitacaoRequestDTO dto, UUID usuarioSolicitanteId, List<MultipartFile> anexos);
    SolicitacaoResponseDTO buscarPorId(UUID id);
    SolicitacaoResponseDTO atualizarStatus(UUID id, StatusSolicitacao novoStatus);
    SolicitacaoResponseDTO atualizarPrioridade(UUID id, PrioridadeSolicitacao novaPrioridade);
    SolicitacaoResponseDTO atribuirTecnico(UUID id, UUID tecnicoId);
}