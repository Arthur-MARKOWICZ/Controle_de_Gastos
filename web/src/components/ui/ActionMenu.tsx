"use client";

import { useEffect, useId, useRef, useState } from "react";
import { CaretDownIcon, CaretUpIcon } from "./NavIcons";
import styles from "./ActionMenu.module.css";

export type ActionMenuItem = {
  key: string;
  label: string;
  ariaLabel?: string;
  danger?: boolean;
  onClick(): void;
};

type Props = {
  label?: string;
  ariaLabel: string;
  items: ActionMenuItem[];
};

export function ActionMenu({ label = "Mais", ariaLabel, items }: Props) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  function onMenuKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    const buttons = Array.from(
      event.currentTarget.querySelectorAll<HTMLButtonElement>("[role='menuitem']"),
    );
    if (buttons.length === 0) return;
    const currentIndex = buttons.indexOf(document.activeElement as HTMLButtonElement);
    if (event.key === "ArrowDown") {
      event.preventDefault();
      buttons[(currentIndex + 1) % buttons.length]?.focus();
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      buttons[(currentIndex - 1 + buttons.length) % buttons.length]?.focus();
    }
  }

  return (
    <div ref={rootRef} className={styles.root} data-open={open ? "true" : undefined}>
      <button
        ref={triggerRef}
        type="button"
        className={styles.trigger}
        aria-label={ariaLabel}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => setOpen((value) => !value)}
      >
        {label}
        <span aria-hidden="true">{open ? <CaretUpIcon /> : <CaretDownIcon />}</span>
      </button>
      {open && (
        <div id={menuId} role="menu" className={styles.menu} onKeyDown={onMenuKeyDown}>
          {items.map((item) => (
            <button
              key={item.key}
              type="button"
              role="menuitem"
              className={item.danger ? `${styles.item} ${styles.itemDanger}` : styles.item}
              aria-label={item.ariaLabel ?? item.label}
              onClick={() => {
                setOpen(false);
                item.onClick();
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
