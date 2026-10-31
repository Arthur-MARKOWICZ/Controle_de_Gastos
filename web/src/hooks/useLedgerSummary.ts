"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "../auth/auth-context";
import { createApiClient, type LedgerSummaryDTO } from "../lib/api";

export function useLedgerSummary(month?: string) {
  const { client, state } = useAuth();
  const [summary, setSummary] = useState<LedgerSummaryDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hasDataRef = useRef(false);

  const api = useMemo(() => createApiClient(client), [client]);

  const refresh = useCallback(async () => {
    if (state !== "authenticated") {
      setLoading(false);
      setBusy(false);
      return;
    }
    if (hasDataRef.current) setBusy(true);
    else setLoading(true);
    setError(null);
    try {
      const data = await api.getLedgerSummary(month);
      setSummary(data);
      hasDataRef.current = true;
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Não foi possível carregar o resumo";
      setError(msg);
    } finally {
      setLoading(false);
      setBusy(false);
    }
  }, [api, month, state]);

  useEffect(() => {
    hasDataRef.current = false;
    setSummary(null);
    setLoading(true);
    void refresh(); // eslint-disable-line react-hooks/set-state-in-effect
  }, [refresh]);

  return { summary, loading, busy, error, refresh, setSummary };
}
