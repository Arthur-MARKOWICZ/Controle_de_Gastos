/** Calendário de negócio do produto: America/Sao_Paulo (ADR-006, ADR-007). */
export const BUSINESS_ZONE = "America/Sao_Paulo";

/** Hoje em `YYYY-MM-DD`, no fuso de negócio — não no fuso da máquina. */
export function todaySaoPaulo(now: Date = new Date()): string {
  return now.toLocaleDateString("en-CA", { timeZone: BUSINESS_ZONE });
}

export function currentMonthSaoPaulo(now: Date = new Date()): string {
  return todaySaoPaulo(now).slice(0, 7);
}

export function isValidMonth(value: string): boolean {
  return /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
}

/** Desloca o mês de referência, sem depender do dia atual. */
export function shiftMonth(month: string, months: number): string {
  if (!isValidMonth(month)) return month;
  const [year, monthNumber] = month.split("-").map(Number);
  const zeroBased = (year * 12 + (monthNumber - 1)) + months;
  const nextYear = Math.floor(zeroBased / 12);
  const nextMonth = (zeroBased % 12) + 1;
  return `${String(nextYear).padStart(4, "0")}-${String(nextMonth).padStart(2, "0")}`;
}

export function formatMonthLabel(month: string): string {
  if (!isValidMonth(month)) return month;
  const [year, monthNumber] = month.split("-").map(Number);
  return new Date(Date.UTC(year, monthNumber - 1, 1)).toLocaleDateString("pt-BR", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

export function formatDateLabel(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  if (!year || !month || !day) return isoDate;
  return new Date(Date.UTC(year, month - 1, day)).toLocaleDateString("pt-BR", {
    timeZone: "UTC",
  });
}
