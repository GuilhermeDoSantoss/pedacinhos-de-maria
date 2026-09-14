/**
 * Configuração central de ambiente do Admin.
 *
 * Em vez de manter uma lista de hostnames "locais" (abordagem frágil: já
 * falhou para 0.0.0.0, e falharia igualmente para um IP de rede local,
 * ::1, ou qualquer outra forma de acessar a página localmente), mantemos
 * uma lista pequena e conhecida dos hostnames REAIS de produção. Qualquer
 * hostname que não esteja nessa lista é tratado como ambiente local — não
 * importa a forma exata como o servidor estático local foi endereçado.
 */
const PRODUCTION_HOSTNAMES = ['pedacinho-admin.onrender.com'];

const isProductionEnvironment = PRODUCTION_HOSTNAMES.includes(window.location.hostname);

export const CONFIG = {
    API_BASE_URL: isProductionEnvironment
        ? 'https://pedacinho-de-maria.onrender.com/api/v1'
        : 'http://localhost:8080/api/v1',
};
