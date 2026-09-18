package com.pedacinhodemaria.modules.health.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint dedicado para o keep-alive do GitHub Actions separado de
 * propósito de /actuator/health, que provavelmente inclui o
 * MongoHealthIndicator (autoconfigurado pelo Spring Boot com
 * spring-boot-starter-actuator + spring-boot-starter-data-mongodb no
 * pom.xml). Um ping de keep-alive precisa responder "o processo está de
 * pé e aceitando tráfego" não "o Mongo está saudável neste instante".
 * Acoplar as duas coisas faz o keep-alive relatar falha (via curl --fail)
 * mesmo quando a aplicação está perfeitamente capaz de servir requisições
 * reais, só porque o Mongo está momentaneamente reconectando.
 *
 * Sem consulta ao banco, sem autenticação, sem qualquer dado — só um 200
 * vazio. Rota pública em SecurityConfig (mesma categoria de
 * /actuator/health) e explicitamente isenta do WarmupGateFilter (ver
 * WarmupGateFilter) — um ping de keep-alive deve suceder assim que o
 * Tomcat aceitar conexões, sem esperar o warm-up interno terminar,
 * porque seu único propósito é sinalizar atividade para o Render, não
 * medir prontidão do login.
 */
@RestController
public class KeepAliveController {

    @GetMapping("/internal/keep-alive")
    public ResponseEntity<Void> keepAlive() {
        return ResponseEntity.ok().build();
    }
}