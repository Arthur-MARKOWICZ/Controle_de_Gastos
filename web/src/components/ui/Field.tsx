import { Children, cloneElement, isValidElement, type ReactElement, type ReactNode } from "react";
import styles from "./Field.module.css";

type Props = {
  id: string;
  label: string;
  hint?: ReactNode;
  error?: string | null;
  children: ReactElement<Record<string, unknown>>;
};

export function Field({ id, label, hint, error, children }: Props) {
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [errorId, hintId].filter(Boolean).join(" ") || undefined;
  const child = Children.only(children);

  if (!isValidElement(child)) {
    throw new Error("Field espera um único elemento de formulário");
  }

  const control = cloneElement(child, {
    id,
    "aria-invalid": error ? true : undefined,
    "aria-describedby": describedBy,
  });

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>{label}</label>
      {control}
      {hint && <span id={hintId} className={styles.hint}>{hint}</span>}
      {error && <p id={errorId} className={styles.error} role="alert">{error}</p>}
    </div>
  );
}
