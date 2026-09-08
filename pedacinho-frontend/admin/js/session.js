/**
 * Mesmo padrão de session.js do kitchen-dashboard — duplicado, não
 * importado, porque admin-app e kitchen-dashboard são deploys/origens
 * separados (não dá pra compartilhar módulo JS entre Static Sites
 * diferentes no Render sem um passo de build, que este projeto não tem).
 * Chave de storage própria: são domínios diferentes, então localStorage já
 * é isolado por origem — o prefixo "admin" aqui é só clareza de leitura de
 * código, não uma proteção adicional.
 */

const STORAGE_KEY = 'pedacinho_admin_session';

export function saveSession(token, expiresInSeconds) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
        token,
        expiresAt: Date.now() + expiresInSeconds * 1000,
    }));
}

export function clearSession() {
    localStorage.removeItem(STORAGE_KEY);
}

export function getToken() {
    const session = readSession();
    if (!session) return null;

    if (Date.now() >= session.expiresAt) {
        clearSession();
        return null;
    }

    return session.token;
}

export function hasValidSession() {
    return getToken() !== null;
}

export function authHeader() {
    const token = getToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
}

function readSession() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}