// Enums matching PostgreSQL schema & Backend Domain Enums

export enum UserRole {
    READER = 'READER',
    ADMIN = 'ADMIN'
}

export enum UserStatus {
    ACTIVE = 'ACTIVE',
    BANNED = 'BANNED',
    SUSPENDED = 'SUSPENDED'
}

export enum AuthProvider {
    LOCAL = 'LOCAL',
    GOOGLE = 'GOOGLE'
}

export enum ThemeMode {
    LIGHT = 'LIGHT',
    DARK = 'DARK',
    SYSTEM = 'SYSTEM'
}

export enum TeamStatus {
    ACTIVE = 'ACTIVE',
    SUSPENDED = 'SUSPENDED',
    DISABLED = 'DISABLED'
}

export enum TeamMemberRole {
    OWNER = 'OWNER',
    MANAGER = 'MANAGER',
    EDITOR = 'EDITOR',
    MEMBER = 'MEMBER'
}

export enum TeamMemberStatus {
    ACTIVE = 'ACTIVE',
    REMOVED = 'REMOVED'
}

export enum StoryContentType {
    COMIC = 'COMIC',
    NOVEL = 'NOVEL',
    AUDIO = 'AUDIO'
}

export enum StoryStatus {
    DRAFT = 'DRAFT',
    PENDING_APPROVAL = 'PENDING_APPROVAL',
    PUBLISHED = 'PUBLISHED',
    REJECTED = 'REJECTED',
    HIDDEN = 'HIDDEN'
}

export enum StoryProgressStatus {
    ONGOING = 'ONGOING',
    COMPLETED = 'COMPLETED',
    HIATUS = 'HIATUS',
    CANCELLED = 'CANCELLED'
}

export enum ReviewStatus {
    PUBLISHED = 'PUBLISHED',
    HIDDEN = 'HIDDEN'
}

export enum ChapterAccessType {
    FREE = 'FREE',
    COIN = 'COIN'
}

export enum ChapterStatus {
    DRAFT = 'DRAFT',
    PUBLISHED = 'PUBLISHED',
    SCHEDULED = 'SCHEDULED',
    HIDDEN = 'HIDDEN'
}

export enum GenericContentStatus {
    PUBLISHED = 'PUBLISHED',
    HIDDEN = 'HIDDEN'
}

export enum CurrencyCode {
    VND = 'VND',
    COIN = 'COIN',
    TICKET = 'TICKET'
}

export enum WalletTransactionType {
    TOPUP = 'TOPUP',
    PURCHASE = 'PURCHASE',
    REWARD = 'REWARD',
    DONATION = 'DONATION',
    REFUND = 'REFUND',
    SYSTEM_ADJUST = 'SYSTEM_ADJUST'
}

export enum PaymentMethodType {
    MOMO = 'MOMO',
    VNPAY = 'VNPAY',
    BANK_TRANSFER = 'BANK_TRANSFER',
    MANUAL_ADMIN = 'MANUAL_ADMIN'
}

export enum PaymentStatus {
    PENDING = 'PENDING',
    SUCCESS = 'SUCCESS',
    FAILED = 'FAILED',
    CANCELLED = 'CANCELLED'
}

export enum PurchaseType {
    SINGLE_CHAPTER = 'SINGLE_CHAPTER',
    COMBO = 'COMBO'
}

export enum TeamLedgerType {
    DONATION = 'DONATION',
    CHAPTER_UNLOCK = 'CHAPTER_UNLOCK',
    WITHDRAWAL = 'WITHDRAWAL',
    PLATFORM_FEE = 'PLATFORM_FEE'
}

export enum MissionType {
    DAILY_LOGIN = 'DAILY_LOGIN',
    READ_CHAPTERS = 'READ_CHAPTERS',
    LEAVE_COMMENT = 'LEAVE_COMMENT',
    SHARE_STORY = 'SHARE_STORY'
}

export enum RankingType {
    VIEW = 'VIEW',
    FOLLOW = 'FOLLOW',
    REVENUE = 'REVENUE'
}

export enum RankingPeriod {
    DAILY = 'DAILY',
    WEEKLY = 'WEEKLY',
    MONTHLY = 'MONTHLY',
    ALL_TIME = 'ALL_TIME'
}

export enum ReportTargetType {
    STORY = 'STORY',
    CHAPTER = 'CHAPTER',
    COMMENT = 'COMMENT',
    USER = 'USER'
}

export enum ReportStatus {
    PENDING = 'PENDING',
    RESOLVED = 'RESOLVED',
    DISMISSED = 'DISMISSED'
}

export enum NotificationType {
    SYSTEM = 'SYSTEM',
    NEW_CHAPTER = 'NEW_CHAPTER',
    COMMENT_REPLY = 'COMMENT_REPLY',
    MISSION_REWARD = 'MISSION_REWARD'
}

export enum SiteDocumentType {
    TERMS_OF_SERVICE = 'TERMS_OF_SERVICE',
    PRIVACY_POLICY = 'PRIVACY_POLICY',
    COMMUNITY_GUIDELINES = 'COMMUNITY_GUIDELINES'
}

export enum AdvertisementType {
    BANNER = 'BANNER',
    POPUP = 'POPUP',
    NATIVE = 'NATIVE'
}

export enum AdvertisementPlacement {
    HOME_HEADER = 'HOME_HEADER',
    STORY_DETAIL = 'STORY_DETAIL',
    READER_BOTTOM = 'READER_BOTTOM'
}

export enum AdEventType {
    IMPRESSION = 'IMPRESSION',
    CLICK = 'CLICK'
}

// ----------------------------------------------------------------------------
// Entity Interfaces
// ----------------------------------------------------------------------------

export interface IUser {
    id: string;
    email: string;
    username: string;
    displayName?: string;
    avatarUrl?: string;
    role: UserRole;
    status: UserStatus;
    emailVerifiedAt?: string;
    lastLoginAt?: string;
    createdAt: string;
    updatedAt: string;
}

export interface IAuthAccount {
    id: string;
    userId: string;
    provider: AuthProvider;
    providerAccountId: string;
    createdAt: string;
}

export interface ITeam {
    id: string;
    name: string;
    slug: string;
    description?: string;
    avatarUrl?: string;
    status: TeamStatus;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
}

export interface ITeamMember {
    id: string;
    teamId: string;
    userId: string;
    role: TeamMemberRole;
    status: TeamMemberStatus;
    joinedAt: string;
}

export interface IGenre {
    id: string;
    name: string;
    slug: string;
    description?: string;
}

export interface IStory {
    id: string;
    title: string;
    slug: string;
    authorName?: string;
    summary?: string;
    coverUrl?: string;
    contentType: StoryContentType;
    status: StoryStatus;
    progressStatus: StoryProgressStatus;
    teamId?: string;
    uploaderId: string;
    viewCount: number;
    followCount: number;
    reviewCount: number;
    ratingAvg: number;
    createdAt: string;
    updatedAt: string;
}

export interface IChapter {
    id: string;
    storyId: string;
    chapterNumber: number;
    title?: string;
    slug: string;
    content?: string;
    shortDescription?: string;
    accessType: ChapterAccessType;
    coinPrice: number;
    status: ChapterStatus;
    publishedAt?: string;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
}

export interface IComment {
    id: string;
    targetType: string;
    targetId: string;
    userId: string;
    parentId?: string;
    content: string;
    likeCount: number;
    status: GenericContentStatus;
    createdAt: string;
    updatedAt: string;
}

export interface IWallet {
    id: string;
    userId: string;
    balanceCoin: number;
    createdAt: string;
    updatedAt: string;
}

export interface IWalletTransaction {
    id: string;
    walletId: string;
    type: WalletTransactionType;
    amountCoin: number;
    description?: string;
    referenceId?: string;
    createdAt: string;
}

export interface IDepositPackage {
    id: string;
    name: string;
    priceVnd: number;
    coinAmount: number;
    bonusCoin: number;
    isActive: boolean;
}
