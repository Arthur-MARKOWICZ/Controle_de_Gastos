# ADR-003: Privacidade e LGPD desde a concepção

## Status

Aceito

## Data

2026-08-27

## Contexto

O sistema tratará identidade, renda, categorias e histórico de gastos. Mesmo que o MVP seja pessoal, a criação de contas para terceiros exige controles de acesso, transparência e atendimento aos direitos dos titulares.

## Decisão

Aplicar minimização de dados, autorização por recurso, retenção documentada, auditoria sem segredos, exportação e fluxo de exclusão desde o desenho inicial. Toda nova coleta deve declarar finalidade, base legal proposta, retenção e destinatários antes da implementação.

## Alternativas consideradas

### Adequar depois da validação

- Vantagem: menor trabalho inicial.
- Desvantagem: esquemas, logs e backups criariam dívida difícil de remover e risco desde o primeiro usuário externo.
- Rejeitada porque segurança deve existir desde a concepção.

## Consequências

- O módulo `privacy` coordenará solicitações sem assumir a propriedade dos dados de outros módulos.
- Exclusão pode exigir anonimização ou retenção limitada quando houver outra obrigação aplicável; a decisão jurídica deve ser documentada.
- Logs não conterão descrições de gastos, tokens ou payloads completos.
- Revisão jurídica é necessária antes de disponibilização pública.
