/**
 * Sessão do usuário da cozinha, persistida em localStorage (não
 * sessionStorage — ver justificativa no relatório da Fase 2B: um turno de
 * cozinha fica com a aba aberta o dia todo, mas o dispositivo pode ser
 * reiniciado; sessionStorage forçaria relogin desnecessário nesse caso. O
 * JWT já expira sozinho, então a persistência não vira uma sessão eterna.
 *
 * Guardamos nosso PRÓPRIO expiresAt calculado a partir da resposta de
 * login, em vez de decodificar o JWT no navegador — mais simples e não
 * depende de conhecer o formato interno das claims do backend.
 */

const STORAGE_KEY = 'pedacinho_kitchen_session';

export function saveSession(token, expiresInSeconds) {
    const session = {
        token,
        expiresAt: Date.now() + expiresInSeconds * 1000,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
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

/** Pronto para spread num objeto `headers` de fetch: `{ ...authHeader() }`. */
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