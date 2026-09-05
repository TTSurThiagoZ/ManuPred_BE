package com.fiec.br.back_end.kipper.features.auth.models.dto;

public record RegisterRequestDTO(String nome, String email, String password, String fcmToken) {}
