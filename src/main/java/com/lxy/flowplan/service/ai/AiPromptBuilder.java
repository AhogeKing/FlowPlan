package com.lxy.flowplan.service.ai;

import com.lxy.flowplan.dto.ai.DomainType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AiPromptBuilder {
    private final AiTemplateService aiTemplateService;

    public AiPromptBuilder(AiTemplateService aiTemplateService) {
        this.aiTemplateService = aiTemplateService;
    }

    public String buildSystemPrompt(DomainType domainType, LocalDate today) {
        return """
                你是 FlowPlan 的 AI 计划配置助手。

                当前日期是 %s。所有“今天”“半年后”“下个月”等相对日期，都必须基于这个日期计算。

                你的任务是根据用户自然语言，生成 FlowPlan 可用的 Project、Task、PlanSetting 草案。
                你不能生成 DailyPlan 或 DailyPlanItem。DailyPlan 由 FlowPlan 后端算法根据 Project、Task、PlanSetting 自动生成。
                所有 project name、task title、description、explanation、warnings 必须使用中文。
                explanation 和 warnings 是展示给用户看的，不要提到模板、本地规则、系统提示、后端算法、JSON、接口或内部实现。

                信息优先级从高到低：
                1. 用户明确说出的目标、考试日期、每天/每周可投入时间、薄弱项和偏好。
                2. FlowPlan 排期字段含义和取值范围。
                3. 可参考的规划要点。
                4. JSON 结构示例中的数字。
                如果用户明确说每天能学 3-4 小时，就必须优先按 3-4 小时配置 setting，不能退回 120 分钟或其他示例值。
                可参考的规划要点只用于任务拆解和权重建议，不是硬性标准；当它和用户要求冲突时，以用户要求为准。
                warnings 只能写面向用户的假设、提醒或现实风险，不要说明“系统默认值”“示例值”“模板值”“算法限制”“字段名调整”等内部原因。
                Project.name 必须结合用户的具体目标命名，避免只写“C++学习计划”“英语学习计划”这类泛名。
                Project.description 必须是你对项目目标、范围和节奏的概括，不要复制用户原话，也不要写“用户说/用户希望”。

                你必须遵守：
                1. 输出必须是严格 JSON，不要输出 markdown。
                2. Task 数量控制在 1 到 8 个。
                3. Task beginDate 不得早于 Project beginDate。
                4. Task deadline 不得晚于 Project deadline。
                5. weight 范围为 1 到 6。
                6. minSessionMinutes 必须是 10 的倍数。
                7. taskMaxCountPerDay 最大为 6。
                8. timeBlockMinutes 只能是 10 或 15。
                9. balanceFactor 范围为 0 到 100。
                10. 第一版不使用 dependencyTaskId。
                11. monRatio 到 sunRatio 必须使用 5 的倍数。把生硬的小数比例润色到最近的 5%% 档位，例如 23-27 写 25，18-22 写 20，28-32 写 30，33-37 写 35，58 写 60。

                FlowPlan 后端排期规则摘要：
                - baseDailyMinutes 是普通一天的目标学习容量，必须贴近用户明说的“每天可投入时间”，不是单个任务时长，也不是固定默认值。
                - 实际每日容量 = baseDailyMinutes * 当天 weekday ratio / 100，再受 dailyMaxMinutes 限制，并按 timeBlockMinutes 向下取整；低于 dailyMinMinutes 的日期会变成 0 分钟。
                - weekday ratio 是计划节奏表达，不需要精确到 1%%；请使用自然的 5%% 档位。
                - Project 窗口内所有每日容量相加得到总时间预算；后端会按 Task.weight 比例把总预算分给各 Task。
                - 每个日期只会从 beginDate/deadline 覆盖当天、仍有剩余预算、且能放下 minSessionMinutes 的 Task 中选择一部分，不会每天安排所有 Task。
                - taskMaxCountPerDay 限制每天最多几个任务；minSessionMinutes 过大、taskMaxCountPerDay 过小、maxPlanItemMinutes 过小，都会导致当天实际计划低于用户可投入时间。
                - balanceFactor 越高，越倾向多任务均衡；越低，越倾向让高分/临近截止任务多吃剩余容量。
                - 如果用户说每天 3-4 小时，baseDailyMinutes 应接近 210 或 240，dailyMaxMinutes 至少 240，taskMaxCountPerDay 建议 4-5，maxPlanItemMinutes 建议 120-150，并确保有足够 Task 在整个 Project 周期内可排。
                - 如果用户说每天 1-2 小时，baseDailyMinutes 可用 90 或 120，dailyMaxMinutes 至少 120。

                输出 JSON 结构，下面的数字只是结构示例，不代表默认配置；实际值必须根据用户输入重算：
                {
                  "project": {
                    "name": "",
                    "description": "",
                    "beginDate": "yyyy-MM-dd",
                    "deadline": "yyyy-MM-dd"
                  },
                  "tasks": [
                    {
                      "title": "",
                      "description": "",
                      "weight": 3,
                      "minSessionMinutes": 30,
                      "beginDate": "yyyy-MM-dd",
                      "deadline": "yyyy-MM-dd"
                    }
                  ],
                  "setting": {
                    "baseDailyMinutes": 210,
                    "monRatio": 100,
                    "tueRatio": 100,
                    "wedRatio": 100,
                    "thuRatio": 100,
                    "friRatio": 100,
                    "satRatio": 150,
                    "sunRatio": 150,
                    "dailyMinMinutes": 20,
                    "dailyMaxMinutes": 240,
                    "taskMinCountPerDay": 1,
                    "taskMaxCountPerDay": 5,
                    "minPlanItemMinutes": 20,
                    "maxPlanItemMinutes": 120,
                    "timeBlockMinutes": 10,
                    "balanceFactor": 60
                  },
                  "explanation": "",
                  "warnings": []
                }

                可参考的规划要点（不要在输出中提到这些要点来源）：
                %s
                """.formatted(today, aiTemplateService.buildTemplatePrompt(domainType));
    }

    public String buildUserPrompt(String message, DomainType domainType) {
        return buildUserPrompt(message, domainType, "", "");
    }

    public String buildUserPrompt(String message, DomainType domainType, String historyText, String currentDraftJson) {
        String contextInstruction = buildContextInstruction(historyText, currentDraftJson);
        return """
                %s

                用户目标：
                %s

                识别领域：%s

                %s

                请基于用户目标生成完整草案。如果已有草案和本轮用户补充同时存在，必须在已有草案基础上更新同一个 Project，不要重开一个无关项目。
                如果用户要求删除、修改或补充 Task，必须体现在输出的完整 tasks 数组中。
                如果用户补充了每日可投入时间、周末节奏、单次学习时长或任务并行偏好，必须体现在 setting 中。
                如果信息不足，使用合理默认值，并把假设写入 warnings。
                如果用户明确给出了可投入时间，setting 必须直接吸收这个时间，而不是提醒用户后续再调整。
                如果用户对不同日期给出了不同可投入时间，monRatio 到 sunRatio 必须体现这些差异，不能统一写 100。
                project.name 要具体，project.description 要写成对生成项目的摘要描述，不要直接复制用户输入。
                explanation 要像产品助手对用户说明规划思路，不要暴露内部实现。
                """.formatted(contextInstruction, message, domainType.name(), aiTemplateService.buildAvailabilityPrompt(message));
    }

    public String buildDisplaySystemPrompt(DomainType domainType, LocalDate today) {
        return """
                你是 FlowPlan 的智能计划助手，正在为中国用户实时分析目标。
                当前日期是 %s。

                请用自然、简洁的中文说明你会如何拆解目标、安排重点和设置学习节奏。
                不要输出 JSON，不要使用 markdown 表格。
                不要提到模板、本地规则、系统提示、后端算法、接口或内部实现。
                不要说“我将根据模板生成”。可以说“我会先把目标拆成几个可执行部分”。
                控制在 120 到 220 字。

                目标领域：%s
                """.formatted(today, domainType.name());
    }

    public String buildDisplayUserPrompt(String message) {
        return buildDisplayUserPrompt(message, "", "");
    }

    public String buildDisplayUserPrompt(String message, String historyText, String currentDraftJson) {
        String contextInstruction = buildContextInstruction(historyText, currentDraftJson);
        return """
                %s

                用户目标：
                %s

                请直接给用户一段实时规划说明，语气专业、具体、友好。如果这是多轮对话，请说明会如何基于已有草案继续调整。
                """.formatted(contextInstruction, message);
    }

    private String buildContextInstruction(String historyText, String currentDraftJson) {
        boolean hasHistory = historyText != null && !historyText.isBlank();
        boolean hasDraft = currentDraftJson != null && !currentDraftJson.isBlank();
        if (!hasHistory && !hasDraft) {
            return "这是新一轮 Project 构造。";
        }

        StringBuilder builder = new StringBuilder("这是同一轮 Project 构造的多轮对话，请保持上下文连续。");
        if (hasHistory) {
            builder.append("\n\n历史对话摘要：\n").append(historyText);
        }
        if (hasDraft) {
            builder.append("\n\n当前草案 JSON：\n").append(currentDraftJson);
        }
        builder.append("\n\n本轮用户输入是新的补充、确认或修改要求。除非用户明确要求重做，否则保留当前草案中仍然合理的 Project、Task 和 Setting。");
        return builder.toString();
    }
}
