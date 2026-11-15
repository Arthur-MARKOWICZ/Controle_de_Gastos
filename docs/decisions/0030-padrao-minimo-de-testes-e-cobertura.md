# ADR-0030: Padrão mínimo de testes e cobertura medida em todos os canais

## Status

Aceito

## Data

2026-11-13

## Contexto

O mapeamento da issue #95 mostrou cobertura desigual entre backend, web,
mobile, desktop e MCP: endpoints sem teste de API, services de identidade sem
teste unitário, arquivos que só testam o caminho feliz e nenhuma medição de
cobertura. O workflow de PR também não rodava MCP nem desktop, e `make check`
não chamava a verificação mobile.

O TDD já é obrigatório para regra de negócio (AGENTS.md), mas sem um mínimo
verificável "tem teste" pode significar um único caso feliz.

## Decisão

### Padrão por unidade

Toda unidade com comportamento (service, entidade de domínio, endpoint, hook,
componente com lógica, controller de tela mobile, módulo do desktop/MCP) tem no
mínimo **7 testes**:

- ≥ 1 **caminho feliz**;
- ≥ 3 **situações indevidas** (estado inconsistente, abuso, falha de
  dependência, concorrência, dado excluído logicamente);
- ≥ 3 **erros esperados**, afirmando **qual** erro: tipo da exceção, status
  HTTP com o código do `ProblemDetail` ou a mensagem renderizada. "Não quebrou"
  ou "lançou alguma exceção" não conta.

Regras adicionais:

- Endpoints autenticados sempre testam **401 sem sessão** e **403/404 para
  terceiro**; recursos com participante testam também o participante em ação
  do proprietário.
- Unidades com dinheiro têm ao menos um caso de borda de `BigDecimal`
  (escala ≠ 2, zero, limite) — ADR-0006.
- Nomes de teste em português descrevendo o comportamento.
- DTOs, records de resposta e configuração declarativa ficam isentos.

### Cobertura

| Módulo | Ferramenta | Onde fica o limite | Exclusões |
|---|---|---|---|
| backend | JaCoCo (`./gradlew check`) | `backend/build.gradle.kts` | `ControleGastosApplication`, pacotes `config` |
| web | `@vitest/coverage-v8` (`pnpm test:coverage`) | `web/vitest.config.mts` | `layout.tsx`, `*.d.ts` |
| mcp | `@vitest/coverage-v8` | `mcp/vitest.config.ts` | `index.ts` (só liga o stdio) |
| desktop (frontend) | `@vitest/coverage-v8` | `desktop/vitest.config.ts` | `main.tsx`, `test-setup.ts` |
| mobile `shared` | Kover (`koverVerify`) | `mobile/shared/build.gradle.kts` | funções `@Composable` |
| mobile `androidApp` | Kover | `mobile/androidApp/build.gradle.kts` | `MainActivity`, `BuildConfig` |
| desktop `session` (Rust) | — | — | `cargo test` obrigatório, sem limite por enquanto |

O relatório do backend combina testes unitários e de integração, porque vários
services só são exercitados pela API. `pnpm test` continua sem cobertura para o
pre-commit ficar rápido; os alvos `*-check` do Makefile usam a cobertura.

### Limites e catraca

Meta: **80% de linhas e 70% de branches**. Cada módulo começa com o limite na
linha de base medida (arredondada para baixo) e o limite **só sobe**. Reduzir
um limite exige registrar o motivo neste ADR.

Linha de base medida em 2026-11-13, antes dos testes novos:

| Módulo | Linhas | Branches |
|---|---|---|
| backend | 84,29% | 65,09% |
| web | 57,43% | 62,98% |
| mcp | 54,10% | 65,64% |
| desktop (frontend) | 90,76% | 78,49% |
| mobile `shared` | 40,29% | 45,54% |
| mobile `androidApp` | 0% | 0% |

### CI

- `make check` inclui `mobile-check` (testes JVM do `shared` e do `androidApp`,
  `koverVerify` e build Android de debug). Exige Android SDK, já pré-requisito
  do README.
- O workflow de PR roda backend, web, mobile, **mcp** e **desktop** e publica
  o relatório de cobertura de cada um como artefato, mesmo quando falha.

## Consequências

- Testes novos e alterados seguem o padrão; unidades antigas fora da lista da
  issue #95 entram no padrão quando forem tocadas.
- O pipeline de PR fica mais longo (jobs mcp e desktop), em paralelo aos atuais.
- `make check` local passa a exigir Android SDK.
- Cobertura Rust (`cargo-llvm-cov`) fica para depois da primeira execução do
  job desktop.
- iOS continua não validado (ADR-0004).
