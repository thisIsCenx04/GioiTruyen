/**
 * Payload shapes the API returns.
 *
 * The catalog types mirror `CatalogDtos` on the backend field for field, since
 * those drive every reader-facing page. The rest are looser: several of them
 * belong to endpoints this backend does not implement yet (moderation,
 * publishing, admin monetization), so there is no server contract to mirror and
 * the screens that use them cannot work until those controllers exist.
 */

/**
 * A payload with no server contract to pin it to. Deliberately permissive:
 * narrowing it would be inventing a shape rather than describing one.
 */
type Unspecified = Record<string, any>;

/* ------------------------------------------------------------------ catalog */

export type StoryTag = { slug: string; label: string };

export type HomeStorySummary = {
  id: string;
  teamId: string;
  teamName: string | null;
  slug: string;
  title: string;
  coverAssetId: string | null;
  publishedAt: string;
  viewCount: number;
  saveCount: number;
  /** SERIAL or ONESHOT. */
  storyFormat: string;
  /** TEXT, AUDIO, EXCLUSIVE or ORIGINAL. */
  storyType: string;
  originalAuthor?: string | null;
  /** Newest published chapter number; 0 when nothing is published. */
  latestChapterNumber?: number;
  /** ONGOING, COMPLETED or HIATUS - drives the FULL ribbon on a card. */
  progressStatus?: string;
};

export type PublicStory = {
  id: string;
  teamId: string;
  slug: string;
  title: string;
  synopsis: string | null;
  coverAssetId: string | null;
  categoryIds: string[];
  origin: string;
  language: string;
  completionStatus: "ONGOING" | "COMPLETED" | "HIATUS";
  storyFormat: "SERIAL" | "ONESHOT";
  storyType: string;
  tags: StoryTag[];
  publishedAt: string;
  updatedAt: string;
  version: number;
};

export type PublicChapter = {
  id: string;
  storyId: string;
  number: number;
  slug: string;
  title: string;
  publishedAt: string;
  version: number;
  accessType: "FREE" | "PAID";
  coinPrice: number;
  unlocked: boolean;
};

export type ChapterLink = {
  id: string;
  number: number;
  slug: string;
  title: string;
} | null;

export type PublishedChapterDetail = {
  id: string;
  storyId: string;
  number: number;
  slug: string;
  title: string;
  publishedAt: string;
  version: number;
  revisionId: string;
  revisionNo: number;
  /** Empty while the chapter is locked - the text is never sent. */
  contentHtml: string;
  wordCount: number;
  etag: string | null;
  previous: ChapterLink;
  next: ChapterLink;
  accessType: "FREE" | "PAID";
  coinPrice: number;
  unlocked: boolean;
};

/** One page of chapters, carrying what a numbered pager needs to draw itself. */
export type ChapterPage = {
  items: PublicChapter[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

export type CategoryItem = {
  id: string;
  slug: string;
  name: string;
  /** Published stories in this genre, counted server-side and uncapped. */
  storyCount: number;
};
export type CategoryGroup = { group: string; label: string; categories: CategoryItem[] };
export type CategoryTaxonomy = { version: string; groups: CategoryGroup[] };

export type HomeSection = {
  id: string;
  type: string;
  title: string;
  /** Set on tag-driven shelves; the "full"/"new"/"original" pages read it. */
  tag?: string;
  stories: HomeStorySummary[];
};
export type HomeResponse = {
  locale: string;
  version: string;
  generatedAt: string;
  sections: HomeSection[];
};

export type PromotedHomeStory = {
  bookingId: string;
  slotPosition: number;
  tagLabel: string | null;
  story: HomeStorySummary;
};

export type RankingStory = { rank: number; metricValue: number; story: HomeStorySummary };
/** The boards the API publishes; each page keys its icon and colour off this. */
export type RankingBoardId = "gold" | "recommendations" | "views";

export type RankingBoard = {
  id: RankingBoardId;
  title: string;
  subtitle: string | null;
  /** Label for metricValue, e.g. "lượt xem". */
  unit: string;
  stories: RankingStory[];
};

export type SearchHit = { story: HomeStorySummary; score: number; highlights: string[] };
export type SearchResponse = {
  items: SearchHit[];
  nextCursor: string | null;
  hasMore: boolean;
  facets: unknown;
  tookMs: number;
};

export type SuggestionItem = {
  id: string;
  slug: string;
  title: string;
  coverAssetId: string | null;
};
export type SuggestionResponse = {
  items: SuggestionItem[];
  nextCursor: string | null;
  hasMore: boolean;
};

/* ------------------------------------------------------------------- wallet */

/** The reader's two balances, as WalletResponse names them. */
export type WalletBalance = {
  coinBalance: number;
  gemBalance: number;
  updatedAt: string;
} & Unspecified;

/**
 * What a donation may carry. DonationRequest declares exactly these fields and
 * the backend refuses any other property outright, so the shape is closed
 * rather than an open record.
 */
export type DonationInput = {
  /** Xu given, before the platform's share. */
  coinAmount: number;
  /** Ties the gift to one story; the team is named by the path instead. */
  storyId?: string | null;
  message?: string | null;
};

/** DonationResponse: what left the wallet, what the team keeps, what remains. */
export type DonationReceipt = {
  donationId: string;
  teamId: string;
  storyId: string | null;
  grossCoin: number;
  platformFeeCoin: number;
  teamNetCoin: number;
  coinBalance: number;
} & Unspecified;

/** Used as a lookup key for the wallet's status copy, so it is a closed set. */
export type TopupStatus = "AWAITING_PAYMENT" | "PENDING_REVIEW" | "CREDITED" | "REJECTED";

export type TopupRequest = {
  id: string;
  status: TopupStatus;
  /** When the reader's payment window closes. */
  expiresAt: string;
} & Unspecified;

export type WithdrawalState =
  | "PENDING_REVIEW"
  | "APPROVED"
  | "PROCESSING"
  | "PAID"
  | "REJECTED"
  | "FAILED";

export type WithdrawalReceipt = { id: string; state: WithdrawalState } & Unspecified;
export type WithdrawalPage = Unspecified;

/* --------------------------------------------------------------------- team */

export type Team = Unspecified;
export type TeamMembership = { permissions: string[] } & Unspecified;
export type TeamFollow = Unspecified;
export type TeamDashboard = Unspecified;

/** Only the two collections the analytics screen charts are pinned down. */
export type TeamAnalyticsReport = {
  series: Array<{ validViews: number; invalidViews: number } & Unspecified>;
  reasons: Array<{ count: number } & Unspecified>;
} & Unspecified;

/* ---------------------------------------------------------------- community */

export type CommunityComment = Unspecified;
export type CommentTargetType = "STORY" | "CHAPTER" | "COMMENT";
export type CommentPage = {
  items: CommunityComment[];
  nextCursor: string | null;
  hasMore: boolean;
};
export type NotificationItem = Unspecified;
export type NotificationPreference = Unspecified;
export type NotificationPage = {
  items: NotificationItem[];
  nextCursor: string | null;
  hasMore: boolean;
  unreadCount: number;
};
/** What markAllRead reports back: the cutoff it applied and the new count. */
export type NotificationReadWatermark = { readBefore: string; unreadCount: number };
export type PushSubscriptionReceipt = { id: string };
export type ReactionState = { active: boolean; count: number };

/* ------------------------------------------------- moderation and reporting */

/** Lower-case on the wire, which is how the report forms submit them. */
export type ReportReason =
  | "spam"
  | "abuse"
  | "broken_content"
  | "copyright"
  | "illegal_content"
  | "other";

export type ReportRequest = {
  targetType: string;
  targetId: string;
  /** Named after the `report_type` column ReportController writes it to. */
  reportType: ReportReason | string;
  description?: string;
};
export type ReportReceipt = {
  reportId: string;
  status: string;
  message: string;
} & Unspecified;

export type AuthSession = Unspecified;
export type ModerationCase = { id: string } & Unspecified;
export type ModerationDecision = Unspecified;

/** The revision under review, with the automated checks it did or did not pass. */
export type ModerationReview = {
  id: string;
  version: number;
  checks: Array<{ rule: string; code: string; outcome: string } & Unspecified>;
} & Unspecified;

export type ModerationReviewDetail = {
  review: ModerationReview;
  chapters: Array<{ id: string } & Unspecified>;
} & Unspecified;

export type MonetizationKillSwitch = {
  operation: "TOPUP_CREDIT" | "WITHDRAWAL_PAYOUT" | "WITHDRAWAL_REQUEST";
} & Unspecified;

export type PublishingStory = {
  id: string;
  workflowStatus: string;
  categoryIds: string[];
} & Unspecified;
export type PublishingChapter = { id: string } & Unspecified;
export type ReadingSessionGrant = Unspecified;
export type StoryRelation = Unspecified;
