import { useEffect, useState } from "react";
import { getNotificationPreferences, updateNotificationPreferences } from "../lib/api";
import { reminderPermission } from "../lib/ipc";
import type { NotificationPreferences } from "../lib/types";
import styles from "./ReminderDialog.module.css";

const pad = (value: number) => String(value).padStart(2, "0");
const TIMES = Array.from({ length: 96 }, (_, i) => `${pad(Math.floor(i / 4))}:${pad((i % 4) * 15)}`);

type Props = {
  onClose: () => void;
  /** Preferências salvas na API, para o app reagendar o lembrete. */
  onSaved: (preferences: NotificationPreferences) => void;
};

/** Lembrete diário do desktop (spec ui-desktop/daily-reminder). */
export function ReminderDialog({ onClose, onSaved }: Props) {
  const [preferences, setPreferences] = useState<NotificationPreferences | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [denied, setDenied] = useState(false);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    let active = true;
    getNotificationPreferences()
      .then((loaded) => active && setPreferences(loaded))
      .catch(() => active && setError("Não foi possível carregar o lembrete."));
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  async function save(next: NotificationPreferences) {
    const previous = preferences;
    setPreferences(next);
    setError(null);
    setPending(true);
    try {
      const saved = await updateNotificationPreferences(next);
      setPreferences(saved);
      onSaved(saved);
    } catch {
      setPreferences(previous);
      setError("Não foi possível salvar o lembrete. Tente de novo.");
    } finally {
      setPending(false);
    }
  }

  async function toggle(enabled: boolean) {
    if (!preferences) return;
    if (enabled) {
      const granted = await reminderPermission().catch(() => false);
      setDenied(!granted);
    }
    await save({ ...preferences, dailyReminder: enabled });
  }

  return (
    <div className={styles.backdrop}>
      <div className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby="titulo-lembrete">
        <h2 className={styles.title} id="titulo-lembrete">
          Lembrete diário
        </h2>
        <p className={styles.note}>
          Avisa no horário escolhido se você ainda não lançou nenhum gasto no dia. O lembrete só dispara com o
          Verbas aberto; horário perdido não é avisado depois.
        </p>

        {error ? (
          <p className={styles.error} role="alert">
            {error}
          </p>
        ) : null}

        {preferences === null ? (
          error ? null : <p role="status">Carregando lembrete…</p>
        ) : (
          <>
            <label className={styles.toggle}>
              <input
                type="checkbox"
                checked={preferences.dailyReminder}
                disabled={pending}
                onChange={(event) => void toggle(event.target.checked)}
              />
              Lembrar todo dia
            </label>

            <label className={styles.field} htmlFor="horario-lembrete">
              Horário do lembrete
              <select
                id="horario-lembrete"
                value={preferences.reminderTime}
                disabled={pending}
                onChange={(event) => void save({ ...preferences, reminderTime: event.target.value })}
              >
                {TIMES.map((time) => (
                  <option key={time} value={time}>
                    {time}
                  </option>
                ))}
              </select>
            </label>
            <p className={styles.hint}>Horário de Brasília, o mesmo da web e do celular.</p>

            {denied ? (
              <p className={styles.warning}>
                As notificações do sistema estão bloqueadas para o Verbas. Libere nas configurações de notificação
                do sistema; o lembrete fica salvo.
              </p>
            ) : null}
          </>
        )}

        <div className={styles.actions}>
          <button className={styles.close} type="button" onClick={onClose}>
            Fechar
          </button>
        </div>
      </div>
    </div>
  );
}
