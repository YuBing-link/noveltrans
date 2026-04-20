import { api } from './client';
import type {
  CollabProjectResponse, CreateCollabProjectRequest,
  ChapterTaskResponse, AssignChapterRequest, SubmitChapterRequest, ReviewChapterRequest,
  ProjectMemberResponse, InviteMemberRequest,
  CommentResponse, CreateCommentRequest,
} from './types';

export const collabApi = {
  // Projects
  createProject: (data: CreateCollabProjectRequest) =>
    api.post<CollabProjectResponse>('/v1/collab/projects', data),
  listProjects: () =>
    api.get<CollabProjectResponse[]>('/v1/collab/projects'),
  getProject: (projectId: number) =>
    api.get<CollabProjectResponse>(`/v1/collab/projects/${projectId}`),
  updateProject: (projectId: number, data: CreateCollabProjectRequest) =>
    api.put<CollabProjectResponse>(`/v1/collab/projects/${projectId}`, data),
  changeProjectStatus: (projectId: number, targetStatus: string) =>
    api.post<null>(`/v1/collab/projects/${projectId}/status?targetStatus=${encodeURIComponent(targetStatus)}`),
  createChapter: (projectId: number, chapterNumber: number, title?: string, sourceText?: string) => {
    const qs = new URLSearchParams({ chapterNumber: String(chapterNumber) });
    if (title) qs.set('title', title);
    if (sourceText) qs.set('sourceText', sourceText);
    return api.post<ChapterTaskResponse>(`/v1/collab/projects/${projectId}/chapters?${qs.toString()}`);
  },
  listChapters: (projectId: number) =>
    api.get<ChapterTaskResponse[]>(`/v1/collab/projects/${projectId}/chapters`),

  // Chapter Tasks
  assignChapter: (chapterId: number, data: AssignChapterRequest) =>
    api.put<ChapterTaskResponse>(`/v1/collab/chapters/${chapterId}/assign`, data),
  submitChapter: (chapterId: number, data: SubmitChapterRequest) =>
    api.put<ChapterTaskResponse>(`/v1/collab/chapters/${chapterId}/submit`, data),
  reviewChapter: (chapterId: number, data: ReviewChapterRequest) =>
    api.put<ChapterTaskResponse>(`/v1/collab/chapters/${chapterId}/review`, data),
  getChapter: (chapterId: number) =>
    api.get<ChapterTaskResponse>(`/v1/collab/chapters/${chapterId}`),
  listMyChapters: () =>
    api.get<ChapterTaskResponse[]>('/v1/collab/chapters/my'),

  // Members
  inviteMember: (projectId: number, data: InviteMemberRequest) =>
    api.post<ProjectMemberResponse>(`/v1/collab/projects/${projectId}/invite`, data),
  joinByCode: (inviteCode: string) =>
    api.post<ProjectMemberResponse>(`/v1/collab/join?inviteCode=${encodeURIComponent(inviteCode)}`),
  listMembers: (projectId: number) =>
    api.get<ProjectMemberResponse[]>(`/v1/collab/projects/${projectId}/members`),
  removeMember: (projectId: number, memberId: number) =>
    api.delete<null>(`/v1/collab/projects/${projectId}/members/${memberId}`),

  // Comments
  createComment: (chapterTaskId: number, data: CreateCommentRequest) =>
    api.post<CommentResponse>(`/v1/collab/chapters/${chapterTaskId}/comments`, data),
  listComments: (chapterTaskId: number) =>
    api.get<CommentResponse[]>(`/v1/collab/chapters/${chapterTaskId}/comments`),
  resolveComment: (commentId: number) =>
    api.put<null>(`/v1/collab/comments/${commentId}/resolve`),
  deleteComment: (commentId: number) =>
    api.delete<null>(`/v1/collab/comments/${commentId}`),
};
