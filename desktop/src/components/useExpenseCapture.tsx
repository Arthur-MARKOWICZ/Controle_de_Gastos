import { useCallback, useEffect, useRef, useState } from "react";
import { ExpenseDialog } from "./ExpenseDialog";
import { getExpenseSuggestions } from "../lib/api";
import { isSpendable } from "../lib/types";
import type { SummaryEnvelope } from "../lib/types";

/** Evento emitido pelo processo nativo (atalho global / bandeja). */
export const OPEN_QUICK_EXPENSE_EVENT = "open-quick-expense";

type CaptureTarget = {
  envelope: SummaryEnvelope;
  initialAmount: string | null;
};

/**
 * Captura de gasto compartilhada pelas telas, com atalho de janela, sugestão da
 * API e abertura via system-launch (ADR-0029 emendado).
 *
 * O atalho de janela (`Ctrl+N` / `Cmd+N`) e o global de SO (`Ctrl+Alt+G`)
 * coexistem: este hook escuta o evento nativo e o teclado da janela focada.
 */
export function useExpenseCapture(
  envelopes: SummaryEnvelope[],
  onRegistered: () => void,
) {
  const [target, setTarget] = useState<CaptureTarget | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimer = useRef<number | undefined>(undefined);
  const envelopesRef = useRef(envelopes);
  const targetRef = useRef<CaptureTarget | null>(null);
  envelopesRef.current = envelopes;
  targetRef.current = target;

  const close = useCallback(() => setTarget(null), []);

  const spendable = envelopes.filter(isSpendable);

  const openFor = useCallback((envelope: SummaryEnvelope) => {
    setTarget({ envelope, initialAmount: null });
    void getExpenseSuggestions()
      .then((reply) => {
        const match = reply.items.find((item) => item.envelopeId === envelope.id);
        const amount = match?.suggestedAmount?.amount ?? null;
        setTarget((current) =>
          current && current.envelope.id === envelope.id
            ? { envelope, initialAmount: amount }
            : current,
        );
      })
      .catch(() => {
        /* mantém a verba forçada sem valor */
      });
  }, []);

  const openQuick = useCallback(() => {
    if (targetRef.current) return;
    const list = envelopesRef.current.filter(isSpendable);
    if (list.length === 0) return;

    const fallback = list[0];
    const token = fallback.id;
    setTarget({ envelope: fallback, initialAmount: null });

    void getExpenseSuggestions()
      .then((reply) => {
        const first = reply.items[0];
        const suggested = first
          ? list.find((envelope) => envelope.id === first.envelopeId)
          : undefined;
        const envelope = suggested ?? fallback;
        const amount = first && suggested ? first.suggestedAmount?.amount ?? null : null;
        setTarget((current) => {
          if (!current || current.envelope.id !== token) return current;
          return { envelope, initialAmount: amount };
        });
      })
      .catch(() => {
        /* fallback já aberto com a primeira spendable */
      });
  }, []);

  const handleRegistered = useCallback(() => {
    const name = target?.envelope.name;
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

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    let cancelled = false;

    async function subscribe() {
      try {
        const { listen } = await import("@tauri-apps/api/event");
        if (cancelled) return;
        unlisten = await listen(OPEN_QUICK_EXPENSE_EVENT, () => {
          openQuick();
        });
      } catch {
        /* fora do Tauri (testes / Vite no browser) */
      }
    }

    void subscribe();
    return () => {
      cancelled = true;
      unlisten?.();
    };
  }, [openQuick]);

  const dialog = target ? (
    <ExpenseDialog
      envelope={target.envelope}
      initialAmount={target.initialAmount}
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
