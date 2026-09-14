package com.pedacinhodemaria.modules.order.controller;

import com.pedacinhodemaria.config.SecurityConfig;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.security.JwtAuthenticationFilter;
import com.pedacinhodemaria.modules.auth.security.JwtService;
import com.pedacinhodemaria.modules.order.service.OrderQueryService;
import com.pedacinhodemaria.modules.order.service.UpdateOrderStatusUseCase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRIMEIRO teste de segurança HTTP real do projeto — todos os outros são
 * Mockito puro (sem contexto Spring). Este carrega o SecurityFilterChain de
 * verdade via @WebMvcTest + @Import(SecurityConfig.class), exatamente como
 * a Fase 3 exigiu ("não depender somente de Mockito/unit tests").
 *
 * @WebMvcTest carrega só a camada web (não o contexto inteiro — sem
 * MongoDB, sem os outros controllers), então os beans que o SecurityConfig
 * precisa por construtor (JwtAuthenticationFilter, e por consequência
 * JwtService) são importados explicitamente. As dependências do próprio
 * KitchenOrderController (OrderQueryService, UpdateOrderStatusUseCase) são
 * mockadas — não são o alvo deste teste, o alvo é o SecurityFilterChain.
 *
 * É o teste mais arriscado de todo o projeto até agora, justamente por ser o
 * primeiro a carregar contexto Spring real; se `mvn clean test` falhar
 * especificamente nele, os candidatos mais prováveis são: (1) o
 * AuthenticationManager bean do SecurityConfig tentando resolver um
 * AuthenticationProvider que não existe neste contexto reduzido — nenhum
 * teste aqui invoca login, então na teoria essa dependência nunca é
 * resolvida, mas @WebMvcTest tem histórico de ser sensível a isso; (2)
 * @ActiveProfiles("test") não encontrar application-test.yml se o Maven
 * não incluir src/test/resources no classpath deste módulo por algum
 * motivo de configuração que eu não tenho visibilidade.
 */
@WebMvcTest(controllers = KitchenOrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@ActiveProfiles("test")
class KitchenOrderControllerSecurityTest {

    // Injetado do mesmo application-test.yml que o JwtService real deste
    // contexto usa (@Value também é resolvido em campos da própria classe
    // de teste pelo Spring TestContext Framework). Antes este valor
    // existia como uma string literal duplicada aqui, manualmente
    // sincronizada com application-test.yml — qualquer diferença entre a
    // cópia e o valor real de jwt.secret (mesmo um espaço a mais) faz a
    // assinatura HMAC não bater, JwtService.parseAndValidate lança
    // SignatureException, JwtAuthenticationFilter descarta a autenticação
    // silenciosamente (ver catch de JwtException), e a requisição chega a
    // authorizeHttpRequests sem nenhuma Authentication — daí o 401 em vez
    // do 403 esperado. Injetar em vez de duplicar elimina esse risco de
    // drift de vez, sem expor a chave em produção (nunca sai do processo
    // de teste).
    @Value("${jwt.secret}")
    private String testSecret;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private OrderQueryService orderQueryService;

    @MockBean
    private UpdateOrderStatusUseCase updateOrderStatusUseCase;

    // ---------- GET /api/v1/kitchen/orders ----------

    @Test
    void getSemJwtRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/kitchen/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getComJwtKitchenPermiteAcesso() throws Exception {
        when(orderQueryService.getActiveOrders()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/kitchen/orders")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN)))
                .andExpect(status().isOk());
    }

    @Test
    void getComJwtOwnerPermiteAcesso() throws Exception {
        when(orderQueryService.getActiveOrders()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/kitchen/orders")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.OWNER)))
                .andExpect(status().isOk());
    }

    @Test
    void getComRoleNaoAutorizadaRetorna403() throws Exception {
        // Não existe uma terceira role no enum UserRole hoje (só KITCHEN e
        // OWNER) — este token é construído manualmente com uma claim
        // "role" fabricada para validar a defesa em profundidade do
        // hasAnyRole contra QUALQUER role fora da lista, não uma role real
        // do domínio atual.
        mockMvc.perform(get("/api/v1/kitchen/orders")
                        .header("Authorization", "Bearer " + tokenWithRawRole("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ---------- PATCH /api/v1/kitchen/orders/{code}/status ----------

    @Test
    void patchSemJwtRetorna401() throws Exception {
        mockMvc.perform(patch("/api/v1/kitchen/orders/ABC123/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"PREPARING\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchComJwtKitchenPermiteAcesso() throws Exception {
        when(updateOrderStatusUseCase.execute(anyString(), any())).thenReturn(null);

        mockMvc.perform(patch("/api/v1/kitchen/orders/ABC123/status")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"PREPARING\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void patchComJwtOwnerPermiteAcesso() throws Exception {
        when(updateOrderStatusUseCase.execute(anyString(), any())).thenReturn(null);

        mockMvc.perform(patch("/api/v1/kitchen/orders/ABC123/status")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"PREPARING\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void patchComRoleNaoAutorizadaRetorna403() throws Exception {
        mockMvc.perform(patch("/api/v1/kitchen/orders/ABC123/status")
                        .header("Authorization", "Bearer " + tokenWithRawRole("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"PREPARING\"}"))
                .andExpect(status().isForbidden());
    }

    private String tokenFor(UserRole role) {
        return jwtService.generateToken("teste@exemplo.com", role);
    }

    private String tokenWithRawRole(String rawRole) {
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("teste@exemplo.com")
                .claim("role", rawRole)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
