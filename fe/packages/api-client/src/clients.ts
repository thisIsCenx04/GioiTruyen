/**
 * One factory per API area. Each takes a base URL and an optional fetch, so a
 * caller can attach the reader's bearer token without this package knowing how
 * tokens are stored.
 *
 * Signatures were recovered from the call sites in apps/web, which pin argument
 * order exactly; paths and request bodies follow the backend controllers where
 * one exists. Publishing, moderation and admin monetization still have no
 * controller in this backend. Their methods call their intended path and fail
 * like any other missing endpoint: resolving to empty data would dress up a gap
 * as a working screen.
 */
import { createTransport, type ClientOptions, type Transport } from "./http";
import type {
  AuthSession,
  CategoryTaxonomy,
  ChapterPage,
  CommentPage,
  CommentTargetType,
  CommunityComment,
  DonationInput,
  DonationReceipt,
  HomeResponse,
  HomeSection,
  HomeStorySummary,
  NotificationPage,
  NotificationPreference,
  NotificationReadWatermark,
  PromotedHomeStory,
  PublicStory,
  PublishedChapterDetail,
  PushSubscriptionReceipt,
  ModerationCase,
  ModerationReview,
  ModerationReviewDetail,
  MonetizationKillSwitch,
  PublishingChapter,
  PublishingStory,
  RankingBoard,
  ReactionState,
  ReadingSessionGrant,
  ReportReceipt,
  ReportRequest,
  SearchResponse,
  StoryRelation,
  SuggestionResponse,
  Team,
  TopupRequest,
  TeamAnalyticsReport,
  TeamDashboard,
  TeamFollow,
  TeamMembership,
  WalletBalance,
  WithdrawalPage,
  WithdrawalReceipt,
} from "./types";

const encode = encodeURIComponent;

/* ------------------------------------------------------------------ catalog */

export function createPublicCatalogClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    home: () => request<HomeResponse>("/home"),
    /** Home sections on their own, which is how the shelves are rendered. */
    /**
     * The catalog shelves: exclusives, editor picks, recent updates, original
     * writing, audio and finished stories.
     *
     * <p>This used to fetch "/home" and return its sections - the same three
     * shelves the caller had already loaded, fetched a second time. The home
     * page then drew them twice under headings that said the same thing, and
     * the six shelves this endpoint actually serves were never shown at all.
     */
    storySections: () => request<HomeSection[]>("/stories/sections"),
    promotedHome: () => request<PromotedHomeStory[]>("/promotions/home"),
    categories: () => request<CategoryTaxonomy>("/categories"),
    categoryStories: (slug: string) =>
      request<HomeStorySummary[]>(`/categories/${encode(slug)}/stories`),
    tagStories: (slug: string) => request<HomeStorySummary[]>(`/tags/${encode(slug)}/stories`),
    rankingBoards: () => request<RankingBoard[]>("/rankings/boards"),
    story: (identifier: string) => request<PublicStory>(`/stories/${encode(identifier)}`),
    chapters: (identifier: string, page = 1, size = 20) =>
      request<ChapterPage>(`/stories/${encode(identifier)}/chapters`, { query: { page, size } }),
    /** Resolves a chapter from its number, so the reader page never needs the whole list. */
    chapterByNumber: (identifier: string, number: string | number) =>
      request<PublishedChapterDetail>(
        `/stories/${encode(identifier)}/chapters/by-number/${encode(String(number))}`,
      ),
    chapter: (chapterId: string) =>
      request<PublishedChapterDetail>(`/chapters/${encode(chapterId)}`),
    // The keyword travels as `q`, which is the parameter PublicCatalogController
    // binds. Sending `query` left the backend reading its empty default, so the
    // search page and the suggestion box answered as if nothing had been typed.
    search: (query: string, limit?: number) =>
      request<SearchResponse>("/search", { query: { q: query, limit } }),
    suggestions: (query: string, limit?: number) =>
      request<SuggestionResponse>("/search/suggestions", { query: { q: query, limit } }),
  };
}

/* --------------------------------------------------------------------- auth */

/** Callers pass `baseUrl: "/api/v1"`; the endpoints sit at the API root. */
export function createBrowserAuthClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    // The backend rejects unknown JSON properties outright, so a body may carry
    // only the fields its request record declares. Sending anything else - the
    // MFA code below, for instance - fails the whole call with "Nội dung yêu
    // cầu sai cấu trúc" rather than being ignored.
    register: (email: string, password: string, acceptedTerms?: boolean) =>
      request<Record<string, any>>("/register", {
        method: "POST",
        body: { acceptedTerms: acceptedTerms === true, email, password },
      }),
    // LoginRequest has no field for an MFA code, and this backend implements no
    // MFA challenge, so the argument is accepted and not sent.
    login: (email: string, password: string, _mfaCode?: string) =>
      request<Record<string, any>>("/login", { method: "POST", body: { email, password } }),
    refresh: (refreshToken: string) =>
      request<Record<string, any>>("/refresh", { method: "POST", body: { refreshToken } }),
    logout: () => request<void>("/logout", { method: "POST" }),
    verifyEmail: (token: string) =>
      request<Record<string, any>>("/email/verify", { method: "POST", body: { token } }),
    forgotPassword: (email: string) =>
      request<void>("/password/forgot", { method: "POST", body: { email } }),
    resetPassword: (token: string, password: string) =>
      request<void>("/password/reset", { method: "POST", body: { password, token } }),
    reauthenticate: (body: Record<string, any>) =>
      request<Record<string, any>>("/reauth/grants", { method: "POST", body }),
    beginMfa: () => request<Record<string, any>>("/mfa/challenge", { method: "POST" }),
    verifyMfa: (code: string) =>
      request<Record<string, any>>("/mfa/verify", { method: "POST", body: { code } }),
    listSessions: () => request<{ sessions: AuthSession[] }>("/sessions"),
    revokeSession: (sessionId: string) =>
      request<void>(`/sessions/${encode(sessionId)}`, { method: "DELETE" }),
    revokeAllSessions: () => request<void>("/sessions", { method: "DELETE" }),
  };
}

/* ------------------------------------------------------------------- wallet */

export function createBrowserWalletClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    balance: () => request<WalletBalance>("/wallets/me"),
    topupHistory: () => request<TopupRequest[]>("/topups"),
    getTopup: (topupId: string) => request<TopupRequest>(`/topups/${encode(topupId)}`),
    /**
     * Opens a payment against a published package. TopupController takes the
     * package and the method the reader picked, not a free-form amount: the
     * price list is the backend's, so a caller cannot name its own figure.
     */
    createTopup: (packageId: string, methodId: string, idempotencyKey?: string) =>
      request<TopupRequest>("/topups", {
        method: "POST",
        body: { packageId, methodId },
        idempotencyKey,
      }),
    /**
     * The receiving team is part of the path, which is where
     * MonetizationFlowController reads it; only the amount, the optional story
     * and the note travel in the body, and it rejects any other property.
     */
    donate: (teamId: string, body: DonationInput, idempotencyKey?: string) =>
      request<DonationReceipt>(`/teams/${encode(teamId)}/donations`, {
        method: "POST",
        body,
        idempotencyKey,
      }),
    withdrawals: (cursor?: string) =>
      request<WithdrawalPage>("/wallets/me/withdrawals", { query: { cursor } }),
    createWithdrawal: (body: Record<string, any>, idempotencyKey?: string) =>
      request<WithdrawalReceipt>("/wallets/me/withdrawals", {
        method: "POST",
        body,
        idempotencyKey,
      }),
  };
}

/* --------------------------------------------------------------------- team */

export function createBrowserTeamClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    listTeams: () => request<Team[]>("/teams"),
    getTeam: (teamId: string) => request<Team>(`/teams/${encode(teamId)}`),
    updateTeam: (teamId: string, body: Record<string, any>, version?: number) =>
      request<Team>(`/teams/${encode(teamId)}`, { method: "PATCH", body, version }),
    dashboard: (teamId: string) => request<TeamDashboard>(`/teams/${encode(teamId)}/dashboard`),
    analytics: (teamId: string, period?: string) =>
      request<TeamAnalyticsReport>(`/teams/${encode(teamId)}/analytics`, { query: { period } }),
    listMembers: (teamId: string) =>
      request<TeamMembership[]>(`/teams/${encode(teamId)}/members`),
    inviteMember: (teamId: string, body: Record<string, any>, idempotencyKey?: string) =>
      request<TeamMembership>(`/teams/${encode(teamId)}/members`, {
        method: "POST",
        body,
        idempotencyKey,
      }),
    updateMemberPermissions: (
      teamId: string,
      userId: string,
      version: number,
      permissions: readonly unknown[],
    ) =>
      request<TeamMembership>(
        `/teams/${encode(teamId)}/members/${encode(userId)}/permissions`,
        { method: "PATCH", body: { permissions }, version },
      ),
    removeMember: (teamId: string, userId: string, version?: number) =>
      request<void>(`/teams/${encode(teamId)}/members/${encode(userId)}`, {
        method: "DELETE",
        version,
      }),
    follow: (teamId: string) =>
      request<TeamFollow>(`/teams/${encode(teamId)}/follow`, { method: "POST" }),
    unfollow: (teamId: string) =>
      request<TeamFollow>(`/teams/${encode(teamId)}/follow`, { method: "DELETE" }),
    followStatus: (teamId: string) => request<TeamFollow>(`/teams/${encode(teamId)}/follow`),
    myApplication: () => request<Record<string, any> | null>("/teams/applications/me"),
    createApplication: (body: Record<string, any>, idempotencyKey?: string) =>
      request<Record<string, any>>("/teams/applications", {
        method: "POST",
        body,
        idempotencyKey,
      }),
  };
}

/* ---------------------------------------------------------------- community */

export function createBrowserCommentClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    list: (targetType: CommentTargetType | string, targetId: string, cursor?: string) =>
      request<CommentPage>("/comments", { query: { cursor, targetId, targetType } }),
    create: (body: Record<string, any>, idempotencyKey?: string) =>
      request<CommunityComment>("/comments", { method: "POST", body, idempotencyKey }),
    update: (commentId: string, version: number, body: string) =>
      request<CommunityComment>(`/comments/${encode(commentId)}`, {
        method: "PATCH",
        body: { body },
        version,
      }),
    remove: (commentId: string, version?: number) =>
      request<CommunityComment>(`/comments/${encode(commentId)}`, { method: "DELETE", version }),
  };
}

export function createBrowserNotificationClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    list: (cursor?: string) => request<NotificationPage>("/notifications", { query: { cursor } }),
    markRead: (notificationId: string) =>
      request<Record<string, any>>(`/notifications/${encode(notificationId)}/read`, {
        method: "POST",
      }),
    markAllRead: () =>
      request<NotificationReadWatermark>("/notifications/read-all", { method: "POST" }),
    preferences: () => request<NotificationPreference>("/notification-preferences"),
    updatePreferences: (version: number, body: Record<string, any>) =>
      request<NotificationPreference>("/notification-preferences", {
        method: "PATCH",
        body,
        version,
      }),
    registerPush: (body: Record<string, any>) =>
      request<PushSubscriptionReceipt>("/notification-push-subscriptions", {
        method: "POST",
        body,
      }),
    removePush: (subscriptionId: string) =>
      request<void>(`/notification-push-subscriptions/${encode(subscriptionId)}`, {
        method: "DELETE",
      }),
  };
}

/* ------------------------------------------- reactions, reports and reading */

export function createBrowserReactionClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  const path = (targetType: string, targetId: string) =>
    `/reactions/${encode(targetType)}/${encode(targetId)}`;
  return {
    status: (targetType: string, targetId: string) =>
      request<ReactionState>(path(targetType, targetId)),
    add: (targetType: string, targetId: string) =>
      request<ReactionState>(path(targetType, targetId), { method: "POST" }),
    remove: (targetType: string, targetId: string) =>
      request<ReactionState>(path(targetType, targetId), { method: "DELETE" }),
  };
}

export function createBrowserReportClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    create: (body: ReportRequest, idempotencyKey?: string) =>
      request<ReportReceipt>("/reports", { method: "POST", body, idempotencyKey }),
  };
}

export function createBrowserStoryRelationClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  const path = (storyId: string, relation: string) =>
    `/stories/${encode(storyId)}/${encode(relation)}`;
  return {
    status: (storyId: string, relation: string) => request<StoryRelation>(path(storyId, relation)),
    add: (storyId: string, relation: string) =>
      request<StoryRelation>(path(storyId, relation), { method: "POST" }),
    remove: (storyId: string, relation: string) =>
      request<StoryRelation>(path(storyId, relation), { method: "DELETE" }),
    /**
     * Spends gems on a story. The reply carries only what this reader gave and
     * their remaining balance - the story's running total is never public.
     */
    recommend: (storyId: string, gemAmount: number) =>
      request<{ id: string; gemAmount: number; gemBalance: number }>(
        `/stories/${encode(storyId)}/recommend`,
        { method: "POST", body: { gemAmount } },
      ),
    myRecommendation: (storyId: string) =>
      request<{ myGemAmount: number }>(`/stories/${encode(storyId)}/recommend`),
  };
}

export function createBrowserReadingClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    history: (cursor?: string) =>
      request<Record<string, any>>("/me/reading-history", { query: { cursor } }),
    synchronize: (storyId: string, body: Record<string, any>) =>
      request<Record<string, any>>(`/me/reading-progress/${encode(storyId)}`, {
        method: "PUT",
        body,
      }),
  };
}

export function createBrowserReadingSessionClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  /** The grant token authorises the session's own writes, not the reader's. */
  const sessionAuth = (sessionToken: string) => ({ "X-Session-Token": sessionToken });
  return {
    start: (body: Record<string, any>) =>
      request<ReadingSessionGrant>("/reading-sessions", { method: "POST", body }),
    heartbeat: (sessionId: string, sessionToken: string, body: Record<string, any>) =>
      request<{ nextSequence: number }>(`/reading-sessions/${encode(sessionId)}/heartbeats`, {
        method: "POST",
        body,
        headers: sessionAuth(sessionToken),
      }),
    complete: (sessionId: string, sessionToken: string, body: Record<string, any>) =>
      request<Record<string, any>>(`/reading-sessions/${encode(sessionId)}/complete`, {
        method: "POST",
        body,
        headers: sessionAuth(sessionToken),
      }),
  };
}

/**
 * Publishing, moderation and admin monetization have no controller in this
 * backend, so there is no contract to describe. Their arguments are left open
 * rather than invented: the call sites pin the order, and pinning types on top
 * would only look authoritative without being so.
 */
export function createBrowserPublishingClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  const send = (path: string, method: string, body?: unknown) =>
    request<any>(path, { method, body });
  return {
    stories: (teamId?: string, cursor?: string) =>
      request<PublishingStory[]>("/publishing/stories", { query: { cursor, teamId } }),
    createStory: (...args: any[]): Promise<PublishingStory> =>
      send("/publishing/stories", "POST", args.at(-1)),
    updateStory: (...args: any[]): Promise<PublishingStory> =>
      send(`/publishing/stories/${encode(String(args[1]))}`, "PATCH", args.at(-1)),
    deleteStory: (...args: any[]): Promise<void> =>
      send(`/publishing/stories/${encode(String(args[1] ?? args[0]))}`, "DELETE"),
    chapters: (...args: any[]): Promise<PublishingChapter[]> =>
      request<PublishingChapter[]>(
        `/publishing/stories/${encode(String(args[1] ?? args[0]))}/chapters`,
      ),
    createChapter: (...args: any[]): Promise<PublishingChapter> =>
      send(`/publishing/stories/${encode(String(args[1]))}/chapters`, "POST", args.at(-1)),
    updateChapter: (...args: any[]): Promise<PublishingChapter> =>
      send(
        `/publishing/stories/${encode(String(args[1]))}/chapters/${encode(String(args[2]))}`,
        "PATCH",
        args.at(-1),
      ),
    deleteChapter: (...args: any[]) =>
      send(
        `/publishing/stories/${encode(String(args[1]))}/chapters/${encode(String(args[2]))}`,
        "DELETE",
      ),
    submit: (...args: any[]): Promise<PublishingStory> =>
      send(`/publishing/stories/${encode(String(args[1] ?? args[0]))}/submit`, "POST", args.at(-1)),
    schedule: (...args: any[]): Promise<PublishingStory> =>
      send(
        `/publishing/stories/${encode(String(args[1] ?? args[0]))}/schedule`,
        "POST",
        args.at(-1),
      ),
  };
}

export function createBrowserModerationClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    list: (cursor?: string) =>
      request<{ items: ModerationCase[]; nextCursor: string | null }>("/moderation/cases", {
        query: { cursor },
      }),
    detail: (...args: any[]): Promise<ModerationReviewDetail> =>
      request<ModerationReviewDetail>(`/moderation/cases/${encode(String(args[0]))}`),
    claim: (...args: any[]): Promise<ModerationReview> =>
      request<ModerationReview>(`/moderation/cases/${encode(String(args[0]))}/claim`, {
        method: "POST",
        body: args[1],
      }),
    decide: (...args: any[]) =>
      request<any>(`/moderation/cases/${encode(String(args[0]))}/decisions`, {
        method: "POST",
        body: args.at(-1),
      }),
  };
}

export function createBrowserAdminMonetizationClient(options: ClientOptions = {}) {
  const request: Transport = createTransport(options);
  return {
    killSwitches: () => request<MonetizationKillSwitch[]>("/admin/monetization/kill-switches"),
    approveTopup: (...args: any[]) =>
      request<any>(`/admin/topups/${encode(String(args[0]))}/approve`, {
        method: "POST",
        body: args.at(-1),
      }),
    rejectTopup: (...args: any[]) =>
      request<any>(`/admin/topups/${encode(String(args[0]))}/reject`, {
        method: "POST",
        body: args.at(-1),
      }),
    reviewWithdrawal: (...args: any[]) =>
      request<any>(`/admin/finance/withdrawals/${encode(String(args[0]))}`, {
        method: "POST",
        body: args.at(-1),
      }),
  };
}
