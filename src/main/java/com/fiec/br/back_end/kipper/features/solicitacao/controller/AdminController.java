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
import com.fiec.br.back_end.kipper.features.solicitacao.repositories.TipoChamadoRepository;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import com.fiec.br.back_end.kipper.model.service.AuditService; // <--- CORRIGIDO AQUI
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final AuditService auditService;

    private Users getUsuarioLogado() {
        return (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // ============ TIPOS DE CHAMADO ============
    @Operation(summary = "Listar tipos de chamado")
    @GetMapping("/tipos")
    public ResponseEntity<List<TipoChamado>> listarTipos() {
        return ResponseEntity.ok(tipoRepo.findAll());
    }

    @Operation(summary = "Criar tipo de chamado")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/tipos")
    public ResponseEntity<TipoChamado> criarTipo(@RequestBody TipoChamadoDTO dto) {
        if (tipoRepo.existsByNome(dto.nome())) throw new IllegalArgumentException("Tipo já existe.");
        TipoChamado t = TipoChamado.builder()
                .nome(dto.nome()).icone(dto.icone())
                .slaHoras(dto.slaHoras()).acaoAoEstourar(dto.acaoAoEstourar()).build();
        TipoChamado salvo = tipoRepo.save(t);
        auditService.registrar("CRIAR_TIPO", "Criou tipo de chamado: " + dto.nome(), getUsuarioLogado());
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @Operation(summary = "Atualizar tipo de chamado")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/tipos/{id}")
    public ResponseEntity<TipoChamado> atualizarTipo(@PathVariable UUID id, @RequestBody TipoChamadoDTO dto) {
        TipoChamado t = tipoRepo.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo não encontrado."));
        t.setNome(dto.nome()); t.setIcone(dto.icone());
        t.setSlaHoras(dto.slaHoras()); t.setAcaoAoEstourar(dto.acaoAoEstourar());
        auditService.registrar("EDITAR_TIPO", "Editou tipo: " + dto.nome(), getUsuarioLogado());
        return ResponseEntity.ok(tipoRepo.save(t));
    }

    @Operation(summary = "Deletar tipo de chamado")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/tipos/{id}")
    public ResponseEntity<Void> deletarTipo(@PathVariable UUID id) {
        TipoChamado t = tipoRepo.findById(id).orElse(null);
        tipoRepo.deleteById(id);
        auditService.registrar("DELETAR_TIPO",
                "Deletou tipo: " + (t != null ? t.getNome() : id), getUsuarioLogado());
        return ResponseEntity.noContent().build();
    }

    // ============ SETORES ============
    @Operation(summary = "Listar setores")
    @GetMapping("/setores")
    public ResponseEntity<List<Setor>> listarSetores() {
        return ResponseEntity.ok(setorRepo.findAll());
    }

    @Operation(summary = "Criar setor")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/setores")
    public ResponseEntity<Setor> criarSetor(@RequestBody SetorDTO dto) {
        if (setorRepo.existsByNome(dto.nome())) throw new IllegalArgumentException("Setor já existe.");
        Setor s = Setor.builder().nome(dto.nome()).build();
        if (dto.responsavelId() != null) {
            s.setResponsavel(userRepo.findById(dto.responsavelId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Responsável não encontrado.")));
        }
        Setor salvo = setorRepo.save(s);
        auditService.registrar("CRIAR_SETOR", "Criou setor: " + dto.nome(), getUsuarioLogado());
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @Operation(summary = "Deletar setor")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/setores/{id}")
    public ResponseEntity<Void> deletarSetor(@PathVariable UUID id) {
        Setor s = setorRepo.findById(id).orElse(null);
        setorRepo.deleteById(id);
        auditService.registrar("DELETAR_SETOR",
                "Deletou setor: " + (s != null ? s.getNome() : id), getUsuarioLogado());
        return ResponseEntity.noContent().build();
    }

    // ============ LOCAIS ============
    @Operation(summary = "Listar locais")
    @GetMapping("/locais")
    public ResponseEntity<List<Local>> listarLocais() {
        return ResponseEntity.ok(localRepo.findAll());
    }

    @Operation(summary = "Criar local")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/locais")
    public ResponseEntity<Local> criarLocal(@RequestBody LocalDTO dto) {
        Local l = Local.builder().nome(dto.nome()).build();
        if (dto.setorId() != null) {
            l.setSetor(setorRepo.findById(dto.setorId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Setor não encontrado.")));
        }
        Local salvo = localRepo.save(l);
        auditService.registrar("CRIAR_LOCAL", "Criou local: " + dto.nome(), getUsuarioLogado());
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @Operation(summary = "Deletar local")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/locais/{id}")
    public ResponseEntity<Void> deletarLocal(@PathVariable UUID id) {
        Local l = localRepo.findById(id).orElse(null);
        localRepo.deleteById(id);
        auditService.registrar("DELETAR_LOCAL",
                "Deletou local: " + (l != null ? l.getNome() : id), getUsuarioLogado());
        return ResponseEntity.noContent().build();
    }

    // ============ AUDITORIA ============
    @Operation(summary = "Listar logs de auditoria")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/auditoria")
    public ResponseEntity<List<com.fiec.br.back_end.kipper.model.entities.AuditLog>> listarAuditoria() { // <--- CORRIGIDO AQUI TAMBÉM
        return ResponseEntity.ok(auditService.listar());
    }
}