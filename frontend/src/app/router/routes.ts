import type { RouteRecordRaw } from 'vue-router'

const ForbiddenView = () => import('@/app/views/ForbiddenView.vue')
const NotFoundView = () => import('@/app/views/NotFoundView.vue')
const LoginView = () => import('@/features/auth/views/LoginView.vue')
const RegisterView = () => import('@/features/auth/views/RegisterView.vue')
const DashboardView = () => import('@/features/dashboard/views/DashboardView.vue')
const CreateInterviewView = () => import('@/features/interview/views/CreateInterviewView.vue')
const InterviewSessionView = () => import('@/features/interview/views/InterviewSessionView.vue')
const HistoryView = () => import('@/features/history/views/HistoryView.vue')
const InterviewDetailView = () => import('@/features/interview/views/InterviewDetailView.vue')
const InterviewReportView = () => import('@/features/interview/views/InterviewReportView.vue')
const ProfileView = () => import('@/features/profile/views/ProfileView.vue')
const AdminQuestionListView = () => import('@/features/admin/questions/views/AdminQuestionListView.vue')
const AdminQuestionFormView = () => import('@/features/admin/questions/views/AdminQuestionFormView.vue')
const AdminQuestionDetailView = () => import('@/features/admin/questions/views/AdminQuestionDetailView.vue')
const AdminKnowledgeView = () => import('@/features/admin/knowledge/views/AdminKnowledgeView.vue')
const ProductShell = () => import('@/components/shell/ProductShell.vue')
const InterviewFocusShell = () => import('@/components/shell/InterviewFocusShell.vue')
const PublicShell = () => import('@/components/shell/PublicShell.vue')

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: PublicShell,
    meta: {
      anonymousOnly: true,
      shell: 'public',
      placeholderTitle: '登录',
      documentTitle: '登录',
    },
    children: [
      {
        path: '',
        name: 'login',
        component: LoginView,
        meta: {
          anonymousOnly: true,
          shell: 'public',
          placeholderTitle: '登录',
      documentTitle: '登录',
        },
      },
    ],
  },
  {
    path: '/register',
    component: PublicShell,
    meta: {
      anonymousOnly: true,
      shell: 'public',
      placeholderTitle: '注册',
      documentTitle: '注册',
    },
    children: [
      {
        path: '',
        name: 'register',
        component: RegisterView,
        meta: {
          anonymousOnly: true,
          shell: 'public',
          placeholderTitle: '注册',
      documentTitle: '注册',
        },
      },
    ],
  },
  {
    path: '/',
    component: ProductShell,
    meta: {
      requiresAuth: true,
      shell: 'product',
      placeholderTitle: '工作台',
      documentTitle: '工作台',
    },
    children: [
      {
        path: '',
        name: 'dashboard',
        component: DashboardView,
        meta: {
          requiresAuth: true,
          shell: 'product',
          placeholderTitle: '工作台',
      documentTitle: '工作台',
        },
      },
      {
        path: 'interviews/new',
        name: 'interview-create',
        component: CreateInterviewView,
        meta: {
          requiresAuth: true,
          shell: 'product',
          placeholderTitle: '开始面试',
      documentTitle: '开始面试',
        },
      },
      {
        path: 'interviews',
        name: 'interview-history',
        component: HistoryView,
        meta: {
          requiresAuth: true,
          shell: 'product',
          placeholderTitle: '历史面试',
      documentTitle: '历史',
        },
      },
      {
        path: 'interviews/:sessionId/report',
        name: 'interview-report',
        component: InterviewReportView,
        meta: {
          requiresAuth: true,
          shell: 'product',
          placeholderTitle: '面试报告',
      documentTitle: '报告',
        },
      },
      {
        path: 'interviews/:sessionId',
        name: 'interview-detail',
        component: InterviewDetailView,
        meta: {
          requiresAuth: true,
          shell: 'product',
          placeholderTitle: '面试详情',
      documentTitle: '复盘',
        },
      },
      {
        path: 'profile',
        name: 'profile',
        component: ProfileView,
        meta: {
          requiresAuth: true,
          shell: 'product',
          placeholderTitle: '账号',
      documentTitle: '账号',
        },
      },
      {
        path: 'admin/questions',
        name: 'admin-questions',
        component: AdminQuestionListView,
        meta: {
          requiresAuth: true,
          requiresAdmin: true,
          shell: 'product',
          placeholderTitle: '题库',
      documentTitle: '题库管理',
        },
      },
      {
        path: 'admin/questions/new',
        name: 'admin-question-create',
        component: AdminQuestionFormView,
        meta: {
          requiresAuth: true,
          requiresAdmin: true,
          shell: 'product',
          placeholderTitle: '新建题目',
      documentTitle: '新建题目',
        },
      },
      {
        path: 'admin/questions/:questionId/edit',
        name: 'admin-question-edit',
        component: AdminQuestionFormView,
        meta: {
          requiresAuth: true,
          requiresAdmin: true,
          shell: 'product',
          placeholderTitle: '编辑题目',
      documentTitle: '编辑题目',
        },
      },
      {
        path: 'admin/questions/:questionId',
        name: 'admin-question-detail',
        component: AdminQuestionDetailView,
        meta: {
          requiresAuth: true,
          requiresAdmin: true,
          shell: 'product',
          placeholderTitle: '题目详情',
      documentTitle: '题目详情',
        },
      },
      {
        path: 'admin/knowledge',
        name: 'admin-knowledge',
        component: AdminKnowledgeView,
        meta: {
          requiresAuth: true,
          requiresAdmin: true,
          shell: 'product',
          placeholderTitle: '知识文档',
      documentTitle: '知识文档',
        },
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: ForbiddenView,
        meta: {
          requiresAuth: true,
          shell: 'product',
          placeholderTitle: '权限不足',
      documentTitle: '权限不足',
        },
      },
    ],
  },
  {
    path: '/interview',
    component: InterviewFocusShell,
    meta: {
      requiresAuth: true,
      shell: 'interview',
      placeholderTitle: '面试进行中',
      documentTitle: '面试进行中',
    },
    children: [
      {
        path: '',
        name: 'interview-session',
        component: InterviewSessionView,
        meta: {
          requiresAuth: true,
          shell: 'interview',
          placeholderTitle: '面试进行中',
      documentTitle: '面试进行中',
        },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: {
      shell: 'public',
      placeholderTitle: '页面不存在',
      documentTitle: '页面不存在',
    },
  },
]
