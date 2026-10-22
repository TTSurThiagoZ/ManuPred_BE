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
import org.springframework.http.ResponseEntity;
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
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Valid RegisterRequestDTO request) {
        authService.register(request);
        return ResponseEntity.ok().build();
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
                                    @ExampleObject(
                                            name = "Exemplo Admin",
                                            value = "{\"email\":\"admin@teste.com\",\"password\":\"senha123\"}"
                                    ),
                                    @ExampleObject(
                                            name = "Exemplo Usuário",
                                            value = "{\"email\":\"carlos@teste.com\",\"password\":\"senha123\"}"
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login realizado, token retornado"),
                    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos")
            }
    )
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "Login via Google Sign-In",
            description = "Recebe o idToken gerado pelo SDK do Google no mobile. Se o usuário não existir, cria automaticamente com USER_ROLE.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login realizado, token JWT do servidor retornado"),
                    @ApiResponse(responseCode = "401", description = "Token do Google inválido ou expirado")
            }
    )
    @PostMapping("/google")
    public ResponseEntity<TokenResponseDTO> loginComGoogle(@RequestBody @Valid GoogleTokenRequestDTO request) {
        return ResponseEntity.ok(authService.loginComGoogle(request));
    }
}