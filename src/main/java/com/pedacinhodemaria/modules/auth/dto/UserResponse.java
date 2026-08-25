package com.pedacinhodemaria.modules.auth.dto;

import com.pedacinhodemaria.modules.auth.domain.UserRole;
import com.pedacinhodemaria.modules.auth.domain.UserStatus;

import java.time.Instant;

/**
 * Nunca inclui passwordHash — mesmo princípio de qualquer *Response deste
 * projeto (ver OrderResponse): o que sai pela API é sempre um subconjunto
 * deliberado da entidade, nunca a entidade inteira serializada.
 */
public record UserResponse(
        String id,
        String name,
        String email,
        UserRole role,
        UserStatus status,
        Instant createdAt
) {
}