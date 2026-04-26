import { api } from './client';
import type {
  CollabProjectResponse,
  ChapterTaskResponse,
  ProjectMemberResponse,
  CommentResponse,
  CreateCollabProjectRequest,
  AssignChapterRequest,
  SubmitChapterRequest,
  ReviewChapterRequest,
  InviteMemberRequest,
  CreateCommentRequest,
  PaginatedList,
} from './types';

export const collabApi = {
  // Projects
  listProjects: (params?: { page?: number; pageSize?: number }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.pageSize) qs.set('pageSize', String(params.pageSize));
    const query = qs.toString();
    return api.get<PaginatedList<CollabProjectResponse>>(`/v1/collab/projects${query ? `?${query}` : ''}`);
  },
  createProject: (data: CreateCollabProjectRequest) =>
    api.post<CollabProjectResponse>('/v1/collab/projects', data),
  getProject: (projectId: number) =>
    api.get<CollabProjectResponse>(`/v1/collab/projects/${projectId}`),
  updateProject: (projectId: number, data: CreateCollabProjectRequest) =>
    api.put<CollabProjectResponse>(`/v1/collab/projects/${projectId}`, data),
  deleteProject: (projectId: number) =>
    api.delete<null>(`/v1/collab/projects/${projectId}`),
  changeProjectStatus: (projectId: number, targetStatus: string) =>
    api.post<null>(`/v1/collab/projects/${projectId}/status?targetStatus=${targetStatus}`),

  // Chapters
  listChapters: (projectId: number, params?: { page?: number; pageSize?: number }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.pageSize) qs.set('pageSize', String(params.pageSize));
    const query = qs.toString();
    return api.get<PaginatedList<ChapterTaskResponse>>(`/v1/collab/projects/${projectId}/chapters${query ? `?${query}` : ''}`);
  },
  createChapter: (projectId: number, chapterNumber: number, title?: string, sourceText?: string) => {
    const qs = new URLSearchParams();
    qs.set('chapterNumber', String(chapterNumber));
    if (title) qs.set('title', title);
    if (sourceText) qs.set('sourceText', sourceText);
    return api.post<ChapterTaskResponse>(`/v1/collab/projects/${projectId}/chapters?${qs}`, {});
  },
  getChapter: (chapterId: number) =>
    api.get<ChapterTaskResponse>(`/v1/collab/chapters/${chapterId}`),
  listMyChapters: (params?: { page?: number; pageSize?: number }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.pageSize) qs.set('pageSize', String(params.pageSize));
    const query = qs.toString();
    return api.get<PaginatedList<ChapterTaskResponse>>(`/v1/collab/chapters/my${query ? `?${query}` : ''}`);
  },

  // Chapter workflow
  assignChapter: (chapterId: number, data: AssignChapterRequest) =>
    api.put<ChapterTaskResponse>(`/v1/collab/chapters/${chapterId}/assign`, data),
  submitChapter: (chapterId: number, data: SubmitChapterRequest) =>
    api.put<ChapterTaskResponse>(`/v1/collab/chapters/${chapterId}/submit`, data),
  reviewChapter: (chapterId: number, data: ReviewChapterRequest) =>
    api.put<ChapterTaskResponse>(`/v1/collab/chapters/${chapterId}/review`, data),

  // Members
  listMembers: (projectId: number, params?: { page?: number; pageSize?: number }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.pageSize) qs.set('pageSize', String(params.pageSize));
    const query = qs.toString();
    return api.get<PaginatedList<ProjectMemberResponse>>(`/v1/collab/projects/${projectId}/members${query ? `?${query}` : ''}`);
  },
  inviteMember: (projectId: number, data: InviteMemberRequest) =>
    api.post<ProjectMemberResponse>(`/v1/collab/projects/${projectId}/invite`, data),
  generateInviteCode: (projectId: number) =>
    api.post<{ code: string; expiresAt: string }>(`/v1/collab/projects/${projectId}/invite-code`),
  joinByCode: (inviteCode: string) =>
    api.post<ProjectMemberResponse>(`/v1/collab/join?inviteCode=${inviteCode}`),
  removeMember: (projectId: number, memberId: number) =>
    api.delete<null>(`/v1/collab/projects/${projectId}/members/${memberId}`),

  // Comments
  listComments: (chapterTaskId: number, page = 1, size = 12) =>
    api.get<PaginatedList<CommentResponse>>(`/v1/collab/chapters/${chapterTaskId}/comments?page=${page}&size=${size}`),
  createComment: (chapterTaskId: number, data: CreateCommentRequest) =>
    api.post<CommentResponse>(`/v1/collab/chapters/${chapterTaskId}/comments`, data),
  resolveComment: (commentId: number) =>
    api.put<null>(`/v1/collab/comments/${commentId}/resolve`),
  deleteComment: (commentId: number) =>
    api.delete<null>(`/v1/collab/comments/${commentId}`),

  // Upload novel to collab project
  uploadNovel: (projectId: number, file: File, sourceLang: string, targetLang: string) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('sourceLang', sourceLang);
    formData.append('targetLang', targetLang);
    formData.append('mode', 'team');
    formData.append('projectId', String(projectId));
    return api.upload<{ projectId: number; chapterCount: number; documentId: number; documentName: string; message: string }>(
      `/user/documents/upload?projectId=${projectId}`,
      formData,
    );
  },
};
