export type ApiProblem = Readonly<{
  type: string;
  title: string;
  status: number;
  code: string;
  detail?: string;
  traceId?: string;
  errors?: Readonly<Record<string, readonly string[]>>;
}>;

export class StoryApiError extends Error {
  readonly problem: ApiProblem;

  constructor(problem: ApiProblem) {
    super(problem.title);
    this.name = "StoryApiError";
    this.problem = problem;
  }
}

export type StoryApiClientOptions = Readonly<{
  baseUrl: string;
  fetchImplementation?: typeof fetch;
}>;

export type RequestOptions = Omit<RequestInit, "body"> & Readonly<{
  body?: unknown;
}>;

export function createStoryApiClient({
  baseUrl,
  fetchImplementation = fetch,
}: StoryApiClientOptions) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");

  async function request<Response>(
    path: `/${string}`,
    options: RequestOptions = {},
  ): Promise<Response> {
    const { body: requestBody, ...requestOptions } = options;
    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");

    let body: BodyInit | undefined;
    if (requestBody !== undefined) {
      headers.set("Content-Type", "application/json");
      body = JSON.stringify(requestBody);
    }

    const response = await fetchImplementation(`${normalizedBaseUrl}${path}`, {
      ...requestOptions,
      ...(body === undefined ? {} : { body }),
      headers,
    });

    if (!response.ok) {
      const problem = (await response.json()) as ApiProblem;
      throw new StoryApiError(problem);
    }

    if (response.status === 204) {
      return undefined as Response;
    }

    return (await response.json()) as Response;
  }

  return Object.freeze({ request });
}

export type AuthSession = Readonly<{
  sessionId: string;
  createdAt: string;
  lastUsedAt: string;
  expiresAt: string;
  current: boolean;
}>;

export type BrowserAuthClientOptions = Readonly<{
  baseUrl?: string;
  fetchImplementation?: typeof fetch;
}>;

export function createBrowserAuthClient({
  baseUrl = "/api/auth",
  fetchImplementation = fetch,
}: BrowserAuthClientOptions = {}) {
  const client = createStoryApiClient({
    baseUrl,
    fetchImplementation,
  });

  async function request<Response>(
    path: `/${string}`,
    options: RequestOptions = {},
    refreshOnUnauthorized = false,
  ): Promise<Response> {
    try {
      return await client.request<Response>(path, {
        credentials: "same-origin",
        ...options,
      });
    } catch (error) {
      if (
        !refreshOnUnauthorized ||
        !(error instanceof StoryApiError) ||
        error.problem.status !== 401
      ) {
        throw error;
      }
      await client.request("/refresh", {
        credentials: "same-origin",
        method: "POST",
      });
      return client.request<Response>(path, {
        credentials: "same-origin",
        ...options,
      });
    }
  }

  return Object.freeze({
    forgotPassword(email: string) {
      return request<{ status: string }>("/password/forgot", {
        body: { email },
        method: "POST",
      });
    },
    login(email: string, password: string, mfaCode?: string) {
      return request<{ status: "AUTHENTICATED" }>("/login", {
        body: { email, password, ...(mfaCode ? { mfaCode } : {}) },
        method: "POST",
      });
    },
    logout() {
      return request<void>("/logout", { method: "POST" }, true);
    },
    beginMfa() {
      return request<{
        provisioningSecret: string;
        algorithm: string;
        digits: number;
        periodSeconds: number;
      }>("/mfa/challenge", { method: "POST" }, true);
    },
    verifyMfa(code: string) {
      return request<{ recoveryCodes: string[] }>(
        "/mfa/verify",
        { body: { code }, method: "POST" },
        true,
      );
    },
    register(email: string, password: string, acceptedTerms: boolean) {
      return request<{ status: string }>("/register", {
        body: {
          acceptedTerms,
          consentVersion: "2026-07-24",
          email,
          password,
        },
        method: "POST",
      });
    },
    resetPassword(token: string, newPassword: string) {
      return request<void>("/password/reset", {
        body: { newPassword, token },
        method: "POST",
      });
    },
    verifyEmail(token: string) {
      return request<void>("/email/verify", {
        body: { token },
        method: "POST",
      });
    },
    listSessions() {
      return request<{ sessions: AuthSession[] }>("/sessions", {}, true);
    },
    revokeAllSessions() {
      return request<void>("/sessions", { method: "DELETE" }, true);
    },
    revokeSession(sessionId: string) {
      return request<void>(
        `/sessions/${encodeURIComponent(sessionId)}`,
        { method: "DELETE" },
        true,
      );
    },
  });
}

export type Team = Readonly<{
  id: string;
  slug: string;
  name: string;
  description: string;
  state: "ACTIVE" | "SUSPENDED" | "ARCHIVED";
  version: number;
}>;

export type TeamMembership = Readonly<{
  teamId: string;
  userId: string;
  role: "OWNER" | "MEMBER";
  permissions: readonly string[];
  state: "INVITED" | "ACTIVE" | "REVOKED";
  joinedAt: string;
  version: number;
}>;

export type TeamFollow = Readonly<{
  teamId: string;
  following: boolean;
  followerCount: number;
}>;

export type BrowserTeamClientOptions = Readonly<{
  baseUrl?: string;
  fetchImplementation?: typeof fetch;
}>;

export function createBrowserTeamClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({
    baseUrl,
    fetchImplementation,
  });

  async function request<Response>(
    path: `/${string}`,
    options: RequestOptions = {},
  ): Promise<Response> {
    try {
      return await client.request<Response>(path, {
        credentials: "same-origin",
        ...options,
      });
    } catch (error) {
      if (
        !(error instanceof StoryApiError) ||
        error.problem.status !== 401
      ) {
        throw error;
      }
      const refresh = createStoryApiClient({
        baseUrl: "/api/auth",
        fetchImplementation,
      });
      await refresh.request("/refresh", {
        credentials: "same-origin",
        method: "POST",
      });
      return client.request<Response>(path, {
        credentials: "same-origin",
        ...options,
      });
    }
  }

  return Object.freeze({
    acceptInvitation(token: string) {
      return request<TeamMembership>(
        `/team-invitations/${encodeURIComponent(token)}/accept`,
        { method: "POST" },
      );
    },
    createTeam(input: {
      slug: string;
      name: string;
      description: string;
    }) {
      return request<Team>("/teams", { body: input, method: "POST" });
    },
    follow(teamId: string) {
      return request<TeamFollow>(
        `/teams/${encodeURIComponent(teamId)}/follow`,
        { method: "PUT" },
      );
    },
    followStatus(teamId: string) {
      return request<TeamFollow>(
        `/teams/${encodeURIComponent(teamId)}/follow`,
      );
    },
    getTeam(teamId: string) {
      return request<Team>(`/teams/${encodeURIComponent(teamId)}`);
    },
    inviteMember(
      teamId: string,
      input: { userId: string; permissions: readonly string[] },
      idempotencyKey: string,
    ) {
      return request<TeamMembership>(
        `/teams/${encodeURIComponent(teamId)}/members`,
        {
          body: input,
          headers: { "Idempotency-Key": idempotencyKey },
          method: "POST",
        },
      );
    },
    listMembers(teamId: string) {
      return request<TeamMembership[]>(
        `/teams/${encodeURIComponent(teamId)}/members`,
      );
    },
    listTeams() {
      return request<Team[]>("/teams");
    },
    removeMember(teamId: string, userId: string, version: number) {
      return request<void>(
        `/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(userId)}?version=${version}`,
        { method: "DELETE" },
      );
    },
    unfollow(teamId: string) {
      return request<TeamFollow>(
        `/teams/${encodeURIComponent(teamId)}/follow`,
        { method: "DELETE" },
      );
    },
    updateMemberPermissions(
      teamId: string,
      userId: string,
      version: number,
      permissions: readonly string[],
    ) {
      return request<TeamMembership>(
        `/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(userId)}/permissions`,
        { body: { permissions, version }, method: "PATCH" },
      );
    },
    updateTeam(
      teamId: string,
      input: { name: string; description: string; version: number },
    ) {
      return request<Team>(
        `/teams/${encodeURIComponent(teamId)}`,
        { body: input, method: "PATCH" },
      );
    },
  });
}
