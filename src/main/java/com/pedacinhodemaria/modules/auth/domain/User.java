package com.pedacinhodemaria.modules.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Usuário do Kitchen Dashboard. Diferente do Customer App (que nunca exige
 * login — ver ADR no README), o painel da cozinha exige conta aprovada
 * antes de qualquer acesso.
 *
 * Nenhum usuário nasce OWNER por autocadastro — role aqui é sempre KITCHEN
 * (ver RegisterUserUseCase). A conta OWNER inicial é criada por um
 * mecanismo separado (seeder idempotente via OWNER_EMAIL/OWNER_PASSWORD),
 * fora deste fluxo público.
 *
 * A aprovação de um cadastro PENDING é feita dentro do próprio sistema, por
 * um usuário OWNER autenticado (ver ApproveUserUseCase/RejectUserUseCase) —
 * não depende mais de nenhuma integração externa.
 */
@Document(collection = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    private String name;

    /** Normalizado (trim + lowercase) antes de persistir — ver RegisterUserUseCase. */
    private String email;

    /**
     * Nunca em texto puro — sempre hash BCrypt (ver RegisterUserUseCase).
     * O nome do campo deixa isso explícito só de ler a entidade, sem
     * precisar abrir o Use Case para confirmar.
     */
    private String passwordHash;

    private UserRole role;

    /**
     * Todo cadastro público nasce PENDING — nunca APPROVED diretamente. A
     * transição para APPROVED/REJECTED é sempre uma ação manual de um
     * usuário OWNER autenticado (ver ApproveUserUseCase/RejectUserUseCase),
     * nunca automática no registro.
     */
    private UserStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}