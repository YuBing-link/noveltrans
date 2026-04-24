import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../components/ui/Toast';
import { Tabs } from '../components/ui/Tabs';
import type { Tab } from '../components/ui/Tabs';
import { Modal } from '../components/ui/Modal';
import { Badge } from '../components/ui/Badge';
import { collabApi } from '../api/collab';
import type {
  CollabProjectResponse,
  ChapterTaskResponse,
  ProjectMemberResponse,
  CommentResponse,
} from '../api/types';
import { SUPPORTED_LANGUAGES } from '../api/types';
import {
  FolderOpen,
  Plus,
  LogIn,
  BookOpen,
  Users,
  MessageSquare,
  CheckCircle,
  XCircle,
  ArrowLeft,
  ClipboardList,
  UserCheck,
  Search,
  Trash2,
} from 'lucide-react';
import { useAuth } from '../hooks/useAuth';

// ==================== Status helpers ====================
const CHAPTER_STATUS_CONFIG: Record<string, { label: string; color: Parameters<typeof Badge>[0]['color'] }> = {
  UNASSIGNED: { label: '待分配', color: 'gray' },
  TRANSLATING: { label: '翻译中', color: 'blue' },
  SUBMITTED: { label: '已提交', color: 'yellow' },
  REVIEWING: { label: '审核中', color: 'purple' },
  APPROVED: { label: '已批准', color: 'green' },
  REJECTED: { label: '已退回', color: 'red' },
  COMPLETED: { label: '已完成', color: 'green' },
};

const PROJECT_STATUS_CONFIG: Record<string, { label: string; color: Parameters<typeof Badge>[0]['color'] }> = {
  ACTIVE: { label: '进行中', color: 'green' },
  PAUSED: { label: '已暂停', color: 'yellow' },
  COMPLETED: { label: '已完成', color: 'gray' },
  ARCHIVED: { label: '已归档', color: 'gray' },
};

const ROLE_CONFIG: Record<string, { label: string; color: Parameters<typeof Badge>[0]['color'] }> = {
  OWNER: { label: '所有者', color: 'purple' },
  REVIEWER: { label: '审校', color: 'blue' },
  TRANSLATOR: { label: '译者', color: 'gray' },
};

function getLangName(code: string): string {
  return SUPPORTED_LANGUAGES.find(l => l.code === code)?.name || code;
}

// ==================== Main Component ====================
function CollabPage() {
  const navigate = useNavigate();
  const { success, error: toastError } = useToast();
  const { user } = useAuth();

  // View state
  const [activeMainTab, setActiveMainTab] = useState<string>('projects');
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);

  // Project list
  const [projects, setProjects] = useState<CollabProjectResponse[]>([]);
  const [projectsLoading, setProjectsLoading] = useState(false);
  const [projectSearch, setProjectSearch] = useState('');

  // Project detail
  const [chapters, setChapters] = useState<ChapterTaskResponse[]>([]);
  const [chaptersLoading, setChaptersLoading] = useState(false);
  const [members, setMembers] = useState<ProjectMemberResponse[]>([]);
  const [_membersLoading, setMembersLoading] = useState(false);
  const [detailTab, setDetailTab] = useState<string>('chapters');
  const [userRole, setUserRole] = useState<string>('');

  // My tasks (top-level)
  const [myChapters, setMyChapters] = useState<ChapterTaskResponse[]>([]);
  const [myChaptersLoading, setMyChaptersLoading] = useState(false);
  const selectedProject = projects.find(p => p.id === selectedProjectId);

  // Modal states
  const [createProjectOpen, setCreateProjectOpen] = useState(false);
  const [joinProjectOpen, setJoinProjectOpen] = useState(false);
  const [joinMode, setJoinMode] = useState<'input' | 'generate'>('input');
  const [generatedCode, setGeneratedCode] = useState('');
  const [generatedExpiry, setGeneratedExpiry] = useState('');
  const [generating, setGenerating] = useState(false);
  const [addChapterOpen, setAddChapterOpen] = useState(false);
  const [uploadNovelOpen, setUploadNovelOpen] = useState(false);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [assignOpen, setAssignOpen] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteCodeModalOpen, setInviteCodeModalOpen] = useState(false);
  const [commentsOpen, setCommentsOpen] = useState(false);

  // Modal form states
  const [selectedChapter, setSelectedChapter] = useState<ChapterTaskResponse | null>(null);
  const [inviteCode, setInviteCode] = useState('');
  const [newChapterNum, setNewChapterNum] = useState(1);
  const [newChapterTitle, setNewChapterTitle] = useState('');
  const [newChapterSource, setNewChapterSource] = useState('');
  const [assignTargetId, setAssignTargetId] = useState<number>(0);
  const [assignReviewerId, setAssignReviewerId] = useState<number>(0);
  const [reviewApproved, setReviewApproved] = useState(true);
  const [reviewComment, setReviewComment] = useState('');
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<'REVIEWER' | 'TRANSLATOR'>('TRANSLATOR');
  const [commentContent, setCommentContent] = useState('');
  const [comments, setComments] = useState<CommentResponse[]>([]);

  // Create project form
  const [cpName, setCpName] = useState('');
  const [cpDesc, setCpDesc] = useState('');
  const [cpSourceLang, setCpSourceLang] = useState('ja');
  const [cpTargetLang, setCpTargetLang] = useState('zh');

  // ==================== Data loading ====================
  const loadProjects = async () => {
    setProjectsLoading(true);
    try {
      const res = await collabApi.listProjects();
      setProjects(res.data);
    } catch {
      // API not ready yet, show empty state
    } finally {
      setProjectsLoading(false);
    }
  };

  const loadChapters = async (projectId: number) => {
    setChaptersLoading(true);
    try {
      const res = await collabApi.listChapters(projectId);
      setChapters(res.data);
    } catch {
      toastError('加载章节失败');
    } finally {
      setChaptersLoading(false);
    }
  };

  const loadMembers = async (projectId: number) => {
    setMembersLoading(true);
    try {
      const res = await collabApi.listMembers(projectId);
      setMembers(res.data);
      if (user?.id) {
        const me = res.data.find(m => m.userId === user.id);
        setUserRole(me?.role || '');
      }
    } catch {
      toastError('加载成员失败');
    } finally {
      setMembersLoading(false);
    }
  };

  const loadMyChapters = async () => {
    setMyChaptersLoading(true);
    try {
      const res = await collabApi.listMyChapters();
      setMyChapters(res.data);
    } catch {
      // API not ready yet
    } finally {
      setMyChaptersLoading(false);
    }
  };

  const loadComments = async (chapterId: number) => {
    try {
      const res = await collabApi.listComments(chapterId);
      setComments(res.data);
    } catch {
      toastError('加载评论失败');
    }
  };

  useEffect(() => { loadProjects(); }, []);
  useEffect(() => {
    if (selectedProjectId) {
      loadChapters(selectedProjectId);
      loadMembers(selectedProjectId);
    }
  }, [selectedProjectId]);
  useEffect(() => {
    if (activeMainTab === 'my-tasks') loadMyChapters();
  }, [activeMainTab]);

  // ==================== Handlers ====================
  const handleSelectProject = (id: number) => {
    setSelectedProjectId(id);
    setDetailTab('chapters');
  };

  const handleBackToProjects = () => {
    setSelectedProjectId(null);
  };

  const handleCreateProject = async () => {
    if (!cpName.trim()) { toastError('项目名称不能为空'); return; }
    if (cpSourceLang === cpTargetLang) { toastError('源语言和目标语言不能相同'); return; }
    try {
      await collabApi.createProject({
        name: cpName,
        description: cpDesc,
        sourceLang: cpSourceLang,
        targetLang: cpTargetLang,
      });
      success('项目创建成功');
      setCreateProjectOpen(false);
      setCpName(''); setCpDesc(''); setCpSourceLang('ja'); setCpTargetLang('zh');
      loadProjects();
    } catch (e) {
      toastError(e instanceof Error ? e.message : '创建失败');
    }
  };

  const handleJoinProject = async () => {
    if (!inviteCode.trim()) { toastError('请输入邀请码'); return; }
    try {
      await collabApi.joinByCode(inviteCode);
      success('加入成功');
      setJoinProjectOpen(false);
      setInviteCode('');
      loadProjects();
    } catch (e) {
      toastError(e instanceof Error ? e.message : '加入失败');
    }
  };

  const handleGenerateInvite = async () => {
    if (!selectedProjectId) { toastError('请先选择一个项目'); return; }
    setGenerating(true);
    try {
      const res = await collabApi.generateInviteCode(selectedProjectId);
      setGeneratedCode(res.data.code);
      setGeneratedExpiry(res.data.expiresAt);
      setInviteCodeModalOpen(true);
    } catch (e) {
      toastError(e instanceof Error ? e.message : '生成失败');
    } finally {
      setGenerating(false);
    }
  };

  const handleAddChapter = async () => {
    if (!selectedProjectId) return;
    try {
      await collabApi.createChapter(selectedProjectId, newChapterNum, newChapterTitle || undefined, newChapterSource || undefined);
      success('章节添加成功');
      setAddChapterOpen(false);
      setNewChapterTitle(''); setNewChapterSource('');
      loadChapters(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : '添加失败');
    }
  };

  const handleUploadNovel = async () => {
    if (!selectedProjectId) return;
    if (!uploadFile) { toastError('请选择文件'); return; }
    const project = projects.find(p => p.id === selectedProjectId);
    if (!project) return;
    setUploading(true);
    setUploadProgress(10);

    // Simulate progress while uploading
    const timer = setInterval(() => {
      setUploadProgress(prev => Math.min(prev + 5, 90));
    }, 300);

    try {
      const res = await collabApi.uploadNovel(selectedProjectId, uploadFile, project.sourceLang, project.targetLang);
      clearInterval(timer);
      setUploadProgress(100);
      success(res.data.message || `上传成功，已创建 ${res.data.chapterCount} 个章节`);
      setTimeout(() => {
        setUploadNovelOpen(false);
        setUploadFile(null);
        setUploadProgress(0);
      }, 500);
      // 刷新章节列表
      loadChapters(selectedProjectId);
    } catch (e) {
      clearInterval(timer);
      toastError(e instanceof Error ? e.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handleAssign = async () => {
    if (!selectedChapter) return;
    try {
      await collabApi.assignChapter(selectedChapter.id, {
        assigneeId: assignTargetId,
        reviewerId: assignReviewerId || undefined,
      });
      success('分配成功');
      setAssignOpen(false);
      if (selectedProjectId) loadChapters(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : '分配失败');
    }
  };

  const handleReview = async () => {
    if (!selectedChapter) return;
    try {
      await collabApi.reviewChapter(selectedChapter.id, {
        approved: reviewApproved,
        comment: reviewComment || undefined,
      });
      success(reviewApproved ? '审核通过' : '已退回');
      setReviewOpen(false);
      setReviewComment('');
      if (selectedProjectId) loadChapters(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : '审核失败');
    }
  };

  const handleInvite = async () => {
    if (!inviteEmail.trim()) { toastError('邮箱不能为空'); return; }
    if (!selectedProjectId) return;
    try {
      await collabApi.inviteMember(selectedProjectId, { email: inviteEmail, role: inviteRole });
      success('邀请已发送');
      setInviteOpen(false);
      setInviteEmail('');
      loadMembers(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : '邀请失败');
    }
  };

  const handleRemoveMember = async (memberId: number) => {
    if (!selectedProjectId) return;
    if (!confirm('确定要移除该成员吗？')) return;
    try {
      await collabApi.removeMember(selectedProjectId, memberId);
      success('成员已移除');
      loadMembers(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : '移除失败');
    }
  };

  const handleOpenComments = (chapter: ChapterTaskResponse) => {
    setSelectedChapter(chapter);
    setCommentsOpen(true);
    loadComments(chapter.id);
  };

  const handleSendComment = async () => {
    if (!selectedChapter || !commentContent.trim()) return;
    try {
      await collabApi.createComment(selectedChapter.id, { content: commentContent });
      setCommentContent('');
      loadComments(selectedChapter.id);
    } catch (e) {
      toastError(e instanceof Error ? e.message : '评论失败');
    }
  };

  const openReviewModal = (chapter: ChapterTaskResponse) => {
    setSelectedChapter(chapter);
    setReviewApproved(true);
    setReviewComment('');
    setReviewOpen(true);
  };

  const openAssignModal = (chapter: ChapterTaskResponse) => {
    setSelectedChapter(chapter);
    setAssignTargetId(0);
    setAssignReviewerId(0);
    setAssignOpen(true);
  };

  // ==================== Filtered data ====================
  const filteredProjects = projects.filter(p =>
    !projectSearch || p.name.toLowerCase().includes(projectSearch.toLowerCase())
  );

  const myProjectTasks = chapters.filter(c =>
    c.assigneeId === user?.id || c.reviewerId === user?.id
  );

  // ==================== Render helpers ====================
  const renderStatusBadge = (status: string, type: 'chapter' | 'project' | 'role') => {
    const config = type === 'chapter' ? CHAPTER_STATUS_CONFIG
      : type === 'project' ? PROJECT_STATUS_CONFIG
      : ROLE_CONFIG;
    const { label, color } = config[status] || { label: status, color: 'gray' as const };
    return <Badge color={color}>{label}</Badge>;
  };

  const renderTranslatorOptions = () => {
    const translators = members.filter(m => m.role === 'TRANSLATOR' || m.role === 'OWNER');
    if (translators.length === 0) return <p className="text-sm text-text-tertiary">暂无可分配的译者</p>;
    return (
      <select
        value={assignTargetId}
        onChange={e => setAssignTargetId(Number(e.target.value))}
        className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
      >
        <option value={0}>选择译者</option>
        {translators.map(m => (
          <option key={m.userId} value={m.userId}>{m.username}</option>
        ))}
      </select>
    );
  };

  const renderReviewerOptions = () => {
    const reviewers = members.filter(m => m.role === 'REVIEWER' || m.role === 'OWNER');
    return (
      <select
        value={assignReviewerId}
        onChange={e => setAssignReviewerId(Number(e.target.value))}
        className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
      >
        <option value={0}>选择审者（可选）</option>
        {reviewers.map(m => (
          <option key={m.userId} value={m.userId}>{m.username}</option>
        ))}
      </select>
    );
  };

  // ==================== Main render ====================
  const mainTabs: Tab[] = [
    { key: 'projects', label: '我的项目' },
    { key: 'my-tasks', label: '我的任务' },
  ];

  return (
    <div className="py-8">
      {/* Page Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-text-primary">协作翻译</h1>
        <p className="text-text-tertiary mt-1">团队协作文档，管理章节翻译任务</p>
      </div>

      {/* Main Tabs */}
      <div className="mb-6">
        <Tabs tabs={mainTabs} activeTab={activeMainTab} onChange={setActiveMainTab} />
      </div>

      {/* ==================== Tab: 我的项目 ==================== */}
      {activeMainTab === 'projects' && !selectedProjectId && (
        <div className="bg-surface-secondary rounded-card border border-border">
          {/* Toolbar */}
          <div className="flex items-center justify-between p-4 border-b border-border">
            <div className="relative flex-1 max-w-xs">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-tertiary" />
              <input
                type="text"
                placeholder="搜索项目..."
                value={projectSearch}
                onChange={e => setProjectSearch(e.target.value)}
                style={{ paddingLeft: '3rem' }}
                className="w-full pr-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              />
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setJoinProjectOpen(true)}
                className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
              >
                <LogIn className="w-3.5 h-3.5" /> 加入项目
              </button>
              <button
                onClick={() => setCreateProjectOpen(true)}
                className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
              >
                <Plus className="w-3.5 h-3.5" /> 创建项目
              </button>
            </div>
          </div>

          {/* Project Grid */}
          {projectsLoading ? (
            <div className="p-12 text-center text-text-tertiary">加载中...</div>
          ) : filteredProjects.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <FolderOpen className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">暂无项目</p>
              <p className="text-text-tertiary text-sm mt-1">创建一个新项目或加入已有项目</p>
            </div>
          ) : (
            <div className="p-6 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {filteredProjects.map(project => (
                <div
                  key={project.id}
                  onClick={() => handleSelectProject(project.id)}
                  className="border border-border rounded-xl p-5 bg-white hover:border-accent/50 hover:shadow-subtle transition-all cursor-pointer"
                >
                  <div className="flex items-start justify-between gap-2 mb-3">
                    <h3 className="text-[15px] font-semibold text-text-primary truncate">{project.name}</h3>
                    {renderStatusBadge(project.status, 'project')}
                  </div>
                  <p className="text-[13px] text-text-tertiary line-clamp-2 mb-4">
                    {project.description || '暂无描述'}
                  </p>
                  <div className="flex items-center gap-2 text-[12px] text-text-tertiary mb-3">
                    <span>{getLangName(project.sourceLang)} → {getLangName(project.targetLang)}</span>
                  </div>
                  {/* Progress */}
                  <div className="mb-3">
                    <div className="flex items-center justify-between text-[11px] text-text-tertiary mb-1">
                      <span>进度</span>
                      <span className="font-mono">{project.progress || 0}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-surface-secondary rounded-full overflow-hidden">
                      <div
                        className="h-full bg-accent transition-all duration-300"
                        style={{ width: `${project.progress || 0}%` }}
                      />
                    </div>
                  </div>
                  <div className="flex items-center justify-between text-[12px] text-text-tertiary">
                    <span>{project.memberCount || 1} 成员 · {project.completedChapters || 0}/{project.totalChapters || 0} 章节</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ==================== Project Detail ==================== */}
      {activeMainTab === 'projects' && selectedProjectId && (
        <ProjectDetailView
          projectId={selectedProjectId}
          projects={projects}
          chapters={chapters}
          chaptersLoading={chaptersLoading}
          members={members}
          userRole={userRole}
          detailTab={detailTab}
          setDetailTab={setDetailTab}
          myProjectTasks={myProjectTasks}
          onBack={handleBackToProjects}
          onAddChapter={() => {
            const maxNum = chapters.reduce((max, c) => Math.max(max, c.chapterNumber), 0);
            setNewChapterNum(maxNum + 1);
            setAddChapterOpen(true);
          }}
          onUploadNovel={() => { setUploadFile(null); setUploadProgress(0); setUploadNovelOpen(true); }}
          onAssign={openAssignModal}
          onReview={openReviewModal}
          onComments={handleOpenComments}
          onInvite={() => { setInviteOpen(true); setJoinMode('input'); }}
          onGenerateInvite={handleGenerateInvite}
          onRemoveMember={handleRemoveMember}
        />
      )}

      {/* ==================== Tab: 我的任务 ==================== */}
      {activeMainTab === 'my-tasks' && (
        <div className="bg-surface-secondary rounded-card border border-border">
          <div className="p-4 border-b border-border">
            <h2 className="text-base font-semibold text-text-primary">我的翻译与审校任务</h2>
          </div>
          {myChaptersLoading ? (
            <div className="p-12 text-center text-text-tertiary">加载中...</div>
          ) : myChapters.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <ClipboardList className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">暂无任务</p>
              <p className="text-text-tertiary text-sm mt-1">等待项目所有者分配章节任务</p>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {myChapters.map(chapter => (
                <div key={chapter.id} className="p-4 flex items-center justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <BookOpen className="w-4 h-4 text-text-tertiary" />
                      <span className="text-sm font-medium text-text-primary truncate">
                        第{chapter.chapterNumber}章 {chapter.title}
                      </span>
                      {renderStatusBadge(chapter.status, 'chapter')}
                    </div>
                    <p className="text-xs text-text-tertiary">
                      {chapter.assigneeName === user?.username ? '译者' : '审者'} · {chapter.progress || 0}% 完成
                    </p>
                  </div>
                  <div className="flex items-center gap-2 ml-4 flex-shrink-0">
                    {chapter.status === 'TRANSLATING' && chapter.assigneeId === user?.id && (
                      <button
                        onClick={() => navigate(`/collab/workspace?chapterId=${chapter.id}&targetLang=${selectedProject?.targetLang}`)}
                        className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
                      >
                        <BookOpen className="w-3 h-3" /> 翻译
                      </button>
                    )}
                    {chapter.status !== 'TRANSLATING' && (
                      <button
                        onClick={() => navigate(`/collab/workspace?chapterId=${chapter.id}&targetLang=${selectedProject?.targetLang}`)}
                        className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
                      >
                        <BookOpen className="w-3 h-3" /> 工作台
                      </button>
                    )}
                    {(chapter.status === 'SUBMITTED' || chapter.status === 'REVIEWING') && chapter.reviewerId === user?.id && (
                      <button
                        onClick={() => openReviewModal(chapter)}
                        className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-white bg-green rounded-button hover:bg-green-hover transition-button"
                      >
                        <CheckCircle className="w-3 h-3" /> 审核
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ==================== Modals ==================== */}
      {/* Create Project */}
      <Modal open={createProjectOpen} onClose={() => setCreateProjectOpen(false)} title="创建项目" size="md">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">项目名称</label>
            <input
              value={cpName}
              onChange={e => setCpName(e.target.value)}
              placeholder="输入项目名称"
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">描述</label>
            <textarea
              value={cpDesc}
              onChange={e => setCpDesc(e.target.value)}
              placeholder="项目描述（可选）"
              rows={3}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50 resize-none"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">源语言</label>
              <select
                value={cpSourceLang}
                onChange={e => setCpSourceLang(e.target.value)}
                className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              >
                {SUPPORTED_LANGUAGES.filter(l => l.code !== 'auto').map(l => (
                  <option key={l.code} value={l.code}>{l.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">目标语言</label>
              <select
                value={cpTargetLang}
                onChange={e => setCpTargetLang(e.target.value)}
                className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              >
                {SUPPORTED_LANGUAGES.filter(l => l.code !== 'auto').map(l => (
                  <option key={l.code} value={l.code}>{l.name}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setCreateProjectOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              取消
            </button>
            <button
              onClick={handleCreateProject}
              className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
            >
              创建
            </button>
          </div>
        </div>
      </Modal>

      {/* Join Project */}
      <Modal open={joinProjectOpen} onClose={() => { setJoinProjectOpen(false); setJoinMode('input'); setGeneratedCode(''); setGeneratedExpiry(''); }} title="加入项目" size="sm">
        <div className="space-y-4">
          {/* Mode switch */}
          <div className="flex gap-2">
            <button
              onClick={() => setJoinMode('input')}
              className={`flex-1 py-2 text-sm font-medium rounded-lg border transition-colors ${
                joinMode === 'input'
                  ? 'border-accent bg-accent/5 text-accent'
                  : 'border-border text-text-tertiary hover:text-text-primary'
              }`}
            >
              输入邀请码
            </button>
            <button
              onClick={() => setJoinMode('generate')}
              className={`flex-1 py-2 text-sm font-medium rounded-lg border transition-colors ${
                joinMode === 'generate'
                  ? 'border-accent bg-accent/5 text-accent'
                  : 'border-border text-text-tertiary hover:text-text-primary'
              }`}
            >
              生成邀请码
            </button>
          </div>

          {joinMode === 'input' ? (
            <>
              <div>
                <label className="block text-sm font-medium text-text-primary mb-1">邀请码</label>
                <input
                  value={inviteCode}
                  onChange={e => setInviteCode(e.target.value)}
                  placeholder="输入他人分享的邀请码"
                  className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  onClick={() => setJoinProjectOpen(false)}
                  className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
                >
                  取消
                </button>
                <button
                  onClick={handleJoinProject}
                  className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
                >
                  加入
                </button>
              </div>
            </>
          ) : (
            <>
              <div className="text-xs text-text-tertiary bg-surface-secondary rounded-lg p-3 border border-border">
                <p className="font-medium text-text-secondary mb-1">邀请码有效期 3 天</p>
                <p>生成后可分享给他人，有效期为生成时间起 72 小时，过期后需重新生成。</p>
              </div>
              <button
                onClick={handleGenerateInvite}
                disabled={generating}
                className="w-full py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button disabled:opacity-50"
              >
                {generating ? '生成中...' : '生成邀请码'}
              </button>
              {generatedCode && (
                <div className="rounded-lg border-2 border-dashed border-accent/40 bg-accent-bg p-4 text-center">
                  <p className="text-xs text-text-tertiary mb-2">邀请码（点击复制）</p>
                  <p
                    className="text-2xl font-mono font-bold text-accent cursor-pointer tracking-widest"
                    onClick={async () => {
                      try { await navigator.clipboard.writeText(generatedCode); success('已复制'); }
                      catch { toastError('复制失败'); }
                    }}
                  >
                    {generatedCode}
                  </p>
                  {generatedExpiry && (
                    <p className="text-xs text-text-tertiary mt-2">
                      有效期至 {new Date(generatedExpiry).toLocaleString('zh-CN')}
                    </p>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </Modal>

      {/* Invite Code Generator */}
      <Modal open={inviteCodeModalOpen} onClose={() => setInviteCodeModalOpen(false)} title="邀请码" size="sm">
        <div className="space-y-4">
          <div className="text-xs text-text-tertiary bg-surface-secondary rounded-lg p-3 border border-border">
            <p className="font-medium text-text-secondary mb-1">邀请码有效期 3 天</p>
            <p>生成后可分享给他人，有效期为生成时间起 72 小时，过期后需重新生成。</p>
          </div>
          <button
            onClick={() => { setGeneratedCode(''); setGeneratedExpiry(''); handleGenerateInvite(); }}
            disabled={generating}
            className="w-full py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button disabled:opacity-50"
          >
            {generating ? '生成中...' : '生成新邀请码'}
          </button>
          {generatedCode && (
            <div className="rounded-lg border-2 border-dashed border-accent/40 bg-accent-bg p-4 text-center">
              <p className="text-xs text-text-tertiary mb-2">邀请码（点击复制）</p>
              <p
                className="text-2xl font-mono font-bold text-accent cursor-pointer tracking-widest"
                onClick={async () => {
                  try { await navigator.clipboard.writeText(generatedCode); success('已复制'); }
                  catch { toastError('复制失败'); }
                }}
              >
                {generatedCode}
              </p>
              {generatedExpiry && (
                <p className="text-xs text-text-tertiary mt-2">
                  有效期至 {new Date(generatedExpiry).toLocaleString('zh-CN')}
                </p>
              )}
            </div>
          )}
        </div>
      </Modal>

      {/* Add Chapter */}
      <Modal open={addChapterOpen} onClose={() => setAddChapterOpen(false)} title="添加章节" size="lg">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">章节号</label>
              <input
                type="number"
                value={newChapterNum}
                onChange={e => setNewChapterNum(Number(e.target.value))}
                className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">章节标题</label>
              <input
                value={newChapterTitle}
                onChange={e => setNewChapterTitle(e.target.value)}
                placeholder="可选"
                className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">原文内容（可选）</label>
            <textarea
              value={newChapterSource}
              onChange={e => setNewChapterSource(e.target.value)}
              placeholder="粘贴章节原文，或稍后上传"
              rows={6}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50 resize-none font-mono"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setAddChapterOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              取消
            </button>
            <button
              onClick={handleAddChapter}
              className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
            >
              添加
            </button>
          </div>
        </div>
      </Modal>

      {/* Upload Novel */}
      <Modal open={uploadNovelOpen} onClose={() => { setUploadNovelOpen(false); setUploadFile(null); setUploadProgress(0); }} title="上传小说" size="md">
        <div className="space-y-4">
          <div className="text-xs text-text-tertiary bg-surface-secondary rounded-lg p-3 border border-border">
            <p className="font-medium text-text-secondary mb-1">团队协作翻译</p>
            <p>上传小说后系统将自动拆分章节，创建翻译任务并分配到项目中。支持 .txt、.epub、.docx 格式文件。</p>
          </div>

          {/* File drop zone */}
          <label className="flex flex-col items-center justify-center border-2 border-dashed border-border rounded-xl p-8 cursor-pointer hover:border-accent/50 hover:bg-accent-bg/30 transition-colors">
            {uploadFile ? (
              <div className="text-center">
                <p className="text-sm font-medium text-text-primary">{uploadFile.name}</p>
                <p className="text-xs text-text-tertiary mt-1">{(uploadFile.size / 1024).toFixed(1)} KB</p>
                <p className="text-xs text-accent mt-2">点击更换文件</p>
              </div>
            ) : (
              <div className="text-center">
                <svg className="w-10 h-10 text-text-tertiary mx-auto mb-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="17 8 12 3 7 8"/>
                  <line x1="12" y1="3" x2="12" y2="15"/>
                </svg>
                <p className="text-sm font-medium text-text-secondary">点击或拖拽上传小说</p>
                <p className="text-xs text-text-tertiary mt-1">支持 .txt .epub .docx 文件</p>
              </div>
            )}
            <input
              type="file"
              accept=".txt,.epub,.docx"
              onChange={e => {
                const f = e.target.files?.[0];
                if (f) setUploadFile(f);
              }}
              className="hidden"
            />
          </label>

          {/* Upload button */}
          <button
            onClick={handleUploadNovel}
            disabled={!uploadFile || uploading}
            className="w-full py-2.5 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {uploading ? '上传中...' : '上传并自动拆分章节'}
          </button>

          {/* Progress */}
          {uploading && (
            <div className="space-y-1">
              <div className="flex items-center justify-between text-xs text-text-tertiary">
                <span>上传进度</span>
                <span className="font-mono">{uploadProgress}%</span>
              </div>
              <div className="w-full h-2 bg-surface-secondary rounded-full overflow-hidden">
                <div
                  className="h-full bg-accent transition-all duration-300"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            </div>
          )}
        </div>
      </Modal>

      {/* Assign Translator */}
      <Modal open={assignOpen} onClose={() => setAssignOpen(false)} title="分配章节" size="sm">
        <div className="space-y-4">
          <p className="text-sm text-text-tertiary">
            {selectedChapter && `第${selectedChapter.chapterNumber}章: ${selectedChapter.title}`}
          </p>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">译者</label>
            {renderTranslatorOptions()}
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">审校（可选）</label>
            {renderReviewerOptions()}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setAssignOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              取消
            </button>
            <button
              onClick={handleAssign}
              className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
            >
              分配
            </button>
          </div>
        </div>
      </Modal>

      {/* Review */}
      <Modal open={reviewOpen} onClose={() => setReviewOpen(false)} title="审核章节" size="lg">
        <div className="space-y-4">
          <p className="text-sm text-text-tertiary">
            {selectedChapter && `第${selectedChapter.chapterNumber}章: ${selectedChapter.title}`}
          </p>
          {selectedChapter?.translatedText && (
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">译文预览</label>
              <div className="max-h-48 overflow-y-auto p-3 border border-border rounded-lg bg-surface-secondary text-sm text-text-secondary whitespace-pre-wrap">
                {selectedChapter.translatedText}
              </div>
            </div>
          )}
          <div>
            <label className="block text-sm font-medium text-text-primary mb-2">审核结果</label>
            <div className="flex gap-4">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  checked={reviewApproved}
                  onChange={() => setReviewApproved(true)}
                  className="w-4 h-4 text-accent"
                />
                <span className="text-sm text-green font-medium">通过</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  checked={!reviewApproved}
                  onChange={() => setReviewApproved(false)}
                  className="w-4 h-4 text-red"
                />
                <span className="text-sm text-red font-medium">退回</span>
              </label>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">
              {reviewApproved ? '备注（可选）' : '退回原因'}
            </label>
            <textarea
              value={reviewComment}
              onChange={e => setReviewComment(e.target.value)}
              placeholder={reviewApproved ? '可以添加备注...' : '请说明需要修改的地方...'}
              rows={3}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50 resize-none"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setReviewOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              取消
            </button>
            <button
              onClick={handleReview}
              className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white rounded-button transition-button ${
                reviewApproved
                  ? 'bg-green hover:bg-green-hover'
                  : 'bg-red hover:bg-red-hover'
              }`}
            >
              {reviewApproved ? <CheckCircle className="w-3.5 h-3.5" /> : <XCircle className="w-3.5 h-3.5" />}
              {reviewApproved ? '通过' : '退回'}
            </button>
          </div>
        </div>
      </Modal>

      {/* Invite Member */}
      <Modal open={inviteOpen} onClose={() => setInviteOpen(false)} title="邀请成员" size="sm">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">邮箱</label>
            <input
              type="email"
              value={inviteEmail}
              onChange={e => setInviteEmail(e.target.value)}
              placeholder="member@example.com"
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">角色</label>
            <select
              value={inviteRole}
              onChange={e => setInviteRole(e.target.value as 'REVIEWER' | 'TRANSLATOR')}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
            >
              <option value="TRANSLATOR">译者</option>
              <option value="REVIEWER">审校</option>
            </select>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setInviteOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              取消
            </button>
            <button
              onClick={handleInvite}
              className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
            >
              邀请
            </button>
          </div>
        </div>
      </Modal>

      {/* Comments */}
      <Modal open={commentsOpen} onClose={() => setCommentsOpen(false)} title="评论" size="lg">
        <div className="space-y-4">
          {selectedChapter && (
            <p className="text-sm text-text-tertiary">
              第{selectedChapter.chapterNumber}章: {selectedChapter.title}
            </p>
          )}
          {/* Comment list */}
          <div className="max-h-64 overflow-y-auto space-y-3">
            {comments.length === 0 ? (
              <p className="text-center text-text-tertiary text-sm py-4">暂无评论</p>
            ) : (
              comments.map(comment => (
                <CommentItem key={comment.id} comment={comment} depth={0} />
              ))
            )}
          </div>
          {/* Input */}
          <div className="flex gap-2 pt-2 border-t border-border">
            <input
              value={commentContent}
              onChange={e => setCommentContent(e.target.value)}
              placeholder="输入评论..."
              className="flex-1 px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              onKeyDown={e => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSendComment();
                }
              }}
            />
            <button
              onClick={handleSendComment}
              disabled={!commentContent.trim()}
              className="px-3 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button disabled:opacity-50 disabled:cursor-not-allowed"
            >
              发送
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

// ==================== Sub-components ====================

function CommentItem({ comment, depth }: { comment: CommentResponse; depth: number }) {
  const indentClass = depth > 0 ? 'ml-8 border-l-2 border-border pl-4' : '';

  return (
    <div className={`${indentClass}`}>
      <div className="flex items-start gap-2">
        <div className="w-7 h-7 rounded-full bg-accent/20 text-accent flex items-center justify-center text-xs font-semibold flex-shrink-0">
          {comment.username?.slice(0, 1).toUpperCase() || 'U'}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-text-primary">{comment.username}</span>
            <span className="text-xs text-text-tertiary">{comment.createTime}</span>
            {comment.resolved && (
              <span className="text-xs text-green">已解决</span>
            )}
          </div>
          {comment.sourceText && (
            <div className="mt-1 text-xs text-text-tertiary bg-surface-secondary px-2 py-1 rounded">
              原文: {comment.sourceText}
            </div>
          )}
          <p className="mt-1 text-sm text-text-secondary whitespace-pre-wrap">{comment.content}</p>
        </div>
      </div>
      {comment.replies?.map(reply => (
        <CommentItem key={reply.id} comment={reply} depth={depth + 1} />
      ))}
    </div>
  );
}

interface ProjectDetailViewProps {
  projectId: number;
  projects: CollabProjectResponse[];
  chapters: ChapterTaskResponse[];
  chaptersLoading: boolean;
  members: ProjectMemberResponse[];
  userRole: string;
  detailTab: string;
  setDetailTab: (tab: string) => void;
  myProjectTasks: ChapterTaskResponse[];
  onBack: () => void;
  onAddChapter: () => void;
  onUploadNovel: () => void;
  onAssign: (chapter: ChapterTaskResponse) => void;
  onReview: (chapter: ChapterTaskResponse) => void;
  onComments: (chapter: ChapterTaskResponse) => void;
  onInvite: () => void;
  onGenerateInvite: () => void;
  onRemoveMember: (memberId: number) => void;
}

function ProjectDetailView({
  projectId, projects, chapters, chaptersLoading, members, userRole,
  detailTab, setDetailTab, myProjectTasks, onBack, onAddChapter, onUploadNovel,
  onAssign, onReview, onComments, onInvite, onGenerateInvite, onRemoveMember,
}: ProjectDetailViewProps) {
  const navigate = useNavigate();
  const project = projects.find(p => p.id === projectId);
  if (!project) return null;

  const detailTabs: Tab[] = [
    { key: 'chapters', label: '章节管理' },
    { key: 'members', label: '成员管理' },
    { key: 'tasks', label: '我的任务' },
  ];

  const renderStatusBadge = (status: string) => {
    const config: Record<string, { label: string; color: Parameters<typeof Badge>[0]['color'] }> = {
      UNASSIGNED: { label: '待分配', color: 'gray' },
      TRANSLATING: { label: '翻译中', color: 'blue' },
      SUBMITTED: { label: '已提交', color: 'yellow' },
      REVIEWING: { label: '审核中', color: 'purple' },
      APPROVED: { label: '已批准', color: 'green' },
      REJECTED: { label: '已退回', color: 'red' },
      COMPLETED: { label: '已完成', color: 'green' },
    };
    const { label, color } = config[status] || { label: status, color: 'gray' as const };
    return <Badge color={color}>{label}</Badge>;
  };

  const canManage = userRole === 'OWNER';

  return (
    <div className="bg-surface-secondary rounded-card border border-border">
      {/* Header */}
      <div className="p-4 border-b border-border">
        <div className="flex items-center gap-3 mb-2">
          <button
            onClick={onBack}
            className="p-1.5 rounded-full hover:bg-surface-secondary transition-button text-text-secondary"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <h2 className="text-lg font-semibold text-text-primary">{project.name}</h2>
          <Badge color={project.status === 'ACTIVE' ? 'green' : 'gray'}>
            {project.status === 'ACTIVE' ? '进行中' : project.status}
          </Badge>
        </div>
        <p className="text-sm text-text-tertiary ml-9">
          {getLangName(project.sourceLang)} → {getLangName(project.targetLang)} · {project.memberCount} 成员 · {project.totalChapters} 章节
        </p>
      </div>

      {/* Detail Tabs */}
      <div className="px-4 pt-3 border-b border-border">
        <Tabs tabs={detailTabs} activeTab={detailTab} onChange={setDetailTab} />
      </div>

      {/* Chapters Tab */}
      {detailTab === 'chapters' && (
        <div>
          <div className="flex items-center justify-between p-4 border-b border-border">
            <span className="text-sm font-medium text-text-primary">章节列表</span>
            {canManage && (
              <div className="flex items-center gap-2">
                <button
                  onClick={onUploadNovel}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
                  title="上传小说文件，自动拆分章节"
                >
                  <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                    <polyline points="17 8 12 3 7 8"/>
                    <line x1="12" y1="3" x2="12" y2="15"/>
                  </svg>
                  上传小说
                </button>
                <button
                  onClick={onAddChapter}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
                >
                  <Plus className="w-3.5 h-3.5" /> 添加章节
                </button>
              </div>
            )}
          </div>
          {chaptersLoading ? (
            <div className="p-12 text-center text-text-tertiary">加载中...</div>
          ) : chapters.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <BookOpen className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">暂无章节</p>
              <p className="text-text-tertiary text-sm mt-1">{canManage ? '点击"添加章节"开始' : '等待所有者添加章节'}</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border bg-surface-secondary">
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">章节</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">标题</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">状态</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">译者</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">审者</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">进度</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {chapters.map(chapter => (
                    <tr key={chapter.id} className="hover:bg-surface-secondary/50">
                      <td className="px-4 py-3 font-mono text-text-primary">#{chapter.chapterNumber}</td>
                      <td className="px-4 py-3 text-text-primary max-w-48 truncate">{chapter.title || '-'}</td>
                      <td className="px-4 py-3">{renderStatusBadge(chapter.status)}</td>
                      <td className="px-4 py-3 text-text-tertiary">{chapter.assigneeName || '-'}</td>
                      <td className="px-4 py-3 text-text-tertiary">{chapter.reviewerName || '-'}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-16 h-1.5 bg-surface-secondary rounded-full overflow-hidden">
                            <div className="h-full bg-accent" style={{ width: `${chapter.progress || 0}%` }} />
                          </div>
                          <span className="text-xs text-text-tertiary font-mono">{chapter.progress || 0}%</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1">
                          {chapter.status === 'UNASSIGNED' && canManage && (
                            <button
                              onClick={() => onAssign(chapter)}
                              className="flex items-center gap-1 px-2 py-1 text-xs text-text-primary border border-border rounded hover:bg-surface-secondary transition-button"
                            >
                              <UserCheck className="w-3 h-3" /> 分配
                            </button>
                          )}
                          <button
                            onClick={() => navigate(`/collab/workspace?chapterId=${chapter.id}&targetLang=${project.targetLang}`)}
                            className="flex items-center gap-1 px-2 py-1 text-xs text-white bg-accent rounded hover:bg-accent-hover transition-button"
                          >
                            <BookOpen className="w-3 h-3" /> 工作台
                          </button>
                          {chapter.status === 'TRANSLATING' && chapter.assigneeId && (
                            <button
                              onClick={() => navigate(`/collab/workspace?chapterId=${chapter.id}&targetLang=${project.targetLang}`)}
                              className="flex items-center gap-1 px-2 py-1 text-xs text-white bg-accent rounded hover:bg-accent-hover transition-button"
                            >
                              <BookOpen className="w-3 h-3" /> 翻译
                            </button>
                          )}
                          {(chapter.status === 'SUBMITTED' || chapter.status === 'REVIEWING') && (
                            <button
                              onClick={() => onReview(chapter)}
                              className="flex items-center gap-1 px-2 py-1 text-xs text-white bg-green rounded hover:bg-green-hover transition-button"
                            >
                              <CheckCircle className="w-3 h-3" /> 审核
                            </button>
                          )}
                          <button
                            onClick={() => onComments(chapter)}
                            className="flex items-center gap-1 px-2 py-1 text-xs text-text-primary border border-border rounded hover:bg-surface-secondary transition-button"
                          >
                            <MessageSquare className="w-3 h-3" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Members Tab */}
      {detailTab === 'members' && (
        <div>
          <div className="flex items-center justify-between p-4 border-b border-border">
            <span className="text-sm font-medium text-text-primary">成员列表</span>
            {canManage && (
              <div className="flex items-center gap-2">
                <button
                  onClick={onGenerateInvite}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
                  title="生成邀请码"
                >
                  <ClipboardList className="w-3.5 h-3.5" /> 邀请码
                </button>
                <button
                  onClick={onInvite}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
                >
                  <Users className="w-3.5 h-3.5" /> 邀请成员
                </button>
              </div>
            )}
          </div>
          {members.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <Users className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">暂无成员</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border bg-surface-secondary">
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">用户名</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">邮箱</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">角色</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">状态</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">加入时间</th>
                    {canManage && (
                      <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">操作</th>
                    )}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {members.map(member => (
                    <tr key={member.id} className="hover:bg-surface-secondary/50">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-full bg-accent/20 text-accent flex items-center justify-center text-xs font-semibold">
                            {member.username?.slice(0, 1).toUpperCase() || 'U'}
                          </div>
                          <span className="text-text-primary">{member.username}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-text-tertiary">{member.email}</td>
                      <td className="px-4 py-3">
                        <Badge color={member.role === 'OWNER' ? 'purple' : member.role === 'REVIEWER' ? 'blue' : 'gray'}>
                          {member.role === 'OWNER' ? '所有者' : member.role === 'REVIEWER' ? '审校' : '译者'}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-text-tertiary text-xs">{member.inviteStatus}</td>
                      <td className="px-4 py-3 text-text-tertiary text-xs">{member.joinedTime || '-'}</td>
                      {canManage && member.role !== 'OWNER' && (
                        <td className="px-4 py-3">
                          <button
                            onClick={() => onRemoveMember(member.id)}
                            className="p-1 text-text-tertiary hover:text-red transition-button"
                            title="移除成员"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </td>
                      )}
                      {canManage && member.role === 'OWNER' && (
                        <td className="px-4 py-3 text-text-tertiary text-xs">-</td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* My Tasks Tab (within project) */}
      {detailTab === 'tasks' && (
        <div>
          <div className="p-4 border-b border-border">
            <span className="text-sm font-medium text-text-primary">我的任务</span>
          </div>
          {myProjectTasks.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <ClipboardList className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">暂无分配任务</p>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {myProjectTasks.map(chapter => (
                <div key={chapter.id} className="p-4 flex items-center justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <BookOpen className="w-4 h-4 text-text-tertiary" />
                      <span className="text-sm font-medium text-text-primary truncate">
                        第{chapter.chapterNumber}章 {chapter.title}
                      </span>
                      {renderStatusBadge(chapter.status)}
                    </div>
                    <p className="text-xs text-text-tertiary">
                      {chapter.assigneeName === '你' || chapter.assigneeId ? '译者' : '审者'} · {chapter.progress || 0}%
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export { CollabPage };
