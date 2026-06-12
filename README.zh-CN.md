# FlowPlan

FlowPlan 是一个全栈个人计划管理系统，用来把长期目标拆成项目、任务、每日计划、打卡记录和进度分析。项目后端使用 Spring Boot，前端使用 Vue，数据持久化使用 MySQL，并支持通过 DeepSeek API 辅助生成项目、任务和计划设置草稿。

English documentation: [README.md](./README.md)

## 功能

- 用户注册、登录、JWT 鉴权和退出登录。
- 项目与任务管理，支持截止日期、进度、状态、风险等级和任务依赖。
- 全局计划设置与项目级计划设置。
- 每日计划生成、查询、删除和任务打卡。
- Dashboard 与 Analytics 页面，用于查看今日安排、完成趋势、投入时间和项目概览。
- 可选 AI 草稿生成流程，用自然语言辅助生成项目、任务和设置草稿。
- 管理员概览、用户管理和操作日志查看。

## 技术栈

- 后端：Java 21、Spring Boot 3.5、MyBatis-Plus、Spring Data JPA、JWT。
- 数据库：MySQL 8 或兼容 MySQL 的数据库。
- 前端：Vue 3、Vite、Axios、ECharts、Lucide Vue。
- AI：DeepSeek 兼容的 Chat API，通过环境变量或本地配置导入。

## 项目结构

```text
.
├── src/main/java/com/lxy/flowplan     # Spring Boot 后端代码
├── src/main/resources/application.yml # 后端通用配置
├── src/main/resources/sql             # 数据库建表和迁移 SQL
├── frontend                           # Vue/Vite 前端
├── config/application-local.example.yml
├── pom.xml
├── mvnw
└── mvnw.cmd
```

## 环境要求

- JDK 21。
- MySQL 8.x。
- Node.js 20 或更高版本。
- npm。

项目已包含 Maven Wrapper，使用 `mvnw` / `mvnw.cmd` 时不需要额外安装 Maven。

## 数据库初始化

先确认 MySQL 服务已经启动，然后执行：

```bash
mysql -u root -p < src/main/resources/sql/flowplan.sql
```

默认后端连接配置为：

```text
jdbc:mysql://localhost:3306/flowplan
username: root
password: 空
```

如果你的 MySQL 用户名或密码不同，可以用环境变量覆盖。

macOS / Linux：

```bash
export MYSQL_URL="jdbc:mysql://localhost:3306/flowplan?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="你的数据库密码"
```

Windows PowerShell：

```powershell
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="你的数据库密码"
```

也可以创建本地配置文件 `config/application-local.yml`，该文件已被 `.gitignore` 忽略，不会提交到 Git。

## DeepSeek API 配置

不要把真实 API Key 提交到仓库。推荐先复制示例配置：

```bash
cp config/application-local.example.yml config/application-local.yml
```

然后在 `config/application-local.yml` 中填写：

```yaml
app:
  ai:
    api-key: your-deepseek-api-key
    audit-enabled: false
```

也可以用环境变量：

```bash
export DEEPSEEK_API_KEY="your-deepseek-api-key"
export DEEPSEEK_BASE_URL="https://api.deepseek.com"
export DEEPSEEK_MODEL="deepseek-v4-flash"
```

Windows PowerShell：

```powershell
$env:DEEPSEEK_API_KEY="your-deepseek-api-key"
$env:DEEPSEEK_BASE_URL="https://api.deepseek.com"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
```

如果不配置 API Key，普通项目、任务、计划和打卡功能仍然可以运行，AI 草稿功能不可用。

## 启动后端

macOS / Linux：

```bash
./mvnw spring-boot:run
```

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

后端默认地址：

```text
http://localhost:8081/flowplan
```

可以用下面的命令检查后端是否启动成功：

```bash
curl http://localhost:8081/flowplan/
```

## 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开：

```text
http://localhost:5173
```

开发环境下，Vite 会把前端的 `/api` 请求代理到 `http://localhost:8081/flowplan`。

## 打包构建

后端：

```bash
./mvnw -DskipTests package
```

Windows：

```powershell
.\mvnw.cmd -DskipTests package
```

前端：

```bash
cd frontend
npm run build
```

## 安全说明

- 不要提交 `config/application-local.yml`、`.env`、数据库备份、个人文档和真实 API Key。
- 如果部署到本机以外的环境，请设置自己的 `APP_JWT_SECRET`。
- 数据库密码和 DeepSeek API Key 推荐使用环境变量或本地配置文件导入。

## License

暂未声明许可证。
