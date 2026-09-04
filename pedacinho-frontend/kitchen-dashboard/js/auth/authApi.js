import { CONFIG } from '../config.js';

/**
 * Erro de API de auth, carregando o status HTTP para o chamador decidir a
 * mensagem exibida (ver authGate.js) sem precisar re-parsear a resposta.
 */
export class AuthApiError extends Error {
    constructor(status, body) {
        super((body && body.message) || 'Erro de autenticação');
        this.status = status;
    }
}

/** Contrato exato de RegisterRequest.java: name, email, password. */
export async function register({ name, email, password }) {
    const response = await fetch(`${CONFIG.API_BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, password }),
    });

    const body = await safeJson(response);
    if (!response.ok) {
        throw new AuthApiError(response.status, body);
    }
    return body; // UserResponse
}

/** Contrato exato de LoginRequest.java: email, password. */
export async function login({ email, password }) {
    const response = await fetch(`${CONFIG.API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
    });

    const body = await safeJson(response);
    if (!response.ok) {
        throw new AuthApiError(response.status, body);
    }
    return body; // TokenResponse: { token, type, expiresIn }
}

async function safeJson(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}