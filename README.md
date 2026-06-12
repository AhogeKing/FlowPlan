# FlowPlan

FlowPlan is a full-stack personal planning system that turns long-term goals into actionable tasks, daily schedules, check-ins, and progress analytics. It combines a Spring Boot backend, a Vue frontend, MySQL persistence, JWT-based authentication, and an optional DeepSeek-powered assistant for drafting projects, tasks, and plan settings.

Chinese documentation: [README.zh-CN.md](./README.zh-CN.md)

## Features

- User registration, login, JWT authentication, and logout.
- Project and task management with deadlines, progress, status, risk level, and task dependencies.
- Configurable planning settings at global and project scope.
- Daily plan generation, plan lookup, deletion, and task check-ins.
- Dashboard and analytics views for today's work, completion trends, time usage, and project overview.
- Optional AI drafting flow for generating project, task, and setting drafts from natural-language input.
- Admin overview, user management, and operation-log inspection.

## Tech Stack

- Backend: Java 21, Spring Boot 3.5, MyBatis-Plus, Spring Data JPA, JWT.
- Database: MySQL 8 or compatible MySQL distribution.
- Frontend: Vue 3, Vite, Axios, ECharts, Lucide Vue.
- AI provider: DeepSeek-compatible chat API, configured through environment variables or local config.

## Project Structure

```text
.
├── src/main/java/com/lxy/flowplan     # Spring Boot backend
├── src/main/resources/application.yml # Shared backend configuration
├── src/main/resources/sql             # Database schema and migration SQL
├── frontend                           # Vue/Vite frontend
├── config/application-local.example.yml
├── pom.xml
├── mvnw
└── mvnw.cmd
```

## Prerequisites

- JDK 21.
- MySQL 8.x.
- Node.js 20 or later.
- npm.

Maven does not need to be installed separately when using the included Maven Wrapper.

## Database Setup

Create or reset the local database with the schema SQL:

```bash
mysql -u root -p < src/main/resources/sql/flowplan.sql
```

The default backend configuration connects to:

```text
jdbc:mysql://localhost:3306/flowplan
username: root
password: empty
```

For a different local database account, use environment variables:

```bash
export MYSQL_URL="jdbc:mysql://localhost:3306/flowplan?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export MYSQL_USERNAME="root"
export MYSQL_PASSWORD="your-password"
```

On Windows PowerShell:

```powershell
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="your-password"
```

## Optional AI Configuration

Do not commit real API keys. For local development, copy the example config:

```bash
cp config/application-local.example.yml config/application-local.yml
```

Then set your key in `config/application-local.yml`:

```yaml
app:
  ai:
    api-key: your-deepseek-api-key
    audit-enabled: false
```

You can also use environment variables:

```bash
export DEEPSEEK_API_KEY="your-deepseek-api-key"
export DEEPSEEK_BASE_URL="https://api.deepseek.com"
export DEEPSEEK_MODEL="deepseek-v4-flash"
```

## Run the Backend

macOS or Linux:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend starts at:

```text
http://localhost:8081/flowplan
```

Health check:

```bash
curl http://localhost:8081/flowplan/
```

## Run the Frontend

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

During development, Vite proxies `/api` requests to `http://localhost:8081/flowplan`.

## Build

Backend:

```bash
./mvnw -DskipTests package
```

Windows:

```powershell
.\mvnw.cmd -DskipTests package
```

Frontend:

```bash
cd frontend
npm run build
```

## Security Notes

- Keep `config/application-local.yml`, `.env`, database dumps, and personal documents out of Git.
- Set a private `APP_JWT_SECRET` before deploying anywhere outside local development.
- Use `DEEPSEEK_API_KEY` or `config/application-local.yml` for AI keys instead of hardcoding secrets.

## License

No license has been declared yet.
