"use client";

import {
  createBrowserStoryRelationClient,
  type StoryRelation,
} from "@gioitruyen/api-client";
import { BookmarkPlus, Heart } from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";

import { isLoggedIn, loginHref } from "@/lib/auth";
import styles from "./story-relations.module.css";
import { API_BASE_URL, authedFetch } from "@/lib/api-base";

type State = Readonly<{
  favorite: StoryRelation | null;
  follow: StoryRelation | null;
}>;

/** True only for 401/403; anything else is a transport or server problem. */
function isUnauthorised(cause: unknown) {
  const status = (cause as { problem?: { status?: number }; status?: number } | null)?.problem?.status
    ?? (cause as { status?: number } | null)?.status;
  return status === 401 || status === 403;
}

export function StoryRelations({ storyId }: Readonly<{ storyId: string }>) {
  // authedFetch, not apiFetch: these endpoints need an account, and sending no
  // token made every call 401, which showed signed-in readers a login prompt.
  const api = useMemo(
    () => createBrowserStoryRelationClient({ baseUrl: API_BASE_URL, fetchImplementation: authedFetch }),
    [],
  );
  const [state, setState] = useState<State>({
    favorite: null,
    follow: null,
  });
  // Only a genuine absence of credentials sends the reader to the login page.
  // Treating every failure as "not signed in" turned a dropped request into a
  // logout prompt for someone already holding a valid session.
  const [requiresLogin, setRequiresLogin] = useState(!isLoggedIn());
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<"favorite" | "follow" | null>(null);

  useEffect(() => {
    if (!isLoggedIn()) {
      setRequiresLogin(true);
      return undefined;
    }
    let cancelled = false;
    Promise.all([
      api.status(storyId, "favorite"),
      api.status(storyId, "follow"),
    ]).then(([favorite, follow]) => {
      if (cancelled) return;
      setState({ favorite, follow });
      setRequiresLogin(false);
    }).catch((cause) => {
      if (cancelled) return;
      if (isUnauthorised(cause)) setRequiresLogin(true);
      else setError("Không tải được trạng thái. Thử lại sau.");
    });
    return () => { cancelled = true; };
  }, [api, storyId]);

  async function toggle(relation: "favorite" | "follow") {
    const current = state[relation];
    if (!current || busy) return;
    setBusy(relation);
    setError("");
    try {
      const next = current.active
        ? await api.remove(storyId, relation)
        : await api.add(storyId, relation);
      setState((value) => ({ ...value, [relation]: next }));
    } catch (cause) {
      if (isUnauthorised(cause)) setRequiresLogin(true);
      else setError("Không lưu được thay đổi. Thử lại sau.");
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
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
    </div>
  );
}
