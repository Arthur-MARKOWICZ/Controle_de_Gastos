"use client";

import { useCallback, useEffect, useState } from "react";
import type { ApiClient, NotificationPreferencesDTO, WebPushSubscriptionDTO } from "../../lib/api";
import {
  PushPermissionDenied,
  currentEndpoint,
  pushSupport,
  subscribeThisBrowser,
  unsubscribeThisBrowser,
  type PushSupport,
} from "../../lib/web-push";
import { Button } from "../ui/Button";
import { Field } from "../ui/Field";
import { Notice } from "../ui/Notice";
import styles from "./NotificationSettings.module.css";

export type NotificationApi = Pick<
  ApiClient,
  | "getNotificationPreferences"
  | "updateNotificationPreferences"
  | "getWebPushPublicKey"
  | "registerWebPushSubscription"
  | "removeWebPushSubscription"
>;

/** Operações do navegador; injetáveis para teste. */
export type PushBrowser = {
  support(): PushSupport;
  currentEndpoint(): Promise<string | null>;
  subscribe(publicKey: string): Promise<WebPushSubscriptionDTO>;
  unsubscribe(): Promise<string | null>;
};

const BROWSER_PUSH: PushBrowser = {
  support: pushSupport,
  currentEndpoint,
  subscribe: subscribeThisBrowser,
  unsubscribe: unsubscribeThisBrowser,
};

const pad = (value: number) => String(value).padStart(2, "0");
const REMINDER_TIMES = Array.from({ length: 96 }, (_, i) => `${pad(Math.floor(i / 4))}:${pad((i % 4) * 15)}`);

type BrowserState = "idle" | "busy" | "denied" | "error";

const TYPES: Array<{ key: "dailyReminder" | "negativeBalance" | "sharedExpense"; label: string; hint: string }> = [
  {
    key: "dailyReminder",
    label: "Lembrete diário",
    hint: "No horário escolhido, só se você ainda não lançou nenhum gasto no dia.",
  },
  {
    key: "negativeBalance",
    label: "Verba com saldo negativo",
    hint: "Quando um gasto deixa uma verba sua no vermelho.",
  },
  {
    key: "sharedExpense",
    label: "Gasto em verba compartilhada",
    hint: "Quando outra pessoa registra um gasto numa verba sua.",
  },
];

export function NotificationSettings({ api, push = BROWSER_PUSH }: { api: NotificationApi; push?: PushBrowser }) {
  const [preferences, setPreferences] = useState<NotificationPreferencesDTO | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [support, setSupport] = useState<PushSupport | null>(null);
  const [publicKey, setPublicKey] = useState<string | null>(null);
  const [endpoint, setEndpoint] = useState<string | null>(null);
  const [browserState, setBrowserState] = useState<BrowserState>("idle");

  const load = useCallback(() => {
    setLoadFailed(false);
    setSupport(push.support());
    void Promise.all([
      api.getNotificationPreferences(),
      api.getWebPushPublicKey().catch(() => null),
      push.currentEndpoint().catch(() => null),
    ])
      .then(([loaded, key, current]) => {
        setPreferences(loaded);
        setPublicKey(key);
        setEndpoint(current);
      })
      .catch(() => setLoadFailed(true));
  }, [api, push]);

  useEffect(() => {
    const timer = window.setTimeout(load, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function save(next: NotificationPreferencesDTO) {
    const previous = preferences;
    setPreferences(next);
    setSaveError(null);
    try {
      setPreferences(await api.updateNotificationPreferences(next));
    } catch {
      setPreferences(previous);
      setSaveError("Não foi possível salvar. Tente novamente.");
    }
  }

  async function enableBrowser() {
    if (!publicKey) return;
    setBrowserState("busy");
    try {
      const subscription = await push.subscribe(publicKey);
      await api.registerWebPushSubscription(subscription);
      setEndpoint(subscription.endpoint);
      setBrowserState("idle");
    } catch (error) {
      setBrowserState(error instanceof PushPermissionDenied ? "denied" : "error");
    }
  }

  async function disableBrowser() {
    setBrowserState("busy");
    try {
      if (endpoint) await api.removeWebPushSubscription(endpoint);
      await push.unsubscribe();
      setEndpoint(null);
      setBrowserState("idle");
    } catch {
      setBrowserState("error");
    }
  }

  if (loadFailed) {
    return (
      <section className={styles.section} aria-labelledby="notifications-title">
        <h2 id="notifications-title" className={styles.title}>Notificações</h2>
        <Notice tone="danger">
          Não foi possível carregar as preferências de notificação.{" "}
          <Button variant="text" onClick={load}>Tentar novamente</Button>
        </Notice>
      </section>
    );
  }

  return (
    <section className={styles.section} aria-labelledby="notifications-title">
      <header className={styles.header}>
        <h2 id="notifications-title" className={styles.title}>Notificações</h2>
        <p className={styles.muted}>
          Tudo começa desligado. Os avisos não mostram nomes de verbas nem valores, só o tipo da verba.
        </p>
      </header>

      {preferences === null ? (
        <p className={styles.muted} role="status">Carregando preferências…</p>
      ) : (
        <>
          <div className={styles.browser}>
            <BrowserStatus
              support={support}
              publicKey={publicKey}
              active={endpoint !== null}
              state={browserState}
              onEnable={() => void enableBrowser()}
              onDisable={() => void disableBrowser()}
            />
          </div>

          <fieldset className={styles.types}>
            <legend className={styles.legend}>O que avisar</legend>
            {TYPES.map((type) => (
              <label key={type.key} className={styles.option}>
                <input
                  type="checkbox"
                  checked={preferences[type.key]}
                  onChange={(event) => void save({ ...preferences, [type.key]: event.target.checked })}
                />
                <span className={styles.optionText}>
                  <strong>{type.label}</strong>
                  <span>{type.hint}</span>
                </span>
              </label>
            ))}
          </fieldset>

          <div className={styles.time}>
            <Field
              id="reminder-time"
              label="Horário do lembrete"
              hint="Horário de Brasília. Vale também para o lembrete do app Android e do desktop."
            >
              <select
                value={preferences.reminderTime}
                onChange={(event) => void save({ ...preferences, reminderTime: event.target.value })}
              >
                {REMINDER_TIMES.map((time) => (
                  <option key={time} value={time}>{time}</option>
                ))}
              </select>
            </Field>
          </div>

          {saveError && <Notice tone="danger">{saveError}</Notice>}
        </>
      )}
    </section>
  );
}

function BrowserStatus({
  support,
  publicKey,
  active,
  state,
  onEnable,
  onDisable,
}: {
  support: PushSupport | null;
  publicKey: string | null;
  active: boolean;
  state: BrowserState;
  onEnable(): void;
  onDisable(): void;
}) {
  if (support === "ios-needs-install") {
    return (
      <Notice tone="info">
        No iPhone e no iPad, as notificações só chegam com o app instalado: toque em Compartilhar e depois em
        “Adicionar à Tela de Início”. Suas escolhas abaixo continuam valendo para os outros aparelhos.
      </Notice>
    );
  }
  if (support === "unsupported") {
    return (
      <Notice tone="info">
        Este navegador não recebe notificações push. Suas escolhas abaixo valem para os outros aparelhos.
      </Notice>
    );
  }
  if (support === null) return null;
  if (!publicKey) {
    return <Notice tone="info">As notificações push estão desativadas neste servidor.</Notice>;
  }

  return (
    <>
      <div className={styles.browserRow}>
        <span className={styles.optionText}>
          <strong>Este navegador</strong>
          <span>{active ? "Recebe as notificações ativadas abaixo." : "Não recebe notificações."}</span>
        </span>
        {active ? (
          <Button variant="secondary" busy={state === "busy"} onClick={onDisable}>Desativar neste navegador</Button>
        ) : (
          <Button busy={state === "busy"} onClick={onEnable}>Ativar neste navegador</Button>
        )}
      </div>
      {state === "denied" && (
        <Notice tone="warning">
          As notificações estão bloqueadas para este site. Libere nas configurações do navegador (ícone ao lado do
          endereço) e tente de novo.
        </Notice>
      )}
      {state === "error" && <Notice tone="danger">Não foi possível alterar as notificações deste navegador.</Notice>}
    </>
  );
}
