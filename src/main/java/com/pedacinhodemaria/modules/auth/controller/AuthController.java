package com.pedacinhodemaria.modules.auth.controller;

import com.pedacinhodemaria.modules.auth.dto.RegisterRequest;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.service.RegisterUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticação do Kitchen Dashboard.
 *
 * Fase 1: só cadastro (público). Login, aprovação/rejeição e proteção de
 * rota chegam em fases seguintes — ver SecurityConfig para o que já está
 * liberado hoje (só /register; tudo o mais continua com a mesma política
 * de antes, incluindo o denyAll padrão para rotas desconhecidas).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Cadastro e autenticação de usuários do Kitchen Dashboard")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;

    @PostMapping("/register")
    @Operation(summary = "Cadastra um novo usuário do Dashboard — nasce sempre PENDING, aguardando aprovação da proprietária")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = registerUserUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}