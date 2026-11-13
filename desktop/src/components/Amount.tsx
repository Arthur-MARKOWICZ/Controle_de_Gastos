import { formatBRL } from "../lib/money";

/**
 * Valor monetário para leitura.
 *
 * O texto visível é formatado em pt-BR, e `data-amount` guarda a string
 * decimal exata que a API devolveu — nada se perde na formatação.
 */
export function Amount({
  plain,
  className,
  label,
}: {
  plain: string;
  className?: string;
  label?: string;
}) {
  return (
    <span className={className} data-amount={plain} aria-label={label}>
      {formatBRL(plain)}
    </span>
  );
}
