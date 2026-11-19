package com.fiec.br.back_end.kipper.features.solicitacao.controller;

import com.fiec.br.back_end.kipper.exception.RecursoNaoEncontradoException;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.LocalDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.SetorDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.dto.TipoChamadoDTO;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Local;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.Setor;
import com.fiec.br.back_end.kipper.features.solicitacao.model.entities.TipoChamado;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.LocalRepository;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.SetorRepository;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.SolicitacaoRepository;
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.TipoChamadoRepository;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.model.entities.AuditLog;
import com.fiec.br.back_end.kipper.model.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Administração", description = "Gerenciamento de tipos, setores, locais e auditoria (Apenas ADMIN)")
public class AdminController {

    private final TipoChamadoRepository tipoRepo;
    private final SetorRepository setorRepo;
    private final LocalRepository localRepo;
    private final UserRepository userRepo;
    private final SolicitacaoRepository solicitacaoRepo;
    private final AuditService auditService;

    private Users getUsuarioLogado() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Users u) {
            return u;
        }
        throw new AccessDeniedException("Usuário não autenticado.");
    }

    // ============ TIPOS DE CHAMADO ============
    @Operation(summary = "Listar tipos de chamado")
    @McpTool(name = "listar_tipos_chamado",
            description = "Lista os tipos de chamado (ex.: elétrica, hidráulica) com id, nome, ícone, SLA em horas e a "
                    + "ação ao estourar o SLA. O 'id' é o 'tipoId' exigido por 'criar_chamado'.",
            annotations = @McpTool.McpAnnotations(title = "Listar tipos de chamado",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    // Liberado sem login: o app e o site listam os tipos antes do usuário entrar
    @PreAuthorize("permitAll()")
    @GetMapping("/tipos")
    public List<TipoChamado> listarTipos() {
        return tipoRepo.findAll();
    }

    @Operation(summary = "Criar tipo de chamado")
    @McpTool(name = "criar_tipo_chamado",
            description = "Cadastra um novo tipo de chamado. O nome deve ser único. Apenas ADMIN.",
            annotations = @McpTool.McpAnnotations(title = "Criar tipo de chamado",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/tipos")
    @ResponseStatus(HttpStatus.CREATED)
    public TipoChamado criarTipo(
            @McpToolParam(description = "Nome, ícone (opcional), SLA em horas (mínimo 1) e ação ao estourar o SLA (opcional)")
            @RequestBody @Valid TipoChamadoDTO dto) {
        String nome = dto.nome().trim();
        if (tipoRepo.existsByNome(nome)) throw new IllegalArgumentException("Tipo já existe.");
        TipoChamado t = TipoChamado.builder()
                .nome(nome).icone(dto.icone())
                .slaHoras(dto.slaHoras()).acaoAoEstourar(dto.acaoAoEstourar()).build();
        TipoChamado salvo = tipoRepo.save(t);
        auditService.registrar("CRIAR_TIPO", "Criou tipo de chamado: " + nome, getUsuarioLogado());
        return salvo;
    }

    @Operation(summary = "Atualizar tipo de chamado")
    @McpTool(name = "atualizar_tipo_chamado",
            description = "Altera nome, ícone, SLA e ação ao estourar de um tipo de chamado existente. "
                    + "Todos os campos são substituídos. Apenas ADMIN.",
            annotations = @McpTool.McpAnnotations(title = "Atualizar tipo de chamado",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/tipos/{id}")
    public TipoChamado atualizarTipo(
            @McpToolParam(description = "ID (UUID) do tipo de chamado") @PathVariable UUID id,
            @McpToolParam(description = "Novos dados do tipo de chamado")
            @RequestBody @Valid TipoChamadoDTO dto) {
        TipoChamado t = tipoRepo.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo não encontrado."));
        String nome = dto.nome().trim();
        if (!nome.equals(t.getNome()) && tipoRepo.existsByNome(nome)) {
            throw new IllegalArgumentException("Já existe outro tipo com este nome.");
        }
        t.setNome(nome); t.setIcone(dto.icone());
        t.setSlaHoras(dto.slaHoras()); t.setAcaoAoEstourar(dto.acaoAoEstourar());
        TipoChamado salvo = tipoRepo.save(t);
        auditService.registrar("EDITAR_TIPO", "Editou tipo: " + nome, getUsuarioLogado());
        return salvo;
    }

    @Operation(summary = "Deletar tipo de chamado")
    @McpTool(name = "deletar_tipo_chamado",
            description = "Apaga um tipo de chamado. Falha se algum chamado usar este tipo. Apenas ADMIN. "
                    + "Ação irreversível: confirme com o usuário antes.",
            annotations = @McpTool.McpAnnotations(title = "Deletar tipo de chamado",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/tipos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletarTipo(
            @McpToolParam(description = "ID (UUID) do tipo de chamado") @PathVariable UUID id) {
        TipoChamado t = tipoRepo.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo não encontrado."));
        // Os chamados guardam só uma referência ao tipo; apagar deixaria chamados sem tipo e sem SLA.
        if (solicitacaoRepo.existsByTipoId(id)) {
            throw new IllegalArgumentException("Não é possível deletar: existem chamados usando este tipo.");
        }
        tipoRepo.deleteById(id);
        auditService.registrar("DELETAR_TIPO", "Deletou tipo: " + t.getNome(), getUsuarioLogado());
    }

    // ============ SETORES ============
    @Operation(summary = "Listar setores")
    @McpTool(name = "listar_setores",
            description = "Lista os setores (áreas do prédio/empresa) com id, nome e responsável.",
            annotations = @McpTool.McpAnnotations(title = "Listar setores",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @GetMapping("/setores")
    public List<Setor> listarSetores() {
        return setorRepo.findAll();
    }

    @Operation(summary = "Criar setor")
    @McpTool(name = "criar_setor",
            description = "Cadastra um novo setor, opcionalmente com um usuário responsável. O nome deve ser único. Apenas ADMIN.",
            annotations = @McpTool.McpAnnotations(title = "Criar setor",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/setores")
    @ResponseStatus(HttpStatus.CREATED)
    public Setor criarSetor(
            @McpToolParam(description = "Nome do setor e, opcionalmente, o ID (UUID) do usuário responsável")
            @RequestBody @Valid SetorDTO dto) {
        String nome = dto.nome().trim();
        if (setorRepo.existsByNome(nome)) throw new IllegalArgumentException("Setor já existe.");
        Setor s = Setor.builder().nome(nome).build();
        if (dto.responsavelId() != null) {
            s.setResponsavel(userRepo.findById(dto.responsavelId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Responsável não encontrado.")));
        }
        Setor salvo = setorRepo.save(s);
        auditService.registrar("CRIAR_SETOR", "Criou setor: " + nome, getUsuarioLogado());
        return salvo;
    }

    @Operation(summary = "Deletar setor")
    @McpTool(name = "deletar_setor",
            description = "Apaga um setor. Falha se houver locais cadastrados nele. Apenas ADMIN. "
                    + "Ação irreversível: confirme com o usuário antes.",
            annotations = @McpTool.McpAnnotations(title = "Deletar setor",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/setores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletarSetor(
            @McpToolParam(description = "ID (UUID) do setor") @PathVariable UUID id) {
        Setor s = setorRepo.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Setor não encontrado."));
        if (!localRepo.findBySetorId(id).isEmpty()) {
            throw new IllegalArgumentException("Não é possível deletar: existem locais cadastrados neste setor.");
        }
        setorRepo.deleteById(id);
        auditService.registrar("DELETAR_SETOR", "Deletou setor: " + s.getNome(), getUsuarioLogado());
    }

    // ============ LOCAIS ============
    @Operation(summary = "Listar locais")
    @McpTool(name = "listar_locais",
            description = "Lista os locais (salas, laboratórios, blocos) com id, nome e setor. "
                    + "O 'id' é o 'localId' aceito por 'criar_chamado' e 'buscar_chamados'.",
            annotations = @McpTool.McpAnnotations(title = "Listar locais",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @GetMapping("/locais")
    public List<Local> listarLocais() {
        return localRepo.findAll();
    }

    @Operation(summary = "Criar local")
    @McpTool(name = "criar_local",
            description = "Cadastra um novo local, opcionalmente ligado a um setor. Apenas ADMIN.",
            annotations = @McpTool.McpAnnotations(title = "Criar local",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/locais")
    @ResponseStatus(HttpStatus.CREATED)
    public Local criarLocal(
            @McpToolParam(description = "Nome do local e, opcionalmente, o ID (UUID) do setor")
            @RequestBody @Valid LocalDTO dto) {
        String nome = dto.nome().trim();
        Local l = Local.builder().nome(nome).build();
        if (dto.setorId() != null) {
            l.setSetor(setorRepo.findById(dto.setorId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Setor não encontrado.")));
        }
        Local salvo = localRepo.save(l);
        auditService.registrar("CRIAR_LOCAL", "Criou local: " + nome, getUsuarioLogado());
        return salvo;
    }

    @Operation(summary = "Deletar local")
    @McpTool(name = "deletar_local",
            description = "Apaga um local. Falha se houver chamados nele. Apenas ADMIN. "
                    + "Ação irreversível: confirme com o usuário antes.",
            annotations = @McpTool.McpAnnotations(title = "Deletar local",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/locais/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletarLocal(
            @McpToolParam(description = "ID (UUID) do local") @PathVariable UUID id) {
        Local l = localRepo.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Local não encontrado."));
        if (solicitacaoRepo.existsByLocalId(id)) {
            throw new IllegalArgumentException("Não é possível deletar: existem chamados neste local.");
        }
        localRepo.deleteById(id);
        auditService.registrar("DELETAR_LOCAL", "Deletou local: " + l.getNome(), getUsuarioLogado());
    }

    // ============ AUDITORIA ============
    @Operation(summary = "Listar logs de auditoria")
    @McpTool(name = "listar_auditoria",
            description = "Lista o log de auditoria das ações administrativas (quem fez o quê e quando). Apenas ADMIN.",
            annotations = @McpTool.McpAnnotations(title = "Logs de auditoria",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/auditoria")
    public List<AuditLog> listarAuditoria() {
        return auditService.listar();
    }
}
