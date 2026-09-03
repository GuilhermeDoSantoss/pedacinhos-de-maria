package com.pedacinhodemaria.modules.auth.dto;

/**
 * Nunca inclui e-mail, id, role ou qualquer outro dado do usuário — só o
 * necessário para autenticar chamadas futuras (mesmo princípio de
 * UserResponse nunca incluir passwordHash). A role já viaja dentro do
 * próprio JWT como claim, não precisa ser duplicada aqui.
 */
public record TokenResponse(
        String token,
        String type,
        long expiresIn
) {
    public static TokenResponse bearer(String token, long expiresInSeconds) {
        return new TokenResponse(token, "Bearer", expiresInSeconds);
    }
}