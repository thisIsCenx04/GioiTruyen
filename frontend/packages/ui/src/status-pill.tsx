import type { ReactNode } from "react";

import styles from "./ui.module.css";

export type StatusTone = "active" | "attention" | "neutral";

export function StatusPill({
  children,
  tone = "neutral",
}: Readonly<{ children: ReactNode; tone?: StatusTone }>) {
  return (
    <span className={styles.status} data-tone={tone}>
      <span aria-hidden="true" className={styles.statusDot} />
      {children}
    </span>
  );
}
