"use client";

import { useEffect, useId, useRef } from "react";
import styles from "./Dialog.module.css";

type Props = {
  open: boolean;
  onClose(): void;
  title: string;
  children: React.ReactNode;
};

const FOCUSABLE =
  "button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])";

export function Dialog({ open, onClose, title, children }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const previousActive = useRef<Element | null>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open) {
      previousActive.current = document.activeElement;
      if (!dialog.open) dialog.showModal();
      const body = dialog.querySelector<HTMLElement>("[data-dialog-body]");
      const focusable = body?.querySelector<HTMLElement>(FOCUSABLE);
      focusable?.focus();
    } else if (dialog.open) {
      dialog.close();
      if (previousActive.current instanceof HTMLElement) previousActive.current.focus();
    }
  }, [open]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const onCancel = (e: Event) => {
      e.preventDefault();
      onClose();
    };
    dialog.addEventListener("cancel", onCancel);
    return () => dialog.removeEventListener("cancel", onCancel);
  }, [onClose]);

  useEffect(() => {
    if (!open) return;
    const dialog = dialogRef.current;
    if (!dialog) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== "Tab" || !dialog.open) return;
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
        (el) => !el.hasAttribute("disabled"),
      );
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    dialog.addEventListener("keydown", onKeyDown);
    return () => dialog.removeEventListener("keydown", onKeyDown);
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-modal="true"
      aria-labelledby={titleId}
      onClick={(e) => {
        if (e.target === dialogRef.current) onClose();
      }}
    >
      {open ? (
        <div className={styles.content}>
          <div className={styles.header}>
            <h2 id={titleId}>{title}</h2>
            <button type="button" aria-label="Fechar" onClick={onClose} className={styles.close}>
              ×
            </button>
          </div>
          <div className={styles.body} data-dialog-body>
            {children}
          </div>
        </div>
      ) : null}
    </dialog>
  );
}
