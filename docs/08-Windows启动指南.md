# Windows 启动指南

## 推荐安装

1. JDK 25：安装后在 PowerShell 执行 `java -version`。
2. Maven 3.9+：执行 `mvn -version`。
3. Node.js 20 LTS 或更高：执行 `node -v`、`npm -v`。
4. Docker Desktop：用于启动 MySQL；确认 Docker Desktop 已运行。

项目路径尽量不要包含中文、空格和过深目录，例如 `D:\Projects\narrative-studio`。

## 启动顺序

在项目根目录打开三个 PowerShell 窗口。

窗口一：

```powershell
docker compose up -d mysql
docker compose ps
```

窗口二：

```powershell
cd backend
mvn spring-boot:run
```

窗口三：

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。

## 常见问题

### 3306 端口被占用

本机已有 MySQL 时，可直接执行 `database/schema.sql`，并修改 `backend/src/main/resources/application.yml` 对应连接；或把 `docker-compose.yml` 的 `3306:3306` 左侧改为其他端口，并同步修改 `DB_URL`。

### Maven 下载依赖慢

在学校网络下可配置可信的 Maven 镜像。不要把从未知网站下载的 JAR 手工放进项目；保持依赖都由 `pom.xml` 管理。

### 前端能打开但接口失败

确认后端控制台无报错、`http://localhost:8080/api/health` 可访问，并从项目根的 `frontend` 目录用 `npm run dev` 启动，使 Vite 代理 `/api` 到 8080。

### 数据库改了但没有生效

Docker 初始化脚本只在空数据卷执行。开发期如需重建，请先备份有价值数据；停止容器后删除本项目的 `narrative_mysql_data` 卷再启动。不要删除不确定归属的其他 Docker 卷。

## 验证与注意事项

- 本项目已在 **JDK 25**（Java 25.0.1）下完成本地构建与测试验证。如需复现：

```powershell
cd backend
..\..\Users\34107\.maven\maven-3.9.15\bin\mvn.cmd -f .\pom.xml clean test
```

- 在 JDK 25 下，Mockito 可能会输出关于动态 agent 的警告（构建仍然通过）。如果未来 JDK 版本禁止动态加载 agent，可按 Mockito 文档将其作为 JVM agent 添加到构建/测试配置中。
