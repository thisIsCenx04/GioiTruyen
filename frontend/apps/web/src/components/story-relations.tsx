"use client";

import {
  createBrowserStoryRelationClient,
  type StoryRelation,
} from "@gioitruyen/api-client";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

import styles from "./story-relations.module.css";

type State = Readonly<{
  favorite: StoryRelation | null;
  follow: StoryRelation | null;
}>;

export function StoryRelations({ storyId }: Readonly<{ storyId: string }>) {
  const api = useMemo(() => createBrowserStoryRelationClient(), []);
  const [state, setState] = useState<State>({
    favorite: null,
    follow: null,
  });
  const [requiresLogin, setRequiresLogin] = useState(false);
  const [busy, setBusy] = useState<"favorite" | "follow" | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.status(storyId, "favorite"),
      api.status(storyId, "follow"),
    ]).then(([favorite, follow]) => {
      if (!cancelled) setState({ favorite, follow });
    }).catch(() => {
      if (!cancelled) setRequiresLogin(true);
    });
    return () => { cancelled = true; };
  }, [api, storyId]);

  async function toggle(relation: "favorite" | "follow") {
    const current = state[relation];
    if (!current || busy) return;
    setBusy(relation);
    try {
      const next = current.active
        ? await api.remove(storyId, relation)
        : await api.add(storyId, relation);
      setState((value) => ({ ...value, [relation]: next }));
    } catch {
      setRequiresLogin(true);
    } finally {
      setBusy(null);
    }
  }

  if (requiresLogin) {
    return (
      <p className={styles.login}>
        <Link href="/auth/login">Đăng nhập</Link> để lưu truyện và nhận chương mới.
      </p>
    );
  }

  return (
    <div aria-label="Lưu truyện" className={styles.actions}>
      <button
        aria-pressed={state.favorite?.active ?? false}
        disabled={!state.favorite || busy !== null}
        onClick={() => void toggle("favorite")}
        type="button"
      >
        <span aria-hidden="true">♡</span>
        {state.favorite?.active ? "Đã yêu thích" : "Yêu thích"}
        {state.favorite && <small>{state.favorite.count}</small>}
      </button>
      <button
        aria-pressed={state.follow?.active ?? false}
        disabled={!state.follow || busy !== null}
        onClick={() => void toggle("follow")}
        type="button"
      >
        <span aria-hidden="true">＋</span>
        {state.follow?.active ? "Đang theo dõi" : "Theo dõi"}
        {state.follow && <small>{state.follow.count}</small>}
      </button>
    </div>
  );
}
