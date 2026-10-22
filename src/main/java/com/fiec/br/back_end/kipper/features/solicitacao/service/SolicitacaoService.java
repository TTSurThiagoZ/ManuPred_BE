package com.fiec.br.back_end.kipper.features.solicitacao.service;

import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.*;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SolicitacaoService {

    Page<SolicitacaoResponseDTO> search(SolicitacaoSearchFilterDTO filtro, Pageable pageable);

    SolicitacaoResponseDTO create(CreateSolicitacaoRequestDTO dto, UUID usuarioSolicitanteId, List<MultipartFile> anexos);

    SolicitacaoResponseDTO buscarPorId(UUID id);

    SolicitacaoResponseDTO atualizarStatus(UUID id, StatusSolicitacao novoStatus, String motivo, UUID autorId);

    SolicitacaoResponseDTO atualizarPrioridade(UUID id, PrioridadeSolicitacao novaPrioridade, UUID autorId);

    SolicitacaoResponseDTO atribuirTecnico(UUID id, UUID tecnicoId, UUID autorId);

    SolicitacaoResponseDTO marcarUrgente(UUID id, boolean urgente, UUID autorId);

    SolicitacaoResponseDTO adicionarAjudante(UUID id, UUID ajudanteId, UUID autorId);

    SolicitacaoResponseDTO removerAjudante(UUID id, UUID ajudanteId, UUID autorId);

    SolicitacaoResponseDTO concluir(UUID id, UUID autorId);

    SolicitacaoResponseDTO validarFinalizacao(UUID id, UUID autorId);

    SolicitacaoResponseDTO rejeitar(UUID id, String motivo, UUID autorId);

    List<ComentarioResponseDTO> listarComentarios(UUID solicitacaoId);

    ComentarioResponseDTO adicionarComentario(UUID solicitacaoId, String texto, UUID autorId);

    List<MovimentacaoResponseDTO> listarMovimentacoes(UUID solicitacaoId);

    RelatorioDTO gerarRelatorio(LocalDateTime inicio, LocalDateTime fim);
}