# Spec Delta

## Purpose

Deixa visíveis no Android o estado da sessão, os alertas de verba negativa e o cromado da navegação, para a pessoa entender erros, tratar estouros e saber onde está.

## ADDED Requirements

### Requirement: Alertas persistentes na Visão geral
Enquanto houver alerta persistente de saldo negativo do titular, a Visão geral SHALL exibi-lo com a mesma opção de dispensar oferecida em Verbas.

#### Scenario: Home com alerta ativo
- **GIVEN** existe um alerta persistente de verba negativa para o titular
- **WHEN** a pessoa abre a Visão geral
- **THEN** o alerta aparece com o nome da verba e o mês de referência
- **AND** dispensar remove o alerta da Visão geral e de Verbas

#### Scenario: Participante não vê alerta de titular
- **GIVEN** a pessoa só participa da verba e não é a titular
- **WHEN** ela abre a Visão geral
- **THEN** o aplicativo não mostra o alerta persistente da titular

### Requirement: Erro de autenticação distinguível
Falhas de login, cadastro, recuperação de senha e MFA SHALL usar a cor e a região ao vivo de erro. Sucesso MUST usar tom distinto do erro.

#### Scenario: Senha incorreta
- **GIVEN** a pessoa está na tela de entrar
- **WHEN** o servidor recusa as credenciais
- **THEN** a mensagem de erro não usa a cor primária da marca
- **AND** leitores de tela anunciam o texto do erro

#### Scenario: Cadastro aceito
- **GIVEN** o cadastro foi recebido
- **WHEN** a tela volta para entrar
- **THEN** a confirmação não usa a cor de erro

### Requirement: Senha visível e autofill
Campos de senha SHALL permitir mostrar e ocultar o texto e MUST aceitar colar e preenchimento do gerenciador de senhas. O aplicativo MUST NOT impedir colar.

#### Scenario: Mostrar senha
- **GIVEN** a pessoa está digitando a senha
- **WHEN** ela aciona mostrar senha
- **THEN** os caracteres ficam visíveis
- **AND** acionar de novo os oculta

### Requirement: Saída da verificação MFA
Enquanto a sessão pede o segundo fator, a pessoa SHALL poder cancelar e voltar à tela de entrar, sem ficar presa até informar um código.

#### Scenario: Cancelar MFA
- **GIVEN** o estado é MFA obrigatório
- **WHEN** a pessoa aciona voltar ou cancelar
- **THEN** o aplicativo mostra a tela de entrar
- **AND** não autentica a sessão

### Requirement: Área segura nas telas anônimas
Telas de entrar, cadastro e MFA SHALL respeitar a área segura do sistema (status bar, recorte e indicador de gesto), sem texto ou controles sob as barras.

#### Scenario: Login com edge-to-edge
- **GIVEN** o dispositivo desenha o conteúdo atrás das barras do sistema
- **WHEN** a tela de entrar aparece
- **THEN** o título e os campos ficam abaixo da status bar
- **AND** o botão principal permanece acima da área de gesto

### Requirement: Cromado previsível
A barra superior MUST NÃO repetir o e-mail em toda tela autenticada (o e-mail permanece em Mais). Rótulos da barra inferior MUST caber sem ellipsis em 360 dp. Datas de lançamento na lista SHALL usar formato pt-BR. Confirmações destrutivas MUST usar ênfase de risco no botão de confirmar.

#### Scenario: E-mail só na conta
- **GIVEN** a pessoa está autenticada na Visão geral
- **WHEN** a barra superior é exibida
- **THEN** o e-mail da conta não aparece como subtítulo
- **AND** em Mais o e-mail continua visível

#### Scenario: Excluir lançamento
- **GIVEN** a pessoa é proprietária de um lançamento
- **WHEN** ela confirma a exclusão
- **THEN** o botão confirmar usa ênfase de risco, distinta da ação primária comum
- **AND** o valor volta ao saldo da verba conforme a API
