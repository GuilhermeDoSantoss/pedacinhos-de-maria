package com.pedacinhodemaria.modules.auth.security;

import com.pedacinhodemaria.modules.auth.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Geração e validação de JWT (lib io.jsonwebtoken — única lib JWT do
 * projeto, não havia nenhuma outra no pom.xml antes desta classe).
 *
 * Claims deliberadamente mínimas: subject = e-mail normalizado, "role" =
 * nome do enum UserRole. Nada de senha/hash/dados pessoais — o token só
 * precisa carregar o suficiente para authorizeHttpRequests decidir acesso
 * sem uma nova consulta ao banco a cada requisição.
 */
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subjectEmail, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subjectEmail)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    /**
     * Lança io.jsonwebtoken.security.SignatureException (assinatura
     * inválida), io.jsonwebtoken.ExpiredJwtException (expirado) ou
     * io.jsonwebtoken.MalformedJwtException (estrutura inválida) — todas
     * subclasses de JwtException. Quem chama decide o que fazer; esta
     * classe não engole nem loga o erro, só propaga.
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }
}