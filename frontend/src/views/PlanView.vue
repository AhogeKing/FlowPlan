<script setup>
import { CalendarPlus, CheckCircle2, CircleArrowLeft, Eye, RefreshCw, Trash2 } from "@lucide/vue";
import { computed, onMounted, ref, watch } from "vue";
import { listProjects } from "../api/project";
import { checkinPlanItem, deletePlanItemCheckin, generatePlan, getPlanByDate, listPlans } from "../api/plan";
import { listTasks } from "../api/task";

const emit = defineEmits(["open-project-tasks"]);

const projects = ref([]);
const plans = ref([]);
const tasks = ref([]);
const planDetailsByDate = ref({});
const detailPanelRef = ref(null);
const selectedProjectId = ref("");
const selectedPlanDate = ref("");
const selectedDetail = ref(null);
const checkinForms = ref({});
const loading = ref(false);
const detailLoading = ref(false);
const checkinSavingId = ref(null);
const checkinDeletingId = ref(null);
const generating = ref(false);
const message = ref("");
const planError = ref("");
const activeFilter = ref("all");
const initializing = ref(false);

const selectedProject = computed(() => {
    return projects.value.find(project => String(project.id) === String(selectedProjectId.value)) || null;
});

const statusFilters = computed(() => {
    const total = plans.value.length;
    const notDone = plans.value.filter(plan => planFilterStatus(plan) === "not_done").length;
    const overdue = plans.value.filter(plan => planFilterStatus(plan) === "overdue").length;
    const done = plans.value.filter(plan => planFilterStatus(plan) === "done").length;

    return [
        { key: "all", label: "全部计划", count: total },
        { key: "not_done", label: "未完成", count: notDone },
        { key: "overdue", label: "已过期", count: overdue },
        { key: "done", label: "已完成", count: done }
    ];
});

const filteredPlans = computed(() => {
    if (activeFilter.value === "all") {
        return plans.value;
    }
    return plans.value.filter(plan => planFilterStatus(plan) === activeFilter.value);
});

const selectedDetailItems = computed(() => {
    const items = selectedDetail.value?.items || [];
    return items.map(item => ({
        ...item,
        taskTitle: taskTitle(item.task_id)
    }));
});

async function loadPlanPage() {
    loading.value = true;
    message.value = "";
    planError.value = "";
    initializing.value = true;
    try {
        const projectList = await listProjects();
        projects.value = projectList;

        if (!selectedProjectId.value && projectList.length > 0) {
            selectedProjectId.value = String(projectList[0].id);
        }

        if (!selectedProjectId.value) {
            plans.value = [];
            tasks.value = [];
            selectedDetail.value = null;
            selectedPlanDate.value = "";
            return;
        }

        await loadCurrentProjectPlans();
    } finally {
        initializing.value = false;
        loading.value = false;
    }
}

async function loadCurrentProjectPlans() {
    if (!selectedProjectId.value) {
        return;
    }

    planError.value = "";
    const [planResult, taskResult] = await Promise.allSettled([
        listPlans(selectedProjectId.value, { silent: true }),
        listTasks(selectedProjectId.value)
    ]);

    if (planResult.status === "fulfilled") {
        plans.value = planResult.value;
    } else {
        plans.value = [];
        planDetailsByDate.value = {};
        planError.value = readErrorMessage(planResult.reason, "计划列表暂时无法加载，请确认后端 Plan 模块已启动");
    }

    if (taskResult.status === "fulfilled") {
        tasks.value = taskResult.value;
    } else {
        tasks.value = [];
    }

    if (selectedPlanDate.value && !plans.value.some(plan => plan.plan_date === selectedPlanDate.value)) {
        selectedPlanDate.value = "";
        selectedDetail.value = null;
    }

    if (planResult.status === "fulfilled") {
        await loadPlanSummaries(plans.value);
        applyDefaultPlanDetail(plans.value);
    }
}

async function loadPlanSummaries(planList) {
    if (!selectedProjectId.value || planList.length === 0) {
        planDetailsByDate.value = {};
        return;
    }

    const detailEntries = await Promise.allSettled(
        planList.map(async plan => {
            const detail = await getPlanByDate(selectedProjectId.value, plan.plan_date, { silent: true });
            return [plan.plan_date, detail];
        })
    );

    planDetailsByDate.value = Object.fromEntries(
        detailEntries
            .filter(result => result.status === "fulfilled")
            .map(result => result.value)
    );
}

function applyDefaultPlanDetail(planList) {
    if (planList.length === 0) {
        selectedPlanDate.value = "";
        selectedDetail.value = null;
        return;
    }

    const existingPlan = planList.find(plan => plan.plan_date === selectedPlanDate.value);
    const defaultPlan = existingPlan || findTodayPlan(planList) || planList[0];
    selectedPlanDate.value = defaultPlan.plan_date;
    selectedDetail.value = planDetailsByDate.value[defaultPlan.plan_date] || null;
    prepareCheckinForms(selectedDetail.value);
}

function findTodayPlan(planList) {
    const today = formatLocalDate(new Date());
    return planList.find(plan => plan.plan_date === today);
}

function formatLocalDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

async function refreshPlans() {
    loading.value = true;
    message.value = "";
    try {
        await loadCurrentProjectPlans();
    } finally {
        loading.value = false;
    }
}

async function handleGeneratePlan() {
    if (!selectedProjectId.value) {
        return;
    }

    generating.value = true;
    message.value = "";
    planError.value = "";
    try {
        const result = await generatePlan(selectedProjectId.value, { silent: true });
        message.value = `计划已生成：${result.plan_count} 天，${result.item_count} 项，未排期 ${formatMinutes(result.unscheduled_minutes)}，${formatRiskLevel(result.risk_level)}`;
        selectedPlanDate.value = "";
        selectedDetail.value = null;
        await loadCurrentProjectPlans();
    } catch (error) {
        planError.value = readErrorMessage(error, "生成计划失败，请确认当前 Project 下已有可排期的 Task");
    } finally {
        generating.value = false;
    }
}

async function loadPlanDetail(plan) {
    selectedPlanDate.value = plan.plan_date;
    selectedDetail.value = planDetailsByDate.value[plan.plan_date] || null;
    detailLoading.value = true;
    planError.value = "";
    try {
        const detail = await getPlanByDate(selectedProjectId.value, plan.plan_date, { silent: true });
        selectedDetail.value = detail;
        planDetailsByDate.value = {
            ...planDetailsByDate.value,
            [plan.plan_date]: detail
        };
        prepareCheckinForms(detail);
    } catch (error) {
        planError.value = readErrorMessage(error, "加载计划明细失败");
    } finally {
        detailLoading.value = false;
    }
}

async function showPlanDetail(plan) {
    await loadPlanDetail(plan);
    detailPanelRef.value?.scrollIntoView({ behavior: "smooth", block: "start" });
}

async function handleCheckin(item) {
    if (!selectedProjectId.value || !item?.id) {
        return;
    }

    const form = checkinForms.value[item.id] || {};
    const completedMinutes = form.completed_minutes === "" || form.completed_minutes == null
        ? null
        : Number(form.completed_minutes);

    checkinSavingId.value = item.id;
    message.value = "";
    planError.value = "";
    try {
        const detail = await checkinPlanItem(selectedProjectId.value, item.id, {
            completed_minutes: Number.isNaN(completedMinutes) ? null : completedMinutes,
            checkin_date: form.checkin_date || null,
            note: form.note || null
        }, { silent: true });
        applyUpdatedPlanDetail(detail);
        message.value = "打卡已保存";
    } catch (error) {
        planError.value = readErrorMessage(error, "打卡失败");
    } finally {
        checkinSavingId.value = null;
    }
}

async function handleDeleteCheckin(item) {
    if (!selectedProjectId.value || !item?.id || !itemCheckinRecord(item)) {
        return;
    }

    checkinDeletingId.value = item.id;
    message.value = "";
    planError.value = "";
    try {
        const detail = await deletePlanItemCheckin(selectedProjectId.value, item.id, { silent: true });
        applyUpdatedPlanDetail(detail);
        message.value = "打卡已撤销";
    } catch (error) {
        planError.value = readErrorMessage(error, "撤销打卡失败");
    } finally {
        checkinDeletingId.value = null;
    }
}

function applyUpdatedPlanDetail(detail) {
    if (!detail?.plan) {
        return;
    }

    selectedDetail.value = detail;
    selectedPlanDate.value = detail.plan.plan_date;
    planDetailsByDate.value = {
        ...planDetailsByDate.value,
        [detail.plan.plan_date]: detail
    };
    plans.value = plans.value.map(plan => plan.id === detail.plan.id ? detail.plan : plan);
    prepareCheckinForms(detail);
}

function prepareCheckinForms(detail) {
    if (!detail?.items) {
        return;
    }

    const nextForms = { ...checkinForms.value };
    for (const item of detail.items) {
        const record = itemCheckinRecord(item);
        nextForms[item.id] = {
            completed_minutes: record?.completed_minutes ?? "",
            checkin_date: record?.checkin_date || formatLocalDate(new Date()),
            note: record?.note || ""
        };
    }
    checkinForms.value = nextForms;
}

function itemCheckinRecord(item) {
    return item?.checkin_record || item?.checkinRecord || null;
}

function backToProjectTasks() {
    if (selectedProjectId.value) {
        emit("open-project-tasks", selectedProjectId.value);
    }
}

function taskTitle(taskId) {
    const task = tasks.value.find(item => String(item.id) === String(taskId));
    return task?.title || `Task #${taskId}`;
}

function taskMeta(taskId) {
    return tasks.value.find(item => String(item.id) === String(taskId)) || null;
}

function planItems(plan) {
    return planDetailsByDate.value[plan.plan_date]?.items || [];
}

function planTaskCount(plan) {
    return new Set(planItems(plan).map(item => item.task_id)).size;
}

function planTaskPreview(plan) {
    return planItems(plan).slice(0, 3);
}

function hiddenTaskCount(plan) {
    return Math.max(planItems(plan).length - planTaskPreview(plan).length, 0);
}

function completionPercent(plan) {
    const recommended = Number(plan.total_recommended_minutes || 0);
    if (recommended <= 0) {
        return 0;
    }
    return Math.min(100, Math.round(Number(plan.total_actual_minutes || 0) * 100 / recommended));
}

function formatDateLabel(dateText) {
    if (!dateText) {
        return "-";
    }
    const date = new Date(`${dateText}T00:00:00`);
    if (Number.isNaN(date.getTime())) {
        return dateText;
    }
    return new Intl.DateTimeFormat("zh-CN", { weekday: "short" }).format(date);
}

function normalizeStatus(status) {
    return String(status || "").toLowerCase();
}

function formatStatus(status) {
    const labels = {
        not_done: "未完成",
        partial_done: "部分完成",
        full_done: "已完成",
        done: "已完成",
        overdue: "已过期"
    };
    return labels[normalizeStatus(status)] || status || "未知";
}

function isBeforeToday(dateText) {
    return Boolean(dateText) && dateText < formatLocalDate(new Date());
}

function isPlanCompleted(plan) {
    const status = normalizeStatus(plan.status);
    const recommended = Number(plan.total_recommended_minutes || 0);
    const actual = Number(plan.total_actual_minutes || 0);
    return status === "done" || status === "full_done" || (recommended > 0 && actual >= recommended);
}

function isItemCompleted(item) {
    const status = normalizeStatus(item.status);
    const recommended = Number(item.recommended_minutes || 0);
    const actual = Number(item.actual_minutes || 0);
    return status === "done" || status === "full_done" || (recommended > 0 && actual >= recommended);
}

function planDisplayStatus(plan) {
    if (isBeforeToday(plan.plan_date)) {
        return isPlanCompleted(plan) ? "done" : "overdue";
    }
    return normalizeStatus(plan.status);
}

function itemDisplayStatus(item) {
    const planDate = selectedDetail.value?.plan?.plan_date || selectedPlanDate.value;
    if (isBeforeToday(planDate)) {
        return isItemCompleted(item) ? "done" : "overdue";
    }
    return normalizeStatus(item.status);
}

function planFilterStatus(plan) {
    const status = planDisplayStatus(plan);
    if (status === "partial_done") {
        return "not_done";
    }
    if (status === "full_done") {
        return "done";
    }
    return status;
}

function formatRiskLevel(riskLevel) {
    const labels = {
        relaxed: "偏松",
        ok: "正常",
        pressure: "有压力"
    };
    return labels[String(riskLevel || "").toLowerCase()] || riskLevel || "未知";
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

function formatSessionMinutes(minutes) {
    return minutes ? formatMinutes(minutes) : "默认";
}

function readErrorMessage(error, fallback) {
    return error?.msg || error?.response?.data?.msg || fallback;
}

onMounted(loadPlanPage);

watch(selectedProjectId, async (projectId, oldProjectId) => {
    if (initializing.value || !projectId || projectId === oldProjectId) {
        return;
    }

    activeFilter.value = "all";
    selectedPlanDate.value = "";
    selectedDetail.value = null;
    loading.value = true;
    message.value = "";
    planError.value = "";
    try {
        await loadCurrentProjectPlans();
    } finally {
        loading.value = false;
    }
});
</script>

<template>
    <section class="plans-page">
        <div class="page-header">
            <div>
                <p class="eyebrow">Plans</p>
                <h2>{{ selectedProject ? `${selectedProject.name} 计划安排` : '计划安排' }}</h2>
            </div>
        </div>

        <div v-if="message || planError" class="feedback-stack">
            <p v-if="message" class="message">{{ message }}</p>
            <p v-if="planError" class="error-message">{{ planError }}</p>
        </div>

        <div class="list-panel">
            <div class="list-toolbar">
                <div class="filter-tabs" aria-label="计划状态筛选">
                    <button
                        v-for="filter in statusFilters"
                        :key="filter.key"
                        :class="['filter-tab', { active: activeFilter === filter.key }]"
                        type="button"
                        @click="activeFilter = filter.key"
                    >
                        <span>{{ filter.label }}</span>
                        <strong>{{ filter.count }}</strong>
                    </button>
                </div>
                <div class="toolbar-actions">
                    <select v-model="selectedProjectId" class="project-select" :disabled="projects.length === 0">
                        <option value="" disabled>请选择 Project</option>
                        <option v-for="project in projects" :key="project.id" :value="String(project.id)">
                            {{ project.name }}
                        </option>
                    </select>
                    <button class="icon-button" type="button" title="返回该 Project 的任务列表" :disabled="!selectedProjectId" @click="backToProjectTasks">
                        <CircleArrowLeft :size="18" stroke-width="2.2" />
                    </button>
                    <button
                        class="generate-plan-button"
                        type="button"
                        :disabled="generating || !selectedProjectId"
                        @click="handleGeneratePlan"
                    >
                        <CalendarPlus :size="16" stroke-width="2.1" />
                        <span>{{ generating ? 'Generating...' : 'Generate plan' }}</span>
                    </button>
                    <button class="refresh-icon-button" type="button" title="刷新计划" :disabled="loading || !selectedProjectId" @click="refreshPlans">
                        <RefreshCw :class="{ spinning: loading }" :size="18" stroke-width="2.2" />
                    </button>
                </div>
            </div>

            <section v-if="selectedPlanDate || detailLoading" ref="detailPanelRef" class="detail-panel">
                <div class="detail-header">
                    <div>
                        <p class="eyebrow">Detail</p>
                        <h3>{{ selectedPlanDate }} 计划明细</h3>
                    </div>
                    <div v-if="selectedDetail?.plan" class="detail-summary">
                        <span>建议 {{ formatMinutes(selectedDetail.plan.total_recommended_minutes) }}</span>
                        <span>实际 {{ formatMinutes(selectedDetail.plan.total_actual_minutes) }}</span>
                    </div>
                </div>

                <div v-if="detailLoading" class="empty-state">加载明细中...</div>
                <div v-else-if="selectedDetailItems.length === 0" class="empty-state">该日期暂无计划项</div>
                <div v-else class="detail-list">
                    <div class="detail-row detail-table-header">
                        <span>任务</span>
                        <span>建议投入</span>
                        <span>实际投入</span>
                        <span>状态</span>
                        <span>打卡</span>
                        <span>说明</span>
                    </div>
                    <div v-for="item in selectedDetailItems" :key="item.id" class="detail-row">
                        <div class="detail-task-cell">
                            <strong>{{ item.taskTitle }}</strong>
                            <span v-if="taskMeta(item.task_id)">
                                权重 {{ taskMeta(item.task_id).weight }} · 单次 {{ formatSessionMinutes(taskMeta(item.task_id).min_session_minutes) }}
                            </span>
                        </div>
                        <span class="duration-text">{{ formatMinutes(item.recommended_minutes) }}</span>
                        <span class="duration-text">{{ formatMinutes(item.actual_minutes) }}</span>
                        <span :class="['status-badge', `status-${itemDisplayStatus(item)}`]">
                            {{ formatStatus(itemDisplayStatus(item)) }}
                        </span>
                        <div v-if="checkinForms[item.id]" class="checkin-cell">
                            <div v-if="itemCheckinRecord(item)" class="checkin-record-meta">
                                已打卡：{{ itemCheckinRecord(item).checkin_date }} · {{ formatMinutes(itemCheckinRecord(item).completed_minutes) }}
                                <span v-if="itemCheckinRecord(item).note"> · {{ itemCheckinRecord(item).note }}</span>
                            </div>
                            <input
                                v-model="checkinForms[item.id].completed_minutes"
                                :placeholder="formatMinutes(item.recommended_minutes)"
                                min="0"
                                type="number"
                                aria-label="实际投入分钟"
                            >
                            <input
                                v-model="checkinForms[item.id].checkin_date"
                                type="date"
                                aria-label="打卡日期"
                            >
                            <input
                                v-model="checkinForms[item.id].note"
                                class="checkin-note-input"
                                type="text"
                                aria-label="打卡备注"
                                placeholder="备注"
                            >
                            <button
                                class="checkin-button"
                                type="button"
                                :disabled="checkinSavingId === item.id || checkinDeletingId === item.id"
                                @click="handleCheckin(item)"
                            >
                                <CheckCircle2 :size="15" stroke-width="2.2" />
                                <span>{{ checkinSavingId === item.id ? '保存中' : (itemCheckinRecord(item) ? '修改' : '打卡') }}</span>
                            </button>
                            <button
                                v-if="itemCheckinRecord(item)"
                                class="checkin-delete-button"
                                type="button"
                                :disabled="checkinSavingId === item.id || checkinDeletingId === item.id"
                                @click="handleDeleteCheckin(item)"
                            >
                                <Trash2 :size="15" stroke-width="2.2" />
                                <span>{{ checkinDeletingId === item.id ? '撤销中' : '撤销' }}</span>
                            </button>
                        </div>
                        <span class="reason-text">{{ item.reason || '-' }}</span>
                    </div>
                </div>
            </section>

            <div class="table-shell">
                <div class="plan-row table-header">
                    <span>日期</span>
                    <span>Project / Task</span>
                    <span>计划概览</span>
                    <span>投入</span>
                    <span>状态</span>
                    <span>操作</span>
                </div>

                <div v-if="loading" class="empty-state">加载中...</div>
                <div v-else-if="projects.length === 0" class="empty-state">暂无项目，先创建 Project 后再生成 Plan</div>
                <div v-else-if="filteredPlans.length === 0" class="empty-state">暂无计划，点击 Generate plan 生成排期</div>

                <template v-else>
                    <article v-for="plan in filteredPlans" :key="plan.id" class="plan-item">
                        <div :class="['plan-row', { selected: selectedPlanDate === plan.plan_date }]">
                            <div class="date-cell">
                                <button class="plan-date-button" type="button" @click="loadPlanDetail(plan)">
                                    {{ plan.plan_date }}
                                </button>
                                <span>{{ formatDateLabel(plan.plan_date) }}</span>
                            </div>
                            <div class="plan-scope-cell">
                                <strong class="project-name">{{ selectedProject?.name || '-' }}</strong>
                                <div v-if="planItems(plan).length > 0" class="task-chip-list">
                                    <span v-for="item in planTaskPreview(plan)" :key="item.id" class="task-chip">
                                        {{ taskTitle(item.task_id) }}
                                        <small>{{ formatMinutes(item.recommended_minutes) }}</small>
                                    </span>
                                    <span v-if="hiddenTaskCount(plan) > 0" class="task-chip muted-chip">
                                        +{{ hiddenTaskCount(plan) }}
                                    </span>
                                </div>
                                <span v-else class="muted-text">点击查看计划明细</span>
                            </div>
                            <div class="plan-summary-cell">
                                <strong>{{ planItems(plan).length || '-' }} 项</strong>
                                <span>{{ planTaskCount(plan) || '-' }} 个 Task</span>
                            </div>
                            <div class="minutes-cell">
                                <strong>建议 {{ formatMinutes(plan.total_recommended_minutes) }}</strong>
                                <span>实际 {{ formatMinutes(plan.total_actual_minutes) }}</span>
                                <small>完成率 {{ completionPercent(plan) }}%</small>
                            </div>
                            <span :class="['status-badge', `status-${planDisplayStatus(plan)}`]">
                                {{ formatStatus(planDisplayStatus(plan)) }}
                            </span>
                            <div class="row-actions">
                                <button class="icon-button" type="button" title="查看该日计划明细" @click="showPlanDetail(plan)">
                                    <Eye :size="18" stroke-width="2.2" />
                                </button>
                            </div>
                        </div>
                    </article>
                </template>
            </div>
        </div>
    </section>
</template>

<style scoped>
.plans-page {
    display: grid;
    gap: 18px;
    color: #172033;
}

.page-header,
.list-toolbar,
.detail-header {
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
    font-size: 19px;
}

input,
select,
button {
    border-radius: 6px;
    font: inherit;
}

input,
select {
    min-width: 190px;
    border: 1px solid #c7d0df;
    padding: 8px 11px;
    color: #172033;
    background: #ffffff;
}

button {
    border: 1px solid #c7d0df;
    padding: 10px 12px;
    color: #172033;
    background: #ffffff;
    cursor: pointer;
}

button:disabled,
select:disabled {
    cursor: not-allowed;
    opacity: 0.7;
}

.message {
    color: #067647;
    font-size: 14px;
}

.error-message {
    color: #b42318;
    font-size: 14px;
}

.feedback-stack {
    display: grid;
    gap: 8px;
}

.list-panel {
    display: grid;
    gap: 14px;
}

.filter-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
}

.filter-tab {
    border-color: #c7d0df;
    border-radius: 999px;
    padding: 7px 14px;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: #2f3a4f;
    background: #f5f7fb;
    font-weight: 650;
}

.filter-tab.active {
    border-color: #2563eb;
    color: #ffffff;
    background: #2563eb;
}

.filter-tab strong {
    font-size: 13px;
}

.toolbar-actions,
.row-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 0 0 auto;
}

.generate-plan-button {
    border-color: #2563eb;
    min-height: 34px;
    padding: 7px 12px 7px 10px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: #ffffff;
    background: #2563eb;
    box-shadow: 0 6px 14px rgba(37, 99, 235, 0.18);
    font-size: 14px;
    font-weight: 550;
    transition: background 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.generate-plan-button:hover:not(:disabled) {
    background: #1d4ed8;
    box-shadow: 0 8px 18px rgba(37, 99, 235, 0.24);
    transform: translateY(-1px);
}

.generate-plan-button:active {
    transform: translateY(0);
}

.refresh-icon-button {
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

.refresh-icon-button:hover:not(:disabled) {
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

.table-shell,
.detail-panel {
    border: 1px solid #c7d0df;
    border-radius: 8px;
    overflow: hidden;
    background: #ffffff;
}

.plan-row {
    display: grid;
    grid-template-columns: minmax(120px, 0.75fr) minmax(300px, 1.85fr) minmax(110px, 0.7fr) minmax(190px, 1fr) 90px 80px;
    gap: 16px;
    align-items: center;
    padding: 16px 18px;
}

.plan-row.selected {
    background: #f8fafc;
}

.table-header,
.detail-table-header {
    color: #2f3a4f;
    background: #eef2f7;
    font-weight: 750;
}

.table-header span:last-child {
    text-align: center;
}

.plan-item + .plan-item,
.detail-row + .detail-row {
    border-top: 1px solid #d8dee9;
}

.plan-date-button {
    width: fit-content;
    border: 0;
    padding: 0;
    color: #172033;
    background: transparent;
    font: inherit;
    font-weight: 700;
    text-align: left;
}

.date-cell,
.plan-scope-cell,
.plan-summary-cell,
.minutes-cell,
.detail-task-cell {
    min-width: 0;
    display: grid;
    gap: 5px;
}

.date-cell span,
.plan-summary-cell span,
.minutes-cell span,
.minutes-cell small,
.detail-task-cell span,
.muted-text {
    color: #667085;
    font-size: 13px;
}

.plan-date-button:hover {
    color: #2563eb;
    text-decoration: underline;
    text-underline-offset: 3px;
}

.project-name {
    color: #2f3a4f;
    font-weight: 650;
}

.task-chip-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.task-chip {
    max-width: 100%;
    border-radius: 999px;
    padding: 5px 9px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: #1849a9;
    background: #dbeafe;
    font-size: 13px;
    font-weight: 700;
}

.task-chip small {
    color: #2f3a4f;
    font-size: 12px;
    font-weight: 700;
}

.muted-chip {
    color: #2f3a4f;
    background: #eef2f7;
}

.minutes-cell strong,
.plan-summary-cell strong {
    color: #172033;
}

.duration-text,
.detail-summary span,
.minutes-cell strong,
.minutes-cell span,
.minutes-cell small,
.task-chip small {
    white-space: nowrap;
}

.status-badge {
    width: fit-content;
    border-radius: 999px;
    padding: 5px 10px;
    font-size: 13px;
    font-weight: 700;
}

.status-not_done {
    color: #1849a9;
    background: #dbeafe;
}

.status-done {
    color: #067647;
    background: #dcfae6;
}

.status-full_done {
    color: #067647;
    background: #dcfae6;
}

.status-partial_done {
    color: #b54708;
    background: #fef0c7;
}

.status-overdue {
    color: #b42318;
    background: #fee4e2;
}

.row-actions {
    justify-content: center;
}

.icon-button {
    width: 36px;
    height: 36px;
    border-color: transparent;
    padding: 0;
    display: inline-grid;
    place-items: center;
    color: #667085;
    background: transparent;
    line-height: 1;
}

.icon-button:hover {
    color: #172033;
    background: #eef2f7;
}

.empty-state {
    padding: 42px 18px;
    color: #667085;
    text-align: center;
}

.detail-panel {
    display: grid;
}

.detail-header {
    padding: 16px 18px;
    border-bottom: 1px solid #d8dee9;
}

.detail-summary {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 8px;
    color: #2f3a4f;
    font-size: 14px;
    font-weight: 700;
}

.detail-summary span {
    border-radius: 999px;
    padding: 6px 10px;
    background: #f5f7fb;
}

.detail-list {
    display: grid;
}

.detail-row {
    display: grid;
    grid-template-columns: minmax(190px, 1.05fr) minmax(128px, 0.65fr) minmax(128px, 0.65fr) 90px minmax(540px, 2fr) minmax(250px, 1fr);
    gap: 16px;
    align-items: center;
    padding: 14px 18px;
}

.checkin-cell {
    min-width: 0;
    display: grid;
    grid-template-columns: minmax(78px, 0.7fr) minmax(138px, 1fr) minmax(130px, 1fr) max-content max-content;
    gap: 8px;
    align-items: center;
}

.checkin-record-meta {
    grid-column: 1 / -1;
    color: #667085;
    font-size: 12px;
    font-weight: 700;
}

.checkin-cell input {
    min-width: 0;
    width: 100%;
    padding: 8px 9px;
    font-size: 13px;
}

.checkin-button,
.checkin-delete-button {
    border-color: #2563eb;
    min-height: 34px;
    padding: 7px 10px;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: #ffffff;
    background: #2563eb;
    font-size: 13px;
    font-weight: 700;
    white-space: nowrap;
}

.checkin-button:hover:not(:disabled) {
    background: #1d4ed8;
}

.checkin-delete-button {
    border-color: #fecaca;
    color: #dc2626;
    background: #ffffff;
}

.checkin-delete-button:hover:not(:disabled) {
    background: #fef2f2;
}

.reason-text {
    color: #667085;
    font-size: 14px;
    line-height: 1.45;
    overflow-wrap: anywhere;
}

@media (max-width: 980px) {
    .list-toolbar,
    .detail-header {
        align-items: flex-start;
        flex-direction: column;
    }

    .toolbar-actions {
        align-items: flex-start;
        flex-wrap: wrap;
    }

    .project-select {
        width: 100%;
    }

    .plan-row,
    .detail-row,
    .checkin-cell {
        grid-template-columns: 1fr;
    }

    .row-actions,
    .detail-summary {
        justify-content: flex-start;
    }
}
</style>
