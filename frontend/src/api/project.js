import request from "./request";

function normalizeProject(project) {
    return {
        ...project,
        status: project.status || "NOT_STARTED",
        risk_level: project.risk_level || "OK",
        need_replan: Boolean(project.need_replan)
    };
}

function normalizeProjectPayload(project) {
    return {
        name: project.name?.trim(),
        description: project.description?.trim() || null,
        begin_date: project.begin_date || null,
        deadline: project.deadline || null
    };
}

export async function listProjects() {
    const projects = await request.get("/project/list");
    return Array.isArray(projects) ? projects.map(normalizeProject) : [];
}

export function addProject(project) {
    return request.post("/project/add", normalizeProjectPayload(project));
}

export function updateProject(id, project) {
    return request.put(`/project/${id}`, normalizeProjectPayload(project));
}

export function deleteProject(id) {
    return request.delete(`/project/${id}`);
}
