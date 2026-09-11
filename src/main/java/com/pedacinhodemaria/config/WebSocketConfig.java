package com.pedacinhodemaria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuração STOMP para comunicação em tempo real com a cozinha e o cliente.
 *
 * Autenticação/autorização de mensagens (CONNECT/SUBSCRIBE) vive em
 * StompAuthChannelInterceptor, registrado abaixo — Kitchen exige JWT com
 * role KITCHEN/OWNER para assinar /topic/kitchen-orders; Customer segue
 * sem login, com isolamento por sessão em /topic/order-status/{orderCode}.
 *
 * Por que STOMP (e não um protocolo custom): Spring oferece upgrade nativo
 * de "simple broker" (memória, uma instância) para "broker relay" (RabbitMQ,
 * múltiplas instâncias) trocando só esta configuração.
 *
 * Dois endpoints expostos:
 *  - /ws        → WebSocket nativo puro, usado pelos frontends vanilla JS
 *                 com parser STOMP escrito à mão.
 *  - /ws-sockjs → mesmo broker, com fallback SockJS habilitado.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins);

        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}