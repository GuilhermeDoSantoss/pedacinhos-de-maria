package com.pedacinhodemaria.modules.auth.security;

import com.pedacinhodemaria.modules.auth.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "uma-chave-de-teste-com-pelo-menos-256-bits-de-tamanho-para-hmac-sha";

    private final JwtService jwtService = new JwtService(SECRET, 3600000L);

    @Test
    void deveGerarTokenComIdentidadeERole() {
        String token = jwtService.generateToken("maria@exemplo.com", UserRole.KITCHEN);

        Claims claims = jwtService.parseAndValidate(token);

        assertThat(jwtService.extractEmail(claims)).isEqualTo("maria@exemplo.com");
        assertThat(jwtService.extractRole(claims)).isEqualTo("KITCHEN");
    }

    @Test
    void deveRejeitarTokenExpirado() {
        // expiração 5s no passado: token já nasce expirado, sem precisar de sleep no teste.
        JwtService jwtServiceExpirado = new JwtService(SECRET, -5000L);
        String tokenExpirado = jwtServiceExpirado.generateToken("maria@exemplo.com", UserRole.KITCHEN);

        assertThatThrownBy(() -> jwtService.parseAndValidate(tokenExpirado))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void deveRejeitarTokenComAssinaturaInvalida() {
        JwtService outroServico = new JwtService("outra-chave-de-teste-completamente-diferente-256-bits-hmac-sha", 3600000L);
        String tokenComOutraChave = outroServico.generateToken("maria@exemplo.com", UserRole.KITCHEN);

        assertThatThrownBy(() -> jwtService.parseAndValidate(tokenComOutraChave))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void deveRejeitarTokenMalformado() {
        assertThatThrownBy(() -> jwtService.parseAndValidate("isso-nao-e-um-jwt"))
                .isInstanceOf(io.jsonwebtoken.MalformedJwtException.class);
    }
}