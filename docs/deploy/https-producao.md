# Deploy com HTTPS em VPS compartilhada

Este runbook publica o controle de gastos sob um subdomínio e mantém Nginx e
Certbot como infraestrutura compartilhada da VPS. Substitua `<DOMINIO>`,
`<EMAIL>` e `<PORTA_SSH>` pelos valores reais. O exemplo de domínio é
`gastos.exemplo.com`; não grave esse exemplo no GitHub Environment.

## 1. DNS e firewall

Crie um registro DNS `A` de `<DOMINIO>` apontando para `103.199.187.185` e
aguarde a propagação. Antes de emitir o certificado, confirme:

```bash
dig +short A <DOMINIO>
```

No hPanel da Hostinger, em **VPS → Security → Firewall**, mantenha regras TCP
para:

| Porta | Origem | Uso |
| --- | --- | --- |
| `<PORTA_SSH>` | Seu IP, quando possível | Administração e GitHub Actions |
| `80` | Anywhere | Desafio HTTP do Let's Encrypt e redirecionamento |
| `443` | Anywhere | Aplicação HTTPS |

Não abra 3000, 5432, 8080, 25, 465 ou 587. Se o UFW estiver ativo, replique as
regras nele; se estiver inativo, o firewall gerenciado da Hostinger continua
sendo uma camada separada. Confira na VPS com `sudo ufw status verbose` e
`sudo ss -lntp`.

## 2. GitHub Environment `production`

O workflow não usa mais o Environment `deploy-test`. Em **Settings →
Environments**, crie `production` e cadastre:

| Nome | Tipo | Obrigatória | Valor ou finalidade |
| --- | --- | --- | --- |
| `PUBLIC_APP_URL` | Variable | Sim | `https://<DOMINIO>`, sem barra final, porta ou caminho |
| `DEPLOY_HOST` | Secret | Sim | IP ou host SSH da VPS |
| `DEPLOY_PORT` | Secret | Sim | Porta SSH, normalmente `22` |
| `DEPLOY_USER` | Secret | Sim | Deve ser `deploy` |
| `DEPLOY_SSH_PRIVATE_KEY` | Secret | Sim | Chave privada exclusiva da Action |
| `DEPLOY_KNOWN_HOSTS` | Secret | Sim | Linha verificada do `ssh-keyscan` |
| `POSTGRES_DB` | Secret | Sim | Mantenha `controle_gastos` se o volume já existe |
| `POSTGRES_USER` | Secret | Sim | Mantenha `controle_gastos` se o volume já existe |
| `POSTGRES_PASSWORD` | Secret | Sim | Senha aleatória nova do banco |
| `AUTH_JWT_SECRET` | Secret | Sim | Segredo HS256 aleatório com pelo menos 32 bytes |
| `AUTH_ATTEMPT_HMAC_SECRET` | Secret | Sim | Segredo HMAC distinto, com pelo menos 32 bytes |
| `GMAIL_SMTP_USERNAME` | Secret | Sim | Conta Gmail remetente exclusiva do serviço |
| `GMAIL_SMTP_APP_PASSWORD` | Secret | Sim | App password de 16 dígitos da conta remetente |

Gere valores diferentes para as três credenciais:

```bash
openssl rand -base64 48
openssl rand -base64 48
openssl rand -base64 48
```

O workflow transmite os valores pelo `stdin` de uma sessão SSH, executa o
Compose com `COMPOSE_DISABLE_ENV_FILE=1` e não cria `.env` na VPS. O Docker
precisa manter as variáveis no metadado dos containers para reiniciá-los; por
isso, acesso ao socket Docker equivale a acesso aos segredos e deve continuar
restrito.

No primeiro deploy seguro sobre um volume existente, o workflow também executa
`ALTER ROLE` para aplicar `POSTGRES_PASSWORD`. `POSTGRES_DB` e `POSTGRES_USER`
não devem ser renomeados sem uma migração explícita.

### SMTP Gmail para recuperação de senha

Esta configuração prepara o deploy; ela não ativa recuperação de senha antes
do ADR correspondente e da implementação da feature. A conta remetente deve
ser exclusiva do serviço, ter a verificação em duas etapas ativada e usar uma
app password dedicada. O backend recebe `smtp.gmail.com`, porta `587`, STARTTLS
e o remetente igual a `GMAIL_SMTP_USERNAME`; host e porta não são Secrets.

O SMTP é exclusivamente tráfego de saída. Não crie regra de entrada para 25,
465 ou 587. Caso a política de saída da VPS seja restritiva, permita somente
TCP 587 da VPS para `smtp.gmail.com`. Antes do deploy, valide DNS e TLS sem
enviar credenciais:

```bash
getent ahosts smtp.gmail.com
openssl s_client -starttls smtp -connect smtp.gmail.com:587 -servername smtp.gmail.com </dev/null
```

App passwords são menos recomendadas pelo Google e são revogadas quando a senha
da conta Gmail muda. Para rotacionar, gere uma nova app password, atualize
`GMAIL_SMTP_APP_PASSWORD` no GitHub Environment, execute o deploy, valide o
envio com uma conta sintética e só então revogue a senha anterior. Nunca grave
a senha de app em `.env`, imagem, ticket, log ou exemplo. O workflow desativa o
rastreio de comandos antes de receber e transportar esses Secrets.

## 3. Deploy dos containers

Faça push para `main` ou execute manualmente **Validar e implantar aplicação**.
O job `validate` precisa passar antes do job `deploy`. Depois, confirme na VPS:

```bash
docker compose --project-name controle-gastos -f /srv/controle-gastos/compose.yaml ps
curl --fail http://127.0.0.1:3000/
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

Os três serviços devem estar ativos. `ss -lntp` deve mostrar 3000, 5432 e 8080
somente em `127.0.0.1`.

## 4. Instalação inicial do Nginx e Certbot

Esta etapa é administrativa e ocorre uma vez por subdomínio; o workflow comum
não altera o Nginx compartilhado. Na máquina local, copie somente os arquivos
de infraestrutura:

```bash
scp -P <PORTA_SSH> -r infra/nginx root@103.199.187.185:/root/controle-gastos-nginx
ssh -p <PORTA_SSH> root@103.199.187.185
```

Na VPS, execute:

```bash
chmod 0755 /root/controle-gastos-nginx/install-https.sh
/root/controle-gastos-nginx/install-https.sh <DOMINIO> <EMAIL>
```

O instalador:

1. instala Nginx e, quando ausente, Certbot com o plugin Nginx;
2. ativa apenas `/etc/nginx/sites-enabled/controle-gastos.conf`;
3. valida e recarrega o Nginx com configuração HTTP temporária;
4. emite o certificado pelo desafio HTTP;
5. ativa TLS, redireciona HTTP com status 308 e encaminha `/api/` e `/`;
6. instala um timer `systemd` que verifica a renovação duas vezes ao dia;
7. instala um hook que executa `nginx -t` antes de recarregar certificados;
8. executa `certbot renew --dry-run`.

Ele não remove o site padrão nem arquivos de outros aplicativos. Para cada app
futuro, use outro subdomínio e outro nome de arquivo em `sites-available`; só a
instância central do Nginx possui 80/443. Se emissão, validação ou renovação de
teste falhar, o instalador restaura o arquivo e o symlink anteriores deste
projeto e recarrega o Nginx somente se `nginx -t` aceitar a restauração.

## 5. Validação externa

```bash
curl --fail --head http://<DOMINIO>/
curl --fail --head https://<DOMINIO>/
curl --fail https://<DOMINIO>/api/v1/users/me
sudo nginx -t
sudo certbot certificates
sudo certbot renew --dry-run
```

O primeiro comando deve redirecionar para HTTPS. A rota autenticada pode
responder `401`, mas deve chegar ao backend por TLS. No navegador, valide login,
refresh e logout e confira que `__Secure-refresh_token` possui `Secure`,
`HttpOnly`, `SameSite=Strict` e path `/api/v1/auth`.

Também confirme externamente que 3000, 5432 e 8080 não aceitam conexão e que
containers de outros projetos permanecem ativos.

## Limites antes de usuários reais

HTTPS protege o transporte, mas não encerra as pendências de produção. Cadastro
público de terceiros continua bloqueado até revisão jurídica, aviso de
privacidade, canal do titular, retenção, exportação/exclusão, backup cifrado e
procedimento de incidentes descritos em
[`docs/privacy/requisitos-de-seguranca.md`](../privacy/requisitos-de-seguranca.md).
O estado auditável da nova borda está em
[`docs/privacy/operacao-da-borda-https.md`](../privacy/operacao-da-borda-https.md).

## Fontes operacionais

- [Hostinger — SSL com Certbot em VPS](https://www.hostinger.com/support/6865487-how-to-install-ssl-on-vps-using-certbot-at-hostinger/)
- [Hostinger — firewall gerenciado da VPS](https://www.hostinger.com/support/8172641-how-to-use-a-managed-vps-firewall-at-hostinger/)
- [Certbot — plugin Nginx e renovação automática](https://eff-certbot.readthedocs.io/en/stable/using.html)
- [Nginx — `proxy_pass` e cabeçalhos](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [Docker Compose — interpolação pelo ambiente](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)
- [PostgreSQL — `psql` `\\getenv` e `\\gexec`](https://www.postgresql.org/docs/current/app-psql.html)
