# 叙事工坊 Narrative Studio

面向互动小说作者与独立游戏团队的剧情设计、状态推演与一致性检测平台。2026-09-12已完成课程范围内P0/P1后端代码，并接入9月11日更新的核心前端UI；目前进入真实数据库、页面与交付验收阶段，不代表课程最终成品已验收。

## 已实现与已预留

| 状态 | 内容 |
|---|---|
| 已实现 | 注册、登录、JWT、统一响应与异常、项目 CRUD、成员管理、世界观条目、角色档案与剧情图后端 CRUD、状态变量/条件/效果与规则引擎、模拟会话/状态快照/回放/重开后端、权限及引用校验、前端登录与项目工作台 |
| 数据库 | MySQL+SQL+Navicat；原20表基础上增加跨局进度，当前需要21表及对应扩展列 |
| Excel生成剧情图 | 支持xlsx/xls、单表/双表、自定义表头与类型映射、预览及自动布局；仅新建项目或导入空图，不覆盖已有剧情；AI生成Excel暂缓。见[使用说明](docs/15-Excel剧情图导入说明.md) |
| 9月17日复杂解锁 | SESSION/PROFILE变量、跨模拟通关与访问记录、嵌套前置规则、测试清档和预置；详见[配置与迁移说明](docs/14-跨模拟进度与复杂解锁说明.md) |
| 本轮补齐的后端 | 角色关系/知识、节点出场角色、草稿转正、结构检测/问题处理、反馈、发布版本试玩、知识快照、邮箱验证/找回/改密、JWT 撤销、管理员用户状态 |
| 9月12日后端收口 | 剧情JSON导入导出、按用户/版本结局覆盖、可选AI候选对话；并发/引用/清空字段修复，线性图算法与生产配置检查 |
| 最新前端 | 世界观、角色/知识、剧情画布/草稿、规则、模拟/发布、检测/反馈与账户等核心页面已接真实API；本轮修复分页和冻结历史显示 |
| 待联调与交付 | 新增P1页面入口；浏览器视觉/交互、真实MySQL/SMTP/模型验收；完整SQL演示数据、真实环境性能、新机部署与答辩 |

## 技术架构

- 后端：Java 25、Spring Boot、Spring MVC、Spring Security、MyBatis-Plus、MySQL
- 前端：Vue 3、TypeScript、Vite、Element Plus、Vue Flow
- 架构：表示层 → Controller → Service → Mapper → MySQL
- 鉴权：JWT + 数据库账户状态/令牌版本校验；项目级权限由 Service 层统一校验

## 本地启动

### 1. 准备环境

- JDK 25
- Maven 3.9+
- Node.js 20+ 与 npm 10+
- MySQL 8.0+
- Navicat（用于连接 MySQL、执行 SQL 脚本和日常数据管理）


### 2. 初始化数据库（Navicat）

1. 在 Navicat 中新建 MySQL 连接并确认服务可用。
2. 按 [数据库升级说明](database/database-design/README.md) 准备21表数据库：17表基线 → schema-extension.sql → progression-extension.sql。已有20表库只执行跨局进度增量一次，不要重复执行旧扩展；先备份并在隔离库验证。
3. 原 `schema.sql` 和 `demo_story.sql` 固定选择 `narrative_studio`；独立测试库须使用修改建库/选库名称后的副本。演示脚本仍是未闭环的最小样例，不是完整验收数据。
4. 由 PowerShell 或 IDE 注入 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，明确指向已验证的数据库；Spring Boot 不自动读取仓库 `.env`。
5. 新后端启动进行只读结构检查；缺少扩展会明确报错，不自动执行 DDL。



### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

健康检查：`GET http://localhost:8080/api/health`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`，先注册账户，再创建项目。

## 目录导航

```text
narrative-studio/
├─ backend/                 Spring Boot 后端
├─ frontend/                Vue 3 前端
├─ database/                建库脚本和演示剧情数据
├─ docs/                    架构、接口、任务计划与协作规范
├─ scripts/                 项目检查脚本

└─ README.md                启动入口
```

开始开发前，三名成员都应依次阅读：

1. `docs/01-项目范围与课程要求.md`
2. `docs/02-系统架构设计.md`
3. `docs/03-阶段任务计划.md`
4. `docs/04-开发与协作规范.md`
5. `docs/05-接口与错误约定.md`

Windows 用户可直接看 `docs/08-Windows启动指南.md`；此前的内部立项策划案也已收录在 `docs/NarrativeStudio_ProjectProposal.docx`。

## 当前完成定义

第3周后端接口和历史验收见 [第3周后端验收记录](docs/09-第3周后端验收记录.md)。普通测试使用隔离H2，不依赖Docker；业务库使用MySQL 8。当前分工调整为A主后端、B主前端并审查数据库、C主数据库与各类测试并负责答辩，详见 [阶段任务计划](docs/03-阶段任务计划.md)。

2026-09-10 数据库接口补全、20 表部署前提、SMTP 可选配置及新增契约见 [数据库接口补全与联调说明](docs/12-数据库接口补全与联调说明.md)。代码完成不代表真实数据库、邮件投递或前端页面验收通过。

本轮后端clean verify共166项测试：165通过、1项真实MySQL跳过；前端4项分页测试、类型检查和生产构建通过。浏览器操作工具初始化失败，未完成页面点击/视觉验收；SMTP和AI仅用测试替身。详细契约、性能样本与限制见 [后端收口与整体检查记录](docs/13-后端收口与整体检查记录.md)。

可导入 [10节点双结局JSON样例](backend/src/test/resources/story-transfer-demo.json) 到独立测试项目；不覆盖已有项目，也不替代C负责的完整SQL数据和Navicat记录。AI默认关闭，配置和人工确认步骤见收口说明。

## 安全提醒

默认数据库密码和 JWT 密钥仅供本地开发。提交或部署前必须使用环境变量替换；不要把 `.env`、真实 API Key 或数据库备份提交到 Git。

正式部署设置SPRING_PROFILES_ACTIVE=prod；后端会检查JWT_SECRET、DB_URL、DB_USERNAME、DB_PASSWORD、CORS_ALLOWED_ORIGIN，拒绝缺失配置及开发默认凭据。数据库不会自动迁移。

## 已验证 Java 版本

- 本项目已在 **JDK 25**（Java 25.0.1）下本地构建并运行测试通过。
- 若要在本地验证环境正确性，请在项目根目录运行：

```bash
mvn -f backend/pom.xml clean test
```

测试通过表示后端在本地 JDK 25 下可正常编译与运行单元测试。
