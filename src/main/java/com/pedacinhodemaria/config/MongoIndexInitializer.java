package com.pedacinhodemaria.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Garante que os índices essenciais existam ao subir a aplicação, incluindo
 * o índice TTL que implementa a limpeza automática de pedidos antigos.
 *
 * Por que isso é código, não uma migration manual rodada uma vez: o período
 * de retenção (`app.order-retention-days`) é uma decisão de negócio que o
 * Owner deve poder ajustar (30/60/90 dias) — ver `updateRetentionDays`.
 * MongoDB permite alterar o `expireAfterSeconds` de um índice TTL já
 * existente via o comando `collMod`, sem recriar o índice nem dar downtime.
 * Esse método fica pronto para ser chamado pelo endpoint de admin quando a
 * Fase de Admin Panel for implementada — não expomos endpoint nenhum agora
 * porque ainda não existe tela de admin que o justifique.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoIndexInitializer implements ApplicationRunner {

    private static final String ORDERS_COLLECTION = "orders";
    private static final String CREATED_AT_FIELD = "createdAt";

    // Confirmado em User.java: @Document(collection = "users").
    private static final String USERS_COLLECTION = "users";
    private static final String EMAIL_FIELD = "email";

    private final MongoTemplate mongoTemplate;

    @Value("${app.order-retention-days}")
    private int orderRetentionDays;

    @Override
    public void run(ApplicationArguments args) {
        ensureOrderTtlIndex();
        ensureOrderCodeUniqueIndex();
        ensureMealDisplayOrderIndex();
        ensureSideDishDisplayOrderIndex();
        ensureExtraDisplayOrderIndex();
        ensureUserEmailUniqueIndex();
    }

    /**
     * Cria o índice TTL na primeira execução, ou ajusta o valor de retenção
     * via collMod se o índice já existe com um valor diferente — permite
     * mudar a política de retenção sem recriar a coleção.
     */
    private void ensureOrderTtlIndex() {
        long expireAfterSeconds = Duration.ofDays(orderRetentionDays).toSeconds();
        IndexOperations indexOps = mongoTemplate.indexOps(ORDERS_COLLECTION);

        boolean indexExists = indexOps.getIndexInfo().stream()
                .anyMatch(info -> info.getName().equals(CREATED_AT_FIELD + "_ttl"));

        if (!indexExists) {
            indexOps.ensureIndex(new Index()
                    .named(CREATED_AT_FIELD + "_ttl")
                    .on(CREATED_AT_FIELD, org.springframework.data.domain.Sort.Direction.ASC)
                    .expire(expireAfterSeconds));
            log.info("Índice TTL criado em orders.createdAt — retenção de {} dias", orderRetentionDays);
        } else {
            updateRetentionDays(orderRetentionDays);
        }
    }

    /**
     * Altera o período de retenção de um índice TTL já existente em runtime.
     * Ponto de extensão para o Admin Panel: quando o Owner escolher
     * "30/60/90 dias" na tela de configurações, este é o método a chamar.
     */
    public void updateRetentionDays(int days) {
        long expireAfterSeconds = Duration.ofDays(days).toSeconds();
        Document collMod = new Document("collMod", ORDERS_COLLECTION)
                .append("index", new Document("keyPattern", new Document(CREATED_AT_FIELD, 1))
                        .append("expireAfterSeconds", expireAfterSeconds));
        mongoTemplate.getDb().runCommand(collMod);
        log.info("Retenção de pedidos atualizada para {} dias via collMod", days);
    }

    /** orderCode é o capability token do cliente — precisa ser único e de busca rápida. */
    private void ensureOrderCodeUniqueIndex() {
        mongoTemplate.indexOps(ORDERS_COLLECTION).ensureIndex(
                new Index().named("order_code_unique").on("order_code", org.springframework.data.domain.Sort.Direction.ASC).unique());
    }

    /** Acelera a query do cardápio (findByActiveTrueOrderByDisplayOrderAsc), chamada em toda carga do Customer App. */
    private void ensureMealDisplayOrderIndex() {
        mongoTemplate.indexOps("meals").ensureIndex(
                new Index().named("active_display_order").on("active", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("displayOrder", org.springframework.data.domain.Sort.Direction.ASC));
    }

    /** Mesmo padrão de índice do Meal — acelera a query do passo "acompanhamento" do wizard. */
    private void ensureSideDishDisplayOrderIndex() {
        mongoTemplate.indexOps("side_dishes").ensureIndex(
                new Index().named("active_display_order").on("active", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("displayOrder", org.springframework.data.domain.Sort.Direction.ASC));
    }

    /** Mesmo padrão de índice do Meal — acelera a query do passo "extras" do wizard. */
    private void ensureExtraDisplayOrderIndex() {
        mongoTemplate.indexOps("extras").ensureIndex(
                new Index().named("active_display_order").on("active", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("displayOrder", org.springframework.data.domain.Sort.Direction.ASC));
    }

    /**
     * Garante unicidade de e-mail no banco — a última linha
     * de defesa contra cadastro duplicado, além do existsByEmail() feito em
     * RegisterUserUseCase. O check em memória sozinho tem uma janela de
     * corrida (duas requisições concorrentes com o mesmo e-mail podem passar
     * pelo existsByEmail() antes de qualquer uma delas salvar); o índice
     * único no Mongo é o que de fato impede o dado duplicado de existir.
     * Mesmo padrão exato dos demais índices desta classe — nenhum mecanismo
     * paralelo foi criado.
     */
    private void ensureUserEmailUniqueIndex() {
        mongoTemplate.indexOps(USERS_COLLECTION).ensureIndex(
                new Index().named("email_unique").on(EMAIL_FIELD, org.springframework.data.domain.Sort.Direction.ASC).unique());
    }
}