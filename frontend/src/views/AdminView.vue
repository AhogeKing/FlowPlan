<script setup>
import { ClipboardList, Database, RefreshCw, Users } from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import {
    deleteAdminUser,
    getAdminOverview,
    listAdminUsers,
    listOperationLogs,
    updateAdminUserRole
} from "../api/admin";

const props = defineProps({
    currentUser: {
        type: Object,
        default: null
    }
});

const overview = ref(null);
const users = ref([]);
const logs = ref({ page: 1, size: 20, total: 0, total_pages: 0, records: [] });
const selectedModule = ref("");
const loading = ref(false);
const actingUserId = ref(null);
const message = ref("");

const moduleOptions = [
    { value: "PROJECT", label: "项目" },
    { value: "TASK", label: "任务" },
    { value: "PLAN", label: "计划" },
    { value: "CHECKIN", label: "打卡" },
    { value: "SETTING", label: "设置" },
    { value: "AI", label: "AI" },
    { value: "USER", label: "用户" }
];

const overviewCards = computed(() => [
    { key: "users", label: "用户总数", value: overview.value?.user_count || 0, icon: Users },
    { key: "projects", label: "项目总数", value: overview.value?.project_count || 0, icon: Database },
    { key: "tasks", label: "任务总数", value: overview.value?.task_count || 0, icon: ClipboardList },
    { key: "plans", label: "今日计划", value: overview.value?.today_plan_count || 0, icon: ClipboardList },
    { key: "checkins", label: "今日打卡", value: overview.value?.today_checkin_count || 0, icon: ClipboardList },
    { key: "ai", label: "AI 调用", value: overview.value?.ai_call_count || 0, icon: Database }
]);

const roleOptions = [
    { value: "USER", label: "普通用户" },
    { value: "ADMIN", label: "管理员" }
];

async function loadAdminPage() {
    loading.value = true;
    message.value = "";
    const results = await Promise.allSettled([
        getAdminOverview({ silent: true }),
        listAdminUsers({ silent: true }),
        listOperationLogs({ page: logs.value.page, size: logs.value.size, module: selectedModule.value }, { silent: true })
    ]);

    const [overviewResult, usersResult, logsResult] = results;
    if (overviewResult.status === "fulfilled") {
        overview.value = overviewResult.value;
    }
    if (usersResult.status === "fulfilled") {
        users.value = Array.isArray(usersResult.value) ? usersResult.value : [];
    }
    if (logsResult.status === "fulfilled") {
        logs.value = logsResult.value || logs.value;
    }

    const failed = results.find(result => result.status === "rejected");
    if (failed) {
        message.value = failed.reason?.msg || "管理数据暂时无法完整加载";
    }
    loading.value = false;
}

async function loadLogs(page = 1) {
    logs.value = { ...logs.value, page };
    loading.value = true;
    message.value = "";
    try {
        logs.value = await listOperationLogs({
            page,
            size: logs.value.size,
            module: selectedModule.value
        }, { silent: true });
    } catch (error) {
        message.value = error?.msg || "操作日志暂时无法加载";
    } finally {
        loading.value = false;
    }
}

function formatDateTime(value) {
    if (!value) {
        return "-";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(date);
}

function formatStatus(status) {
    const labels = {
        SUCCESS: "成功",
        FAILED: "失败"
    };
    return labels[status] || status || "-";
}

function statusClass(status) {
    return `status-${String(status || "unknown").toLowerCase()}`;
}

function formatModule(module) {
    return moduleOptions.find(item => item.value === module)?.label || module || "-";
}

function formatOperation(operationType) {
    const labels = {
        CREATE: "新增",
        UPDATE: "更新",
        DELETE: "删除",
        GENERATE: "生成",
        CHECKIN: "打卡",
        APPLY: "应用",
        RESET: "重置",
        LOGIN: "登录"
    };
    return labels[operationType] || operationType || "-";
}

function isCurrentUser(user) {
    return props.currentUser?.id === user.user_id;
}

function isUserActing(user) {
    return actingUserId.value === user.user_id;
}

async function handleRoleChange(user, event) {
    const nextRole = event.target.value;
    if (nextRole === user.role) {
        return;
    }
    if (isCurrentUser(user) && nextRole !== "ADMIN") {
        event.target.value = user.role;
        message.value = "不能将当前登录管理员降级为普通用户";
        return;
    }

    actingUserId.value = user.user_id;
    message.value = "";
    try {
        await updateAdminUserRole(user.user_id, nextRole, { silent: true });
        await loadAdminPage();
    } catch (error) {
        event.target.value = user.role;
        message.value = error?.msg || "用户权限修改失败";
    } finally {
        actingUserId.value = null;
    }
}

async function handleDeleteUser(user) {
    if (isCurrentUser(user)) {
        message.value = "不能删除当前登录管理员";
        return;
    }
    if (!window.confirm(`确定要删除用户「${user.username}」吗？该用户的项目、任务、计划和打卡数据也会一并删除。`)) {
        return;
    }

    actingUserId.value = user.user_id;
    message.value = "";
    try {
        await deleteAdminUser(user.user_id, { silent: true });
        await loadAdminPage();
    } catch (error) {
        message.value = error?.msg || "用户删除失败";
    } finally {
        actingUserId.value = null;
    }
}

onMounted(loadAdminPage);
</script>

<template>
    <section class="admin-page">
        <div class="page-header">
            <div>
                <p class="eyebrow">管理员</p>
                <h2>系统管理</h2>
            </div>
            <button class="refresh-button" type="button" title="刷新管理数据" :disabled="loading" @click="loadAdminPage">
                <RefreshCw :class="{ spinning: loading }" :size="18" stroke-width="2.2" />
            </button>
        </div>

        <p v-if="message" class="error-message">{{ message }}</p>

        <div class="overview-grid">
            <article v-for="card in overviewCards" :key="card.key" class="overview-card">
                <component :is="card.icon" :size="19" stroke-width="2.1" />
                <span>{{ card.label }}</span>
                <strong>{{ card.value }}</strong>
            </article>
        </div>

        <section class="admin-panel">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">用户管理</p>
                    <h3>用户列表</h3>
                </div>
                <span>{{ users.length }} 个用户</span>
            </div>
            <div class="table-wrap">
                <table>
                    <thead>
                    <tr>
                        <th>用户 ID</th>
                        <th>用户名</th>
                        <th>角色</th>
                        <th>项目数量</th>
                        <th>注册时间</th>
                        <th>最近登录</th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="user in users" :key="user.user_id">
                        <td>{{ user.user_id }}</td>
                        <td>{{ user.username }}</td>
                        <td>
                            <select
                                class="role-select"
                                :value="user.role"
                                :disabled="loading || isUserActing(user)"
                                @change="handleRoleChange(user, $event)"
                            >
                                <option v-for="role in roleOptions" :key="role.value" :value="role.value">
                                    {{ role.label }}
                                </option>
                            </select>
                        </td>
                        <td>{{ user.project_count }}</td>
                        <td>{{ formatDateTime(user.register_time) }}</td>
                        <td>{{ formatDateTime(user.last_login) }}</td>
                        <td>
                            <button
                                class="danger-button"
                                type="button"
                                :disabled="loading || isUserActing(user) || isCurrentUser(user)"
                                @click="handleDeleteUser(user)"
                            >
                                删除
                            </button>
                        </td>
                    </tr>
                    <tr v-if="users.length === 0">
                        <td colspan="7" class="empty-cell">暂无用户数据</td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="admin-panel">
            <div class="panel-heading">
                <div>
                    <p class="eyebrow">操作日志</p>
                    <h3>关键操作日志</h3>
                </div>
                <div class="log-toolbar">
                    <select v-model="selectedModule" @change="loadLogs(1)">
                        <option value="">全部模块</option>
                        <option v-for="module in moduleOptions" :key="module.value" :value="module.value">{{ module.label }}</option>
                    </select>
                </div>
            </div>
            <div class="table-wrap">
                <table>
                    <thead>
                    <tr>
                        <th>时间</th>
                        <th>用户</th>
                        <th>模块</th>
                        <th>操作</th>
                        <th>说明</th>
                        <th>状态</th>
                        <th>请求地址</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="log in logs.records" :key="log.id">
                        <td>{{ formatDateTime(log.create_time) }}</td>
                        <td>{{ log.username || log.user_id }}</td>
                        <td>{{ formatModule(log.module) }}</td>
                        <td>{{ formatOperation(log.operation_type) }}</td>
                        <td>
                            <span>{{ log.description }}</span>
                            <small v-if="log.error_message">{{ log.error_message }}</small>
                        </td>
                        <td><span :class="['status-pill', statusClass(log.status)]">{{ formatStatus(log.status) }}</span></td>
                        <td>{{ log.request_method }} {{ log.request_url }}</td>
                    </tr>
                    <tr v-if="!logs.records || logs.records.length === 0">
                        <td colspan="7" class="empty-cell">暂无操作日志</td>
                    </tr>
                    </tbody>
                </table>
            </div>
            <div class="pager">
                <button type="button" :disabled="loading || logs.page <= 1" @click="loadLogs(logs.page - 1)">上一页</button>
                <span>第 {{ logs.page || 1 }} / {{ logs.total_pages || 0 }} 页</span>
                <button type="button" :disabled="loading || logs.page >= logs.total_pages" @click="loadLogs(logs.page + 1)">下一页</button>
            </div>
        </section>
    </section>
</template>

<style scoped>
.admin-page {
    display: grid;
    gap: 18px;
    color: #172033;
}

.page-header,
.panel-heading,
.pager {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.eyebrow,
h2,
h3,
p {
    margin: 0;
}

.eyebrow {
    color: #2563eb;
    font-size: 13px;
    font-weight: 700;
}

h2 {
    margin-top: 3px;
    font-size: 24px;
}

h3 {
    margin-top: 3px;
    font-size: 18px;
}

button,
select {
    border: 1px solid #c7d0df;
    border-radius: 6px;
    padding: 8px 11px;
    color: #172033;
    background: #ffffff;
    font: inherit;
}

button {
    cursor: pointer;
}

button:disabled {
    cursor: not-allowed;
    opacity: 0.62;
}

.refresh-button {
    width: 36px;
    height: 36px;
    border-color: transparent;
    padding: 0;
    display: inline-grid;
    place-items: center;
    color: #2563eb;
    background: transparent;
}

.spinning {
    animation: refresh-spin 0.8s linear infinite;
}

@keyframes refresh-spin {
    to {
        transform: rotate(360deg);
    }
}

.error-message {
    color: #b42318;
    font-size: 14px;
}

.overview-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
    gap: 12px;
}

.overview-card,
.admin-panel {
    border: 1px solid #c7d0df;
    border-radius: 8px;
    background: #ffffff;
}

.overview-card {
    padding: 15px;
    display: grid;
    gap: 8px;
}

.overview-card svg {
    color: #2563eb;
}

.overview-card span,
.panel-heading span {
    color: #667085;
    font-size: 13px;
    font-weight: 700;
}

.overview-card strong {
    font-size: 22px;
}

.admin-panel {
    padding: 16px;
    display: grid;
    gap: 14px;
}

.table-wrap {
    overflow-x: auto;
}

table {
    width: 100%;
    border-collapse: collapse;
    font-size: 14px;
}

th,
td {
    border-bottom: 1px solid #e4e7ec;
    padding: 10px 11px;
    text-align: left;
    vertical-align: top;
}

th {
    color: #667085;
    font-size: 12px;
    font-weight: 800;
    white-space: nowrap;
}

td small {
    margin-top: 4px;
    display: block;
    color: #b42318;
}

.status-pill {
    border-radius: 999px;
    padding: 3px 8px;
    display: inline-flex;
    font-size: 12px;
    font-weight: 800;
}

.role-select {
    min-width: 104px;
    padding: 6px 9px;
    color: #1849a9;
    border-color: #b7c9ee;
    background: #f7faff;
    font-size: 13px;
    font-weight: 700;
}

.danger-button {
    padding: 6px 10px;
    color: #b42318;
    border-color: #fecdca;
    background: #fff5f5;
    font-size: 13px;
    font-weight: 700;
}

.danger-button:not(:disabled):hover {
    background: #fee4e2;
}

.status-success {
    color: #067647;
    background: #dcfae6;
}

.status-failed {
    color: #b42318;
    background: #fee4e2;
}

.status-unknown {
    color: #667085;
    background: #eef2f7;
}

.empty-cell {
    color: #667085;
    text-align: center;
}

.log-toolbar {
    display: flex;
    justify-content: flex-end;
}

.pager {
    justify-content: center;
}

.pager span {
    color: #667085;
    font-size: 14px;
    font-weight: 700;
}

@media (max-width: 720px) {
    .page-header,
    .panel-heading {
        align-items: flex-start;
        flex-direction: column;
    }
}
</style>
