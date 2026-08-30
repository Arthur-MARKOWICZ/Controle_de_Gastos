# Operação da borda HTTPS

Registro de engenharia para a publicação por Nginx na VPS. Não certifica
conformidade jurídica nem autoriza cadastro público de terceiros.

## Escopo e fluxo

- Finalidade técnica: transportar com confidencialidade e integridade o acesso
  do titular à web e à API.
- Fluxo: navegador → Nginx/Hostinger → web ou backend em loopback → PostgreSQL.
- Dados em trânsito: IP, cabeçalhos HTTP, cookie de refresh, access token,
  credenciais e os dados pessoais/financeiros das rotas utilizadas.
- O Nginx não cria uma base nova nem encaminha dados a serviço de analytics. O
  access log do site fica desativado; erros são registrados somente no nível
  `error`, sem configuração para registrar corpos ou cabeçalhos de autenticação.
- Certificados e chaves TLS ficam em `/etc/letsencrypt`; segredos da aplicação
  vêm do GitHub Environment e não são persistidos em `.env` na VPS.

## Evidências e pendências

| Requisito | Estado | Evidência ou pendência |
| --- | --- | --- |
| TLS externo | atende com evidência | Nginx aceita TLS 1.2/1.3, HTTP redireciona para HTTPS e Certbot testa renovação. |
| Minimização de exposição | atende com evidência | Banco, backend e web vinculam somente a `127.0.0.1`; 3000, 5432 e 8080 não devem ser abertas no firewall. |
| Cookies de autenticação | atende com evidência | Produção força `__Secure-refresh_token`, `Secure`, `HttpOnly`, `SameSite=Strict` e path restrito. |
| Gestão de segredos da aplicação | atende com evidência | Secrets são transmitidos pelo `stdin` do SSH; Compose ignora `.env`; nenhum valor real está no Git. |
| Minimização de logs na borda | atende com evidência | `access_log off` e error log no nível `error`; Nginx não registra corpo/cookie/Authorization por configuração deste site. |
| Operador, região e transferência internacional | pendente | Controlador deve verificar contrato, região da VPS, acesso de suporte, suboperadores e mecanismo aplicável antes de terceiros. |
| Retenção e backup | pendente | Definir retenção do error log, backup cifrado, restauração e propagação de exclusão. |
| Transparência, hipótese legal e canal | pendente | Publicar aviso, identificar controlador/canal e registrar a hipótese por finalidade com revisão jurídica. |
| Direitos do titular | pendente | Exportação, correção e exclusão verificável ainda não estão prontas para abertura pública. |
| Incidentes | pendente | Definir responsáveis, contenção, preservação de evidência, avaliação e comunicação. |

## Barreira de ativação

Até as pendências receberem responsável, evidência e decisão humana, o domínio
serve somente para validação do proprietário com dados sintéticos ou
descartáveis. HTTPS é necessário, mas não suficiente para abrir cadastro ou
armazenar dados de terceiros.

Documentos relacionados:

- [Requisitos mínimos de segurança e LGPD](requisitos-de-seguranca.md)
- [Operação segura da autenticação](operacao-de-autenticacao.md)
- [Operação de renda mensal](operacao-de-renda.md)
- [ADR-012](../decisions/0012-nginx-central-e-https-por-subdominio.md)
