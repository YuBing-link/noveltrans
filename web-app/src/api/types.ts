// ==================== API Result Wrapper ====================
export interface ApiResult<T> {
  success: boolean;
  data: T;
  code: string;
  message: string | null;
  token?: string | null;
}

export interface PaginatedList<T> {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  list: T[];
}

/** MyBatis-Plus IPage response wrapper */
export interface PageResult<T> {
  records: T[];
  current: number;
  size: number;
  total: number;
  pages: number;
}

// ==================== Auth ====================
export interface LoginRequest {
  email: string;
  password: string;
  from?: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  code: string;
  username?: string;
}

export interface LoginResponse {
  id: number;
  email: string;
  username: string;
  avatar: string;
  userLevel: 'FREE' | 'PRO' | 'TEAM';
  createTime: string;
}

// ==================== User ====================
export interface UserProfile {
  id: number;
  email: string;
  username: string;
  avatar: string;
  userLevel: string;
  createTime: string;
}

export interface UserStatistics {
  totalTranslations: number;
  textTranslations: number;
  documentTranslations: number;
  totalCharacters: number;
  totalDocuments: number;
  weekTranslations: number;
  monthTranslations: number;
}

export interface UserQuota {
  userLevel: string;
  monthlyChars: number;
  usedThisMonth: number;
  remainingChars: number;
  concurrencyLimit: number;
  fastModeEquivalent: number;
  expertModeEquivalent: number;
  teamModeEquivalent: number;
}

export interface TranslationHistoryItem {
  id: number;
  taskId: string;
  type: string;
  sourceLang: string;
  targetLang: string;
  sourceTextPreview: string;
  targetTextPreview: string;
  createTime: string;
  status?: string;
  filename?: string;
}

// ==================== Translation ====================
export interface TranslateTextRequest {
  text: string;
  sourceLang: string;
  targetLang: string;
  mode?: string;
  engine?: string;
}

export interface TranslateTextResponse {
  translatedText: string;
  detectedLang: string;
  targetLang: string;
  engine: string;
  costTime: number;
}

export interface TaskStatus {
  taskId: string;
  type: string;
  status: string;
  progress: number;
  sourceLang: string;
  targetLang: string;
  createTime: string;
  completedTime: string | null;
  errorMessage: string | null;
  filename?: string;
}

export interface TranslationResult {
  taskId: string;
  content: string;
  sourceLang: string;
  targetLang: string;
}

// ==================== Documents ====================
export interface DocumentItem {
  id: number;
  name: string;
  fileName?: string;
  fileType: string;
  fileSize: number;
  sourceLang: string;
  targetLang: string;
  status: string;
  progress: number;
  createTime: string;
  completedTime: string | null;
  errorMessage: string | null;
  taskId?: string;
}

// ==================== Glossary ====================
export interface GlossaryItem {
  id: number;
  sourceWord: string;
  targetWord: string;
  remark: string;
  createTime: string;
}

// ==================== API Keys ====================
export interface ApiKeyItem {
  id: number;
  name: string;
  apiKey: string;
  active: boolean;
  lastUsedAt: string | null;
  totalUsage: number;
  createdAt: string;
}

// ==================== Preferences ====================
export interface UserPreferences {
  defaultEngine: string;
  defaultTargetLang: string;
  enableGlossary: boolean;
  defaultGlossaryId: number | null;
  enableCache: boolean;
  autoTranslateSelection: boolean;
  fontSize: number;
  themeMode: string;
}

// ==================== Platform ====================
export interface PlatformStats {
  totalUsers: number;
  activeUsersToday: number;
  activeUsersWeek: number;
  activeUsersMonth: number;
  totalTranslations: number;
  translationsToday: number;
  totalCharacters: number;
  totalDocumentTranslations: number;
  totalGlossaries: number;
  systemStatus: string;
}

// ==================== RAG ====================
export interface RagTranslationRequest {
  text: string;
  targetLang: string;
  sourceLang?: string;
  engine?: string;
}
export interface RagTranslationResponse {
  translatedText: string;
  engine: string;
  costTime: number;
  memoryMatches: number;
}

// ==================== External Translation ====================
export interface ExternalTranslateRequest {
  targetLang: string;
  sourceLang?: string;
  text: string;
  engine?: string;
}
export interface ExternalBatchTranslateRequest {
  targetLang: string;
  sourceLang?: string;
  engine?: string;
  texts: string[];
}
export interface ExternalTranslateResponse {
  translatedText: string;
  engine: string;
  costTime: number;
  sourceLang: string;
}

// ==================== Device Auth ====================
export interface DeviceTokenResponse {
  token: string;
  expiresIn: number;
}

// ==================== Supported Languages ====================
export const SUPPORTED_LANGUAGES = [
  { code: 'auto', name: '自动检测' },
  { code: 'zh', name: '中文' },
  { code: 'en', name: '英语' },
  { code: 'ja', name: '日语' },
  { code: 'ko', name: '韩语' },
  { code: 'fr', name: '法语' },
  { code: 'de', name: '德语' },
  { code: 'es', name: '西班牙语' },
  { code: 'ru', name: '俄语' },
  { code: 'pt', name: '葡萄牙语' },
  { code: 'it', name: '意大利语' },
  { code: 'th', name: '泰语' },
  { code: 'vi', name: '越南语' },
];

export const TRANSLATION_ENGINES = [
  { value: 'ai', label: 'AI 翻译 (推荐)' },
  { value: 'neural', label: '神经网络' },
  { value: 'statistical', label: '统计机器' },
];

export const SUPPORTED_FILE_TYPES = ['.txt', '.epub', '.docx', '.pdf'];
export const MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

// ==================== Collaboration ====================
export interface CollabProjectResponse {
  id: number;
  name: string;
  description: string;
  ownerId: number;
  ownerName: string;
  sourceLang: string;
  targetLang: string;
  status: string;
  progress: number;
  memberCount: number;
  totalChapters: number;
  completedChapters: number;
  createTime: string;
  updateTime: string;
}

export interface ChapterTaskResponse {
  id: number;
  chapterNumber: number;
  title: string;
  status: string;
  progress: number;
  assigneeId: number | null;
  assigneeName: string | null;
  reviewerId: number | null;
  reviewerName: string | null;
  reviewComment: string | null;
  sourceWordCount: number;
  targetWordCount: number;
  assignedTime: string | null;
  submittedTime: string | null;
  reviewedTime: string | null;
  completedTime: string | null;
  sourceText: string | null;
  translatedText: string | null;
}

export interface ProjectMemberResponse {
  id: number;
  userId: number;
  username: string;
  email: string;
  avatar: string;
  role: string;
  inviteStatus: string;
  joinedTime: string | null;
}

export interface CommentResponse {
  id: number;
  userId: number;
  username: string;
  avatar: string;
  sourceText: string | null;
  targetText: string | null;
  content: string;
  resolved: boolean;
  createTime: string;
  replies: CommentResponse[];
}

export interface CreateCollabProjectRequest {
  name: string;
  description: string;
  sourceLang: string;
  targetLang: string;
}

export interface AssignChapterRequest {
  assigneeId: number;
  reviewerId?: number;
}

export interface SubmitChapterRequest {
  translatedText: string;
}

export interface ReviewChapterRequest {
  approved: boolean;
  comment?: string;
}

export interface InviteMemberRequest {
  email: string;
  role: 'OWNER' | 'REVIEWER' | 'TRANSLATOR';
}

export interface CreateCommentRequest {
  sourceText?: string;
  targetText?: string;
  content: string;
  parentId?: number;
}

// ==================== Subscription ====================
export interface CheckoutSessionRequest {
  plan: string;
  billingCycle: string;
}

export interface CheckoutSessionResponse {
  checkoutUrl: string | null;
}

export interface SubscriptionStatusResponse {
  plan: string;
  status: string;
  periodEnd: string | null;
  cancelAtPeriodEnd: boolean;
}

export interface PortalSessionResponse {
  portalUrl: string;
}

export interface PaymentVerificationResponse {
  paid: boolean;
  sessionId: string;
  plan: string;
  status: string;
  message: string;
}

export const PLAN_CONFIGS = {
  FREE: {
    name: '免费版',
    price: '¥0',
    features: [
      '每日 100 次翻译',
      '5 并发翻译',
      '月 10,000 字符',
      '基础翻译引擎',
      '术语表管理',
    ],
    cta: '当前方案',
    highlighted: false,
  },
  PRO: {
    name: '专业版',
    price: '¥49/月',
    priceMonthly: '¥49/月',
    priceYearly: '¥39/月',
    yearlyTotal: '¥468/年',
    features: [
      '每日 1,000 次翻译',
      '20 并发翻译',
      '月 50,000 字符',
      'AI 翻译 + 神经网络',
      '翻译记忆库',
      '协作项目 3 个',
      '优先客服',
    ],
    cta: '订阅专业版',
    highlighted: true,
  },
  MAX: {
    name: '旗舰版',
    price: '¥99/月',
    priceMonthly: '¥99/月',
    priceYearly: '¥79/月',
    yearlyTotal: '¥948/年',
    features: [
      '无限翻译次数',
      '50 并发翻译',
      '月 200,000 字符',
      '全部翻译引擎',
      '翻译记忆库',
      '无限协作项目',
      '专属客服',
      'API 访问权限',
    ],
    cta: '订阅旗舰版',
    highlighted: false,
  },
};
