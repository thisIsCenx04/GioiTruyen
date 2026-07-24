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
