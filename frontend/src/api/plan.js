import request from "./request";

export function generatePlan(projectId, config = {}) {
    return request.post(`/project/${projectId}/plan/generate`, null, config);
}

export function listPlans(projectId, config = {}) {
    return request.get(`/project/${projectId}/plan/list`, config);
}

export function getPlanByDate(projectId, planDate, config = {}) {
    return request.get(`/project/${projectId}/plan/date/${planDate}`, config);
}

export function checkinPlanItem(projectId, planItemId, payload, config = {}) {
    return request.post(`/project/${projectId}/plan/item/${planItemId}/checkin`, payload, config);
}

export function deletePlanItemCheckin(projectId, planItemId, config = {}) {
    return request.delete(`/project/${projectId}/plan/item/${planItemId}/checkin`, config);
}
