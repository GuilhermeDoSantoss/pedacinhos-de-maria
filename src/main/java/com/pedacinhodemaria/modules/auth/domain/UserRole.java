package com.pedacinhodemaria.modules.auth.domain;

/**
 * OWNER: proprietária do restaurante — única role com permissão de
 * aprovar/rejeitar cadastros (fase futura). KITCHEN: funcionário do
 * salão/cozinha, acesso ao Dashboard depois de aprovado.
 *
 * Autocadastro público (RegisterUserUseCase) nunca atribui OWNER — essa
 * role só existe via o mecanismo de conta inicial (fase futura), nunca
 * pelo endpoint de registro.
 */
public enum UserRole {
    OWNER,
    KITCHEN
}