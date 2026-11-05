"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import type { ApiClient, ParticipantDTO } from "../../lib/api";
import { Button } from "../ui/Button";
import { Notice } from "../ui/Notice";
import styles from "./ParticipantsPanel.module.css";

type Props = {
  client: ApiClient;
  envelopeId: string;
  envelopeName: string;
  onClose(): void;
};

export function ParticipantsPanel({ client, envelopeId, envelopeName, onClose }: Props) {
  const [items, setItems] = useState<ParticipantDTO[]>([]);
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setError(null);
    void client.listParticipants(envelopeId)
      .then((page) => setItems(page.items))
      .catch(() => setError("Não foi possível carregar os participantes."));
  }, [client, envelopeId]);

  useEffect(() => {
    const timer = window.setTimeout(() => load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function invite(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await client.inviteParticipant(envelopeId, email.trim());
      setItems((current) => {
        if (current.some((item) => item.userId === created.userId)) return current;
        return [...current, created];
      });
      setEmail("");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Não foi possível convidar.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(userId: string) {
    setBusy(true);
    setError(null);
    try {
      await client.removeParticipant(envelopeId, userId);
      setItems((current) => current.filter((item) => item.userId !== userId));
    } catch {
      setError("Não foi possível remover o participante.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.panel}>
      <p className={styles.lead}>
        Convidados veem <strong>{envelopeName}</strong> e registram gastos. Só você abastece, convida e encerra.
      </p>

      {error && <Notice tone="danger">{error}</Notice>}

      <ul className={styles.list}>
        {items.length === 0 && (
          <li className={styles.empty}>Nenhum participante ainda.</li>
        )}
        {items.map((item) => (
          <li key={item.userId} className={styles.item}>
            <span>{item.email}</span>
            <Button variant="text" disabled={busy} onClick={() => void remove(item.userId)}>
              Remover
            </Button>
          </li>
        ))}
      </ul>

      <form className={styles.form} onSubmit={(event) => void invite(event)}>
        <label htmlFor="invite-email">E-mail da pessoa (conta já verificada)</label>
        <input
          id="invite-email"
          type="email"
          required
          maxLength={254}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          disabled={busy}
        />
        <div className={styles.actions}>
          <Button type="submit" disabled={busy || !email.trim()}>
            {busy ? "Aguarde…" : "Convidar"}
          </Button>
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
            Fechar
          </Button>
        </div>
      </form>
    </div>
  );
}
