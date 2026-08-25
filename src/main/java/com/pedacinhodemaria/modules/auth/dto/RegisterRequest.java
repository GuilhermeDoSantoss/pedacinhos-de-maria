package com.pedacinhodemaria.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de cadastro de usuário do Kitchen Dashboard.
 *
 * Sem campo de confirmação de senha aqui de propósito — comparar as duas
 * senhas digitadas é responsabilidade da tela de cadastro (validação de
 * UX, antes mesmo de montar a requisição); o backend só precisa da senha
 * final escolhida, mesmo raciocínio de CreateOrderRequest não carregar
 * nenhum campo que seja só de apresentação do formulário.
 */
public record RegisterRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
        String name,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail em formato inválido")
        String email,

        /**
         * Limite máximo de 72 não é arbitrário: BCrypt ignora silenciosamente
         * qualquer byte além do 72º caractere da senha — sem esse limite,
         * duas senhas diferentes que só divergem depois do byte 72 gerariam
         * o mesmo hash, uma falha de segurança sutil e fácil de não notar.
         */
        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
        String password
) {
}