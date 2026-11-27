import { useEffect, useState } from "react";
import { getLimitSpendingPace } from "./api";
import type { LimitSpendingPaceRowDTO } from "./types";

/** Carrega ritmo LIMIT; falha resulta em mapa vazio sem derrubar a tela. */
export function useLimitSpendingPace(month: string, revision: number) {
  const [byEnvelopeId, setByEnvelopeId] = useState<Record<string, LimitSpendingPaceRowDTO>>({});

  useEffect(() => {
    let active = true;
    getLimitSpendingPace(month)
      .then((rows) => {
        if (!active) return;
        const next: Record<string, LimitSpendingPaceRowDTO> = {};
        for (const row of rows) {
          next[row.envelopeId] = row;
        }
        setByEnvelopeId(next);
      })
      .catch(() => {
        if (active) setByEnvelopeId({});
      });
    return () => {
      active = false;
    };
  }, [month, revision]);

  return byEnvelopeId;
}
