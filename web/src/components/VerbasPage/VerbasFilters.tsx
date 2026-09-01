"use client";

import styles from "./Verbas.module.css";

type PurposeFilter = "ALL" | "LIMIT" | "FIXED";
type SortKey = "progress" | "saldo" | "nome";

type Props = {
  active: PurposeFilter;
  onChangePurpose: (p: PurposeFilter) => void;
  counts: Record<PurposeFilter, number>;
  query: string;
  onQuery: (q: string) => void;
  sort: SortKey;
  onSort: (s: SortKey) => void;
};

const TABS: { key: PurposeFilter; label: string }[] = [
  { key: "ALL", label: "Todas" },
  { key: "LIMIT", label: "Limite de gasto" },
  { key: "FIXED", label: "Compromisso fixo" },
];

export function VerbasFilters({ active, onChangePurpose, counts, query, onQuery, sort, onSort }: Props) {
  return (
    <div className={styles.filtersBar} role="toolbar" aria-label="Filtros de verbas">
      <div className={styles.tabs} role="tablist" aria-label="Filtrar por natureza">
        {TABS.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={active === t.key}
            className={styles.tab}
            onClick={() => onChangePurpose(t.key)}
            type="button"
          >
            {t.label} ({counts[t.key] ?? 0})
          </button>
        ))}
      </div>

      <div className={styles.searchBox}>
        <label htmlFor="verbas-q">Buscar</label>
        <input
          id="verbas-q"
          type="search"
          placeholder="Nome da verba"
          value={query}
          onChange={(e) => onQuery(e.target.value)}
          aria-label="Buscar verba por nome"
        />
        <label htmlFor="verbas-sort">Ordenar</label>
        <select id="verbas-sort" value={sort} onChange={(e) => onSort(e.target.value as SortKey)} aria-label="Ordenar verbas">
          <option value="progress">Mais usado</option>
          <option value="saldo">Maior saldo</option>
          <option value="nome">A–Z</option>
        </select>
      </div>
    </div>
  );
}
