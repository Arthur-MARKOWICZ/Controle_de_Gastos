# ADR-012: Nginx central e HTTPS por subdomínio

## Status

Aceito

## Data

2026-08-30

## Contexto

O teste do ADR-010 confirmou a entrega de PostgreSQL, backend e web por Docker
Compose. A VPS também hospedará outros aplicativos, portanto nenhum Compose
individual pode disputar as portas públicas 80 e 443. O sistema transmite
credenciais e dados financeiros e os requisitos de segurança exigem TLS em
todo tráfego externo.

A Hostinger não configura automaticamente o certificado de uma VPS. A emissão
depende de um domínio apontado para a máquina, de um servidor HTTP acessível e
da instalação administrativa do certificado.

## Decisão

- Executar uma única instância de Nginx diretamente na VPS como infraestrutura
  compartilhada. Cada aplicativo recebe um subdomínio e um arquivo em
  `/etc/nginx/sites-available`, sem entrar no Compose de outro aplicativo.
- Publicar este projeto em `https://<subdomínio>` e encaminhar `/` para a web
  em `127.0.0.1:3000` e `/api/` para o backend em `127.0.0.1:8080`, preservando
  a URI e os cabeçalhos de encaminhamento.
- Manter PostgreSQL, backend e web vinculados exclusivamente ao loopback. O
  firewall público libera somente SSH, HTTP e HTTPS; a porta 80 redireciona
  permanentemente para 443 após a emissão do certificado.
- Emitir o certificado com Certbot e o plugin Nginx. A chave TLS permanece sob
  `/etc/letsencrypt` na VPS e a renovação recarrega o Nginx somente depois de
  validar sua configuração.
- Configurar a origem pública pelo GitHub Environment em `PUBLIC_APP_URL`. O
  mesmo valor é gravado no bundle Next.js e usado pelo backend para CORS.
- Guardar senha do PostgreSQL, segredo JWT e segredo HMAC somente em GitHub
  Secrets. O workflow os envia pelo `stdin` do SSH, desabilita a leitura de
  `.env` pelo Compose e não persiste um arquivo de segredos na VPS.
- Usar cookie `__Secure-refresh_token` com `Secure`, `HttpOnly` e
  `SameSite=Strict` em produção.

Esta decisão encerra o adiamento de proxy e TLS do ADR-010. Ela não autoriza
cadastro público de terceiros: as pendências jurídicas, de retenção, backup e
resposta a incidentes continuam obrigatórias.

## Alternativas consideradas

### Nginx em cada Compose

Rejeitado porque mais de um projeto tentaria possuir 80/443 e porque a
renovação de certificados ficaria acoplada ao ciclo de cada aplicativo.

### Caddy ou Traefik compartilhado em Docker

Automatizariam parte da emissão e descoberta, mas acrescentariam uma rede e um
projeto global compartilhado antes de essa complexidade ser necessária. O
Nginx no host atende ao número atual de aplicativos com isolamento por arquivo.

### Frontend e API em subdomínios diferentes

Rejeitado neste corte porque uma única origem elimina CORS entre web e API,
simplifica cookies e exige somente um registro DNS e um certificado.

## Consequências

- Nginx e Certbot passam a ser infraestrutura global da VPS e não são
  reinstalados pelo deploy normal deste repositório.
- Um erro no Nginx pode afetar vários aplicativos; toda alteração deve passar
  por `nginx -t` e usar arquivos separados por projeto.
- O primeiro deploy com um volume PostgreSQL existente precisa aplicar a senha
  nova à role: variáveis da imagem não alteram bancos já inicializados.
- O domínio e as portas 80/443 precisam estar funcionais antes da emissão.
- Certificados e chaves TLS exigem backup e permissões próprias da VPS; não são
  copiados para o GitHub Secrets.

## Fontes

- [Nginx — módulo de proxy HTTP](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [Certbot — plugin Nginx e renovação](https://eff-certbot.readthedocs.io/en/stable/using.html)
- [Hostinger — instalar SSL com Certbot em VPS](https://www.hostinger.com/support/6865487-how-to-install-ssl-on-vps-using-certbot-at-hostinger/)
- [Docker — variáveis da imagem PostgreSQL](https://github.com/docker-library/docs/blob/master/postgres/content.md#environment-variables)
- [GitHub Actions — uso de secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
