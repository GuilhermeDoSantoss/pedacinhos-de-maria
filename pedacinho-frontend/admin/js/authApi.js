import { CONFIG } from './config.js';

export class AuthApiError extends Error {
    constructor(status, body) {
        super((body && body.message) || 'Erro de autenticação');
        this.status = status;
    }
}

const REQUEST_TIMEOUT_MS = 10_000;

/** Contrato exato de LoginRequest.java: email, password. Mesmo endpoint usado pela cozinha — o backend decide role/permissão, não o admin-app. */
export async function login({ email, password }) {
    const response = await fetchWithTimeout(`${CONFIG.API_BASE_URL}/auth/login`, {
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

async function fetchWithTimeout(url, options) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
        return await fetch(url, { ...options, signal: controller.signal });
    } catch {
        throw new AuthApiError(0, { message: 'Não foi possível contatar o servidor. Verifique sua conexão e tente novamente.' });
    } finally {
        clearTimeout(timeoutId);
    }
}

async function safeJson(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}