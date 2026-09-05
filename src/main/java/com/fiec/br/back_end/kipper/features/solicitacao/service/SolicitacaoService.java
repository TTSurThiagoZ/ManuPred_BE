package com.fiec.br.back_end.kipper.features.solicitacao.service;

import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.CreateSolicitacaoRequestDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoResponseDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoSearchFilterDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SolicitacaoService {
    Page<SolicitacaoResponseDTO> search(SolicitacaoSearchFilterDTO filtro, Pageable pageable);
    SolicitacaoResponseDTO create(CreateSolicitacaoRequestDTO dto, List<MultipartFile> anexos);
}
