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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(@RequestBody @Valid CreateUserRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(dto));
    }

    @Operation(
            summary = "Login via Firebase",
            description = "Autentica usando token do Firebase Auth.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token JWT do servidor retornado"),
                    @ApiResponse(responseCode = "401", description = "Token Firebase inválido")
            }
    )
    @PostMapping("/auth/firebase")
    public ResponseEntity<TokenResponseDTO> authenticateWithFirebase(@RequestBody @Valid TokenRequestDTO dto) {
        return ResponseEntity.ok(userService.verifyAndAuthenticateFirebaseToken(dto.token()));
    }

    @Operation(
            summary = "Buscar usuário por ID (ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Usuário encontrado"),
                    @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
            }
    )
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @Operation(summary = "Listar todos os usuários (ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @Operation(summary = "Deletar usuário (ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Alterar role de um usuário (ADMIN)",
            description = "Permite trocar entre ADMIN_ROLE, SUPERVISOR_ROLE, FUNCIONARIO_ROLE e USER_ROLE."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/role")
    public ResponseEntity<UserResponseDTO> atualizarRole(
            @PathVariable UUID id,
            @RequestBody @Valid RoleUpdateDTO dto) {
        return ResponseEntity.ok(userService.atualizarRole(id, dto.role()));
    }

    @Operation(summary = "Dados do usuário logado (id, email, nome, role)")
    @GetMapping("/me")
    public ResponseEntity<UserMeDTO> getMe() {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(new UserMeDTO(user.getId(), user.getEmail(), user.getName(), user.getRole()));
    }

    @Operation(summary = "Atualizar FCM Token do celular")
    @PatchMapping("/me/fcm-token")
    public ResponseEntity<Void> atualizarFcmToken(@RequestBody @Valid FcmTokenRequestDTO dto) {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.atualizarFcmToken(user.getEmail(), dto.fcmToken());
        return ResponseEntity.ok().build();
    }
}