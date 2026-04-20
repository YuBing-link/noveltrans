import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Badge } from '../components/ui/Badge';
import { Modal } from '../components/ui/Modal';
import { Spinner, EmptyState } from '../components/ui/Feedback';
import { useToast } from '../components/ui/Toast';
import { collabApi } from '../api/collab';
import type { CollabProjectResponse, ChapterTaskResponse, ProjectMemberResponse, CommentResponse } from '../api/types';
import { SUPPORTED_LANGUAGES } from '../api/types';
import { Plus, Users, BookOpen, Pencil, Eye, CheckCircle, MessageSquare, ArrowLeft, Send, XCircle } from 'lucide-react';

// ==================== Status Helpers ====================

const STATUS_CONFIG: Record<string, { badge: 'blue' | 'red' | 'green' | 'gray'; label: string }> = {
  pending: { badge: 'gray', label: '待分配' },
  translating: { badge: 'blue', label: '翻译中' },
  reviewing: { badge: 'gray', label: '审核中' },
  completed: { badge: 'green', label: '已完成' },
  rejected: { badge: 'red', label: '已退回' },
};

function StatusBadge({ status }: { status: string }) {
  const config = STATUS_CONFIG[status] ?? { badge: 'gray' as const, label: status };
  return <Badge color={config.badge}>{config.label}</Badge>;
}

function langName(code: string) {
  return SUPPORTED_LANGUAGES.find(l => l.code === code)?.name ?? code;
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-CN');
}

// ==================== ProjectList ====================

function ProjectList() {
  const [projects, setProjects] = useState<CollabProjectResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const fetchProjects = useCallback(async () => {
    setLoading(true);
    try {
      const res = await collabApi.listProjects();
      setProjects(res.data);
    } catch {
      // silent
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchProjects(); }, [fetchProjects]);

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size="lg" />
      </div>
    );
  }

  if (projects.length === 0) {
    return (
      <EmptyState
        icon={<BookOpen className="w-16 h-16 text-text-tertiary mb-4" />}
        title="暂无协作项目"
        description="创建第一个协作项目，邀请团队成员一起翻译"
      />
    );
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      {projects.map(project => (
        <Card
          key={project.id}
          className="cursor-pointer border border-border/50 hover:shadow-card-hover transition-shadow"
          onClick={() => navigate(`/collab/${project.id}`)}
        >
          <div className="p-6">
            <div className="flex items-start justify-between mb-3">
              <h3 className="text-[15px] font-semibold text-text-primary truncate flex-1">{project.name}</h3>
              <StatusBadge status={project.status} />
            </div>
            {project.description && (
              <p className="text-[13px] text-text-tertiary mb-3 line-clamp-2">{project.description}</p>
            )}
            <div className="text-[12px] text-text-tertiary mb-3">
              {langName(project.sourceLang)} → {langName(project.targetLang)}
            </div>
            <div className="flex items-center gap-4 text-[12px] text-text-tertiary">
              <span className="flex items-center gap-1">
                <Users className="w-3.5 h-3.5" /> {project.memberCount} 成员
              </span>
              <span className="flex items-center gap-1">
                <BookOpen className="w-3.5 h-3.5" /> {project.chapterCount} 章节
              </span>
            </div>
            <div className="text-[12px] text-text-tertiary mt-3 pt-3 border-t border-border/50">
              创建者: {project.ownerName} · {formatDate(project.createdAt)}
            </div>
          </div>
        </Card>
      ))}
    </div>
  );
}

// ==================== CreateProjectForm ====================

function CreateProjectForm({ onCancel, onSuccess }: { onCancel: () => void; onSuccess: () => void }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [sourceLang, setSourceLang] = useState('en');
  const [targetLang, setTargetLang] = useState('zh');
  const [loading, setLoading] = useState(false);
  const toast = useToast();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      toast.error('请输入项目名称');
      return;
    }
    setLoading(true);
    try {
      await collabApi.createProject({ name: name.trim(), description: description.trim() || undefined, sourceLang, targetLang });
      toast.success('项目创建成功');
      onSuccess();
    } catch {
      toast.error('项目创建失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card>
      <div className="p-6">
        <h2 className="text-[17px] font-semibold text-text-primary mb-6">创建协作项目</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            label="项目名称"
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="输入项目名称"
            required
          />
          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium text-text-secondary">项目描述</label>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value)}
              placeholder="可选，描述项目目标和范围"
              rows={3}
              className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary placeholder:text-text-tertiary rounded-input border border-transparent focus:border-accent focus:outline-none transition-colors resize-none"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Select label="源语言" value={sourceLang} onChange={e => setSourceLang(e.target.value)}>
              {SUPPORTED_LANGUAGES.filter(l => l.code !== 'auto').map(l => (
                <option key={l.code} value={l.code}>{l.name}</option>
              ))}
            </Select>
            <Select label="目标语言" value={targetLang} onChange={e => setTargetLang(e.target.value)}>
              {SUPPORTED_LANGUAGES.filter(l => l.code !== 'auto').map(l => (
                <option key={l.code} value={l.code}>{l.name}</option>
              ))}
            </Select>
          </div>
          <div className="flex gap-2 justify-end pt-2">
            <Button type="button" variant="secondary" onClick={onCancel}>取消</Button>
            <Button type="submit" loading={loading} disabled={!name.trim()}>创建项目</Button>
          </div>
        </form>
      </div>
    </Card>
  );
}

// ==================== CommentSection ====================

function CommentSection({ chapterTaskId }: { chapterTaskId: number }) {
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [content, setContent] = useState('');
  const [sourceText, setSourceText] = useState('');
  const [targetText, setTargetText] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const toast = useToast();

  const fetchComments = useCallback(async () => {
    try {
      const res = await collabApi.listComments(chapterTaskId);
      setComments(res.data);
    } catch {
      // silent
    }
  }, [chapterTaskId]);

  useEffect(() => { fetchComments(); }, [fetchComments]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) return;
    setSubmitting(true);
    try {
      await collabApi.createComment(chapterTaskId, {
        content: content.trim(),
        sourceText: sourceText.trim(),
        targetText: targetText.trim(),
      });
      setContent('');
      setSourceText('');
      setTargetText('');
      toast.success('评论已添加');
      fetchComments();
    } catch {
      toast.error('评论添加失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mt-6">
      <h4 className="text-[13px] font-semibold text-text-primary mb-3 flex items-center gap-1.5">
        <MessageSquare className="w-4 h-4" /> 评论 ({comments.length})
      </h4>

      <form onSubmit={handleSubmit} className="space-y-2 mb-4 bg-surface-secondary p-4 rounded-card">
        <textarea
          value={content}
          onChange={e => setContent(e.target.value)}
          placeholder="输入评论内容..."
          rows={2}
          className="w-full px-3 py-2 text-[13px] bg-white text-text-primary placeholder:text-text-tertiary rounded-input border border-border focus:border-accent focus:outline-none resize-none"
        />
        <div className="grid grid-cols-2 gap-2">
          <input
            value={sourceText}
            onChange={e => setSourceText(e.target.value)}
            placeholder="引用的原文（可选）"
            className="w-full px-2 py-1.5 text-[12px] bg-white text-text-primary placeholder:text-text-tertiary rounded-input border border-border focus:border-accent focus:outline-none"
          />
          <input
            value={targetText}
            onChange={e => setTargetText(e.target.value)}
            placeholder="引用的译文（可选）"
            className="w-full px-2 py-1.5 text-[12px] bg-white text-text-primary placeholder:text-text-tertiary rounded-input border border-border focus:border-accent focus:outline-none"
          />
        </div>
        <Button type="submit" loading={submitting} disabled={!content.trim()} className="w-fit text-[12px] py-1 px-3">
          发送
        </Button>
      </form>

      <div className="space-y-2">
        {comments.length === 0 && (
          <p className="text-[13px] text-text-tertiary text-center py-4">暂无评论</p>
        )}
        {comments.map(comment => (
          <div key={comment.id} className="border border-border/50 rounded-card p-4 text-[13px]">
            <div className="flex items-center justify-between mb-1">
              <span className="font-medium text-text-primary">{comment.username}</span>
              <span className="text-[12px] text-text-tertiary">{formatDate(comment.createdAt)}</span>
            </div>
            {(comment.sourceText || comment.targetText) && (
              <div className="text-[12px] text-text-tertiary mb-1 space-y-0.5">
                {comment.sourceText && <div className="bg-surface-secondary px-2 py-1 rounded-input">原文: {comment.sourceText}</div>}
                {comment.targetText && <div className="bg-purple-bg px-2 py-1 rounded-input">译文: {comment.targetText}</div>}
              </div>
            )}
            <p className="text-text-secondary">{comment.content}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

// ==================== ChapterEditor ====================

function ChapterEditor({ chapter, onBack }: { chapter: ChapterTaskResponse; onBack: () => void }) {
  const [sourceText, setSourceText] = useState(chapter.sourceText);
  const [translatedText, setTranslatedText] = useState(chapter.translatedText ?? '');
  const [submitting, setSubmitting] = useState(false);
  const toast = useToast();

  const handleSubmit = async () => {
    if (!translatedText.trim()) {
      toast.error('请输入翻译内容');
      return;
    }
    setSubmitting(true);
    try {
      await collabApi.submitChapter(chapter.id, { translatedText: translatedText.trim() });
      toast.success('章节已提交审核');
      onBack();
    } catch {
      toast.error('提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card>
      <div className="p-6">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h3 className="text-[17px] font-semibold text-text-primary">
              第 {chapter.chapterNumber} 章: {chapter.title || '无标题'}
            </h3>
            <StatusBadge status={chapter.status} />
          </div>
          <Button variant="secondary" onClick={onBack}>
            <ArrowLeft className="w-4 h-4" /> 返回
          </Button>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div>
            <label className="text-[13px] font-medium text-text-secondary mb-1.5 block">原文</label>
            <textarea
              value={sourceText}
              onChange={e => setSourceText(e.target.value)}
              readOnly={chapter.status === 'completed'}
              rows={20}
              className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none font-mono resize-y"
            />
          </div>
          <div>
            <label className="text-[13px] font-medium text-text-secondary mb-1.5 block">译文</label>
            <textarea
              value={translatedText}
              onChange={e => setTranslatedText(e.target.value)}
              rows={20}
              className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none font-mono resize-y"
            />
          </div>
        </div>

        {(chapter.status === 'translating' || chapter.status === 'rejected') && (
          <div className="mt-6 flex justify-end">
            <Button loading={submitting} onClick={handleSubmit}>
              <Send className="w-4 h-4" /> 提交翻译
            </Button>
          </div>
        )}

        <CommentSection chapterTaskId={chapter.id} />
      </div>
    </Card>
  );
}

// ==================== InviteMemberModal ====================

function InviteMemberModal({ projectId, open, onClose }: { projectId: number; open: boolean; onClose: () => void }) {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('translator');
  const [loading, setLoading] = useState(false);
  const toast = useToast();

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;
    setLoading(true);
    try {
      await collabApi.inviteMember(projectId, { email: email.trim(), role });
      toast.success('邀请已发送');
      setEmail('');
      onClose();
    } catch {
      toast.error('邀请发送失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="邀请成员">
      <form onSubmit={handleInvite} className="p-6 space-y-4">
        <Input label="邮箱" type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="输入用户邮箱" required />
        <Select label="角色" value={role} onChange={e => setRole(e.target.value)}>
          <option value="translator">翻译者</option>
          <option value="reviewer">审核者</option>
        </Select>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>取消</Button>
          <Button type="submit" loading={loading}>发送邀请</Button>
        </div>
      </form>
    </Modal>
  );
}

// ==================== AssignModal ====================

function AssignModal({ chapter, members, open, onClose, onAssigned }: {
  chapter: ChapterTaskResponse;
  members: ProjectMemberResponse[];
  open: boolean;
  onClose: () => void;
  onAssigned: () => void;
}) {
  const [assigneeId, setAssigneeId] = useState<number | ''>('');
  const [reviewerId, setReviewerId] = useState<number | ''>('');
  const [loading, setLoading] = useState(false);
  const toast = useToast();

  const translators = members.filter(m => m.role === 'translator' || m.role === 'owner');
  const reviewers = members.filter(m => m.role === 'reviewer' || m.role === 'owner');

  const handleAssign = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!assigneeId) {
      toast.error('请选择翻译者');
      return;
    }
    setLoading(true);
    try {
      await collabApi.assignChapter(chapter.id, {
        assigneeId: Number(assigneeId),
        reviewerId: reviewerId ? Number(reviewerId) : undefined,
      });
      toast.success('章节已分配');
      onAssigned();
    } catch {
      toast.error('分配失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={`分配章节: ${chapter.title || `第${chapter.chapterNumber}章`}`}>
      <form onSubmit={handleAssign} className="p-6 space-y-4">
        <Select label="翻译者" value={assigneeId} onChange={e => setAssigneeId(e.target.value ? Number(e.target.value) : '')}>
          <option value="">选择翻译者</option>
          {translators.map(m => (
            <option key={m.id} value={m.userId}>{m.username}</option>
          ))}
        </Select>
        <Select label="审核者（可选）" value={reviewerId} onChange={e => setReviewerId(e.target.value ? Number(e.target.value) : '')}>
          <option value="">选择审核者</option>
          {reviewers.map(m => (
            <option key={m.id} value={m.userId}>{m.username}</option>
          ))}
        </Select>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>取消</Button>
          <Button type="submit" loading={loading}>确认分配</Button>
        </div>
      </form>
    </Modal>
  );
}

// ==================== ReviewModal ====================

function ReviewModal({ chapter, open, onClose, onReviewed }: {
  chapter: ChapterTaskResponse;
  open: boolean;
  onClose: () => void;
  onReviewed: () => void;
}) {
  const [approved, setApproved] = useState(true);
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(false);
  const toast = useToast();

  const handleReview = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await collabApi.reviewChapter(chapter.id, { approved, comment: comment.trim() || undefined });
      toast.success(approved ? '章节已通过审核' : '章节已退回');
      onReviewed();
    } catch {
      toast.error('审核操作失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="审核章节">
      <form onSubmit={handleReview} className="p-6 space-y-4">
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => setApproved(true)}
            className={`flex-1 py-2 px-3 rounded-button text-[13px] font-medium transition-colors ${approved ? 'bg-green text-white' : 'bg-surface-secondary text-text-secondary'}`}
          >
            <CheckCircle className="w-4 h-4 inline mr-1" /> 通过
          </button>
          <button
            type="button"
            onClick={() => setApproved(false)}
            className={`flex-1 py-2 px-3 rounded-button text-[13px] font-medium transition-colors ${!approved ? 'bg-red text-white' : 'bg-surface-secondary text-text-secondary'}`}
          >
            <XCircle className="w-4 h-4 inline mr-1" /> 退回
          </button>
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-[13px] font-medium text-text-secondary">审核意见{!approved ? ' *' : ''}</label>
          <textarea
            value={comment}
            onChange={e => setComment(e.target.value)}
            placeholder={approved ? '可选，填写审核意见' : '请填写退回原因'}
            rows={3}
            required={!approved}
            className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary placeholder:text-text-tertiary rounded-input border border-transparent focus:border-accent focus:bg-white focus:outline-none resize-none"
          />
        </div>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>取消</Button>
          <Button type="submit" loading={loading}>{approved ? '通过' : '退回'}</Button>
        </div>
      </form>
    </Modal>
  );
}

// ==================== ProjectDetail ====================

function ProjectDetail() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const toast = useToast();

  const [project, setProject] = useState<CollabProjectResponse | null>(null);
  const [chapters, setChapters] = useState<ChapterTaskResponse[]>([]);
  const [members, setMembers] = useState<ProjectMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingChapter, setEditingChapter] = useState<ChapterTaskResponse | null>(null);
  const [showInvite, setShowInvite] = useState(false);
  const [assigningChapter, setAssigningChapter] = useState<ChapterTaskResponse | null>(null);
  const [reviewingChapter, setReviewingChapter] = useState<ChapterTaskResponse | null>(null);

  const fetchData = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    try {
      const [projRes, chaptersRes, membersRes] = await Promise.all([
        collabApi.getProject(Number(projectId)),
        collabApi.listChapters(Number(projectId)),
        collabApi.listMembers(Number(projectId)),
      ]);
      setProject(projRes.data);
      setChapters(chaptersRes.data);
      setMembers(membersRes.data);
    } catch {
      toast.error('加载项目数据失败');
    } finally {
      setLoading(false);
    }
  }, [projectId, toast]);

  useEffect(() => { fetchData(); }, [fetchData]);

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size="lg" />
      </div>
    );
  }

  if (!project) {
    return <EmptyState title="项目未找到" description="该项目不存在或已被删除" />;
  }

  // Show chapter editor if editing
  if (editingChapter) {
    return <ChapterEditor chapter={editingChapter} onBack={() => { setEditingChapter(null); fetchData(); }} />;
  }

  return (
    <div className="space-y-6">
      {/* Project Header */}
      <Card>
        <div className="p-6 flex items-start justify-between">
          <div>
            <Button variant="ghost" className="mb-2 -ml-2" onClick={() => navigate('/collab')}>
              <ArrowLeft className="w-4 h-4" /> 返回项目列表
            </Button>
            <h2 className="text-[20px] font-semibold text-text-primary">{project.name}</h2>
            {project.description && <p className="text-[13px] text-text-tertiary mt-1">{project.description}</p>}
            <div className="flex items-center gap-3 mt-3 text-[13px] text-text-tertiary">
              <span>{langName(project.sourceLang)} → {langName(project.targetLang)}</span>
              <StatusBadge status={project.status} />
              <span className="flex items-center gap-1"><Users className="w-3.5 h-3.5" /> {members.length} 成员</span>
            </div>
          </div>
          <div className="flex gap-2 flex-shrink-0">
            <Button variant="secondary" onClick={() => setShowInvite(true)}>
              <Plus className="w-4 h-4" /> 邀请成员
            </Button>
          </div>
        </div>
      </Card>

      {/* Members */}
      <Card>
        <div className="p-6">
          <h3 className="text-[13px] font-semibold text-text-primary mb-4 flex items-center gap-1.5">
            <Users className="w-4 h-4" /> 成员 ({members.length})
          </h3>
          <div className="flex flex-wrap gap-2">
            {members.map(member => (
              <div key={member.id} className="flex items-center gap-2 px-3 py-2 bg-surface-secondary rounded-button text-[13px]">
                <div className="w-6 h-6 rounded-full bg-accent text-white flex items-center justify-center text-xs font-semibold">
                  {member.username?.slice(0, 1).toUpperCase() ?? 'U'}
                </div>
                <span className="text-text-primary">{member.username}</span>
                <span className="text-[12px] text-text-tertiary">({member.role})</span>
              </div>
            ))}
          </div>
        </div>
      </Card>

      {/* Chapter Table */}
      <Card>
        <div className="p-6">
          <h3 className="text-[13px] font-semibold text-text-primary mb-4 flex items-center gap-1.5">
            <BookOpen className="w-4 h-4" /> 章节 ({chapters.length})
          </h3>

          {chapters.length === 0 ? (
            <EmptyState title="暂无章节" description="请添加章节到该项目" />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-[13px]">
                <thead>
                  <tr className="border-b border-border/50">
                    <th className="text-left py-3 px-4 text-text-tertiary font-medium">章节</th>
                    <th className="text-left py-3 px-4 text-text-tertiary font-medium">标题</th>
                    <th className="text-left py-3 px-4 text-text-tertiary font-medium">状态</th>
                    <th className="text-left py-3 px-4 text-text-tertiary font-medium">翻译者</th>
                    <th className="text-left py-3 px-4 text-text-tertiary font-medium">审核者</th>
                    <th className="text-left py-3 px-4 text-text-tertiary font-medium">更新时间</th>
                    <th className="text-right py-3 px-4 text-text-tertiary font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {chapters.map(chapter => (
                    <tr key={chapter.id} className="border-b border-border/50 last:border-0">
                      <td className="py-3 px-4 text-text-primary">第 {chapter.chapterNumber} 章</td>
                      <td className="py-3 px-4 text-text-primary max-w-48 truncate">{chapter.title || '-'}</td>
                      <td className="py-3 px-4"><StatusBadge status={chapter.status} /></td>
                      <td className="py-3 px-4 text-text-tertiary">{chapter.assigneeName || '-'}</td>
                      <td className="py-3 px-4 text-text-tertiary">{chapter.reviewerName || '-'}</td>
                      <td className="py-3 px-4 text-text-tertiary text-[12px]">{formatDate(chapter.updatedAt)}</td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            className="px-2 py-1 text-[12px]"
                            onClick={() => setEditingChapter(chapter)}
                          >
                            <Eye className="w-3.5 h-3.5" /> 查看
                          </Button>
                          {(chapter.status === 'pending') && (
                            <Button
                              variant="ghost"
                              className="px-2 py-1 text-[12px]"
                              onClick={() => setAssigningChapter(chapter)}
                            >
                              <Pencil className="w-3.5 h-3.5" /> 分配
                            </Button>
                          )}
                          {chapter.status === 'reviewing' && (
                            <Button
                              variant="ghost"
                              className="px-2 py-1 text-[12px]"
                              onClick={() => setReviewingChapter(chapter)}
                            >
                              <CheckCircle className="w-3.5 h-3.5" /> 审核
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Card>

      {/* Modals */}
      <InviteMemberModal projectId={project.id} open={showInvite} onClose={() => { setShowInvite(false); fetchData(); }} />

      {assigningChapter && (
        <AssignModal
          chapter={assigningChapter}
          members={members}
          open={!!assigningChapter}
          onClose={() => setAssigningChapter(null)}
          onAssigned={() => { setAssigningChapter(null); fetchData(); }}
        />
      )}

      {reviewingChapter && (
        <ReviewModal
          chapter={reviewingChapter}
          open={!!reviewingChapter}
          onClose={() => setReviewingChapter(null)}
          onReviewed={() => { setReviewingChapter(null); fetchData(); }}
        />
      )}
    </div>
  );
}

// ==================== CollabPage (Main Entry) ====================

export function CollabPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [mode, setMode] = useState<'list' | 'create'>('list');

  // If URL has a projectId, show project detail directly
  if (projectId) {
    return (
      <PageLayout maxWidth="xl" className="py-6">
        <ProjectDetail />
      </PageLayout>
    );
  }

  return (
    <PageLayout maxWidth="xl" className="py-6">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-[28px] sm:text-[32px] font-semibold text-text-primary tracking-display leading-[1.07] mb-2">协作项目</h1>
          <p className="text-[13px] text-text-tertiary mt-1">管理翻译协作项目，邀请团队成员共同翻译</p>
        </div>
        <Button onClick={() => setMode('create')}>
          <Plus className="w-4 h-4" /> 创建项目
        </Button>
      </div>

      {mode === 'create' ? (
        <CreateProjectForm
          onCancel={() => setMode('list')}
          onSuccess={() => { setMode('list'); }}
        />
      ) : (
        <ProjectList />
      )}
    </PageLayout>
  );
}
