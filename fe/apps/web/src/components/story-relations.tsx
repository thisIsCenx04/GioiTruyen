"use client";

import {
  createBrowserStoryRelationClient,
  type StoryRelation,
} from "@gioitruyen/api-client";
import { BookmarkPlus, Heart } from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";

import { loginHref } from "@/lib/auth";
import styles from "./story-relations.module.css";
import { API_BASE_URL, apiFetch } from "@/lib/api-base";

type State = Readonly<{
  favorite: StoryRelation | null;
  follow: StoryRelation | null;
}>;

export function StoryRelations({ storyId }: Readonly<{ storyId: string }>) {
  const api = useMemo(() => createBrowserStoryRelationClient({ baseUrl: API_BASE_URL, fetchImplementation: apiFetch }), []);
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
      <div aria-label="Lưu truyện" className={styles.actions}>
        <Link to={loginHref()}><Heart aria-hidden="true" /> Yêu thích</Link>
        <Link to={loginHref()}><BookmarkPlus aria-hidden="true" /> Theo dõi</Link>
      </div>
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
        <Heart aria-hidden="true" />
        {state.favorite?.active ? "Đã yêu thích" : "Yêu thích"}
        {state.favorite && <small>{state.favorite.count}</small>}
      </button>
      <button
        aria-pressed={state.follow?.active ?? false}
        disabled={!state.follow || busy !== null}
        onClick={() => void toggle("follow")}
        type="button"
      >
        <BookmarkPlus aria-hidden="true" />
        {state.follow?.active ? "Đang theo dõi" : "Theo dõi"}
        {state.follow && <small>{state.follow.count}</small>}
      </button>
    </div>
  );
}
