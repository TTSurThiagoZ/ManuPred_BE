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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

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
    public SolicitacaoResponseDTO create(CreateSolicitacaoRequestDTO dto, List<MultipartFile> anexos) {
        Users solicitante = userRepository.findById(dto.usuarioSolicitanteId())
                .orElseThrow(() -> new RuntimeException("Usuário solicitante não encontrado."));

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
}
