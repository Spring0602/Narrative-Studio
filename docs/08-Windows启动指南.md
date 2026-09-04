# Windows 启动指南

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
2. 打开 `database/schema.sql` 并执行全部语句。
3. 如需演示数据，再执行 `database/demo_story.sql`。
4. 确认生成 `narrative_studio` 数据库和 17 张业务表。
5. 根据连接信息设置后端的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。

### 3306 端口被占用

在 Navicat 连接属性中确认 MySQL 实际端口，并将该端口同步写入 `DB_URL`。

### Maven 下载依赖慢

在学校网络下可配置可信的 Maven 镜像。不要把从未知网站下载的 JAR 手工放进项目；保持依赖都由 `pom.xml` 管理。

### 前端能打开但接口失败

确认后端控制台无报错、`http://localhost:8080/api/health` 可访问，并从项目根的 `frontend` 目录用 `npm run dev` 启动，使 Vite 代理 `/api` 到 8080。

### 数据库改了但没有生效

使用 Navicat 重新执行前，应先备份有价值数据；`schema.sql` 使用 `CREATE TABLE IF NOT EXISTS`，不会自动重建已有表。结构变更应通过迁移 SQL 执行。

## 验证与注意事项

- 本项目已在 **JDK 25**（Java 25.0.1）下完成本地构建与测试验证。如需复现：

```powershell
cd backend
..\..\Users\34107\.maven\maven-3.9.15\bin\mvn.cmd -f .\pom.xml clean test
```

- 在 JDK 25 下，Mockito 可能会输出关于动态 agent 的警告（构建仍然通过）。如果未来 JDK 版本禁止动态加载 agent，可按 Mockito 文档将其作为 JVM agent 添加到构建/测试配置中。
