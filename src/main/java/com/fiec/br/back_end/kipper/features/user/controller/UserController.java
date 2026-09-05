package com.fiec.br.back_end.kipper.features.user.controller;

import com.fiec.br.back_end.kipper.features.user.model.dto.CreateUserRequestDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.TokenRequestDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.UserMeDTO;
import com.fiec.br.back_end.kipper.features.user.model.dto.UserResponseDTO;
import com.fiec.br.back_end.kipper.features.user.model.entities.Users;
import com.fiec.br.back_end.kipper.features.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(@RequestBody @Valid CreateUserRequestDTO dto) {
        UserResponseDTO response = userService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/auth/firebase")
    public ResponseEntity<UserResponseDTO> authenticateWithFirebase(@RequestBody @Valid TokenRequestDTO dto) {
        UserResponseDTO response = userService.verifyAndAuthenticateFirebaseToken(dto.token());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> findById(@PathVariable UUID id) {
        UserResponseDTO response = userService.findById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> findAll() {
        List<UserResponseDTO> response = userService.findAll();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserMeDTO> getMe() {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(new UserMeDTO(user.getEmail(), user.getName()));
    }
}