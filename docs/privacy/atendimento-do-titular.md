# Atendimento ao titular (procedimento operacional)

Procedimento interno para pedidos feitos pelo canal eletrônico publicado na
política de privacidade da web (`/privacidade`).

## Canal

- **E-mail público:** o endereço em
  [`web/src/privacy/titular-contact.ts`](../../web/src/privacy/titular-contact.ts)
  (`TITULAR_CONTACT_EMAIL`).
- A página `/privacidade` e os links na autenticação e em Configurações apontam
  para o mesmo canal.

## Tipos de pedido

| Pedido | Caminho preferencial | Observação |
|---|---|---|
| Exportar dados | Fluxo autenticado no produto | Já existe endpoint/fluxo de exportação do titular |
| Excluir conta | Fluxo autenticado no produto | Confirmação reforçada; ver inventário e ADR-0023 |
| Corrigir cadastro | Conta / segurança no produto | E-mail, senha, MFA, vínculos sociais |
| Esclarecimento / reclamação | E-mail do canal | Sem pedir senha, TOTP ou dados financeiros detalhados na resposta |

Oriente o titular a usar o produto autenticado sempre que o fluxo existir; o
e-mail cobre o que a interface ainda não automatiza ou dúvidas sobre o tratamento.

## Registro mínimo

Para cada mensagem recebida no canal, anote em local protegido (fora do Git):

1. Data/hora de recebimento (fuso `America/Sao_Paulo`).
2. Identificador interno do titular (UUID), se a identidade for confirmada — nunca
   grave a senha nem códigos MFA.
3. Tipo do pedido e resumo sem PII desnecessária.
4. Data/hora da resposta e resultado (atendido, parcialmente atendido, encaminhado).

Não versione esses registros neste repositório.

## Prazos operacionais (uso pessoal / pré-abertura pública)

Enquanto o cadastro público de terceiros não estiver liberado:

- **Confirmação de recebimento:** até 3 dias úteis.
- **Resposta substantiva ou execução do fluxo no produto:** até 15 dias corridos,
  salvo complexidade que exija prazo maior — nesse caso, informar o titular.

Antes de abrir cadastro a terceiros, revise estes prazos com orientação jurídica
e atualize este documento.

## Prova do canal

Após publicar ou alterar o e-mail:

1. Envie uma mensagem de prova para o endereço publicado.
2. Confirme o recebimento na caixa do operador.
3. Registre a data da prova no mesmo local protegido dos atendimentos (não no Git).

## Relação com outros documentos

- Requisitos: [`requisitos-de-seguranca.md`](requisitos-de-seguranca.md) — Direitos do titular.
- Inventário: [`inventario-de-dados.md`](inventario-de-dados.md).
- Texto público: rota web `/privacidade` (versão operacional até parecer jurídico).
