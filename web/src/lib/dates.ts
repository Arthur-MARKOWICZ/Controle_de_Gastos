export const BUSINESS_ZONE = "America/Sao_Paulo";

export function todaySaoPaulo(): string {
  // Returns YYYY-MM-DD in America/Sao_Paulo
  return new Date().toLocaleDateString("en-CA", { timeZone: BUSINESS_ZONE });
}

export function currentMonthSaoPaulo(): string {
  const today = todaySaoPaulo();
  return today.slice(0, 7);
}

export function parseMonthParam(value: string | null | undefined): string | null {
  if (!value) return null;
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(value)) return null;
  return value;
}

export function formatMonthLabel(month: string): string {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) return month;
  const [y, m] = month.split("-");
  const date = new Date(Number(y), Number(m) - 1, 1);
  return date.toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
}

export function isFutureDate(dateStr: string, todayStr: string = todaySaoPaulo()): boolean {
  return dateStr > todayStr;
}

export function addDays(dateStr: string, days: number): string {
  const d = new Date(dateStr + "T12:00:00");
  d.setDate(d.getDate() + days);
  return d.toLocaleDateString("en-CA");
}
