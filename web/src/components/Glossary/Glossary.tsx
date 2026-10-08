"use client";

import styles from "./Glossary.module.css";

const ENTRIES = [
  {
    purpose: "LIMIT",
    label: "Limite de gasto",
    description: "Controle do que você pode gastar em uma categoria neste mês. O saldo reinicia no valor-base a cada mês; gastar mais gera alerta, não bloqueio.",
  },
  {
    purpose: "FIXED",
    label: "Compromisso fixo",
    description: "Contas que não podem falhar, como aluguel ou financiamento — reservas obrigatórias do mês, sem acumular sobra.",
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
      <p className={styles.intro}>Cada verba tem um propósito diferente. Veja o que cada tipo significa e quando usá-lo.</p>
      <dl className={styles.list}>
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
