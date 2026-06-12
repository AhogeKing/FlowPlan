<script setup>
import {
    AlertTriangle,
    CalendarDays,
    CheckCircle2,
    Clock3,
    FolderOpen,
    ListChecks,
    RefreshCw,
    Sparkles,
    Target,
    TrendingUp,
    XCircle
} from "@lucide/vue";
import { computed, onMounted, ref } from "vue";
import { getDashboardToday } from "../api/dashboard";
import { checkinPlanItem } from "../api/plan";

const props = defineProps({
    user: {
        type: Object,
        default: null
    }
});

const emit = defineEmits(["open-project-tasks", "open-plans"]);

const dashboard = ref(null);
const loading = ref(false);
const message = ref("");
const savingItemId = ref(null);

const summary = computed(() => dashboard.value?.summary || {});
const todayPlans = computed(() => dashboard.value?.today_plans || []);
const activeProjects = computed(() => dashboard.value?.active_projects || []);
const recentStats = computed(() => dashboard.value?.recent_stats || {});
const aiSuggestion = computed(() => dashboard.value?.ai_suggestion || {});

const overviewCards = computed(() => [
    {
        key: "recommended",
        label: "Today Recommended",
        value: formatMinutes(summary.value.total_recommended_minutes),
        icon: Clock3
    },
    {
        key: "completed",
        label: "Today Completed",
        value: formatMinutes(summary.value.total_completed_minutes),
        icon: CheckCircle2
    },
    {
        key: "items",
        label: "Today's Plan Items",
        value: `${summary.value.total_plan_item_count || 0} 项`,
        icon: ListChecks
    },
    {
        key: "projects",
        label: "Active Projects",
        value: `${summary.value.active_project_count || 0} 个`,
        icon: FolderOpen
    }
]);

async function loadDashboard() {
    loading.value = true;
    message.value = "";
    try {
        dashboard.value = await getDashboardToday({ silent: true });
    } catch (error) {
        message.value = error?.msg || "Dashboard 数据暂时无法加载";
    } finally {
        loading.value = false;
    }
}

async function quickComplete(plan, item) {
    await submitQuickCheckin(plan, item, item.recommended_minutes || 0, "Dashboard quick complete");
}

async function quickSkip(plan, item) {
    await submitQuickCheckin(plan, item, 0, "Dashboard quick skip");
}

async function submitQuickCheckin(plan, item, completedMinutes, note) {
    if (!plan?.project_id || !item?.plan_item_id) {
        return;
    }

    savingItemId.value = item.plan_item_id;
    message.value = "";
    try {
        await checkinPlanItem(plan.project_id, item.plan_item_id, {
            completed_minutes: completedMinutes,
            checkin_date: dashboard.value?.today || null,
            note
        }, { silent: true });
        await loadDashboard();
    } catch (error) {
        message.value = error?.msg || "Quick Check-in 失败";
    } finally {
        savingItemId.value = null;
    }
}

function openProject(projectId) {
    emit("open-project-tasks", projectId);
}

function formatDate(dateText) {
    if (!dateText) {
        return "-";
    }
    const date = new Date(`${dateText}T00:00:00`);
    if (Number.isNaN(date.getTime())) {
        return dateText;
    }
    return new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    }).format(date);
}

function formatMinutes(minutes) {
    const value = Number(minutes || 0);
    if (value < 60) {
        return `${value} 分钟`;
    }
    const hours = Math.floor(value / 60);
    const rest = value % 60;
    return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分钟`;
}

function formatHours(minutes) {
    return `${(Number(minutes || 0) / 60).toFixed(1)} h`;
}

function formatRiskLevel(riskLevel) {
    const labels = {
        OK: "OK",
        PRESSURE: "Pressure",
        RELAXED: "Relaxed"
    };
    return labels[riskLevel] || riskLevel || "OK";
}

function pressureClass(level) {
    return `pressure-${String(level || "OK").toLowerCase()}`;
}

function itemDone(item) {
    return item.status === "FULL_DONE";
}

onMounted(loadDashboard);
</script>

<template>
    <section class="dashboard-page">
        <div class="dashboard-header">
            <div>
                <p class="eyebrow">Today Dashboard</p>
                <h2>{{ dashboard?.greeting || 'Hello' }}, {{ props.user?.username || 'User' }}</h2>
                <p class="header-meta">
                    Today {{ formatDate(dashboard?.today) }} {{ dashboard?.weekday || '' }}
                    <span>Recommended study time: {{ formatMinutes(summary.total_recommended_minutes) }}</span>
                </p>
            </div>
            <button class="icon-button" type="button" title="刷新 Dashboard" :disabled="loading" @click="loadDashboard">
                <RefreshCw :class="{ spinning: loading }" :size="18" stroke-width="2.2" />
            </button>
        </div>

        <p v-if="message" class="error-message">{{ message }}</p>

        <div class="overview-grid">
            <article v-for="card in overviewCards" :key="card.key" class="overview-card">
                <component :is="card.icon" :size="20" stroke-width="2.1" />
                <span>{{ card.label }}</span>
                <strong>{{ card.value }}</strong>
            </article>
        </div>

        <div class="dashboard-layout">
            <main class="today-column">
                <section class="section-panel plan-panel">
                    <div class="section-heading">
                        <div>
                            <p class="eyebrow">Today's Plans</p>
                            <h3>今天要做什么</h3>
                        </div>
                        <span :class="['pressure-pill', pressureClass(summary.pressure_level)]">
                            {{ formatRiskLevel(summary.pressure_level) }}
                        </span>
                    </div>

                    <div v-if="todayPlans.length === 0" class="empty-state">
                        <CalendarDays :size="34" stroke-width="1.8" />
                        <strong>今天还没有计划</strong>
                        <p>先为进行中的 Project 生成计划，Dashboard 会自动展示今日任务。</p>
                        <button type="button" @click="emit('open-plans')">打开 Plans</button>
                    </div>

                    <article v-for="plan in todayPlans" :key="plan.project_id" class="project-plan-card">
                        <div class="project-plan-header">
                            <div>
                                <h4>{{ plan.project_name }}</h4>
                                <p>Recommended: {{ formatMinutes(plan.recommended_minutes) }}</p>
                            </div>
                            <strong>{{ formatMinutes(plan.completed_minutes) }}</strong>
                        </div>

                        <div class="progress-track" aria-label="Project progress">
                            <span :style="{ width: `${Math.min(plan.progress_rate || 0, 100)}%` }"></span>
                        </div>

                        <ul class="plan-item-list">
                            <li v-for="item in plan.items" :key="item.plan_item_id" :class="{ done: itemDone(item) }">
                                <div class="item-title">
                                    <CheckCircle2 v-if="itemDone(item)" :size="18" stroke-width="2.2" />
                                    <XCircle v-else :size="18" stroke-width="2.2" />
                                    <span>{{ item.task_name }}</span>
                                </div>
                                <div class="item-actions">
                                    <span>{{ formatMinutes(item.actual_minutes) }} / {{ formatMinutes(item.recommended_minutes) }}</span>
                                    <button
                                        class="text-button"
                                        type="button"
                                        :disabled="savingItemId === item.plan_item_id || itemDone(item)"
                                        @click="quickComplete(plan, item)"
                                    >
                                        Complete
                                    </button>
                                    <button
                                        class="ghost-button"
                                        type="button"
                                        :disabled="savingItemId === item.plan_item_id || itemDone(item)"
                                        @click="quickSkip(plan, item)"
                                    >
                                        Skip
                                    </button>
                                </div>
                            </li>
                        </ul>
                    </article>
                </section>
            </main>

            <aside class="side-column">
                <section class="section-panel coach-panel">
                    <div class="section-heading">
                        <div>
                            <p class="eyebrow">AI Daily Coach</p>
                            <h3>今日建议</h3>
                        </div>
                        <Sparkles :size="20" stroke-width="2.1" />
                    </div>
                    <div class="coach-copy">
                        <strong>Today's Focus</strong>
                        <p>{{ aiSuggestion.focus || '暂无建议' }}</p>
                        <strong>Suggestion</strong>
                        <p>{{ aiSuggestion.suggestion || '完成一次打卡后会生成更明确的建议。' }}</p>
                        <strong>Motivation</strong>
                        <p>{{ aiSuggestion.motivation || 'Keep moving.' }}</p>
                    </div>
                </section>

                <section class="section-panel">
                    <div class="section-heading">
                        <div>
                            <p class="eyebrow">Active Projects</p>
                            <h3>进行中的项目</h3>
                        </div>
                        <FolderOpen :size="20" stroke-width="2.1" />
                    </div>
                    <div v-if="activeProjects.length === 0" class="small-empty">暂无进行中的 Project。</div>
                    <button
                        v-for="project in activeProjects"
                        :key="project.project_id"
                        class="active-project-row"
                        type="button"
                        @click="openProject(project.project_id)"
                    >
                        <span>
                            <strong>{{ project.project_name }}</strong>
                            <small>{{ project.remaining_days ?? '-' }} days left</small>
                        </span>
                        <span class="project-progress">
                            <em>{{ project.progress_rate || 0 }}%</em>
                            <small>{{ formatRiskLevel(project.risk_level) }}</small>
                        </span>
                    </button>
                </section>

                <section class="section-panel quick-stats">
                    <div class="section-heading">
                        <div>
                            <p class="eyebrow">Quick Analytics</p>
                            <h3>最近 7 天</h3>
                        </div>
                        <TrendingUp :size="20" stroke-width="2.1" />
                    </div>
                    <div class="stat-row">
                        <Target :size="18" stroke-width="2.1" />
                        <span>Completion Rate</span>
                        <strong>{{ recentStats.completion_rate || 0 }}%</strong>
                    </div>
                    <div class="stat-row">
                        <Clock3 :size="18" stroke-width="2.1" />
                        <span>Study Time</span>
                        <strong>{{ formatHours(recentStats.study_minutes) }}</strong>
                    </div>
                    <div class="stat-row">
                        <AlertTriangle :size="18" stroke-width="2.1" />
                        <span>Current Streak</span>
                        <strong>{{ recentStats.current_streak || 0 }} Days</strong>
                    </div>
                </section>
            </aside>
        </div>
    </section>
</template>

<style scoped>
.dashboard-page {
    display: grid;
    gap: 18px;
    color: #172033;
}

.dashboard-header,
.section-heading,
.project-plan-header,
.item-actions,
.stat-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
}

.eyebrow,
h2,
h3,
h4,
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

h4 {
    font-size: 17px;
}

button {
    border: 1px solid #c7d0df;
    border-radius: 6px;
    padding: 8px 11px;
    color: #172033;
    background: #ffffff;
    font: inherit;
    cursor: pointer;
}

button:disabled {
    cursor: not-allowed;
    opacity: 0.62;
}

.header-meta {
    margin-top: 6px;
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    color: #667085;
    font-size: 14px;
}

.icon-button {
    width: 36px;
    height: 36px;
    flex: 0 0 auto;
    border-color: transparent;
    padding: 0;
    display: inline-grid;
    place-items: center;
    color: #2563eb;
    background: transparent;
}

.icon-button:hover:not(:disabled) {
    background: #eef2ff;
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
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
}

.overview-card,
.section-panel {
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

.overview-card span {
    color: #667085;
    font-size: 13px;
    font-weight: 700;
}

.overview-card strong {
    font-size: 22px;
}

.dashboard-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 360px;
    gap: 16px;
    align-items: start;
}

.today-column,
.side-column {
    display: grid;
    gap: 16px;
}

.section-panel {
    padding: 16px;
    display: grid;
    gap: 14px;
}

.pressure-pill {
    border-radius: 999px;
    padding: 5px 10px;
    font-size: 12px;
    font-weight: 800;
}

.pressure-ok {
    color: #155eef;
    background: #eaf1ff;
}

.pressure-pressure {
    color: #b42318;
    background: #fee4e2;
}

.pressure-relaxed {
    color: #067647;
    background: #dcfae6;
}

.empty-state {
    min-height: 260px;
    display: grid;
    place-items: center;
    align-content: center;
    gap: 10px;
    color: #667085;
    text-align: center;
}

.empty-state strong {
    color: #172033;
}

.empty-state button,
.text-button {
    border-color: #2563eb;
    color: #ffffff;
    background: #2563eb;
    font-weight: 700;
}

.project-plan-card {
    border: 1px solid #d8dee9;
    border-radius: 8px;
    padding: 14px;
    display: grid;
    gap: 12px;
}

.project-plan-header p {
    margin-top: 4px;
    color: #667085;
    font-size: 13px;
}

.project-plan-header strong {
    color: #2563eb;
    font-size: 18px;
    white-space: nowrap;
}

.progress-track {
    height: 8px;
    border-radius: 999px;
    overflow: hidden;
    background: #eef2f7;
}

.progress-track span {
    height: 100%;
    display: block;
    border-radius: inherit;
    background: #2563eb;
}

.plan-item-list {
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
    list-style: none;
}

.plan-item-list li {
    border: 1px solid #e4e7ec;
    border-radius: 8px;
    padding: 10px;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 12px;
    align-items: center;
}

.plan-item-list li.done {
    background: #f6fef9;
}

.item-title {
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 700;
}

.item-title span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.item-title svg {
    flex: 0 0 auto;
    color: #98a2b3;
}

.done .item-title svg {
    color: #16a34a;
}

.item-actions span {
    color: #667085;
    font-size: 13px;
    font-weight: 700;
    white-space: nowrap;
}

.ghost-button {
    color: #2f3a4f;
    background: #f5f7fb;
    font-weight: 700;
}

.coach-panel svg {
    color: #2563eb;
}

.coach-copy {
    display: grid;
    gap: 7px;
}

.coach-copy strong {
    color: #172033;
    font-size: 13px;
}

.coach-copy p {
    color: #667085;
    font-size: 14px;
    line-height: 1.55;
}

.small-empty {
    color: #667085;
    font-size: 14px;
}

.active-project-row {
    width: 100%;
    border-color: #e4e7ec;
    padding: 11px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    text-align: left;
}

.active-project-row:hover {
    border-color: #2563eb;
    background: #f8fbff;
}

.active-project-row span,
.project-progress {
    display: grid;
    gap: 3px;
}

.active-project-row small {
    color: #667085;
    font-size: 12px;
}

.project-progress {
    justify-items: end;
}

.project-progress em {
    color: #2563eb;
    font-style: normal;
    font-weight: 800;
}

.quick-stats {
    gap: 10px;
}

.stat-row {
    border-top: 1px solid #e4e7ec;
    padding-top: 10px;
    color: #667085;
    font-size: 14px;
}

.stat-row svg {
    color: #2563eb;
}

.stat-row span {
    flex: 1;
}

.stat-row strong {
    color: #172033;
}

@media (max-width: 1100px) {
    .overview-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .dashboard-layout {
        grid-template-columns: 1fr;
    }
}

@media (max-width: 720px) {
    .dashboard-header,
    .project-plan-header,
    .item-actions {
        align-items: flex-start;
        flex-direction: column;
    }

    .overview-grid {
        grid-template-columns: 1fr;
    }

    .plan-item-list li {
        grid-template-columns: 1fr;
    }
}
</style>
