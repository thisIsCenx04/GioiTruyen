"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";

export function AdminLoginForm() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      const response = await fetch("/api/auth/login", {
        body: JSON.stringify({
          email: String(form.get("email") ?? ""),
          password: String(form.get("password") ?? ""),
        }),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      if (!response.ok) {
        throw new Error("Email hoặc mật khẩu quản trị không đúng.");
      }
      router.push("/");
      router.refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể đăng nhập.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <label>Email<input autoComplete="username" defaultValue="admin@gioitruyen.local" name="email" required type="email" /></label>
      <label>Mật khẩu<input autoComplete="current-password" name="password" required type="password" /></label>
      {error && <p role="alert">{error}</p>}
      <button disabled={busy} type="submit">{busy ? "Đang đăng nhập..." : "Đăng nhập quản trị"}</button>
    </form>
  );
}
