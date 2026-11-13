# ADR-013: Tema adaptativo e preferência local nos clientes

## Status

Aceito

## Data

2026-08-31

## Contexto

Web, mobile e o cliente desktop (ADR-0029) precisam oferecer temas claro e
escuro com a opção de acompanhar a preferência do sistema. A escolha é uma configuração de apresentação do
dispositivo, não uma regra financeira nem uma preferência que precise viajar
entre contas.

Na web, esperar a hidratação do React para descobrir a preferência causa um
flash do tema incorreto. No mobile, adicionar uma biblioteca multiplataforma
somente para gravar um enum aumentaria a superfície de dependências sem
benefício proporcional.

## Decisão

- Oferecer os modos `SYSTEM`, `LIGHT` e `DARK` em todos os clientes com UI.
- Usar `SYSTEM` quando nenhuma escolha explícita existir e reagir a mudanças do
  sistema enquanto esse modo estiver ativo.
- Persistir a escolha somente no dispositivo:
  - web em `localStorage`, na chave `verbas.theme`;
  - Android em `SharedPreferences`, no arquivo `verbas_preferences` e chave
    `theme_mode`;
  - iOS em `NSUserDefaults`, na chave `verbas.themeMode`;
  - desktop em arquivo de configuração do aplicativo no dispositivo
    (`theme.json`, chave `theme`), gravado pelo processo Rust — não no cofre do
    sistema, que guarda apenas o refresh de sessão.
- Aplicar um script mínimo antes da hidratação da web para definir
  `data-theme`, `data-theme-preference` e `color-scheme` no elemento raiz.
- Sincronizar alterações entre abas da web pelo evento `storage`.
- Não enviar a preferência à API, não associá-la ao UUID do usuário e não
  registrá-la em logs ou telemetria.
- Usar APIs nativas de armazenamento no mobile, sem nova dependência de
  runtime.
- No desktop, resolver o tema no arranque antes de pintar a interface, pelo
  mesmo critério de evitar flash adotado na web.

## Alternativas consideradas

### Seguir somente o sistema

Reduziria o estado local, mas impediria uma escolha explícita importante para
conforto visual e acessibilidade.

### Sincronizar pelo backend

Manteria a escolha entre dispositivos, mas exigiria contrato, persistência e
tratamento de um dado sem valor para as regras financeiras. Foi rejeitado por
minimização e simplicidade operacional.

### Aplicar o tema web somente após a hidratação

Evitaria o script inicial, mas produziria flash de cores e poderia exibir uma
superfície clara antes do tema escuro. Foi rejeitado por qualidade percebida e
conforto visual.

### Adotar uma biblioteca multiplataforma de preferências

Uniformizaria a API de armazenamento, porém adicionaria dependência para uma
chave simples. `SharedPreferences` e `NSUserDefaults` atendem ao caso atual.

## Consequências

- A preferência pode diferir entre navegadores, dispositivos e clientes do mesmo usuário.
- Limpar dados locais restaura o modo `SYSTEM`.
- O script inicial da web deve permanecer alinhado ao resolvedor testado no
  `ThemeProvider`.
- Android é validado neste ambiente. O armazenamento iOS permanece preservado
  no código KMP, mas só poderá ser declarado testado após build em macOS/Xcode.
- O desktop é validado em Linux. O arquivo de tema segue o diretório de
  configuração que o Tauri resolve por plataforma; Windows e macOS não são
  declarados testados neste repositório.
- Não há alteração de banco, REST, OpenAPI, autenticação ou tratamento de dados
  financeiros.
