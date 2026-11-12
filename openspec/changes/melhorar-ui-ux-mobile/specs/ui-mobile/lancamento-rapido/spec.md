# Spec Delta

## Purpose

Permite à pessoa autenticada registrar um gasto ou aporte no Android em poucos toques, com valor em BRL compreensível e data coerente com o mês que está vendo.

## ADDED Requirements

### Requirement: Folha única de lançamento
O aplicativo Android SHALL abrir o registro de gasto ou aporte numa única folha inferior que reúne verba, valor, data e descrição opcional, sem exigir um diálogo só para escolher a verba.

#### Scenario: Lançamento a partir do atalho global
- **GIVEN** a pessoa autenticada está na Visão geral, em Verbas, em Gastos anuais ou na aba Gastos
- **AND** existe ao menos uma verba em que ela pode registrar gasto
- **WHEN** ela aciona "Novo gasto"
- **THEN** uma folha inferior apresenta a escolha da verba junto dos campos de valor, data e descrição
- **AND** confirmar envia um único lançamento `EXPENSE` com o valor em string decimal de duas casas

#### Scenario: Sem verba de gasto
- **GIVEN** o mês exibido não tem verba de limite, compromisso fixo nem gasto anual
- **WHEN** a pessoa aciona "Novo gasto"
- **THEN** a folha explica que é preciso criar uma verba antes
- **AND** não envia lançamento

#### Scenario: Aporte pelo card da meta
- **GIVEN** a pessoa é proprietária de uma verba de meta de aporte ou de acumulação
- **WHEN** ela aciona a ação primária dessa verba
- **THEN** a folha abre já naquela verba, com tipo `CONTRIBUTION`
- **AND** um participante da mesma verba vê ação primária de gasto, não de aporte

### Requirement: Mês de referência visível na folha
A folha SHALL exibir o mês em que o lançamento será registrado, porque o painel e a aba Gastos podem estar em meses diferentes.

#### Scenario: Folha aberta a partir do histórico em outro mês
- **GIVEN** o painel mostra o mês corrente e a aba Gastos mostra `"2026-08"`
- **WHEN** a pessoa aciona "Novo gasto" na aba Gastos
- **THEN** a folha identifica `"2026-08"` como mês do lançamento
- **AND** confirmar registra o lançamento nesse mês, não no mês do painel

### Requirement: Máscara de centavos explicada
O campo de valor SHALL manter a entrada por centavos da direita para a esquerda e MUST exibir um exemplo em pt-BR para que "50" não seja confundido com "50,00".

#### Scenario: Dica visível ao focar o valor
- **GIVEN** a folha de lançamento está aberta
- **WHEN** o campo de valor está visível
- **THEN** a pessoa vê uma dica no formato "ex.: 120,00"
- **AND** ao digitar `50` o campo mostra `R$ 0,50`

### Requirement: Prévia de saldo após gasto
Ao informar um gasto, o aplicativo SHALL mostrar o saldo previsto da verba escolhida antes de confirmar, inclusive quando o resultado for negativo, sem bloquear o registro.

#### Scenario: Gasto que deixa a verba negativa
- **GIVEN** a verba escolhida tem disponível `"10.00"`
- **WHEN** a pessoa informa gasto `"15.00"`
- **THEN** a folha mostra saldo previsto `"-5.00"` e avisa que o registro continua permitido
- **AND** confirmar envia o lançamento

#### Scenario: Aporte não mostra prévia de consumo
- **GIVEN** a folha está em aporte
- **WHEN** a pessoa informa um valor
- **THEN** a folha não apresenta "saldo após o gasto"

### Requirement: Data alinhada ao mês exibido
A data inicial do lançamento SHALL ser o dia de hoje quando o mês exibido for o mês corrente, e MUST ser um dia daquele mês (não futuro) quando a pessoa estiver vendo outro mês.

#### Scenario: Mês corrente
- **GIVEN** o mês exibido é o mês corrente em America/Sao_Paulo
- **WHEN** a folha abre
- **THEN** a data começa em hoje
- **AND** atalhos "Hoje" e "Ontem" permanecem disponíveis

#### Scenario: Mês passado
- **GIVEN** o mês exibido é anterior ao corrente
- **WHEN** a folha abre
- **THEN** a data inicial pertence a esse mês
- **AND** após confirmar o lançamento aparece na lista daquele mês

#### Scenario: Mês futuro
- **GIVEN** o mês exibido é posterior ao corrente em America/Sao_Paulo
- **WHEN** a tela é exibida
- **THEN** o aplicativo não oferece o atalho de novo gasto nesse mês
- **AND** nenhuma data futura é proposta como data de lançamento
