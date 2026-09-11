package com.pedacinhodemaria.config;

import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompAuthChannelInterceptorTest {

    private static final String SECRET = "uma-chave-de-teste-com-pelo-menos-256-bits-de-tamanho-para-hmac-sha";

    private final JwtService jwtService = new JwtService(SECRET, 3600000L);
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtService);

    private StompHeaderAccessor connectAccessor(String sessionId, String bearerHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        if (bearerHeader != null) {
            accessor.setNativeHeader("Authorization", bearerHeader);
        }
        return accessor;
    }

    private StompHeaderAccessor subscribeAccessor(String sessionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        return accessor;
    }

    private Message<byte[]> toMessage(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String tokenWithRawRole(String rawRole) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("teste@exemplo.com")
                .claim("role", rawRole)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    // ---------- CONNECT ----------

    @Test
    void connectSemAuthorizationSeguiAnonimo() {
        StompHeaderAccessor accessor = connectAccessor("s1", null);

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void connectComJwtValidoAssociaUsuarioASessao() {
        String token = jwtService.generateToken("cozinha@exemplo.com", UserRole.KITCHEN);
        StompHeaderAccessor accessor = connectAccessor("s2", "Bearer " + token);

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    void connectComJwtInvalidoLancaExcecao() {
        StompHeaderAccessor accessor = connectAccessor("s3", "Bearer token-invalido");

        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connectComJwtExpiradoLancaExcecao() {
        JwtService expiredService = new JwtService(SECRET, -5000L);
        String tokenExpirado = expiredService.generateToken("cozinha@exemplo.com", UserRole.KITCHEN);
        StompHeaderAccessor accessor = connectAccessor("s4", "Bearer " + tokenExpirado);

        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), null))
                .isInstanceOf(MessagingException.class);
    }

    // ---------- SUBSCRIBE kitchen ----------

    @Test
    void subscribeKitchenSemAutenticacaoPreviaEBloqueado() {
        StompHeaderAccessor subscribe = subscribeAccessor("s5", "/topic/kitchen-orders");

        assertThatThrownBy(() -> interceptor.preSend(toMessage(subscribe), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribeKitchenComRoleKitchenEPermitido() {
        String token = jwtService.generateToken("cozinha@exemplo.com", UserRole.KITCHEN);
        StompHeaderAccessor connect = connectAccessor("s6", "Bearer " + token);
        interceptor.preSend(toMessage(connect), null);

        // Simula a propagação de Principal que o Spring faz automaticamente
        // entre frames da mesma sessão STOMP (StompSubProtocolHandler) —
        // aqui testamos a AUTORIZAÇÃO em isolamento, dado que o usuário já
        // foi associado à sessão no CONNECT.
        StompHeaderAccessor subscribe = subscribeAccessor("s6", "/topic/kitchen-orders");
        subscribe.setUser(connect.getUser());

        interceptor.preSend(toMessage(subscribe), null); // não deve lançar
    }

    @Test
    void subscribeKitchenComRoleOwnerEPermitido() {
        String token = jwtService.generateToken("dona@exemplo.com", UserRole.OWNER);
        StompHeaderAccessor connect = connectAccessor("s7", "Bearer " + token);
        interceptor.preSend(toMessage(connect), null);

        StompHeaderAccessor subscribe = subscribeAccessor("s7", "/topic/kitchen-orders");
        subscribe.setUser(connect.getUser());

        interceptor.preSend(toMessage(subscribe), null);
    }

    @Test
    void subscribeKitchenComRoleFabricadaEBloqueado() {
        // Mesma técnica de KitchenOrderControllerSecurityTest: não existe
        // uma terceira role no domínio, então o teste de "role inadequada"
        // usa um token com claim de role fora do enum UserRole.
        StompHeaderAccessor connect = connectAccessor("s8", "Bearer " + tokenWithRawRole("CUSTOMER"));
        interceptor.preSend(toMessage(connect), null);

        StompHeaderAccessor subscribe = subscribeAccessor("s8", "/topic/kitchen-orders");
        subscribe.setUser(connect.getUser());

        assertThatThrownBy(() -> interceptor.preSend(toMessage(subscribe), null))
                .isInstanceOf(MessagingException.class);
    }

    // ---------- SUBSCRIBE customer ----------

    @Test
    void customerPodeAssinarOProprioPedidoSemAutenticacao() {
        StompHeaderAccessor subscribe = subscribeAccessor("s9", "/topic/order-status/PM-A1B2C");

        interceptor.preSend(toMessage(subscribe), null); // não deve lançar
    }

    @Test
    void customerPodeAssinarAteOLimiteDeTopicosDistintosNaMesmaSessao() {
        String sessionId = "s10";
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-AAAAA")), null);
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-BBBBB")), null);
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-CCCCC")), null);
        // 3 tópicos distintos na mesma sessão — nenhuma exceção até aqui.
    }

    @Test
    void customerExcedendoOLimiteDeTopicosDistintosEBloqueado() {
        String sessionId = "s11";
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-AAAAA")), null);
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-BBBBB")), null);
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-CCCCC")), null);

        assertThatThrownBy(() -> interceptor.preSend(
                toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-DDDDD")), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void resubscreverAoMesmoTopicoNaoContaComoNovoParaOLimite() {
        String sessionId = "s12";
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-AAAAA")), null);
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-AAAAA")), null);
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-AAAAA")), null);
        interceptor.preSend(toMessage(subscribeAccessor(sessionId, "/topic/order-status/PM-AAAAA")), null);
        // mesmo tópico 4x na mesma sessão — não deve disparar o throttle, é o mesmo pedido.
    }
}