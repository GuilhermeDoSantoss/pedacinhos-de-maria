export function qs(selector, scope = document) {
    return scope.querySelector(selector);
}
export function qsa(selector, scope = document) {
    return Array.from(scope.querySelectorAll(selector));
}
export function show(element) {
    element.classList.remove('hidden');
}
export function hide(element) {
    element.classList.add('hidden');
}
export function createElement(tag, attributes = {}, children = []) {
    const element = document.createElement(tag);
    for (const [key, value] of Object.entries(attributes)) {
        if (key === 'className') element.className = value;
        else if (key === 'dataset') Object.assign(element.dataset, value);
        else if (key.startsWith('on') && typeof value === 'function') element.addEventListener(key.slice(2).toLowerCase(), value);
        else if (typeof value === 'boolean') element[key] = value;
        else element.setAttribute(key, value);
    }
    for (const child of children) element.append(child instanceof Node ? child : document.createTextNode(child));
    return element;
}
export function formatCurrency(value) {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}

/**
 * Usado por usersView.js para exibir "Cadastrado em <data>" em cada card
 * de usuário. Trata com segurança um isoString ausente ou malformado —
 * sem essa guarda, `Intl.DateTimeFormat.format()` lança RangeError sobre
 * uma Invalid Date e quebra a renderização do card inteiro.
 */
export function formatDate(isoString) {
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) {
        return '-';
    }
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(date);
}
