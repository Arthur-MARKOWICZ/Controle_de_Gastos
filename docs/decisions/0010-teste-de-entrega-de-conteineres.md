# ADR-010: Testar entrega de contêineres antes da borda HTTPS

## Status

Aceito

## Data

2026-08-30

## Contexto

O primeiro objetivo operacional é confirmar que a VPS consegue receber e
executar as imagens da API, da web e do PostgreSQL. A máquina tem 2 vCPU e
4 GB de RAM, e não será usado registry externo. A configuração anterior
adiantava Caddy, TLS, limites de recursos e segredos de runtime, antes de a
entrega básica ter sido validada.

## Decisão

- Manter um único `compose.yaml` com `postgres`, `backend` e `web`.
- O GitHub Actions executa a verificação do projeto, constrói as duas imagens
  com a tag fixa `latest` e as transfere por SSH para a VPS.
- A VPS só carrega as imagens e executa `infra/deploy.sh`; o workflow não
  transfere Compose, proxy ou configurações adicionais.
- Portas ficam em loopback por padrão. O teste externo pode usar túnel SSH ou,
  temporariamente, `HOST_BIND_ADDRESS=0.0.0.0` com firewall restritivo.
- HTTPS, domínio, Caddy/Nginx, segredos de produção, backup, limites de
  recursos, monitoramento e abertura pública ficam fora deste teste.

## Consequências

- O fluxo é pequeno e permite validar CI, transferência de imagens, Docker e
  Flyway sem dependências de borda HTTP.
- Os valores padrão do Compose são apenas para teste e não podem ser usados
  como configuração de produção.
- A próxima etapa de produção exige novo ADR para TLS/proxy e gestão de
  segredos de runtime, em conformidade com os requisitos de privacidade.

## Fontes

- [GitHub Actions — secrets](https://docs.github.com/en/actions/concepts/security/secrets)
- [Docker `image load`](https://docs.docker.com/reference/cli/docker/image/load/)
- [Docker Compose `up`](https://docs.docker.com/reference/cli/docker/compose/up/)
