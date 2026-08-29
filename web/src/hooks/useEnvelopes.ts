"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../auth/auth-context";
import { createApiClient, type EnvelopeDTO } from "../lib/api";

export function useEnvelopes(month?: string) {
  const { client, state } = useAuth();
  const [envelopes, setEnvelopes] = useState<EnvelopeDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const api = useMemo(() => createApiClient(client), [client]);

  const refresh = useCallback(async () => {
    if (state !== "authenticated") {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await api.getEnvelopes(month);
      setEnvelopes(data);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Não foi possível carregar as verbas";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [api, month, state]);

  useEffect(() => {
    void refresh(); // eslint-disable-line react-hooks/set-state-in-effect
  }, [refresh]);

  return { envelopes, loading, error, refresh, setEnvelopes };
}
