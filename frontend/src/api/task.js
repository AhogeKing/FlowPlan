import request from "./request";

function toNumberOrNull(value) {
    if (value === undefined || value === null || value === "") {
        return null;
    }
    return Number(value);
}

function normalizeTask(task) {
    return {
        ...task,
        weight: task.weight ?? 1,
        min_session_minutes: task.min_session_minutes ?? null,
        dependency_task_id: task.dependency_task_id ?? null,
        done_flag: Boolean(task.done_flag),
        status: task.status || (task.done_flag ? "DONE" : "NOT_STARTED")
    };
}

function normalizeTaskPayload(task) {
    return {
        title: task.title?.trim(),
        description: task.description?.trim() || null,
        weight: Number(task.weight || 1),
        min_session_minutes: toNumberOrNull(task.min_session_minutes),
        begin_date: task.begin_date || null,
        deadline: task.deadline || null,
        dependency_task_id: toNumberOrNull(task.dependency_task_id),
        done_flag: Boolean(task.done_flag),
        status: task.status || (task.done_flag ? "DONE" : "NOT_STARTED")
    };
}

export async function listTasks(projectId) {
    const tasks = await request.get(`/project/${projectId}/task/list`);
    return Array.isArray(tasks) ? tasks.map(normalizeTask) : [];
}

export function addTask(projectId, task) {
    return request.post(`/project/${projectId}/task/add`, normalizeTaskPayload(task));
}

export function updateTask(projectId, taskId, task) {
    return request.put(`/project/${projectId}/task/${taskId}`, normalizeTaskPayload(task));
}

export function deleteTask(projectId, taskId) {
    return request.delete(`/project/${projectId}/task/${taskId}`);
}
