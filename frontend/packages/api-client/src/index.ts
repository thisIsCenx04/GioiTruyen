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

export type ReauthenticationGrant = Readonly<{
  grantToken: string;
  grantType: "Scoped-Reauthentication";
  expiresIn: number;
  expiresAt: string;
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
    reauthenticate(input: {
      password: string;
      mfaCode?: string;
      scope:
        | "TOPUP_MANUAL_APPROVAL"
        | "WITHDRAWAL_APPROVAL"
        | "MONETIZATION_KILL_SWITCH"
        | "SYSTEM_CONFIG_CHANGE";
      targetType: string;
      targetId: string;
    }) {
      return request<ReauthenticationGrant>("/reauth/grants", {
        body: input,
        method: "POST",
      }, true);
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

export type TeamAnalyticsReport = Readonly<{
  teamId: string;
  period: "7D" | "30D" | "90D";
  from: string;
  to: string;
  totals: Readonly<{
    rawEvents: number;
    completedViews: number;
    validViews: number;
    invalidViews: number;
    qualityRate: number;
  }>;
  series: readonly Readonly<{
    start: string;
    rawEvents: number;
    completedViews: number;
    validViews: number;
    invalidViews: number;
    qualityRate: number;
  }>[];
  reasons: readonly Readonly<{
    code: string;
    count: number;
  }>[];
}>;

export type PublishingStory = Readonly<{
  id: string;
  teamId: string;
  slug: string;
  title: string;
  synopsis: string;
  origin: "ORIGINAL" | "TRANSLATED";
  language: string;
  categoryIds: readonly string[];
  coverAssetId: string | null;
  completionStatus: "ONGOING" | "COMPLETED" | "HIATUS";
  workflowStatus: string;
  currentRevision: string;
  revisionNo: number;
  version: number;
  updatedAt: string;
}>;

export type PublishingChapter = Readonly<{
  id: string;
  storyId: string;
  teamId: string;
  number: number;
  slug: string;
  title: string;
  workflowStatus: string;
  currentRevision: string;
  revisionNo: number;
  version: number;
  contentHtml: string;
  wordCount: number;
  updatedAt: string;
}>;

export type PublishingSchedule = Readonly<{
  scheduleId: string;
  storyId: string;
  teamId: string;
  revision: string;
  chapterCount: number;
  state: "SCHEDULED";
  publishAt: string;
  timeZone: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}>;

export type ModerationCheck = Readonly<{
  rule: string;
  outcome: "PASS" | "FLAG" | "MANUAL" | "FAIL" | "TIMEOUT";
  code: string;
  policyVersion: string;
}>;

export type ModerationCase = Readonly<{
  id: string;
  targetType: "STORY";
  targetId: string;
  teamId: string;
  state: "OPEN" | "CLAIMED";
  priority: number;
  manualFallback: boolean;
  checks: readonly ModerationCheck[];
  assigneeId: string | null;
  leaseUntil: string | null;
  submittedAt: string;
  version: number;
}>;

export type ModerationReviewDetail = Readonly<{
  review: ModerationCase;
  story: Readonly<{
    revisionId: string;
    revisionNo: number;
    title: string;
    synopsis: string;
    origin: string;
    language: string;
    categoryIds: readonly string[];
    coverAssetId: string | null;
    checksum: string;
  }>;
  chapters: readonly Readonly<{
    chapterId: string;
    revisionId: string;
    number: number;
    revisionNo: number;
    contentHtml: string;
    plainText: string;
    checksum: string;
  }>[];
}>;

export type ModerationDecision = Readonly<{
  reviewId: string;
  state: "APPROVED" | "CHANGES_REQUESTED" | "REJECTED";
  decision: "APPROVE" | "REQUEST_CHANGES" | "REJECT";
  reasonCode: string;
  policyVersion: string;
  reviewerId: string;
  decidedAt: string;
  version: number;
}>;

export type ModerationAppeal = Readonly<{
  id: string;
  reviewId: string;
  appellantId: string;
  originalReviewerId: string;
  statement: string;
  status: "PENDING" | "UPHELD" | "OVERTURNED";
  decision: "UPHOLD" | "OVERTURN" | null;
  decisionReasonCode: string | null;
  decisionNote: string | null;
  appealReviewerId: string | null;
  createdAt: string;
  deadline: string;
  decidedAt: string | null;
}>;

export type CopyrightCase = Readonly<{
  id: string;
  storyId: string;
  claimantId: string;
  claimantName: string;
  statement: string;
  evidenceMediaIds: readonly string[];
  status: "PENDING" | "APPEALED" | "TAKEDOWN" | "REINSTATED";
  createdAt: string;
  responseDueAt: string;
  holdUntil: string;
  appealStatement: string | null;
  appealActorId: string | null;
  appealedAt: string | null;
  decision: "TAKEDOWN" | "REINSTATE" | null;
  decisionReasonCode: string | null;
  decisionNote: string | null;
  reviewerId: string | null;
  decidedAt: string | null;
}>;

export type NotificationItem = Readonly<{
  id: string;
  type: string;
  title: string;
  body: string;
  data: Readonly<Record<string, string>>;
  readAt: string | null;
  createdAt: string;
}>;

export type NotificationPage = Readonly<{
  items: readonly NotificationItem[];
  nextCursor: string | null;
  hasMore: boolean;
  unreadCount: number;
}>;

export type NotificationPreference = Readonly<{
  userId: string;
  emailEnabled: boolean;
  pushEnabled: boolean;
  categories: readonly string[];
  consentVersion: string;
  consentedAt: string | null;
  updatedAt: string;
  version: number;
}>;

export type WalletBalance = Readonly<{
  currency: "XU";
  availableXu: number;
  reservedXu: number;
  version: number;
  asOf: string;
}>;

export type TopupStatus =
  | "AWAITING_PAYMENT"
  | "CREDITED"
  | "PENDING_REVIEW"
  | "REJECTED";

export type TopupRequest = Readonly<{
  id: string;
  amountVnd: number;
  creditedXu: number;
  discountPercent: number;
  discountVersion: number;
  transferReference: string;
  qrPayload: string;
  status: TopupStatus;
  expiresAt: string;
  createdAt: string;
}>;

export type DonationReceipt = Readonly<{
  donationId: string;
  teamId: string;
  amountXu: number;
  message: string;
  ledgerTransactionId: string;
  status: "POSTED";
  replayed: boolean;
  createdAt: string;
}>;

export type WithdrawalState =
  | "PENDING_REVIEW"
  | "APPROVED"
  | "PROCESSING"
  | "PAID"
  | "REJECTED"
  | "FAILED";

export type WithdrawalReceipt = Readonly<{
  id: string;
  teamId: string;
  grossAmountXu: number;
  feeXu: number;
  netAmountXu: number;
  feeRuleVersion: string;
  destinationMasked: string;
  state: WithdrawalState;
  replayed: boolean;
  createdAt: string;
}>;

export type WithdrawalPage = Readonly<{
  items: readonly WithdrawalReceipt[];
  nextCursor?: string;
}>;

export type WithdrawalDecision = Readonly<{
  withdrawalId: string;
  state: "APPROVED" | "REJECTED";
  reviewerId: string;
  reason: string;
  riskLevel: "STANDARD" | "HIGH_VALUE";
  riskRuleVersion: string;
  releaseTransactionId?: string;
  replayed: boolean;
  reviewedAt: string;
}>;

export type ManualTopupApproval = Readonly<{
  topupId: string;
  paymentEventId: string;
  ledgerTransactionId: string;
  status: "CREDITED";
  decidedAt: string;
}>;

export type TopupRejection = Readonly<{
  topupId: string;
  paymentEventId: string;
  status: "REJECTED";
  replayed: boolean;
}>;

export type MonetizationKillSwitch = Readonly<{
  operation: "TOPUP_CREDIT" | "WITHDRAWAL_REQUEST" | "WITHDRAWAL_PAYOUT";
  engaged: boolean;
  version: number;
  changedBy: string;
  changedAt: string;
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
    analytics(teamId: string, period: "7D" | "30D" | "90D" = "30D") {
      return request<TeamAnalyticsReport>(
        `/teams/${encodeURIComponent(teamId)}/analytics/views?period=${period}`,
      );
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

export function createBrowserPublishingClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });

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
      await createStoryApiClient({
        baseUrl: "/api/auth",
        fetchImplementation,
      }).request("/refresh", {
        credentials: "same-origin",
        method: "POST",
      });
      return client.request<Response>(path, {
        credentials: "same-origin",
        ...options,
      });
    }
  }

  const storyPath = (teamId: string, storyId: string) =>
    `/teams/${encodeURIComponent(teamId)}/stories/${encodeURIComponent(storyId)}` as const;

  return Object.freeze({
    stories(teamId: string) {
      return request<PublishingStory[]>(
        `/teams/${encodeURIComponent(teamId)}/stories`,
      );
    },
    story(teamId: string, storyId: string) {
      return request<PublishingStory>(storyPath(teamId, storyId));
    },
    createStory(
      teamId: string,
      input: {
        title: string;
        synopsis: string;
        origin: "ORIGINAL" | "TRANSLATED";
        language: string;
        categoryIds: readonly string[];
        coverAssetId: string | null;
      },
      idempotencyKey: string,
    ) {
      return request<PublishingStory>(
        `/teams/${encodeURIComponent(teamId)}/stories`,
        {
          body: input,
          headers: { "Idempotency-Key": idempotencyKey },
          method: "POST",
        },
      );
    },
    updateStory(
      teamId: string,
      storyId: string,
      version: number,
      input: {
        title: string;
        synopsis: string;
        categoryIds: readonly string[];
        coverAssetId: string | null;
        completionStatus: "ONGOING" | "COMPLETED" | "HIATUS";
      },
    ) {
      return request<PublishingStory>(storyPath(teamId, storyId), {
        body: input,
        headers: { "If-Match": `"${version}"` },
        method: "PATCH",
      });
    },
    chapters(teamId: string, storyId: string) {
      return request<PublishingChapter[]>(
        `${storyPath(teamId, storyId)}/chapters`,
      );
    },
    async createChapter(
      teamId: string,
      storyId: string,
      input: { number: number; title: string; contentHtml: string },
    ) {
      const chapter = await request<PublishingChapter>(
        `${storyPath(teamId, storyId)}/chapters`,
        { body: input, method: "POST" },
      );
      return { ...chapter, contentHtml: input.contentHtml };
    },
    async updateChapter(
      teamId: string,
      storyId: string,
      chapterId: string,
      version: number,
      input: { title: string; contentHtml: string },
    ) {
      const chapter = await request<PublishingChapter>(
        `${storyPath(teamId, storyId)}/chapters/${encodeURIComponent(chapterId)}`,
        {
          body: input,
          headers: { "If-Match": `"${version}"` },
          method: "PATCH",
        },
      );
      return { ...chapter, contentHtml: input.contentHtml };
    },
    submit(teamId: string, storyId: string, idempotencyKey: string) {
      return request<{
        reviewId: string;
        storyId: string;
        state: string;
        version: number;
      }>(`${storyPath(teamId, storyId)}/submit`, {
        headers: { "Idempotency-Key": idempotencyKey },
        method: "POST",
      });
    },
    schedule(
      teamId: string,
      storyId: string,
      input: {
        publishAt: string;
        timeZone: string;
        revision: string;
      },
    ) {
      return request<PublishingSchedule>(
        `${storyPath(teamId, storyId)}/schedule`,
        { body: input, method: "POST" },
      );
    },
  });
}

export function createBrowserModerationClient({
  baseUrl = "/api/moderation",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });
  const casePath = (reviewId: string) =>
    `/cases/${encodeURIComponent(reviewId)}` as const;
  async function request<Response>(
    path: `/${string}`,
    options: RequestOptions = {},
  ) {
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
      await createStoryApiClient({
        baseUrl: "/api/auth",
        fetchImplementation,
      }).request("/refresh", {
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
    list(cursor?: string) {
      const query = new URLSearchParams({
        limit: "20",
        state: "OPEN",
        type: "PUBLISHING",
      });
      if (cursor) query.set("cursor", cursor);
      return request<{
        items: ModerationCase[];
        nextCursor: string | null;
      }>(`/cases?${query.toString()}`);
    },
    detail(reviewId: string) {
      return request<ModerationReviewDetail>(casePath(reviewId));
    },
    claim(reviewId: string, version: number) {
      return request<ModerationCase>(casePath(reviewId), {
        headers: { "If-Match": `"${version}"` },
        method: "PATCH",
      });
    },
    decide(
      reviewId: string,
      version: number,
      input: {
        decision: "APPROVE" | "REQUEST_CHANGES" | "REJECT";
        reasonCode: string;
        note: string | null;
        evidenceRefs: readonly string[];
        policyVersion: string;
      },
    ) {
      return request<ModerationDecision>(
        `${casePath(reviewId)}/decisions`,
        {
          body: input,
          headers: { "If-Match": `"${version}"` },
          method: "POST",
        },
      );
    },
    decideAppeal(
      reviewId: string,
      appealId: string,
      input: {
        decision: "UPHOLD" | "OVERTURN";
        reasonCode: string;
        note: string;
      },
    ) {
      return request<ModerationAppeal>(
        `${casePath(reviewId)}/appeals/${encodeURIComponent(appealId)}/decisions`,
        { body: input, method: "POST" },
      );
    },
    decideCopyrightCase(
      caseId: string,
      input: {
        decision: "TAKEDOWN" | "REINSTATE";
        reasonCode: string;
        note: string;
      },
    ) {
      return request<CopyrightCase>(
        `/copyright/cases/${encodeURIComponent(caseId)}/decisions`,
        { body: input, method: "POST" },
      );
    },
  });
}

export function createBrowserAppealClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });

  return Object.freeze({
    create(reviewId: string, statement: string) {
      return client.request<ModerationAppeal>(
        `/moderation/cases/${encodeURIComponent(reviewId)}/appeals`,
        {
          body: { statement },
          credentials: "same-origin",
          method: "POST",
        },
      );
    },
  });
}

export function createBrowserCopyrightClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });
  return Object.freeze({
    create(input: {
      storyId: string;
      claimantName: string;
      statement: string;
      evidenceMediaIds: readonly string[];
    }) {
      return client.request<CopyrightCase>("/copyright/cases", {
        body: input,
        credentials: "same-origin",
        method: "POST",
      });
    },
    appeal(caseId: string, statement: string) {
      return client.request<CopyrightCase>(
        `/copyright/cases/${encodeURIComponent(caseId)}/appeals`,
        {
          body: { statement },
          credentials: "same-origin",
          method: "POST",
        },
      );
    },
  });
}

export function createBrowserNotificationClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });
  const request = <Response>(
    path: `/${string}`,
    options: RequestOptions = {},
  ) =>
    client.request<Response>(path, {
      credentials: "same-origin",
      ...options,
    });

  return Object.freeze({
    list(cursor?: string) {
      const query = new URLSearchParams({ limit: "20" });
      if (cursor) query.set("cursor", cursor);
      return request<NotificationPage>(
        `/notifications?${query.toString()}`,
      );
    },
    markRead(notificationId: string) {
      return request<NotificationItem>(
        `/notifications/${encodeURIComponent(notificationId)}/read`,
        { method: "POST" },
      );
    },
    markAllRead() {
      return request<{ readBefore: string; unreadCount: number }>(
        "/notifications/read-all",
        { method: "POST" },
      );
    },
    preferences() {
      return request<NotificationPreference>("/notification-preferences");
    },
    updatePreferences(
      version: number,
      input: {
        emailEnabled: boolean;
        pushEnabled: boolean;
        categories: readonly string[];
        consentGranted: boolean;
      },
    ) {
      return request<NotificationPreference>("/notification-preferences", {
        body: input,
        headers: { "If-Match": `"${version}"` },
        method: "PATCH",
      });
    },
    registerPush(input: {
      endpoint: string;
      p256dh: string;
      auth: string;
    }) {
      return request<{ id: string; createdAt: string; updatedAt: string }>(
        "/notification-push-subscriptions",
        { body: input, method: "POST" },
      );
    },
    removePush(subscriptionId: string) {
      return request<void>(
        `/notification-push-subscriptions/${encodeURIComponent(subscriptionId)}`,
        { method: "DELETE" },
      );
    },
  });
}

export type PublicStory = Readonly<{
  id: string;
  teamId: string;
  slug: string;
  title: string;
  synopsis: string;
  categoryIds: readonly string[];
  origin: "ORIGINAL" | "TRANSLATED";
  language: string;
  completionStatus: "ONGOING" | "COMPLETED" | "HIATUS";
  publishedAt: string;
  updatedAt: string;
  version: number;
}>;

export type HomeStorySummary = Readonly<{
  id: string;
  teamId: string;
  slug: string;
  title: string;
  coverAssetId: string | null;
  publishedAt: string;
}>;

export type HomeSection = Readonly<{
  id: "latest" | "completed" | "original";
  type: "LATEST" | "COMPLETED" | "ORIGINAL";
  title: string;
  stories: readonly HomeStorySummary[];
}>;

export type HomeResponse = Readonly<{
  locale: string;
  version: string;
  generatedAt: string;
  sections: readonly HomeSection[];
}>;

export type PublicChapter = Readonly<{
  id: string;
  storyId: string;
  number: number;
  slug: string;
  title: string;
  publishedAt: string;
  version: number;
}>;

export type PublishedChapterDetail = PublicChapter &
  Readonly<{
    revisionId: string;
    revisionNo: number;
    contentHtml: string;
    wordCount: number;
    etag: string;
    previous: Readonly<{
      id: string;
      number: number;
      slug: string;
      title: string;
    }> | null;
    next: Readonly<{
      id: string;
      number: number;
      slug: string;
      title: string;
    }> | null;
  }>;

export type ReadingProgress = Readonly<{
  storyId: string;
  chapterId: string;
  position: number;
  deviceUpdatedAt: string;
  updatedAt: string;
  version: number;
}>;

export type ReadingHistoryPage = Readonly<{
  items: readonly ReadingProgress[];
  nextCursor: string | null;
  hasMore: boolean;
}>;

export type ReadingSessionGrant = Readonly<{
  sessionId: string;
  sessionToken: string;
  expiresAt: string;
  heartbeatIntervalSeconds: number;
}>;

export type ReadingHeartbeatReceipt = Readonly<{
  batchId: string;
  nextSequence: number;
  duplicate: boolean;
}>;

export type ReadingCompletionReceipt = Readonly<{
  completionId: string;
  status: "COMPLETION_PENDING";
  duplicate: boolean;
}>;

export type StoryRelation = Readonly<{
  storyId: string;
  type: "FAVORITE" | "FOLLOW";
  active: boolean;
  count: number;
}>;

export type CommentTargetType = "STORY" | "CHAPTER";

export type CommunityComment = Readonly<{
  id: string;
  targetType: CommentTargetType;
  targetId: string;
  parentId: string | null;
  rootId: string;
  depth: number;
  author: Readonly<{
    id: string;
    displayName: string;
    avatarMediaId: string | null;
  }>;
  body: string;
  status: "VISIBLE" | "HIDDEN" | "DELETED";
  version: number;
  createdAt: string;
  updatedAt: string;
}>;

export type CommentPage = Readonly<{
  items: readonly CommunityComment[];
  nextCursor: string | null;
  hasMore: boolean;
}>;

export type ReactionTargetType = "STORY" | "CHAPTER" | "COMMENT";

export type CommunityReaction = Readonly<{
  targetType: ReactionTargetType;
  targetId: string;
  active: boolean;
  count: number;
}>;

export type ReportReason =
  | "copyright"
  | "impersonation"
  | "harassment"
  | "sexual_content"
  | "illegal_content"
  | "spam"
  | "broken_content"
  | "other";

export type CommunityReport = Readonly<{
  id: string;
  targetType: "STORY" | "CHAPTER" | "COMMENT" | "TEAM" | "USER";
  targetId: string;
  reason: Uppercase<ReportReason>;
  status:
    | "RECEIVED"
    | "TRIAGED"
    | "INVESTIGATING"
    | "RESOLVED"
    | "REJECTED"
    | "APPEALED";
  riskScore: number;
  duplicate: boolean;
  createdAt: string;
}>;

export function createBrowserReportClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });
  return Object.freeze({
    create(input: {
      targetType: "story" | "chapter" | "comment" | "team" | "user";
      targetId: string;
      reasonCode: ReportReason;
      detail?: string;
      evidenceMediaIds?: readonly string[];
    }) {
      return client.request<CommunityReport>("/reports", {
        body: input,
        credentials: "same-origin",
        method: "POST",
      });
    },
  });
}

export function createBrowserReactionClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });
  const path = (
    targetType: ReactionTargetType,
    targetId: string,
  ): `/${string}` =>
    `/reactions/${targetType.toLowerCase()}/${encodeURIComponent(targetId)}`;
  const request = (
    targetType: ReactionTargetType,
    targetId: string,
    method = "GET",
  ) => client.request<CommunityReaction>(path(targetType, targetId), {
    credentials: "same-origin",
    method,
  });
  return Object.freeze({
    status: (targetType: ReactionTargetType, targetId: string) =>
      request(targetType, targetId),
    add: (targetType: ReactionTargetType, targetId: string) =>
      request(targetType, targetId, "PUT"),
    remove: (targetType: ReactionTargetType, targetId: string) =>
      request(targetType, targetId, "DELETE"),
  });
}

export function createBrowserCommentClient({
  fetchImplementation = fetch,
}: Pick<BrowserTeamClientOptions, "fetchImplementation"> = {}) {
  const publicClient = createStoryApiClient({
    baseUrl: "/api/catalog",
    fetchImplementation,
  });
  const privateClient = createStoryApiClient({
    baseUrl: "/api/workspace",
    fetchImplementation,
  });
  return Object.freeze({
    list(
      targetType: CommentTargetType,
      targetId: string,
      cursor?: string,
    ) {
      const query = new URLSearchParams({
        limit: "20",
        targetId,
        targetType: targetType.toLowerCase(),
      });
      if (cursor) query.set("cursor", cursor);
      return publicClient.request<CommentPage>(`/comments?${query}`, {
        cache: "no-store",
      });
    },
    create(input: {
      targetType: CommentTargetType;
      targetId: string;
      parentId?: string;
      body: string;
    }) {
      return privateClient.request<CommunityComment>("/comments", {
        body: {
          ...input,
          targetType: input.targetType.toLowerCase(),
        },
        credentials: "same-origin",
        method: "POST",
      });
    },
    update(commentId: string, version: number, body: string) {
      return privateClient.request<CommunityComment>(
        `/comments/${encodeURIComponent(commentId)}`,
        {
          body: { body },
          credentials: "same-origin",
          headers: { "If-Match": `"${version}"` },
          method: "PATCH",
        },
      );
    },
    remove(commentId: string, version: number) {
      return privateClient.request<CommunityComment>(
        `/comments/${encodeURIComponent(commentId)}`,
        {
          credentials: "same-origin",
          headers: { "If-Match": `"${version}"` },
          method: "DELETE",
        },
      );
    },
  });
}

export function createBrowserStoryRelationClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });
  const path = (storyId: string, relation: "favorite" | "follow") =>
    `/stories/${encodeURIComponent(storyId)}/${relation}` as const;
  const request = (
    storyId: string,
    relation: "favorite" | "follow",
    method = "GET",
  ) => client.request<StoryRelation>(path(storyId, relation), {
    credentials: "same-origin",
    method,
  });
  return Object.freeze({
    status: (storyId: string, relation: "favorite" | "follow") =>
      request(storyId, relation),
    add: (storyId: string, relation: "favorite" | "follow") =>
      request(storyId, relation, "PUT"),
    remove: (storyId: string, relation: "favorite" | "follow") =>
      request(storyId, relation, "DELETE"),
  });
}

export function createBrowserReadingSessionClient({
  baseUrl = "/api",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });
  return Object.freeze({
    start(input: {
      storyId: string;
      chapterId: string;
      anonymousId?: string;
    }) {
      return client.request<ReadingSessionGrant>("/reading-sessions", {
        body: input,
        credentials: "same-origin",
        method: "POST",
      });
    },
    heartbeat(
      sessionId: string,
      sessionToken: string,
      input: {
        batchId: string;
        heartbeats: readonly {
          sequence: number;
          occurredAt: string;
          position: number;
          activeSeconds: number;
        }[];
      },
    ) {
      return client.request<ReadingHeartbeatReceipt>(
        `/reading-sessions/${encodeURIComponent(sessionId)}/heartbeats`,
        {
          body: input,
          credentials: "same-origin",
          headers: { "X-Reading-Session-Token": sessionToken },
          method: "POST",
        },
      );
    },
    complete(
      sessionId: string,
      sessionToken: string,
      input: {
        completionId: string;
        finalSequence: number;
        occurredAt: string;
        position: number;
      },
    ) {
      return client.request<ReadingCompletionReceipt>(
        `/reading-sessions/${encodeURIComponent(sessionId)}/complete`,
        {
          body: input,
          credentials: "same-origin",
          headers: { "X-Reading-Session-Token": sessionToken },
          method: "POST",
        },
      );
    },
  });
}

export function createBrowserReadingClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });
  const path = (storyId: string) =>
    `/me/reading-progress/${encodeURIComponent(storyId)}` as const;

  return Object.freeze({
    progress(storyId: string) {
      return client.request<ReadingProgress>(path(storyId), {
        credentials: "same-origin",
      });
    },
    history(options: { cursor?: string; limit?: number } = {}) {
      const query = new URLSearchParams();
      if (options.cursor) query.set("cursor", options.cursor);
      if (options.limit !== undefined) {
        query.set("limit", String(options.limit));
      }
      const suffix = query.size > 0 ? `?${query.toString()}` : "";
      return client.request<ReadingHistoryPage>(
        `/me/reading-history${suffix}`,
        { credentials: "same-origin" },
      );
    },
    deleteHistory(storyId: string) {
      return client.request<void>(
        `/me/reading-history/${encodeURIComponent(storyId)}`,
        {
          credentials: "same-origin",
          method: "DELETE",
        },
      );
    },
    synchronize(
      storyId: string,
      input: {
        chapterId: string;
        position: number;
        deviceUpdatedAt: string;
      },
      version?: number,
    ) {
      return client.request<ReadingProgress>(path(storyId), {
        body: input,
        credentials: "same-origin",
        ...(version === undefined
          ? {}
          : { headers: { "If-Match": `"${version}"` } }),
        method: "PUT",
      });
    },
  });
}

export type SearchHit = Readonly<{
  story: HomeStorySummary;
  score: number;
  highlights: readonly string[];
}>;

export type SearchResponse = Readonly<{
  items: readonly SearchHit[];
  nextCursor: string | null;
  hasMore: boolean;
  facets: Readonly<Record<string, Readonly<Record<string, number>>>>;
  tookMs: number;
}>;

export type SuggestionResponse = Readonly<{
  items: readonly Readonly<{
    id: string;
    slug: string;
    title: string;
    coverAssetId: string | null;
  }>[];
  nextCursor: string | null;
  hasMore: boolean;
}>;

export type CategoryTaxonomy = Readonly<{
  version: string;
  groups: readonly Readonly<{
    group: string;
    label: string;
    categories: readonly Readonly<{
      id: string;
      slug: string;
      name: string;
    }>[];
  }>[];
}>;

export function createPublicCatalogClient({
  baseUrl,
  fetchImplementation = fetch,
}: StoryApiClientOptions) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });

  return Object.freeze({
    home(locale = "vi-VN") {
      return client.request<HomeResponse>(
        `/home?locale=${encodeURIComponent(locale)}`,
      );
    },
    categories() {
      return client.request<CategoryTaxonomy>("/categories");
    },
    story(identifier: string) {
      return client.request<PublicStory>(
        `/stories/${encodeURIComponent(identifier)}`,
      );
    },
    chapters(identifier: string, limit = 100) {
      return client.request<{
        items: PublicChapter[];
        nextCursor: string | null;
        hasMore: boolean;
      }>(
        `/stories/${encodeURIComponent(identifier)}/chapters?limit=${limit}`,
      );
    },
    chapter(chapterId: string) {
      return client.request<PublishedChapterDetail>(
        `/chapters/${encodeURIComponent(chapterId)}`,
      );
    },
    search(query: string, limit = 20) {
      return client.request<SearchResponse>(
        `/search?q=${encodeURIComponent(query)}&limit=${limit}`,
      );
    },
    suggestions(query: string, limit = 8) {
      return client.request<SuggestionResponse>(
        `/search/suggestions?q=${encodeURIComponent(query)}&limit=${limit}`,
      );
    },
  });
}

export function createBrowserWalletClient({
  baseUrl = "/api/workspace",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });

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
      await createStoryApiClient({
        baseUrl: "/api/auth",
        fetchImplementation,
      }).request("/refresh", {
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
    balance() {
      return request<WalletBalance>("/wallets/me");
    },
    createTopup(amountVnd: number, idempotencyKey: string) {
      return request<TopupRequest>("/wallets/me/topups", {
        body: { amountVnd },
        headers: { "Idempotency-Key": idempotencyKey },
        method: "POST",
      });
    },
    donate(
      input: { teamId: string; amountXu: number; message: string },
      idempotencyKey: string,
    ) {
      return request<DonationReceipt>("/donations", {
        body: input,
        headers: { "Idempotency-Key": idempotencyKey },
        method: "POST",
      });
    },
    getTopup(requestId: string) {
      return request<TopupRequest>(
        `/wallets/me/topups/${encodeURIComponent(requestId)}`,
      );
    },
    topupHistory() {
      return request<TopupRequest[]>("/wallets/me/topups");
    },
    createWithdrawal(
      teamId: string,
      input: { grossAmountXu: number; destinationId: string },
      idempotencyKey: string,
    ) {
      return request<WithdrawalReceipt>(
        `/teams/${encodeURIComponent(teamId)}/withdrawals`,
        {
          body: input,
          headers: { "Idempotency-Key": idempotencyKey },
          method: "POST",
        },
      );
    },
    withdrawals(teamId: string, cursor?: string, limit = 20) {
      const query = new URLSearchParams({ limit: String(limit) });
      if (cursor) query.set("cursor", cursor);
      return request<WithdrawalPage>(
        `/teams/${encodeURIComponent(teamId)}/withdrawals?${query}`,
      );
    },
  });
}

export function createBrowserAdminMonetizationClient({
  baseUrl = "/api/monetization",
  fetchImplementation = fetch,
}: BrowserTeamClientOptions = {}) {
  const client = createStoryApiClient({ baseUrl, fetchImplementation });

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
      await createStoryApiClient({
        baseUrl: "/api/auth",
        fetchImplementation,
      }).request("/refresh", {
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
    approveTopup(
      topupId: string,
      input: { reason: string; evidenceReference: string },
      grantToken: string,
    ) {
      return request<ManualTopupApproval>(
        `/admin/topups/${encodeURIComponent(topupId)}/approve`,
        {
          body: input,
          headers: { "Scoped-Reauthentication": grantToken },
          method: "POST",
        },
      );
    },
    rejectTopup(
      topupId: string,
      input: {
        reasonCode:
          | "AMOUNT_MISMATCH"
          | "REFERENCE_UNVERIFIABLE"
          | "DUPLICATE_PAYMENT"
          | "FRAUD_SUSPECTED"
          | "OTHER";
        reason: string;
        evidenceReference: string;
      },
    ) {
      return request<TopupRejection>(
        `/admin/topups/${encodeURIComponent(topupId)}/reject`,
        { body: input, method: "POST" },
      );
    },
    reviewWithdrawal(
      withdrawalId: string,
      decision: "approve" | "reject",
      reason: string,
      grantToken: string,
      idempotencyKey: string,
    ) {
      return request<WithdrawalDecision>(
        `/admin/withdrawals/${encodeURIComponent(withdrawalId)}/${decision}`,
        {
          body: { reason },
          headers: {
            "Idempotency-Key": idempotencyKey,
            "Scoped-Reauthentication": grantToken,
          },
          method: "POST",
        },
      );
    },
    killSwitches() {
      return request<MonetizationKillSwitch[]>(
        "/admin/configuration/monetization-kill-switches",
      );
    },
  });
}
