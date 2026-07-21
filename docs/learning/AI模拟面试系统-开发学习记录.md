# AI 模拟面试系统开发学习记录

> 日期以 Git 提交为主，并用提交差异、源码、测试、`AGENTS.md`、SQL 和《项目设计记录 v1.1》交叉验证。设计记录另有 07-09、07-10、07-12、07-15 日志，但仓库这些日期无提交，相关代码实际在 07-13、07-14 入库，无法确认未提交阶段的精确完成日，故不另建日期。

## 2026-07-13｜项目初始化与用户持久层

**开发内容：** `a962ae9` 初始化 Java 21、Spring Boot 3.5.16、MyBatis 3.0.5 与 MySQL 项目，建立 `User`、角色/状态枚举、`UserMapper` 和 XML，支持按 id、账号、邮箱查询；数据库连接改由环境变量提供。`4945253` 增加真实数据库集成测试，补齐查询字段并排除 `User.toString()` 中的密码。12 表模型虽已见于设计记录，完整 SQL 当天尚未提交。

**框架设计原因：** 用户是注册、登录、JWT 定位和面试归属的根实体，先验证“对象—Mapper 代理—SQL—MySQL”最小链路，可在业务开发前暴露映射与环境问题。MyBatis 保留显式 SQL，符合学习数据库设计的目标；结构采用业务模块优先，不预建空层。

**核心注解：** `@SpringBootApplication` 写在启动类，启动时开启自动配置和组件扫描，缺少它容器不会装配应用，属 Boot 固定入口。`@Mapper` 写在接口，启动扫描时让 MyBatis 生成代理 Bean；`@Param` 给 XML 的 `#{...}` 提供稳定参数名，属 MyBatis 约定。`@SpringBootTest` 启动完整测试容器，`@ActiveProfiles` 在测试建容器时加载 local/test 配置；`@BeforeEach/@AfterEach` 是 JUnit 固定生命周期，用于隔离数据库数据。`@ToString(exclude="password")` 是项目的日志脱敏选择。

**核心类与 API：** Mapper 方法必须对应 XML 的 `namespace + id`；代理被调用后绑定 `#{参数}`、执行预编译 SQL，并通过 `map-underscore-to-camel-case` 把蛇形列映射为驼峰属性，配置关闭或列名错误会导致空值/SQL 异常。测试用 `JdbcTemplate` 准备、查询和清理真实数据，不用 Mock，因此验证了连接、SQL 和转换全过程。

**完整执行链路：** 测试激活配置并启动容器 → 数据源连接 MySQL → 准备用户 → 调 Mapper 代理 → XML SQL 执行并转为 `User`/枚举 → 断言 → 清理；连接或映射失败会在调用处中断。

**框架固定写法与项目决策：** 固定写法是启动容器、Mapper 代理、XML 映射和测试生命周期；项目决策是显式 SQL、角色/状态枚举、密码不进字符串、配置外置及当前分包。

**关键问题与修正：** 设计记录确认曾出现 Profile 未激活、数据库列名与 Java 属性名混用、测试残留触发唯一键。根因是配置、跨层命名和用例隔离不清；最终通过明确 Profile、真实列名配合驼峰映射、用例前后清理修正。`4945253` 还补回密码等列，避免登录取不到哈希。

**验证结果：** `4945253` 覆盖按账号、id、邮箱查询和字段映射；当天完成骨架与查询持久层，未实现注册 HTTP 链路。

**学习状态：** 已实现并理解 Mapper 接口/XML 对应关系；需继续练习结果映射、Profile 加载和可重复数据库测试，才能独立迁移到新表。

## 2026-07-14｜注册完整链路、统一异常与数据库基准

**开发内容：** `e93a350` 至 `e099edf` 完成注册：DTO 校验输入，Service 查重、BCrypt 哈希、固定 `USER/ENABLED` 并插入，Controller 暴露 `POST /api/auth/register`，`Result` 与全局异常处理统一 JSON，唯一键冲突转业务异常；认证代码归入 `auth`。`cb9971c` 删除 Service 上误加的 `@Configuration`。同日首次提交 v1.1 的 12 表 SQL；其他 11 表仍是设计基准，不代表业务已实现。

**框架设计原因：** Controller 管协议、Service 管业务/事务、Mapper 管持久化，避免散落 `try-catch`。查重能精确提示，但并发请求可能同时通过，仍须数据库唯一索引兜底，无需 Java 锁。`auth` 与 `user` 分包区分认证流程和资料管理；12 表为题库、会话、FINAL 评价、RAG 证据、报告和薄弱点预留可追溯结构。

**核心注解：** `@RestController` 在启动扫描时注册 MVC Bean并序列化返回值，`@RequestMapping/@PostMapping` 同期建立路由；缺少路由便无法匹配请求。`@RequestBody` 调方法前反序列化 JSON，`@Valid` 随后读取 DTO 的 `@NotBlank/@Size/@Email`，失败即中断，属 MVC/Validation 固定链路，长度和提示是项目规则。`@Service` 注册业务 Bean；`@Validated` 使方法参数校验生效；`@Transactional` 由代理在注册前开启事务、异常时回滚。`@Configuration + @Bean` 在启动时提供 `PasswordEncoder`；`@RestControllerAdvice + @ExceptionHandler + @ResponseStatus` 在 MVC 异常阶段选择 JSON 处理器，均是框架扩展模式。

**核心类与 API：** `PasswordEncoder.encode` 生成带盐 BCrypt 哈希，只能保存结果。`insertUser` 的 `useGeneratedKeys/keyProperty` 回填自增 id。Spring 将唯一键异常翻译为 `DuplicateKeyException`，Service 捕获并转 `BusinessException`；参数校验、坏 JSON、业务异常和未知异常分别由全局处理器转 400/500，未知错误只记日志。`Result.success/error` 是项目响应外壳。

**完整执行链路：** JSON → MVC 解析/校验 → Controller → Service 事务 → Mapper 查重 → BCrypt → 构造固定角色状态的 `User` → MyBatis 插入 → 提交 → `Result`；已知异常进 Advice，并发唯一键冲突转 400，其余异常回滚。

**框架固定写法与项目决策：** 固定写法是 Bean、MVC、事务代理和异常扩展点；项目决策是客户端不能传角色、BCrypt 8～72 位、预查+唯一键双保险、统一响应、模块边界和冻结表结构。

**关键问题与修正：** 重复邮箱测试曾同时使用已存在账号，先命中账号分支造成假覆盖，后拆成独立数据。构造器需要 `PasswordEncoder` 却没有 Bean，遂新增配置；又误把 `@Configuration` 放在 `AuthService`，`cb9971c` 删除，明确业务组件不是 Bean 定义源。

**验证结果：** `e9b29ba` 验证两种重复、成功落库、非明文及 BCrypt 匹配；`e271673` 用 MockMvc 验证成功、参数失败、重复注册与统一 JSON。注册链路完成。

**学习状态：** 已实现并能解释分层和并发兜底；Spring 代理何时执行事务/方法校验、异常翻译细节仍需小实验巩固。

## 2026-07-16｜登录、BCrypt 校验与状态防泄露

**开发内容：** `2ddccf0` 新增登录 DTO/VO 和 `POST /api/auth/login`：按账号查用户、校验 BCrypt、检查状态，成功仅返回 id、账号、用户名、角色，当天尚无 JWT。`188403b` 把密码验证移到禁用状态判断之前，并补测试。

**框架设计原因：** 登录需同时验证凭证和当前账户可用性。DTO 避免绑定实体，VO 建立字段白名单，防止密码哈希、状态和时间被序列化。不存在用户与错误密码统一提示可降低账号枚举；只有密码正确才披露禁用状态，避免错误密码确认账号存在。

**核心注解：** `@PostMapping` 启动时注册登录路由；`@RequestBody` 在方法调用前转 JSON，`@Valid` 读取 `@NotBlank/@Size`，失败交全局处理器，这是固定链路，账号 50 位、密码 8～72 位是项目规则。Lombok 的 `@ToString(exclude="password")` 在生成字符串方法时排除密码，防日志泄露；`@Builder` 只辅助构造 VO。

**核心类与 API：** `PasswordEncoder.matches(raw, encoded)` 从数据库哈希读取盐与成本并验证，参数顺序不能反，也不能再次 `encode` 后比较，因为 BCrypt 每次盐不同。Mapper 返回 `User` 后，Service 用 `LoginResponse.builder` 选择公开字段，Controller 再用 `Result.success` 封装；业务失败抛 `BusinessException`。

**完整执行链路：** JSON → 解析/校验 → Controller → Service 查账号 → 不存在返回通用 400 → `matches` 失败同样返回通用 400 → 密码正确后检查 `DISABLED` → VO → `Result` JSON；无写操作，故登录不加事务。

**框架固定写法与项目决策：** 固定写法是 MVC 绑定校验、PasswordEncoder 接口和 Advice；项目决策是错误消息合并、判断顺序、不返回实体、本阶段不提前加入 JWT/Redis/Security。

**关键问题与修正：** 原顺序先查状态再验密码，攻击者用错误密码也能识别禁用账号。`188403b` 前移 `matches`，并分别验证“禁用+正确/错误密码”，锁定安全语义。

**验证结果：** Service 与 MockMvc 覆盖成功、不存在、错密、禁用、输入边界和状态不泄露；基础登录完成，Token 与请求鉴权未完成。

**学习状态：** 已实现并应能独立解释 `encode/matches`、DTO/VO 和信息泄露；下一步学习 JWT 如何延续身份且不把 Token 当实时数据库状态。

## 2026-07-17｜JWT Access Token 签发与独立验证

**开发内容：** `c813486` 引入 JJWT 0.13.0，新增类型安全配置和 `JwtTokenService`；登录通过凭证/状态校验后签发 HS256 Access Token，响应增加类型和有效秒数。Claims 仅含 `sub/account/role/iss/iat/exp`；正式 Secret 由 `JWT_SECRET` 注入，测试使用独立配置。当天尚无请求 Filter 和权限控制。

**框架设计原因：** 基础登录只证明一次请求，后续无状态 API 需携带可验签凭据。配置绑定、Token 技术操作与登录业务分离，便于测试和替换。Payload 只是编码、可被读取，故最小化 Claims；Token 又是签发快照，验签成功不能证明数据库用户仍启用或角色仍最新。

**核心注解：** `@ConfigurationProperties("jwt")` 在启动绑定 YAML，`@EnableConfigurationProperties` 配合 `@Configuration` 注册属性 Bean；缺失便无法类型化注入。`@Validated` 在绑定后读取 `@NotBlank/@NotNull/@AssertTrue`，非法密钥配置或非正期限使启动尽早失败，属 Boot 固定模式，2 小时是项目选择。`@Component` 在扫描时注册 `JwtTokenService`，但它不等于 Spring Security 登录状态。

**核心类与 API：** `Decoders.BASE64.decode` 还原密钥字节，`Keys.hmacShaKeyFor` 创建 `SecretKey`；对 Base64 文本直接 `getBytes()` 会改变密钥语义。`Jwts.parser().verifyWith(key).requireIssuer(...).build()` 预建 Parser，验证时 `parseSignedClaims(...).getPayload()`，签名、过期或 issuer 错会抛异常。签发依次 `builder → subject/claim/issuer/issuedAt/expiration → signWith → compact`，`Instant + Duration` 计算期限。API 调法是库约定，Claims、issuer、期限和 Secret 来源是项目决策。

**完整执行链路：** 07-16 登录成功 → `AuthService` 调 Token Service → 写 Claims、HS256 签名并压缩 → `LoginResponse` 返回 Bearer Token；独立验证时 Parser 验签并检查 issuer/exp后返回 Claims。未创建 `Authentication/SecurityContext`，所以受保护请求仍不会自动登录。

**框架固定写法与项目决策：** 固定写法是配置绑定校验与 JJWT Builder/Parser；项目选择外置 Base64 Secret、只发 Access Token、最小 Claims、无刷新令牌，后续以 `sub` 查数据库。

**关键问题与修正：** 需避免把 Base64URL 当加密、把验签当实时账户校验；测试用篡改、错误密钥/issuer明确边界。生产密钥不能进仓库，测试又不能依赖本机环境，故新增测试 YAML。次日 `f45155d` 回退 `189bcfc` 带入的无关格式改动。

**验证结果：** Token 单测覆盖 Claims、过期、篡改、错误密钥/issuer；属性测试覆盖缺失、零、负期限；登录集成验证 Token 可解析且响应无密码/刷新令牌。签发与独立验证完成。

**学习状态：** 已理解签名不等于加密、Claims 和密钥外置；JJWT 构建器、异常及配置绑定能看懂但未必能独立写全，下一步手写签发—解析—失败链。

## 2026-07-18｜Spring Security 无状态 JWT 请求认证与权限控制

**开发内容：** `b758fdd` 新增安全配置、JWT Filter、401/403处理器、principal和测试：认证接口公开，其余默认保护，admin限管理员；Filter用JWT `sub` 查库，以最新 `status/role` 建身份。随后回退无关格式并清除误提交产物。

**框架设计原因：** JWT验签只产出可信Claims，不等于Security登录；须把已认证 `Authentication` 放入 `SecurityContext`，再由 `SecurityContextHolder` 保存，授权器才看得见。Filter认证“是谁/是否可用”，`SecurityFilterChain`授权“能否访问”。

**核心注解：** 启动时 `@Configuration/@Bean` 注册安全链，`@EnableWebSecurity` 显式启用Web安全（Boot常可省，源码保留），`@Component` 扫描Filter/处理器；缺少即无法装配，属框架模式。测试用 `@WebMvcTest/@Import` 加载MVC切片与安全组件。

**核心类与 API：** `HttpSecurity` 构建 `SecurityFilterChain`；`SessionCreationPolicy.STATELESS` 禁止Session保存认证，每次重验Token。关闭 `formLogin/httpBasic` 避免默认页面、重定向和Basic挑战。`authorizeHttpRequests` 用 `requestMatchers(...).permitAll()` 公开登录/注册，`hasRole("ADMIN")` 匹配 `ROLE_ADMIN`，`anyRequest().authenticated()`兜底；`exceptionHandling` 接入 `AuthenticationEntryPoint` 处理无有效身份的401、`AccessDeniedHandler`处理已认证但无权的403。

Filter继承 `OncePerRequestFilter`，每请求一次 `doFilterInternal`；`shouldNotFilter` 跳过登录/注册，使坏旧Token不阻断重新登录。无Token继续 `filterChain.doFilter`，由授权规则决定公开放行或受保护401；有Token则验Bearer、取正数 `sub`、查启用用户。数据库role转为 `ROLE_USER/ROLE_ADMIN` 的 `GrantedAuthority`，再创建已认证 `Authentication`（credentials为空），写入新Context和Holder。`addFilterBefore`保证授权前恢复身份；`FilterRegistrationBean.setEnabled(false)`禁用普通Servlet自动注册，避免链外或执行两次。

信任数据库最新status/role而非JWT role，可立即禁用/降权。Filter早于Controller，异常通常不到 `GlobalExceptionHandler`，故认证失败清上下文后调用EntryPoint，授权失败交DeniedHandler，两者输出统一 `Result` JSON。

**完整执行链路：** 请求→安全链；公开POST跳过Filter并放行。受保护请求无/坏Token或用户不可用→401；有效Token→查库、建Authentication/Context→ `authenticated/hasRole`；USER访问admin→403，权限足才进Controller；不建Session。

**框架固定写法与项目决策：** 固定模式：FilterChain、覆盖Filter方法、Authentication—Context—Holder、401/403扩展点、Filter顺序；项目选择：无状态、认证入口忽略旧Token、默认保护、admin规则、数据库实时状态/角色、精简principal和统一文案。

**关键问题与修正：** 实现有较多辅助生成痕迹，理解概念不等于会写API。仓库曾因 `git add .` 纳入临时产物，现已清理；以后先审查status/diff并按文件暂存。

**验证结果：** 测试覆盖公开入口坏Token、各类401、库状态/角色、USER 403、ADMIN成功、无Session、Filter仅经安全链和401/403 JSON。真实受保护业务Controller尚未开发，权限由测试Controller验证。

**学习状态：** 功能已实现、职责已理解；Security DSL、Filter生命周期、异常和测试API能看懂但不能保证独立实现，下一步手写无Token、有效Token、权限不足三链。
