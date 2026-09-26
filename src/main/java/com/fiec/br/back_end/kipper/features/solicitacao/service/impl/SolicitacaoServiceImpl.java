package com.fiec.br.back_end.kipper.features.solicitacao.service.impl;

import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.AnexoResponseDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.CreateSolicitacaoRequestDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoResponseDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SolicitacaoSearchFilterDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Anexo;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Solicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.AnexoRepository;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.SolicitacaoRepository;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.specification.SolicitacaoSpecification;
import com.fiec.br.back_end.kipper.features.solicitacao.service.FileStorageService;
import com.fiec.br.back_end.kipper.features.solicitacao.service.SolicitacaoService;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolicitacaoServiceImpl implements SolicitacaoService {

    private final SolicitacaoRepository solicitacaoRepository;
    private final AnexoRepository anexoRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public Page<SolicitacaoResponseDTO> search(SolicitacaoSearchFilterDTO filtro, Pageable pageable) {
        return solicitacaoRepository
                .findAll(SolicitacaoSpecification.comFiltros(filtro), pageable)
                .map(SolicitacaoResponseDTO::fromEntity);
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO create(CreateSolicitacaoRequestDTO dto, UUID usuarioSolicitanteId, List<MultipartFile> anexos) {
        Users solicitante = userRepository.findById(usuarioSolicitanteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário solicitante não encontrado."));

        Solicitacao solicitacao = Solicitacao.builder()
                .titulo(dto.tipo() + " - " + dto.localizacaoProblema())
                .descricao(dto.descricao())
                .status(StatusSolicitacao.ABERTO)
                .prioridade(PrioridadeSolicitacao.MEDIA)
                .tipo(dto.tipo())
                .numeroPatrimonio(dto.numeroPatrimonio())
                .localizacaoProblema(dto.localizacaoProblema())
                .usuarioSolicitante(solicitante)
                .build();

        Solicitacao salva = solicitacaoRepository.save(solicitacao);

        List<AnexoResponseDTO> anexosSalvos = new ArrayList<>();
        if (anexos != null) {
            for (MultipartFile arquivo : anexos) {
                if (arquivo == null || arquivo.isEmpty()) continue;

                String caminho = fileStorageService.salvar(salva.getId(), arquivo);
                Anexo anexo = Anexo.builder()
                        .nomeArquivo(arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "arquivo")
                        .caminhoArmazenado(caminho)
                        .tipoConteudo(arquivo.getContentType())
                        .solicitacao(salva)
                        .build();
                anexosSalvos.add(AnexoResponseDTO.fromEntity(anexoRepository.save(anexo)));
            }
        }

        return SolicitacaoResponseDTO.fromEntity(salva, anexosSalvos);
    }

    @Override
    @Transactional(readOnly = true)
    public SolicitacaoResponseDTO buscarPorId(UUID id) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        List<AnexoResponseDTO> anexos = anexoRepository.findBySolicitacaoId(id).stream()
                .map(AnexoResponseDTO::fromEntity)
                .toList();
        return SolicitacaoResponseDTO.fromEntity(solicitacao, anexos);
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO atualizarStatus(UUID id, StatusSolicitacao novoStatus) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        solicitacao.setStatus(novoStatus);

        if (novoStatus == StatusSolicitacao.CONCLUIDO || novoStatus == StatusSolicitacao.CANCELADO) {
            solicitacao.setDataFinalizacao(LocalDateTime.now());
        } else {
            solicitacao.setDataFinalizacao(null);
        }

        return SolicitacaoResponseDTO.fromEntity(solicitacaoRepository.save(solicitacao));
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO atualizarPrioridade(UUID id, PrioridadeSolicitacao novaPrioridade) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        solicitacao.setPrioridade(novaPrioridade);
        return SolicitacaoResponseDTO.fromEntity(solicitacaoRepository.save(solicitacao));
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO atribuirTecnico(UUID id, UUID tecnicoId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users tecnico = userRepository.findById(tecnicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Técnico não encontrado com ID: " + tecnicoId));

        solicitacao.setTecnicoResponsavel(tecnico);
        if (solicitacao.getStatus() == StatusSolicitacao.ABERTO) {
            solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        }

        return SolicitacaoResponseDTO.fromEntity(solicitacaoRepository.save(solicitacao));
    }

    private Solicitacao buscarEntidadePorId(UUID id) {
        return solicitacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada com ID: " + id));
    }
}