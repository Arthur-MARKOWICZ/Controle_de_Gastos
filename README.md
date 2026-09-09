# Controle de gastos e verbas

Fundação de um sistema web e móvel para reservar renda em verbas acumuláveis, registrar gastos e compartilhar verbas com autorização explícita.

Leia primeiro [`docs/ideas/fundacao-tecnica.md`](docs/ideas/fundacao-tecnica.md). Agentes de IA devem seguir [`AGENTS.md`](AGENTS.md).

## Arquitetura

```text
web (Next.js) ─────┐
                   ├── API Spring Modulith ── PostgreSQL
mobile (KMP) ──────┘          │
                              └── identity: e-mail/senha, JWT e sessões
```

- `backend/`: regras, REST, persistência e integrações.
- `web/`: configuração e relatórios.
- `mobile/`: lançamentos, saldos, alertas e histórico.
- `infra/`: serviços locais e arquivos de deploy.
- `docs/`: intenção, ADRs, privacidade e API.

## Pré-requisitos

- Docker 29+ e Docker Compose.
- Node.js 24+ e pnpm 11+ para a web.
- JDK 25, ou Docker, para o backend.
- Android Studio com SDK Android para o mobile.
- macOS/Xcode somente quando o alvo iOS for validado.

## Início rápido

1. Copie `.env.example` para `.env` e troque as senhas locais.
2. Inicie PostgreSQL: `make infra-up`.
3. Para desenvolvimento fora dos contêineres, inicie somente o PostgreSQL com
   `docker compose up -d postgres`, depois use `./gradlew bootRun` em `backend/`
   e `pnpm dev` em `web/`.
4. Para executar API e web em contêineres locais, use
   `docker compose up --build`. O Compose reconstrói as imagens a partir de
   `backend/` e `web/` antes de recriar os serviços afetados.
5. Abra `mobile/` no Android Studio e execute `androidApp`.

### Ambientes da API no Android

O app escolhe a URL da API em tempo de build, a partir de `mobile/.env`. Copie o
exemplo versionado e ajuste os valores:

```bash
cp mobile/.env.example mobile/.env
```

```properties
API_ENV=local
API_BASE_URL_LOCAL=http://10.0.2.2:8080
API_BASE_URL_PROD=https://<SUBDOMINIO>
```

Alterne entre os ambientes trocando `API_ENV` para `local` ou `prod`. A
precedência é `-PAPI_ENV`/`-PAPI_BASE_URL` na linha de comando, depois o `.env`,
depois `API_BASE_URL` em `mobile/local.properties` e, por fim, o emulador.

Em `prod`, use somente a origem HTTPS, sem porta, caminho, query ou barra final —
por exemplo `https://gastos.exemplo.com`. O build rejeita as demais formas: a API
fica em loopback atrás do Nginx e a porta 8080 não é publicada, conforme o
[runbook de HTTPS](docs/deploy/https-producao.md). Não use placeholders de
configuração do Spring, como `${api_base_url:...}`: o Gradle não os expande e
interrompe o build porque o valor não é uma URL HTTP(S).

O certificado do subdomínio deve ser público e válido. O build release exige uma
URL HTTPS. O build debug libera HTTP apenas para os hosts em
`mobile/androidApp/src/debug/res/xml/network_security_config.xml` — o emulador e
o `localhost`; acrescente ali o IP da sua máquina para testar em aparelho físico
contra a API local.

## Comandos

| Comando | Descrição |
|---|---|
| `make test` | Executa testes de backend e web |
| `make check` | Executa testes, lint e builds verificáveis neste ambiente |
| `make infra-up` | Inicia PostgreSQL local |
| `make infra-down` | Para a infraestrutura local |
| `cd backend && ./gradlew test` | Testes Java e arquitetura modular |
| `cd web && pnpm test` | Testes unitários da web |
| `cd web && pnpm lint` | Lint da web |

O ambiente de teste usa uma chave JWT efêmera e cookie sem `Secure` apenas
porque serve HTTP. Ele não é configuração de produção.

O cadastro não verifica o e-mail imediatamente; uma recuperação de senha bem-sucedida
o confirma e revoga todas as sessões existentes. Não abra o cadastro ao público antes
dos requisitos jurídicos e operacionais listados em
[`docs/privacy/requisitos-de-seguranca.md`](docs/privacy/requisitos-de-seguranca.md).
Rotação de chaves, benchmark Argon2id e retenção estão descritos em
[`docs/privacy/operacao-de-autenticacao.md`](docs/privacy/operacao-de-autenticacao.md).

MFA por TOTP é opcional e configurável pela conta autenticada (web em
`/conta/seguranca`, mobile no botão "Segurança" do painel). Além do
`AUTH_JWT_SECRET`, produção exige `AUTH_TOTP_ENCRYPTION_KEY` (32 bytes em
base64, `openssl rand -base64 32`) para cifrar os segredos TOTP; veja
[`docs/decisions/0019-mfa-totp.md`](docs/decisions/0019-mfa-totp.md) e a
rotação dessa chave em
[`docs/privacy/operacao-de-autenticacao.md`](docs/privacy/operacao-de-autenticacao.md).

## Deploy

O GitHub Actions valida o projeto, constrói e transfere as imagens por SSH e
atualiza somente os containers do projeto `controle-gastos`. Em produção,
PostgreSQL, API e web ficam em loopback e um Nginx central da VPS publica cada
aplicativo por subdomínio com Certbot/HTTPS. Consulte o
[`runbook de HTTPS`](docs/deploy/https-producao.md), o
[`ADR-012`](docs/decisions/0012-nginx-central-e-https-por-subdominio.md) e o
[`registro do teste inicial`](docs/deploy/teste-entrega-conteineres.md).

## Backend de renda

O módulo `income` permite configurar a renda do mês corrente, consultar o valor
efetivo em qualquer mês e navegar pelo histórico de alterações. Dinheiro usa
`BigDecimal`/`NUMERIC(19,2)` e strings decimais na API. Consulte o contrato em
[`docs/api/openapi.yaml`](docs/api/openapi.yaml) e a decisão em
[`docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md`](docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md).

## Estado do esqueleto

O alvo Android é a primeira plataforma móvel verificável. O código iOS é preparado no KMP, mas não é considerado testado sem macOS/Xcode. Consulte [`docs/decisions/0004-kmp-android-primeiro.md`](docs/decisions/0004-kmp-android-primeiro.md).

Web e mobile oferecem aparência `Sistema`, `Claro` e `Escuro`. A preferência é
mantida somente no dispositivo, sem sincronização com a API, conforme o
[`ADR-013`](docs/decisions/0013-tema-adaptativo-e-preferencia-local.md).

O computador de desenvolvimento possui apenas a plataforma Android 37, enquanto
o AGP 9.1 declara validação oficial até 36.1. O APK compila, mas essa diferença
deve ser eliminada no ambiente de CI; ela não deve ser escondida por flags de
supressão.

A web permanece no ESLint 9 enquanto os plugins do Next.js 16.2 ainda não
declaram compatibilidade com ESLint 10. A migração deve ser reavaliada junto da
próxima atualização do Next.js; as dependências transitivas com correções de
segurança são fixadas em `web/pnpm-workspace.yaml`.

## Fontes técnicas principais

- Spring Boot: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Modulith: https://docs.spring.io/spring-modulith/reference/
- Next.js: https://nextjs.org/docs/app/getting-started/installation
- Kotlin Multiplatform: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- Spring Security Password Storage: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
- Imagem PostgreSQL: https://github.com/docker-library/docs/blob/master/postgres/content.md
- LGPD: https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm
