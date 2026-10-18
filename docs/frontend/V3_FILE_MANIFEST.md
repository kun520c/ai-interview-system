# V3 文件清单

相对原工作区的当前前端快照，共修改 30 个生产展示文件、2 个测试文件，新增 1 个 QA 脚本。没有修改生产业务 composable、API、router、store、storage 或后端文件。文件完整性哈希与前端差异补丁位于 `v3/`。

## 修改文件

| 文件（frontend/ 下） | 用途 |
|---|---|
| src/app/styles/tokens.css | V3 颜色、表面、圆角、阴影和布局尺寸，沿用原变量入口 |
| src/app/styles/shells.css | 产品侧栏、认证双栏、专注面试双栏与手机 dock 布局 |
| src/components/shell/InterviewFocusShell.vue | 面试工作室品牌副标识 |
| src/components/shell/ProductShell.vue | 导航分组、桌面路径栏、工作室品牌 |
| src/components/shell/SessionSplitWorkspace.vue | 双栏的步骤标记和视觉标题 |
| src/components/ui/BaseButton.vue | 按钮高度、层次和 hover，保留 loading / disabled |
| src/components/ui/BaseInput.vue | 一致输入高度与轻阴影 |
| src/components/ui/BaseTextarea.vue | 一致输入样式与错误边界 |
| src/components/ui/EmptyState.vue | 共用图标、说明和真实操作的空状态 |
| src/components/ui/PageHeader.vue | 标题与次操作对齐，窄屏换行 |
| src/components/ui/ProductMark.vue | 图标颜色 token，适配蓝色品牌底 |
| src/components/ui/StatusNotice.vue | 真实反馈的语义边界和长文本换行 |
| src/features/admin/knowledge/views/AdminKnowledgeView.vue | 上传分组、文件选择器和响应式表单 |
| src/features/admin/questions/views/AdminQuestionDetailView.vue | 题目元信息与内容的统一表面 |
| src/features/admin/questions/views/AdminQuestionFormView.vue | 主表单、评分点和底部操作区密度 |
| src/features/admin/questions/views/AdminQuestionListView.vue | 筛选器、表头、行 hover 和手机密度 |
| src/features/dashboard/components/DashboardInterviewFlow.vue | 低优先级流程说明与步骤样式 |
| src/features/dashboard/components/DashboardRecentList.vue | 最近记录层级与全部记录入口 |
| src/features/dashboard/views/DashboardView.vue | 问候、主训练区、开始／继续视觉中心 |
| src/features/history/components/HistoryRow.vue | 历史行的统一间距 |
| src/features/interview/components/DifficultyCard.vue | 三种平等中文名称、选择／焦点样式 |
| src/features/interview/components/InterviewAnswerPane.vue | 作答工具行、大面积输入与简短引导 |
| src/features/interview/components/InterviewEvaluationPane.vue | 总分、评级、真实五维细条、反馈分组 |
| src/features/interview/components/InterviewProgress.vue | 进度样式与长知识点换行 |
| src/features/interview/components/InterviewQuestionPane.vue | 题目阅读排版与已提交答案 |
| src/features/interview/components/InterviewSessionDock.vue | 当前操作上下文、对齐与手机可达性 |
| src/features/interview/components/InterviewTimelineItem.vue | 问答证据表面与折叠状态标记 |
| src/features/interview/views/CreateInterviewView.vue | 难度选择页与恢复事实的布局 |
| src/features/interview/views/InterviewReportView.vue | 摘要、优弱项、行动建议、证据层次 |
| src/features/profile/views/ProfileView.vue | 账号信息和密码表单分组样式 |
| tests/DifficultyCard.spec.ts | 新增 3 种中文名称与枚举／无默认选中测试 |
| tests/InterviewEvaluationPane.spec.ts | 新增真实细条边界、服务端总分与装饰隐藏测试 |

登录、注册、HistoryView、InterviewDetailView 通过共用 shell、基础控件和复用评价／证据组件完成视觉统一，其自身业务文件不需要修改。

## 新增文件与证据

| 文件 | 用途 |
|---|---|
| frontend/scripts/v3-visual-check.mjs | 三尺寸浏览器截图、DOM 边界和 Mock 状态回归；不进入生产代码 |
| docs/frontend/V3_OPEN_SOURCE_RESEARCH.md | 五个项目的官方源码、演示、借鉴与拒绝项、许可证 |
| docs/frontend/V3_DESIGN_SYSTEM.md | V3 Tokens、组件状态、页面层级、响应式与 V2 差异 |
| docs/frontend/V3_VERIFICATION.md | 实际测试结果、环境边界、浏览器记录与复现步骤 |
| docs/frontend/V3_FILE_MANIFEST.md | 本清单 |
| docs/frontend/v3/COMPARISON.html | 可离线打开的截图对照页 |
| docs/frontend/v3/change-manifest.json | 修改路径和前后 SHA-256 |
| docs/frontend/v3/V3_SOURCE.patch | 相对原前端快照的可审查补丁，未自动应用回原工作区 |
| docs/frontend/v3/before/ | 48 张基线截图与浏览器日志 |
| docs/frontend/v3/after/ | 83 张自动验收截图、1 张人工交互截图与浏览器日志 |
| docs/frontend/v3/references/ | 可保存的官方页面实际截图和观察说明 |
| docs/frontend/v3/verification/ | 类型、Lint、单元测试、构建及浏览器执行日志 |

原有 V2 文档、测试和业务代码均保留。没有新增 npm 依赖，也没有 git commit / push。

## GitHub 发布归档说明（2026-10-18）

上述截图数量与补丁路径描述原始完整交付包。公开仓库精选 16 张 V2、24 张 V3 和 5 张真实后端 / 本地模型截图；完整矩阵、第三方参考截图及两个重复源码补丁仍在本地原位保留。公开日志仅脱敏机器路径，结果、断言和失败记录不变。最终源码请审查 Git Diff，当前验收以 V3_BLOCKER_FIX_20261018.md 为准。
