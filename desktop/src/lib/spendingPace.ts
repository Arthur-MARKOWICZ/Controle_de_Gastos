import { formatBRL } from "./money";
import type { LimitSpendingPaceRowDTO } from "./types";

/** Texto de ritmo a partir da API — sem recalcular no cliente. */
export function spendingPaceLabel(pace: LimitSpendingPaceRowDTO): string {
  if (pace.projectedOverrunDay) {
    const [year, month, day] = pace.projectedOverrunDay.split("-");
    return `Estouro projetado em ${day}/${month}/${year}`;
  }
  return `${formatBRL(pace.dailyRemainingAllowance)} por dia até o fim do mês`;
}
