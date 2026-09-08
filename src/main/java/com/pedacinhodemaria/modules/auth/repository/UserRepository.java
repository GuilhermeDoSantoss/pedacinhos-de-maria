package com.pedacinhodemaria.modules.auth.repository;

import com.pedacinhodemaria.modules.auth.domain.User;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** NOVO. Usado pelo painel administrativo para o filtro "Pendentes"/"Aprovados"/"Rejeitados", mais recentes primeiro. */
    List<User> findByStatusOrderByCreatedAtDesc(UserStatus status);

    /** NOVO. Usado pelo painel administrativo para o filtro "Todos" — mais recentes primeiro. */
    List<User> findAllByOrderByCreatedAtDesc();

    /** NOVO. Usado pelo seeder do OWNER inicial para checar idempotência (não recriar/sobrescrever se já existir). */
    boolean existsByRole(com.pedacinhodemaria.modules.auth.domain.UserRole role);
}