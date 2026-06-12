<script setup>
import { BarChart3, Clock3, Flame, ListChecks, RefreshCw, Target, Timer } from "@lucide/vue";
import { BarChart, LineChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import * as echarts from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { getAnalyticsOverview } from "../api/analytics";
import { listProjects } from "../api/project";

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

const projects = ref([]);
const selectedProjectId = ref("");
const selectedRange = ref("7d");
const overview = ref(null);
const loading = ref(false);
const message = ref("");
const completionChartRef = ref(null);
const timeChartRef = ref(null);

let completionChart = null;
let timeChart = null;

const rangeOptions = [
    { key: "7d", label: "7 天" },
    { key: "30d", label: "30 天" }
];

const summary = computed(() => overview.value?.summary || {});

const summaryCards = computed(() => [
    {
        key: "today-focus",
        label: "今日投入",
        value: formatMinutes(summary.value.today_actual_minutes),
        subtext: "今天实际完成的投入时间",
        icon: Clock3
    },
    {
        key: "today-completed",
        label: "今日完成 Plan",
        value: `${summary.value.today_completed_items || 0} 项`,
        subtext: "今天已完成的计划项数量",
        icon: ListChecks
    },
    {
        key: "completion-rate",
        label: "完成率",
        value: `${summary.value.completion_rate || 0}%`,
        subtext: "当前时间范围内实际/推荐时间",
        icon: Target
    },
    {
        key: "total-focus",
        label: "累计投入",
        value: formatMinutes(summary.value.total_actual_minutes),
        subtext: "该范围 Project 的历史总投入",
        icon: Timer
    },
    {
        key: "total-completed",
        label: "累计完成 Plan",
        value: `${summary.value.total_completed_items || 0} 项`,
        subtext: "历史 FULL_DONE 计划项数量",
        icon: BarChart3
    },
    {
        key: "streak",
        label: "连续打卡",
        value: `${summary.value.streak_days || 0} 天`,
        subtext: "按打卡日期计算，今天未打卡时延续昨天",
        icon: Flame
    }
]);

const templateInsight = computed(() => {
    const timeTrend = overview.value?.time_trend || [];
    const completionTrend = overview.value?.completion_trend || [];
    const actualMinutes = timeTrend.reduce((total, point) => total + Number(point.actual_minutes || 0), 0);
    const recommendedMinutes = timeTrend.reduce((total, point) => total + Number(point.recommended_minutes || 0), 0);
    const completedPlans = completionTrend.reduce((total, point) => total + Number(point.completed_count || 0), 0);
    const rangeLabel = selectedRange.value === "30d" ? "最近 30 天" : "最近 7 天";
    const completionRate = summary.value.completion_rate || calculateRate(actualMinutes, recommendedMinutes);

    if (actualMinutes === 0 && completedPlans === 0) {
        return `${rangeLabel}：还没有形成打卡数据。先完成一次计划，Summary 就会开始记录你的节奏。`;
    }

    return `${rangeLabel}：你投入了 ${formatMinutes(actualMinutes)}，完成了 ${completedPlans} 个计划项，达到推荐时间的 ${completionRate}%。${buildInsightClosing(completionRate, actualMinutes)}`;
});

const todayCompletionStats = computed(() => {
    const point = findTodayPoint(overview.value?.completion_trend || []);
    return {
        completed: Number(point?.completed_count || 0),
        generated: Number(point?.recommended_count || 0)
    };
});

const todayTimeStats = computed(() => {
    const point = findTodayPoint(overview.value?.time_trend || []);
    return {
        actual: Number(point?.actual_minutes || 0),
        recommended: Number(point?.recommended_minutes || 0)
    };
});

async function loadAnalyticsPage() {
    loading.value = true;
    message.value = "";
    try {
        projects.value = await listProjects();
        await loadOverview();
    } finally {
        loading.value = false;
    }
}

async function loadOverview() {
    loading.value = true;
    message.value = "";
    try {
        overview.value = await getAnalyticsOverview({
            projectId: selectedProjectId.value,
            range: selectedRange.value
        }, { silent: true });
        await nextTick();
        renderCharts();
    } catch (error) {
        message.value = error?.msg || "Analytics 数据暂时无法加载";
    } finally {
        loading.value = false;
    }
}

function renderCharts() {
    renderCompletionChart();
    renderTimeChart();
}

function renderCompletionChart() {
    if (!completionChartRef.value) {
        return;
    }
    completionChart = completionChart || echarts.init(completionChartRef.value);
    const data = overview.value?.completion_trend || [];
    completionChart.setOption({
        color: ["#2563eb", "#16a34a"],
        tooltip: { trigger: "axis" },
        legend: {
            top: 0,
            right: 0,
            data: ["推荐", "完成"]
        },
        grid: {
            top: 42,
            right: 18,
            bottom: 28,
            left: 34,
            containLabel: true
        },
        xAxis: {
            type: "category",
            data: data.map(point => formatDateLabel(point.date))
        },
        yAxis: {
            type: "value",
            minInterval: 1
        },
        series: [
            {
                name: "推荐",
                type: "bar",
                data: data.map(point => point.recommended_count || 0),
                barMaxWidth: 22
            },
            {
                name: "完成",
                type: "bar",
                data: data.map(point => point.completed_count || 0),
                barMaxWidth: 22
            }
        ]
    });
}

function renderTimeChart() {
    if (!timeChartRef.value) {
        return;
    }
    timeChart = timeChart || echarts.init(timeChartRef.value);
    const data = overview.value?.time_trend || [];
    timeChart.setOption({
        color: ["#16a34a", "#94a3b8"],
        tooltip: { trigger: "axis" },
        legend: {
            top: 0,
            right: 0,
            data: ["实际", "推荐"]
        },
        grid: {
            top: 42,
            right: 18,
            bottom: 28,
            left: 34,
            containLabel: true
        },
        xAxis: {
            type: "category",
            boundaryGap: false,
            data: data.map(point => formatDateLabel(point.date))
        },
        yAxis: {
            type: "value"
        },
        series: [
            {
                name: "实际",
                type: "line",
                smooth: true,
                data: data.map(point => point.actual_minutes || 0)
            },
            {
                name: "推荐",
                type: "line",
                smooth: true,
                data: data.map(point => point.recommended_minutes || 0)
            }
        ]
    });
}

function resizeCharts() {
    completionChart?.resize();
    timeChart?.resize();
}

function findTodayPoint(points) {
    const today = formatLocalDate(new Date());
    return points.find(point => point.date === today);
}

function formatLocalDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function formatDateLabel(dateText) {
    if (!dateText) {
        return "-";
    }
    const date = new Date(`${dateText}T00:00:00`);
    if (Number.isNaN(date.getTime())) {
        return dateText;
    }
    return selectedRange.value === "7d"
        ? new Intl.DateTimeFormat("zh-CN", { weekday: "short" }).format(date)
        : new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit" }).format(date);
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

function calculateRate(actualMinutes, recommendedMinutes) {
    if (!recommendedMinutes) {
        return 0;
    }
    return Math.min(Math.round((actualMinutes / recommendedMinutes) * 100), 999);
}

function buildInsightClosing(completionRate, actualMinutes) {
    if (completionRate >= 100) {
        return "执行节奏很好，继续保持。";
    }
    if (completionRate >= 75) {
        return "整体推进很稳，再补一点就能追上推荐进度。";
    }
    if (actualMinutes > 0) {
        return "已经开始积累投入了，下一步先把节奏稳定下来。";
    }
    return "先从一次打卡开始建立节奏。";
}

watch([selectedProjectId, selectedRange], loadOverview);

onMounted(() => {
    loadAnalyticsPage();
    window.addEventListener("resize", resizeCharts);
});

onUnmounted(() => {
    window.removeEventListener("resize", resizeCharts);
    completionChart?.dispose();
    timeChart?.dispose();
});
</script>

<template>
    <section class="analytics-page">
        <div class="page-header">
            <div>
                <p class="eyebrow">Analytics</p>
                <h2>执行分析</h2>
            </div>
            <button class="refresh-icon-button" type="button" title="刷新分析" :disabled="loading" @click="loadOverview">
                <RefreshCw :class="{ spinning: loading }" :size="18" stroke-width="2.2" />
            </button>
        </div>

        <p v-if="message" class="error-message">{{ message }}</p>

        <div class="analytics-toolbar">
            <select v-model="selectedProjectId" :disabled="projects.length === 0">
                <option value="">所有项目</option>
                <option v-for="project in projects" :key="project.id" :value="String(project.id)">
                    {{ project.name }}
                </option>
            </select>
            <div class="range-tabs" aria-label="分析时间范围">
                <button
                    v-for="range in rangeOptions"
                    :key="range.key"
                    :class="['range-tab', { active: selectedRange === range.key }]"
                    type="button"
                    @click="selectedRange = range.key"
                >
                    {{ range.label }}
                </button>
            </div>
        </div>

        <div class="summary-grid">
            <article v-for="card in summaryCards" :key="card.key" class="summary-card">
                <component :is="card.icon" :size="19" stroke-width="2.1" />
                <div>
                    <span>{{ card.label }}</span>
                    <strong>{{ card.value }}</strong>
                    <small>{{ card.subtext }}</small>
                </div>
            </article>
        </div>

        <div class="chart-grid">
            <section class="chart-panel">
                <div class="chart-header">
                    <div>
                        <p class="eyebrow">趋势</p>
                        <h3>计划完成趋势</h3>
                    </div>
                    <span>推荐 / 完成</span>
                </div>
                <div ref="completionChartRef" class="chart-canvas" />
                <div class="chart-metrics">
                    <span>当日完成 Plan：{{ todayCompletionStats.completed }} 项</span>
                    <span>计划生成 Plan：{{ todayCompletionStats.generated }} 项</span>
                </div>
            </section>

            <section class="chart-panel">
                <div class="chart-header">
                    <div>
                        <p class="eyebrow">趋势</p>
                        <h3>投入时间趋势</h3>
                    </div>
                    <span>实际 / 推荐</span>
                </div>
                <div ref="timeChartRef" class="chart-canvas" />
                <div class="chart-metrics">
                    <span>当日投入时间：{{ formatMinutes(todayTimeStats.actual) }}</span>
                    <span>当日推荐时间：{{ formatMinutes(todayTimeStats.recommended) }}</span>
                </div>
            </section>
        </div>

        <section class="insight-panel">
            <p class="eyebrow">洞察</p>
            <p>{{ templateInsight }}</p>
        </section>
    </section>
</template>

<style scoped>
.analytics-page {
    display: grid;
    gap: 18px;
    color: #172033;
}

.page-header,
.analytics-toolbar,
.chart-header {
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

select,
button {
    border-radius: 6px;
    font: inherit;
}

select {
    min-width: 240px;
    border: 1px solid #c7d0df;
    padding: 9px 11px;
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

button:disabled,
select:disabled {
    cursor: not-allowed;
    opacity: 0.7;
}

.error-message {
    color: #b42318;
    font-size: 14px;
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

.range-tabs {
    display: flex;
    gap: 8px;
}

.range-tab {
    border-radius: 999px;
    padding: 7px 14px;
    color: #2f3a4f;
    background: #f5f7fb;
    font-weight: 700;
}

.range-tab.active {
    border-color: #2563eb;
    color: #ffffff;
    background: #2563eb;
}

.summary-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
    gap: 12px;
}

.summary-card {
    border: 1px solid #c7d0df;
    border-radius: 8px;
    padding: 15px;
    display: flex;
    align-items: flex-start;
    gap: 12px;
    background: #ffffff;
}

.summary-card svg {
    flex: 0 0 auto;
    color: #2563eb;
    margin-top: 2px;
}

.summary-card div {
    min-width: 0;
    display: grid;
    gap: 5px;
}

.summary-card span {
    color: #667085;
    font-size: 13px;
    font-weight: 700;
}

.summary-card strong {
    color: #172033;
    font-size: 22px;
}

.summary-card small {
    color: #667085;
    font-size: 12px;
    line-height: 1.45;
}

.insight-panel {
    border: 1px solid #c7d0df;
    border-radius: 8px;
    padding: 15px 16px;
    display: grid;
    gap: 6px;
    background: #ffffff;
}

.insight-panel p:last-child {
    color: #172033;
    font-size: 15px;
    font-weight: 700;
    line-height: 1.6;
}

.chart-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
}

.chart-panel {
    border: 1px solid #c7d0df;
    border-radius: 8px;
    overflow: hidden;
    background: #ffffff;
}

.chart-header {
    border-bottom: 1px solid #d8dee9;
    padding: 15px 16px;
}

.chart-header span {
    color: #667085;
    font-size: 13px;
    font-weight: 700;
}

.chart-canvas {
    width: 100%;
    height: 320px;
}

.chart-metrics {
    border-top: 1px solid #d8dee9;
    padding: 12px 16px;
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: center;
    gap: 16px;
    color: #172033;
    font-size: 14px;
    font-weight: 700;
}

.chart-metrics span:first-child {
    justify-self: start;
}

.chart-metrics span:last-child {
    justify-self: center;
}

@media (max-width: 980px) {
    .analytics-toolbar,
    .chart-header {
        align-items: flex-start;
        flex-direction: column;
    }

    select,
    .range-tabs {
        width: 100%;
    }

    .range-tab {
        flex: 1;
    }

    .chart-grid {
        grid-template-columns: 1fr;
    }

    .chart-metrics {
        align-items: flex-start;
        grid-template-columns: 1fr;
        gap: 6px;
    }

    .chart-metrics span:last-child {
        justify-self: start;
    }
}
</style>
