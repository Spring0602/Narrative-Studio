# Windows 启动指南

Excel导入已加入：后端新增Apache POI依赖，更新代码后重新Maven构建并重启前后端；不需要额外服务或Docker。文件上限5MB、请求上限6MB，若有反向代理也应核对上传限制。使用入口及表格规范见[Excel剧情图导入说明](15-Excel剧情图导入说明.md)。

2026-09-17 启动前置更新：当前需要21表及新增进度列。已有20表库先备份，在隔离库验证 database/database-design/progression-extension.sql，再执行一次；新库依次执行基线、旧扩展、跨局扩展。不要重复运行ALTER；后端不自动迁移，不使用Docker。运行两份只读verify脚本后再启动。详细见[数据库升级说明](../database/database-design/README.md)。下文20表步骤仅是前一阶段。

## 推荐安装

1. JDK 25：安装后在 PowerShell 执行 `java -version`。
2. Maven 3.9+：执行 `mvn -version`。
3. Node.js 20 LTS 或更高：执行 `node -v`、`npm -v`。
4. MySQL 8.0+：确认 MySQL 服务已经启动。
5. Navicat：用于连接 MySQL、执行项目 SQL 和检查数据。


项目路径尽量不要包含中文、空格和过深目录，例如 `D:\Projects\narrative-studio`。

## 启动顺序

1. 启动本机 MySQL 服务，在 Navicat 中连接并按上文执行初始化 SQL。
2. 在项目根目录打开两个 PowerShell 窗口，分别启动后端和前端。

窗口一（后端）：

```powershell
cd backend
mvn spring-boot:run
```

窗口二（前端）：

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。

## 常见问题

### 使用 Navicat 初始化数据库

1. 在 Navicat 中创建 MySQL 连接。
2. 先读 `database/database-design/README.md`，区分已有17表库、已有20表库和全新测试库；执行前备份。
3. 当前后端需要基线加扩展共20表。已有扩展不可重复执行；测试库需修改基线副本中的 CREATE DATABASE/USE，避免误操作业务库。
4. 示例剧情脚本需先有账号和项目并修改 project_id，仍不是完整10节点2结局演示数据。
5. 在启动窗口/IDE 设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 指向已验证库；仓库 .env 不会自动加载。启动时会只读检查扩展列。
6. 邮件可选配置和接口联调见 [数据库接口补全说明](12-数据库接口补全与联调说明.md)。没有 SMTP 不影响普通登录和项目功能，但申请邮件操作返回503。

### 3306 端口被占用

在 Navicat 连接属性中确认 MySQL 实际端口，并将该端口同步写入 `DB_URL`。

### Maven 下载依赖慢

在学校网络下可配置可信的 Maven 镜像。不要把从未知网站下载的 JAR 手工放进项目；保持依赖都由 `pom.xml` 管理。

### 前端能打开但接口失败

确认后端控制台无报错、`http://localhost:8080/api/health` 可访问，并从项目根的 `frontend` 目录用 `npm run dev` 启动，使 Vite 代理 `/api` 到 8080。

### 数据库改了但没有生效

使用 Navicat 重新执行前，应先备份有价值数据；`schema.sql` 使用 `CREATE TABLE IF NOT EXISTS`，不会自动重建已有表。结构变更应通过迁移 SQL 执行。

## 验证与注意事项

### 生产模式

在启动环境明确配置 DB_URL、DB_USERNAME、DB_PASSWORD、JWT_SECRET、CORS_ALLOWED_ORIGIN，并设置 SPRING_PROFILES_ACTIVE=prod。JWT_SECRET至少32字节且不能使用开发默认值，数据库不能使用默认开发密码；缺失配置会阻止启动。不要把真实值写进仓库。

部署时运行构建后的JAR：先在项目根执行 mvn -f backend/pom.xml clean verify，再运行 java -jar backend/target/narrative-studio-backend-0.1.0-SNAPSHOT.jar。前端dist由静态服务器提供，并将/api反向代理至后端、配置history路由回退；Vite开发代理不是生产服务器。

### 可选AI与新增接口

AI默认关闭，不影响核心业务；若需使用，先自行准备可用模型，再配置AI_ENABLED、AI_ENDPOINT、AI_MODEL。具体限制、数据发送同意和测试样例见 [后端收口说明](13-后端收口与整体检查记录.md)。本轮不要求Docker，也不自动安装模型或迁移数据库。

### 测试出现 Unresolved compilation problems

本机曾出现IDE/增量产物与Maven编译结果不一致，表现为JDK25运行时getFirst/getLast不可用。确认mvn -version与IDE运行时均使用JDK25，再从项目根执行mvn -f backend/pom.xml clean verify；该命令只清理Maven生成的target，不删除源码。不要将增量残留错误当成业务断言失败或修改已有规则语义。

- 本项目已在 **JDK 25**（Java 25.0.1）下完成本地构建与测试验证。如需复现：

```powershell
cd backend
mvn -f .\pom.xml clean test
```

- 在 JDK 25 下，Mockito 可能会输出关于动态 agent 的警告（构建仍然通过）。如果未来 JDK 版本禁止动态加载 agent，可按 Mockito 文档将其作为 JVM agent 添加到构建/测试配置中。
