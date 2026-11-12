import type { ReactNode } from "react";
import styles from "./Field.module.css";

export type FieldProps = {
  id: string;
  label: string;
  type?: "text" | "email" | "password" | "date" | "number";
  value: string;
  onChange: (value: string) => void;
  error?: string | null;
  hint?: ReactNode;
  autoFocus?: boolean;
  autoComplete?: string;
  inputMode?: "text" | "numeric" | "decimal" | "email";
  maxLength?: number;
  required?: boolean;
  disabled?: boolean;
};

/**
 * Campo com rótulo associado, erro inline e `aria-describedby` — o mesmo
 * contrato de acessibilidade da web, sem depender dos componentes dela.
 */
export function Field({
  id,
  label,
  type = "text",
  value,
  onChange,
  error,
  hint,
  autoFocus,
  autoComplete,
  inputMode,
  maxLength,
  required,
  disabled,
}: FieldProps) {
  const errorId = `${id}-erro`;
  const hintId = `${id}-ajuda`;
  const describedBy = [hint ? hintId : null, error ? errorId : null]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      {hint ? (
        <p className={styles.hint} id={hintId}>
          {hint}
        </p>
      ) : null}
      <input
        className={styles.input}
        id={id}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy === "" ? undefined : describedBy}
        autoFocus={autoFocus}
        autoComplete={autoComplete}
        inputMode={inputMode}
        maxLength={maxLength}
        required={required}
        disabled={disabled}
      />
      {error ? (
        <p className={styles.error} id={errorId}>
          {error}
        </p>
      ) : null}
    </div>
  );
}
