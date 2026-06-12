<script setup>
import { LogOut } from "@lucide/vue";
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref } from "vue";
import { getCurrentUser, logout } from "../api/auth";
import { clearAiBuilderState } from "../utils/aiBuilderStorage";
import DashboardView from "./DashboardView.vue";
import PlanView from "./PlanView.vue";
import ProjectView from "./ProjectView.vue";
import SettingView from "./SettingView.vue";
import TaskView from "./TaskView.vue";

const AnalyticsView = defineAsyncComponent(() => import("./AnalyticsView.vue"));
const AiBuilderView = defineAsyncComponent(() => import("./AiBuilderView.vue"));
const AdminView = defineAsyncComponent(() => import("./AdminView.vue"));

const emit = defineEmits(["logout"]);

const activePage = ref("dashboard");
const selectedProjectId = ref(null);
const user = ref(null);
const logoutLoading = ref(false);

const menuItems = computed(() => {
    const items = [
        { key: "dashboard", icon: "🏠", label: "Dashboard" },
        { key: "projects", icon: "📁", label: "Projects" },
        { key: "tasks", icon: "✓", label: "Tasks" },
        { key: "plans", icon: "📅", label: "Plans" },
        { key: "analytics", icon: "📊", label: "Analytics" },
        { key: "ai", icon: "🤖", label: "AI" },
        { key: "settings", icon: "⚙", label: "Settings" }
    ];
    if (user.value?.role === "ADMIN") {
        items.push({ key: "admin", icon: "🛡", label: "管理员" });
    }
    return items;
});

const currentTitle = computed(() => {
    return menuItems.value.find(item => item.key === activePage.value)?.label || "Dashboard";
});

async function loadUser() {
    user.value = await getCurrentUser();
    const route = readRoute();
    applyRoute(route.page, route.projectId);
}

async function handleLogout() {
    logoutLoading.value = true;
    try {
        await logout();
    } finally {
        localStorage.removeItem("token");
        clearAiBuilderState();
        logoutLoading.value = false;
        emit("logout");
    }
}

function switchPage(page) {
    navigateTo(page, null);
}

function openProjectTasks(project) {
    navigateTo("tasks", project.id);
}

function openProjectTaskList(projectId) {
    navigateTo("tasks", projectId);
}

function backToProjects() {
    navigateTo("projects", null);
}

function navigateTo(page, projectId) {
    applyRoute(page, projectId);
    writeRoute(page, projectId, "push");
}

function applyRoute(page, projectId) {
    activePage.value = menuItems.value.some(item => item.key === page) ? page : "dashboard";
    selectedProjectId.value = activePage.value === "tasks" || activePage.value === "settings" ? projectId : null;
}

function readRoute() {
    const rawHash = window.location.hash.replace(/^#\/?/, "");
    const [page = "dashboard", query = ""] = rawHash.split("?");
    const params = new URLSearchParams(query);
    return {
        page: page || "dashboard",
        projectId: params.get("projectId")
    };
}

function routeHash(page, projectId) {
    if ((page === "tasks" || page === "settings") && projectId) {
        return `#/${page}?projectId=${encodeURIComponent(projectId)}`;
    }
    return `#/${page || "dashboard"}`;
}

function writeRoute(page, projectId, mode = "replace") {
    const hash = routeHash(page, projectId);
    const state = { page, projectId };
    if (window.location.hash === hash) {
        window.history.replaceState(state, "", hash);
        return;
    }
    if (mode === "push") {
        window.history.pushState(state, "", hash);
        return;
    }
    window.history.replaceState(state, "", hash);
}

function handleBrowserNavigation() {
    const route = readRoute();
    applyRoute(route.page, route.projectId);
}

onMounted(() => {
    const route = readRoute();
    applyRoute(route.page, route.projectId);
    writeRoute(route.page, route.projectId);
    window.addEventListener("popstate", handleBrowserNavigation);
    window.addEventListener("hashchange", handleBrowserNavigation);
    loadUser();
});

onUnmounted(() => {
    window.removeEventListener("popstate", handleBrowserNavigation);
    window.removeEventListener("hashchange", handleBrowserNavigation);
});
</script>

<template>
    <div class="app-shell">
        <header class="topbar">
            <div>
                <p class="eyebrow">FlowPlan</p>
                <h1>{{ currentTitle }}</h1>
            </div>

            <div class="user-area">
                <div class="user-info">
                    <strong>{{ user?.username || '用户' }}</strong>
                    <span>{{ user?.email || '正在加载用户信息' }}</span>
                </div>
                <button class="logout-button" type="button" title="退出登录" :disabled="logoutLoading" @click="handleLogout">
                    <LogOut :size="19" stroke-width="2.2" />
                </button>
            </div>
        </header>

        <div class="body">
            <aside class="toolbar" aria-label="Main navigation">
                <button
                    v-for="item in menuItems"
                    :key="item.key"
                    :class="['nav-button', { active: activePage === item.key }]"
                    type="button"
                    @click="switchPage(item.key)"
                >
                    <span class="nav-icon">{{ item.icon }}</span>
                    <span>{{ item.label }}</span>
                </button>
            </aside>

            <main class="main-content">
                <DashboardView
                    v-if="activePage === 'dashboard'"
                    :user="user"
                    @open-project-tasks="openProjectTaskList"
                    @open-plans="switchPage('plans')"
                />
                <ProjectView v-else-if="activePage === 'projects'" @open-tasks="openProjectTasks" />
                <TaskView v-else-if="activePage === 'tasks'" :project-id="selectedProjectId" @back-projects="backToProjects" />
                <PlanView v-else-if="activePage === 'plans'" @open-project-tasks="openProjectTaskList" />
                <AnalyticsView v-else-if="activePage === 'analytics'" />
                <AiBuilderView v-else-if="activePage === 'ai'" :user="user" @open-plans="switchPage('plans')" />
                <SettingView v-else-if="activePage === 'settings'" :project-id="selectedProjectId" />
                <AdminView v-else-if="activePage === 'admin'" :current-user="user" />
                <section v-else class="empty-page">
                    <h2>{{ currentTitle }}</h2>
                    <p>该页面尚未开发。</p>
                </section>
            </main>
        </div>
    </div>
</template>

<style scoped>
.app-shell {
    height: 100vh;
    display: grid;
    grid-template-rows: auto 1fr;
    overflow: hidden;
    color: #172033;
    background: #f5f7fb;
    font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.topbar {
    min-height: 58px;
    border-bottom: 1px solid #d8dee9;
    background: #ffffff;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    padding: 9px 24px;
}

.eyebrow {
    margin: 0 0 3px;
    color: #2563eb;
    font-size: 13px;
    font-weight: 700;
}

h1,
h2,
p {
    margin: 0;
}

h1 {
    font-size: 21px;
    line-height: 1.2;
}

.user-area {
    display: flex;
    align-items: center;
    gap: 14px;
}

.user-info {
    display: grid;
    gap: 3px;
    text-align: right;
    font-size: 14px;
}

.user-info span {
    color: #667085;
}

.logout-button {
    width: 36px;
    height: 36px;
    border: 1px solid #c7d0df;
    border-radius: 6px;
    padding: 0;
    display: inline-grid;
    place-items: center;
    color: #2f3a4f;
    background: #ffffff;
    font: inherit;
    cursor: pointer;
}

.logout-button:hover {
    color: #172033;
    background: #eef2f7;
}

.logout-button:disabled {
    cursor: not-allowed;
    opacity: 0.7;
}

.body {
    min-height: 0;
    display: grid;
    grid-template-columns: 180px 1fr;
    overflow: hidden;
}

.toolbar {
    border-right: 1px solid #d8dee9;
    background: #ffffff;
    padding: 16px 10px;
    display: grid;
    align-content: start;
    gap: 6px;
    overflow-y: auto;
}

.nav-button {
    width: 100%;
    border: 0;
    border-radius: 6px;
    padding: 10px 12px;
    display: grid;
    grid-template-columns: 24px 1fr;
    align-items: center;
    gap: 8px;
    color: #2f3a4f;
    background: transparent;
    font: inherit;
    text-align: left;
    cursor: pointer;
}

.nav-button:hover,
.nav-button.active {
    color: #1849a9;
    background: #eaf1ff;
}

.nav-icon {
    text-align: center;
}

.main-content {
    min-width: 0;
    min-height: 0;
    padding: 24px;
    overflow-y: auto;
}

.empty-page {
    min-height: 240px;
    border: 1px solid #d8dee9;
    border-radius: 8px;
    background: #ffffff;
    display: grid;
    align-content: center;
    justify-items: center;
    gap: 10px;
    color: #2f3a4f;
}

.empty-page h2 {
    font-size: 22px;
}

.empty-page p {
    color: #667085;
}

@media (max-width: 720px) {
    .topbar {
        align-items: flex-start;
        flex-direction: column;
    }

    .user-area {
        width: 100%;
        justify-content: space-between;
    }

    .user-info {
        text-align: left;
    }

    .body {
        grid-template-columns: 1fr;
    }

    .toolbar {
        border-right: 0;
        border-bottom: 1px solid #d8dee9;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        max-height: 180px;
    }
}
</style>
