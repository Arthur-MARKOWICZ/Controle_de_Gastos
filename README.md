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
2. Inicie PostgreSQL, API e web: `make infra-up`.
3. Para desenvolvimento fora dos contêineres, inicie somente o PostgreSQL com
   `docker compose up -d postgres`, depois use `./gradlew bootRun` em `backend/`
   e `pnpm dev` em `web/`.
4. Abra `mobile/` no Android Studio e execute `androidApp`.

## Comandos

| Comando | Descrição |
|---|---|
| `make test` | Executa testes de backend e web |
| `make check` | Executa testes, lint e builds verificáveis neste ambiente |
| `make infra-up` | Inicia PostgreSQL, API e web |
| `make infra-down` | Para a infraestrutura local |
| `cd backend && ./gradlew test` | Testes Java e arquitetura modular |
| `cd web && pnpm test` | Testes unitários da web |
| `cd web && pnpm lint` | Lint da web |

O ambiente local usa uma chave JWT efêmera e cookie sem `Secure` apenas porque
serve HTTP. Em produção, gere um par RSA PKCS#8/X.509 fora do repositório,
configure os caminhos absolutos da `.env` e suba com
`docker compose -f compose.yaml -f compose.production.yaml up -d`. A chave
privada é montada somente como leitura. O segredo HMAC também deve ser aleatório
e fornecido pelo ambiente.

Cadastro não verifica e-mail e recuperação de senha ainda não existe. Não abra
o cadastro ao público antes dos requisitos jurídicos e operacionais listados em
[`docs/privacy/requisitos-de-seguranca.md`](docs/privacy/requisitos-de-seguranca.md).
Rotação de chaves, benchmark Argon2id e retenção estão descritos em
[`docs/privacy/operacao-de-autenticacao.md`](docs/privacy/operacao-de-autenticacao.md).

## Backend de renda

O módulo `income` permite configurar a renda do mês corrente, consultar o valor
efetivo em qualquer mês e navegar pelo histórico de alterações. Dinheiro usa
`BigDecimal`/`NUMERIC(19,2)` e strings decimais na API. Consulte o contrato em
[`docs/api/openapi.yaml`](docs/api/openapi.yaml) e a decisão em
[`docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md`](docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md).

## Estado do esqueleto

O alvo Android é a primeira plataforma móvel verificável. O código iOS é preparado no KMP, mas não é considerado testado sem macOS/Xcode. Consulte [`docs/decisions/0004-kmp-android-primeiro.md`](docs/decisions/0004-kmp-android-primeiro.md).

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
