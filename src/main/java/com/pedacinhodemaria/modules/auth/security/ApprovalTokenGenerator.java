package com.pedacinhodemaria.modules.auth.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Gera o capability token de aprovação de cadastro: 256 bits de
 * SecureRandom, codificado em Base64 URL-safe (seguro dentro de uma URL,
 * sem precisar de escaping). Só o hash SHA-256 (hex) é persistido — o
 * valor bruto existe apenas em memória, dentro do link enviado à dona,
 * nunca no banco e nunca em log (ver SendApprovalRequestWhatsAppMessageUseCase).
 */
@Component
public class ApprovalTokenGenerator {

    private static final int TOKEN_BYTES = 32; // 256 bits
    private final SecureRandom secureRandom = new SecureRandom();

    public record GeneratedToken(String rawToken, String tokenHash) {}

    public GeneratedToken generate() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return new GeneratedToken(rawToken, hash(rawToken));
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é um algoritmo obrigatório de qualquer provider padrão
            // da JVM — isso nunca deveria acontecer em runtime real.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}