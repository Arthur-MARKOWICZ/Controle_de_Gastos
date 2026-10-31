"use client";

import styles from "./Glossary.module.css";

const CORE_TERMS = [
  {
    term: "Verba",
    description:
      "Um destino para parte da renda: limite de gasto, compromisso fixo, meta ou gasto anual. Cada verba tem saldo próprio.",
  },
  {
    term: "Não alocado",
    description:
      "Parte da renda que ainda não foi destinada a nenhuma verba. Se não for usada, acumula para o mês seguinte.",
  },
  {
    term: "Saldo acumulado",
    description:
      "O que sobrou ao longo dos meses (renda menos gastos). Mostra o resultado contínuo, não só o mês atual.",
  },
] as const;

const ENTRIES = [
  {
    purpose: "LIMIT",
    label: "Limite de gasto",
    description: "Controle do que você pode gastar em uma categoria. Gastar menos deixa saldo disponível; gastar mais gera alerta, não bloqueio.",
  },
  {
    purpose: "FIXED",
    label: "Compromisso fixo",
    description: "Contas que não podem falhar, como aluguel ou financiamento — reservas obrigatórias do mês.",
  },
  {
    purpose: "GOAL",
    label: "Meta de aporte",
    description: "Faça aportes de pelo menos o valor planejado a cada mês, como investimentos.",
  },
  {
    purpose: "SAVINGS_TARGET",
    label: "Meta de acumulação",
    description: "Junte qualquer valor até alcançar um alvo. O saldo acumulado não reinicia no próximo mês.",
  },
  {
    purpose: "ANNUAL_EXPENSE",
    label: "Gasto anual",
    description: "Planeje despesas anuais, como IPVA e assinaturas. Uma parcela mensal acumula até a data de vencimento escolhida.",
  },
] as const;

export function Glossary() {
  return (
    <>
      <p className={styles.intro}>
        Conceitos centrais e o que cada tipo de verba significa.
      </p>
      <dl className={styles.list} aria-label="Conceitos centrais">
        {CORE_TERMS.map((entry) => (
          <div key={entry.term} className={styles.item}>
            <dt>{entry.term}</dt>
            <dd>{entry.description}</dd>
          </div>
        ))}
      </dl>
      <h3 className={styles.sectionTitle}>Tipos de verba</h3>
      <dl className={styles.list} aria-label="Tipos de verba">
        {ENTRIES.map((entry) => (
          <div key={entry.purpose} className={styles.item}>
            <dt>{entry.label}</dt>
            <dd>{entry.description}</dd>
          </div>
        ))}
      </dl>
    </>
  );
}
