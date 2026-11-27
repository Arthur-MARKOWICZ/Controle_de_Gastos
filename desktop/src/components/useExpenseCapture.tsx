import { useCallback, useEffect, useRef, useState } from "react";
import { ExpenseDialog } from "./ExpenseDialog";
import { isSpendable } from "../lib/types";
import type { SummaryEnvelope } from "../lib/types";

/**
 * Captura de gasto compartilhada pelas telas, com o atalho da janela e toast de confirmação.
 *
 * O atalho (`Ctrl+N`, `Cmd+N` no macOS) é da janela focada, não do sistema:
 * atalho global de SO está fora do corte (ADR-0029).
 */
export function useExpenseCapture(
  envelopes: SummaryEnvelope[],
  onRegistered: () => void,
) {
  const [target, setTarget] = useState<SummaryEnvelope | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimer = useRef<number | undefined>(undefined);

  const openFor = useCallback((envelope: SummaryEnvelope) => setTarget(envelope), []);
  const close = useCallback(() => setTarget(null), []);

  const spendable = envelopes.filter(isSpendable);

  const openQuick = useCallback(() => {
    const first = spendable[0];
    if (first) setTarget((current) => current ?? first);
  }, [spendable]);

  const handleRegistered = useCallback(() => {
    const name = target?.name;
    onRegistered();
    if (name) {
      setToastMessage(`Gasto registrado em ${name}.`);
      window.clearTimeout(toastTimer.current);
      toastTimer.current = window.setTimeout(() => setToastMessage(null), 4000);
    }
  }, [onRegistered, target]);

  useEffect(() => () => window.clearTimeout(toastTimer.current), []);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const isShortcut = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "n";
      if (!isShortcut) return;
      event.preventDefault();
      openQuick();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [openQuick]);

  const dialog = target ? (
    <ExpenseDialog
      envelope={target}
      onClose={close}
      onRegistered={handleRegistered}
    />
  ) : null;

  const toast = toastMessage ? (
    <div
      role="status"
      aria-live="polite"
      style={{
        position: "fixed",
        right: "1.25rem",
        bottom: "1.25rem",
        zIndex: 40,
        padding: "0.75rem 1rem",
        borderRadius: "0.5rem",
        background: "var(--surface-inverse)",
        color: "var(--text-inverse)",
        fontSize: "0.875rem",
        fontWeight: 600,
        boxShadow: "0 8px 24px rgba(0, 0, 0, 0.25)",
      }}
    >
      {toastMessage}
    </div>
  ) : null;

  return { openFor, openQuick, dialog, toast, hasSpendable: spendable.length > 0 };
}
