# 叙事工坊 Narrative Studio

面向互动小说作者与独立游戏团队的剧情设计、状态推演与一致性检测平台。本压缩包是课程设计的 `v0.1` 基础框架：基础鉴权、项目 CRUD、项目成员权限边界、前端工作台、完整领域数据库草案已经就位，其余模块按阶段任务继续开发。

## 已实现与已预留

| 状态 | 内容 |
|---|---|
| 已实现 | 注册、登录、JWT、统一响应与异常、项目创建/列表/详情/编辑/归档、OWNER/EDITOR/TESTER 权限基础、前端登录与项目工作台 |
| 已设计表结构 | 世界观、角色、关系与知识、节点、选择、变量、条件、效果、测试会话、测试步骤、检测问题、反馈 |
| 待按计划实现 | 成员邀请、世界观与角色 CRUD、可视化剧情图、规则引擎、剧情模拟、图结构检测、AI 适配层 |

## 技术架构

- 后端：Java 25、Spring Boot、Spring MVC、Spring Security、MyBatis-Plus、MySQL
- 前端：Vue 3、TypeScript、Vite、Element Plus、Vue Flow
- 架构：表示层 → Controller → Service → Mapper → MySQL
- 鉴权：无状态 JWT；项目级权限由 Service 层统一校验

## 本地启动

### 1. 准备环境

- JDK 25
- Maven 3.9+
- Node.js 20+ 与 npm 10+
- Docker Desktop（推荐）或 MySQL 8.0+

### 2. 启动数据库

```bash
docker compose up -d mysql
```

首次启动会执行 `database/schema.sql`。如果不用 Docker，请手动创建 MySQL 数据库并执行该脚本。

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
├─ docker-compose.yml       本地 MySQL
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
