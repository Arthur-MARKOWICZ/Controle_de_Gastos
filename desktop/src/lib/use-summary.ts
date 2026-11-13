import { useCallback, useEffect, useState } from "react";
import { getLedgerSummary } from "./api";
import { IpcError } from "./ipc";
import type { LedgerSummary } from "./types";

/**
 * Resumo do mês vindo de `GET /api/v1/ledger/summary`, a fonte autoritativa de
 * saldo com carry e ledger.
 *
 * O resultado vive no estado do componente enquanto a tela está aberta e é
 * refeito a cada troca de mês e a cada lançamento. Não é cache: é uma
 * requisição por mês (ADR-0029, decisão 6).
 */
export type SummaryState =
  | { kind: "loading" }
  | { kind: "ready"; summary: LedgerSummary }
  | { kind: "error"; message: string };

export function useSummary(
  month: string,
  revision: number,
  onAuthRequired: () => void,
): { state: SummaryState; reload: () => void } {
  const [state, setState] = useState<SummaryState>({ kind: "loading" });
  const [localRevision, setLocalRevision] = useState(0);

  const reload = useCallback(() => setLocalRevision((n) => n + 1), []);

  useEffect(() => {
    let active = true;
    setState({ kind: "loading" });
    getLedgerSummary(month)
      .then((summary) => {
        if (active) setState({ kind: "ready", summary });
      })
      .catch((error: Error) => {
        if (!active) return;
        if (error instanceof IpcError && error.authRequired) {
          onAuthRequired();
          return;
        }
        setState({ kind: "error", message: error.message });
      });
    return () => {
      active = false;
    };
  }, [month, revision, localRevision, onAuthRequired]);

  return { state, reload };
}
