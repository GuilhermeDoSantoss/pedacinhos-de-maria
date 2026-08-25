package com.pedacinhodemaria.modules.auth.domain;

/**
 * Ciclo de vida do cadastro de um usuário do Dashboard.
 *
 * PENDING é sempre o estado inicial (ver RegisterUserUseCase) — a transição
 * para APPROVED/REJECTED é uma decisão manual da proprietária, implementada
 * em fase futura (ApproveUserUseCase/RejectUserUseCase). Nenhum código
 * ainda cria usuário diretamente como APPROVED ou REJECTED.
 *
 * Sem métodos de transição (ex.: canTransitionTo, ao estilo de OrderStatus)
 * nesta fase de propósito — essas regras só fazem sentido quando o Use Case
 * de aprovação/rejeição existir para usá-las; adicionar agora seria código
 * sem consumidor.
 */
public enum UserStatus {
    PENDING,
    APPROVED,
    REJECTED
}