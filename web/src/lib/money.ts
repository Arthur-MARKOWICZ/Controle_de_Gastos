export function formatBRL(plain: string): string {
  const normalized = plain.trim();
  if (!isValidBRL(normalized) && !isValidBRLNegative(normalized)) {
    return normalized;
  }
  const value = Number(normalized);
  if (Number.isNaN(value)) return normalized;
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
}

export function parseBRLInput(ptBR: string): string {
  const trimmed = ptBR.trim();
  if (trimmed === "") return "";
  // remove thousand separators (.), replace decimal comma with dot, keep minus
  const withoutThousands = trimmed.replace(/\./g, "");
  const withDot = withoutThousands.replace(",", ".");
  // keep only digits, minus and dot
  const cleaned = withDot.replace(/[^0-9.-]/g, "");
  return cleaned;
}

export function isValidBRL(plain: string): boolean {
  return /^\d{1,17}\.\d{2}$/.test(plain);
}

function isValidBRLNegative(plain: string): boolean {
  return /^-\d{1,17}\.\d{2}$/.test(plain);
}

export function isValidBRLInputBRL(ptBR: string): boolean {
  const plain = parseBRLInput(ptBR);
  return plain !== "" && isValidBRL(plain);
}

export function toPlainStringFromInput(ptBR: string): string | null {
  const plain = parseBRLInput(ptBR);
  if (plain === "") return null;
  // Money.brl expects normalized string with exactly 2 decimals without unnecessary rounding
  // Accept inputs like "1200" or "1200,5" but we normalize to 2 decimals? No - we require 2 decimals in API.
  // For validation, ensure it matches /^\d+(\.\d{1,2})?$/ then pad later if needed.
  // Here we just return plain if it is decimal compatible, caller will validate scale.
  return plain;
}

export function formatBRLInputMask(value: string): string {
  // Keep digits and comma/dot, format as pt-BR currency without symbol for input display
  const digitsOnly = value.replace(/\D/g, "");
  if (digitsOnly === "") return "";
  const number = Number(digitsOnly) / 100;
  return new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(number);
}

export function maskToPlain(mask: string): string {
  // mask like "1.200,50" -> "1200.50"
  return parseBRLInput(mask);
}
