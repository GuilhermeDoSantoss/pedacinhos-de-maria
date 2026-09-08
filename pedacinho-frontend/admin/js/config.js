const isLocalEnvironment = ['localhost', '127.0.0.1'].includes(window.location.hostname);

export const CONFIG = {
    API_BASE_URL: isLocalEnvironment
        ? 'http://localhost:8080/api/v1'
        : 'https://pedacinho-de-maria.onrender.com/api/v1',
};