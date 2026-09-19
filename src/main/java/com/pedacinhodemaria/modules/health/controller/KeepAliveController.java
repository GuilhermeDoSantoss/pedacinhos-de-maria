package com.pedacinhodemaria.modules.health.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint dedicado para o keep-alive do GitHub Actions — separado de
 * propósito de /actuator/health, que depende do MongoHealthIndicator
 * (autoconfigurado pelo Spring Boot com spring-boot-starter-actuator +
 * spring-boot-starter-data-mongodb no pom.xml). Um ping de keep-alive
 * precisa responder "o processo está de pé e aceitando tráfego" — não "o
 * Mongo está saudável neste instante". Acoplar as duas coisas faria o
 * keep-alive relatar falha mesmo quando a aplicação está perfeitamente
 * capaz de servir requisições reais, só porque o Mongo está
 * momentaneamente reconectando.
 *
 * Sem consulta ao banco, sem autenticação, sem BCrypt, sem JWT — só um 200
 * vazio. Rota pública em SecurityConfig (mesma categoria de
 * /actuator/health) e explicitamente isenta do WarmupGateFilter — um ping
 * de keep-alive deve suceder assim que o Tomcat aceitar conexões, sem
 * esperar o warm-up interno terminar, porque seu único propósito é
 * sinalizar atividade para o Render, não medir prontidão do login.
 *
 * Loga cada ping recebido com a origem declarada via header
 * X-Keep-Alive-Source. Isso é PURAMENTE observabilidade — o header não é
 * validado nem exigido, não é mecanismo de segurança; qualquer chamador
 * pode enviá-lo ou omiti-lo (cai em "unknown" nesse caso). Serve para
 * cruzar, nos logs do Render, o horário em que este endpoint foi
 * realmente atingido com o horário em que o workflow do GitHub Actions
 * registrou sucesso — provando que o request chegou de verdade, e não só
 * que o Actions marcou "completed successfully".
 */
@RestController
@Slf4j
public class KeepAliveController {

    @GetMapping("/internal/keep-alive")
    public ResponseEntity<Void> keepAlive(
            @RequestHeader(value = "X-Keep-Alive-Source", required = false, defaultValue = "unknown") String source) {
        log.info("KEEP_ALIVE_RECEIVED source={}", source);
        return ResponseEntity.ok().build();
    }
}
