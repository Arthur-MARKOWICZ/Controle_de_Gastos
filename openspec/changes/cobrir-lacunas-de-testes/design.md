# Design

## Context

Estado observado em 2026-11-13 (ver proposal.md — Why):

- `make check` roda backend, web, mcp, deploy e desktop (se houver `cargo`),
  mas **não** `mobile-check`.
- `.github/workflows/pull-request.yml` tem jobs `backend`, `web`, `mobile` e
  `configuracao`. O mobile **já** roda no PR (`make mobile-check`), ao contrário
  do que a issue afirma; o que falta no CI são **mcp** e **desktop**.
- Nenhum módulo mede cobertura: não há JaCoCo em `backend/build.gradle.kts`,
  Kover no mobile nem bloco `coverage` nos `vitest.config.*` de web, desktop e
  mcp.
- Backend separa `unitTest` (sem Docker, usado no pre-commit) de `test`
  (inclui `*IntegrationTest` com Testcontainers, usado no CI).
- `mobile/androidApp` não tem `src/test`; só `main` e `debug`.
- `desktop/session` já concentra a lógica Rust testável (cookies, http, ipc,
  session, vault, theme); `src-tauri` tem só `lib.rs`/`main.rs`.
- Endpoints sem teste de API confirmados: alertas, `mfa/status`,
  `mfa/recovery-codes`, `oauth/mobile-handoff` e participantes. Os contratos
  descritos nas specs foram lidos do código atual (handlers de exceção de
  `identity`, `notifications`, `sharing` e `envelopes`).

## Goals / Non-Goals

**Goals:**
- Cobertura medida e com limite em todos os módulos, sem duplicar a execução
  das suítes.
- Testes novos descobrem defeitos reais; o contrato atual é a referência.
- Entregar em fatias independentes, cada uma deixando `make check` verde.

**Non-Goals:**
- Atingir o padrão de sete testes em unidades fora da lista da issue nesta
  change; elas ficam sujeitas ao padrão quando forem tocadas.
- Mudar formato de `ProblemDetail`, status HTTP ou mensagens existentes.

## Decisions

### D1. Ferramentas de cobertura
- **Backend**: plugin `jacoco` do Gradle; `jacocoTestReport` depende de
  `test`, e `jacocoTestCoverageVerification` entra no `check`. O relatório
  combina unitários e integração, porque vários services só têm cobertura via
  API. *Alternativa*: medir só `unitTest` — descartada porque puniria módulos
  cobertos corretamente por integração.
- **Mobile**: Kover (`org.jetbrains.kotlinx.kover`) em `:shared` e
  `:androidApp`, com `koverVerify`. *Alternativa*: JaCoCo direto no Android —
  pior suporte a KMP.
- **Web, desktop, MCP**: `@vitest/coverage-v8` com `thresholds` no
  `vitest.config.*`; scripts `test:coverage` novos, e os alvos `*-check` do
  Makefile passam a usá-los. `pnpm test` simples continua sem cobertura para o
  pre-commit ficar rápido.
- **Rust (`desktop/session`)**: sem limite nesta change; `cargo test` continua
  obrigatório. *Alternativa*: `cargo-llvm-cov` — adiado por exigir toolchain
  extra no runner.

### D2. Limites iniciais
Primeiro mede-se a linha de base, depois fixa-se o limite. Meta da issue:
80% linhas / 70% branches no backend e nas libs (`web/lib`, `mcp/src`,
`desktop/src/lib`, `mobile/shared`). Se a linha de base ficar abaixo, o limite
é a linha de base arredondada para baixo (catraca) e o ADR registra a meta e o
plano de subida. Exclusões (classes `*Application`, configuração, código
gerado, `main.tsx`, `layout.tsx`) ficam listadas no próprio arquivo de build.

### D3. CI
- Novos jobs `mcp` (`make mcp-check`) e `desktop` (instala Rust estável e as
  dependências do crate `session`, roda `make desktop-check`).
- Cada job publica `coverage/` ou `build/reports/jacoco|kover` com
  `actions/upload-artifact` fixado por SHA, `if: ${{ !cancelled() }}`, como os
  jobs atuais.
- `make check` passa a incluir `mobile-check`. Em máquina sem Android SDK o
  alvo falha; isso é aceito porque o README já lista o SDK como pré-requisito.
  *Alternativa*: `mobile-skip` condicional como no desktop — descartada para não
  esconder o mobile, que é o alvo validável (AGENTS.md).

### D4. Organização dos testes
- **API backend**: novos `*ApiIntegrationTest` seguindo os existentes
  (Testcontainers + `FixedClockTestConfiguration`), um arquivo por controller.
  Cada um cobre 401, terceiro e, quando houver, participante.
- **Services de identidade**: testes unitários com dublês de repositório e
  `Clock` fixo; clientes OAuth testados contra servidor HTTP local (MockWebServer
  ou `MockRestServiceServer`) para erro 4xx/5xx, timeout e JSON inválido.
- **Domínio**: testes puros, sem Spring.
- **Web**: `lib/api.ts` testado com `fetch` simulado (401/403/409/422/5xx e
  falha de rede); hooks com `renderHook`; componentes com Testing Library,
  afirmando a mensagem renderizada.
- **Mobile**: gateways em `commonTest` com cliente HTTP falso; `androidApp`
  ganha `src/test` (JVM) para as classes listadas, isolando APIs do Android
  atrás das interfaces já existentes ou de dublês; sem Robolectric nem
  emulador.
- **Desktop/MCP**: Vitest com `invoke` do Tauri e `fetch` simulados.

### D5. Defeitos revelados
Se um teste novo contradisser a spec (por exemplo, resgate concorrente de
handoff emitindo duas sessões), a correção entra na mesma fatia com TDD: o
teste falhando vem primeiro. Se a correção mudar contrato público, a fatia para
e a spec é revista antes.

### D6. ADR
ADR-0030 "Padrão mínimo de testes e cobertura" registra: o padrão 1+3+3, a
regra de autorização em endpoints, as ferramentas de D1, os limites e a
catraca de D2.

## Risks / Trade-offs

- [Volume grande, PRs difíceis de revisar] → seguir o fatiamento de
  `tasks.md`; cada fatia é revisável e deixa o check verde.
- [Integração backend mais lenta no CI] → reaproveitar o contexto Spring
  (mesma configuração de teste) para não multiplicar containers.
- [Testes que só inflam contagem] → a revisão aplica a spec
  `quality/test-standard`: erro sem asserção exata não conta.
- [Job desktop pesado por causa do Rust] → cache do Cargo e build só do crate
  `session`, sem WebKitGTK.
- [`make check` local quebra sem Android SDK] → documentar no README; o
  pre-commit continua só com `:shared:jvmTest`.
- [Limite fixado alto demais trava PRs] → catraca a partir da linha de base
  medida.

## Migration Plan

Sem migração de dados. Ordem: infraestrutura de cobertura e CI (sem limite,
só relatório) → testes por fatia → ativar os limites após a última fatia de
cada módulo. Reverter é remover o bloco de verificação de cobertura do módulo.

## Open Questions

- Ferramenta de cobertura Rust (`cargo-llvm-cov`) e limite para o crate
  `session`: decidir depois da primeira execução do job desktop.
