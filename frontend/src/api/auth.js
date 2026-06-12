import request from "./request";

function cleanParams(params) {
    return Object.fromEntries(
        Object.entries(params)
            .map(([key, value]) => [key, typeof value === "string" ? value.trim() : value])
            .filter(([, value]) => value !== undefined && value !== null && value !== "")
    );
}

export function login(username, password) {
    return request.post("/app-user/login", null, {
        params: cleanParams({ username, password })
    });
}

export function register(username, password, email) {
    return request.post("/app-user/register", null, {
        params: cleanParams({ username, password, email })
    });
}

export function getCurrentUser() {
    return request.get("/app-user/info");
}

export function logout() {
    return request.post("/app-user/logout");
}
