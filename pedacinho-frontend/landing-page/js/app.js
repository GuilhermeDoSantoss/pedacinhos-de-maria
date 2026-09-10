/**
 * URLs provisórias — únicas linhas a alterar quando os domínios definitivos
 * forem decididos. Mesmo padrão de config.js dos outros frontends: fonte
 * única, nada hardcoded espalhado pelo HTML.
 */
const isLocalEnvironment = ['localhost', '127.0.0.1'].includes(window.location.hostname);

const LINKS = {
    customer: isLocalEnvironment ? 'http://localhost:5500' : 'https://pedacinho-customer.onrender.com/',
    admin: isLocalEnvironment ? 'http://localhost:5502' : 'https://pedacinho-admin.onrender.com/',
    kitchen: isLocalEnvironment ? 'http://localhost:5501' : 'https://pedacinho-dashboard.onrender.com/',
};

for (const [key, url] of Object.entries(LINKS)) {
    document.querySelectorAll(`[data-link="${key}"]`).forEach((el) => el.setAttribute('href', url));
}

// Menu mobile — simples toggle de classe + aria-expanded, sem dependência externa.
const menuToggle = document.getElementById('menu-toggle');
const siteNav = document.getElementById('site-nav');

if (menuToggle && siteNav) {
    menuToggle.addEventListener('click', () => {
        const isOpen = siteNav.classList.toggle('is-open');
        menuToggle.setAttribute('aria-expanded', String(isOpen));
        menuToggle.setAttribute('aria-label', isOpen ? 'Fechar menu' : 'Abrir menu');
    });

    // Clicar num link do menu (mobile) fecha o menu antes de navegar/rolar.
    siteNav.querySelectorAll('a').forEach((link) => {
        link.addEventListener('click', () => {
            siteNav.classList.remove('is-open');
            menuToggle.setAttribute('aria-expanded', 'false');
            menuToggle.setAttribute('aria-label', 'Abrir menu');
        });
    });

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && siteNav.classList.contains('is-open')) {
            siteNav.classList.remove('is-open');
            menuToggle.setAttribute('aria-expanded', 'false');
            menuToggle.setAttribute('aria-label', 'Abrir menu');
            menuToggle.focus();
        }
    });
}