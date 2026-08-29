"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../auth/auth-context";
import { ApiError, createApiClient, type IncomeDTO, type IncomeHistoryPageDTO } from "../lib/api";

export function useIncome(month?: string) {
  const { client, state } = useAuth();
  const [income, setIncome] = useState<IncomeDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notConfigured, setNotConfigured] = useState(false);

  const api = useMemo(() => createApiClient(client), [client]);

  const refresh = useCallback(async () => {
    if (state !== "authenticated") {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    setNotConfigured(false);
    try {
      const data = await api.getIncome(month);
      setIncome(data);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404 && e.code === "INCOME_NOT_CONFIGURED") {
        setIncome(null);
        setNotConfigured(true);
      } else {
        const msg = e instanceof Error ? e.message : "Não foi possível carregar a renda";
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  }, [api, month, state]);

  useEffect(() => {
    void refresh(); // eslint-disable-line react-hooks/set-state-in-effect
  }, [refresh]);

  return { income, loading, error, notConfigured, refresh, setIncome };
}

export function useIncomeHistory(initialPage = 0, size = 20) {
  const { client, state } = useAuth();
  const [data, setData] = useState<IncomeHistoryPageDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const api = useMemo(() => createApiClient(client), [client]);

  const load = useCallback(async (p: number) => {
    if (state !== "authenticated") {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await api.getIncomeHistory(p, size);
      setData(res);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Não foi possível carregar o histórico";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [api, size, state]);

  useEffect(() => {
    void load(initialPage); // eslint-disable-line react-hooks/set-state-in-effect
  }, [load, initialPage]);

  const page = data?.page ?? initialPage;
  return { data, loading, error, page, load, refresh: () => load(page) };
}
