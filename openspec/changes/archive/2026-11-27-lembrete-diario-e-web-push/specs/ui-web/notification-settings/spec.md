# Spec Delta

## Purpose

Permite, na web, ativar notificações push neste navegador e escolher tipos e
horário do lembrete, e exibe os avisos recebidos sem expor dados financeiros.

## ADDED Requirements

### Requirement: Seção de notificações nas configurações
A página de configurações SHALL exibir a seção "Notificações" com um controle
para cada tipo (lembrete diário, verba negativa, gasto em verba compartilhada)
e o horário do lembrete em passos de 15 minutos. Alterações SHALL ser salvas na
API e refletidas nos outros canais.

#### Scenario: Ativar lembrete e escolher horário
- **GIVEN** a pessoa está nas configurações
- **WHEN** ativa o lembrete diário e escolhe `21:00`
- **THEN** a preferência é salva na API
- **AND** a tela mostra o lembrete ativo às `21:00`

#### Scenario: Falha ao salvar
- **WHEN** a API recusa ou não responde ao salvar
- **THEN** a tela volta ao valor anterior e mostra mensagem de erro

### Requirement: Ativação do push neste navegador
A seção SHALL oferecer "Ativar neste navegador", que pede a permissão do
navegador só após o clique e registra a assinatura na API. Com permissão
negada, a tela SHALL explicar como liberar no navegador. Sem suporte a Web
Push, a tela SHALL informar isso e manter as preferências editáveis.

#### Scenario: Permissão concedida
- **WHEN** a pessoa clica em "Ativar neste navegador" e concede a permissão
- **THEN** a assinatura é registrada na API
- **AND** a tela mostra o navegador como ativo

#### Scenario: Permissão negada
- **WHEN** a pessoa nega a permissão
- **THEN** nenhuma assinatura é registrada
- **AND** a tela explica como liberar a permissão

#### Scenario: Navegador sem suporte
- **GIVEN** o navegador não suporta Web Push ou é iOS sem o app instalado
- **WHEN** a pessoa abre a seção
- **THEN** a tela informa a limitação e, no iOS, orienta a instalar o app

#### Scenario: Desativar neste navegador
- **GIVEN** o navegador está ativo
- **WHEN** a pessoa clica em "Desativar neste navegador"
- **THEN** a assinatura é removida da API e do navegador

### Requirement: Aplicativo instalável
A web SHALL servir manifest com nome, ícones e `display` `standalone`, e
SHALL registrar um service worker no escopo raiz. O service worker MUST NOT
guardar em cache respostas da API nem dados financeiros.

#### Scenario: Instalação no iOS
- **GIVEN** Safari no iOS 16.4 ou superior
- **WHEN** a pessoa adiciona o app à tela inicial
- **THEN** o app abre em modo standalone e pode ativar o push

#### Scenario: Sem cache de dados
- **WHEN** o service worker está ativo
- **THEN** requisições a `/api/` seguem sempre para a rede

### Requirement: Exibição e clique no aviso
O service worker SHALL exibir o aviso com texto local a partir da categoria e do
tipo da verba, como "Você tem um alerta de verba do tipo Limite". Ao clicar, a
web SHALL abrir ou focar o app no caminho de destino.

#### Scenario: Aviso de verba negativa
- **WHEN** chega um push `NEGATIVE_BALANCE` com tipo `LIMIT`
- **THEN** o navegador mostra "Você tem um alerta de verba do tipo Limite"

#### Scenario: Clique no lembrete
- **WHEN** a pessoa clica no lembrete diário
- **THEN** o app abre na visão geral, onde cada verba oferece "Registrar gasto"

### Requirement: Logout remove a assinatura
Ao sair da conta, a web SHALL remover a assinatura deste navegador na API e no
navegador antes de encerrar a sessão.

#### Scenario: Logout com push ativo
- **GIVEN** o navegador está ativo para push
- **WHEN** a pessoa sai da conta
- **THEN** o navegador deixa de receber avisos dessa conta
