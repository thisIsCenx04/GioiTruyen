"use client";

import {
  createBrowserAuthClient,
  StoryApiError,
  type AuthSession,
} from "@gioitruyen/api-client";
import { Eye, EyeOff } from "lucide-react";
import { Link } from "react-router-dom";
import { useNavigate, useLocation, useParams, useSearchParams } from "react-router-dom";
import {
  type FormEvent,
  useEffect,
  useMemo,
  useState,
} from "react";

import { getPostLoginDestination } from "@/lib/auth";
import styles from "./auth-journeys.module.css";

type LoginResponse = Readonly<{
  accessToken?: string;
  refreshToken?: string;
}>;

/**
 * Puts the reader into a signed-in state after the API hands back a token pair.
 *
 * Both journeys end here: registering already returns the same pair as logging
 * in, so making the new account sign in immediately is a matter of storing what
 * the response carried rather than asking for the password a second time.
 */
function startSession(session: LoginResponse): void {
  document.cookie = "logged_in=true; path=/; max-age=2592000; SameSite=Lax";
  if (!session?.accessToken) {
    return;
  }
  document.cookie = `access_token=${encodeURIComponent(session.accessToken)}; path=/; max-age=2592000; SameSite=Lax`;
  localStorage.setItem("access_token", session.accessToken);
  if (session.refreshToken) {
    document.cookie = `refresh_token=${encodeURIComponent(session.refreshToken)}; path=/; max-age=2592000; SameSite=Lax`;
    localStorage.setItem("refresh_token", session.refreshToken);
  }
  // The header watches this to swap the sign-in link for the profile menu.
  window.dispatchEvent(new Event("auth-change"));
}

const auth = createBrowserAuthClient({ baseUrl: "/api/v1" });
const routes = {
  forgotPassword: "/auth/forgot-password" as string,
  login: "/login" as string,
  mfa: "/auth/mfa" as string,
  register: "/register" as string,
};

const messages: Readonly<Record<string, string>> = {
  AUTHENTICATION_FAILED: "Email hoặc mật khẩu chưa đúng.",
  AUTHENTICATION_REQUIRED: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  EMAIL_ALREADY_REGISTERED:
    "Nếu địa chỉ đủ điều kiện, hướng dẫn xác minh sẽ được gửi.",
  EMAIL_VERIFICATION_INVALID:
    "Liên kết xác minh không hợp lệ hoặc đã hết hạn.",
  LOGIN_RATE_LIMITED: "Bạn thử quá nhiều lần. Vui lòng chờ rồi thử lại.",
  MFA_ALREADY_ENABLED: "Tài khoản này đã bật xác thực hai bước.",
  MFA_CODE_INVALID: "Mã xác thực không đúng. Hãy dùng mã mới nhất.",
  MFA_RATE_LIMITED: "Bạn nhập sai quá nhiều lần. Vui lòng thử lại sau.",
  PASSWORD_RESET_INVALID: "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.",
};

function errorMessage(error: unknown) {
  if (error instanceof StoryApiError) {
    return (
      messages[error.problem.code] ??
      error.problem.detail ??
      "Yêu cầu chưa thể hoàn tất. Vui lòng thử lại."
    );
  }
  return "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại.";
}

function safeInternalPath(value: string | null) {
  if (!value || !value.startsWith("/") || value.startsWith("//")) {
    return null;
  }
  return value;
}

function Status({
  error,
  message,
}: Readonly<{ error?: string; message?: string }>) {
  if (error) {
    return (
      <p className={styles.error} role="alert">
        {error}
      </p>
    );
  }
  if (message) {
    return (
      <p className={styles.notice} role="status">
        {message}
      </p>
    );
  }
  return <div aria-live="polite" />;
}

/**
 * Only the login form shows these. Google sign-in creates the account when it
 * does not exist, so registration needs no separate provider button.
 */
function SocialAuthLinks() {
  const [searchParams] = useSearchParams();
  const returnToParam = safeInternalPath(searchParams.get("returnTo"));
  const targetReturnTo = returnToParam ?? "/";
  const [facebookNotice, setFacebookNotice] = useState("");
  return (
    <div className={styles.socialAuth} aria-label="Đăng nhập mạng xã hội">
      <a href={`/api/v1/auth/oauth2/google/authorize?returnTo=${encodeURIComponent(targetReturnTo)}`}>
        <span aria-hidden="true">G</span>
        Đăng nhập bằng Google
      </a>
      <button
        onClick={() => setFacebookNotice("Đăng nhập Facebook đang phát triển.")}
        type="button"
      >
        <span aria-hidden="true">f</span>
        Đăng nhập bằng Facebook
      </button>
      {facebookNotice ? <em role="status">{facebookNotice}</em> : null}
      <small>Hoặc dùng email</small>
    </div>
  );
}

export function LoginJourney() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [needsMfa, setNeedsMfa] = useState(false);
  const socialMessage = "";

  const oauthStatus = searchParams.get("status");
  const oauthProviderParam = searchParams.get("oauth");
  const oauthNotice =
    oauthStatus === "error"
      ? `Đăng nhập bằng ${oauthProviderParam === "google" ? "Google" : "mạng xã hội"} không thành công. Vui lòng thử lại.`
      : oauthStatus === "unconfigured"
        ? "Đăng nhập Google chưa được cấu hình thông số Client ID."
        : "";

  const searchParamsKey = searchParams.toString();
  useEffect(() => {
    if (!searchParams.has("email") && !searchParams.has("password")) {
      return;
    }
    const sanitized = new URLSearchParams(searchParamsKey);
    sanitized.delete("email");
    sanitized.delete("password");
    const query = sanitized.toString();
    navigate(query ? `${routes.login}?${query}` : routes.login, { replace: true });
  }, [navigate, searchParamsKey]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const values = new FormData(event.currentTarget);
    try {
      const res = (await auth.login(
        String(values.get("email")),
        String(values.get("password")),
        needsMfa ? String(values.get("mfaCode")) : undefined,
      )) as unknown as LoginResponse;
      startSession(res);
      const destination = getPostLoginDestination(
        res.accessToken ?? null,
        searchParams.get("returnTo"),
      );
      window.location.href = destination;
    } catch (requestError) {
      if (
        requestError instanceof StoryApiError &&
        requestError.problem.code === "MFA_CODE_REQUIRED"
      ) {
        setNeedsMfa(true);
        setError("Nhập mã từ ứng dụng xác thực để hoàn tất đăng nhập.");
      } else if (
        requestError instanceof StoryApiError &&
        requestError.problem.code === "MFA_ENROLLMENT_REQUIRED"
      ) {
        navigate(routes.mfa);
      } else {
        setError(errorMessage(requestError));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      <SocialAuthLinks />
      <Status error={error || oauthNotice} message={socialMessage} />
      <div className={styles.field}>
        <label htmlFor="login-email">Email</label>
        <input
          autoComplete="email"
          autoFocus
          id="login-email"
          maxLength={254}
          name="email"
          required
          type="email"
        />
      </div>
      <div className={styles.field}>
        <label htmlFor="login-password">Mật khẩu</label>
        <input
          autoComplete="current-password"
          id="login-password"
          maxLength={128}
          name="password"
          required
          type="password"
        />
      </div>
      {needsMfa && (
        <div className={styles.field}>
          <label htmlFor="login-mfa">Mã xác thực hoặc mã khôi phục</label>
          <input
            autoComplete="one-time-code"
            id="login-mfa"
            inputMode="numeric"
            maxLength={32}
            name="mfaCode"
            pattern="[A-Za-z0-9-]{6,32}"
            required
          />
        </div>
      )}
      <div className={styles.actions}>
        <Link to={routes.forgotPassword}>Quên mật khẩu?</Link>
        <button className={styles.primary} disabled={busy} type="submit">
          {busy ? "Đang kiểm tra…" : "Đăng nhập"}
        </button>
      </div>
      <p className={styles.hint}>
        Chưa có tài khoản?{" "}
        <Link className={styles.textLink} to={routes.register}>
          Tạo bản đọc riêng
        </Link>
      </p>
    </form>
  );
}

export function RegisterJourney() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    // Held before the first await: React clears currentTarget once the
    // synchronous handler returns, so reaching for it afterwards threw a
    // TypeError that the catch below reported as "không kết nối được máy chủ" -
    // even though the account had just been created.
    const form = event.currentTarget;
    const values = new FormData(form);
    try {
      const res = (await auth.register(
        String(values.get("email")),
        String(values.get("password")),
        values.get("acceptedTerms") === "on",
      )) as unknown as LoginResponse;
      setMessage("Tạo tài khoản thành công. Đang đưa bạn vào trang chủ…");
      form.reset();
      // Registering returns a token pair, so the new account is signed in here
      // rather than being sent to the login form to retype what it just typed.
      startSession(res);
      window.location.href = "/";
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      {/*
        No Google/Facebook buttons here on purpose: signing in with Google
        creates the account when it does not exist yet, so a separate
        "register with Google" is the same action under another name and only
        makes the form look longer than it is.
      */}
      <p className={styles.socialHint}>
        Dùng Google? Bạn không cần đăng ký — bấm{" "}
        <Link to={routes.login}>đăng nhập bằng Google</Link>, tài khoản sẽ được tạo tự động.
      </p>
      <Status error={error} message={message} />
      <div className={styles.field}>
        <label htmlFor="register-email">Email</label>
        <input
          autoComplete="email"
          autoFocus
          id="register-email"
          maxLength={254}
          name="email"
          required
          type="email"
        />
      </div>
      <div className={styles.field}>
        <label htmlFor="register-password">Mật khẩu</label>
        <div className={styles.passwordField}>
          <input
            aria-describedby="password-hint"
            autoComplete="new-password"
            id="register-password"
            maxLength={128}
            minLength={8}
            name="password"
            // Mirrors the server's rule so the browser catches it before the
            // round trip; the server still enforces it either way.
            pattern="(?=.*[A-Z])(?=.*\d).*"
            required
            title="Ít nhất 8 ký tự, có chữ in hoa và chữ số."
            type={showPassword ? "text" : "password"}
          />
          <button
            aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            aria-pressed={showPassword}
            className={styles.passwordToggle}
            onClick={() => setShowPassword((visible) => !visible)}
            type="button"
          >
            {showPassword ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
          </button>
        </div>
        <p className={styles.hint} id="password-hint">
          Ít nhất 8 ký tự, có ít nhất một chữ in hoa và một chữ số.
        </p>
      </div>
      <label className={styles.check}>
        <input name="acceptedTerms" required type="checkbox" />
        <span>
          Tôi đồng ý với điều khoản sử dụng và chính sách quyền riêng tư phiên bản
          24/07/2026.
        </span>
      </label>
      <div className={styles.actions}>
        <Link to={routes.login}>Đã có tài khoản</Link>
        <button className={styles.primary} disabled={busy} type="submit">
          {busy ? "Đang tạo…" : "Tạo tài khoản"}
        </button>
      </div>
    </form>
  );
}

export function VerifyEmailJourney({ token }: Readonly<{ token: string }>) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [verified, setVerified] = useState(false);

  async function verify() {
    setBusy(true);
    setError("");
    try {
      await auth.verifyEmail(token);
      setVerified(true);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <>
        <Status error="Liên kết xác minh thiếu mã bảo mật." />
        <Link className={styles.textLink} to={routes.login}>
          Trở về đăng nhập
        </Link>
      </>
    );
  }
  if (verified) {
    return (
      <>
        <Status message="Email đã được xác minh. Bản đọc của bạn đã sẵn sàng." />
        <Link className={styles.textLink} to={routes.login}>
          Đăng nhập để đọc tiếp
        </Link>
      </>
    );
  }
  return (
    <>
      <Status error={error} />
      <button
        className={styles.primary}
        disabled={busy}
        onClick={verify}
        type="button"
      >
        {busy ? "Đang xác minh…" : "Xác minh email"}
      </button>
    </>
  );
}

export function ForgotPasswordJourney() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    const values = new FormData(event.currentTarget);
    try {
      await auth.forgotPassword(String(values.get("email")));
    } catch {
      // Deliberately return the same message to prevent account enumeration.
    } finally {
      setMessage(
        "Nếu email tồn tại, hướng dẫn đặt lại mật khẩu sẽ được gửi trong ít phút.",
      );
      setBusy(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      <Status message={message} />
      <div className={styles.field}>
        <label htmlFor="forgot-email">Email đã đăng ký</label>
        <input
          autoComplete="email"
          autoFocus
          id="forgot-email"
          maxLength={254}
          name="email"
          required
          type="email"
        />
      </div>
      <div className={styles.actions}>
        <Link to={routes.login}>Trở về đăng nhập</Link>
        <button className={styles.primary} disabled={busy} type="submit">
          {busy ? "Đang gửi…" : "Gửi hướng dẫn"}
        </button>
      </div>
    </form>
  );
}

export function ResetPasswordJourney({ token }: Readonly<{ token: string }>) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [complete, setComplete] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const values = new FormData(event.currentTarget);
    const password = String(values.get("password"));
    if (password !== String(values.get("confirmPassword"))) {
      setError("Hai mật khẩu chưa khớp.");
      setBusy(false);
      return;
    }
    try {
      await auth.resetPassword(token, password);
      setComplete(true);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return <Status error="Liên kết đặt lại mật khẩu thiếu mã bảo mật." />;
  }
  if (complete) {
    return (
      <>
        <Status message="Mật khẩu đã đổi và các phiên cũ đã bị thu hồi." />
        <Link className={styles.textLink} to={routes.login}>
          Đăng nhập bằng mật khẩu mới
        </Link>
      </>
    );
  }
  return (
    <form className={styles.form} onSubmit={submit}>
      <Status error={error} />
      <div className={styles.field}>
        <label htmlFor="reset-password">Mật khẩu mới</label>
        <input
          autoComplete="new-password"
          autoFocus
          id="reset-password"
          maxLength={128}
          minLength={12}
          name="password"
          required
          type="password"
        />
      </div>
      <div className={styles.field}>
        <label htmlFor="reset-confirm">Nhập lại mật khẩu mới</label>
        <input
          autoComplete="new-password"
          id="reset-confirm"
          maxLength={128}
          minLength={12}
          name="confirmPassword"
          required
          type="password"
        />
      </div>
      <button className={styles.primary} disabled={busy} type="submit">
        {busy ? "Đang cập nhật…" : "Đặt mật khẩu mới"}
      </button>
    </form>
  );
}

export function MfaJourney() {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [secret, setSecret] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<readonly string[]>([]);

  async function begin() {
    setBusy(true);
    setError("");
    try {
      const challenge = await auth.beginMfa();
      setSecret(challenge.provisioningSecret);
    } catch (requestError) {
      if (
        requestError instanceof StoryApiError &&
        requestError.problem.status === 401
      ) {
        navigate(routes.login);
      } else {
        setError(errorMessage(requestError));
      }
    } finally {
      setBusy(false);
    }
  }

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const values = new FormData(event.currentTarget);
    try {
      const result = await auth.verifyMfa(String(values.get("code")));
      setRecoveryCodes(result.recoveryCodes);
      setSecret("");
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  if (recoveryCodes.length > 0) {
    return (
      <>
        <Status message="Xác thực hai bước đã bật. Mọi phiên cũ đã được thu hồi." />
        <p className={styles.hint}>
          Lưu các mã này ở nơi an toàn. Mỗi mã chỉ dùng được một lần và sẽ không
          hiển thị lại.
        </p>
        <ul className={styles.recovery} aria-label="Mã khôi phục">
          {recoveryCodes.map((code) => (
            <li key={code}>{code}</li>
          ))}
        </ul>
        <Link className={styles.textLink} to={routes.login}>
          Đăng nhập lại
        </Link>
      </>
    );
  }
  if (!secret) {
    return (
      <>
        <Status error={error} />
        <button
          className={styles.primary}
          disabled={busy}
          onClick={begin}
          type="button"
        >
          {busy ? "Đang chuẩn bị…" : "Bắt đầu thiết lập"}
        </button>
      </>
    );
  }
  return (
    <form className={styles.form} onSubmit={verify}>
      <Status error={error} />
      <div>
        <p className={styles.legend}>Khóa thiết lập</p>
        <p className={styles.secret}>{secret}</p>
        <p className={styles.hint}>
          Nhập khóa này vào ứng dụng xác thực, sau đó dùng mã 6 số vừa tạo.
        </p>
      </div>
      <div className={styles.field}>
        <label htmlFor="mfa-code">Mã xác thực 6 số</label>
        <input
          autoComplete="one-time-code"
          autoFocus
          id="mfa-code"
          inputMode="numeric"
          maxLength={6}
          name="code"
          pattern="[0-9]{6}"
          required
        />
      </div>
      <button className={styles.primary} disabled={busy} type="submit">
        {busy ? "Đang xác nhận…" : "Bật xác thực hai bước"}
      </button>
    </form>
  );
}

function dateTime(value: string) {
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function SessionsJourney() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<readonly AuthSession[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;
    auth
      .listSessions()
      .then((result) => {
        if (active) {
          setSessions(result.sessions);
        }
      })
      .catch((requestError: unknown) => {
        if (!active) {
          return;
        }
        if (
          requestError instanceof StoryApiError &&
          requestError.problem.status === 401
        ) {
          navigate(routes.login);
        } else {
          setError(errorMessage(requestError));
        }
      })
      .finally(() => {
        if (active) {
          setBusy(false);
        }
      });
    return () => {
      active = false;
    };
  }, [navigate]);

  const remoteSessions = useMemo(
    () => sessions.filter((session) => !session.current),
    [sessions],
  );

  async function revoke(sessionId: string) {
    setBusy(true);
    setError("");
    try {
      await auth.revokeSession(sessionId);
      setSessions((current) =>
        current.filter((session) => session.sessionId !== sessionId),
      );
      setMessage("Phiên đã được thu hồi.");
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  async function revokeAll() {
    setBusy(true);
    setError("");
    try {
      await auth.revokeAllSessions();
      navigate(routes.login);
    } catch (requestError) {
      setError(errorMessage(requestError));
      setBusy(false);
    }
  }

  async function logout() {
    setBusy(true);
    try {
      await auth.logout();
    } finally {
      navigate(routes.login);
    }
  }

  return (
    <>
      <Status error={error} message={message} />
      {busy && sessions.length === 0 ? (
        <p className={styles.hint} role="status">
          Đang đọc danh sách phiên…
        </p>
      ) : (
        <ul className={styles.sessionList}>
          {sessions.map((session) => (
            <li className={styles.session} key={session.sessionId}>
              <div>
                <strong>
                  Phiên {session.sessionId.slice(0, 8)}…
                  {session.current && (
                    <span className={styles.current}> · Thiết bị này</span>
                  )}
                </strong>
                <small>Dùng gần nhất: {dateTime(session.lastUsedAt)}</small>
                <small>Hết hạn: {dateTime(session.expiresAt)}</small>
              </div>
              {!session.current && (
                <button
                  className={styles.danger}
                  disabled={busy}
                  onClick={() => void revoke(session.sessionId)}
                  type="button"
                >
                  Thu hồi
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <div className={styles.inlineActions}>
        <button
          className={styles.secondary}
          disabled={busy}
          onClick={() => void logout()}
          type="button"
        >
          Đăng xuất
        </button>
        <button
          className={styles.danger}
          disabled={busy || remoteSessions.length === 0}
          onClick={() => void revokeAll()}
          type="button"
        >
          Thu hồi tất cả phiên
        </button>
      </div>
    </>
  );
}
