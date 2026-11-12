import { useCallback, useEffect, useState } from "react";
import { ExpenseDialog } from "./ExpenseDialog";
import { isSpendable } from "../lib/types";
import type { SummaryEnvelope } from "../lib/types";

/**
 * Captura de gasto compartilhada pelas telas, com o atalho da janela.
 *
 * O atalho (`Ctrl+N`, `Cmd+N` no macOS) é da janela focada, não do sistema:
 * atalho global de SO está fora do corte (ADR-0029).
 */
export function useExpenseCapture(
  envelopes: SummaryEnvelope[],
  onRegistered: () => void,
) {
  const [target, setTarget] = useState<SummaryEnvelope | null>(null);

  const openFor = useCallback((envelope: SummaryEnvelope) => setTarget(envelope), []);
  const close = useCallback(() => setTarget(null), []);

  const spendable = envelopes.filter(isSpendable);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const isShortcut = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "n";
      if (!isShortcut) return;
      event.preventDefault();
      // Sem verba gastável não há o que capturar.
      const first = spendable[0];
      if (first) setTarget((current) => current ?? first);
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [spendable]);

  const dialog = target ? (
    <ExpenseDialog
      envelope={target}
      onClose={close}
      onRegistered={onRegistered}
    />
  ) : null;

  return { openFor, dialog, hasSpendable: spendable.length > 0 };
}
