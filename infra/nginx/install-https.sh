#!/bin/sh
set -eu

usage() {
  printf 'Uso: sudo %s <dominio> <email-letsencrypt>\n' "$0" >&2
  exit 2
}

fail() {
  printf 'ERRO: %s\n' "$1" >&2
  exit 1
}

test "$#" -eq 2 || usage
test "$(id -u)" -eq 0 || fail "execute este script com sudo"

readonly DOMAIN=$1
readonly CERTBOT_EMAIL=$2
readonly SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
readonly HTTP_TEMPLATE="$SCRIPT_DIR/controle-gastos.http.conf.template"
readonly HTTPS_TEMPLATE="$SCRIPT_DIR/controle-gastos.https.conf.template"
readonly AVAILABLE_CONFIG=/etc/nginx/sites-available/controle-gastos.conf
readonly ENABLED_CONFIG=/etc/nginx/sites-enabled/controle-gastos.conf
readonly CERTIFICATE_DIR="/etc/letsencrypt/live/$DOMAIN"

printf '%s' "$DOMAIN" | grep -Eq '^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$' \
  || fail "domínio inválido: use um nome completo como gastos.exemplo.com"
printf '%s' "$CERTBOT_EMAIL" | grep -Eq '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' \
  || fail "e-mail inválido para avisos do Let's Encrypt"

test -f "$HTTP_TEMPLATE" || fail "template ausente: $HTTP_TEMPLATE"
test -f "$HTTPS_TEMPLATE" || fail "template ausente: $HTTPS_TEMPLATE"
command -v apt-get >/dev/null 2>&1 || fail "este instalador requer Ubuntu ou Debian com apt-get"

APT_UPDATED=false
apt_update_once() {
  if [ "$APT_UPDATED" = false ]; then
    apt-get update
    APT_UPDATED=true
  fi
}

if ! command -v nginx >/dev/null 2>&1; then
  apt_update_once
  DEBIAN_FRONTEND=noninteractive apt-get install -y nginx
fi

if ! command -v certbot >/dev/null 2>&1; then
  apt_update_once
  DEBIAN_FRONTEND=noninteractive apt-get install -y python3 python3-venv libaugeas0
  python3 -m venv /opt/certbot
  /opt/certbot/bin/pip install --upgrade pip
  /opt/certbot/bin/pip install certbot certbot-nginx
  ln -sfn /opt/certbot/bin/certbot /usr/local/bin/certbot
fi

readonly CERTBOT=$(command -v certbot)
"$CERTBOT" plugins 2>/dev/null | grep -F '* nginx' >/dev/null \
  || fail "a instalação atual do Certbot não possui o plugin Nginx"

install -d -m 0755 /etc/nginx/sites-available /etc/nginx/sites-enabled
systemctl enable --now nginx

readonly CONFIG_BACKUP=$(mktemp -d)
HAD_AVAILABLE=false
HAD_ENABLED=false
if [ -e "$AVAILABLE_CONFIG" ] || [ -L "$AVAILABLE_CONFIG" ]; then
  cp -a "$AVAILABLE_CONFIG" "$CONFIG_BACKUP/available"
  HAD_AVAILABLE=true
fi
if [ -e "$ENABLED_CONFIG" ] || [ -L "$ENABLED_CONFIG" ]; then
  cp -a "$ENABLED_CONFIG" "$CONFIG_BACKUP/enabled"
  HAD_ENABLED=true
fi

rollback_on_error() {
  status=$?
  trap - EXIT HUP INT TERM
  if [ "$status" -ne 0 ]; then
    rm -f "$AVAILABLE_CONFIG" "$ENABLED_CONFIG"
    if [ "$HAD_AVAILABLE" = true ]; then
      cp -a "$CONFIG_BACKUP/available" "$AVAILABLE_CONFIG"
    fi
    if [ "$HAD_ENABLED" = true ]; then
      cp -a "$CONFIG_BACKUP/enabled" "$ENABLED_CONFIG"
    fi
    if nginx -t; then
      systemctl reload nginx
    else
      printf 'AVISO: não foi possível validar o Nginx após restaurar a configuração anterior.\n' >&2
    fi
  fi
  rm -rf "$CONFIG_BACKUP"
  exit "$status"
}

trap rollback_on_error EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

render_config() {
  template=$1
  destination=$2
  temporary=$(mktemp)
  sed "s/__PUBLIC_APP_DOMAIN__/$DOMAIN/g" "$template" >"$temporary"
  install -o root -g root -m 0644 "$temporary" "$destination"
  rm -f "$temporary"
}

if [ ! -s "$CERTIFICATE_DIR/fullchain.pem" ] || [ ! -s "$CERTIFICATE_DIR/privkey.pem" ]; then
  render_config "$HTTP_TEMPLATE" "$AVAILABLE_CONFIG"
  ln -sfn "$AVAILABLE_CONFIG" "$ENABLED_CONFIG"
  nginx -t
  systemctl reload nginx

  "$CERTBOT" certonly \
    --nginx \
    --non-interactive \
    --agree-tos \
    --email "$CERTBOT_EMAIL" \
    --cert-name "$DOMAIN" \
    -d "$DOMAIN"
fi

render_config "$HTTPS_TEMPLATE" "$AVAILABLE_CONFIG"
ln -sfn "$AVAILABLE_CONFIG" "$ENABLED_CONFIG"
nginx -t
systemctl reload nginx

install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
readonly RELOAD_HOOK=/etc/letsencrypt/renewal-hooks/deploy/reload-nginx
readonly HOOK_TEMP=$(mktemp)
printf '%s\n' '#!/bin/sh' 'set -eu' 'nginx -t' 'systemctl reload nginx' >"$HOOK_TEMP"
install -o root -g root -m 0755 "$HOOK_TEMP" "$RELOAD_HOOK"
rm -f "$HOOK_TEMP"

readonly RENEW_SERVICE=/etc/systemd/system/vps-certbot-renew.service
readonly RENEW_TIMER=/etc/systemd/system/vps-certbot-renew.timer
readonly SERVICE_TEMP=$(mktemp)
readonly TIMER_TEMP=$(mktemp)
printf '%s\n' \
  '[Unit]' \
  'Description=Renovar certificados TLS gerenciados pelo Certbot' \
  'After=network-online.target' \
  'Wants=network-online.target' \
  '' \
  '[Service]' \
  'Type=oneshot' \
  "ExecStart=$CERTBOT renew --quiet" >"$SERVICE_TEMP"
printf '%s\n' \
  '[Unit]' \
  'Description=Verificar renovação dos certificados TLS duas vezes ao dia' \
  '' \
  '[Timer]' \
  'OnCalendar=*-*-* 00,12:00:00' \
  'RandomizedDelaySec=3600' \
  'Persistent=true' \
  '' \
  '[Install]' \
  'WantedBy=timers.target' >"$TIMER_TEMP"
install -o root -g root -m 0644 "$SERVICE_TEMP" "$RENEW_SERVICE"
install -o root -g root -m 0644 "$TIMER_TEMP" "$RENEW_TIMER"
rm -f "$SERVICE_TEMP" "$TIMER_TEMP"
systemctl daemon-reload
systemctl enable --now vps-certbot-renew.timer

"$CERTBOT" renew --cert-name "$DOMAIN" --dry-run

trap - EXIT HUP INT TERM
rm -rf "$CONFIG_BACKUP"
printf 'HTTPS configurado para https://%s\n' "$DOMAIN"
