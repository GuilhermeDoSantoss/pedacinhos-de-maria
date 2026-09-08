package com.pedacinhodemaria.modules.auth.controller;

import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.dto.UserResponse;
import com.pedacinhodemaria.modules.auth.service.ApproveUserUseCase;
import com.pedacinhodemaria.modules.auth.service.ListUsersUseCase;
import com.pedacinhodemaria.modules.auth.service.RejectUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administração de usuários — exclusivo da proprietária do estabelecimento.
 * Toda a proteção real vive no SecurityConfig (hasRole("OWNER") em
 * /api/v1/admin/**); este controller não reverifica role, não confia em
 * nada vindo do frontend, e nunca recebe/decide status ou role a partir do
 * corpo da requisição — a única entrada é o {id} do usuário alvo na URL.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administração de usuários — exclusivo de ROLE_OWNER")
public class AdminUserController {

    private final ListUsersUseCase listUsersUseCase;
    private final ApproveUserUseCase approveUserUseCase;
    private final RejectUserUseCase rejectUserUseCase;

    @GetMapping
    @Operation(summary = "Lista usuários, com filtro opcional por status (PENDING/APPROVED/REJECTED)")
    public ResponseEntity<List<UserResponse>> list(@RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(listUsersUseCase.execute(status));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Aprova um usuário PENDING — só ele passa a poder fazer login")
    public ResponseEntity<UserResponse> approve(@PathVariable String id) {
        return ResponseEntity.ok(approveUserUseCase.execute(id));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Rejeita um usuário PENDING — login permanece negado")
    public ResponseEntity<UserResponse> reject(@PathVariable String id) {
        return ResponseEntity.ok(rejectUserUseCase.execute(id));
    }
}