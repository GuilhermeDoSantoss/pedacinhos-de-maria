package com.pedacinhodemaria.config;

import com.pedacinhodemaria.modules.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autenticação e autorização de WebSocket/STOMP para /ws e /ws-sockjs
 * (ver WebSocketConfig). Um único interceptor cobre as duas preocupações,
 * porque Kitchen Dashboard e Customer App compartilham exatamente o mesmo
 * endpoint de conexão — não há como diferenciá-los no CONNECT em si, só a
 * partir de qual tópico cada sessão tenta assinar.
 *
 * CONNECT é OPCIONALMENTE autenticado: um cliente que nunca envia
 * Authorization (o Customer, que nunca faz login) segue conectado como
 * anônimo — rejeitar todo CONNECT sem credencial quebraria o
 * acompanhamento de pedido, que é uma decisão de produto preservada
 * deliberadamente. Um cliente que ENVIA Authorization, porém com um JWT
 * inválido ou expirado, é rejeitado — esse caminho nunca é acionado pelo
 * Customer, que nunca manda esse header.
 *
 * A autorização real acontece no SUBSCRIBE, por destino:
 *  - /topic/kitchen-orders: exige a sessão ter sido autenticada no CONNECT
 *    com role KITCHEN ou OWNER.
 *  - /topic/order-status/{orderCode}: sem exigência de login (preserva o
 *    Customer sem conta), mas limitada por sessão — ver
 *    MAX_ORDER_TOPICS_PER_SESSION abaixo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KITCHEN_TOPIC = "/topic/kitchen-orders";
    private static final String CUSTOMER_TOPIC_PREFIX = "/topic/order-status/";

    /**
     * OrderCodeGenerator produz ~26 bits de entropia (5 caracteres base36)
     * — o bastante para não ser adivinhado por acidente, mas não para
     * resistir a uma varredura automatizada sem limite de tentativas (uma
     * conexão STOMP aberta pode tentar SUBSCRIBE em muitos códigos por
     * segundo, sem o custo de um round-trip HTTP por tentativa). Um
     * cliente legítimo assina exatamente 1 tópico de pedido por sessão
     * (ver main.js: subscribeToOrderStatus é chamado uma única vez, no
     * evento 'order-created'). O limite abaixo é a defesa contra
     * enumeração, sem exigir login do Customer nem alterar o formato do
     * orderCode (que precisa continuar curto para ser lido em voz alta no
     * balcão — ver OrderCodeGenerator).
     */
    private static final int MAX_ORDER_TOPICS_PER_SESSION = 3;

    private final JwtService jwtService;

    private final Map<String, Set<String>> orderTopicsBySession = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            handleConnect(accessor);
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            handleSubscribe(accessor);
        }

        return message;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        orderTopicsBySession.remove(event.getSessionId());
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return; // sem credencial — segue anônimo (fluxo do Customer)
        }

        try {
            Claims claims = jwtService.parseAndValidate(header.substring(BEARER_PREFIX.length()));
            String email = jwtService.extractEmail(claims);
            String role = jwtService.extractRole(claims);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            accessor.setUser(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("CONNECT STOMP rejeitado: {}", ex.getClass().getSimpleName());
            throw new MessagingException("Token inválido ou expirado");
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        if (destination.equals(KITCHEN_TOPIC)) {
            authorizeKitchenSubscription(accessor);
        } else if (destination.startsWith(CUSTOMER_TOPIC_PREFIX)) {
            throttleCustomerSubscription(accessor, destination);
        }
    }

    /**
     * accessor.getUser() aqui reflete o Principal associado no CONNECT
     * desta mesma sessão STOMP — o Spring propaga isso automaticamente
     * entre frames da mesma sessão (StompSubProtocolHandler), sem esta
     * classe precisar guardar estado de autenticação por sessão.
     */
    private void authorizeKitchenSubscription(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        boolean authorized = user instanceof Authentication auth && auth.getAuthorities().stream()
                .map(Object::toString)
                .anyMatch(role -> role.equals("ROLE_KITCHEN") || role.equals("ROLE_OWNER"));

        if (!authorized) {
            log.debug("SUBSCRIBE a {} rejeitado — sessão sem role KITCHEN/OWNER", KITCHEN_TOPIC);
            throw new MessagingException("Não autorizado a assinar este tópico");
        }
    }

    private void throttleCustomerSubscription(StompHeaderAccessor accessor, String destination) {
        Set<String> topics = orderTopicsBySession.computeIfAbsent(
                accessor.getSessionId(), id -> ConcurrentHashMap.newKeySet());
        topics.add(destination);

        if (topics.size() > MAX_ORDER_TOPICS_PER_SESSION) {
            log.warn("Sessão STOMP {} excedeu o limite de tópicos de pedido distintos numa única conexão", accessor.getSessionId());
            throw new MessagingException("Limite de assinaturas excedido");
        }
    }
}