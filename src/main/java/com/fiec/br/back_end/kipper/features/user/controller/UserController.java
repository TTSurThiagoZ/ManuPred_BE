package com.fiec.br.back_end.kipper.features.user.controller;

import com.fiec.br.back_end.kipper.features.auth.models.dto.TokenResponseDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.*;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Gerenciamento de usuários e permissões")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Criar usuário manualmente",
            description = "Cria um usuário (uso administrativo).",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Usuário criado"),
                    @ApiResponse(responseCode = "400", description = "E-mail já cadastrado ou dados inválidos")
            }
    )
    @McpTool(name = "criar_usuario",
            description = "Cria um usuário manualmente (uso administrativo). Apenas ADMIN. "
                    + "Para mudar a role depois, use 'alterar_role_usuario'.",
            annotations = @McpTool.McpAnnotations(title = "Criar usuário",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    // CORREÇÃO: Adicionado PreAuthorize para restringir a ADMIN
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO createUser(
            @McpToolParam(description = "Nome, e-mail, senha (mínimo 6 caracteres) e, opcionalmente, firebaseUid")
            @RequestBody @Valid CreateUserRequestDTO dto) {
        return userService.createUser(dto);
    }

    // Sem @McpTool: o token vem do SDK do Firebase no app, um agente de IA não teria como obtê-lo.
    @Operation(
            summary = "Login via Firebase",
            description = "Autentica usando token do Firebase Auth.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token JWT do servidor retornado"),
                    @ApiResponse(responseCode = "401", description = "Token Firebase inválido")
            }
    )
    @PostMapping("/auth/firebase")
    public TokenResponseDTO authenticateWithFirebase(@RequestBody @Valid TokenRequestDTO dto) {
        return userService.verifyAndAuthenticateFirebaseToken(dto.token());
    }

    @Operation(
            summary = "Buscar usuário por ID (ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Usuário encontrado"),
                    @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
            }
    )
    @McpTool(name = "buscar_usuario",
            description = "Retorna os dados de um usuário pelo ID. Apenas ADMIN.",
            annotations = @McpTool.McpAnnotations(title = "Buscar usuário por ID",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public UserResponseDTO findById(
            @McpToolParam(description = "ID (UUID) do usuário") @PathVariable UUID id) {
        return userService.findById(id);
    }

    @Operation(summary = "Listar todos os usuários (ADMIN e SUPERVISOR)")
    @McpTool(name = "listar_usuarios",
            description = "Lista todos os usuários do sistema com id, nome, e-mail e role. "
                    + "Útil para descobrir o ID de técnicos e ajudantes. Apenas ADMIN e SUPERVISOR.",
            annotations = @McpTool.McpAnnotations(title = "Listar usuários",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @GetMapping
    public List<UserResponseDTO> findAll() {
        return userService.findAll();
    }

    @Operation(summary = "Deletar usuário (ADMIN)")
    @McpTool(name = "deletar_usuario",
            description = "Apaga um usuário de forma permanente. Apenas ADMIN. Ação irreversível: confirme com o usuário antes.",
            annotations = @McpTool.McpAnnotations(title = "Deletar usuário",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(
            @McpToolParam(description = "ID (UUID) do usuário a apagar") @PathVariable UUID id) {
        userService.deleteUser(id);
    }

    @Operation(
            summary = "Alterar role de um usuário (ADMIN)",
            description = "Permite trocar entre SUPER_ADMIN_ROLE, ADMIN_ROLE, SUPERVISOR_ROLE, FUNCIONARIO_ROLE e USER_ROLE. "
                    + "Conceder ou remover SUPER_ADMIN_ROLE exige ser SUPER_ADMIN."
    )
    @McpTool(name = "alterar_role_usuario",
            description = "Altera a role (permissão) de um usuário: SUPER_ADMIN_ROLE, ADMIN_ROLE, SUPERVISOR_ROLE, "
                    + "FUNCIONARIO_ROLE ou USER_ROLE. Apenas ADMIN; conceder ou remover SUPER_ADMIN_ROLE exige SUPER_ADMIN.",
            annotations = @McpTool.McpAnnotations(title = "Alterar role do usuário",
                    readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/role")
    public UserResponseDTO atualizarRole(
            @McpToolParam(description = "ID (UUID) do usuário") @PathVariable UUID id,
            @McpToolParam(description = "Nova role do usuário")
            @RequestBody @Valid RoleUpdateDTO dto) {
        return userService.atualizarRole(id, dto.role());
    }

    private static final String FORMATO_CSV = """
            Primeira linha = cabeçalho. Separador "," ou ";". Codificação UTF-8 (ou Windows-1252, como o Excel salva).
            Colunas (a ordem não importa, maiúsculas/minúsculas e acentos são ignorados):
            - email (obrigatória)
            - password ou senha (obrigatória, mínimo 6 caracteres)
            - status: DELETED, NOT_CONFIRMED ou CONFIRMED (vazio = NOT_CONFIRMED)
            - rm
            - setor
            - userType ou tipo: PROFESSOR ou COORDENADOR (pode ficar vazio)
            - nome (opcional; vazio = parte do e-mail antes do @)
            Todos entram com a role USER_ROLE. Máximo de 1000 linhas por arquivo.
            Linhas com erro (e-mail repetido ou já cadastrado, senha curta, valor inválido) não são criadas e
            aparecem em 'erros'; as demais são criadas normalmente.""";

    @Operation(
            summary = "Importar usuários por CSV (SUPER_ADMIN)",
            description = "Cria vários usuários de uma vez a partir de um arquivo CSV.\n\n" + FORMATO_CSV
                    + "\n\nExemplo:\n\n    email;password;status;rm;setor;userType\n"
                    + "    maria@escola.com;Senha123;CONFIRMED;12345;Informática;PROFESSOR",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Arquivo processado; veja 'criados' e 'erros'"),
                    @ApiResponse(responseCode = "400", description = "Arquivo vazio, cabeçalho inválido ou linhas demais"),
                    @ApiResponse(responseCode = "403", description = "Usuário não é SUPER_ADMIN")
            }
    )
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping(value = "/importar-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacaoUsuariosResponseDTO importarCsv(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Envie um arquivo CSV no campo 'arquivo'.");
        }
        return userService.importarUsuariosCsv(arquivo.getBytes());
    }

    /** Versão de {@link #importarCsv} para agentes de IA (MCP), que recebe o CSV como texto. */
    @McpTool(name = "importar_usuarios_csv",
            description = "Cria vários usuários de uma vez a partir do conteúdo de um CSV (texto). Apenas SUPER_ADMIN. "
                    + "Devolve os usuários criados e as linhas com erro.\n" + FORMATO_CSV,
            annotations = @McpTool.McpAnnotations(title = "Importar usuários por CSV",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ImportacaoUsuariosResponseDTO importarCsvTexto(
            @McpToolParam(description = "Conteúdo completo do CSV, com a linha de cabeçalho") String conteudoCsv) {
        if (conteudoCsv == null || conteudoCsv.isBlank()) {
            throw new IllegalArgumentException("O conteúdo do CSV está vazio.");
        }
        return userService.importarUsuariosCsv(conteudoCsv.getBytes(StandardCharsets.UTF_8));
    }

    private Users getUsuarioLogado() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Users u) {
            return u;
        }
        throw new AccessDeniedException("Usuário não autenticado.");
    }

    @Operation(summary = "Dados do usuário logado (id, email, nome, role)")
    @McpTool(name = "usuario_atual",
            description = "Retorna id, e-mail, nome e role do usuário autenticado nesta conexão MCP. "
                    + "Use para saber quais ferramentas ele tem permissão de usar.",
            annotations = @McpTool.McpAnnotations(title = "Usuário logado",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @GetMapping("/me")
    public UserMeDTO getMe() {
        Users user = getUsuarioLogado();
        return new UserMeDTO(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }

    // Sem @McpTool: o FCM token identifica o celular do usuário para notificações push,
    // não é algo que um agente de IA deva informar.
    @Operation(summary = "Atualizar FCM Token do celular")
    @PatchMapping("/me/fcm-token")
    public void atualizarFcmToken(@RequestBody @Valid FcmTokenRequestDTO dto) {
        Users user = getUsuarioLogado();
        userService.atualizarFcmToken(user.getEmail(), dto.fcmToken());
    }
}
