import { qs, show, hide } from '../utils/domHelpers.js';
import { login, register, AuthApiError } from './authApi.js';
import { saveSession } from './session.js';

/**
 * Controla inteiramente a tela #auth-gate: alternância entre login/cadastro,
 * submissão dos formulários, mensagens de erro e de "aguardando aprovação".
 * Não sabe nada sobre o board/WebSocket — só chama `onAuthenticated` quando
 * o login é bem-sucedido; dashboard.js decide o que fazer depois disso.
 */
export function initAuthGate(onAuthenticated) {
    const loginForm = qs('#login-form');
    const registerForm = qs('#register-form');

    qs('#show-register-button').addEventListener('click', () => switchTo('register'));
    qs('#show-login-button').addEventListener('click', () => switchTo('login'));
    qs('#back-to-login-button').addEventListener('click', () => switchTo('login'));

    loginForm.addEventListener('submit', (event) => handleLogin(event, onAuthenticated));
    registerForm.addEventListener('submit', handleRegister);
}

function switchTo(mode) {
    hide(qs('#login-form'));
    hide(qs('#register-form'));
    hide(qs('#pending-message'));
    hide(qs('#login-error'));
    hide(qs('#register-error'));
    hide(qs('#show-register-button'));
    hide(qs('#show-login-button'));

    if (mode === 'login') {
        show(qs('#login-form'));
        show(qs('#show-register-button'));
    } else if (mode === 'register') {
        show(qs('#register-form'));
        show(qs('#show-login-button'));
    } else if (mode === 'pending') {
        show(qs('#pending-message'));
    }
}

async function handleLogin(event, onAuthenticated) {
    event.preventDefault();
    const submitButton = qs('#login-submit');
    const errorEl = qs('#login-error');
    hide(errorEl);

    const email = qs('#login-email').value.trim();
    const password = qs('#login-password').value;

    setLoading(submitButton, true, 'Entrando...');
    try {
        const response = await login({ email, password });
        saveSession(response.token, response.expiresIn);
        onAuthenticated();
    } catch (err) {
        errorEl.textContent = loginErrorMessage(err);
        show(errorEl);
    } finally {
        setLoading(submitButton, false, 'Entrar');
    }
}

async function handleRegister(event) {
    event.preventDefault();
    const submitButton = qs('#register-submit');
    const errorEl = qs('#register-error');
    hide(errorEl);

    const name = qs('#register-name').value.trim();
    const email = qs('#register-email').value.trim();
    const password = qs('#register-password').value;

    const validationError = validateRegister({ name, email, password });
    if (validationError) {
        errorEl.textContent = validationError;
        show(errorEl);
        return;
    }

    setLoading(submitButton, true, 'Enviando...');
    try {
        await register({ name, email, password });
        qs('#register-form').reset();
        switchTo('pending');
    } catch (err) {
        errorEl.textContent = registerErrorMessage(err);
        show(errorEl);
    } finally {
        setLoading(submitButton, false, 'Criar acesso');
    }
}

/** Espelha exatamente as constraints reais de RegisterRequest.java (@Size). */
function validateRegister({ name, email, password }) {
    if (name.length < 2 || name.length > 100) return 'Nome deve ter entre 2 e 100 caracteres.';
    if (!/^\S+@\S+\.\S+$/.test(email)) return 'E-mail em formato inválido.';
    if (password.length < 8 || password.length > 72) return 'Senha deve ter entre 8 e 72 caracteres.';
    return null;
}

/**
 * Mensagem única para QUALQUER falha de login (senha errada, e-mail
 * inexistente, conta PENDING ou REJECTED). Não é uma limitação de UX — é o
 * próprio backend (AuthenticateUserUseCase, Fase 2A) devolvendo a mesma
 * resposta genérica para os 4 casos de propósito, para não permitir
 * descobrir por tentativa e erro se um e-mail está cadastrado ou qual é o
 * status de uma conta. O frontend não tem como diferenciar PENDING de
 * senha errada aqui — o texto abaixo cobre as possibilidades honestamente,
 * sem inventar uma certeza que os dados não sustentam.
 */
function loginErrorMessage(err) {
    if (err instanceof AuthApiError && err.status === 401) {
        return 'E-mail ou senha inválidos, ou sua conta ainda não foi aprovada pela responsável pelo estabelecimento.';
    }
    if (err instanceof AuthApiError && (err.status === 400 || err.status === 0)) {
        return err.message || 'Verifique os dados informados.';
    }
    return 'Não foi possível entrar agora. Tente novamente em instantes.';
}

function registerErrorMessage(err) {
    if (err instanceof AuthApiError && err.status === 409) {
        return 'Já existe uma conta cadastrada com este e-mail.';
    }
    if (err instanceof AuthApiError && (err.status === 400 || err.status === 0)) {
        return err.message || 'Verifique os dados informados.';
    }
    return 'Não foi possível concluir o cadastro agora. Tente novamente em instantes.';
}

function setLoading(button, isLoading, label) {
    button.disabled = isLoading;
    button.textContent = label;
}