import request from "./request";

export function getAdminOverview(config = {}) {
    return request.get("/admin/overview", config);
}

export function listAdminUsers(config = {}) {
    return request.get("/admin/users", config);
}

export function listOperationLogs(params = {}, config = {}) {
    return request.get("/admin/operation-logs", {
        ...config,
        params: {
            page: params.page || 1,
            size: params.size || 20,
            module: params.module || undefined
        }
    });
}

export function updateAdminUserRole(userId, role, config = {}) {
    return request.patch(`/admin/users/${userId}/role`, null, {
        ...config,
        params: { role }
    });
}

export function deleteAdminUser(userId, config = {}) {
    return request.delete(`/admin/users/${userId}`, config);
}
