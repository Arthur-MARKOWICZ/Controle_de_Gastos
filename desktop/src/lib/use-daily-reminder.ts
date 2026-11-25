import { useEffect, useRef } from "react";
import { startDailyReminder, type ReminderDeps } from "./reminder";

/**
 * Mantém o lembrete agendado enquanto o componente vive (sessão aberta) e o
 * lembrete estiver ativo. Desmontar, ao sair da conta, cancela.
 */
export function useDailyReminder(config: { enabled: boolean; time: string }, deps: ReminderDeps): void {
  const depsRef = useRef(deps);
  useEffect(() => {
    depsRef.current = deps;
  });

  const { enabled, time } = config;
  useEffect(() => {
    if (!enabled) return undefined;
    return startDailyReminder(time, {
      hasExpenseToday: () => depsRef.current.hasExpenseToday(),
      notify: () => depsRef.current.notify(),
    });
  }, [enabled, time]);
}
