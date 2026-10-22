package com.fiec.br.back_end.kipper.features.solicitacao.service.impl;

import com.fiec.br.back_end.kipper.exception.RecursoNaoEncontradoException;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.*;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.*;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.*;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.specification.SolicitacaoSpecification;
import com.fiec.br.back_end.kipper.features.solicitacao.service.FileStorageService;
import com.fiec.br.back_end.kipper.features.solicitacao.service.SolicitacaoService;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.features.user.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SolicitacaoServiceImpl implements SolicitacaoService {

    private final SolicitacaoRepository solicitacaoRepository;
    private final AnexoRepository anexoRepository;
    private final UserRepository userRepository;
    private final TipoChamadoRepository tipoChamadoRepository;
    private final LocalRepository localRepository;
    private final ComentarioRepository comentarioRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final FileStorageService fileStorageService;
    private final NotificacaoService notificacaoService;

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

        TipoChamado tipo = tipoChamadoRepository.findById(dto.tipoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de chamado não encontrado."));

        Local local = dto.localId() != null
                ? localRepository.findById(dto.localId()).orElse(null)
                : null;

        Solicitacao solicitacao = Solicitacao.builder()
                .titulo(tipo.getNome() + " - " + dto.localizacaoProblema())
                .descricao(dto.descricao())
                .status(StatusSolicitacao.ABERTO)
                .prioridade(PrioridadeSolicitacao.MEDIA)
                .tipo(tipo)
                .local(local)
                .numeroPatrimonio(dto.numeroPatrimonio())
                .localizacaoProblema(dto.localizacaoProblema())
                .usuarioSolicitante(solicitante)
                .urgente(false)
                .build();

        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        registrarMovimentacao(salva, "Chamado criado",
                "Aberto por " + solicitante.getName(), solicitante);

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
        return paraRespostaComAnexos(buscarEntidadePorId(id));
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO atualizarStatus(UUID id, StatusSolicitacao novoStatus, String motivo, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = userRepository.findById(autorId).orElse(null);
        StatusSolicitacao antigo = solicitacao.getStatus();

        solicitacao.setStatus(novoStatus);

        if (novoStatus == StatusSolicitacao.CONCLUIDO) {
            solicitacao.setDataConclusao(LocalDateTime.now());
        } else if (novoStatus == StatusSolicitacao.FINALIZADA) {
            solicitacao.setDataFinalizacao(LocalDateTime.now());
        } else if (novoStatus == StatusSolicitacao.REJEITADA) {
            solicitacao.setMotivoRejeicao(motivo);
            solicitacao.setDataFinalizacao(LocalDateTime.now());
        }

        registrarMovimentacao(solicitacao, "Status alterado",
                antigo + " → " + novoStatus + (motivo != null ? " (" + motivo + ")" : ""), autor);

        if (solicitacao.getUsuarioSolicitante() != null
                && (novoStatus == StatusSolicitacao.CONCLUIDO
                || novoStatus == StatusSolicitacao.FINALIZADA
                || novoStatus == StatusSolicitacao.REJEITADA)
                && (autor == null || !autor.getId().equals(solicitacao.getUsuarioSolicitante().getId()))) {
            notificacaoService.enviar(solicitacao.getUsuarioSolicitante(),
                    "Atualização no seu chamado",
                    solicitacao.getTitulo() + " agora está: " + novoStatus,
                    solicitacao.getId());
        }

        return paraRespostaComAnexos(solicitacaoRepository.save(solicitacao));
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO atualizarPrioridade(UUID id, PrioridadeSolicitacao novaPrioridade, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = userRepository.findById(autorId).orElse(null);
        solicitacao.setPrioridade(novaPrioridade);
        registrarMovimentacao(solicitacao, "Prioridade alterada", "Nova prioridade: " + novaPrioridade, autor);
        return paraRespostaComAnexos(solicitacaoRepository.save(solicitacao));
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO atribuirTecnico(UUID id, UUID tecnicoId, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users tecnico = userRepository.findById(tecnicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Técnico não encontrado: " + tecnicoId));
        Users autor = userRepository.findById(autorId).orElse(null);

        solicitacao.setTecnicoResponsavel(tecnico);
        if (solicitacao.getStatus() == StatusSolicitacao.ABERTO) {
            solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        }
        registrarMovimentacao(solicitacao, "Chamado atribuído",
                autor != null ? autor.getName() + " atribuiu a " + tecnico.getName() : "Atribuído a " + tecnico.getName(), autor);

        notificacaoService.enviar(tecnico,
                "Novo chamado atribuído",
                "Você foi atribuído ao chamado: " + solicitacao.getTitulo(),
                solicitacao.getId());

        return paraRespostaComAnexos(solicitacaoRepository.save(solicitacao));
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO marcarUrgente(UUID id, boolean urgente, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = userRepository.findById(autorId).orElse(null);
        solicitacao.setUrgente(urgente);
        if (urgente) {
            solicitacao.setPrioridade(PrioridadeSolicitacao.URGENTE);
        }
        registrarMovimentacao(solicitacao, urgente ? "Marcado como urgente" : "Urgência removida",
                autor != null ? "Por " + autor.getName() : null, autor);

        if (urgente && solicitacao.getTecnicoResponsavel() != null) {
            notificacaoService.enviar(solicitacao.getTecnicoResponsavel(),
                    "Chamado marcado como URGENTE",
                    solicitacao.getTitulo(),
                    solicitacao.getId());
        }

        return paraRespostaComAnexos(solicitacaoRepository.save(solicitacao));
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO adicionarAjudante(UUID id, UUID ajudanteId, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users ajudante = userRepository.findById(ajudanteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ajudante não encontrado."));
        Users autor = userRepository.findById(autorId).orElse(null);

        if (solicitacao.getAjudantes() == null) solicitacao.setAjudantes(new ArrayList<>());
        if (solicitacao.getAjudantes().stream().noneMatch(u -> u.getId().equals(ajudanteId))) {
            solicitacao.getAjudantes().add(ajudante);
            registrarMovimentacao(solicitacao, "Ajudante adicionado", ajudante.getName(), autor);

            notificacaoService.enviar(ajudante,
                    "Você foi adicionado como ajudante",
                    solicitacao.getTitulo(),
                    solicitacao.getId());
        }
        return paraRespostaComAnexos(solicitacaoRepository.save(solicitacao));
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO removerAjudante(UUID id, UUID ajudanteId, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = userRepository.findById(autorId).orElse(null);
        if (solicitacao.getAjudantes() != null) {
            solicitacao.getAjudantes().removeIf(u -> u.getId().equals(ajudanteId));
            registrarMovimentacao(solicitacao, "Ajudante removido", null, autor);
        }
        return paraRespostaComAnexos(solicitacaoRepository.save(solicitacao));
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO concluir(UUID id, UUID autorId) {
        return atualizarStatus(id, StatusSolicitacao.CONCLUIDO, null, autorId);
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO validarFinalizacao(UUID id, UUID autorId) {
        return atualizarStatus(id, StatusSolicitacao.FINALIZADA, null, autorId);
    }

    @Override
    @Transactional
    public SolicitacaoResponseDTO rejeitar(UUID id, String motivo, UUID autorId) {
        return atualizarStatus(id, StatusSolicitacao.REJEITADA, motivo, autorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComentarioResponseDTO> listarComentarios(UUID solicitacaoId) {
        return comentarioRepository.findBySolicitacaoIdOrderByCreatedAtAsc(solicitacaoId)
                .stream().map(ComentarioResponseDTO::fromEntity).toList();
    }

    @Override
    @Transactional
    public ComentarioResponseDTO adicionarComentario(UUID solicitacaoId, String texto, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(solicitacaoId);
        Users autor = userRepository.findById(autorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Autor não encontrado."));
        Comentario c = Comentario.builder().texto(texto).autor(autor).solicitacao(solicitacao).build();
        registrarMovimentacao(solicitacao, "Comentário adicionado", autor.getName() + ": " + texto, autor);

        if (solicitacao.getUsuarioSolicitante() != null
                && !autor.getId().equals(solicitacao.getUsuarioSolicitante().getId())) {
            notificacaoService.enviar(solicitacao.getUsuarioSolicitante(),
                    "Novo comentário no seu chamado",
                    autor.getName() + ": " + texto,
                    solicitacao.getId());
        }

        return ComentarioResponseDTO.fromEntity(comentarioRepository.save(c));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimentacaoResponseDTO> listarMovimentacoes(UUID solicitacaoId) {
        return movimentacaoRepository.findBySolicitacaoIdOrderByCreatedAtDesc(solicitacaoId)
                .stream().map(MovimentacaoResponseDTO::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RelatorioDTO gerarRelatorio(LocalDateTime inicio, LocalDateTime fim) {
        List<Solicitacao> lista = (inicio != null && fim != null)
                ? solicitacaoRepository.findByPeriodo(inicio, fim)
                : solicitacaoRepository.findAll();

        long total = lista.size();
        long rejeitados = lista.stream().filter(s -> s.getStatus() == StatusSolicitacao.REJEITADA).count();

        double tempoMedio = lista.stream()
                .filter(s -> s.getDataFinalizacao() != null && s.getCreatedAt() != null)
                .mapToLong(s -> Duration.between(s.getCreatedAt(), s.getDataFinalizacao()).toHours())
                .average().orElse(0) / 24.0;

        Map<String, Long> contagemTipo = new HashMap<>();
        for (Solicitacao s : lista) {
            if (s.getTipo() != null) {
                contagemTipo.merge(s.getTipo().getNome(), 1L, Long::sum);
            }
        }
        String tipoMaisFreq = contagemTipo.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-");
        long tipoQtd = contagemTipo.getOrDefault(tipoMaisFreq, 0L);

        double percRej = total > 0 ? (rejeitados * 100.0 / total) : 0;

        return new RelatorioDTO(total, tempoMedio, tipoMaisFreq, tipoQtd, rejeitados, percRej);
    }

    private Solicitacao buscarEntidadePorId(UUID id) {
        return solicitacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada com ID: " + id));
    }

    private void registrarMovimentacao(Solicitacao s, String titulo, String descricao, Users autor) {
        Movimentacao m = Movimentacao.builder()
                .titulo(titulo).descricao(descricao).autor(autor).solicitacao(s).build();
        movimentacaoRepository.save(m);
    }

    // Método auxiliar novo para carregar os anexos
    private SolicitacaoResponseDTO paraRespostaComAnexos(Solicitacao solicitacao) {
        List<AnexoResponseDTO> anexos = anexoRepository.findBySolicitacaoId(solicitacao.getId()).stream()
                .map(AnexoResponseDTO::fromEntity).toList();
        return SolicitacaoResponseDTO.fromEntity(solicitacao, anexos);
    }
}