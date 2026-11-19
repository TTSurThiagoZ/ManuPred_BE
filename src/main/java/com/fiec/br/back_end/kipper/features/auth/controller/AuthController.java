package com.fiec.br.back_end.kipper.features.auth.controller;

import com.fiec.br.back_end.kipper.features.auth.models.dto.GoogleTokenRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.LoginRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.RegisterRequestDTO;
import com.fiec.br.back_end.kipper.features.auth.models.dto.TokenResponseDTO;
import com.fiec.br.back_end.kipper.features.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Login, registro e autenticação via Google")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Registrar novo usuário",
            description = "Cria uma conta com e-mail e senha. Role padrão é USER_ROLE.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Usuário registrado com sucesso"),
                    @ApiResponse(responseCode = "400", description = "E-mail já cadastrado ou dados inválidos")
            }
    )
    @McpTool(name = "registrar_usuario",
            description = "Cria uma nova conta de usuário com e-mail e senha. A conta nasce com a role USER_ROLE. "
                    + "Falha se o e-mail já estiver cadastrado.",
            annotations = @McpTool.McpAnnotations(title = "Registrar usuário",
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    @PostMapping("/register")
    public void register(
            @McpToolParam(description = "Nome, e-mail e senha do novo usuário")
            @RequestBody @Valid RegisterRequestDTO request) {
        authService.register(request);
    }

    @Operation(
            summary = "Login com email e senha",
            description = "Autentica o usuário e retorna um token JWT válido por 30 minutos.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequestDTO.class),
                            examples = {
                                    // Admin criado automaticamente na primeira inicialização (DataInitializer).
                                    // A senha real vem da variável ADMIN_PASSWORD; mantenha as duas iguais.
                                    @ExampleObject(
                                            name = "Admin",
                                            value = "{\"email\":\"admin@manupred.com\",\"password\":\"Admin@2026\"}"
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login realizado, token retornado"),
                    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos")
            }
    )
    @McpTool(name = "login",
            description = "Autentica com e-mail e senha e devolve um token JWT (válido por 30 minutos) "
                    + "para ser usado no cabeçalho Authorization: Bearer <token>.",
            annotations = @McpTool.McpAnnotations(title = "Login com e-mail e senha",
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    @PostMapping("/login")
    public TokenResponseDTO login(
            @McpToolParam(description = "E-mail e senha do usuário")
            @RequestBody @Valid LoginRequestDTO request) {
        return authService.login(request);
    }

    // Sem @McpTool: o idToken só é gerado pelo SDK do Google no celular/navegador,
    // então um agente de IA não teria como obtê-lo.
    @Operation(
            summary = "Login via Google Sign-In",
            description = "Recebe o idToken gerado pelo SDK do Google no mobile. Se o usuário não existir, cria automaticamente com USER_ROLE.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login realizado, token JWT do servidor retornado"),
                    @ApiResponse(responseCode = "401", description = "Token do Google inválido ou expirado")
            }
    )
    @PostMapping("/google")
    public TokenResponseDTO loginComGoogle(@RequestBody @Valid GoogleTokenRequestDTO request) {
        return authService.loginComGoogle(request);
    }
}
