# Frontend V3 · 开源设计研究

研究日期：2026-10-17。目标为 Technical Interview Studio V3；本文件是设计取舍，不改变 PRODUCT_UX_SPEC 或 STATE_MODEL 的行为合同。

## 研究方式与证据

直接阅读以下官方仓库、具体组件源码与演示页面。浏览器实际打开了 shadcn-vue Sidebar / Login、Soybean 登录、Vben 演示和 Pure Admin 演示。Vben 在本次环境停留于启动屏，Pure Admin 首次观察仅有插画，后续观察见下方截图记录；未把不完整页面当作完整后台交互验证。源码链接为研究当天的公开分支，可能随上游变化。

| 项目 | 具体参考 | 借鉴到本项目 | 不直接照搬 |
|---|---|---|---|
| [shadcn-vue](https://github.com/unovue/shadcn-vue) | [Sidebar 源码](https://github.com/unovue/shadcn-vue/blob/dev/apps/v4/registry/new-york-v4/ui/sidebar/Sidebar.vue)、[官方 Sidebar Blocks](https://shadcn-vue.com/blocks/sidebar) | 导航分组、浅色 active 背景、主内容 inset 层级；桌面导航与移动抽屉承担同一入口 | 多级目录、组织切换、项目列表均与单用户面试无关；不安装整套组件生成器 |
| shadcn-vue Login | [Login-02 源码](https://github.com/unovue/shadcn-vue/blob/dev/apps/v4/registry/new-york-v4/blocks/login-02/page.vue)、[Login Blocks](https://shadcn-vue.com/blocks/login)、[Blocks 索引](https://shadcn-vue.com/blocks) | 有限宽表单、分离的产品说明、桌面双栏／窄屏单栏、按钮与输入等高 | 不使用大幅图库、OAuth、验证码或服务条款占位；本项目只有现有账号密码登录与注册 |
| [Vben Admin](https://github.com/vbenjs/vue-vben-admin) | [Basic Layout 源码](https://github.com/vbenjs/vue-vben-admin/blob/main/packages/effects/layouts/src/basic/layout.vue)、[官方演示](https://www.vben.pro/) | 布局职责分离、菜单当前路由状态、内容区和导航区密度有别 | 不迁移 monorepo、偏好中心、多标签页、混合菜单或权限实现。演示启动屏不能作为表格交互证据 |
| [Soybean Admin](https://github.com/soybeanjs/soybean-admin) | [Base Layout 源码](https://github.com/soybeanjs/soybean-admin/blob/main/src/layouts/base-layout/index.vue)、[Naive UI 演示](https://naive.soybeanjs.cn/) | 内容、header、sider 各自清晰；移动端通过布局切换适配；登录控件密度和清楚的主按钮 | 演示登录页的大面积紫色曲线、角色快捷登录与多种登录方式不适合本产品；不复制模板统计卡 |
| [Pure Admin](https://github.com/pure-admin/vue-pure-admin) | [NavVertical 源码](https://github.com/pure-admin/vue-pure-admin/blob/main/src/layout/components/lay-sidebar/NavVertical.vue)、[演示](https://pure-admin.github.io/vue-pure-admin)、[官方 README 截图](https://github.com/pure-admin/vue-pure-admin#预览) | 当前菜单取自路由、菜单 loading 与空状态分开；管理员筛选和表格保持紧凑，手机切成行项目 | 不引入 Element Plus，不增加标签栏、复杂折叠菜单或布局设置入口 |
| [shadcn/ui](https://github.com/shadcn-ui/ui) | [Button 源码](https://github.com/shadcn-ui/ui/blob/main/apps/v4/registry/new-york-v4/ui/button.tsx)、[Input 源码](https://github.com/shadcn-ui/ui/blob/main/apps/v4/registry/new-york-v4/ui/input.tsx)、[Empty](https://ui.shadcn.com/docs/components/base/empty)、[Skeleton](https://ui.shadcn.com/docs/components/base/skeleton) | 组件的尺寸／variant／状态统一；空状态由图标、标题、说明、真实操作组成；加载占位对应真实内容 | React 与 Tailwind 源码仅供研究，不迁移组件；不用 skeleton 伪装成绩，不引入图表库 |

## 横向结论

导航采用白色边栏、浅蓝选中态、图标加文字；手机保留现有可关闭菜单、焦点管理和 Escape 行为。面试仍为独立 focus shell。字体采用系统 sans 与现有 monospace fallback：标题、正文、元信息、分数四层；不下载字体，避免网络和授权增加。

背景、内容表面和输入边界分别使用中性画布、白色、较强边框。主操作只用蓝色，成功／待补充／错误同时有文字或图标。阴影主要用于功能面板，16px 大面板与 10px 控件形成尺寸关系。柔和渐变仅用于工作台训练区、认证说明区、报告摘要；不进入作答编辑区。

加载、空、失败均使用现有数据分支。提交中的禁用和文字提示、等待时的原答案重试、恢复受阻说明、报告生成失败与刷新入口全部保留。管理员权限仍由原 store / router / API 行为控制。

以上为本项目基于参考的设计推导，不是对各模板所有页面或无障碍能力的认证。实际页面素材见 `v3/references/`，本项目对照见 `v3/before/` 与 `v3/after/`。

## 许可证、兼容性与依赖决策

五个仓库均通过其官方仓库 LICENSE / README 说明确认 MIT：
[shadcn-vue](https://github.com/unovue/shadcn-vue/blob/dev/LICENSE)、[Vben](https://github.com/vbenjs/vue-vben-admin/blob/main/LICENSE)、[Soybean](https://github.com/soybeanjs/soybean-admin/blob/main/LICENSE)、[Pure Admin](https://github.com/pure-admin/vue-pure-admin/blob/main/LICENSE)、[shadcn/ui](https://github.com/shadcn-ui/ui/blob/main/LICENSE.md)。

本轮不复制上游实现，也不引入第三方组件。生产与开发 package.json / lockfile 均不变，保留 Vue 3、现有 Router / Pinia 和 Lucide。浏览器 QA 通过已安装的 Playwright 运行工具执行，不进入应用依赖。组件兼容性以当前安装版本的 typecheck、Vitest、Vite 构建和 Chrome 验证为准。
