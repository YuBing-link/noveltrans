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

// ==================== Collab Project ====================
export interface CollabProjectResponse {
  id: number;
  name: string;
  description: string;
  sourceLang: string;
  targetLang: string;
  status: string;
  ownerId: number;
  ownerName: string;
  memberCount: number;
  chapterCount: number;
  inviteCode: string;
  createdAt: string;
  updatedAt: string;
}
export interface CreateCollabProjectRequest {
  name: string;
  description?: string;
  sourceLang: string;
  targetLang: string;
}
export interface ChapterTaskResponse {
  id: number;
  projectId: number;
  chapterNumber: number;
  title: string;
  sourceText: string;
  translatedText: string | null;
  status: string;
  assigneeId: number | null;
  assigneeName: string | null;
  reviewerId: number | null;
  reviewerName: string | null;
  reviewComment: string | null;
  createdAt: string;
  updatedAt: string;
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
export interface ProjectMemberResponse {
  id: number;
  userId: number;
  username: string;
  email: string;
  role: string;
  joinedAt: string;
}
export interface InviteMemberRequest {
  email: string;
  role: string;
}
export interface CommentResponse {
  id: number;
  chapterTaskId: number;
  userId: number;
  username: string;
  sourceText: string;
  targetText: string;
  content: string;
  parentId: number | null;
  resolved: boolean;
  createdAt: string;
}
export interface CreateCommentRequest {
  sourceText: string;
  targetText: string;
  content: string;
  parentId?: number;
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
