import { CONFIG } from '../config.js';

/**
 * Erro de API de auth, carregando o status HTTP para o chamador decidir a
 * mensagem exibida (ver authGate.js) sem precisar re-parsear a resposta.
 * status = 0 sinaliza timeout/rede (nenhuma resposta HTTP chegou) — não
 * existe status HTTP 0 de verdade, é só um marcador interno.
 */
export class AuthApiError extends Error {
    constructor(status, body) {
        super((body && body.message) || 'Erro de autenticação');
        this.status = status;
    }
}

// NOVO: sem isso, se o backend não responder (fora do ar, rede lenta,
// erro de conexão com o banco no startup), o fetch fica pendurado
// indefinidamente e o botão "Entrando..."/"Enviando..." nunca volta ao
// normal — parece um bug de frontend quando na verdade é o backend
// inacessível. 10s é generoso o bastante para uma rede lenta normal, sem
// deixar o usuário esperando por muito tempo sem feedback nenhum.
const REQUEST_TIMEOUT_MS = 10_000;

/** Contrato exato de RegisterRequest.java: name, email, password. */
export async function register({ name, email, password }) {
    const response = await fetchWithTimeout(`${CONFIG.API_BASE_URL}/auth/register`, {
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
    } catch (err) {
        // AbortError (timeout) ou falha de rede pura (backend fora do ar,
        // DNS, CORS bloqueado) — nos dois casos não existe response HTTP
        // real, então sinalizamos com status 0 em vez de deixar o erro
        // genérico do fetch vazar sem contexto para quem chamou.
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