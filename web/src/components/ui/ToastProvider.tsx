"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { XIcon } from "./NavIcons";
import styles from "./ToastProvider.module.css";

export type ToastTone = "success" | "info" | "warning" | "danger";

export type ToastAction = {
  label: string;
  onClick(): void;
};

export type ToastInput = {
  message: string;
  tone?: ToastTone;
  action?: ToastAction;
  durationMs?: number;
};

type ToastItem = ToastInput & {
  id: number;
  tone: ToastTone;
};

type ToastContextValue = {
  showToast(input: ToastInput | string): void;
  dismissToast(id: number): void;
};

const noopContext: ToastContextValue = {
  showToast: () => undefined,
  dismissToast: () => undefined,
};

const ToastContext = createContext<ToastContextValue>(noopContext);

const DEFAULT_DURATION_MS = 5000;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(1);
  const timers = useRef<Map<number, number>>(new Map());

  const dismissToast = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer !== undefined) {
      window.clearTimeout(timer);
      timers.current.delete(id);
    }
    setToasts((current) => current.filter((item) => item.id !== id));
  }, []);

  const scheduleDismiss = useCallback(
    (id: number, durationMs: number) => {
      if (durationMs <= 0) return;
      const existing = timers.current.get(id);
      if (existing !== undefined) window.clearTimeout(existing);
      const timer = window.setTimeout(() => dismissToast(id), durationMs);
      timers.current.set(id, timer);
    },
    [dismissToast],
  );

  const pauseDismiss = useCallback((id: number) => {
    const existing = timers.current.get(id);
    if (existing !== undefined) {
      window.clearTimeout(existing);
      timers.current.delete(id);
    }
  }, []);

  const showToast = useCallback(
    (input: ToastInput | string) => {
      const normalized: ToastInput = typeof input === "string" ? { message: input } : input;
      const id = nextId.current++;
      const durationMs = normalized.durationMs ?? DEFAULT_DURATION_MS;
      const item: ToastItem = {
        ...normalized,
        id,
        tone: normalized.tone ?? "success",
      };
      setToasts((current) => [...current.slice(-2), item]);
      scheduleDismiss(id, durationMs);
    },
    [scheduleDismiss],
  );

  useEffect(() => {
    const map = timers.current;
    return () => {
      map.forEach((timer) => window.clearTimeout(timer));
      map.clear();
    };
  }, []);

  return (
    <ToastContext.Provider value={{ showToast, dismissToast }}>
      {children}
      <div className={styles.stack} aria-label="Notificações">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`${styles.toast} ${styles[toast.tone]}`}
            role={toast.tone === "danger" ? "alert" : "status"}
            aria-live={toast.tone === "danger" ? "assertive" : "polite"}
            onMouseEnter={() => pauseDismiss(toast.id)}
            onMouseLeave={() => scheduleDismiss(toast.id, toast.durationMs ?? DEFAULT_DURATION_MS)}
            onFocus={() => pauseDismiss(toast.id)}
            onBlur={() => scheduleDismiss(toast.id, toast.durationMs ?? DEFAULT_DURATION_MS)}
          >
            <span className={styles.message}>{toast.message}</span>
            <div className={styles.actions}>
              {toast.action && (
                <button
                  type="button"
                  className={styles.actionButton}
                  onClick={() => {
                    toast.action?.onClick();
                    dismissToast(toast.id);
                  }}
                >
                  {toast.action.label}
                </button>
              )}
              <button
                type="button"
                className={styles.closeButton}
                aria-label="Fechar notificação"
                onClick={() => dismissToast(toast.id)}
              >
                <XIcon />
              </button>
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  return useContext(ToastContext);
}
