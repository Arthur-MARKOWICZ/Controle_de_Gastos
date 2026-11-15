# ADR-0032: Busca textual no histórico com `pg_trgm`

## Status

Aceito

## Data

2026-11-14

## Contexto

A issue #94 (NEG-014) pede busca na descrição dos gastos do histórico, além de
filtros por verba e autor. A pessoa quer responder perguntas como "quanto gastei
na farmácia X?" sem rolar o mês inteiro. A descrição (`ledger_entry.description`)
é texto curto, de até 140 caracteres e opcional. O que se procura é um trecho
("farm", "uber"), não palavras inteiras.

O backend é um monólito modular em PostgreSQL 18, sem cache nem serviço de busca
(ADR-0001), e a visibilidade é aplicada nas queries da aplicação (ADR-0024).

## Decisão

- Habilitar a extensão `pg_trgm` na migração `V18`. Ela é *trusted* desde o
  PostgreSQL 13, então basta o role dono do banco ter `CREATE` no banco, sem
  superusuário.
- Criar o índice GIN de expressão
  `ledger_entry_description_trgm_idx ON ledger_entry USING gin (lower(description) gin_trgm_ops)`.
- Filtrar com `LOWER(e.description) LIKE :pattern ESCAPE '\'` em JPQL. O padrão
  é montado na aplicação: `trim`, minúsculas, escape de `\`, `%` e `_`, e
  envolto em `%…%`.
- O termo buscado é dado financeiro transitório. Ele não é persistido nem
  registrado em logs (ADR-0003).

## Alternativas consideradas

- **`ILIKE` em SQL nativo com índice em `description`**: obrigaria reescrever a
  query do histórico como nativa e perder o mapeamento da entidade.
- **Full-text (`tsvector`/`to_tsquery`)**: busca palavras com stemming e não
  encontra trechos parciais, que é o caso de uso principal.
- **`unaccent`**: exige mais uma extensão e uma função `IMMUTABLE` própria para
  indexar. Fica para quando o diário de validação mostrar necessidade.
- **Serviço externo (Elasticsearch, OpenSearch)**: desproporcional ao volume e
  vetado pela regra do monólito sem infraestrutura adicional.

## Consequências

- "farmacia" não encontra "farmácia". A busca diferencia acentos.
- Os filtros opcionais usam flags booleanas na mesma JPQL, então o planner pode
  escolher um plano genérico sem o índice trigram. No volume de uma pessoa, o
  filtro por dono ou participante já restringe bastante as linhas. Se medições
  mostrarem lentidão, a query passa a ser montada só com os predicados
  presentes.
- Termos de 1 ou 2 caracteres não aproveitam trigramas e fazem varredura
  limitada pela visibilidade.
- Revisar esta decisão se: houver pedido recorrente por busca sem acento, o
  histórico de um usuário passar de dezenas de milhares de lançamentos, ou
  surgir busca em outros campos.

## Verificação de privilégio

Produção e desenvolvimento sobem o banco pelo `compose.yaml` com a imagem
oficial `postgres:18-alpine`. O role da aplicação é o `POSTGRES_USER`, criado
pelo entrypoint da imagem como superusuário e dono do banco, e o mesmo vale
para o Testcontainers. Ele pode criar a extensão. Se o banco algum dia passar a
usar um role restrito, confirme antes do deploy com:

```sql
SELECT has_database_privilege(current_user, current_database(), 'CREATE');
```

Se a consulta retornar `false`, a extensão precisa ser criada uma vez por um
administrador antes da migração. O `IF NOT EXISTS` torna a `V18` idempotente.
