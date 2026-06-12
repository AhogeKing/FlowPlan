<script setup>
import { CircleArrowRight, CirclePlus, RefreshCw, Settings, Trash2 } from "@lucide/vue";
import { computed, onMounted, reactive, ref } from "vue";
import { addProject, deleteProject, listProjects, updateProject } from "../api/project";

const emit = defineEmits(["open-tasks"]);

const projects = ref([]);
const loading = ref(false);
const saving = ref(false);
const message = ref("");
const activeFilter = ref("all");
const editingId = ref(null);
const showCreateForm = ref(false);

const form = reactive({
    name: "",
    description: "",
    begin_date: "",
    deadline: ""
});

const editForm = reactive({
    name: "",
    description: "",
    begin_date: "",
    deadline: ""
});

const statusFilters = computed(() => {
    const total = projects.value.length;
    const inProgress = projects.value.filter(project => normalizeStatus(project.status) === "in_progress").length;
    const notStarted = projects.value.filter(project => normalizeStatus(project.status) === "not_started").length;
    const done = projects.value.filter(project => normalizeStatus(project.status) === "done").length;

    return [
        { key: "all", label: "所有项目", count: total },
        { key: "in_progress", label: "进行中", count: inProgress },
        { key: "not_started", label: "未开始", count: notStarted },
        { key: "done", label: "已完成", count: done }
    ];
});

const filteredProjects = computed(() => {
    if (activeFilter.value === "all") {
        return projects.value;
    }
    return projects.value.filter(project => normalizeStatus(project.status) === activeFilter.value);
});

async function loadProjects() {
    loading.value = true;
    try {
        projects.value = await listProjects();
    } finally {
        loading.value = false;
    }
}

async function handleAdd() {
    message.value = "";
    saving.value = true;
    try {
        await addProject({
            name: form.name,
            description: form.description,
            begin_date: form.begin_date,
            deadline: form.deadline
        });
        resetCreateForm();
        showCreateForm.value = false;
        message.value = "项目已添加";
        await loadProjects();
    } finally {
        saving.value = false;
    }
}

function toggleCreateForm() {
    showCreateForm.value = !showCreateForm.value;
    message.value = "";
}

function cancelCreate() {
    showCreateForm.value = false;
    resetCreateForm();
}

function startEdit(project) {
    editingId.value = project.id;
    editForm.name = project.name || "";
    editForm.description = project.description || "";
    editForm.begin_date = project.begin_date || "";
    editForm.deadline = project.deadline || "";
    message.value = "";
}

function cancelEdit() {
    editingId.value = null;
    resetEditForm();
}

async function handleUpdate(project) {
    message.value = "";
    saving.value = true;
    try {
        await updateProject(project.id, {
            name: editForm.name,
            description: editForm.description,
            begin_date: editForm.begin_date,
            deadline: editForm.deadline
        });
        editingId.value = null;
        resetEditForm();
        message.value = "项目已更新";
        await loadProjects();
    } finally {
        saving.value = false;
    }
}

async function handleDelete(project) {
    const confirmed = window.confirm(`确定删除项目「${project.name}」吗？`);
    if (!confirmed) {
        return;
    }

    message.value = "";
    saving.value = true;
    try {
        await deleteProject(project.id);
        message.value = "项目已删除";
        if (editingId.value === project.id) {
            cancelEdit();
        }
        await loadProjects();
    } finally {
        saving.value = false;
    }
}

function openTasks(project) {
    emit("open-tasks", project);
}

function resetCreateForm() {
    form.name = "";
    form.description = "";
    form.begin_date = "";
    form.deadline = "";
}

function resetEditForm() {
    editForm.name = "";
    editForm.description = "";
    editForm.begin_date = "";
    editForm.deadline = "";
}

function normalizeStatus(status) {
    const normalized = String(status || "").trim().toLowerCase();
    if (normalized === "completed" || normalized === "finished") {
        return "done";
    }
    return normalized;
}

function formatStatus(status) {
    const labels = {
        not_started: "未开始",
        in_progress: "进行中",
        done: "已完成"
    };
    return labels[normalizeStatus(status)] || status || "未知";
}

function normalizeRiskLevel(riskLevel) {
    return String(riskLevel || "").toUpperCase();
}

function formatRiskLevel(riskLevel) {
    const labels = {
        RELAXED: "偏宽松",
        OK: "正常",
        PRESSURE: "有压力"
    };
    return labels[normalizeRiskLevel(riskLevel)] || riskLevel || "未知";
}

onMounted(loadProjects);
</script>

<template>
    <section class="projects-page">
        <div class="page-header">
            <div>
                <p class="eyebrow">Projects</p>
                <h2>项目列表</h2>
            </div>
        </div>

        <p v-if="message" class="message">{{ message }}</p>

        <div class="list-panel">
            <div class="list-toolbar">
                <div class="filter-tabs" aria-label="项目状态筛选">
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
                    <button class="add-project-button" type="button" :aria-pressed="showCreateForm" @click="toggleCreateForm">
                        <CirclePlus :size="16" stroke-width="2.1" />
                        <span>Add project</span>
                    </button>
                    <button class="refresh-icon-button" type="button" title="刷新项目" :disabled="loading" @click="loadProjects">
                        <RefreshCw :class="{ spinning: loading }" :size="18" stroke-width="2.2" />
                    </button>
                </div>
            </div>

            <form v-if="showCreateForm" class="project-form create-panel" @submit.prevent="handleAdd">
                <label>
                    <span>项目名称（必填）</span>
                    <input v-model.trim="form.name" required placeholder="请输入项目名称">
                </label>
                <label>
                    <span>项目描述（选填）</span>
                    <input v-model.trim="form.description" placeholder="请输入项目描述">
                </label>
                <label>
                    <span>开始日期（选填）</span>
                    <input v-model="form.begin_date" type="date">
                </label>
                <label>
                    <span>截止日期（必填）</span>
                    <input v-model="form.deadline" required type="date">
                </label>
                <div class="create-actions">
                    <button type="submit" :disabled="saving">{{ saving ? '保存中...' : '添加Project' }}</button>
                    <button class="secondary-button" type="button" @click="cancelCreate">取消</button>
                </div>
            </form>

            <div class="table-shell">
                <div class="project-row table-header">
                    <span>名称</span>
                    <span>周期</span>
                    <span>状态</span>
                    <span>风险</span>
                    <span>操作</span>
                </div>

                <div v-if="loading" class="empty-state">加载中...</div>
                <div v-else-if="filteredProjects.length === 0" class="empty-state">暂无项目</div>

                <template v-else>
                    <article
                        v-for="project in filteredProjects"
                        :key="project.id"
                        class="project-item"
                    >
                        <div class="project-row">
                            <div class="project-main">
                                <button class="project-link-button" type="button" @click="openTasks(project)">
                                    {{ project.name }}
                                </button>
                                <p>{{ project.description || '暂无描述' }}</p>
                                <span v-if="project.need_replan" class="replan-hint">需要重新生成计划</span>
                            </div>
                            <div class="project-dates">
                                <span><strong>开始</strong>{{ project.begin_date || '未设置' }}</span>
                                <span><strong>截止</strong>{{ project.deadline }}</span>
                            </div>
                            <span :class="['status-badge', `status-${normalizeStatus(project.status)}`]">
                                {{ formatStatus(project.status) }}
                            </span>
                            <span :class="['risk-badge', `risk-${normalizeRiskLevel(project.risk_level).toLowerCase()}`]">
                                {{ formatRiskLevel(project.risk_level) }}
                            </span>
                            <div class="row-actions">
                                <button class="icon-button" type="button" title="查看任务" @click="openTasks(project)">
                                    <CircleArrowRight :size="18" stroke-width="2.2" />
                                </button>
                                <button class="icon-button" type="button" title="修改项目" @click="startEdit(project)">
                                    <Settings :size="18" stroke-width="2.2" />
                                </button>
                                <button class="icon-button danger-button" type="button" title="删除项目" @click="handleDelete(project)">
                                    <Trash2 :size="18" stroke-width="2.2" />
                                </button>
                            </div>
                        </div>

                        <form
                            v-if="editingId === project.id"
                            class="edit-form"
                            @submit.prevent="handleUpdate(project)"
                        >
                            <label class="edit-field edit-field-wide">
                                <span>名称：</span>
                                <input v-model.trim="editForm.name" required placeholder="项目名称">
                            </label>
                            <label class="edit-field edit-field-wide">
                                <span>描述：</span>
                                <input v-model.trim="editForm.description" placeholder="项目描述">
                            </label>
                            <label class="edit-field">
                                <span>开始日期：</span>
                                <input v-model="editForm.begin_date" type="date">
                            </label>
                            <label class="edit-field">
                                <span>截止日期：</span>
                                <input v-model="editForm.deadline" required type="date">
                            </label>
                            <div class="edit-actions">
                                <button type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
                                <button class="secondary-button" type="button" @click="cancelEdit">取消</button>
                            </div>
                        </form>
                    </article>
                </template>
            </div>
        </div>
    </section>
</template>

<style scoped>
.projects-page {
    display: grid;
    gap: 18px;
    color: #172033;
}

.page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.eyebrow,
h2,
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

.edit-form {
    display: grid;
    grid-template-columns: minmax(150px, 1fr) minmax(150px, 1fr) auto;
    gap: 12px;
    align-items: end;
}

input,
label,
button {
    border-radius: 6px;
    font: inherit;
}

input {
    min-width: 0;
    border: 1px solid #c7d0df;
    padding: 10px 11px;
    color: #172033;
    background: #ffffff;
}

.edit-field {
    min-width: 0;
    display: grid;
    gap: 7px;
}

.edit-field span {
    flex: 0 0 auto;
    color: #2f3a4f;
    font-size: 14px;
    font-weight: 700;
    white-space: nowrap;
}

.edit-field input {
    width: 100%;
}

.edit-field-wide {
    grid-column: 1 / -1;
}

button {
    border: 1px solid #c7d0df;
    padding: 10px 12px;
    color: #172033;
    background: #ffffff;
    cursor: pointer;
}

button:disabled {
    cursor: not-allowed;
    opacity: 0.7;
}

.create-actions button:first-child,
.edit-actions button {
    border-color: #2563eb;
    color: #ffffff;
    background: #2563eb;
}

.message {
    color: #067647;
    font-size: 14px;
}

.list-panel {
    display: grid;
    gap: 14px;
}

.list-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
}

.toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 0 0 auto;
}

.add-project-button {
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

.add-project-button:hover {
    background: #1d4ed8;
    box-shadow: 0 8px 18px rgba(37, 99, 235, 0.24);
    transform: translateY(-1px);
}

.add-project-button:active {
    transform: translateY(0);
}

.add-project-button:focus-visible {
    outline: 3px solid rgba(37, 99, 235, 0.22);
    outline-offset: 2px;
}

.add-project-button[aria-pressed="true"] {
    background: #1e40af;
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

.project-form {
    display: grid;
    grid-template-columns: minmax(180px, 1fr) minmax(220px, 1.25fr) minmax(150px, 0.85fr) minmax(150px, 0.85fr);
    gap: 14px;
    align-items: end;
}

.create-panel {
    border: 1px solid #d8dee9;
    border-radius: 8px;
    padding: 16px;
    background: #ffffff;
    box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
}

.project-form label {
    display: grid;
    gap: 7px;
}

.project-form label span {
    color: #2f3a4f;
    font-size: 13px;
    font-weight: 700;
}

.create-actions {
    grid-column: 1 / -1;
    display: flex;
    justify-content: flex-end;
    gap: 8px;
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

.table-shell {
    border: 1px solid #c7d0df;
    border-radius: 8px;
    overflow: hidden;
    background: #ffffff;
}

.project-row {
    display: grid;
    grid-template-columns: minmax(220px, 1.8fr) minmax(150px, 1fr) 110px 110px 150px;
    gap: 16px;
    align-items: center;
    padding: 16px 18px;
}

.table-header {
    color: #2f3a4f;
    background: #eef2f7;
    font-weight: 750;
}

.table-header span:last-child {
    text-align: center;
}

.project-item + .project-item {
    border-top: 1px solid #d8dee9;
}

.project-main {
    display: grid;
    gap: 5px;
}

.project-link-button {
    width: fit-content;
    border: 0;
    padding: 0;
    color: #172033;
    background: transparent;
    font: inherit;
    font-weight: 700;
    text-align: left;
}

.project-link-button:hover {
    color: #2563eb;
    text-decoration: underline;
    text-underline-offset: 3px;
}

.project-main p,
.project-dates {
    color: #667085;
    font-size: 14px;
}

.project-dates {
    display: grid;
    gap: 4px;
}

.project-dates span {
    display: flex;
    gap: 8px;
}

.project-dates strong {
    color: #2f3a4f;
}

.status-badge,
.risk-badge {
    width: fit-content;
    border-radius: 999px;
    padding: 5px 10px;
    font-size: 13px;
    font-weight: 700;
}

.status-not_started {
    color: #344054;
    background: #eef2f7;
}

.status-in_progress {
    color: #1849a9;
    background: #dbeafe;
}

.status-done {
    color: #067647;
    background: #dcfae6;
}

.risk-relaxed {
    color: #1849a9;
    background: #dbeafe;
}

.risk-ok {
    color: #067647;
    background: #dcfae6;
}

.risk-pressure {
    color: #b42318;
    background: #fee4e2;
}

.replan-hint {
    width: fit-content;
    border-radius: 999px;
    padding: 3px 8px;
    color: #b54708;
    background: #fef0c7;
    font-size: 12px;
    font-weight: 700;
}

.row-actions,
.edit-actions {
    display: flex;
    gap: 8px;
    justify-content: center;
}

.edit-actions {
    align-items: end;
    justify-content: flex-end;
}

.row-actions button,
.secondary-button {
    padding: 8px 10px;
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
    font-size: 17px;
    line-height: 1;
}

.icon-button:hover {
    color: #172033;
    background: #eef2f7;
}

.danger-button {
    color: #b42318;
    background: transparent;
}

.danger-button:hover {
    color: #b42318;
    background: #fff5f4;
}

.edit-form {
    border-top: 1px solid #d8dee9;
    padding: 14px 18px 18px;
    background: #f8fafc;
}

.empty-state {
    padding: 42px 18px;
    color: #667085;
    text-align: center;
}

@media (max-width: 980px) {
    .project-form,
    .edit-form,
    .project-row {
        grid-template-columns: 1fr;
    }

    .list-toolbar {
        align-items: flex-start;
    }

    .toolbar-actions {
        align-items: flex-start;
    }

    .create-actions {
        justify-content: flex-start;
    }

    .row-actions,
    .edit-actions {
        justify-content: flex-start;
    }

    .edit-field-wide {
        grid-column: auto;
    }
}
</style>
