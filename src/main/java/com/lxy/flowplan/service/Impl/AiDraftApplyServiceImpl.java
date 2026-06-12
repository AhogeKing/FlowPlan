package com.lxy.flowplan.service.Impl;

import com.lxy.flowplan.pojo.PlanGenerateResult;
import com.lxy.flowplan.pojo.PlanSetting;
import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.pojo.Task;
import com.lxy.flowplan.dto.ai.AiApplyResult;
import com.lxy.flowplan.dto.ai.AiDraftApplyRequest;
import com.lxy.flowplan.dto.ai.AiProjectDraft;
import com.lxy.flowplan.dto.ai.DomainType;
import com.lxy.flowplan.dto.ai.PlanSettingDraft;
import com.lxy.flowplan.dto.ai.ProjectDraft;
import com.lxy.flowplan.dto.ai.TaskDraft;
import com.lxy.flowplan.service.AiDraftApplyService;
import com.lxy.flowplan.service.OperationLogService;
import com.lxy.flowplan.service.PlanService;
import com.lxy.flowplan.service.PlanSettingService;
import com.lxy.flowplan.service.ProjectService;
import com.lxy.flowplan.service.TaskService;
import com.lxy.flowplan.service.ai.AiAuditService;
import com.lxy.flowplan.service.ai.AiDraftSanitizer;
import com.lxy.flowplan.service.ai.AiTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class AiDraftApplyServiceImpl implements AiDraftApplyService {
    private final ProjectService projectService;
    private final TaskService taskService;
    private final PlanSettingService planSettingService;
    private final PlanService planService;
    private final AiDraftSanitizer aiDraftSanitizer;
    private final AiTemplateService aiTemplateService;
    private final AiAuditService aiAuditService;
    private final OperationLogService operationLogService;

    public AiDraftApplyServiceImpl(ProjectService projectService,
                                   TaskService taskService,
                                   PlanSettingService planSettingService,
                                   PlanService planService,
                                   AiDraftSanitizer aiDraftSanitizer,
                                   AiTemplateService aiTemplateService,
                                   AiAuditService aiAuditService,
                                   OperationLogService operationLogService) {
        this.projectService = projectService;
        this.taskService = taskService;
        this.planSettingService = planSettingService;
        this.planService = planService;
        this.aiDraftSanitizer = aiDraftSanitizer;
        this.aiTemplateService = aiTemplateService;
        this.aiAuditService = aiAuditService;
        this.operationLogService = operationLogService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiApplyResult applyDraft(AiDraftApplyRequest request) {
        if (request == null || request.getDraft() == null) {
            throw new IllegalArgumentException("AI 草案不能为空");
        }

        DomainType domainType = request.getDraft().getDomainType() == null ? DomainType.GENERAL : request.getDraft().getDomainType();
        AiProjectDraft fallback = aiTemplateService.buildFallbackDraft("", domainType, LocalDate.now());
        AiProjectDraft draft = aiDraftSanitizer.sanitize(request.getDraft(), fallback, domainType, LocalDate.now());

        Project project = projectService.createProject(toProject(draft.getProject()));
        if (project.getId() == null) {
            throw new IllegalStateException("Project 创建后未返回 id");
        }

        for (TaskDraft taskDraft : draft.getTasks()) {
            taskService.addTask(project.getId(), toTask(taskDraft));
        }

        planSettingService.addLocalPlanSetting(project.getId(), toPlanSetting(draft.getSetting()));
        PlanGenerateResult planResult = planService.generatePlan(project.getId());
        aiAuditService.recordApply(request.getSessionId(), project.getId());

        AiApplyResult result = new AiApplyResult();
        result.setProjectId(project.getId());
        result.setPlanResult(planResult);
        result.setMessage("AI 草案已创建为项目并生成计划。");
        operationLogService.log("AI", "APPLY", "Apply AI Draft to Project " + project.getId());
        return result;
    }

    private Project toProject(ProjectDraft draft) {
        Project project = new Project();
        project.setName(draft.getName());
        project.setDescription(draft.getDescription());
        project.setBeginDate(draft.getBeginDate());
        project.setDeadline(draft.getDeadline());
        return project;
    }

    private Task toTask(TaskDraft draft) {
        Task task = new Task();
        task.setTitle(draft.getTitle());
        task.setDescription(draft.getDescription());
        task.setWeight(draft.getWeight());
        task.setMinSessionMinutes(draft.getMinSessionMinutes());
        task.setBeginDate(draft.getBeginDate());
        task.setDeadline(draft.getDeadline());
        task.setDoneFlag(false);
        task.setStatus("NOT_STARTED");
        return task;
    }

    private PlanSetting toPlanSetting(PlanSettingDraft draft) {
        PlanSetting setting = new PlanSetting();
        setting.setBaseDailyMinutes(draft.getBaseDailyMinutes());
        setting.setMonRatio(draft.getMonRatio());
        setting.setTueRatio(draft.getTueRatio());
        setting.setWedRatio(draft.getWedRatio());
        setting.setThuRatio(draft.getThuRatio());
        setting.setFriRatio(draft.getFriRatio());
        setting.setSatRatio(draft.getSatRatio());
        setting.setSunRatio(draft.getSunRatio());
        setting.setDailyMinMinutes(draft.getDailyMinMinutes());
        setting.setDailyMaxMinutes(draft.getDailyMaxMinutes());
        setting.setTaskMinCountPerDay(draft.getTaskMinCountPerDay());
        setting.setTaskMaxCountPerDay(draft.getTaskMaxCountPerDay());
        setting.setMinPlanItemMinutes(draft.getMinPlanItemMinutes());
        setting.setMaxPlanItemMinutes(draft.getMaxPlanItemMinutes());
        setting.setTimeBlockMinutes(draft.getTimeBlockMinutes());
        setting.setBalanceFactor(draft.getBalanceFactor());
        return setting;
    }
}
