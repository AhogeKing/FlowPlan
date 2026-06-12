import request from "./request";

export function getAnalyticsOverview(params = {}, config = {}) {
    return request.get("/analytics/overview", {
        ...config,
        params: {
            projectId: params.projectId || undefined,
            range: params.range || "7d"
        }
    });
}
