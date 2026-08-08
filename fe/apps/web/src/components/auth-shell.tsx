import { BrandMark } from "@gioitruyen/ui";
import { Link } from "react-router-dom";
import type { ReactNode } from "react";

import styles from "./auth-shell.module.css";

type AuthShellProps = Readonly<{
  chapter: string;
  children: ReactNode;
  eyebrow: string;
  lead: string;
  title: string;
  titleAccent: string;
}>;

export function AuthShell({
  chapter,
  children,
  eyebrow,
  lead,
  title,
  titleAccent,
}: AuthShellProps) {
  return (
    <main className={styles.page}>
      <aside className={styles.margin}>
        <div className={styles.artBackdrop} aria-hidden="true" />
        <Link className={styles.back} to="/">
          ← Trở về trang đọc
        </Link>
        <div className={styles.chapter}>
          <p className={styles.chapterNumber}>{chapter}</p>
          <h1>
            {title}
            <em>{titleAccent}</em>
          </h1>
          <p>{lead}</p>
        </div>
        <div className={styles.folio}>
          <BrandMark inverse />
          <span>
            Bản đọc <strong>cá nhân</strong>
          </span>
        </div>
      </aside>
      <section className={styles.sheet}>
        <div className={styles.sheetInner}>
          <p className={styles.eyebrow}>{eyebrow}</p>
          {children}
        </div>
      </section>
    </main>
  );
}

export function JourneyHeading({
  children,
  lead,
}: Readonly<{ children: ReactNode; lead: string }>) {
  return (
    <>
      <h2 className={styles.sheetTitle}>{children}</h2>
      <p className={styles.lede}>{lead}</p>
    </>
  );
}
