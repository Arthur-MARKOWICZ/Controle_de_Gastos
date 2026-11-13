# Proposal

## Why

O mapeamento da issue #95 (2026-11-12) mostrou que a suíte do monorepo é
desigual: endpoints REST, services de identidade, entidades de domínio, telas
web, gateways mobile e módulos do desktop/MCP não têm teste nenhum ou só cobrem
o caminho feliz, e nenhum módulo mede cobertura. Sem um padrão mínimo
verificável, regressões de autorização, dinheiro e sessão passam pelo CI sem
serem notadas.

## What Changes

- Define um **padrão mínimo de testes** por unidade com comportamento: ≥ 1
  caminho feliz, ≥ 3 situações indevidas e ≥ 3 erros esperados com o erro exato
  verificado (tipo de exceção, status HTTP + `type` do `ProblemDetail`, ou
  mensagem renderizada). Endpoints sempre incluem 401 sem sessão e 403/404 para
  terceiro não proprietário/participante; unidades com dinheiro incluem um caso
  de borda de `BigDecimal`.
- Adiciona **medição de cobertura** em todos os módulos: JaCoCo (backend),
  Kover (mobile `:shared`) e `@vitest/coverage-v8` (web, desktop, mcp), com
  relatório publicado no CI e limite mínimo que falha a verificação.
- Amplia a **verificação contínua**: `make check` passa a incluir
  `mobile-check`; o workflow de PR ganha jobs para `mcp-check` e
  `desktop-check` (hoje ausentes); o crate `desktop/session` continua no
  `cargo test` e a lógica testável do shell Tauri é isolada nele.
- Escreve os testes que faltam para as unidades listadas na issue (backend,
  web, mobile `commonMain` e `androidApp`, desktop, MCP) e completa os arquivos
  existentes abaixo do padrão.
- Fixa em spec o contrato de autorização dos endpoints hoje sem teste de API:
  alertas, status/códigos de recuperação de MFA, *handoff* OAuth mobile e
  participantes de verba.
- Registra a decisão em um ADR novo (padrão de testes e limites de cobertura).
- Correções de produção só entram quando um teste novo revelar um defeito; cada
  uma segue TDD e é descrita no próprio teste.

## Capabilities

### New Capabilities
- `quality/test-standard`: padrão mínimo de testes por unidade (feliz,
  indevidos, erros verificados), regras de autorização e de dinheiro nos testes
  e convenção de nomes em português.
- `quality/ci-verification`: medição de cobertura por módulo, limites mínimos e
  quais verificações (`make check` e workflow de PR) cada módulo executa.
- `notifications/alerts-api`: contrato observável de listagem e dispensa de
  alertas in-app, incluindo autorização por proprietário.
- `identity/mfa-api`: contrato de `GET /api/v1/mfa/status` e
  `POST /api/v1/mfa/recovery-codes`.
- `identity/oauth-mobile-handoff`: contrato de resgate do código de *handoff*
  OAuth pelo app mobile.
- `sharing/participants-api`: contrato HTTP de listar, convidar e remover
  participantes de uma verba.

### Modified Capabilities
- Nenhuma. `openspec/specs/` ainda não tem capacidades publicadas; os
  comportamentos de API acima já existem no código e passam a ser descritos
  pela primeira vez, sem mudar o contrato.

## Impact

- **Backend**: `build.gradle.kts` (plugin JaCoCo, `jacocoTestReport`,
  `jacocoTestCoverageVerification`); novos testes em `src/test` para
  `notifications`, `identity`, `sharing`, `envelopes`, `income`, `ledger`,
  `reporting`, `privacy`.
- **Web / desktop / MCP**: `vitest.config.*` com bloco `coverage`, dependência
  de desenvolvimento `@vitest/coverage-v8`, novos testes de libs, hooks e
  componentes.
- **Mobile**: plugin Kover em `mobile/shared` (e `androidApp`), testes em
  `commonTest`/`jvmTest` e testes unitários JVM no `androidApp`.
- **Desktop**: extração de lógica pura do `src-tauri` para o crate
  `desktop/session` quando necessário para testar.
- **CI/Makefile**: `Makefile` (`check` inclui `mobile-check`; alvos de
  cobertura), `.github/workflows/pull-request.yml` (jobs mcp e desktop, upload
  dos relatórios de cobertura).
- **Docs**: ADR novo em `docs/decisions/` e menção no `README.md` às novas
  verificações.
- Sem mudança de contrato público, de schema de banco ou de comportamento para
  o usuário final.

### ADRs relacionados
- ADR-0001 (monorepo e monólito modular): testes respeitam os limites de
  módulo; `ModularityTest` continua valendo.
- ADR-0006 (dinheiro decimal): casos de borda de `BigDecimal` nos testes.
- ADR-0010 (teste de entrega de contêineres): `deploy-check` permanece.
- ADR-0019, ADR-0020, ADR-0026: contratos de MFA, OAuth e compartilhamento
  descritos nas specs de API.
- ADR-0029 (cliente desktop Tauri): o shell `src-tauri` continua fora do
  `cargo test` por depender do WebKitGTK; só a lógica movida para o crate
  `session` passa a ser verificada. O ADR não precisa mudar.

## Fora do escopo

- Testes de iOS (sem macOS/Xcode) e testes instrumentados Android em emulador
  (`connectedAndroidTest`); `androidApp` recebe só testes JVM com dublês.
- Testes end-to-end de navegador (Playwright/Cypress) e testes de carga.
- Testes de mutação e ferramentas de análise estática novas.
- Refatorações de produção que não sejam necessárias para tornar uma unidade
  testável ou corrigir um defeito revelado por teste.
- Compilar e testar o shell `desktop/src-tauri` no CI.
- Cache, filas ou qualquer mudança de arquitetura.
