# Spec Delta

## Purpose

Permite ao usuário autorizado registrar um gasto real em BRL na verba escolhida,
inclusive quando o saldo ficar negativo, sem arredondar o valor.

## ADDED Requirements

### Requirement: Register expense with exact BRL
O cliente SHALL enviar o valor como string decimal com exatamente duas casas,
moeda BRL, sem arredondar. Data MUST usar o calendário America/Sao_Paulo.
Descrição, se houver, MUST ter no máximo 140 caracteres.

#### Scenario: Owner registers a valid expense
- **GIVEN** o titular pode gastar na verba "Combustível"
- **WHEN** ele registra gasto de "45.90" na data de hoje
- **THEN** a API recebe amount "45.90" e currency "BRL"
- **AND** o cliente confirma o registro e atualiza saldos visíveis

#### Scenario: Rejects amount without two decimal places
- **WHEN** o usuário tenta enviar um valor que não casa duas casas decimais
- **THEN** o cliente não envia a requisição
- **AND** o erro fica associado ao campo valor

### Requirement: Authorization to spend
Somente dono ou participante da verba SHALL conseguir registrar gasto. Se a API
recusar acesso, o cliente MUST NÃO tratar a operação como sucesso.

#### Scenario: Participant registers expense
- **GIVEN** um participante de "Mercado"
- **WHEN** ele registra gasto de "20.00" em "Mercado"
- **THEN** o cliente confirma o registro

#### Scenario: Third party cannot spend
- **GIVEN** um usuário que não é dono nem participante de "Viagem"
- **WHEN** uma tentativa de registrar gasto em "Viagem" é recusada pela API
- **THEN** o cliente mostra erro
- **AND** não afirma que o gasto foi lançado

### Requirement: Negative balance does not block
O cliente MUST permitir o envio mesmo quando o saldo projetado for negativo. Após
sucesso com saldo negativo, SHALL avisar com texto (e ícone, se houver), não só
com cor.

#### Scenario: Overdraft still registers
- **GIVEN** a verba "Lazer" com disponível "10.00"
- **WHEN** o dono registra gasto de "25.00"
- **THEN** o gasto é aceito
- **AND** o aviso de saldo negativo inclui o valor "-15.00" em texto

### Requirement: Capture form accessibility
O diálogo de captura SHALL ter rótulos nos campos, resumo de erro focável após
submit inválido, erros inline preservados, e fechar com Escape devolvendo o
foco ao gatilho.

#### Scenario: Invalid submit focuses error summary
- **WHEN** o usuário envia o formulário com valor inválido
- **THEN** o foco vai ao resumo de erro no topo
- **AND** o campo valor continua com o erro inline

#### Scenario: Cancel restores focus
- **GIVEN** o diálogo aberto a partir do atalho ou da ação da verba
- **WHEN** o usuário cancela
- **THEN** o diálogo fecha
- **AND** o foco volta ao controle que o abriu
