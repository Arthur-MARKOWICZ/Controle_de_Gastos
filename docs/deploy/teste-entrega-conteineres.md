# Plano de teste de deploy por contêineres

## Escopo deste teste

O primeiro job do GitHub Actions valida o projeto. Depois de aprovado, o job
de deploy constrói as imagens da API e da web, prepara `/srv/controle-gastos`,
envia e valida o `compose.yaml`, transfere as imagens por SSH e atualiza o
projeto Compose `controle-gastos`. O Compose contém exclusivamente PostgreSQL,
backend e web.

Não configurar ainda domínio, HTTPS, Caddy/Nginx, backup, observabilidade,
limites de recursos ou cadastro público. As portas ficam em loopback por
padrão; use túnel SSH para testar de fora sem expor a aplicação.

## Preparação única da VPS

1. Instale Docker Engine com Docker Compose v2 e `curl`.
2. Crie o usuário `deploy` e adicione-o ao grupo `docker`.
3. Permita que esse usuário crie o diretório do aplicativo sem interação. Como
   `root`, abra `visudo -f /etc/sudoers.d/controle-gastos-deploy` e adicione:

   ```sudoers
   deploy ALL=(root) NOPASSWD: /usr/bin/install -d -o deploy -g deploy -m 0750 /srv/controle-gastos
   ```

   Valide com `visudo -cf /etc/sudoers.d/controle-gastos-deploy`. Não armazene
   senha de `sudo` no GitHub.
4. Cadastre a chave pública exclusiva do GitHub Actions em
   `~deploy/.ssh/authorized_keys`.
5. Gere a entrada de host da VPS com a mesma porta e o mesmo host que serão
   usados em `DEPLOY_PORT` e `DEPLOY_HOST`:

   ```bash
   ssh-keyscan -p <porta-ssh> -t ed25519 -H <host-ou-ip>
   ```

   Confira a impressão digital obtida por um canal confiável, por exemplo no
   console da VPS com `sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`.
   Salve a linha completa retornada por `ssh-keyscan` como o secret multiline
   `DEPLOY_KNOWN_HOSTS`. Em porta diferente de 22, não omita `-p`: a entrada do
   `known_hosts` será associada a `[host]:porta`.

O job de deploy só inicia se o job de validação e testes terminar com sucesso.
No primeiro deploy, ele cria o diretório e baixa `postgres:18-alpine`; em todos
os deploys, envia o Compose como `compose.yaml.next`, valida o arquivo e só
então substitui o anterior.

O comando usa explicitamente o projeto `controle-gastos`. Backend e web são
recriados com as imagens novas, serviços órfãos desse projeto são removidos e o
PostgreSQL preserva o volume nomeado. Não é executado nenhum `prune`, nem são
enumerados ou removidos containers de outros projetos da VPS.

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
2. Na VPS, `docker compose --project-name controle-gastos -f compose.yaml ps`
   mostra os três serviços.
3. Por túnel SSH, `curl http://localhost:8080/actuator/health/readiness` retorna
   estado `UP` e a web responde em `http://localhost:3000`.
4. Nenhuma porta é aberta no firewall público durante o teste.

## Próxima etapa

Depois deste teste, criar uma nova decisão para proxy HTTPS, TLS, limites de
recursos, gestão de segredos de runtime, backup e monitoramento. Não reutilize
os valores padrão deste documento em produção.
