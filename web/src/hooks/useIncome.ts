"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "../auth/auth-context";
import { ApiError, createApiClient, type IncomeDTO, type IncomeHistoryPageDTO } from "../lib/api";

export function useIncome(month?: string) {
  const { client, state } = useAuth();
  const [income, setIncome] = useState<IncomeDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notConfigured, setNotConfigured] = useState(false);
  const hasDataRef = useRef(false);

  const api = useMemo(() => createApiClient(client), [client]);

  const refresh = useCallback(async () => {
    if (state !== "authenticated") {
      setLoading(false);
      setBusy(false);
      return;
    }
    if (hasDataRef.current) {
      setBusy(true);
    } else {
      setIncome(null);
      setLoading(true);
    }
    setError(null);
    setNotConfigured(false);
    try {
      const data = await api.getIncome(month);
      setIncome(data);
      hasDataRef.current = true;
    } catch (e) {
      if (e instanceof ApiError && e.status === 404 && e.code === "INCOME_NOT_CONFIGURED") {
        setIncome(null);
        setNotConfigured(true);
        hasDataRef.current = true;
      } else {
        const msg = e instanceof Error ? e.message : "Não foi possível carregar a renda";
        setError(msg);
      }
    } finally {
      setLoading(false);
      setBusy(false);
    }
  }, [api, month, state]);

  useEffect(() => {
    hasDataRef.current = false;
    const timer = window.setTimeout(() => void refresh(), 0);
    return () => window.clearTimeout(timer);
  }, [refresh]);

  return { income, loading, busy, error, notConfigured, refresh, setIncome };
}

export function useIncomeHistory(initialPage = 0, size = 20) {
  const { client, state } = useAuth();
  const [data, setData] = useState<IncomeHistoryPageDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hasDataRef = useRef(false);

  const api = useMemo(() => createApiClient(client), [client]);

  const load = useCallback(async (p: number) => {
    if (state !== "authenticated") {
      setLoading(false);
      setBusy(false);
      return;
    }
    if (hasDataRef.current) setBusy(true);
    else setLoading(true);
    setError(null);
    try {
      const res = await api.getIncomeHistory(p, size);
      setData(res);
      hasDataRef.current = true;
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Não foi possível carregar o histórico";
      setError(msg);
    } finally {
      setLoading(false);
      setBusy(false);
    }
  }, [api, size, state]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(initialPage), 0);
    return () => window.clearTimeout(timer);
  }, [load, initialPage]);

  const page = data?.page ?? initialPage;
  return { data, loading, busy, error, page, load, refresh: () => load(page) };
}
