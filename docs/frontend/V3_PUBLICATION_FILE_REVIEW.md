# V3 发布文件审查清单

审查输入为 266 个暂存路径。以下逐项记录用途和发布决定；“本地保留”只从发布索引移除，磁盘文件不删除。原始完整暂存 Diff 保留在忽略目录 `target/publication/original-staged.patch`。

| 文件 | 用途 | 发布决定 |
|---|---|---|
| `docs/README.md` | V3当前和历史报告导航 | 纳入提交 |
| `docs/frontend/V3_BLOCKER_FIX_20261018.md` | 设计、验证或来源说明：V3_BLOCKER_FIX_20261018.md | 纳入提交 |
| `docs/frontend/V3_DESIGN_SYSTEM.md` | 设计、验证或来源说明：V3_DESIGN_SYSTEM.md | 纳入提交 |
| `docs/frontend/V3_FILE_MANIFEST.md` | 设计、验证或来源说明：V3_FILE_MANIFEST.md | 纳入提交 |
| `docs/frontend/V3_INTEGRATION_20261018.md` | 设计、验证或来源说明：V3_INTEGRATION_20261018.md | 纳入提交 |
| `docs/frontend/V3_OPEN_SOURCE_RESEARCH.md` | 设计、验证或来源说明：V3_OPEN_SOURCE_RESEARCH.md | 纳入提交 |
| `docs/frontend/V3_VERIFICATION.md` | 设计、验证或来源说明：V3_VERIFICATION.md | 纳入提交 |
| `docs/frontend/v3/.gitattributes` | 归档证据换行和diff空白规则 | 纳入提交 |
| `docs/frontend/v3/COMPARISON.html` | 仓库精选桌面/手机V2与V3对照，明确Mock证据边界 | 纳入提交 |
| `docs/frontend/v3/V3_SOURCE.patch` | 原始前端交付补丁，本地保留；公开审查使用Git Diff避免重复源码 | 本地保留 |
| `docs/frontend/v3/after/MOCK-manual-evaluation.png` | V3视觉验收（Mock API）：MOCK-manual-evaluation.png | 本地保留 |
| `docs/frontend/v3/after/admin-knowledge-1024.png` | V3视觉验收（Mock API）：admin-knowledge-1024.png | 本地保留 |
| `docs/frontend/v3/after/admin-knowledge-1440.png` | V3视觉验收（Mock API）：admin-knowledge-1440.png | 本地保留 |
| `docs/frontend/v3/after/admin-knowledge-390.png` | V3视觉验收（Mock API）：admin-knowledge-390.png | 本地保留 |
| `docs/frontend/v3/after/admin-question-form-1024.png` | V3视觉验收（Mock API）：admin-question-form-1024.png | 本地保留 |
| `docs/frontend/v3/after/admin-question-form-1440.png` | V3视觉验收（Mock API）：admin-question-form-1440.png | 本地保留 |
| `docs/frontend/v3/after/admin-question-form-390.png` | V3视觉验收（Mock API）：admin-question-form-390.png | 本地保留 |
| `docs/frontend/v3/after/admin-questions-1024.png` | V3视觉验收（Mock API）：admin-questions-1024.png | 本地保留 |
| `docs/frontend/v3/after/admin-questions-1440.png` | V3视觉验收（Mock API）：admin-questions-1440.png | 纳入提交 |
| `docs/frontend/v3/after/admin-questions-390.png` | V3视觉验收（Mock API）：admin-questions-390.png | 纳入提交 |
| `docs/frontend/v3/after/auth-expired-1440.png` | V3视觉验收（Mock API）：auth-expired-1440.png | 本地保留 |
| `docs/frontend/v3/after/auth-expired-390.png` | V3视觉验收（Mock API）：auth-expired-390.png | 本地保留 |
| `docs/frontend/v3/after/browser-log.json` | 完整原始Mock截图矩阵和overflow/页面错误检查记录（截图仅公开精选） | 纳入提交 |
| `docs/frontend/v3/after/create-1024.png` | V3视觉验收（Mock API）：create-1024.png | 本地保留 |
| `docs/frontend/v3/after/create-1440.png` | V3视觉验收（Mock API）：create-1440.png | 纳入提交 |
| `docs/frontend/v3/after/create-390.png` | V3视觉验收（Mock API）：create-390.png | 纳入提交 |
| `docs/frontend/v3/after/dashboard-empty-1440.png` | V3视觉验收（Mock API）：dashboard-empty-1440.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-empty-390.png` | V3视觉验收（Mock API）：dashboard-empty-390.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-error-1440.png` | V3视觉验收（Mock API）：dashboard-error-1440.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-error-390.png` | V3视觉验收（Mock API）：dashboard-error-390.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-loading-1440.png` | V3视觉验收（Mock API）：dashboard-loading-1440.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-loading-390.png` | V3视觉验收（Mock API）：dashboard-loading-390.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-resume-1024.png` | V3视觉验收（Mock API）：dashboard-resume-1024.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-resume-1440.png` | V3视觉验收（Mock API）：dashboard-resume-1440.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-resume-390.png` | V3视觉验收（Mock API）：dashboard-resume-390.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-start-1024.png` | V3视觉验收（Mock API）：dashboard-start-1024.png | 本地保留 |
| `docs/frontend/v3/after/dashboard-start-1440.png` | V3视觉验收（Mock API）：dashboard-start-1440.png | 纳入提交 |
| `docs/frontend/v3/after/dashboard-start-390.png` | V3视觉验收（Mock API）：dashboard-start-390.png | 纳入提交 |
| `docs/frontend/v3/after/difficulty-keyboard-focus-1440.png` | V3视觉验收（Mock API）：difficulty-keyboard-focus-1440.png | 纳入提交 |
| `docs/frontend/v3/after/difficulty-keyboard-focus-390.png` | V3视觉验收（Mock API）：difficulty-keyboard-focus-390.png | 纳入提交 |
| `docs/frontend/v3/after/history-1024.png` | V3视觉验收（Mock API）：history-1024.png | 本地保留 |
| `docs/frontend/v3/after/history-1440.png` | V3视觉验收（Mock API）：history-1440.png | 本地保留 |
| `docs/frontend/v3/after/history-390.png` | V3视觉验收（Mock API）：history-390.png | 本地保留 |
| `docs/frontend/v3/after/history-empty-1440.png` | V3视觉验收（Mock API）：history-empty-1440.png | 本地保留 |
| `docs/frontend/v3/after/history-empty-390.png` | V3视觉验收（Mock API）：history-empty-390.png | 本地保留 |
| `docs/frontend/v3/after/login-1024.png` | V3视觉验收（Mock API）：login-1024.png | 本地保留 |
| `docs/frontend/v3/after/login-1440.png` | V3视觉验收（Mock API）：login-1440.png | 纳入提交 |
| `docs/frontend/v3/after/login-390.png` | V3视觉验收（Mock API）：login-390.png | 纳入提交 |
| `docs/frontend/v3/after/mobile-menu-390.png` | V3视觉验收（Mock API）：mobile-menu-390.png | 纳入提交 |
| `docs/frontend/v3/after/profile-1024.png` | V3视觉验收（Mock API）：profile-1024.png | 本地保留 |
| `docs/frontend/v3/after/profile-1440.png` | V3视觉验收（Mock API）：profile-1440.png | 本地保留 |
| `docs/frontend/v3/after/profile-390.png` | V3视觉验收（Mock API）：profile-390.png | 本地保留 |
| `docs/frontend/v3/after/register-1024.png` | V3视觉验收（Mock API）：register-1024.png | 本地保留 |
| `docs/frontend/v3/after/register-1440.png` | V3视觉验收（Mock API）：register-1440.png | 本地保留 |
| `docs/frontend/v3/after/register-390.png` | V3视觉验收（Mock API）：register-390.png | 本地保留 |
| `docs/frontend/v3/after/report-1024.png` | V3视觉验收（Mock API）：report-1024.png | 本地保留 |
| `docs/frontend/v3/after/report-1440.png` | V3视觉验收（Mock API）：report-1440.png | 纳入提交 |
| `docs/frontend/v3/after/report-390.png` | V3视觉验收（Mock API）：report-390.png | 纳入提交 |
| `docs/frontend/v3/after/report-failed-1440.png` | V3视觉验收（Mock API）：report-failed-1440.png | 本地保留 |
| `docs/frontend/v3/after/report-failed-390.png` | V3视觉验收（Mock API）：report-failed-390.png | 纳入提交 |
| `docs/frontend/v3/after/report-generating-1440.png` | V3视觉验收（Mock API）：report-generating-1440.png | 本地保留 |
| `docs/frontend/v3/after/report-generating-390.png` | V3视觉验收（Mock API）：report-generating-390.png | 本地保留 |
| `docs/frontend/v3/after/report-not-started-1440.png` | V3视觉验收（Mock API）：report-not-started-1440.png | 本地保留 |
| `docs/frontend/v3/after/report-not-started-390.png` | V3视觉验收（Mock API）：report-not-started-390.png | 本地保留 |
| `docs/frontend/v3/after/report-recovered-1440.png` | V3视觉验收（Mock API）：report-recovered-1440.png | 本地保留 |
| `docs/frontend/v3/after/report-recovered-390.png` | V3视觉验收（Mock API）：report-recovered-390.png | 本地保留 |
| `docs/frontend/v3/after/session-answering-1024.png` | V3视觉验收（Mock API）：session-answering-1024.png | 本地保留 |
| `docs/frontend/v3/after/session-answering-1440.png` | V3视觉验收（Mock API）：session-answering-1440.png | 纳入提交 |
| `docs/frontend/v3/after/session-answering-390.png` | V3视觉验收（Mock API）：session-answering-390.png | 纳入提交 |
| `docs/frontend/v3/after/session-blocked-1024.png` | V3视觉验收（Mock API）：session-blocked-1024.png | 本地保留 |
| `docs/frontend/v3/after/session-blocked-1440.png` | V3视觉验收（Mock API）：session-blocked-1440.png | 纳入提交 |
| `docs/frontend/v3/after/session-blocked-390.png` | V3视觉验收（Mock API）：session-blocked-390.png | 纳入提交 |
| `docs/frontend/v3/after/session-created-1440.png` | V3视觉验收（Mock API）：session-created-1440.png | 本地保留 |
| `docs/frontend/v3/after/session-created-390.png` | V3视觉验收（Mock API）：session-created-390.png | 本地保留 |
| `docs/frontend/v3/after/session-evaluation-1024.png` | V3视觉验收（Mock API）：session-evaluation-1024.png | 本地保留 |
| `docs/frontend/v3/after/session-evaluation-1440.png` | V3视觉验收（Mock API）：session-evaluation-1440.png | 纳入提交 |
| `docs/frontend/v3/after/session-evaluation-390.png` | V3视觉验收（Mock API）：session-evaluation-390.png | 纳入提交 |
| `docs/frontend/v3/after/session-feedback-bottom-1024.png` | V3视觉验收（Mock API）：session-feedback-bottom-1024.png | 本地保留 |
| `docs/frontend/v3/after/session-feedback-bottom-1440.png` | V3视觉验收（Mock API）：session-feedback-bottom-1440.png | 本地保留 |
| `docs/frontend/v3/after/session-feedback-bottom-390.png` | V3视觉验收（Mock API）：session-feedback-bottom-390.png | 本地保留 |
| `docs/frontend/v3/after/session-feedback-reading-1024.png` | V3视觉验收（Mock API）：session-feedback-reading-1024.png | 本地保留 |
| `docs/frontend/v3/after/session-feedback-reading-1440.png` | V3视觉验收（Mock API）：session-feedback-reading-1440.png | 本地保留 |
| `docs/frontend/v3/after/session-feedback-reading-390.png` | V3视觉验收（Mock API）：session-feedback-reading-390.png | 本地保留 |
| `docs/frontend/v3/after/session-finish-gate-1440.png` | V3视觉验收（Mock API）：session-finish-gate-1440.png | 本地保留 |
| `docs/frontend/v3/after/session-finish-gate-390.png` | V3视觉验收（Mock API）：session-finish-gate-390.png | 本地保留 |
| `docs/frontend/v3/after/session-followup-1024.png` | V3视觉验收（Mock API）：session-followup-1024.png | 本地保留 |
| `docs/frontend/v3/after/session-followup-1440.png` | V3视觉验收（Mock API）：session-followup-1440.png | 纳入提交 |
| `docs/frontend/v3/after/session-followup-390.png` | V3视觉验收（Mock API）：session-followup-390.png | 纳入提交 |
| `docs/frontend/v3/after/session-long-answer-1440.png` | V3视觉验收（Mock API）：session-long-answer-1440.png | 纳入提交 |
| `docs/frontend/v3/after/session-long-answer-390.png` | V3视觉验收（Mock API）：session-long-answer-390.png | 纳入提交 |
| `docs/frontend/v3/after/session-pending-1024.png` | V3视觉验收（Mock API）：session-pending-1024.png | 本地保留 |
| `docs/frontend/v3/after/session-pending-1440.png` | V3视觉验收（Mock API）：session-pending-1440.png | 本地保留 |
| `docs/frontend/v3/after/session-pending-390.png` | V3视觉验收（Mock API）：session-pending-390.png | 本地保留 |
| `docs/frontend/v3/after/session-resumed-1440.png` | V3视觉验收（Mock API）：session-resumed-1440.png | 本地保留 |
| `docs/frontend/v3/after/session-resumed-390.png` | V3视觉验收（Mock API）：session-resumed-390.png | 本地保留 |
| `docs/frontend/v3/before/admin-knowledge-1024.png` | V2视觉基线（Mock API）：admin-knowledge-1024.png | 本地保留 |
| `docs/frontend/v3/before/admin-knowledge-1440.png` | V2视觉基线（Mock API）：admin-knowledge-1440.png | 本地保留 |
| `docs/frontend/v3/before/admin-knowledge-390.png` | V2视觉基线（Mock API）：admin-knowledge-390.png | 本地保留 |
| `docs/frontend/v3/before/admin-question-form-1024.png` | V2视觉基线（Mock API）：admin-question-form-1024.png | 本地保留 |
| `docs/frontend/v3/before/admin-question-form-1440.png` | V2视觉基线（Mock API）：admin-question-form-1440.png | 本地保留 |
| `docs/frontend/v3/before/admin-question-form-390.png` | V2视觉基线（Mock API）：admin-question-form-390.png | 本地保留 |
| `docs/frontend/v3/before/admin-questions-1024.png` | V2视觉基线（Mock API）：admin-questions-1024.png | 本地保留 |
| `docs/frontend/v3/before/admin-questions-1440.png` | V2视觉基线（Mock API）：admin-questions-1440.png | 纳入提交 |
| `docs/frontend/v3/before/admin-questions-390.png` | V2视觉基线（Mock API）：admin-questions-390.png | 纳入提交 |
| `docs/frontend/v3/before/browser-log.json` | 完整原始Mock截图矩阵和overflow/页面错误检查记录（截图仅公开精选） | 纳入提交 |
| `docs/frontend/v3/before/create-1024.png` | V2视觉基线（Mock API）：create-1024.png | 本地保留 |
| `docs/frontend/v3/before/create-1440.png` | V2视觉基线（Mock API）：create-1440.png | 纳入提交 |
| `docs/frontend/v3/before/create-390.png` | V2视觉基线（Mock API）：create-390.png | 纳入提交 |
| `docs/frontend/v3/before/dashboard-resume-1024.png` | V2视觉基线（Mock API）：dashboard-resume-1024.png | 本地保留 |
| `docs/frontend/v3/before/dashboard-resume-1440.png` | V2视觉基线（Mock API）：dashboard-resume-1440.png | 本地保留 |
| `docs/frontend/v3/before/dashboard-resume-390.png` | V2视觉基线（Mock API）：dashboard-resume-390.png | 本地保留 |
| `docs/frontend/v3/before/dashboard-start-1024.png` | V2视觉基线（Mock API）：dashboard-start-1024.png | 本地保留 |
| `docs/frontend/v3/before/dashboard-start-1440.png` | V2视觉基线（Mock API）：dashboard-start-1440.png | 纳入提交 |
| `docs/frontend/v3/before/dashboard-start-390.png` | V2视觉基线（Mock API）：dashboard-start-390.png | 纳入提交 |
| `docs/frontend/v3/before/history-1024.png` | V2视觉基线（Mock API）：history-1024.png | 本地保留 |
| `docs/frontend/v3/before/history-1440.png` | V2视觉基线（Mock API）：history-1440.png | 本地保留 |
| `docs/frontend/v3/before/history-390.png` | V2视觉基线（Mock API）：history-390.png | 本地保留 |
| `docs/frontend/v3/before/login-1024.png` | V2视觉基线（Mock API）：login-1024.png | 本地保留 |
| `docs/frontend/v3/before/login-1440.png` | V2视觉基线（Mock API）：login-1440.png | 纳入提交 |
| `docs/frontend/v3/before/login-390.png` | V2视觉基线（Mock API）：login-390.png | 纳入提交 |
| `docs/frontend/v3/before/profile-1024.png` | V2视觉基线（Mock API）：profile-1024.png | 本地保留 |
| `docs/frontend/v3/before/profile-1440.png` | V2视觉基线（Mock API）：profile-1440.png | 本地保留 |
| `docs/frontend/v3/before/profile-390.png` | V2视觉基线（Mock API）：profile-390.png | 本地保留 |
| `docs/frontend/v3/before/register-1024.png` | V2视觉基线（Mock API）：register-1024.png | 本地保留 |
| `docs/frontend/v3/before/register-1440.png` | V2视觉基线（Mock API）：register-1440.png | 本地保留 |
| `docs/frontend/v3/before/register-390.png` | V2视觉基线（Mock API）：register-390.png | 本地保留 |
| `docs/frontend/v3/before/report-1024.png` | V2视觉基线（Mock API）：report-1024.png | 本地保留 |
| `docs/frontend/v3/before/report-1440.png` | V2视觉基线（Mock API）：report-1440.png | 纳入提交 |
| `docs/frontend/v3/before/report-390.png` | V2视觉基线（Mock API）：report-390.png | 纳入提交 |
| `docs/frontend/v3/before/session-answering-1024.png` | V2视觉基线（Mock API）：session-answering-1024.png | 本地保留 |
| `docs/frontend/v3/before/session-answering-1440.png` | V2视觉基线（Mock API）：session-answering-1440.png | 纳入提交 |
| `docs/frontend/v3/before/session-answering-390.png` | V2视觉基线（Mock API）：session-answering-390.png | 纳入提交 |
| `docs/frontend/v3/before/session-blocked-1024.png` | V2视觉基线（Mock API）：session-blocked-1024.png | 本地保留 |
| `docs/frontend/v3/before/session-blocked-1440.png` | V2视觉基线（Mock API）：session-blocked-1440.png | 本地保留 |
| `docs/frontend/v3/before/session-blocked-390.png` | V2视觉基线（Mock API）：session-blocked-390.png | 本地保留 |
| `docs/frontend/v3/before/session-evaluation-1024.png` | V2视觉基线（Mock API）：session-evaluation-1024.png | 本地保留 |
| `docs/frontend/v3/before/session-evaluation-1440.png` | V2视觉基线（Mock API）：session-evaluation-1440.png | 纳入提交 |
| `docs/frontend/v3/before/session-evaluation-390.png` | V2视觉基线（Mock API）：session-evaluation-390.png | 纳入提交 |
| `docs/frontend/v3/before/session-followup-1024.png` | V2视觉基线（Mock API）：session-followup-1024.png | 本地保留 |
| `docs/frontend/v3/before/session-followup-1440.png` | V2视觉基线（Mock API）：session-followup-1440.png | 纳入提交 |
| `docs/frontend/v3/before/session-followup-390.png` | V2视觉基线（Mock API）：session-followup-390.png | 纳入提交 |
| `docs/frontend/v3/before/session-pending-1024.png` | V2视觉基线（Mock API）：session-pending-1024.png | 本地保留 |
| `docs/frontend/v3/before/session-pending-1440.png` | V2视觉基线（Mock API）：session-pending-1440.png | 本地保留 |
| `docs/frontend/v3/before/session-pending-390.png` | V2视觉基线（Mock API）：session-pending-390.png | 本地保留 |
| `docs/frontend/v3/blocker-fix/B1_B2_FINAL_SOURCE.patch` | 修复验收时补丁，本地保留；公开审查使用Git Diff避免重复源码 | 本地保留 |
| `docs/frontend/v3/blocker-fix/RESULT.json` | B1/B2最终验收证据：RESULT；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/browser-full.json` | B1/B2最终验收证据：browser-full；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/candidate-final-export.json` | B1/B2最终验收证据：candidate-final-export；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/change-list.json` | B1/B2最终验收证据：change-list；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/clean-environment-interruption.json` | B1/B2最终验收证据：clean-environment-interruption；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/clean-maven.json` | B1/B2最终验收证据：clean-maven；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/consistency-after-regression.json` | B1/B2最终验收证据：consistency-after-regression；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/consistency-before-regression.json` | B1/B2最终验收证据：consistency-before-regression；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/early-focused-failure.json` | B1/B2最终验收证据：early-focused-failure；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/early-full-failure.json` | B1/B2最终验收证据：early-full-failure；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/final-source-index-proof.json` | B1/B2最终验收证据：final-source-index-proof；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/focused-tests.json` | B1/B2最终验收证据：focused-tests；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/frontend-build.txt` | B1/B2最终验收证据：frontend-build；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/frontend-exit-codes.json` | B1/B2最终验收证据：frontend-exit-codes；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/frontend-lint.txt` | B1/B2最终验收证据：frontend-lint；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/frontend-test-unit.txt` | B1/B2最终验收证据：frontend-test-unit；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/frontend-typecheck.txt` | B1/B2最终验收证据：frontend-typecheck；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/integration-maven.json` | B1/B2最终验收证据：integration-maven；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/invalid-model-http.json` | B1/B2最终验收证据：invalid-model-http；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/original-workspaces-preserved.json` | B1/B2最终验收证据：original-workspaces-preserved；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/provider-summary.json` | B1/B2最终验收证据：provider-summary；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/resource-audit.json` | B1/B2最终验收证据：resource-audit；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/services-stopped.json` | B1/B2最终验收证据：services-stopped；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/blocker-fix/staging-preservation.json` | B1/B2最终验收证据：staging-preservation；早期失败另行标记，未替代最终结果 | 纳入提交 |
| `docs/frontend/v3/change-manifest.json` | 原V3交付前后哈希清单；不声称与发布路径配置调整后的QA脚本字节一致 | 纳入提交 |
| `docs/frontend/v3/integration/RESULT.json` | 最初集成阶段的历史证据：RESULT；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/after-patch-check.json` | 最初集成阶段的历史证据：after-patch-check；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/attempt-01-result.json` | 最初集成阶段的历史证据：attempt-01-result；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/attempt-02-result.json` | 最初集成阶段的历史证据：attempt-02-result；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/attempt-03-result.json` | 最初集成阶段的历史证据：attempt-03-result；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/blocker-http.json` | 最初集成阶段的历史证据：blocker-http；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/change-list.json` | 最初集成阶段的历史证据：change-list；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/consistency-after-regression.json` | 最初集成阶段的历史证据：consistency-after-regression；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/consistency.json` | 最初集成阶段的历史证据：consistency；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/final-source-integrity.json` | 最初集成阶段的历史证据：final-source-integrity；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/frontend-build.txt` | 最初集成阶段的历史证据：frontend-build；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/frontend-exit-codes.json` | 最初集成阶段的历史证据：frontend-exit-codes；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/frontend-lint.txt` | 最初集成阶段的历史证据：frontend-lint；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/frontend-test-unit.txt` | 最初集成阶段的历史证据：frontend-test-unit；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/frontend-typecheck.txt` | 最初集成阶段的历史证据：frontend-typecheck；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/index-integrity.json` | 最初集成阶段的历史证据：index-integrity；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/known-blocker.json` | 最初集成阶段的历史证据：known-blocker；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/line-ending-analysis.json` | 最初集成阶段的历史证据：line-ending-analysis；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/local-replay-resource.json` | 最初集成阶段的历史证据：local-replay-resource；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/maven-summary-with-local-archive.json` | 最初集成阶段的历史证据：maven-summary-with-local-archive；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/maven-summary.json` | 最初集成阶段的历史证据：maven-summary；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/proposed-model-error-classification.patch` | 最初集成阶段的历史证据：proposed-model-error-classification；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/provider-summary.json` | 最初集成阶段的历史证据：provider-summary；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/screenshots/REAL_BACKEND_LOCAL_AI-admin-knowledge.png` | 真实Spring Boot/独立MySQL和本地模型的代表截图：REAL_BACKEND_LOCAL_AI-admin-knowledge.png | 纳入提交 |
| `docs/frontend/v3/integration/screenshots/REAL_BACKEND_LOCAL_AI-admin-question-list.png` | 真实Spring Boot/独立MySQL和本地模型的代表截图：REAL_BACKEND_LOCAL_AI-admin-question-list.png | 纳入提交 |
| `docs/frontend/v3/integration/screenshots/REAL_BACKEND_LOCAL_AI-final-first-question.png` | 真实Spring Boot/独立MySQL和本地模型的代表截图：REAL_BACKEND_LOCAL_AI-final-first-question.png | 纳入提交 |
| `docs/frontend/v3/integration/screenshots/REAL_BACKEND_LOCAL_AI-final-report.png` | 真实Spring Boot/独立MySQL和本地模型的代表截图：REAL_BACKEND_LOCAL_AI-final-report.png | 纳入提交 |
| `docs/frontend/v3/integration/screenshots/REAL_BACKEND_LOCAL_AI-main-feedback-1.png` | 真实Spring Boot/独立MySQL和本地模型的代表截图：REAL_BACKEND_LOCAL_AI-main-feedback-1.png | 纳入提交 |
| `docs/frontend/v3/integration/service-safety-checks.json` | 最初集成阶段的历史证据：service-safety-checks；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/integration/workspace-preservation.json` | 最初集成阶段的历史证据：workspace-preservation；当前结论由B1/B2最终报告接续 | 纳入提交 |
| `docs/frontend/v3/references/README.md` | 设计、验证或来源说明：README.md | 纳入提交 |
| `docs/frontend/v3/references/pure-admin-browser.png` | 第三方官方页面参考截图；保留本地，公开仓库保留来源链接 | 本地保留 |
| `docs/frontend/v3/references/shadcn-vue-login.png` | 第三方官方页面参考截图；保留本地，公开仓库保留来源链接 | 本地保留 |
| `docs/frontend/v3/references/shadcn-vue-sidebar.png` | 第三方官方页面参考截图；保留本地，公开仓库保留来源链接 | 本地保留 |
| `docs/frontend/v3/verification/baseline-browser.log` | 原始V3 Mock视觉/工程验收记录：baseline-browser.log | 纳入提交 |
| `docs/frontend/v3/verification/baseline-unit-retry.log` | 原始V3 Mock视觉/工程验收记录：baseline-unit-retry.log | 纳入提交 |
| `docs/frontend/v3/verification/baseline-unit.log` | 原始V3 Mock视觉/工程验收记录：baseline-unit.log | 纳入提交 |
| `docs/frontend/v3/verification/integrity.json` | 原始V3 Mock视觉/工程验收记录：integrity.json | 纳入提交 |
| `docs/frontend/v3/verification/v3-browser.log` | 原始V3 Mock视觉/工程验收记录：v3-browser.log | 纳入提交 |
| `docs/frontend/v3/verification/v3-build.log` | 原始V3 Mock视觉/工程验收记录：v3-build.log | 纳入提交 |
| `docs/frontend/v3/verification/v3-lint.log` | 原始V3 Mock视觉/工程验收记录：v3-lint.log | 纳入提交 |
| `docs/frontend/v3/verification/v3-typecheck.log` | 原始V3 Mock视觉/工程验收记录：v3-typecheck.log | 纳入提交 |
| `docs/frontend/v3/verification/v3-unit.log` | 原始V3 Mock视觉/工程验收记录：v3-unit.log | 纳入提交 |
| `frontend/scripts/v3-visual-check.mjs` | Mock API视觉/响应式检查，不代表真实后端联调 | 纳入提交 |
| `frontend/src/app/styles/shells.css` | V3共用产品/认证/面试布局与响应式样式 | 纳入提交 |
| `frontend/src/app/styles/tokens.css` | V3统一颜色、字体、间距、边框与布局token | 纳入提交 |
| `frontend/src/components/shell/InterviewFocusShell.vue` | InterviewFocusShell：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/components/shell/ProductShell.vue` | ProductShell：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/components/shell/SessionSplitWorkspace.vue` | SessionSplitWorkspace：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/components/ui/BaseButton.vue` | BaseButton：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/components/ui/BaseInput.vue` | BaseInput：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/components/ui/BaseTextarea.vue` | BaseTextarea：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/components/ui/EmptyState.vue` | EmptyState：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/components/ui/PageHeader.vue` | PageHeader：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/components/ui/ProductMark.vue` | ProductMark：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/components/ui/StatusNotice.vue` | StatusNotice：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/admin/knowledge/views/AdminKnowledgeView.vue` | AdminKnowledgeView：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/admin/questions/views/AdminQuestionDetailView.vue` | AdminQuestionDetailView：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/admin/questions/views/AdminQuestionFormView.vue` | AdminQuestionFormView：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/admin/questions/views/AdminQuestionListView.vue` | AdminQuestionListView：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/dashboard/components/DashboardInterviewFlow.vue` | DashboardInterviewFlow：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/dashboard/components/DashboardRecentList.vue` | DashboardRecentList：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/dashboard/views/DashboardView.vue` | DashboardView：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/history/components/HistoryRow.vue` | HistoryRow：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/interview/components/DifficultyCard.vue` | DifficultyCard：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/interview/components/InterviewAnswerPane.vue` | InterviewAnswerPane：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/interview/components/InterviewEvaluationPane.vue` | InterviewEvaluationPane：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/interview/components/InterviewProgress.vue` | InterviewProgress：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/interview/components/InterviewQuestionPane.vue` | InterviewQuestionPane：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/interview/components/InterviewSessionDock.vue` | InterviewSessionDock：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/interview/components/InterviewTimelineItem.vue` | InterviewTimelineItem：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/interview/views/CreateInterviewView.vue` | CreateInterviewView：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/interview/views/InterviewReportView.vue` | InterviewReportView：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/src/features/profile/views/ProfileView.vue` | ProfileView：现有组件的V3模板/样式层级与响应式调整；原业务状态继续沿用 | 纳入提交 |
| `frontend/tests/DifficultyCard.spec.ts` | 可读难度名称、原枚举值和无默认radio选中约束 | 纳入提交 |
| `frontend/tests/InterviewEvaluationPane.spec.ts` | 原始五维分数、服务端总分及辅助装饰无障碍约束 | 纳入提交 |
| `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java` | 仅捕获明确的模型校验异常，沿用现有反馈重试及回退 | 纳入提交 |
| `src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java` | 客户端局部严格JSON评分解析，拒绝字符串及小数强转 | 纳入提交 |
| `src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java` | 模型输出的明确违规使用现有外部依赖异常；内部上下文错误仍为500 | 纳入提交 |
| `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevE06ReliabilityReplayTest.java` | 公开合成协议矩阵：原生判分、反馈重试、复用、回退及调用计数 | 纳入提交 |
| `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevFeedbackReliabilityTest.java` | 反馈可靠性和内部异常不触发外部重试/回退 | 纳入提交 |
| `src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClientTest.java` | 独立ObjectMapper及客户端响应解析约束 | 纳入提交 |
| `src/test/java/com/kun/aiinterview/interview/evaluation/prompt/FollowUpOutputCompletenessHardeningTest.java` | 保持完整性拒绝条件并匹配模型响应异常合同 | 纳入提交 |
| `src/test/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidatorTest.java` | 模型违规类型与内部调用错误区分 | 纳入提交 |
| `src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java` | 真实MVC/Security/MyBatis事务：502、边界分数、500/400/409、FAILED和幂等恢复 | 纳入提交 |
| `src/test/resources/evaluation/README.md` | 公开合成数据与既有captures/私有历史研究的证据边界 | 纳入提交 |
| `src/test/resources/evaluation/jev-reliability-synthetic-scenarios.json` | 六个公开编写的合成场景，不是历史成功率样本 | 纳入提交 |
| `tools/browser-e2e/BrowserE2eServer.java` | 隔离真实Spring Boot/MySQL启动及只读持久化快照；替换外部向量端口 | 纳入提交 |
| `tools/browser-e2e/README.md` | 隔离工具的配置、操作、安全边界及复现说明 | 纳入提交 |
| `tools/browser-e2e/acceptance.mjs` | 严格full真实浏览器流程、权限、失败恢复和502断言 | 纳入提交 |
| `tools/browser-e2e/environment.ps1` | 测试实例准备、端口和进程身份保护、快照及仅停止自建服务 | 纳入提交 |
| `tools/browser-e2e/fixtures.sql` | 独立测试库合成题库和知识数据；不用于生产入库 | 纳入提交 |
| `tools/browser-e2e/mock-provider.mjs` | 仅loopback确定性Embedding/Chat响应，显式本地模拟AI标记 | 纳入提交 |
| `tools/browser-e2e/regression.ps1` | 独立测试库上的完整Maven运行；不排除用例 | 纳入提交 |
| `tools/browser-e2e/verify-blocker.mjs` | 历史B1拒绝后的持久化安全诊断，仅用于原NOT_READY现场 | 纳入提交 |
| `tools/browser-e2e/verify.mjs` | 真实数据库快照与浏览器结果逐项一致性断言 | 纳入提交 |

新增发布资料：本清单、`V3_PUBLICATION_REVIEW_20261018.md` 与 `v3/publication-proof.json`。
