# Spec Delta

## Purpose

Define quais verificações cada módulo do monorepo executa localmente e no CI,
como a cobertura de testes é medida e publicada e quando ela reprova a mudança.

## ADDED Requirements

### Requirement: Coverage measured in every module
Backend, web, mobile (`shared` e `androidApp`), desktop e MCP SHALL gerar
relatório de cobertura de linhas e de branches a cada execução da sua suíte de
testes na verificação completa.

#### Scenario: Relatório gerado
- **WHEN** a verificação de um módulo roda com sucesso
- **THEN** existe um relatório de cobertura de linhas e branches desse módulo

#### Scenario: Suíte falha
- **WHEN** um teste do módulo falha
- **THEN** a verificação reprova pelo teste, independentemente da cobertura

### Requirement: Coverage threshold gates the check
Cada módulo SHALL declarar um limite mínimo de cobertura de linhas e de
branches. Cobertura abaixo do limite MUST reprovar a verificação do módulo. Os
limites MUST NOT ser reduzidos sem registro do motivo no ADR de testes.

#### Scenario: Cobertura abaixo do limite
- **GIVEN** um módulo com limite de 80% de linhas
- **WHEN** a suíte passa mas mede 78% de linhas
- **THEN** a verificação do módulo reprova indicando a cobertura medida e o limite

#### Scenario: Cobertura no limite
- **WHEN** a cobertura medida é igual ao limite declarado
- **THEN** a verificação de cobertura passa

#### Scenario: Código gerado fora da medição
- **GIVEN** classes geradas, pontos de entrada sem lógica ou configuração declarativa
- **WHEN** a cobertura é calculada
- **THEN** esses arquivos são excluídos de forma explícita e documentada

### Requirement: Coverage reports published on pull requests
O workflow de pull request SHALL publicar o relatório de cobertura de cada
módulo como artefato da execução, inclusive quando a verificação reprova.

#### Scenario: PR com falha de cobertura
- **WHEN** o job de um módulo reprova por cobertura
- **THEN** o relatório de cobertura desse módulo fica disponível como artefato da execução

### Requirement: Every client module runs in the pull request workflow
O workflow de pull request SHALL executar a verificação de backend, web,
mobile, MCP e desktop. O desktop SHALL rodar os testes do frontend, o
`cargo test` do crate de sessão e o build de verificação; o shell Tauri que
depende das bibliotecas de GUI MAY ficar fora.

#### Scenario: Mudança só no MCP
- **WHEN** um pull request altera apenas `mcp/`
- **THEN** o workflow executa os testes e o build do MCP

#### Scenario: Mudança só no desktop
- **WHEN** um pull request altera apenas `desktop/`
- **THEN** o workflow executa os testes do frontend desktop e o `cargo test` do crate de sessão

### Requirement: Local check includes mobile
`make check` SHALL executar a verificação mobile (testes compartilhados JVM,
testes JVM do `androidApp` e build Android de debug) junto das demais, mantendo
a regra de omitir o desktop quando não houver toolchain Rust.

#### Scenario: Check completo local
- **GIVEN** um ambiente com JDK, Android SDK, Node e Docker
- **WHEN** o desenvolvedor roda `make check`
- **THEN** a verificação mobile é executada e sua falha reprova o `make check`

#### Scenario: Sem Rust
- **GIVEN** um ambiente sem `cargo` no PATH
- **WHEN** o desenvolvedor roda `make check`
- **THEN** o desktop é omitido com aviso e os demais módulos, incluindo mobile, são verificados
