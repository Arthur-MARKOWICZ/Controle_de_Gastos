# Plano de teste de deploy por contêineres

## Escopo deste teste

O GitHub Actions valida o projeto, constrói as imagens da API e da web, envia
somente essas imagens para a VPS por SSH e executa o único `compose.yaml` já
instalado em `/srv/controle-gastos`. O Compose contém exclusivamente
PostgreSQL, backend e web.

Não configurar ainda domínio, HTTPS, Caddy/Nginx, backup, observabilidade,
limites de recursos ou cadastro público. As portas ficam em loopback por
padrão; use túnel SSH para testar de fora sem expor a aplicação.

## Preparação única da VPS

1. Instale Docker Engine com Docker Compose v2 e `curl`.
2. Crie o usuário `deploy`, adicione-o ao grupo `docker` e dê a ele posse de
   `/srv/controle-gastos`.
3. Copie uma única vez `compose.yaml` e `infra/deploy.sh` para esse diretório,
   mantendo a mesma estrutura do repositório.
4. Baixe a imagem do banco: `docker pull postgres:18-alpine`.
5. Cadastre a chave pública exclusiva do GitHub Actions em
   `~deploy/.ssh/authorized_keys`.
6. Gere a entrada de host da VPS com a mesma porta e o mesmo host que serão
   usados em `DEPLOY_PORT` e `DEPLOY_HOST`:

   ```bash
   ssh-keyscan -p <porta-ssh> -t ed25519 -H <host-ou-ip>
   ```

   Confira a impressão digital obtida por um canal confiável, por exemplo no
   console da VPS com `sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`.
   Salve a linha completa retornada por `ssh-keyscan` como o secret multiline
   `DEPLOY_KNOWN_HOSTS`. Em porta diferente de 22, não omita `-p`: a entrada do
   `known_hosts` será associada a `[host]:porta`.

O primeiro job não copia arquivos de configuração: depois desse preparo, ele
envia apenas as duas imagens e chama o script já presente na VPS.

## Variáveis de ambiente

### GitHub Environment `deploy-test`

| Nome | Tipo | Obrigatória | Uso |
| --- | --- | --- | --- |
| `DEPLOY_HOST` | Secret | Sim | Host ou IP da VPS |
| `DEPLOY_PORT` | Secret | Sim | Porta SSH |
| `DEPLOY_USER` | Secret | Sim | Usuário `deploy` |
| `DEPLOY_SSH_PRIVATE_KEY` | Secret | Sim | Chave privada exclusiva da Action |
| `DEPLOY_KNOWN_HOSTS` | Secret | Sim | Linha `known_hosts` verificada da VPS, gerada para o mesmo host e porta |
| `TEST_API_URL` | Variable | Não | URL gravada no bundle web; padrão `http://localhost:8080` |

### Compose na VPS

Para reduzir o número de variáveis neste teste, a Action usa a tag fixa
`latest` nas duas imagens e a sobrescreve na VPS a cada deploy.

| Nome | Obrigatória neste teste? | Padrão de teste | Finalidade |
| --- | --- | --- | --- |
| `HOST_BIND_ADDRESS` | Não | `127.0.0.1` | Interface das portas 3000, 5432 e 8080 |
| `POSTGRES_DB` | Não | `controle_gastos` | Nome do banco |
| `POSTGRES_USER` | Não | `controle_gastos` | Usuário do banco |
| `POSTGRES_PASSWORD` | Não | valor de teste | Senha do banco; trocar antes de qualquer uso real |
| `AUTH_COOKIE_SECURE` | Não | `false` | Permite HTTP somente neste teste |
| `AUTH_COOKIE_NAME` | Não | `refresh_token` | Nome do cookie de refresh |
| `AUTH_JWT_ISSUER` | Não | `controle-gastos-api` | Emissor do access token |
| `AUTH_JWT_AUDIENCE` | Não | `controle-gastos-clients` | Audiência do access token |
| `AUTH_JWT_SECRET` | Não | valor exclusivo de teste | Segredo HS256 do access token; substituir por 32+ bytes aleatórios antes de uso real |
| `AUTH_ACCESS_TOKEN_LIFETIME` | Não | `15m` | Vida do access token |
| `AUTH_REFRESH_IDLE_LIFETIME` | Não | `30d` | Inatividade máxima do refresh |
| `AUTH_SESSION_ABSOLUTE_LIFETIME` | Não | `365d` | Vida máxima da sessão |
| `AUTH_ALLOWED_ORIGINS` | Não | `http://localhost:3000` | Origem CORS permitida |
| `AUTH_ATTEMPT_HMAC_SECRET` | Não | valor de teste | Segredo do rate limit; trocar antes de uso real |
| `NEXT_PUBLIC_API_URL` | Não | `http://localhost:8080` | URL da API no runtime web; o valor de build vem de `TEST_API_URL` |

Não há variável obrigatória no Compose para este teste: todas têm valores
padrão exclusivos desse ambiente. A tag `latest` simplifica a validação, mas
não fornece rastreabilidade ou rollback confiável; isso será revisto antes da
configuração de produção.

## Critério de sucesso

1. Um push na `main` termina o workflow verde.
2. Na VPS, `docker compose -f compose.yaml ps` mostra os três serviços.
3. Por túnel SSH, `curl http://localhost:8080/actuator/health/readiness` retorna
   estado `UP` e a web responde em `http://localhost:3000`.
4. Nenhuma porta é aberta no firewall público durante o teste.

## Próxima etapa

Depois deste teste, criar uma nova decisão para proxy HTTPS, TLS, limites de
recursos, gestão de segredos de runtime, backup e monitoramento. Não reutilize
os valores padrão deste documento em produção.
