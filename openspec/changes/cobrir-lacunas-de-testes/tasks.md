# Tasks

> Padrão de cada unidade: spec `quality/test-standard` (1 feliz + 3 indevidos +
> 3 erros com asserção exata; endpoints com 401 e terceiro; borda de
> `BigDecimal` onde houver dinheiro; nomes em português). Testes que revelarem
> defeito seguem TDD: teste falhando, correção mínima, refatoração (design D5).

## 1. Infraestrutura de cobertura e CI

- [x] 1.1 Escrever `docs/decisions/0030-padrao-minimo-de-testes-e-cobertura.md` (padrão 1+3+3, regra de endpoints, ferramentas, limites e catraca) e verificar que o README o referencia
- [x] 1.2 Adicionar JaCoCo ao `backend/build.gradle.kts` (`jacocoTestReport` após `test`, verificação sem limite ainda, exclusões documentadas) e verificar que `./gradlew test jacocoTestReport` gera `build/reports/jacoco`
- [x] 1.3 Adicionar Kover a `mobile/shared` e `mobile/androidApp` e verificar que `./gradlew koverHtmlReport` gera relatório dos dois módulos
- [x] 1.4 Adicionar `@vitest/coverage-v8`, bloco `coverage` e script `test:coverage` em web, desktop e mcp e verificar que cada `pnpm test:coverage` gera `coverage/`
- [ ] 1.5 Fazer `web-check`, `mcp-check`, `desktop-test` e `mobile-check` usarem as tarefas de cobertura, incluir `mobile-check` em `make check` e verificar com `make check` local
- [x] 1.6 Adicionar jobs `mcp` e `desktop` ao `.github/workflows/pull-request.yml` (actions fixadas por SHA, cache de pnpm/Cargo) e upload de cobertura em todos os jobs com `if: ${{ !cancelled() }}`; verificar com `pnpm exec lefthook validate` e com `actionlint` ou execução do workflow
- [x] 1.7 Medir a linha de base de cada módulo e registrá-la no ADR-0030; verificar que os números citados batem com os relatórios gerados

## 2. Backend — endpoints sem teste de API

- [x] 2.1 Criar `AlertApiIntegrationTest` cobrindo a spec `notifications/alerts-api` (listar só os próprios, dispensado some, dispensa de outro titular `404`, inexistente `404`, UUID malformado `400`, 401) e verificar com `./gradlew test --tests '*AlertApiIntegrationTest'`
- [x] 2.2 Criar `MfaStatusAndRecoveryCodesApiIntegrationTest` cobrindo a spec `identity/mfa-api` (`ENABLED`/`PENDING`/`DISABLED`, códigos antigos invalidados, senha errada `401 PASSWORD_REQUIRED_INVALID`, senha em branco `400`, MFA desabilitado `409 MFA_NOT_ENABLED`, conta só OAuth, 401) e verificar com o teste passando
- [x] 2.3 Criar `OAuthMobileHandoffApiIntegrationTest` cobrindo a spec `identity/oauth-mobile-handoff` (válido, reutilizado, expirado, desconhecido com mesma mensagem, concorrente, sem `code`) e verificar com o teste passando; se o caso concorrente falhar, corrigir com TDD antes de seguir
- [x] 2.4 Criar `ParticipantsApiIntegrationTest` cobrindo a spec `sharing/participants-api` (proprietário lista/convida/remove, participante `403`, terceiro `403`/`404`, arquivada `404`, convite repetido idempotente, e-mail desconhecido genérico, e-mail em branco, convidar o proprietário, removido perde acesso, 401) e verificar com o teste passando

## 3. Backend — services e domínio de identidade

- [x] 3.1 Testes unitários de `PasswordResetService`, `PasswordResetToken` e `PasswordCredential` (token expirado/reutilizado, senha fraca, e-mail inexistente sem revelar conta) e verificar com `./gradlew unitTest`
- [x] 3.2 Testes unitários de `MfaEnrollmentService`, `MfaLoginService`, `RecoveryLoginService` e `RecoveryCodeGenerator` (código TOTP inválido, desafio expirado, código de recuperação reutilizado, quantidade e unicidade dos códigos gerados) e verificar com `./gradlew unitTest`
- [x] 3.3 Testes unitários de `UserAccountService`, `SessionService`, `AuthAttemptService` e `AuthAttempt` (sessão revogada, refresh reutilizado, bloqueio por tentativas, janela de tentativas expirada) e verificar com `./gradlew unitTest`
- [x] 3.4 Testes de `GoogleOAuthClient` e `GitHubOAuthClient` contra servidor HTTP local (4xx, 5xx, timeout, JSON inválido, e-mail não verificado) afirmando a exceção exata e verificar com `./gradlew unitTest`
- [x] 3.5 Testes unitários de `AccountClosureService` e `TitularRightsService` (titular com verbas compartilhadas, conta já encerrada, exportação de outro titular) e verificar com `./gradlew unitTest`

## 4. Backend — envelopes, renda, ledger e alertas

- [x] 4.1 Testes unitários de `EnvelopeService` (acesso de terceiro, participante em ação do proprietário, verba arquivada, alocação acima da renda, escala inválida e valor no limite) e verificar com `./gradlew unitTest`
- [x] 4.2 Testes de domínio de `IncomeRevision`, `MonthlyIncome`, `BaseAllocation`, `EnvelopeParticipant`, `AnnualExpenseInstallment`, `InAppAlert` e `LedgerEntryRevision` com bordas de `BigDecimal` (`"0.00"`, `"10.5"`, limite) e verificar com `./gradlew unitTest`
- [x] 4.3 Completar até o padrão os unitários abaixo do padrão: `LedgerServiceTest`, `IdentityRetentionServiceTest`, `RestrictedSessionTokenServiceTest`, `ReportingServiceTest`, `MonthlyCommittedAllocationTest`, `SessionValidationFilterTest`, `EnvelopeBalanceTest`, `EmailAddressTest`, `RefreshTokenCodecTest`, `ReportRangeTest`, `AnnualExpensePlanTest`, `AuthenticationServiceTest`, `AuthSessionTest`, `LedgerEntryTest`; verificar com `./gradlew unitTest`
- [x] 4.4 Completar até o padrão os de integração abaixo do padrão: `AnnualExpenseApiIntegrationTest`, `EnvelopeEntrySoftDeleteApiIntegrationTest`, `SavingsTargetApiIntegrationTest`, `GoalProgressApiIntegrationTest`, `WealthAllocationHistorySummaryApiIntegrationTest`, `LedgerSummaryUsagePctApiIntegrationTest`, `MonthlyResetBalanceApiIntegrationTest`, `OAuthApiIntegrationTest`, `TotpCredentialRepositoryIntegrationTest`, `TitularRightsApiIntegrationTest`, `EnvelopeOptimisticLockApiIntegrationTest`, `AnnualDueDateValidationApiIntegrationTest`, `EnvelopeAllocationApiIntegrationTest`, `MfaLoginApiIntegrationTest`, `MfaRecoveryApiIntegrationTest`; verificar com `./gradlew test`
- [x] 4.5 Varrer todos os controllers e confirmar que cada endpoint autenticado tem teste de 401 e de terceiro (criar os que faltarem) e verificar com `./gradlew test` e a lista anexada ao ADR-0030
- [x] 4.6 Ativar o limite JaCoCo do backend conforme design D2 e verificar que `./gradlew check` passa e reprova ao remover um teste localmente

## 5. Web

- [x] 5.1 Testar `lib/api.ts` diretamente (401, 403, 409, 422 com `ProblemDetail`, 5xx e falha de rede), `lib/form-errors.ts` e `lib/dates.ts` e verificar com `pnpm test`
- [x] 5.2 Testar diretamente os hooks `useEnvelopes`, `useIncome` e `useLedgerSummary` (loading, erro, refetch, sessão expirada) e verificar com `pnpm test`
- [x] 5.3 Criar testes de `MetasPage`, `BalanceSummary`, `Glossary`, `VerbasFilters`, `PurposeSection` e `ThemeSelector` afirmando a mensagem renderizada nos erros e verificar com `pnpm test`
- [ ] 5.4 Completar até o padrão `ParticipantsPanel`, `ReportsPage`, `SettingsPage`, `PrivacyPolicy`, `auth-screen`, `AnnualExpensesPage`, `PersistentAlerts`, `MfaSettings`, `MfaLoginStep`, `MonthSummary`, `IncomeHistory`, `IncomeForm`, `SecuritySettings` e `ui/*` (`Button`, `ConfirmDialog`, `Dialog`, `PageHeader`, `ProgressBar`) e verificar com `pnpm test`
- [ ] 5.5 Ativar o limite de cobertura do web e verificar que `make web-check` passa

## 6. Mobile

- [ ] 6.1 Testar em `commonTest` `AlertGateway`, `SharingGateway`, `ReportGateway`, `EnvelopeGateway`, `IncomeGateway` e `describeFailure` com cliente HTTP falso (401, 403, 422, 5xx, falha de rede, corpo inválido) e verificar com `./gradlew :shared:jvmTest`
- [ ] 6.2 Testar `Forms.kt` (`isIsoDate`, validações dos diálogos de verba e renda com valores `"0.00"`, `"10.5"` e vazio), `EntryDate.monthOf`, `EnvelopeCardActions.headlineAmount`/`isGoal` e `purposeColor` e verificar com `./gradlew :shared:jvmTest`
- [ ] 6.3 Testar os controllers de tela de relatórios, segurança/senha, MFA, compartilhamento e histórico de renda e completar `ThemePreferenceControllerTest`, `IsoDateTest` e `LoginMethodActionsTest`; verificar com `./gradlew :shared:jvmTest`
- [ ] 6.4 Criar `androidApp/src/test` com testes JVM de `AndroidApiClient`, `AndroidApiGateway`, `EncryptedRefreshTokenStore`, `OAuthBrowser`, `ReportSharing` e `AndroidThemePreferenceStore`, extraindo para interfaces o que depender de API Android, e verificar com `./gradlew :androidApp:testDebugUnitTest`
- [ ] 6.5 Ativar o limite Kover e verificar que `make mobile-check` passa (iOS não validado: registrar no README)

## 7. Desktop

- [ ] 7.1 Testar `Amount`, `MonthSelector`, `useExpenseCapture`, `lib/use-summary.ts`, `lib/api.ts` e `lib/dates.ts` e completar `ipc.test.ts` e `icons.test.ts`; verificar com `cd desktop && pnpm test`
- [ ] 7.2 Revisar `src-tauri/src/lib.rs`, mover para o crate `desktop/session` a lógica pura que restar e cobri-la com `cargo test`; verificar com `cd desktop/session && cargo test`
- [ ] 7.3 Ativar o limite de cobertura do frontend desktop e verificar que `make desktop-check` passa

## 8. MCP

- [ ] 8.1 Testar `login-cli.ts` (login feliz, MFA, credencial inválida, API indisponível, sessão salva corrompida) e completar `server.test.ts`, `cookies.test.ts`, `terminal-prompt.test.ts` e `session-store.test.ts`; verificar com `cd mcp && pnpm test`
- [ ] 8.2 Ativar o limite de cobertura do MCP e verificar que `make mcp-check` passa

## 9. Verificação integrada

- [ ] 9.1 Rodar `make check` completo e o workflow de PR, confirmar que todos os jobs passam e publicam cobertura, e atualizar a issue #95 marcando os itens entregues
