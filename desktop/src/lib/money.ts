/**
 * Dinheiro em BRL no cliente desktop (ADR-006).
 *
 * Regra que não se negocia: o valor enviado à API é string decimal com
 * exatamente duas casas, sem arredondar. A conta de centavos usa `BigInt`, não
 * ponto flutuante.
 */

/** Formato que a API aceita e devolve: `"45.90"`, `"8000.00"`. */
const PLAIN_POSITIVE = /^\d{1,17}\.\d{2}$/;
const PLAIN_NEGATIVE = /^-\d{1,17}\.\d{2}$/;

export function isPlainAmount(value: string): boolean {
  return PLAIN_POSITIVE.test(value);
}

export function isPlainAmountAllowingNegative(value: string): boolean {
  return PLAIN_POSITIVE.test(value) || PLAIN_NEGATIVE.test(value);
}

/**
 * Valor para leitura: `"8000.00"` vira `"R$ 8.000,00"`.
 *
 * Só apresentação. O valor exato da API continua disponível em `data-amount`
 * nos componentes, para nunca se perder na formatação.
 */
export function formatBRL(plain: string): string {
  const value = plain.trim();
  if (!isPlainAmountAllowingNegative(value)) return value;
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(Number(value));
}

/**
 * Máscara pt-BR do campo de valor: o usuário digita dígitos e lê
 * `1.234,56`. Implementada sobre centavos, para a casa decimal nunca escapar.
 */
export function maskPtBR(typed: string): string {
  const digits = typed.replace(/\D/g, "");
  if (digits === "") return "";
  // Trabalha em centavos: `BigInt` para não perder casa em valores longos.
  const cents = BigInt(digits);
  const padded = cents.toString().padStart(3, "0");
  const whole = padded.slice(0, -2);
  const fraction = padded.slice(-2);
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${grouped},${fraction}`;
}

/**
 * Converte o que está na máscara para o formato que a API aceita.
 * Devolve `null` quando não dá duas casas decimais — e aí a requisição não sai.
 */
export function toPlainAmount(masked: string): string | null {
  const trimmed = masked.trim();
  if (trimmed === "") return null;
  // Formato aceito do campo: dígitos com milhar opcional e vírgula decimal.
  if (!/^\d{1,3}(\.\d{3})*,\d{2}$|^\d+,\d{2}$/.test(trimmed)) {
    return null;
  }
  const plain = trimmed.replace(/\./g, "").replace(",", ".");
  if (!isPlainAmount(plain)) return null;
  // A API exige gasto maior que zero (`amount NUMERIC(19,2) > 0`).
  if (/^0+\.00$/.test(plain)) return null;
  return plain;
}

/**
 * Saldo projetado só para apresentação: `"10.00"` − `"25.00"` = `"-15.00"`.
 * Nunca arredonda e nunca decide se o envio é permitido (ADR-007).
 */
export function subtractBRL(minuend: string, subtrahend: string): string | null {
  const a = toCents(minuend);
  const b = toCents(subtrahend);
  if (a === null || b === null) return null;
  return fromCents(a - b);
}

function toCents(plain: string): bigint | null {
  if (!isPlainAmountAllowingNegative(plain)) return null;
  const negative = plain.startsWith("-");
  const digits = plain.replace("-", "").replace(".", "");
  const cents = BigInt(digits);
  return negative ? -cents : cents;
}

function fromCents(cents: bigint): string {
  const negative = cents < 0n;
  const digits = (negative ? -cents : cents).toString().padStart(3, "0");
  const sign = negative ? "-" : "";
  return `${sign}${digits.slice(0, -2)}.${digits.slice(-2)}`;
}
