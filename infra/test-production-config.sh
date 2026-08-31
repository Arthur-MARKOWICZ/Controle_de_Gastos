#!/bin/sh
set -eu

readonly ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
readonly COMPOSE_FILE="$ROOT_DIR/compose.yaml"
readonly WORKFLOW_FILE="$ROOT_DIR/.github/workflows/deploy.yml"
readonly NGINX_HTTP_TEMPLATE="$ROOT_DIR/infra/nginx/controle-gastos.http.conf.template"
readonly NGINX_HTTPS_TEMPLATE="$ROOT_DIR/infra/nginx/controle-gastos.https.conf.template"
readonly INSTALL_SCRIPT="$ROOT_DIR/infra/nginx/install-https.sh"

fail() {
  printf 'ERRO: %s\n' "$1" >&2
  exit 1
}

assert_contains() {
  file=$1
  pattern=$2
  message=$3
  grep -F -- "$pattern" "$file" >/dev/null || fail "$message"
}

for file in "$COMPOSE_FILE" "$WORKFLOW_FILE" "$NGINX_HTTP_TEMPLATE" "$NGINX_HTTPS_TEMPLATE" "$INSTALL_SCRIPT"; do
  test -f "$file" || fail "arquivo obrigatório ausente: $file"
done

assert_contains "$COMPOSE_FILE" 'context: ./backend' 'Compose deve declarar o contexto local de build do backend'
assert_contains "$COMPOSE_FILE" 'context: ./web' 'Compose deve declarar o contexto local de build da web'

readonly RESOLVED_COMPOSE=$(mktemp)
trap 'rm -f "$RESOLVED_COMPOSE"' EXIT HUP INT TERM

POSTGRES_DB=controle_gastos \
POSTGRES_USER=controle_gastos \
POSTGRES_PASSWORD=validation-only-password \
POSTGRES_BIND_ADDRESS=127.0.0.1 \
BACKEND_BIND_ADDRESS=127.0.0.1 \
WEB_BIND_ADDRESS=127.0.0.1 \
AUTH_COOKIE_SECURE=true \
AUTH_COOKIE_NAME=__Secure-refresh_token \
AUTH_JWT_SECRET=validation-only-jwt-secret-at-least-32-bytes \
AUTH_ALLOWED_ORIGINS=https://gastos.example.com \
AUTH_ATTEMPT_HMAC_SECRET=validation-only-attempt-secret-at-least-32-bytes \
NEXT_PUBLIC_API_URL=https://gastos.example.com \
docker compose --project-name controle-gastos -f "$COMPOSE_FILE" config >"$RESOLVED_COMPOSE"

test "$(grep -Fc 'host_ip: 127.0.0.1' "$RESOLVED_COMPOSE")" -eq 3 \
  || fail 'as três portas do Compose devem permanecer publicadas apenas em loopback'
assert_contains "$RESOLVED_COMPOSE" 'target: 5432' 'PostgreSQL deve publicar somente sua porta esperada'
assert_contains "$RESOLVED_COMPOSE" 'target: 8080' 'backend deve publicar somente sua porta esperada'
assert_contains "$RESOLVED_COMPOSE" 'target: 3000' 'web deve publicar somente sua porta esperada'
assert_contains "$RESOLVED_COMPOSE" 'AUTH_COOKIE_SECURE: "true"' 'cookie de produção deve exigir HTTPS'
assert_contains "$RESOLVED_COMPOSE" 'AUTH_COOKIE_NAME: __Secure-refresh_token' 'cookie de produção deve usar o prefixo __Secure-'
assert_contains "$RESOLVED_COMPOSE" 'AUTH_ALLOWED_ORIGINS: https://gastos.example.com' 'CORS deve aceitar somente a origem pública'

assert_contains "$WORKFLOW_FILE" 'PUBLIC_APP_URL: ${{ vars.PUBLIC_APP_URL }}' 'workflow deve receber PUBLIC_APP_URL do GitHub Environment'
assert_contains "$WORKFLOW_FILE" 'AUTH_JWT_SECRET: ${{ secrets.AUTH_JWT_SECRET }}' 'workflow deve receber AUTH_JWT_SECRET do GitHub Secrets'
assert_contains "$WORKFLOW_FILE" 'POSTGRES_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}' 'workflow deve receber POSTGRES_PASSWORD do GitHub Secrets'
assert_contains "$WORKFLOW_FILE" "'bash -se'" 'segredos devem ser enviados pelo stdin do SSH, não na linha de comando'
assert_contains "$WORKFLOW_FILE" "ALTER ROLE %I WITH PASSWORD %L" 'workflow deve aplicar a senha ao volume PostgreSQL já inicializado'
assert_contains "$WORKFLOW_FILE" 'wait_for_http http://127.0.0.1:8080/actuator/health/readiness backend' 'deploy deve aguardar readiness do backend'

for template in "$NGINX_HTTP_TEMPLATE" "$NGINX_HTTPS_TEMPLATE"; do
  assert_contains "$template" '__PUBLIC_APP_DOMAIN__' "template Nginx deve possuir o placeholder de domínio: $template"
  assert_contains "$template" 'access_log off;' "site não deve persistir IP e rota em access log sem retenção definida: $template"
done

assert_contains "$NGINX_HTTP_TEMPLATE" 'return 503;' 'estágio HTTP não deve expor a aplicação antes do certificado'
assert_contains "$NGINX_HTTPS_TEMPLATE" 'proxy_pass http://127.0.0.1:8080;' 'Nginx deve encaminhar /api ao backend somente após habilitar TLS'
assert_contains "$NGINX_HTTPS_TEMPLATE" 'proxy_pass http://127.0.0.1:3000;' 'Nginx deve encaminhar a web ao Next.js somente após habilitar TLS'
assert_contains "$NGINX_HTTPS_TEMPLATE" 'proxy_set_header X-Forwarded-For $remote_addr;' 'Nginx não deve aceitar cadeia X-Forwarded-For fornecida pelo cliente'
assert_contains "$NGINX_HTTPS_TEMPLATE" 'listen 443 ssl;' 'configuração final deve publicar HTTPS'
assert_contains "$NGINX_HTTPS_TEMPLATE" 'return 308 https://__PUBLIC_APP_DOMAIN__$request_uri;' 'HTTP deve redirecionar para o domínio configurado'

sh -n "$INSTALL_SCRIPT"
assert_contains "$INSTALL_SCRIPT" 'vps-certbot-renew.timer' 'instalador deve habilitar a renovação automática do Certbot'
assert_contains "$INSTALL_SCRIPT" 'OnCalendar=*-*-* 00,12:00:00' 'instalador deve verificar renovação duas vezes ao dia'
assert_contains "$INSTALL_SCRIPT" 'rollback_on_error' 'instalador deve restaurar a configuração anterior quando falhar'

printf 'Configuração de produção validada.\n'
