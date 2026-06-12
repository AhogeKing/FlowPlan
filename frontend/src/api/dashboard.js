import request from "./request";

export function getDashboardToday(config = {}) {
    return request.get("/dashboard/today", config);
}
