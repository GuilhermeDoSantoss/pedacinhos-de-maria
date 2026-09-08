import { qs, show, hide } from '../utils/domHelpers.js';
import { login, AuthApiError } from '../auth/authApi.js';
import { saveSession } from '../auth/session.js';

export function initAdminGate(onAuthenticated) {
    const form = qs('#login-form');
    form.addEventListener('submit', (event) => handleLogin(event, onAuthenticated));
}

async function handleLogin(event, onAuthenticated) {
    event.preventDefault();
    const submitButton = qs('#login-submit');
    const errorEl = qs('#login-error');
    hide(errorEl);

    const email = qs('#login-email').value.trim();
    const password = qs('#login-password').value;

    submitButton.disabled = true;
    submitButton.textContent = 'Entrando...';

    try {
        const response = await login({ email, password });
        saveSession(response.token, response.expiresIn);
        onAuthenticated();
    } catch (err) {
        errorEl.textContent = loginErrorMessage(err);
        show(errorEl);
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = 'Entrar';
    }
}

/**
 * Mesma mensagem genérica usada no login da cozinha — o backend devolve a
 * mesma resposta para senha errada, e-mail inexistente e conta ainda não
 * aprovada, de propósito (anti account enumeration). Aqui isso também
 * cobre "esta conta existe mas não é OWNER" só de forma indireta: login
 * pode passar (credenciais certas) mesmo para uma conta KITCHEN — a real
 * verificação de que é OWNER só acontece na primeira chamada à API
 * administrativa (ver app.js), não aqui.
 */
function loginErrorMessage(err) {
    if (err instanceof AuthApiError && err.status === 401) {
        return 'E-mail ou senha inválidos, ou conta ainda não aprovada.';
    }
    if (err instanceof AuthApiError && (err.status === 400 || err.status === 0)) {
        return err.message || 'Verifique os dados informados.';
    }
    return 'Não foi possível entrar agora. Tente novamente em instantes.';
}