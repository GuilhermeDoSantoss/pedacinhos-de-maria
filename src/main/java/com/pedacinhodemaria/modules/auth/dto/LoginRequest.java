package com.pedacinhodemaria.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de login. Sem validação de tamanho de senha aqui — diferente de
 * RegisterRequest, não faz sentido rejeitar "senha curta" no login: a
 * senha já existe no banco com o formato que tinha quando foi cadastrada.
 */
public record LoginRequest(

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail em formato inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        String password
) {
}