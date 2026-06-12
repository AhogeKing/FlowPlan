<script setup>
import { Bot, CheckCircle2, RefreshCw, Send, Sparkles, Trash2 } from "@lucide/vue";
import { computed, onMounted, ref, watch } from "vue";
import { applyAiDraft, clearAiDraftSession, startAiDraft, startAiDraftStream } from "../api/ai";
import { clearAiBuilderState, loadAiBuilderState, saveAiBuilderState } from "../utils/aiBuilderStorage";

const emit = defineEmits(["open-plans"]);
const props = defineProps({
    user: {
        type: Object,
        default: null
    }
});

const sessionId = ref("");
const prompt = ref("");
const draft = ref(null);
const loading = ref(false);
const applying = ref(false);
const message = ref("");
const messages = ref([]);

const userDisplayName = computed(() => {
    return props.user?.username || "我";
});

const examples = [
    "我 7 月 20 日要考雅思，现在听力和阅读比较弱，写作一般，口语还可以。每天大概能学 2 小时，周末时间更多。",
    "我接下来半年想学习 C++，主要是为了做项目，每天 1 到 2 小时，周末可以多学一点。",
    "我接下来 6 个月准备考研，数学和专业课比较重要，英语每天也要保持，政治后期再加强。"
];

const settingSummary = computed(() => {
    const setting = draft.value?.setting;
    if (!setting) {
        return [];
    }
    return [
        { label: "每日基准", value: `${setting.baseDailyMinutes} 分钟` },
        { label: "每日上限", value: `${setting.dailyMaxMinutes} 分钟` },
        { label: "任务数量", value: `${setting.taskMinCountPerDay}-${setting.taskMaxCountPerDay} 个/天` },
        { label: "单项时长", value: `${setting.minPlanItemMinutes}-${setting.maxPlanItemMinutes} 分钟` },
        { label: "时间块", value: `${setting.timeBlockMinutes} 分钟` },
        { label: "平衡因子", value: setting.balanceFactor }
    ];
});

function useExample(example) {
    prompt.value = example;
}

async function generateDraft() {
    const text = prompt.value.trim();
    if (!text) {
        message.value = "请输入目标";
        return;
    }

    message.value = "";
    loading.value = true;
    const contextMessages = messages.value.slice();
    const contextDraft = draft.value;
    messages.value.push({ role: "user", content: text });
    const assistantIndex = messages.value.push({ role: "assistant", content: "" }) - 1;
    try {
        await startAiDraftStream({
            sessionId: sessionId.value,
            message: text,
            messages: contextMessages,
            currentDraft: contextDraft
        }, {
            onToken(chunk) {
                appendAssistantContent(assistantIndex, chunk);
            },
            onFinal(response) {
                sessionId.value = response.sessionId || sessionId.value;
                draft.value = response.draft;
                if (!messages.value[assistantIndex]?.content?.trim()) {
                    setAssistantContent(assistantIndex, response.reply || response.draft?.explanation || "已生成草案。");
                } else if (response.reply && !messages.value[assistantIndex].content.includes(response.reply)) {
                    appendAssistantContent(assistantIndex, `\n${response.reply}`);
                }
            },
            onError(errorMessage) {
                if (!messages.value[assistantIndex]?.content?.trim()) {
                    setAssistantContent(assistantIndex, errorMessage || "智能生成暂时不可用。");
                }
            }
        });
        prompt.value = "";
    } catch (error) {
        const response = await startAiDraft({
            sessionId: sessionId.value,
            message: text,
            messages: contextMessages,
            currentDraft: contextDraft
        });
        sessionId.value = response.sessionId || sessionId.value;
        draft.value = response.draft;
        setAssistantContent(assistantIndex, response.reply || response.draft?.explanation || "已生成草案。");
    } finally {
        loading.value = false;
    }
}

function appendAssistantContent(index, chunk) {
    const item = messages.value[index];
    if (!item) {
        return;
    }
    messages.value[index] = {
        ...item,
        content: `${item.content || ""}${chunk}`
    };
}

function setAssistantContent(index, content) {
    const item = messages.value[index];
    if (!item) {
        return;
    }
    messages.value[index] = {
        ...item,
        content
    };
}

async function applyDraft() {
    if (!draft.value) {
        return;
    }

    message.value = "";
    applying.value = true;
    try {
        const result = await applyAiDraft({
            sessionId: sessionId.value,
            draft: draft.value
        });
        const plan = result.plan_result;
        const countText = plan ? `，生成 ${plan.plan_count || 0} 天计划` : "";
        message.value = `项目已创建${countText}`;
    } finally {
        applying.value = false;
    }
}

async function resetDraft() {
    const currentSessionId = sessionId.value;
    prompt.value = "";
    draft.value = null;
    message.value = "";
    messages.value = [];
    sessionId.value = "";
    clearAiBuilderState();
    try {
        await clearAiDraftSession(currentSessionId);
    } catch {
        // Local reset should still succeed even if the expired session no longer exists server-side.
    }
}

function restoreDraftState() {
    const state = loadAiBuilderState();
    if (!state || typeof state !== "object") {
        return;
    }
    sessionId.value = typeof state.sessionId === "string" ? state.sessionId : "";
    prompt.value = typeof state.prompt === "string" ? state.prompt : "";
    draft.value = state.draft || null;
    message.value = typeof state.message === "string" ? state.message : "";
    messages.value = Array.isArray(state.messages) ? state.messages : [];
}

function persistDraftState() {
    if (!sessionId.value && !prompt.value && !draft.value && !message.value && messages.value.length === 0) {
        clearAiBuilderState();
        return;
    }
    saveAiBuilderState({
        sessionId: sessionId.value,
        prompt: prompt.value,
        draft: draft.value,
        message: message.value,
        messages: messages.value
    });
}

function formatDomain(domainType) {
    const labels = {
        ENGLISH_LEARNING: "英语学习",
        PROGRAMMING_LANGUAGE: "编程学习",
        CHINESE_POSTGRAD_EXAM: "中国考研",
        GENERAL: "通用目标"
    };
    return labels[domainType] || "通用目标";
}

function formatWeekdayRatio(setting) {
    if (!setting) {
        return "-";
    }
    return `周一到周五 ${setting.monRatio}/${setting.tueRatio}/${setting.wedRatio}/${setting.thuRatio}/${setting.friRatio}，周末 ${setting.satRatio}/${setting.sunRatio}`;
}

onMounted(() => {
    restoreDraftState();
});

watch([sessionId, prompt, draft, message, messages], persistDraftState, { deep: true });
</script>

<template>
    <section class="ai-page">
        <div class="ai-workspace">
            <section class="chat-panel" aria-label="AI Builder">
                <div class="panel-header">
                    <div>
                        <p class="eyebrow">AI Builder</p>
                        <h2>智能创建项目</h2>
                    </div>
                    <Bot :size="24" stroke-width="2.1" />
                </div>

                <div class="example-row">
                    <button
                        v-for="example in examples"
                        :key="example"
                        type="button"
                        class="example-button"
                        @click="useExample(example)"
                    >
                        {{ example.slice(0, 18) }}
                    </button>
                </div>

                <div class="message-list">
                    <div v-if="messages.length === 0" class="empty-chat">
                        <Sparkles :size="26" stroke-width="2" />
                        <span>输入目标后生成 Project、Task 和局部 Setting 草案</span>
                    </div>
                    <div
                        v-for="(item, index) in messages"
                        :key="`${item.role}-${index}`"
                        :class="['chat-message', item.role]"
                    >
                        <strong>{{ item.role === 'user' ? userDisplayName : 'FlowPlan AI' }}</strong>
                        <p>{{ item.content }}</p>
                    </div>
                </div>

                <form class="prompt-form" @submit.prevent="generateDraft">
                    <textarea
                        v-model="prompt"
                        rows="4"
                        placeholder="我接下来半年想学习 C++，主要为了做项目，每天 1 到 2 小时，周末可以多学一点。"
                    />
                    <div class="form-actions">
                        <button class="secondary-button" type="button" :disabled="loading || applying" @click="resetDraft">
                            <Trash2 :size="16" stroke-width="2.1" />
                            清空
                        </button>
                        <button class="primary-button" type="submit" :disabled="loading || applying">
                            <RefreshCw v-if="loading" :size="16" stroke-width="2.1" class="spin" />
                            <Send v-else :size="16" stroke-width="2.1" />
                            {{ draft ? '继续完善' : '生成草案' }}
                        </button>
                    </div>
                </form>
            </section>

            <section class="draft-panel" aria-label="草案预览">
                <div class="panel-header">
                    <div>
                        <p class="eyebrow">Draft</p>
                        <h2>草案预览</h2>
                    </div>
                    <span v-if="draft" class="domain-pill">{{ formatDomain(draft.domainType) }}</span>
                </div>

                <p v-if="message" class="success-message">{{ message }}</p>

                <div v-if="!draft" class="empty-draft">
                    <Sparkles :size="28" stroke-width="2" />
                    <span>等待生成草案</span>
                </div>

                <div v-else class="draft-content">
                    <section class="draft-section">
                        <h3>Project</h3>
                        <dl class="project-grid">
                            <div>
                                <dt>名称</dt>
                                <dd>{{ draft.project.name }}</dd>
                            </div>
                            <div>
                                <dt>周期</dt>
                                <dd>{{ draft.project.beginDate }} 至 {{ draft.project.deadline }}</dd>
                            </div>
                            <div class="wide">
                                <dt>描述</dt>
                                <dd>{{ draft.project.description }}</dd>
                            </div>
                        </dl>
                    </section>

                    <section class="draft-section">
                        <h3>Tasks</h3>
                        <div class="task-table">
                            <div class="task-row task-head">
                                <span>任务</span>
                                <span>权重</span>
                                <span>单次</span>
                                <span>截止</span>
                            </div>
                            <div v-for="task in draft.tasks" :key="task.title" class="task-row">
                                <span>
                                    <strong>{{ task.title }}</strong>
                                    <small>{{ task.description }}</small>
                                </span>
                                <span>{{ task.weight }}</span>
                                <span>{{ task.minSessionMinutes }} 分钟</span>
                                <span>{{ task.deadline }}</span>
                            </div>
                        </div>
                    </section>

                    <section class="draft-section">
                        <h3>Local Setting</h3>
                        <div class="setting-grid">
                            <div v-for="item in settingSummary" :key="item.label" class="setting-item">
                                <span>{{ item.label }}</span>
                                <strong>{{ item.value }}</strong>
                            </div>
                        </div>
                        <p class="ratio-text">{{ formatWeekdayRatio(draft.setting) }}</p>
                    </section>

                    <section v-if="draft.warnings?.length" class="draft-section tips-section">
                        <h3>Tips</h3>
                        <ul>
                            <li v-for="warning in draft.warnings" :key="warning">{{ warning }}</li>
                        </ul>
                    </section>

                    <div class="apply-actions">
                        <button class="secondary-button" type="button" :disabled="applying" @click="emit('open-plans')">
                            查看计划
                        </button>
                        <button class="primary-button" type="button" :disabled="applying" @click="applyDraft">
                            <RefreshCw v-if="applying" :size="16" stroke-width="2.1" class="spin" />
                            <CheckCircle2 v-else :size="16" stroke-width="2.1" />
                            确认创建
                        </button>
                    </div>
                </div>
            </section>
        </div>
    </section>
</template>

<style scoped>
.ai-page {
    min-height: 100%;
    color: #172033;
}

.ai-workspace {
    display: grid;
    grid-template-columns: minmax(360px, 0.9fr) minmax(480px, 1.1fr);
    gap: 18px;
    align-items: start;
}

.chat-panel,
.draft-panel {
    min-width: 0;
    border: 1px solid #d8dee9;
    border-radius: 8px;
    background: #ffffff;
}

.chat-panel {
    min-height: calc(100vh - 116px);
    display: grid;
    grid-template-rows: auto auto 1fr auto;
}

.draft-panel {
    padding-bottom: 16px;
}

.panel-header {
    min-height: 66px;
    border-bottom: 1px solid #e5eaf2;
    padding: 14px 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.eyebrow {
    margin: 0 0 3px;
    color: #2563eb;
    font-size: 13px;
    font-weight: 700;
}

h2,
h3,
p,
dl,
dd,
ul {
    margin: 0;
}

h2 {
    font-size: 20px;
    line-height: 1.2;
}

h3 {
    margin-bottom: 10px;
    font-size: 15px;
}

.domain-pill {
    border: 1px solid #bfd1ee;
    border-radius: 999px;
    padding: 5px 10px;
    color: #1849a9;
    background: #eaf1ff;
    font-size: 13px;
    font-weight: 700;
}

.example-row {
    border-bottom: 1px solid #eef2f7;
    padding: 10px 12px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.example-button {
    border: 1px solid #c7d0df;
    border-radius: 999px;
    padding: 6px 10px;
    color: #2f3a4f;
    background: #ffffff;
    font: inherit;
    font-size: 13px;
    cursor: pointer;
}

.example-button:hover {
    color: #1849a9;
    background: #eaf1ff;
}

.message-list {
    min-height: 320px;
    padding: 16px;
    display: grid;
    align-content: start;
    gap: 12px;
    overflow-y: auto;
}

.empty-chat,
.empty-draft {
    min-height: 220px;
    display: grid;
    align-content: center;
    justify-items: center;
    gap: 10px;
    color: #667085;
    text-align: center;
}

.chat-message {
    max-width: 88%;
    border: 1px solid #d8dee9;
    border-radius: 8px;
    padding: 10px 12px;
    display: grid;
    gap: 6px;
    font-size: 14px;
}

.chat-message strong {
    font-size: 13px;
}

.chat-message.user {
    justify-self: end;
    color: #102116;
    border-color: #95d5a6;
    background: #b9f6c8;
}

.chat-message.assistant {
    justify-self: start;
    background: #f8fafc;
}

.chat-message p {
    line-height: 1.55;
    white-space: pre-line;
}

.prompt-form {
    border-top: 1px solid #e5eaf2;
    padding: 14px;
    display: grid;
    gap: 10px;
}

textarea {
    width: 100%;
    min-height: 108px;
    box-sizing: border-box;
    border: 1px solid #c7d0df;
    border-radius: 8px;
    padding: 10px 12px;
    color: #172033;
    background: #ffffff;
    font: inherit;
    line-height: 1.5;
    resize: vertical;
}

textarea:focus {
    border-color: #2563eb;
    outline: 2px solid #bfdbfe;
}

.form-actions,
.apply-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}

.primary-button,
.secondary-button {
    min-height: 36px;
    border-radius: 6px;
    padding: 0 13px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 7px;
    font: inherit;
    font-weight: 700;
    cursor: pointer;
}

.primary-button {
    border: 1px solid #2563eb;
    color: #ffffff;
    background: #2563eb;
}

.primary-button:hover {
    background: #1d4ed8;
}

.secondary-button {
    border: 1px solid #c7d0df;
    color: #2f3a4f;
    background: #ffffff;
}

.secondary-button:hover {
    background: #eef2f7;
}

.primary-button:disabled,
.secondary-button:disabled {
    cursor: not-allowed;
    opacity: 0.65;
}

.success-message {
    margin: 14px 16px 0;
    border: 1px solid #bbf7d0;
    border-radius: 8px;
    padding: 10px 12px;
    color: #166534;
    background: #f0fdf4;
    font-weight: 700;
}

.draft-content {
    padding: 16px;
    display: grid;
    gap: 16px;
}

.draft-section {
    border: 1px solid #e5eaf2;
    border-radius: 8px;
    padding: 14px;
}

.project-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
}

.project-grid .wide {
    grid-column: 1 / -1;
}

dt {
    margin-bottom: 4px;
    color: #667085;
    font-size: 13px;
}

dd {
    color: #172033;
    line-height: 1.5;
}

.task-table {
    display: grid;
    gap: 8px;
}

.task-row {
    display: grid;
    grid-template-columns: minmax(180px, 1fr) 52px 82px 108px;
    gap: 10px;
    align-items: center;
    border: 1px solid #eef2f7;
    border-radius: 8px;
    padding: 9px 10px;
    font-size: 14px;
}

.task-head {
    color: #667085;
    background: #f8fafc;
    font-size: 13px;
    font-weight: 700;
}

.task-row strong,
.task-row small {
    display: block;
}

.task-row small {
    margin-top: 3px;
    color: #667085;
    line-height: 1.4;
}

.setting-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
}

.setting-item {
    border: 1px solid #eef2f7;
    border-radius: 8px;
    padding: 10px;
    display: grid;
    gap: 4px;
}

.setting-item span {
    color: #667085;
    font-size: 13px;
}

.ratio-text {
    margin-top: 10px;
    color: #2f3a4f;
    font-size: 14px;
}

.tips-section {
    border-color: #cfe7da;
    background: #f6fbf8;
}

.tips-section h3 {
    color: #245b3f;
}

.tips-section ul {
    padding-left: 18px;
    color: #385948;
    line-height: 1.6;
}

.spin {
    animation: spin 0.9s linear infinite;
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
}

@media (max-width: 1060px) {
    .ai-workspace {
        grid-template-columns: 1fr;
    }

    .chat-panel {
        min-height: auto;
    }
}

@media (max-width: 720px) {
    .task-row {
        grid-template-columns: 1fr 48px;
    }

    .task-row span:nth-child(3),
    .task-row span:nth-child(4) {
        grid-column: span 1;
    }

    .setting-grid,
    .project-grid {
        grid-template-columns: 1fr;
    }
}
</style>
