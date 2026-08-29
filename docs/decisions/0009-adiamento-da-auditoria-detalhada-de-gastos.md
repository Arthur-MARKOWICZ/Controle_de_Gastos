# ADR-009: Adiamento da auditoria detalhada de gastos

## Status

Aceito

## Data

2026-08-29

## Contexto

A página de histórico precisa permitir que o proprietário de uma verba edite
valor, verba e descrição de um gasto, além de excluí-lo logicamente. O escopo
atual não inclui uma trilha de auditoria que retenha todas as versões desses
campos, o ator de cada alteração e a verba anterior.

A regra geral de privacidade desde a concepção previa auditoria no desenho
inicial. Para permitir este corte, a decisão precisa registrar explicitamente
a limitação, em vez de deixar mutações sem uma posição de produto documentada.

## Decisão

- Adiar a auditoria detalhada das edições e exclusões de `ledger_entry` para
  uma entrega futura.
- Preservar a autoria e o instante de criação do lançamento. A exclusão será
  lógica e não deve remover o lançamento fisicamente.
- Não preservar, neste corte, os valores, a descrição ou a verba anteriores à
  edição; relatórios históricos são recalculados conforme o estado atual do
  lançamento.
- Manter as proteções de privacidade existentes: autorização por recurso,
  minimização de dados, valores monetários exatos e proibição de registrar
  valores, descrições, tokens ou payloads completos em logs.
- Exigir que a implementação de uma trilha detalhada futura receba novo ADR,
  incluindo retenção, acesso, exportação e exclusão dos registros de auditoria.

Esta decisão substitui somente a exigência de auditoria detalhada para essas
mutações no corte de histórico; os demais controles do ADR-003 permanecem
aplicáveis.

## Alternativas consideradas

### Implementar a trilha detalhada agora

Preservaria versões, ator e instante de cada alteração, mas amplia o escopo do
corte com um modelo de retenção, permissões e exportação que ainda não foi
definido.

### Excluir gastos fisicamente

Reduziria o volume de dados, mas impediria recuperar o lançamento e contraria
a decisão de exclusão lógica necessária para a experiência de histórico.

## Consequências

- O proprietário pode editar e excluir logicamente lançamentos, mas a aplicação
  não oferecerá histórico detalhado de versões nesta entrega.
- Totais de períodos passados podem mudar após uma edição ou exclusão posterior.
- A dívida de auditoria fica explícita e não pode ser confundida com uma
  implementação completa de rastreabilidade.
