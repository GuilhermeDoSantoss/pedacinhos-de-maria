package com.pedacinhodemaria.modules.auth.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalTokenGeneratorTest {

    private final ApprovalTokenGenerator generator = new ApprovalTokenGenerator();

    @Test
    void deveGerarTokenComPeloMenos256BitsDeEntropia() {
        ApprovalTokenGenerator.GeneratedToken generated = generator.generate();

        byte[] decoded = Base64.getUrlDecoder().decode(generated.rawToken());
        assertThat(decoded).hasSize(32); // 256 bits
    }

    @Test
    void doisTokensGeradosDevemSerDiferentes() {
        ApprovalTokenGenerator.GeneratedToken primeiro = generator.generate();
        ApprovalTokenGenerator.GeneratedToken segundo = generator.generate();

        assertThat(primeiro.rawToken()).isNotEqualTo(segundo.rawToken());
        assertThat(primeiro.tokenHash()).isNotEqualTo(segundo.tokenHash());
    }

    @Test
    void hashNuncaDeveSerIgualAoTokenBruto() {
        ApprovalTokenGenerator.GeneratedToken generated = generator.generate();

        assertThat(generated.tokenHash()).isNotEqualTo(generated.rawToken());
    }

    @Test
    void hashDeveSerDeterministicoParaOMesmoTokenBruto() {
        ApprovalTokenGenerator.GeneratedToken generated = generator.generate();

        assertThat(generator.hash(generated.rawToken())).isEqualTo(generated.tokenHash());
    }
}