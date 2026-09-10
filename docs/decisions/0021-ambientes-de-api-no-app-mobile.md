# ADR-021: Ambientes de API no app mobile por arquivo `.env`

## Status

Aceito

## Data

2026-09-09

## Contexto

O app Android resolvia a URL da API apenas por `mobile/local.properties`, com
fallback para `http://10.0.2.2:8080`. Esse arranjo tinha três problemas.

Primeiro, `local.properties` é ignorado pelo Git (`.gitignore:22`), então um
clone novo compilava silenciosamente contra o endereço do emulador — inexistente
em aparelho físico e bloqueado no build release por cleartext.

Segundo, existia um único valor por vez: alternar entre a API local e a
publicada exigia reescrever o arquivo, sem registro de qual valor pertence a
qual ambiente.

Terceiro, nada validava o formato. Um `mobile/.env` órfão no repositório
apontava para `https://gastos.arthurmlopes.systems:8080`, com uma porta que o
[runbook de HTTPS](../deploy/https-producao.md) mantém fechada no firewall: em
produção o Nginx publica a API em 443 e o contêiner fica em loopback. Nenhum
código lia esse arquivo, e se lesse a comunicação falharia sem mensagem útil.

A web já resolve isso com `NEXT_PUBLIC_API_URL` embutida em tempo de build, e o
deploy valida `PUBLIC_APP_URL` como origem HTTPS sem porta nem caminho
(`.github/workflows/deploy.yml:70-86`).

## Decisão

- Resolver a URL da API em tempo de build a partir de `mobile/.env`, com
  `mobile/.env.example` versionado como referência. `mobile/.env` permanece
  ignorado pelo Git.
- Declarar os dois ambientes ao mesmo tempo, em `API_BASE_URL_LOCAL` e
  `API_BASE_URL_PROD`, e escolher entre eles pela chave `API_ENV`, com valores
  `local` e `prod`.
- Aplicar esta precedência: `-PAPI_BASE_URL`, depois `-PAPI_ENV` ou `API_ENV` do
  `.env` resolvendo a chave correspondente, e por fim `http://10.0.2.2:8080`.
- Manter `local.properties` restrito ao caminho do SDK. Um `API_BASE_URL` ali
  interrompe o build com instrução de migração, em vez de ser silenciosamente
  ignorado: duas fontes para a mesma URL fazem quem edita a errada não entender
  por que nada muda.
- Falhar o build, e não o aplicativo em execução, quando a configuração estiver
  errada:
  - qualquer ambiente: a URL precisa casar `https?://[^\s"\\]+`;
  - `API_ENV=prod`: exigir `https`, sem porta, caminho, query, fragmento nem
    barra final, espelhando a validação de `PUBLIC_APP_URL` no deploy;
  - build `release`: exigir URL HTTPS.
- Restringir cleartext ao build debug e apenas aos hosts da API local, por
  `network_security_config.xml` com `10.0.2.2`, `localhost` e `127.0.0.1`, em
  vez do `usesCleartextTraffic="true"` global anterior.
- Manter `-PAPI_BASE_URL` como override de linha de comando, útil em CI e para
  um teste pontual sem editar arquivo.

## Consequências

- A porta em uma URL de produção passa a ser um erro de build com mensagem
  explícita, em vez de um app que não conecta.
- Alternar entre API local e publicada é trocar uma palavra em `API_ENV`.
- Um clone novo tem um exemplo versionado e explicado, sem segredo no Git: a URL
  pública não é credencial, e `mobile/.env` continua fora do versionamento.
- Quem já tinha a URL em `local.properties` precisa movê-la uma vez, orientado
  pela mensagem do build.
- Testar contra a API local em aparelho físico passa a exigir acrescentar o IP da
  máquina em `network_security_config.xml`, além de `API_BASE_URL_LOCAL`. É um
  passo a mais, aceito em troca de o build debug não liberar HTTP para qualquer
  host da internet.
- O valor continua sendo de build, não de execução: trocar de ambiente exige
  recompilar. Uma troca em tempo de execução foi descartada por adicionar
  superfície de configuração ao app instalado.
- iOS não é coberto: `MainViewController` não recebe gateway de rede e o alvo
  não é validável neste ambiente, conforme o
  [ADR-004](0004-kmp-android-primeiro.md).

## Fontes

- [Android — Network security configuration](https://developer.android.com/privacy-and-security/security-config)
- [runbook de HTTPS em produção](../deploy/https-producao.md)
- [ADR-012: Nginx central e HTTPS por subdomínio](0012-nginx-central-e-https-por-subdominio.md)
