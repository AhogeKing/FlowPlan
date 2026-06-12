<script setup>
import { RefreshCw, RotateCcw, Save, Trash2 } from "@lucide/vue";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { listProjects } from "../api/project";
import {
    createLocalSetting,
    deleteLocalSetting,
    getGlobalSetting,
    getLocalSetting,
    resetGlobalSetting,
    settingDefaults,
    updateGlobalSetting,
    updateLocalSetting
} from "../api/setting";

const props = defineProps({
    projectId: {
        type: [Number, String],
        default: null
    }
});

const projects = ref([]);
const selectedProjectId = ref("");
const hasLocalSetting = ref(false);
const loading = ref(false);
const globalSaving = ref(false);
const localSaving = ref(false);
const message = ref("");

const globalForm = reactive(settingDefaults());
const localForm = reactive(settingDefaults());

const capacityFields = [
    { key: "base_daily_minutes", label: "基准分钟", min: 1, hint: "每天可投入时间的基础值，会先乘以周比例再参与排期。" },
    { key: "daily_min_minutes", label: "每日下限", min: 0, hint: "低于这个值的日期会被视为不可排期，避免生成太碎的计划。" },
    { key: "daily_max_minutes", label: "每日上限", min: 1, hint: "限制单日最多安排多少分钟，直接影响计划压力和跨度。" }
];

const weekdayFields = [
    { key: "mon_ratio", label: "周一", hint: "100 表示使用完整基准分钟，0 表示这一天不安排计划。" },
    { key: "tue_ratio", label: "周二", hint: "用于调节这一天的可用容量，数值越高排得越多。" },
    { key: "wed_ratio", label: "周三", hint: "用于调节这一天的可用容量，适合表达固定忙闲规律。" },
    { key: "thu_ratio", label: "周四", hint: "用于调节这一天的可用容量，影响当天是否更容易排计划。" },
    { key: "fri_ratio", label: "周五", hint: "用于调节这一天的可用容量，可降低周五计划强度。" },
    { key: "sat_ratio", label: "周六", hint: "用于调节这一天的可用容量，周末学习可适当提高。" },
    { key: "sun_ratio", label: "周日", hint: "用于调节这一天的可用容量，设置为 0 可留作休息日。" }
];

const taskCountFields = [
    { key: "task_min_count_per_day", label: "任务下限", min: 0, hint: "控制一天尽量至少覆盖几个 Task，数值高会更分散。" },
    { key: "task_max_count_per_day", label: "任务上限", min: 1, hint: "限制一天最多出现几个 Task，数值低会更聚焦。" }
];

const planItemFields = [
    { key: "min_plan_item_minutes", label: "计划项下限", min: 1, hint: "单个 Task 每次至少排多久，避免出现过短计划项。" },
    { key: "max_plan_item_minutes", label: "计划项上限", min: 1, hint: "单个 Task 每天最多吃掉多少容量，避免一个任务占满全天。" },
    { key: "time_block_minutes", label: "时间块", min: 1, hint: "所有计划分钟会按这个粒度取整，例如 10 表示按 10 分钟切块。" },
    { key: "balance_factor", label: "均衡系数", min: 0, max: 100, hint: "越低越聚焦高分任务，越高越倾向多个任务均匀分配。" }
];

const selectedProject = computed(() => {
    return projects.value.find(project => String(project.id) === String(selectedProjectId.value)) || null;
});

const localStatusText = computed(() => hasLocalSetting.value ? "LOCAL" : "GLOBAL");

function applySetting(target, setting) {
    Object.assign(target, settingDefaults(), setting || {});
}

function validateSetting(setting) {
    if (setting.base_daily_minutes <= 0) {
        return "基准分钟必须大于 0";
    }
    if (setting.daily_min_minutes < 0) {
        return "每日下限不能小于 0";
    }
    if (setting.daily_max_minutes < setting.daily_min_minutes) {
        return "每日上限不能小于每日下限";
    }
    if (setting.task_min_count_per_day < 0) {
        return "任务下限不能小于 0";
    }
    if (setting.task_max_count_per_day < setting.task_min_count_per_day) {
        return "任务上限不能小于任务下限";
    }
    if (setting.min_plan_item_minutes <= 0) {
        return "计划项下限必须大于 0";
    }
    if (setting.max_plan_item_minutes < setting.min_plan_item_minutes) {
        return "计划项上限不能小于计划项下限";
    }
    if (setting.daily_max_minutes < setting.min_plan_item_minutes) {
        return "每日上限不能小于计划项下限";
    }
    if (setting.time_block_minutes <= 0) {
        return "时间块必须大于 0";
    }
    if (setting.time_block_minutes > setting.daily_max_minutes) {
        return "时间块不能大于每日上限";
    }
    if (setting.balance_factor < 0 || setting.balance_factor > 100) {
        return "均衡系数必须在 0 到 100 之间";
    }
    return "";
}

async function loadGlobalSetting() {
    applySetting(globalForm, await getGlobalSetting());
}

async function loadLocalSetting() {
    if (!selectedProjectId.value) {
        hasLocalSetting.value = false;
        applySetting(localForm, globalForm);
        return;
    }

    const setting = await getLocalSetting(selectedProjectId.value);
    hasLocalSetting.value = Boolean(setting);
    applySetting(localForm, setting || globalForm);
}

async function loadPage() {
    loading.value = true;
    message.value = "";
    try {
        await loadGlobalSetting();
        projects.value = await listProjects();
        if (props.projectId && projects.value.some(project => String(project.id) === String(props.projectId))) {
            selectedProjectId.value = String(props.projectId);
        } else if (!selectedProjectId.value && projects.value.length > 0) {
            selectedProjectId.value = String(projects.value[0].id);
        }
        await loadLocalSetting();
    } finally {
        loading.value = false;
    }
}

async function saveGlobalSetting() {
    const error = validateSetting(globalForm);
    if (error) {
        message.value = error;
        return;
    }

    globalSaving.value = true;
    message.value = "";
    try {
        await updateGlobalSetting(globalForm);
        message.value = "全局设置已保存";
        await loadGlobalSetting();
        if (!hasLocalSetting.value) {
            applySetting(localForm, globalForm);
        }
    } finally {
        globalSaving.value = false;
    }
}

async function restoreGlobalSetting() {
    globalSaving.value = true;
    message.value = "";
    try {
        await resetGlobalSetting();
        await loadGlobalSetting();
        if (!hasLocalSetting.value) {
            applySetting(localForm, globalForm);
        }
        message.value = "全局设置已恢复默认";
    } finally {
        globalSaving.value = false;
    }
}

async function saveLocalSetting() {
    if (!selectedProjectId.value) {
        message.value = "请先选择 Project";
        return;
    }

    const error = validateSetting(localForm);
    if (error) {
        message.value = error;
        return;
    }

    localSaving.value = true;
    message.value = "";
    try {
        if (hasLocalSetting.value) {
            await updateLocalSetting(selectedProjectId.value, localForm);
            message.value = "项目设置已保存";
        } else {
            await createLocalSetting(selectedProjectId.value, localForm);
            hasLocalSetting.value = true;
            message.value = "项目设置已创建";
        }
        await loadLocalSetting();
    } finally {
        localSaving.value = false;
    }
}

async function removeLocalSetting() {
    if (!selectedProjectId.value || !hasLocalSetting.value) {
        return;
    }

    const confirmed = window.confirm(`确定让「${selectedProject.value?.name || "当前 Project"}」回退到全局设置吗？`);
    if (!confirmed) {
        return;
    }

    localSaving.value = true;
    message.value = "";
    try {
        await deleteLocalSetting(selectedProjectId.value);
        hasLocalSetting.value = false;
        applySetting(localForm, globalForm);
        message.value = "项目设置已回退到全局";
    } finally {
        localSaving.value = false;
    }
}

watch(selectedProjectId, () => {
    loadLocalSetting();
});

watch(() => props.projectId, projectId => {
    if (projectId && String(projectId) !== String(selectedProjectId.value)) {
        selectedProjectId.value = String(projectId);
    }
});

onMounted(loadPage);
</script>

<template>
    <section class="settings-page">
        <div class="page-header">
            <div>
                <p class="eyebrow">Settings</p>
                <h2>排期设置</h2>
            </div>
            <button class="refresh-icon-button" type="button" title="刷新设置" :disabled="loading" @click="loadPage">
                <RefreshCw :class="{ spinning: loading }" :size="18" stroke-width="2.2" />
            </button>
        </div>

        <p v-if="message" class="message">{{ message }}</p>

        <div class="settings-scroll">
            <form class="setting-panel" @submit.prevent="saveGlobalSetting">
                <div class="panel-header">
                    <div>
                        <p class="eyebrow">GLOBAL</p>
                        <h3>全局设置</h3>
                    </div>
                    <div class="panel-actions">
                        <button class="primary-button" type="submit" :disabled="globalSaving">
                            <Save :size="16" stroke-width="2.2" />
                            <span>{{ globalSaving ? '保存中...' : '保存' }}</span>
                        </button>
                        <button class="secondary-button" type="button" :disabled="globalSaving" @click="restoreGlobalSetting">
                            <RotateCcw :size="16" stroke-width="2.2" />
                            <span>恢复默认</span>
                        </button>
                    </div>
                </div>

                <div class="setting-groups">
                    <section class="setting-group">
                        <h4>每日容量</h4>
                        <div class="field-grid">
                            <label v-for="field in capacityFields" :key="field.key">
                                <span>{{ field.label }}</span>
                                <input v-model.number="globalForm[field.key]" type="number" :min="field.min">
                                <small>{{ field.hint }}</small>
                            </label>
                        </div>
                    </section>

                    <section class="setting-group">
                        <h4>周比例</h4>
                        <div class="weekday-grid">
                            <label v-for="field in weekdayFields" :key="field.key">
                                <span>{{ field.label }}</span>
                                <input v-model.number="globalForm[field.key]" type="number" min="0">
                                <small>{{ field.hint }}</small>
                            </label>
                        </div>
                    </section>

                    <section class="setting-group">
                        <h4>任务数量</h4>
                        <div class="field-grid compact-grid">
                            <label v-for="field in taskCountFields" :key="field.key">
                                <span>{{ field.label }}</span>
                                <input v-model.number="globalForm[field.key]" type="number" :min="field.min">
                                <small>{{ field.hint }}</small>
                            </label>
                        </div>
                    </section>

                    <section class="setting-group">
                        <h4>计划项规则</h4>
                        <div class="field-grid">
                            <label v-for="field in planItemFields" :key="field.key">
                                <span>{{ field.label }}</span>
                                <input v-model.number="globalForm[field.key]" type="number" :min="field.min" :max="field.max">
                                <small>{{ field.hint }}</small>
                            </label>
                        </div>
                    </section>
                </div>
            </form>

            <form class="setting-panel" @submit.prevent="saveLocalSetting">
                <div class="panel-header">
                    <div>
                        <p class="eyebrow">LOCAL</p>
                        <h3>项目设置</h3>
                    </div>
                    <div class="project-picker">
                        <span :class="['scope-badge', hasLocalSetting ? 'scope-local' : 'scope-global']">{{ localStatusText }}</span>
                        <select v-model="selectedProjectId" :disabled="projects.length === 0">
                            <option value="" disabled>请选择 Project</option>
                            <option v-for="project in projects" :key="project.id" :value="String(project.id)">
                                {{ project.name }}
                            </option>
                        </select>
                    </div>
                </div>

                <div v-if="projects.length === 0" class="empty-state">暂无项目</div>

                <template v-else>
                    <div class="setting-groups">
                        <section class="setting-group">
                            <h4>每日容量</h4>
                            <div class="field-grid">
                                <label v-for="field in capacityFields" :key="field.key">
                                    <span>{{ field.label }}</span>
                                    <input v-model.number="localForm[field.key]" type="number" :min="field.min">
                                    <small>{{ field.hint }}</small>
                                </label>
                            </div>
                        </section>

                        <section class="setting-group">
                            <h4>周比例</h4>
                            <div class="weekday-grid">
                                <label v-for="field in weekdayFields" :key="field.key">
                                    <span>{{ field.label }}</span>
                                    <input v-model.number="localForm[field.key]" type="number" min="0">
                                    <small>{{ field.hint }}</small>
                                </label>
                            </div>
                        </section>

                        <section class="setting-group">
                            <h4>任务数量</h4>
                            <div class="field-grid compact-grid">
                                <label v-for="field in taskCountFields" :key="field.key">
                                    <span>{{ field.label }}</span>
                                    <input v-model.number="localForm[field.key]" type="number" :min="field.min">
                                    <small>{{ field.hint }}</small>
                                </label>
                            </div>
                        </section>

                        <section class="setting-group">
                            <h4>计划项规则</h4>
                            <div class="field-grid">
                                <label v-for="field in planItemFields" :key="field.key">
                                    <span>{{ field.label }}</span>
                                    <input v-model.number="localForm[field.key]" type="number" :min="field.min" :max="field.max">
                                    <small>{{ field.hint }}</small>
                                </label>
                            </div>
                        </section>
                    </div>

                    <div class="local-actions">
                        <button class="primary-button" type="submit" :disabled="localSaving || !selectedProjectId">
                            <Save :size="16" stroke-width="2.2" />
                            <span>{{ hasLocalSetting ? '保存项目设置' : '创建项目设置' }}</span>
                        </button>
                        <button
                            v-if="hasLocalSetting"
                            class="danger-button"
                            type="button"
                            :disabled="localSaving"
                            @click="removeLocalSetting"
                        >
                            <Trash2 :size="16" stroke-width="2.2" />
                            <span>回退到全局</span>
                        </button>
                    </div>
                </template>
            </form>
        </div>
    </section>
</template>

<style scoped>
.settings-page {
    display: grid;
    gap: 18px;
    color: #172033;
}

.page-header,
.panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
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
    color: #2f3a4f;
    font-size: 14px;
}

button,
input,
select {
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
    padding: 9px 12px;
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

.settings-scroll {
    display: grid;
    gap: 18px;
}

.setting-panel {
    border: 1px solid #c7d0df;
    border-radius: 8px;
    padding: 18px;
    display: grid;
    gap: 18px;
    background: #ffffff;
}

.panel-actions,
.local-actions,
.project-picker {
    display: flex;
    align-items: center;
    gap: 8px;
}

.setting-groups {
    display: grid;
    gap: 16px;
}

.setting-group {
    display: grid;
    gap: 10px;
}

.field-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
    gap: 12px;
}

.compact-grid {
    grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
}

.weekday-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 10px;
}

label {
    display: grid;
    gap: 7px;
}

label span {
    color: #2f3a4f;
    font-size: 13px;
    font-weight: 700;
}

label small {
    min-height: 34px;
    color: #667085;
    font-size: 12px;
    line-height: 1.45;
}

.primary-button,
.secondary-button,
.danger-button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.primary-button {
    border-color: #2563eb;
    color: #ffffff;
    background: #2563eb;
}

.secondary-button:hover {
    background: #eef2f7;
}

.danger-button {
    border-color: #fecdca;
    color: #b42318;
    background: #fff5f4;
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

.scope-badge {
    border-radius: 999px;
    padding: 5px 10px;
    font-size: 13px;
    font-weight: 700;
}

.scope-global {
    color: #1849a9;
    background: #dbeafe;
}

.scope-local {
    color: #067647;
    background: #dcfae6;
}

.empty-state {
    padding: 28px 18px;
    color: #667085;
    text-align: center;
}

@media (max-width: 980px) {
    .page-header,
    .panel-header,
    .panel-actions,
    .local-actions,
    .project-picker {
        align-items: flex-start;
        flex-wrap: wrap;
    }

    .field-grid,
    .compact-grid,
    .weekday-grid {
        grid-template-columns: 1fr;
    }
}
</style>
