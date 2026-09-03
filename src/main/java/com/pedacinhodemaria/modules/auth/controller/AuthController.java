package com.pedacinhodemaria.modules.auth.controller;

import com.pedacinhodemaria.modules.auth.dto.LoginRequest;
import com.pedacinhodemaria.modules.auth.dto.RegisterRequest;
import com.pedacinhodemaria.modules.auth.dto.TokenResponse;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.service.AuthenticateUserUseCase;
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
 * Ambos os endpoints são públicos por natureza — não existe usuário
 * autenticado antes de se cadastrar ou de logar (ver SecurityConfig). A
 * aprovação em si não passa por este controller — ela acontece via link do
 * WhatsApp, tratado por UserApprovalController.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Cadastro e autenticação de usuários do Kitchen Dashboard")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final AuthenticateUserUseCase authenticateUserUseCase;

    @PostMapping("/register")
    @Operation(summary = "Cadastra um novo usuário do Dashboard — nasce sempre PENDING, aguardando aprovação da proprietária")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = registerUserUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica um usuário APPROVED e retorna um JWT Bearer")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authenticateUserUseCase.execute(request);
        return ResponseEntity.ok(response);
    }
}