import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../components/ui/Toast';
import { Tabs } from '../components/ui/Tabs';
import type { Tab } from '../components/ui/Tabs';
import { Modal } from '../components/ui/Modal';
import { Badge } from '../components/ui/Badge';
import { Pagination } from '../components/ui/Pagination';
import { collabApi } from '../api/collab';
import type {
  CollabProjectResponse,
  ChapterTaskResponse,
  ProjectMemberResponse,
  CommentResponse,
} from '../api/types';
import { LANGUAGE_CODES } from '../api/types';
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
import { useTranslation } from 'react-i18next';

// ==================== Status helpers (resolved inside components via t()) ====================

function getLangName(t: ReturnType<typeof useTranslation>['t'], code: string): string {
  return t(`common.languages.${code}`, { defaultValue: code });
}

// ==================== Main Component ====================
function CollabPage() {
  const navigate = useNavigate();
  const { success, error: toastError } = useToast();
  const { user } = useAuth();
  const { t } = useTranslation();

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

  // Pagination state
  const [projectPage, setProjectPage] = useState(1);
  const [projectTotalPages, setProjectTotalPages] = useState(1);
  const [chapterPage, setChapterPage] = useState(1);
  const [chapterTotalPages, setChapterTotalPages] = useState(1);
  const [memberPage, setMemberPage] = useState(1);
  const [memberTotalPages, setMemberTotalPages] = useState(1);
  const [myTasksPage, setMyTasksPage] = useState(1);
  const [myTasksTotalPages, setMyTasksTotalPages] = useState(1);
  const [commentPage, setCommentPage] = useState(1);

  // Create project form
  const [cpName, setCpName] = useState('');
  const [cpDesc, setCpDesc] = useState('');
  const [cpSourceLang, setCpSourceLang] = useState('ja');
  const [cpTargetLang, setCpTargetLang] = useState('zh');

  // ==================== Data loading ====================
  const loadProjects = async () => {
    setProjectsLoading(true);
    try {
      const res = await collabApi.listProjects({ page: projectPage, pageSize: 9 });
      setProjects(res.data.list);
      setProjectTotalPages(res.data.totalPages);
    } catch {
      // API not ready yet, show empty state
    } finally {
      setProjectsLoading(false);
    }
  };

  const loadChapters = async (projectId: number) => {
    setChaptersLoading(true);
    try {
      const res = await collabApi.listChapters(projectId, { page: chapterPage, pageSize: 20 });
      setChapters(res.data.list);
      setChapterTotalPages(res.data.totalPages);
    } catch {
      toastError(t('collab.messages.loadChapterFailed'));
    } finally {
      setChaptersLoading(false);
    }
  };

  const loadMembers = async (projectId: number) => {
    try {
      const res = await collabApi.listMembers(projectId, { page: memberPage, pageSize: 20 });
      setMembers(res.data.list);
      setMemberTotalPages(res.data.totalPages);
      if (user?.id) {
        const me = res.data.list.find(m => m.userId === user.id);
        setUserRole(me?.role || '');
      }
    } catch {
      toastError(t('collab.messages.loadMemberFailed'));
    }
  };

  const loadMyChapters = async () => {
    setMyChaptersLoading(true);
    try {
      const res = await collabApi.listMyChapters({ page: myTasksPage, pageSize: 20 });
      setMyChapters(res.data.list);
      setMyTasksTotalPages(res.data.totalPages);
    } catch {
      // API not ready yet
    } finally {
      setMyChaptersLoading(false);
    }
  };

  const loadComments = async (chapterId: number) => {
    try {
      const res = await collabApi.listComments(chapterId, commentPage, 20);
      setComments(res.data.list);
    } catch {
      toastError(t('collab.messages.commentFailed'));
    }
  };

  useEffect(() => { loadProjects(); }, [projectPage]);
  useEffect(() => {
    if (selectedProjectId) {
      loadChapters(selectedProjectId);
      loadMembers(selectedProjectId);
    }
  }, [selectedProjectId, chapterPage, memberPage]);
  useEffect(() => {
    if (activeMainTab === 'my-tasks') loadMyChapters();
  }, [activeMainTab, myTasksPage]);
  useEffect(() => {
    if (commentsOpen && selectedChapter) {
      loadComments(selectedChapter.id);
    }
  }, [commentPage]);

  // ==================== Handlers ====================
  const handleSelectProject = (id: number) => {
    setSelectedProjectId(id);
    setDetailTab('chapters');
    setChapterPage(1);
    setMemberPage(1);
  };

  const handleBackToProjects = () => {
    setSelectedProjectId(null);
    setChapterPage(1);
    setMemberPage(1);
  };

  const handleCreateProject = async () => {
    if (!cpName.trim()) { toastError(t('collab.fields.projectName') + t('collab.validation.required', { defaultValue: '不能为空' })); return; }
    if (cpSourceLang === cpTargetLang) { toastError(t('collab.validation.langMismatch', { defaultValue: '源语言和目标语言不能相同' })); return; }
    try {
      await collabApi.createProject({
        name: cpName,
        description: cpDesc,
        sourceLang: cpSourceLang,
        targetLang: cpTargetLang,
      });
      success(t('collab.messages.projectCreated'));
      setCreateProjectOpen(false);
      setCpName(''); setCpDesc(''); setCpSourceLang('ja'); setCpTargetLang('zh');
      loadProjects();
    } catch (e) {
      toastError(e instanceof Error ? e.message : t('collab.messages.createFailed'));
    }
  };

  const handleJoinProject = async () => {
    if (!inviteCode.trim()) { toastError(t('collab.validation.inviteCodeRequired', { defaultValue: '请输入邀请码' })); return; }
    try {
      await collabApi.joinByCode(inviteCode);
      success(t('collab.messages.joinSuccess'));
      setJoinProjectOpen(false);
      setInviteCode('');
      loadProjects();
    } catch (e) {
      toastError(e instanceof Error ? e.message : t('collab.messages.joinFailed'));
    }
  };

  const handleGenerateInvite = async () => {
    if (!selectedProjectId) { toastError(t('collab.validation.selectProject', { defaultValue: '请先选择一个项目' })); return; }
    setGenerating(true);
    try {
      const res = await collabApi.generateInviteCode(selectedProjectId);
      setGeneratedCode(res.data.code);
      setGeneratedExpiry(res.data.expiresAt);
      setInviteCodeModalOpen(true);
    } catch (e) {
      toastError(e instanceof Error ? e.message : t('collab.messages.generateFailed'));
    } finally {
      setGenerating(false);
    }
  };

  const handleAddChapter = async () => {
    if (!selectedProjectId) return;
    try {
      await collabApi.createChapter(selectedProjectId, newChapterNum, newChapterTitle || undefined, newChapterSource || undefined);
      success(t('collab.messages.chapterAdded'));
      setAddChapterOpen(false);
      setNewChapterTitle(''); setNewChapterSource('');
      loadChapters(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : t('collab.messages.addChapterFailed'));
    }
  };

  const handleUploadNovel = async () => {
    if (!selectedProjectId) return;
    if (!uploadFile) { toastError(t('collab.validation.selectFile', { defaultValue: '请选择文件' })); return; }
    const project = projects.find(p => p.id === selectedProjectId);
    if (!project) return;
    setUploading(true);
    setUploadProgress(10);

    const timer = setInterval(() => {
      setUploadProgress(prev => Math.min(prev + 5, 90));
    }, 300);

    try {
      const res = await collabApi.uploadNovel(selectedProjectId, uploadFile, project.sourceLang, project.targetLang);
      clearInterval(timer);
      setUploadProgress(100);
      success(res.data.message || t('collab.messages.uploadSuccess'));
      setTimeout(() => {
        setUploadNovelOpen(false);
        setUploadFile(null);
        setUploadProgress(0);
      }, 500);
      loadChapters(selectedProjectId);
    } catch (e) {
      clearInterval(timer);
      toastError(e instanceof Error ? e.message : t('collab.messages.uploadFailed'));
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
      success(t('collab.messages.assignSuccess'));
      setAssignOpen(false);
      if (selectedProjectId) loadChapters(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : t('collab.messages.assignFailed'));
    }
  };

  const handleReview = async () => {
    if (!selectedChapter) return;
    try {
      await collabApi.reviewChapter(selectedChapter.id, {
        approved: reviewApproved,
        comment: reviewComment || undefined,
      });
      success(reviewApproved ? t('collab.messages.reviewApproved') : t('collab.messages.reviewRejected'));
      setReviewOpen(false);
      setReviewComment('');
      if (selectedProjectId) loadChapters(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : t('collab.messages.reviewFailed', { defaultValue: '审核失败' }));
    }
  };

  const handleInvite = async () => {
    if (!inviteEmail.trim()) { toastError(t('collab.validation.emailRequired', { defaultValue: '邮箱不能为空' })); return; }
    if (!selectedProjectId) return;
    try {
      await collabApi.inviteMember(selectedProjectId, { email: inviteEmail, role: inviteRole });
      success(t('collab.messages.inviteSent'));
      setInviteOpen(false);
      setInviteEmail('');
      loadMembers(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : t('collab.messages.inviteFailed'));
    }
  };

  const handleRemoveMember = async (memberId: number) => {
    if (!selectedProjectId) return;
    if (!confirm(t('collab.confirmRemoveMember', { defaultValue: '确定要移除该成员吗？' }))) return;
    try {
      await collabApi.removeMember(selectedProjectId, memberId);
      success(t('collab.messages.memberRemoved', { defaultValue: '成员已移除' }));
      loadMembers(selectedProjectId);
    } catch (e) {
      toastError(e instanceof Error ? e.message : t('collab.messages.removeFailed', { defaultValue: '移除失败' }));
    }
  };

  const handleOpenComments = (chapter: ChapterTaskResponse) => {
    setSelectedChapter(chapter);
    setCommentsOpen(true);
    setCommentPage(1);
    loadComments(chapter.id);
  };

  const handleSendComment = async () => {
    if (!selectedChapter || !commentContent.trim()) return;
    try {
      await collabApi.createComment(selectedChapter.id, { content: commentContent });
      setCommentContent('');
      loadComments(selectedChapter.id);
    } catch (e) {
      toastError(e instanceof Error ? e.message : t('collab.messages.commentFailed'));
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

  const handleMainTabChange = (tab: string) => {
    setActiveMainTab(tab);
    if (tab === 'projects') setProjectPage(1);
    if (tab === 'my-tasks') setMyTasksPage(1);
  };

  const handleProjectSearchChange = (value: string) => {
    setProjectSearch(value);
    setProjectPage(1);
  };

  const handleDetailTabChange = (tab: string) => {
    setDetailTab(tab);
    if (tab === 'chapters') setChapterPage(1);
    if (tab === 'members') setMemberPage(1);
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
    const config: Record<string, { label: string; color: Parameters<typeof Badge>[0]['color'] }> = type === 'chapter'
      ? {
          UNASSIGNED: { label: t('collab.taskStatus.pending'), color: 'gray' },
          TRANSLATING: { label: t('collab.taskStatus.translating'), color: 'blue' },
          SUBMITTED: { label: t('collab.taskStatus.submitted'), color: 'yellow' },
          REVIEWING: { label: t('collab.taskStatus.reviewing'), color: 'purple' },
          APPROVED: { label: t('collab.taskStatus.approved'), color: 'green' },
          REJECTED: { label: t('collab.taskStatus.rejected'), color: 'red' },
          COMPLETED: { label: t('collab.taskStatus.completed'), color: 'green' },
        }
      : type === 'project'
        ? {
            ACTIVE: { label: t('collab.projectStatus.active', { defaultValue: '进行中' }), color: 'green' },
            PAUSED: { label: t('collab.projectStatus.paused', { defaultValue: '已暂停' }), color: 'yellow' },
            COMPLETED: { label: t('collab.taskStatus.completed'), color: 'gray' },
            ARCHIVED: { label: t('collab.projectStatus.archived', { defaultValue: '已归档' }), color: 'gray' },
          }
        : {
            OWNER: { label: t('collab.roles.owner'), color: 'purple' },
            REVIEWER: { label: t('collab.roles.reviewer'), color: 'blue' },
            TRANSLATOR: { label: t('collab.roles.translator'), color: 'gray' },
          };
    const { label, color } = config[status] || { label: status, color: 'gray' as const };
    return <Badge color={color}>{label}</Badge>;
  };

  const renderTranslatorOptions = () => {
    const translators = members.filter(m => m.role === 'TRANSLATOR' || m.role === 'OWNER');
    if (translators.length === 0) return <p className="text-sm text-text-tertiary">{t('collab.validation.noTranslators', { defaultValue: '暂无可分配的译者' })}</p>;
    return (
      <select
        value={assignTargetId}
        onChange={e => setAssignTargetId(Number(e.target.value))}
        className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
      >
        <option value={0}>{t('collab.fields.translator')}</option>
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
        <option value={0}>{t('collab.fields.reviewer')}</option>
        {reviewers.map(m => (
          <option key={m.userId} value={m.userId}>{m.username}</option>
        ))}
      </select>
    );
  };

  // ==================== Main render ====================
  const mainTabs: Tab[] = [
    { key: 'projects', label: t('collab.tabs.myProjects') },
    { key: 'my-tasks', label: t('collab.tabs.myTasks') },
  ];

  return (
    <div className="py-8">
      {/* Page Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-text-primary">{t('collab.title')}</h1>
        <p className="text-text-tertiary mt-1">{t('collab.subtitle')}</p>
      </div>

      {/* Main Tabs */}
      <div className="mb-6">
        <Tabs tabs={mainTabs} activeTab={activeMainTab} onChange={handleMainTabChange} />
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
                placeholder={t('common.search')}
                value={projectSearch}
                onChange={e => handleProjectSearchChange(e.target.value)}
                style={{ paddingLeft: '3rem' }}
                className="w-full pr-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              />
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setJoinProjectOpen(true)}
                className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
              >
                <LogIn className="w-3.5 h-3.5" /> {t('collab.actions.joinProject')}
              </button>
              <button
                onClick={() => setCreateProjectOpen(true)}
                className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
              >
                <Plus className="w-3.5 h-3.5" /> {t('collab.actions.createProject')}
              </button>
            </div>
          </div>

          {/* Project Grid */}
          {projectsLoading ? (
            <div className="p-12 text-center text-text-tertiary">{t('common.loading')}</div>
          ) : filteredProjects.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <FolderOpen className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">{t('common.noData')}</p>
              <p className="text-text-tertiary text-sm mt-1">{t('collab.empty.createOrJoin', { defaultValue: '创建一个新项目或加入已有项目' })}</p>
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
                    {project.description || t('collab.empty.noDescription', { defaultValue: '暂无描述' })}
                  </p>
                  <div className="flex items-center gap-2 text-[12px] text-text-tertiary mb-3">
                    <span>{getLangName(t, project.sourceLang)} → {getLangName(t, project.targetLang)}</span>
                  </div>
                  {/* Progress */}
                  <div className="mb-3">
                    <div className="flex items-center justify-between text-[11px] text-text-tertiary mb-1">
                      <span>{t('collab.fields.progress', { defaultValue: '进度' })}</span>
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
                    <span>{project.memberCount || 1} {t('collab.fields.members', { defaultValue: '成员' })} · {project.completedChapters || 0}/{project.totalChapters || 0} {t('collab.fields.chapters', { defaultValue: '章节' })}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
          <Pagination page={projectPage} totalPages={projectTotalPages} onPageChange={setProjectPage} />
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
          setDetailTab={handleDetailTabChange}
          myProjectTasks={myProjectTasks}
          chapterPage={chapterPage}
          chapterTotalPages={chapterTotalPages}
          memberPage={memberPage}
          memberTotalPages={memberTotalPages}
          onChapterPageChange={setChapterPage}
          onMemberPageChange={setMemberPage}
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
          t={t}
        />
      )}

      {/* ==================== Tab: 我的任务 ==================== */}
      {activeMainTab === 'my-tasks' && (
        <div className="bg-surface-secondary rounded-card border border-border">
          <div className="p-4 border-b border-border">
            <h2 className="text-base font-semibold text-text-primary">{t('collab.myTasksTitle', { defaultValue: '我的翻译与审校任务' })}</h2>
          </div>
          {myChaptersLoading ? (
            <div className="p-12 text-center text-text-tertiary">{t('common.loading')}</div>
          ) : myChapters.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <ClipboardList className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">{t('common.noData')}</p>
              <p className="text-text-tertiary text-sm mt-1">{t('collab.empty.waitingAssignment', { defaultValue: '等待项目所有者分配章节任务' })}</p>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {myChapters.map(chapter => (
                <div key={chapter.id} className="p-4 flex items-center justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <BookOpen className="w-4 h-4 text-text-tertiary" />
                      <span className="text-sm font-medium text-text-primary truncate">
                        {t('collab.chapterLabel', { num: chapter.chapterNumber, title: chapter.title || '' })}
                      </span>
                      {renderStatusBadge(chapter.status, 'chapter')}
                    </div>
                    <p className="text-xs text-text-tertiary">
                      {chapter.assigneeName === user?.username ? t('collab.roles.translator') : t('collab.roles.reviewer')} · {chapter.progress || 0}% {t('collab.fields.complete', { defaultValue: '完成' })}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 ml-4 flex-shrink-0">
                    {chapter.status === 'TRANSLATING' && chapter.assigneeId === user?.id && (
                      <button
                        onClick={() => navigate(`/collab/workspace?chapterId=${chapter.id}&targetLang=${selectedProject?.targetLang}`)}
                        className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
                      >
                        <BookOpen className="w-3 h-3" /> {t('collab.actions.translate')}
                      </button>
                    )}
                    {chapter.status !== 'TRANSLATING' && (
                      <button
                        onClick={() => navigate(`/collab/workspace?chapterId=${chapter.id}&targetLang=${selectedProject?.targetLang}`)}
                        className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
                      >
                        <BookOpen className="w-3 h-3" /> {t('collab.actions.workspace')}
                      </button>
                    )}
                    {(chapter.status === 'SUBMITTED' || chapter.status === 'REVIEWING') && chapter.reviewerId === user?.id && (
                      <button
                        onClick={() => openReviewModal(chapter)}
                        className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-white bg-green rounded-button hover:bg-green-hover transition-button"
                      >
                        <CheckCircle className="w-3 h-3" /> {t('collab.actions.review')}
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
          <Pagination page={myTasksPage} totalPages={myTasksTotalPages} onPageChange={setMyTasksPage} />
        </div>
      )}

      {/* ==================== Modals ==================== */}
      {/* Create Project */}
      <Modal open={createProjectOpen} onClose={() => setCreateProjectOpen(false)} title={t('collab.forms.createProject')} size="md">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.projectName')}</label>
            <input
              value={cpName}
              onChange={e => setCpName(e.target.value)}
              placeholder={t('collab.placeholder.projectName', { defaultValue: '输入项目名称' })}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.description')}</label>
            <textarea
              value={cpDesc}
              onChange={e => setCpDesc(e.target.value)}
              placeholder={t('collab.placeholder.projectDesc', { defaultValue: '项目描述（可选）' })}
              rows={3}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50 resize-none"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.sourceLanguage')}</label>
              <select
                value={cpSourceLang}
                onChange={e => setCpSourceLang(e.target.value)}
                className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              >
                {LANGUAGE_CODES.filter(c => c !== 'auto').map(code => (
                  <option key={code} value={code}>{t(`common.languages.${code}`)}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.targetLanguage')}</label>
              <select
                value={cpTargetLang}
                onChange={e => setCpTargetLang(e.target.value)}
                className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              >
                {LANGUAGE_CODES.filter(c => c !== 'auto').map(code => (
                  <option key={code} value={code}>{t(`common.languages.${code}`)}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setCreateProjectOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              {t('common.cancel')}
            </button>
            <button
              onClick={handleCreateProject}
              className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
            >
              {t('collab.actions.createProject')}
            </button>
          </div>
        </div>
      </Modal>

      {/* Join Project */}
      <Modal open={joinProjectOpen} onClose={() => { setJoinProjectOpen(false); setJoinMode('input'); setGeneratedCode(''); setGeneratedExpiry(''); }} title={t('collab.forms.joinProject')} size="sm">
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
              {t('collab.joinMode.inputCode', { defaultValue: '输入邀请码' })}
            </button>
            <button
              onClick={() => setJoinMode('generate')}
              className={`flex-1 py-2 text-sm font-medium rounded-lg border transition-colors ${
                joinMode === 'generate'
                  ? 'border-accent bg-accent/5 text-accent'
                  : 'border-border text-text-tertiary hover:text-text-primary'
              }`}
            >
              {t('collab.joinMode.generateCode', { defaultValue: '生成邀请码' })}
            </button>
          </div>

          {joinMode === 'input' ? (
            <>
              <div>
                <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.invitationCode')}</label>
                <input
                  value={inviteCode}
                  onChange={e => setInviteCode(e.target.value)}
                  placeholder={t('collab.placeholder.inviteCode', { defaultValue: '输入他人分享的邀请码' })}
                  className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  onClick={() => setJoinProjectOpen(false)}
                  className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
                >
                  {t('common.cancel')}
                </button>
                <button
                  onClick={handleJoinProject}
                  className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
                >
                  {t('collab.actions.joinProject', { defaultValue: '加入' })}
                </button>
              </div>
            </>
          ) : (
            <>
              <div className="text-xs text-text-tertiary bg-surface-secondary rounded-lg p-3 border border-border">
                <p className="font-medium text-text-secondary mb-1">{t('collab.inviteCode.expiry', { defaultValue: '邀请码有效期 3 天' })}</p>
                <p>{t('collab.inviteCode.description', { defaultValue: '生成后可分享给他人，有效期为生成时间起 72 小时，过期后需重新生成。' })}</p>
              </div>
              <button
                onClick={handleGenerateInvite}
                disabled={generating}
                className="w-full py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button disabled:opacity-50"
              >
                {generating ? t('common.loading') : t('collab.joinMode.generateCode', { defaultValue: '生成邀请码' })}
              </button>
              {generatedCode && (
                <div className="rounded-lg border-2 border-dashed border-accent/40 bg-accent-bg p-4 text-center">
                  <p className="text-xs text-text-tertiary mb-2">{t('collab.fields.invitationCode')}（{t('collab.inviteCode.clickCopy', { defaultValue: '点击复制' })}）</p>
                  <p
                    className="text-2xl font-mono font-bold text-accent cursor-pointer tracking-widest"
                    onClick={async () => {
                      try { await navigator.clipboard.writeText(generatedCode); success(t('collab.messages.copied')); }
                      catch { toastError(t('collab.messages.copyFailed')); }
                    }}
                  >
                    {generatedCode}
                  </p>
                  {generatedExpiry && (
                    <p className="text-xs text-text-tertiary mt-2">
                      {t('collab.inviteCode.validUntil', { defaultValue: '有效期至' })} {new Date(generatedExpiry).toLocaleString()}
                    </p>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </Modal>

      {/* Invite Code Generator */}
      <Modal open={inviteCodeModalOpen} onClose={() => setInviteCodeModalOpen(false)} title={t('collab.fields.invitationCode')} size="sm">
        <div className="space-y-4">
          <div className="text-xs text-text-tertiary bg-surface-secondary rounded-lg p-3 border border-border">
            <p className="font-medium text-text-secondary mb-1">{t('collab.inviteCode.expiry', { defaultValue: '邀请码有效期 3 天' })}</p>
            <p>{t('collab.inviteCode.description', { defaultValue: '生成后可分享给他人，有效期为生成时间起 72 小时，过期后需重新生成。' })}</p>
          </div>
          <button
            onClick={() => { setGeneratedCode(''); setGeneratedExpiry(''); handleGenerateInvite(); }}
            disabled={generating}
            className="w-full py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button disabled:opacity-50"
          >
            {generating ? t('common.loading') : t('collab.inviteCode.generateNew', { defaultValue: '生成新邀请码' })}
          </button>
          {generatedCode && (
            <div className="rounded-lg border-2 border-dashed border-accent/40 bg-accent-bg p-4 text-center">
              <p className="text-xs text-text-tertiary mb-2">{t('collab.fields.invitationCode')}（{t('collab.inviteCode.clickCopy', { defaultValue: '点击复制' })}）</p>
              <p
                className="text-2xl font-mono font-bold text-accent cursor-pointer tracking-widest"
                onClick={async () => {
                  try { await navigator.clipboard.writeText(generatedCode); success(t('collab.messages.copied')); }
                  catch { toastError(t('collab.messages.copyFailed')); }
                }}
              >
                {generatedCode}
              </p>
              {generatedExpiry && (
                <p className="text-xs text-text-tertiary mt-2">
                  {t('collab.inviteCode.validUntil', { defaultValue: '有效期至' })} {new Date(generatedExpiry).toLocaleString()}
                </p>
              )}
            </div>
          )}
        </div>
      </Modal>

      {/* Add Chapter */}
      <Modal open={addChapterOpen} onClose={() => setAddChapterOpen(false)} title={t('collab.forms.addChapter')} size="lg">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.chapterNumber')}</label>
              <input
                type="number"
                value={newChapterNum}
                onChange={e => setNewChapterNum(Number(e.target.value))}
                className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.chapterTitle')}</label>
              <input
                value={newChapterTitle}
                onChange={e => setNewChapterTitle(e.target.value)}
                placeholder={t('common.optional', { defaultValue: '可选' })}
                className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.sourceContent')}</label>
            <textarea
              value={newChapterSource}
              onChange={e => setNewChapterSource(e.target.value)}
              placeholder={t('collab.placeholder.sourceContent', { defaultValue: '粘贴章节原文，或稍后上传' })}
              rows={6}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50 resize-none font-mono"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setAddChapterOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              {t('common.cancel')}
            </button>
            <button
              onClick={handleAddChapter}
              className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
            >
              {t('collab.actions.addChapter')}
            </button>
          </div>
        </div>
      </Modal>

      {/* Upload Novel */}
      <Modal open={uploadNovelOpen} onClose={() => { setUploadNovelOpen(false); setUploadFile(null); setUploadProgress(0); }} title={t('collab.forms.uploadNovel')} size="md">
        <div className="space-y-4">
          <div className="text-xs text-text-tertiary bg-surface-secondary rounded-lg p-3 border border-border">
            <p className="font-medium text-text-secondary mb-1">{t('collab.uploadNovel.title', { defaultValue: '团队协作翻译' })}</p>
            <p>{t('collab.uploadNovel.description', { defaultValue: '上传小说后系统将自动拆分章节，创建翻译任务并分配到项目中。支持 .txt、.epub、.docx 格式文件。' })}</p>
          </div>

          {/* File drop zone */}
          <label className="flex flex-col items-center justify-center border-2 border-dashed border-border rounded-xl p-8 cursor-pointer hover:border-accent/50 hover:bg-accent-bg/30 transition-colors">
            {uploadFile ? (
              <div className="text-center">
                <p className="text-sm font-medium text-text-primary">{uploadFile.name}</p>
                <p className="text-xs text-text-tertiary mt-1">{(uploadFile.size / 1024).toFixed(1)} KB</p>
                <p className="text-xs text-accent mt-2">{t('collab.uploadNovel.changeFile', { defaultValue: '点击更换文件' })}</p>
              </div>
            ) : (
              <div className="text-center">
                <svg className="w-10 h-10 text-text-tertiary mx-auto mb-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="17 8 12 3 7 8"/>
                  <line x1="12" y1="3" x2="12" y2="15"/>
                </svg>
                <p className="text-sm font-medium text-text-secondary">{t('collab.uploadNovel.dragOrClick', { defaultValue: '点击或拖拽上传小说' })}</p>
                <p className="text-xs text-text-tertiary mt-1">{t('collab.uploadNovel.supportedFormats', { defaultValue: '支持 .txt .epub .docx 文件' })}</p>
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
            {uploading ? t('document.upload.uploading') : t('collab.uploadNovel.uploadAndSplit', { defaultValue: '上传并自动拆分章节' })}
          </button>

          {/* Progress */}
          {uploading && (
            <div className="space-y-1">
              <div className="flex items-center justify-between text-xs text-text-tertiary">
                <span>{t('collab.uploadNovel.progress', { defaultValue: '上传进度' })}</span>
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
      <Modal open={assignOpen} onClose={() => setAssignOpen(false)} title={t('collab.forms.assignChapter')} size="sm">
        <div className="space-y-4">
          <p className="text-sm text-text-tertiary">
            {selectedChapter && t('collab.chapterLabel', { num: selectedChapter.chapterNumber, title: selectedChapter.title || '' })}
          </p>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.translator')}</label>
            {renderTranslatorOptions()}
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.reviewer')}</label>
            {renderReviewerOptions()}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setAssignOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              {t('common.cancel')}
            </button>
            <button
              onClick={handleAssign}
              className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
            >
              {t('collab.actions.assign')}
            </button>
          </div>
        </div>
      </Modal>

      {/* Review */}
      <Modal open={reviewOpen} onClose={() => setReviewOpen(false)} title={t('collab.forms.reviewChapter')} size="lg">
        <div className="space-y-4">
          <p className="text-sm text-text-tertiary">
            {selectedChapter && t('collab.chapterLabel', { num: selectedChapter.chapterNumber, title: selectedChapter.title || '' })}
          </p>
          {selectedChapter?.translatedText && (
            <div>
              <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.review.preview', { defaultValue: '译文预览' })}</label>
              <div className="max-h-48 overflow-y-auto p-3 border border-border rounded-lg bg-surface-secondary text-sm text-text-secondary whitespace-pre-wrap">
                {selectedChapter.translatedText}
              </div>
            </div>
          )}
          <div>
            <label className="block text-sm font-medium text-text-primary mb-2">{t('collab.fields.reviewResult')}</label>
            <div className="flex gap-4">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  checked={reviewApproved}
                  onChange={() => setReviewApproved(true)}
                  className="w-4 h-4 text-accent"
                />
                <span className="text-sm text-green font-medium">{t('collab.fields.approve')}</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  checked={!reviewApproved}
                  onChange={() => setReviewApproved(false)}
                  className="w-4 h-4 text-red"
                />
                <span className="text-sm text-red font-medium">{t('collab.fields.reject')}</span>
              </label>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">
              {reviewApproved ? t('collab.review.optionalComment', { defaultValue: '备注（可选）' }) : t('collab.review.rejectReason', { defaultValue: '退回原因' })}
            </label>
            <textarea
              value={reviewComment}
              onChange={e => setReviewComment(e.target.value)}
              placeholder={reviewApproved ? t('collab.review.commentPlaceholder', { defaultValue: '可以添加备注...' }) : t('collab.review.rejectPlaceholder', { defaultValue: '请说明需要修改的地方...' })}
              rows={3}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50 resize-none"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setReviewOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              {t('common.cancel')}
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
              {reviewApproved ? t('collab.fields.approve') : t('collab.fields.reject')}
            </button>
          </div>
        </div>
      </Modal>

      {/* Invite Member */}
      <Modal open={inviteOpen} onClose={() => setInviteOpen(false)} title={t('collab.forms.inviteMember')} size="sm">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.email')}</label>
            <input
              type="email"
              value={inviteEmail}
              onChange={e => setInviteEmail(e.target.value)}
              placeholder="member@example.com"
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">{t('collab.fields.role')}</label>
            <select
              value={inviteRole}
              onChange={e => setInviteRole(e.target.value as 'REVIEWER' | 'TRANSLATOR')}
              className="w-full px-3 py-2 border border-border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-accent/50"
            >
              <option value="TRANSLATOR">{t('collab.roles.translator')}</option>
              <option value="REVIEWER">{t('collab.roles.reviewer')}</option>
            </select>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              onClick={() => setInviteOpen(false)}
              className="px-4 py-2 text-sm font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
            >
              {t('common.cancel')}
            </button>
            <button
              onClick={handleInvite}
              className="px-4 py-2 text-sm font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
            >
              {t('collab.actions.inviteMember')}
            </button>
          </div>
        </div>
      </Modal>

      {/* Comments */}
      <Modal open={commentsOpen} onClose={() => setCommentsOpen(false)} title={t('collab.tabs.comments')} size="lg">
        <div className="space-y-4">
          {selectedChapter && (
            <p className="text-sm text-text-tertiary">
              {t('collab.chapterLabel', { num: selectedChapter.chapterNumber, title: selectedChapter.title || '' })}
            </p>
          )}
          {/* Comment list */}
          <div className="max-h-64 overflow-y-auto space-y-3">
            {comments.length === 0 ? (
              <p className="text-center text-text-tertiary text-sm py-4">{t('collab.empty.noComments', { defaultValue: '暂无评论' })}</p>
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
              placeholder={t('collab.placeholder.comment', { defaultValue: '输入评论...' })}
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
              {t('collab.actions.send')}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

// ==================== Sub-components ====================

function CommentItem({ comment, depth }: { comment: CommentResponse; depth: number }) {
  const { t } = useTranslation();
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
              <span className="text-xs text-green">{t('collab.comment.resolved', { defaultValue: '已解决' })}</span>
            )}
          </div>
          {comment.sourceText && (
            <div className="mt-1 text-xs text-text-tertiary bg-surface-secondary px-2 py-1 rounded">
              {t('collab.comment.sourceText', { defaultValue: '原文' })}: {comment.sourceText}
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
  chapterPage: number;
  chapterTotalPages: number;
  memberPage: number;
  memberTotalPages: number;
  onChapterPageChange: (page: number) => void;
  onMemberPageChange: (page: number) => void;
  onBack: () => void;
  onAddChapter: () => void;
  onUploadNovel: () => void;
  onAssign: (chapter: ChapterTaskResponse) => void;
  onReview: (chapter: ChapterTaskResponse) => void;
  onComments: (chapter: ChapterTaskResponse) => void;
  onInvite: () => void;
  onGenerateInvite: () => void;
  onRemoveMember: (memberId: number) => void;
  t: ReturnType<typeof useTranslation>['t'];
}

function ProjectDetailView({
  projectId, projects, chapters, chaptersLoading, members, userRole,
  detailTab, setDetailTab, myProjectTasks,
  chapterPage, chapterTotalPages, memberPage, memberTotalPages,
  onChapterPageChange, onMemberPageChange,
  onBack, onAddChapter, onUploadNovel,
  onAssign, onReview, onComments, onInvite, onGenerateInvite, onRemoveMember,
  t,
}: ProjectDetailViewProps) {
  const navigate = useNavigate();
  const project = projects.find(p => p.id === projectId);
  if (!project) return null;

  const detailTabs: Tab[] = [
    { key: 'chapters', label: t('collab.tabs.chapters') },
    { key: 'members', label: t('collab.tabs.members') },
    { key: 'tasks', label: t('collab.tabs.myTasks') },
  ];

  const renderStatusBadge = (status: string) => {
    const config: Record<string, { label: string; color: Parameters<typeof Badge>[0]['color'] }> = {
      UNASSIGNED: { label: t('collab.taskStatus.pending'), color: 'gray' },
      TRANSLATING: { label: t('collab.taskStatus.translating'), color: 'blue' },
      SUBMITTED: { label: t('collab.taskStatus.submitted'), color: 'yellow' },
      REVIEWING: { label: t('collab.taskStatus.reviewing'), color: 'purple' },
      APPROVED: { label: t('collab.taskStatus.approved'), color: 'green' },
      REJECTED: { label: t('collab.taskStatus.rejected'), color: 'red' },
      COMPLETED: { label: t('collab.taskStatus.completed'), color: 'green' },
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
            {project.status === 'ACTIVE' ? t('collab.projectStatus.active', { defaultValue: '进行中' }) : project.status}
          </Badge>
        </div>
        <p className="text-sm text-text-tertiary ml-9">
          {getLangName(t, project.sourceLang)} → {getLangName(t, project.targetLang)} · {project.memberCount} {t('collab.fields.members', { defaultValue: '成员' })} · {project.totalChapters} {t('collab.fields.chapters', { defaultValue: '章节' })}
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
            <span className="text-sm font-medium text-text-primary">{t('collab.chapterList', { defaultValue: '章节列表' })}</span>
            {canManage && (
              <div className="flex items-center gap-2">
                <button
                  onClick={onUploadNovel}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
                  title={t('collab.uploadNovel.tooltip', { defaultValue: '上传小说文件，自动拆分章节' })}
                >
                  <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                    <polyline points="17 8 12 3 7 8"/>
                    <line x1="12" y1="3" x2="12" y2="15"/>
                  </svg>
                  {t('collab.actions.uploadNovel')}
                </button>
                <button
                  onClick={onAddChapter}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
                >
                  <Plus className="w-3.5 h-3.5" /> {t('collab.actions.addChapter')}
                </button>
              </div>
            )}
          </div>
          {chaptersLoading ? (
            <div className="p-12 text-center text-text-tertiary">{t('common.loading')}</div>
          ) : chapters.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <BookOpen className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">{t('common.noData')}</p>
              <p className="text-text-tertiary text-sm mt-1">{canManage ? t('collab.empty.addChapterHint', { defaultValue: '点击"添加章节"开始' }) : t('collab.empty.waitOwner', { defaultValue: '等待所有者添加章节' })}</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border bg-surface-secondary">
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.chapter', { defaultValue: '章节' })}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.title', { defaultValue: '标题' })}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.status', { defaultValue: '状态' })}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.translator')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.reviewer', { defaultValue: '审者' })}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.progress', { defaultValue: '进度' })}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.action', { defaultValue: '操作' })}</th>
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
                              <UserCheck className="w-3 h-3" /> {t('collab.actions.assign')}
                            </button>
                          )}
                          <button
                            onClick={() => navigate(`/collab/workspace?chapterId=${chapter.id}&targetLang=${project.targetLang}`)}
                            className="flex items-center gap-1 px-2 py-1 text-xs text-white bg-accent rounded hover:bg-accent-hover transition-button"
                          >
                            <BookOpen className="w-3 h-3" /> {t('collab.actions.workspace')}
                          </button>
                          {chapter.status === 'TRANSLATING' && chapter.assigneeId && (
                            <button
                              onClick={() => navigate(`/collab/workspace?chapterId=${chapter.id}&targetLang=${project.targetLang}`)}
                              className="flex items-center gap-1 px-2 py-1 text-xs text-white bg-accent rounded hover:bg-accent-hover transition-button"
                            >
                              <BookOpen className="w-3 h-3" /> {t('collab.actions.translate')}
                            </button>
                          )}
                          {(chapter.status === 'SUBMITTED' || chapter.status === 'REVIEWING') && (
                            <button
                              onClick={() => onReview(chapter)}
                              className="flex items-center gap-1 px-2 py-1 text-xs text-white bg-green rounded hover:bg-green-hover transition-button"
                            >
                              <CheckCircle className="w-3 h-3" /> {t('collab.actions.review')}
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
          <Pagination page={chapterPage} totalPages={chapterTotalPages} onPageChange={onChapterPageChange} />
        </div>
      )}

      {/* Members Tab */}
      {detailTab === 'members' && (
        <div>
          <div className="flex items-center justify-between p-4 border-b border-border">
            <span className="text-sm font-medium text-text-primary">{t('collab.memberList', { defaultValue: '成员列表' })}</span>
            {canManage && (
              <div className="flex items-center gap-2">
                <button
                  onClick={onGenerateInvite}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
                  title={t('collab.inviteCode.generate', { defaultValue: '生成邀请码' })}
                >
                  <ClipboardList className="w-3.5 h-3.5" /> {t('collab.actions.invitationCode')}
                </button>
                <button
                  onClick={onInvite}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button"
                >
                  <Users className="w-3.5 h-3.5" /> {t('collab.actions.inviteMember')}
                </button>
              </div>
            )}
          </div>
          {members.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <Users className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">{t('collab.empty.noMembers', { defaultValue: '暂无成员' })}</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border bg-surface-secondary">
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.username', { defaultValue: '用户名' })}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.email')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.role')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.status', { defaultValue: '状态' })}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.joinTime', { defaultValue: '加入时间' })}</th>
                    {canManage && (
                      <th className="text-left px-4 py-3 text-xs font-medium text-text-tertiary uppercase tracking-wider">{t('collab.fields.action', { defaultValue: '操作' })}</th>
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
                          {member.role === 'OWNER' ? t('collab.roles.owner') : member.role === 'REVIEWER' ? t('collab.roles.reviewer') : t('collab.roles.translator')}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-text-tertiary text-xs">{member.inviteStatus}</td>
                      <td className="px-4 py-3 text-text-tertiary text-xs">{member.joinedTime || '-'}</td>
                      {canManage && member.role !== 'OWNER' && (
                        <td className="px-4 py-3">
                          <button
                            onClick={() => onRemoveMember(member.id)}
                            className="p-1 text-text-tertiary hover:text-red transition-button"
                            title={t('collab.action.removeMember', { defaultValue: '移除成员' })}
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
          <Pagination page={memberPage} totalPages={memberTotalPages} onPageChange={onMemberPageChange} />
        </div>
      )}

      {/* My Tasks Tab (within project) */}
      {detailTab === 'tasks' && (
        <div>
          <div className="p-4 border-b border-border">
            <span className="text-sm font-medium text-text-primary">{t('collab.tabs.myTasks')}</span>
          </div>
          {myProjectTasks.length === 0 ? (
            <div className="p-12 flex flex-col items-center">
              <ClipboardList className="w-12 h-12 text-text-tertiary mb-4" />
              <p className="text-text-secondary font-medium">{t('collab.empty.noTasks', { defaultValue: '暂无分配任务' })}</p>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {myProjectTasks.map(chapter => (
                <div key={chapter.id} className="p-4 flex items-center justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <BookOpen className="w-4 h-4 text-text-tertiary" />
                      <span className="text-sm font-medium text-text-primary truncate">
                        {t('collab.chapterLabel', { num: chapter.chapterNumber, title: chapter.title || '' })}
                      </span>
                      {renderStatusBadge(chapter.status)}
                    </div>
                    <p className="text-xs text-text-tertiary">
                      {chapter.assigneeName ? t('collab.roles.translator') : t('collab.roles.reviewer')} · {chapter.progress || 0}%
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
