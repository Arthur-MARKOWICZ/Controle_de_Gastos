"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../auth/auth-context";
import { createApiClient, type LimitSpendingPaceRowDTO } from "../lib/api";

/** Carrega ritmo LIMIT; falha da API resulta em mapa vazio sem derrubar a overview. */
export function useLimitSpendingPace(month?: string) {
  const { client, state } = useAuth();
  const [byEnvelopeId, setByEnvelopeId] = useState<Record<string, LimitSpendingPaceRowDTO>>({});
  const api = useMemo(() => createApiClient(client), [client]);

  const refresh = useCallback(async () => {
    if (state !== "authenticated" || !month) {
      setByEnvelopeId({});
      return;
    }
    try {
      const rows = await api.getLimitSpendingPace(month);
      const next: Record<string, LimitSpendingPaceRowDTO> = {};
      for (const row of rows) {
        next[row.envelopeId] = row;
      }
      setByEnvelopeId(next);
    } catch {
      setByEnvelopeId({});
    }
  }, [api, month, state]);

  useEffect(() => {
    const timer = window.setTimeout(() => void refresh(), 0);
    return () => window.clearTimeout(timer);
  }, [refresh]);

  return { byEnvelopeId, refresh };
}
