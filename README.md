# 叙事工坊 Narrative Studio

面向互动小说作者与独立游戏团队的剧情设计、状态推演与一致性检测平台。本压缩包是课程设计的 `v0.1` 基础框架：基础鉴权、项目 CRUD、项目成员权限边界、前端工作台、完整领域数据库草案已经就位，其余模块按阶段任务继续开发。

## 已实现与已预留

| 状态 | 内容 |
|---|---|
| 已实现 | 注册、登录、JWT、统一响应与异常、项目 CRUD、成员管理、世界观条目、角色档案与剧情图后端 CRUD、状态变量/条件/效果与规则引擎、模拟会话/状态快照/回放/重开后端、权限及引用校验、前端登录与项目工作台 |
| 已设计表结构 | 世界观、角色、关系与知识、节点、选择、变量、条件、效果、测试会话、测试步骤、检测问题、反馈 |
| 本轮补齐的后端 | 角色关系/知识、节点出场角色、草稿转正、结构检测/问题处理、反馈、发布版本试玩、知识快照、邮箱验证/找回/改密、JWT 撤销、管理员用户状态 |
| 待联调与交付 | 真实 MySQL 与 SMTP 验证；世界观、角色、剧情图、规则、模拟、发布、反馈等前端页面；完整演示数据、性能验收、AI 适配层 |

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
2. 按 [数据库升级说明](database/database-design/README.md) 准备 20 表数据库：17 表基线 + 3 张新增表及 4 张原表扩展。已有 20 表库不要重复执行扩展。
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

本版本是“可继续开发的框架”，不是课程最终成品。框架完成的判定是：目录和依赖明确、数据库可初始化、注册登录和项目 CRUD 形成最小纵向切片、权限边界集中、前后端接口格式统一、后续 P0 模块都有数据与路由落点。

## 安全提醒

默认数据库密码和 JWT 密钥仅供本地开发。提交或部署前必须使用环境变量替换；不要把 `.env`、真实 API Key 或数据库备份提交到 Git。

## 已验证 Java 版本

- 本项目已在 **JDK 25**（Java 25.0.1）下本地构建并运行测试通过。
- 若要在本地验证环境正确性，请在项目根目录运行：

```bash
mvn -f backend/pom.xml clean test
```

测试通过表示后端在本地 JDK 25 下可正常编译与运行单元测试。
