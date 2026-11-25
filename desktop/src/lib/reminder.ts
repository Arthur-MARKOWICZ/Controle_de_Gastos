import { BUSINESS_ZONE } from "./dates";

/**
 * Lembrete diário do desktop (ADR-0033, spec ui-desktop/daily-reminder).
 * Só funciona com o aplicativo aberto e não recupera horário perdido.
 */

const DAY_MS = 24 * 60 * 60 * 1000;

function saoPauloClock(now: Date): { hour: number; minute: number; second: number; ms: number } {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: BUSINESS_ZONE,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const value = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? 0);
  return { hour: value("hour"), minute: value("minute"), second: value("second"), ms: now.getMilliseconds() };
}

/** Milissegundos até o próximo `HH:mm` em São Paulo; horário de hoje já passado vale amanhã. */
export function millisUntilNextReminder(now: Date, time: string): number {
  const [hour, minute] = time.split(":").map(Number);
  const clock = saoPauloClock(now);
  const nowMs = ((clock.hour * 60 + clock.minute) * 60 + clock.second) * 1000 + clock.ms;
  const targetMs = (hour * 60 + minute) * 60 * 1000;
  const delta = targetMs - nowMs;
  return delta > 0 ? delta : delta + DAY_MS;
}

export type ReminderDeps = {
  /** Consulta "lançou hoje" na API; falha (sem sessão, sem rede) não notifica. */
  hasExpenseToday(): Promise<boolean>;
  notify(): Promise<void>;
};

/** Agenda o lembrete e devolve a função que o cancela. */
export function startDailyReminder(time: string, deps: ReminderDeps): () => void {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let stopped = false;

  /**
   * Depois de um disparo, conta a partir de um minuto à frente: um timer que
   * acorde milissegundos antes da hora não pode agendar um segundo aviso no mesmo dia.
   */
  const scheduleNext = (afterFire = false) => {
    if (stopped) return;
    const margin = afterFire ? 60_000 : 0;
    const delay = millisUntilNextReminder(new Date(Date.now() + margin), time) + margin;
    timer = setTimeout(() => void fire(), delay);
  };

  const fire = async () => {
    try {
      if (!(await deps.hasExpenseToday()) && !stopped) {
        await deps.notify();
      }
    } catch {
      // Sem sessão ou sem resposta: nenhum aviso, como diz o spec.
    }
    scheduleNext(true);
  };

  scheduleNext();
  return () => {
    stopped = true;
    if (timer !== undefined) clearTimeout(timer);
  };
}
