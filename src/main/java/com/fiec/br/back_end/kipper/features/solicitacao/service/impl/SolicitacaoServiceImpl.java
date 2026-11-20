package com.fiec.br.back_end.kipper.features.solicitacao.service.impl;

import com.fiec.br.back_end.kipper.exception.RecursoNaoEncontradoException;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.*;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.*;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.PrioridadeSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.model.enums.StatusSolicitacao;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.*;
import com.fiec.br.back_end.kipper.features.solicitacao.service.FileStorageService;
import com.fiec.br.back_end.kipper.features.solicitacao.service.SolicitacaoService;
import com.fiec.br.back_end.kipper.features.user.model.entities.UserRole;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.features.user.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitacaoServiceImpl implements SolicitacaoService {

    private static final Set<StatusSolicitacao> STATUS_FINAIS = EnumSet.of(
            StatusSolicitacao.FINALIZADA, StatusSolicitacao.REJEITADA, StatusSolicitacao.CANCELADO);

    private static final Set<StatusSolicitacao> STATUS_QUE_NOTIFICAM = EnumSet.of(
            StatusSolicitacao.CONCLUIDO, StatusSolicitacao.FINALIZADA,
            StatusSolicitacao.REJEITADA, StatusSolicitacao.CANCELADO);

    private final SolicitacaoRepository solicitacaoRepository;
    private final AnexoRepository anexoRepository;
    private final UserRepository userRepository;
    private final TipoChamadoRepository tipoChamadoRepository;
    private final LocalRepository localRepository;
    private final ComentarioRepository comentarioRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final FileStorageService fileStorageService;
    private final NotificacaoService notificacaoService;
    private final MongoTemplate mongoTemplate;

    // =====================================================================
    // BUSCA
    // =====================================================================

    @Override
    public Page<SolicitacaoResponseDTO> search(SolicitacaoSearchFilterDTO filtro, Pageable pageable) {
        Query query = new Query();
        List<Criteria> criterias = new ArrayList<>();

        if (filtro.id() != null) criterias.add(Criteria.where("id").is(filtro.id()));
        if (temTexto(filtro.termo())) {
            criterias.add(new Criteria().orOperator(
                    Criteria.where("titulo").regex(textoLiteral(filtro.termo()), "i"),
                    Criteria.where("descricao").regex(textoLiteral(filtro.termo()), "i")
            ));
        }
        if (filtro.status() != null) criterias.add(Criteria.where("status").is(filtro.status()));
        if (filtro.prioridade() != null) criterias.add(Criteria.where("prioridade").is(filtro.prioridade()));
        if (temTexto(filtro.numeroPatrimonio())) criterias.add(Criteria.where("numeroPatrimonio").regex(textoLiteral(filtro.numeroPatrimonio()), "i"));
        if (temTexto(filtro.localizacaoProblema())) criterias.add(Criteria.where("localizacaoProblema").regex(textoLiteral(filtro.localizacaoProblema()), "i"));

        if (filtro.tipoId() != null) criterias.add(Criteria.where("tipo.$id").is(filtro.tipoId()));
        if (filtro.localId() != null) criterias.add(Criteria.where("local.$id").is(filtro.localId()));
        if (filtro.setorId() != null) {
            List<UUID> locaisDoSetor = localRepository.findBySetorId(filtro.setorId()).stream()
                    .map(Local::getId).toList();
            criterias.add(Criteria.where("local.$id").in(locaisDoSetor));
        }
        if (filtro.urgente() != null) criterias.add(Criteria.where("urgente").is(filtro.urgente()));

        if (filtro.usuarioSolicitanteIds() != null && !filtro.usuarioSolicitanteIds().isEmpty()) criterias.add(Criteria.where("usuarioSolicitante.$id").in(filtro.usuarioSolicitanteIds()));
        if (filtro.tecnicoResponsavelIds() != null && !filtro.tecnicoResponsavelIds().isEmpty()) criterias.add(Criteria.where("tecnicoResponsavel.$id").in(filtro.tecnicoResponsavelIds()));

        if (filtro.dataAberturaInicio() != null) criterias.add(Criteria.where("createdAt").gte(filtro.dataAberturaInicio()));
        if (filtro.dataAberturaFim() != null) criterias.add(Criteria.where("createdAt").lte(filtro.dataAberturaFim()));
        if (filtro.dataFinalizacaoInicio() != null) criterias.add(Criteria.where("dataFinalizacao").gte(filtro.dataFinalizacaoInicio()));
        if (filtro.dataFinalizacaoFim() != null) criterias.add(Criteria.where("dataFinalizacao").lte(filtro.dataFinalizacaoFim()));

        if (!criterias.isEmpty()) query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));

        long total = mongoTemplate.count(query, Solicitacao.class);
        query.with(pageable);
        List<Solicitacao> solicitacoes = mongoTemplate.find(query, Solicitacao.class);
        List<SolicitacaoResponseDTO> dtos = solicitacoes.stream().map(SolicitacaoResponseDTO::fromEntity).toList();
        return new PageImpl<>(dtos, pageable, total);
    }

    private boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }

    private String textoLiteral(String texto) {
        return Pattern.quote(texto.trim());
    }

    // =====================================================================
    // CRIAÇÃO
    // =====================================================================

    @Override
    public SolicitacaoResponseDTO create(CreateSolicitacaoRequestDTO dto, UUID usuarioSolicitanteId, List<MultipartFile> anexos) {
        if (dto.tipoId() == null) throw new IllegalArgumentException("O tipo do chamado é obrigatório.");

        Users solicitante = buscarUsuario(usuarioSolicitanteId, "Usuário solicitante não encontrado.");
        TipoChamado tipo = tipoChamadoRepository.findById(dto.tipoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de chamado não encontrado."));
        Local local = dto.localId() != null
                ? localRepository.findById(dto.localId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Local não encontrado."))
                : null;

        List<MultipartFile> arquivos = anexos == null ? List.of()
                : anexos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        arquivos.forEach(fileStorageService::validar);

        Solicitacao solicitacao = Solicitacao.builder()
                .titulo(tipo.getNome() + " - " + dto.localizacaoProblema())
                .descricao(dto.descricao()).status(StatusSolicitacao.ABERTO).prioridade(PrioridadeSolicitacao.MEDIA)
                .tipo(tipo).local(local).numeroPatrimonio(dto.numeroPatrimonio()).localizacaoProblema(dto.localizacaoProblema())
                .usuarioSolicitante(solicitante).urgente(false).build();

        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        registrarMovimentacao(salva, "Chamado criado", "Aberto por " + solicitante.getName(), solicitante);

        List<AnexoResponseDTO> anexosSalvos = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            String caminho = fileStorageService.salvar(salva.getId(), arquivo);
            Anexo anexo = Anexo.builder()
                    .nomeArquivo(arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "arquivo")
                    .caminhoArmazenado(caminho).tipoConteudo(arquivo.getContentType()).solicitacao(salva).build();
            anexosSalvos.add(AnexoResponseDTO.fromEntity(anexoRepository.save(anexo)));
        }
        return SolicitacaoResponseDTO.fromEntity(salva, anexosSalvos);
    }

    @Override
    public SolicitacaoResponseDTO buscarPorId(UUID id) { return paraRespostaComAnexos(buscarEntidadePorId(id)); }

    // =====================================================================
    // EXCLUSÃO
    // =====================================================================

    /**
     * Apaga o chamado de vez, junto com comentários, histórico e anexos.
     *  - ADMIN: qualquer chamado, em qualquer status;
     *  - solicitante: só o próprio chamado e só enquanto estiver ABERTO.
     *
     * A checagem de "é o dono?" é feita direto no banco com mongoTemplate.exists, sem
     * depender de o @DBRef usuarioSolicitante ter vindo carregado. Antes, se o DBRef viesse
     * nulo (por inconsistência do banco), o dono recebia 403 ao tentar apagar o próprio chamado.
     */
    @Override
    public void deletar(UUID id, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");

        if (!isAdmin(autor)) {
            Query queryDono = new Query(Criteria.where("_id").is(id)
                    .and("usuarioSolicitante.$id").is(autorId));
            boolean ehDono = mongoTemplate.exists(queryDono, Solicitacao.class);
            if (!ehDono) {
                throw new AccessDeniedException("Só quem abriu o chamado ou um ADMIN pode excluí-lo.");
            }
            if (solicitacao.getStatus() != StatusSolicitacao.ABERTO) {
                throw new IllegalArgumentException("Só é possível excluir um chamado que ainda está ABERTO. "
                        + "Este já está " + solicitacao.getStatus() + ".");
            }
        }

        log.info("Excluindo chamado {} por {} ({})", id, autor.getEmail(), autor.getRole());

        mongoTemplate.remove(new Query(Criteria.where("solicitacao.$id").is(id)), Comentario.class);
        mongoTemplate.remove(new Query(Criteria.where("solicitacao.$id").is(id)), Movimentacao.class);
        mongoTemplate.remove(new Query(Criteria.where("solicitacao.$id").is(id)), Anexo.class);
        fileStorageService.apagarArquivos(id);

        solicitacaoRepository.deleteById(id);
    }

    // =====================================================================
    // STATUS
    // =====================================================================

    @Override
    public SolicitacaoResponseDTO atualizarStatus(UUID id, StatusSolicitacao novoStatus, String motivo, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");

        if (autor.getRole() == UserRole.USER_ROLE) {
            boolean ehDono = solicitacao.getUsuarioSolicitante() != null && solicitacao.getUsuarioSolicitante().getId().equals(autor.getId());
            if (!ehDono) {
                throw new AccessDeniedException("Você só pode cancelar seus próprios chamados.");
            }
            if (novoStatus != StatusSolicitacao.CANCELADO) {
                throw new AccessDeniedException("Usuários só podem alterar o status de um chamado para CANCELADO.");
            }
        } else {
            verificarPodeAtuar(autor, solicitacao);
        }

        if (novoStatus == StatusSolicitacao.FINALIZADA && !isAdmin(autor)) {
            throw new AccessDeniedException("A finalização é feita pelo solicitante, ao validar o chamado.");
        }
        return mudarStatus(solicitacao, novoStatus, motivo, autor);
    }

    @Override
    public SolicitacaoResponseDTO concluir(UUID id, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");
        verificarPodeAtuar(autor, solicitacao);
        return mudarStatus(solicitacao, StatusSolicitacao.CONCLUIDO, null, autor);
    }

    @Override
    public SolicitacaoResponseDTO validarFinalizacao(UUID id, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");
        return mudarStatus(solicitacao, StatusSolicitacao.FINALIZADA, null, autor);
    }

    @Override
    public SolicitacaoResponseDTO rejeitar(UUID id, String motivo, UUID autorId) {
        if (!temTexto(motivo)) throw new IllegalArgumentException("Informe o motivo da rejeição.");
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");
        return mudarStatus(solicitacao, StatusSolicitacao.REJEITADA, motivo.trim(), autor);
    }

    private SolicitacaoResponseDTO mudarStatus(Solicitacao solicitacao, StatusSolicitacao novoStatus, String motivo, Users autor) {
        StatusSolicitacao antigo = solicitacao.getStatus();
        validarTransicao(antigo, novoStatus, autor);

        LocalDateTime agora = LocalDateTime.now();
        solicitacao.setStatus(novoStatus);
        switch (novoStatus) {
            case CONCLUIDO -> solicitacao.setDataConclusao(agora);
            case FINALIZADA -> solicitacao.setDataFinalizacao(agora);
            case REJEITADA -> {
                solicitacao.setMotivoRejeicao(motivo);
                solicitacao.setDataFinalizacao(agora);
            }
            case CANCELADO -> solicitacao.setDataFinalizacao(agora);
            default -> {
                solicitacao.setDataConclusao(null);
                solicitacao.setDataFinalizacao(null);
                solicitacao.setMotivoRejeicao(null);
                solicitacao.setSlaEstourado(false);
            }
        }

        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        registrarMovimentacao(salva, "Status alterado",
                antigo + " → " + novoStatus + (temTexto(motivo) ? " (" + motivo.trim() + ")" : ""), autor);

        if (STATUS_QUE_NOTIFICAM.contains(novoStatus)) {
            notificarEnvolvidos(salva, autor, "Atualização no chamado", salva.getTitulo() + " agora está: " + novoStatus);
        }
        return paraRespostaComAnexos(salva);
    }

    private void validarTransicao(StatusSolicitacao antigo, StatusSolicitacao novo, Users autor) {
        if (novo == null) throw new IllegalArgumentException("O novo status é obrigatório.");
        if (antigo == novo) {
            throw new IllegalArgumentException("O chamado já está com o status " + novo + ".");
        }
        if (antigo != null && STATUS_FINAIS.contains(antigo) && !isAdmin(autor)) {
            throw new IllegalArgumentException("Este chamado já foi encerrado (" + antigo + "). Apenas um ADMIN pode reabri-lo.");
        }
        if (novo == StatusSolicitacao.CONCLUIDO && antigo == StatusSolicitacao.ABERTO) {
            throw new IllegalArgumentException("O chamado precisa estar em andamento antes de ser concluído.");
        }
        if (novo == StatusSolicitacao.FINALIZADA && antigo != StatusSolicitacao.CONCLUIDO) {
            throw new IllegalArgumentException("Só é possível finalizar um chamado que está CONCLUIDO.");
        }
    }

    // =====================================================================
    // PRIORIDADE, TÉCNICO, URGÊNCIA, AJUDANTES
    // =====================================================================

    @Override
    public SolicitacaoResponseDTO atualizarPrioridade(UUID id, PrioridadeSolicitacao novaPrioridade, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");
        solicitacao.setPrioridade(novaPrioridade);
        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        registrarMovimentacao(salva, "Prioridade alterada", "Nova prioridade: " + novaPrioridade, autor);
        return paraRespostaComAnexos(salva);
    }

    @Override
    public SolicitacaoResponseDTO atribuirTecnico(UUID id, UUID tecnicoId, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users tecnico = buscarUsuario(tecnicoId, "Técnico não encontrado: " + tecnicoId);
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");

        verificarChamadoEmAberto(solicitacao);
        if (!isEquipe(tecnico)) {
            throw new IllegalArgumentException(tecnico.getName() + " não é funcionário e não pode ser técnico de um chamado.");
        }

        solicitacao.setTecnicoResponsavel(tecnico);
        if (solicitacao.getStatus() == StatusSolicitacao.ABERTO) solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        Solicitacao salva = solicitacaoRepository.save(solicitacao);

        registrarMovimentacao(salva, "Chamado atribuído", autor.getName() + " atribuiu a " + tecnico.getName(), autor);
        if (!tecnico.getId().equals(autor.getId())) {
            notificacaoService.enviar(tecnico, "Novo chamado atribuído", "Você foi atribuído ao chamado: " + salva.getTitulo(), salva.getId());
        }
        return paraRespostaComAnexos(salva);
    }

    @Override
    public SolicitacaoResponseDTO marcarUrgente(UUID id, boolean urgente, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");
        solicitacao.setUrgente(urgente);
        if (urgente) {
            solicitacao.setPrioridade(PrioridadeSolicitacao.URGENTE);
        } else if (solicitacao.getPrioridade() == PrioridadeSolicitacao.URGENTE) {
            solicitacao.setPrioridade(PrioridadeSolicitacao.ALTA);
        }
        Solicitacao salva = solicitacaoRepository.save(solicitacao);

        registrarMovimentacao(salva, urgente ? "Marcado como urgente" : "Urgência removida", "Por " + autor.getName(), autor);
        if (urgente && salva.getTecnicoResponsavel() != null && !salva.getTecnicoResponsavel().getId().equals(autor.getId())) {
            notificacaoService.enviar(salva.getTecnicoResponsavel(), "Chamado marcado como URGENTE", salva.getTitulo(), salva.getId());
        }
        return paraRespostaComAnexos(salva);
    }

    @Override
    public SolicitacaoResponseDTO adicionarAjudante(UUID id, UUID ajudanteId, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users ajudante = buscarUsuario(ajudanteId, "Ajudante não encontrado.");
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");

        verificarPodeGerenciarAjudantes(autor, solicitacao);
        verificarChamadoEmAberto(solicitacao);
        if (!isEquipe(ajudante)) {
            throw new IllegalArgumentException(ajudante.getName() + " não é funcionário e não pode ser ajudante.");
        }
        if (solicitacao.getTecnicoResponsavel() != null && solicitacao.getTecnicoResponsavel().getId().equals(ajudanteId)) {
            throw new IllegalArgumentException(ajudante.getName() + " já é o técnico responsável por este chamado.");
        }

        if (solicitacao.getAjudantes() == null) solicitacao.setAjudantes(new ArrayList<>());
        boolean jaEhAjudante = solicitacao.getAjudantes().stream().anyMatch(u -> u != null && u.getId().equals(ajudanteId));
        if (jaEhAjudante) return paraRespostaComAnexos(solicitacao);

        solicitacao.getAjudantes().add(ajudante);
        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        registrarMovimentacao(salva, "Ajudante adicionado", ajudante.getName(), autor);
        if (!ajudante.getId().equals(autor.getId())) {
            notificacaoService.enviar(ajudante, "Você foi adicionado como ajudante", salva.getTitulo(), salva.getId());
        }
        return paraRespostaComAnexos(salva);
    }

    @Override
    public SolicitacaoResponseDTO removerAjudante(UUID id, UUID ajudanteId, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(id);
        Users autor = buscarUsuario(autorId, "Usuário não encontrado.");
        verificarPodeGerenciarAjudantes(autor, solicitacao);

        if (solicitacao.getAjudantes() == null) return paraRespostaComAnexos(solicitacao);

        Optional<Users> removido = solicitacao.getAjudantes().stream()
                .filter(u -> u != null && u.getId().equals(ajudanteId)).findFirst();
        if (removido.isEmpty()) return paraRespostaComAnexos(solicitacao);

        solicitacao.getAjudantes().removeIf(u -> u != null && u.getId().equals(ajudanteId));
        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        registrarMovimentacao(salva, "Ajudante removido", removido.get().getName(), autor);
        return paraRespostaComAnexos(salva);
    }

    // =====================================================================
    // COMENTÁRIOS E HISTÓRICO
    // =====================================================================

    @Override
    public List<ComentarioResponseDTO> listarComentarios(UUID solicitacaoId) {
        return comentarioRepository.findBySolicitacaoIdOrderByCreatedAtAsc(solicitacaoId).stream().map(ComentarioResponseDTO::fromEntity).toList();
    }

    @Override
    public ComentarioResponseDTO adicionarComentario(UUID solicitacaoId, String texto, UUID autorId) {
        Solicitacao solicitacao = buscarEntidadePorId(solicitacaoId);
        Users autor = buscarUsuario(autorId, "Autor não encontrado.");
        String textoLimpo = texto.trim();

        Comentario salvo = comentarioRepository.save(
                Comentario.builder().texto(textoLimpo).autor(autor).solicitacao(solicitacao).build());
        registrarMovimentacao(solicitacao, "Comentário adicionado", autor.getName() + ": " + textoLimpo, autor);

        notificarEnvolvidos(solicitacao, autor, "Novo comentário no chamado", autor.getName() + ": " + textoLimpo);
        return ComentarioResponseDTO.fromEntity(salvo);
    }

    @Override
    public List<MovimentacaoResponseDTO> listarMovimentacoes(UUID solicitacaoId) {
        return movimentacaoRepository.findBySolicitacaoIdOrderByCreatedAtDesc(solicitacaoId).stream().map(MovimentacaoResponseDTO::fromEntity).toList();
    }

    // =====================================================================
    // RELATÓRIO
    // =====================================================================

    @Override
    public RelatorioDTO gerarRelatorio(LocalDateTime inicio, LocalDateTime fim) {
        Query query = new Query();
        if (inicio != null || fim != null) {
            Criteria periodo = Criteria.where("createdAt");
            if (inicio != null) periodo = periodo.gte(inicio);
            if (fim != null) periodo = periodo.lte(fim);
            query.addCriteria(periodo);
        }
        List<Solicitacao> lista = mongoTemplate.find(query, Solicitacao.class);

        long total = lista.size();
        long rejeitados = lista.stream().filter(s -> s.getStatus() == StatusSolicitacao.REJEITADA).count();

        double tempoMedioDias = lista.stream()
                .filter(s -> s.getStatus() == StatusSolicitacao.FINALIZADA
                        && s.getDataFinalizacao() != null && s.getCreatedAt() != null)
                .mapToLong(s -> Duration.between(s.getCreatedAt(), s.getDataFinalizacao()).toMinutes())
                .average().orElse(0) / (60.0 * 24.0);

        Map<String, Long> contagemTipo = new HashMap<>();
        for (Solicitacao s : lista) { if (s.getTipo() != null) contagemTipo.merge(s.getTipo().getNome(), 1L, Long::sum); }
        String tipoMaisFreq = contagemTipo.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-");
        long tipoQtd = contagemTipo.getOrDefault(tipoMaisFreq, 0L);
        double percRej = total > 0 ? (rejeitados * 100.0 / total) : 0;
        return new RelatorioDTO(total, tempoMedioDias, tipoMaisFreq, tipoQtd, rejeitados, percRej);
    }

    // =====================================================================
    // AUXILIARES
    // =====================================================================

    private Solicitacao buscarEntidadePorId(UUID id) {
        return solicitacaoRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada com ID: " + id));
    }

    private Users buscarUsuario(UUID id, String mensagemSeNaoExistir) {
        return userRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException(mensagemSeNaoExistir));
    }

    private boolean isAdmin(Users u) {
        return u.getRole().ehAdmin();
    }

    private boolean isSupervisorOuAdmin(Users u) {
        return u.getRole().ehSupervisor();
    }

    private boolean isEquipe(Users u) {
        return u.getRole().ehFuncionario();
    }

    private void verificarPodeAtuar(Users autor, Solicitacao s) {
        if (isSupervisorOuAdmin(autor)) return;

        boolean ehTecnico = s.getTecnicoResponsavel() != null && s.getTecnicoResponsavel().getId().equals(autor.getId());
        boolean ehAjudante = s.getAjudantes() != null
                && s.getAjudantes().stream().anyMatch(a -> a != null && a.getId().equals(autor.getId()));

        if (autor.getRole() != UserRole.FUNCIONARIO_ROLE || (!ehTecnico && !ehAjudante)) {
            throw new AccessDeniedException("Você só pode alterar chamados em que é o técnico responsável ou ajudante.");
        }
    }

    private void verificarPodeGerenciarAjudantes(Users autor, Solicitacao s) {
        if (isSupervisorOuAdmin(autor)) return;

        boolean ehTecnico = s.getTecnicoResponsavel() != null && s.getTecnicoResponsavel().getId().equals(autor.getId());
        if (!ehTecnico) {
            throw new AccessDeniedException("Apenas o supervisor ou o técnico responsável podem gerenciar ajudantes.");
        }
    }

    private void verificarChamadoEmAberto(Solicitacao s) {
        if (s.getStatus() != null && STATUS_FINAIS.contains(s.getStatus())) {
            throw new IllegalArgumentException("Este chamado já foi encerrado (" + s.getStatus() + ").");
        }
    }

    private void notificarEnvolvidos(Solicitacao s, Users autor, String titulo, String corpo) {
        Set<UUID> jaAvisados = new HashSet<>();
        if (autor != null) jaAvisados.add(autor.getId());

        for (Users destinatario : Arrays.asList(s.getUsuarioSolicitante(), s.getTecnicoResponsavel())) {
            if (destinatario != null && jaAvisados.add(destinatario.getId())) {
                notificacaoService.enviar(destinatario, titulo, corpo, s.getId());
            }
        }
    }

    private void registrarMovimentacao(Solicitacao s, String titulo, String descricao, Users autor) {
        Movimentacao m = Movimentacao.builder().titulo(titulo).descricao(descricao).autor(autor).solicitacao(s).build();
        movimentacaoRepository.save(m);
    }

    private SolicitacaoResponseDTO paraRespostaComAnexos(Solicitacao solicitacao) {
        List<AnexoResponseDTO> anexos = anexoRepository.findBySolicitacaoId(solicitacao.getId()).stream().map(AnexoResponseDTO::fromEntity).toList();
        return SolicitacaoResponseDTO.fromEntity(solicitacao, anexos);
    }
}