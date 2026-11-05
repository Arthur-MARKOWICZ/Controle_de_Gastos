"use client";

import { useCallback, useEffect, useState } from "react";
import type { ApiClient, InAppAlertDTO } from "../../lib/api";
import { Button } from "../ui/Button";
import { Notice } from "../ui/Notice";
import styles from "./PersistentAlerts.module.css";

type Props = {
  client: ApiClient;
  /** Recarrega junto com mutações financeiras. */
  refreshKey?: number | string;
};

export function PersistentAlerts({ client, refreshKey = 0 }: Props) {
  const [items, setItems] = useState<InAppAlertDTO[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    void client.listAlerts()
      .then((page) => setItems(page.items))
      .catch(() => setError("Não foi possível carregar os alertas persistentes."));
  }, [client]);

  useEffect(() => {
    const timer = window.setTimeout(() => load(), 0);
    return () => window.clearTimeout(timer);
  }, [load, refreshKey]);

  async function dismiss(id: string) {
    setBusyId(id);
    setError(null);
    try {
      await client.dismissAlert(id);
      setItems((current) => current.filter((alert) => alert.id !== id));
    } catch {
      setError("Não foi possível dispensar o alerta.");
    } finally {
      setBusyId(null);
    }
  }

  if (error) {
    return (
      <Notice tone="danger">
        {error}{" "}
        <Button variant="text" onClick={load}>Tentar novamente</Button>
      </Notice>
    );
  }

  if (items.length === 0) return null;

  return (
    <section className={styles.list} aria-label="Alertas persistentes de saldo negativo">
      {items.map((alert) => (
        <Notice
          key={alert.id}
          tone="warning"
          onClose={busyId === alert.id ? undefined : () => void dismiss(alert.id)}
        >
          <strong>{alert.envelopeName}</strong> ficou com saldo negativo em {alert.referenceMonth}.
          O registro do gasto não foi bloqueado. Dispense este aviso quando já tiver tratado.
        </Notice>
      ))}
    </section>
  );
}
