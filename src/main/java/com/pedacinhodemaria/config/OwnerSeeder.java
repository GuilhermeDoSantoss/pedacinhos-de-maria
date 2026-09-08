package com.pedacinhodemaria.config;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import com.pedacinhodemaria.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Cria a primeira conta OWNER a partir de variáveis de ambiente, resolvendo
 * o item "não quero que alguém vire OWNER pelo cadastro público" sem
 * complicar o projeto com um fluxo de setup separado.
 *
 * Estratégia escolhida (adequada para Render, sem passo manual a cada
 * deploy): ApplicationRunner que roda no startup — mesmo padrão já usado
 * por MongoIndexInitializer — e faz exatamente:
 *
 *   existe algum usuário com role OWNER?
 *     sim → não faz absolutamente nada (idempotente; nunca sobrescreve
 *           e-mail, senha ou qualquer campo de um OWNER já existente,
 *           mesmo que OWNER_EMAIL/OWNER_PASSWORD mudem depois)
 *     não → OWNER_EMAIL e OWNER_PASSWORD estão configuradas?
 *             sim → cria o OWNER (senha via BCrypt, nunca em texto puro)
 *             não → loga um aviso e segue o boot normalmente (não impede
 *                   a aplicação de subir; só o painel administrativo fica
 *                   inacessível até alguém configurar as variáveis)
 *
 * A checagem é por ROLE, não por e-mail específico — assim funciona mesmo
 * que a proprietária decida trocar o e-mail do OWNER depois manualmente no
 * banco; o seeder não tenta "corrigir" isso.
 *
 * @Order(0) garante que isso roda antes de qualquer outro ApplicationRunner
 * que dependa de dados existirem (não é o caso hoje, mas evita acoplamento
 * a ordem de declaração de bean, que o Spring não garante por padrão).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class OwnerSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.owner.email:}")
    private String ownerEmail;

    @Value("${app.owner.password:}")
    private String ownerPassword;

    @Value("${app.owner.name:Proprietária}")
    private String ownerName;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(UserRole.OWNER)) {
            log.debug("Já existe uma conta OWNER — seeder não faz nada.");
            return;
        }

        if (ownerEmail == null || ownerEmail.isBlank() || ownerPassword == null || ownerPassword.isBlank()) {
            log.warn("Nenhuma conta OWNER encontrada e OWNER_EMAIL/OWNER_PASSWORD não configuradas — "
                    + "o painel administrativo ficará inacessível até essas variáveis serem definidas e a aplicação reiniciada.");
            return;
        }

        String normalizedEmail = ownerEmail.trim().toLowerCase();

        User owner = User.builder()
                .name(ownerName)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(ownerPassword))
                .role(UserRole.OWNER)
                // OWNER nasce já APPROVED — é a própria autoridade de aprovação;
                // não faria sentido depender de outro OWNER para aprovar o primeiro.
                .status(UserStatus.APPROVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        userRepository.save(owner);
        log.info("Conta OWNER inicial criada para {} — variável OWNER_PASSWORD pode ser removida do ambiente após este boot, se preferir.", normalizedEmail);
    }
}