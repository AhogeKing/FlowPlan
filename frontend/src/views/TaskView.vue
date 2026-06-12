<script setup>
import { CircleArrowLeft, CirclePlus, RefreshCw, Settings, Trash2 } from "@lucide/vue";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { listProjects } from "../api/project";
import { addTask, deleteTask, listTasks, updateTask } from "../api/task";

const emit = defineEmits(["back-projects"]);

const props = defineProps({
    projectId: {
        type: [Number, String],
        default: null
    }
});

const projects = ref([]);
const tasks = ref([]);
const selectedTaskProjectId = ref(props.projectId ? String(props.projectId) : "");
const loading = ref(false);
const saving = ref(false);
const message = ref("");
const activeFilter = ref("all");
const editingId = ref(null);
const showCreateForm = ref(false);

const form = reactive({
    projectId: "",
    title: "",
    description: "",
    weight: 1,
    min_session_minutes: "",
    dependency_task_id: "",
    begin_date: "",
    deadline: "",
    done_flag: false,
    status: "NOT_STARTED"
});

const editForm = reactive({
    title: "",
    description: "",
    weight: 1,
    min_session_minutes: "",
    dependency_task_id: "",
    begin_date: "",
    deadline: "",
    done_flag: false,
    status: "NOT_STARTED"
});

const selectedProject = computed(() => {
    if (!selectedTaskProjectId.value) {
        return null;
    }
    return projects.value.find(project => String(project.id) === String(selectedTaskProjectId.value)) || null;
});

const statusFilters = computed(() => {
    const total = tasks.value.length;
    const inProgress = tasks.value.filter(task => normalizeStatus(task.status) === "in_progress").length;
    const notStarted = tasks.value.filter(task => normalizeStatus(task.status) === "not_started").length;
    const done = tasks.value.filter(task => normalizeStatus(task.status) === "done").length;

    return [
        { key: "all", label: "全部任务", count: total },
        { key: "in_progress", label: "进行中", count: inProgress },
        { key: "not_started", label: "未开始", count: notStarted },
        { key: "done", label: "已完成", count: done }
    ];
});

const filteredTasks = computed(() => {
    if (activeFilter.value === "all") {
        return tasks.value;
    }
    return tasks.value.filter(task => normalizeStatus(task.status) === activeFilter.value);
});

async function loadTasks() {
    loading.value = true;
    try {
        const projectList = await listProjects();
        projects.value = projectList;

        if (selectedTaskProjectId.value) {
            form.projectId = selectedTaskProjectId.value;
        } else if (!form.projectId && projectList.length > 0) {
            form.projectId = String(projectList[0].id);
        }

        const visibleProjects = selectedTaskProjectId.value
            ? projectList.filter(project => String(project.id) === String(selectedTaskProjectId.value))
            : projectList;

        const taskGroups = await Promise.all(
            visibleProjects.map(async project => {
                const projectTasks = await listTasks(project.id);
                return projectTasks.map(task => ({
                    ...task,
                    projectName: project.name
                }));
            })
        );

        tasks.value = taskGroups.flat();
    } finally {
        loading.value = false;
    }
}

async function handleAdd() {
    message.value = "";
    saving.value = true;
    try {
        await addTask(form.projectId, taskPayload(form));
        resetCreateForm();
        showCreateForm.value = false;
        message.value = "任务已添加";
        await loadTasks();
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

function startEdit(task) {
    editingId.value = task.id;
    editForm.title = task.title || "";
    editForm.description = task.description || "";
    editForm.weight = task.weight ?? 1;
    editForm.min_session_minutes = task.min_session_minutes ?? "";
    editForm.dependency_task_id = task.dependency_task_id ?? "";
    editForm.begin_date = task.begin_date || "";
    editForm.deadline = task.deadline || "";
    editForm.done_flag = Boolean(task.done_flag);
    editForm.status = task.status || (task.done_flag ? "DONE" : "NOT_STARTED");
    message.value = "";
}

function cancelEdit() {
    editingId.value = null;
    resetEditForm();
}

async function handleUpdate(task) {
    message.value = "";
    saving.value = true;
    try {
        await updateTask(task.project_id, task.id, taskPayload(editForm));
        editingId.value = null;
        resetEditForm();
        message.value = "任务已更新";
        await loadTasks();
    } finally {
        saving.value = false;
    }
}

async function handleDelete(task) {
    const confirmed = window.confirm(`确定删除任务「${task.title}」吗？`);
    if (!confirmed) {
        return;
    }

    message.value = "";
    saving.value = true;
    try {
        await deleteTask(task.project_id, task.id);
        message.value = "任务已删除";
        if (editingId.value === task.id) {
            cancelEdit();
        }
        await loadTasks();
    } finally {
        saving.value = false;
    }
}

function taskPayload(source) {
    const status = source.done_flag ? "DONE" : source.status || "NOT_STARTED";
    return {
        title: source.title,
        description: source.description,
        weight: Number(source.weight || 1),
        min_session_minutes: source.min_session_minutes || null,
        dependency_task_id: source.dependency_task_id || null,
        begin_date: source.begin_date || null,
        deadline: source.deadline || null,
        done_flag: Boolean(source.done_flag || status === "DONE"),
        status
    };
}

function resetCreateForm() {
    form.projectId = selectedTaskProjectId.value
        ? selectedTaskProjectId.value
        : projects.value.length > 0 ? String(projects.value[0].id) : "";
    form.title = "";
    form.description = "";
    form.weight = 1;
    form.min_session_minutes = "";
    form.dependency_task_id = "";
    form.begin_date = "";
    form.deadline = "";
    form.done_flag = false;
    form.status = "NOT_STARTED";
}

function resetEditForm() {
    editForm.title = "";
    editForm.description = "";
    editForm.weight = 1;
    editForm.min_session_minutes = "";
    editForm.dependency_task_id = "";
    editForm.begin_date = "";
    editForm.deadline = "";
    editForm.done_flag = false;
    editForm.status = "NOT_STARTED";
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

function dependencyOptions(projectId, excludeTaskId = null) {
    return tasks.value.filter(task => {
        return String(task.project_id) === String(projectId)
            && String(task.id) !== String(excludeTaskId);
    });
}

function formatSessionMinutes(minutes) {
    return minutes ? `${minutes} 分钟` : "使用默认";
}

onMounted(loadTasks);

watch(() => props.projectId, () => {
    selectedTaskProjectId.value = props.projectId ? String(props.projectId) : "";
    activeFilter.value = "all";
    editingId.value = null;
});

watch(selectedTaskProjectId, () => {
    activeFilter.value = "all";
    editingId.value = null;
    resetCreateForm();
    loadTasks();
});

watch(() => form.projectId, () => {
    form.dependency_task_id = "";
});
</script>

<template>
    <section class="tasks-page">
        <div class="page-header">
            <div>
                <p class="eyebrow">Tasks</p>
                <h2>{{ selectedProject ? `${selectedProject.name} 任务列表` : '任务列表' }}</h2>
            </div>
        </div>

        <p v-if="message" class="message">{{ message }}</p>

        <div class="list-panel">
            <div class="list-toolbar">
                <div class="filter-tabs" aria-label="任务状态筛选">
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
                    <select v-model="selectedTaskProjectId" class="project-filter-select" :disabled="projects.length === 0">
                        <option value="">所有 Project</option>
                        <option v-for="project in projects" :key="project.id" :value="String(project.id)">
                            {{ project.name }}
                        </option>
                    </select>
                    <button class="icon-button" type="button" title="返回项目列表" @click="emit('back-projects')">
                        <CircleArrowLeft :size="18" stroke-width="2.2" />
                    </button>
                    <button class="add-task-button" type="button" :aria-pressed="showCreateForm" @click="toggleCreateForm">
                        <CirclePlus :size="16" stroke-width="2.1" />
                        <span>Add task</span>
                    </button>
                    <button class="refresh-icon-button" type="button" title="刷新任务" :disabled="loading" @click="loadTasks">
                        <RefreshCw :class="{ spinning: loading }" :size="18" stroke-width="2.2" />
                    </button>
                </div>
            </div>

            <form v-if="showCreateForm" class="task-form create-panel" @submit.prevent="handleAdd">
                <label>
                    <span>所属项目（必填）</span>
                    <select v-model="form.projectId" :disabled="Boolean(selectedTaskProjectId)" required>
                        <option value="" disabled>请选择 Project</option>
                        <option v-for="project in projects" :key="project.id" :value="String(project.id)">
                            {{ project.name }}
                        </option>
                    </select>
                </label>
                <label>
                    <span>任务标题（必填）</span>
                    <input v-model.trim="form.title" required placeholder="请输入任务标题">
                </label>
                <label>
                    <span>权重</span>
                    <input v-model.number="form.weight" min="1" type="number">
                </label>
                <label>
                    <span>单次时长（分钟）</span>
                    <input v-model.number="form.min_session_minutes" min="1" type="number" placeholder="默认">
                </label>
                <label class="wide-field">
                    <span>任务描述（选填）</span>
                    <input v-model.trim="form.description" placeholder="请输入任务描述">
                </label>
                <label>
                    <span>依赖任务（选填）</span>
                    <select v-model="form.dependency_task_id">
                        <option value="">无依赖</option>
                        <option
                            v-for="task in dependencyOptions(form.projectId)"
                            :key="task.id"
                            :value="task.id"
                        >
                            {{ task.title }}
                        </option>
                    </select>
                </label>
                <label>
                    <span>状态</span>
                    <select v-model="form.status">
                        <option value="NOT_STARTED">未开始</option>
                        <option value="IN_PROGRESS">进行中</option>
                        <option value="DONE">已完成</option>
                    </select>
                </label>
                <label>
                    <span>开始日期（选填）</span>
                    <input v-model="form.begin_date" type="date">
                </label>
                <label>
                    <span>截止日期（必填）</span>
                    <input v-model="form.deadline" required type="date">
                </label>
                <label class="checkbox-field">
                    <input v-model="form.done_flag" type="checkbox">
                    <span>标记完成</span>
                </label>
                <div class="form-actions">
                    <button type="submit" :disabled="saving || projects.length === 0">{{ saving ? '保存中...' : '添加Task' }}</button>
                    <button class="secondary-button" type="button" @click="cancelCreate">取消</button>
                </div>
            </form>

            <div class="table-shell">
                <div class="task-row table-header">
                    <span>任务</span>
                    <span>所属项目</span>
                    <span>周期</span>
                    <span>权重</span>
                    <span>单次时长</span>
                    <span>状态</span>
                    <span>操作</span>
                </div>

                <div v-if="loading" class="empty-state">加载中...</div>
                <div v-else-if="projects.length === 0" class="empty-state">暂无项目，先创建 Project 后再查看 Task</div>
                <div v-else-if="filteredTasks.length === 0" class="empty-state">暂无任务</div>

                <template v-else>
                    <article v-for="task in filteredTasks" :key="task.id" class="task-item">
                        <div class="task-row">
                            <div class="task-main">
                                <strong>{{ task.title }}</strong>
                                <p>{{ task.description || '暂无描述' }}</p>
                            </div>
                            <span class="project-name">{{ task.projectName }}</span>
                            <div class="task-dates">
                                <span><strong>开始</strong>{{ task.begin_date || '未设置' }}</span>
                                <span><strong>截止</strong>{{ task.deadline }}</span>
                            </div>
                            <span class="task-weight">{{ task.weight }}</span>
                            <div class="task-minutes">
                                <span>{{ formatSessionMinutes(task.min_session_minutes) }}</span>
                                <small v-if="task.dependency_task_id">依赖 #{{ task.dependency_task_id }}</small>
                            </div>
                            <span :class="['status-badge', `status-${normalizeStatus(task.status)}`]">
                                {{ formatStatus(task.status) }}
                            </span>
                            <div class="row-actions">
                                <button class="icon-button" type="button" title="修改任务" @click="startEdit(task)">
                                    <Settings :size="18" stroke-width="2.2" />
                                </button>
                                <button class="icon-button danger-button" type="button" title="删除任务" @click="handleDelete(task)">
                                    <Trash2 :size="18" stroke-width="2.2" />
                                </button>
                            </div>
                        </div>

                        <form
                            v-if="editingId === task.id"
                            class="task-form edit-form"
                            @submit.prevent="handleUpdate(task)"
                        >
                            <label>
                                <span>任务标题</span>
                                <input v-model.trim="editForm.title" required placeholder="任务标题">
                            </label>
                            <label>
                                <span>权重</span>
                                <input v-model.number="editForm.weight" min="1" type="number">
                            </label>
                            <label>
                                <span>单次时长</span>
                                <input v-model.number="editForm.min_session_minutes" min="1" type="number" placeholder="默认">
                            </label>
                            <label class="wide-field">
                                <span>任务描述</span>
                                <input v-model.trim="editForm.description" placeholder="任务描述">
                            </label>
                            <label>
                                <span>依赖任务</span>
                                <select v-model="editForm.dependency_task_id">
                                    <option value="">无依赖</option>
                                    <option
                                        v-for="dependency in dependencyOptions(task.project_id, task.id)"
                                        :key="dependency.id"
                                        :value="dependency.id"
                                    >
                                        {{ dependency.title }}
                                    </option>
                                </select>
                            </label>
                            <label>
                                <span>状态</span>
                                <select v-model="editForm.status">
                                    <option value="NOT_STARTED">未开始</option>
                                    <option value="IN_PROGRESS">进行中</option>
                                    <option value="DONE">已完成</option>
                                </select>
                            </label>
                            <label>
                                <span>开始日期</span>
                                <input v-model="editForm.begin_date" type="date">
                            </label>
                            <label>
                                <span>截止日期</span>
                                <input v-model="editForm.deadline" required type="date">
                            </label>
                            <label class="checkbox-field">
                                <input v-model="editForm.done_flag" type="checkbox">
                                <span>标记完成</span>
                            </label>
                            <div class="form-actions">
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
.tasks-page {
    display: grid;
    gap: 18px;
    color: #172033;
}

.page-header,
.list-toolbar {
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

input,
select,
label,
button {
    border-radius: 6px;
    font: inherit;
}

input,
select {
    min-width: 0;
    border: 1px solid #c7d0df;
    padding: 10px 11px;
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

button:disabled {
    cursor: not-allowed;
    opacity: 0.7;
}

.message {
    color: #067647;
    font-size: 14px;
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
.row-actions,
.form-actions {
    display: flex;
    gap: 8px;
}

.toolbar-actions {
    align-items: center;
    flex: 0 0 auto;
}

.project-filter-select {
    width: 230px;
    min-height: 36px;
    padding: 7px 11px;
}

.add-task-button {
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

.add-task-button:hover {
    background: #1d4ed8;
    box-shadow: 0 8px 18px rgba(37, 99, 235, 0.24);
    transform: translateY(-1px);
}

.add-task-button:active {
    transform: translateY(0);
}

.add-task-button:focus-visible {
    outline: 3px solid rgba(37, 99, 235, 0.22);
    outline-offset: 2px;
}

.add-task-button[aria-pressed="true"] {
    background: #1e40af;
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

.task-form {
    display: grid;
    grid-template-columns: minmax(150px, 0.9fr) minmax(190px, 1.2fr) 140px 110px;
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

.edit-form {
    border-top: 1px solid #d8dee9;
    padding: 14px 18px 18px;
    background: #f8fafc;
}

.task-form label {
    display: grid;
    gap: 7px;
}

.task-form label span {
    color: #2f3a4f;
    font-size: 13px;
    font-weight: 700;
}

.wide-field {
    grid-column: span 2;
}

.checkbox-field {
    min-height: 42px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.checkbox-field input {
    width: 16px;
    height: 16px;
    min-width: 16px;
    padding: 0;
}

.form-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
}

.form-actions button:first-child {
    border-color: #2563eb;
    color: #ffffff;
    background: #2563eb;
}

.secondary-button {
    padding: 8px 10px;
}

.table-shell {
    border: 1px solid #c7d0df;
    border-radius: 8px;
    overflow: hidden;
    background: #ffffff;
}

.task-row {
    display: grid;
    grid-template-columns: minmax(200px, 1.6fr) minmax(105px, 0.8fr) minmax(135px, 1fr) 100px 82px 82px 92px;
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

.task-item + .task-item {
    border-top: 1px solid #d8dee9;
}

.task-main {
    display: grid;
    gap: 5px;
}

.task-main p,
.task-dates,
.task-minutes small {
    color: #667085;
    font-size: 14px;
}

.project-name {
    color: #2f3a4f;
    font-weight: 650;
}

.task-dates {
    display: grid;
    gap: 4px;
}

.task-dates span {
    display: flex;
    gap: 8px;
}

.task-dates strong {
    color: #2f3a4f;
}

.task-minutes {
    display: grid;
    gap: 3px;
}

.task-minutes span {
    font-weight: 700;
}

.task-weight {
    color: #2f3a4f;
    font-weight: 750;
}

.status-badge {
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

.danger-button {
    color: #b42318;
    background: transparent;
}

.danger-button:hover {
    color: #b42318;
    background: #fff5f4;
}

.empty-state {
    padding: 42px 18px;
    color: #667085;
    text-align: center;
}

@media (max-width: 860px) {
    .list-toolbar {
        align-items: flex-start;
    }

    .toolbar-actions {
        flex-wrap: wrap;
    }

    .project-filter-select {
        width: 100%;
    }

    .task-form,
    .task-row {
        grid-template-columns: 1fr;
    }

    .wide-field {
        grid-column: auto;
    }

    .form-actions,
    .row-actions {
        justify-content: flex-start;
    }

}
</style>
