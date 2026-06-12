import request from "./request";

const DEFAULT_SETTING = {
    base_daily_minutes: 120,
    mon_ratio: 100,
    tue_ratio: 100,
    wed_ratio: 100,
    thu_ratio: 100,
    fri_ratio: 100,
    sat_ratio: 100,
    sun_ratio: 100,
    daily_min_minutes: 20,
    daily_max_minutes: 120,
    task_min_count_per_day: 1,
    task_max_count_per_day: 4,
    min_plan_item_minutes: 20,
    max_plan_item_minutes: 120,
    time_block_minutes: 10,
    balance_factor: 50
};

const NUMBER_FIELDS = Object.keys(DEFAULT_SETTING);

function normalizeSetting(setting = {}) {
    const normalized = { ...DEFAULT_SETTING, ...setting };
    for (const field of NUMBER_FIELDS) {
        normalized[field] = Number(normalized[field] ?? DEFAULT_SETTING[field]);
    }
    return normalized;
}

function normalizeSettingPayload(setting = {}) {
    const normalized = normalizeSetting(setting);
    return Object.fromEntries(
        NUMBER_FIELDS.map(field => [field, normalized[field]])
    );
}

export function settingDefaults() {
    return normalizeSetting();
}

export async function getGlobalSetting() {
    return normalizeSetting(await request.get("/setting/global"));
}

export function updateGlobalSetting(setting) {
    return request.put("/setting/global", normalizeSettingPayload(setting));
}

export function resetGlobalSetting() {
    return request.post("/setting/global/reset");
}

export async function getLocalSetting(projectId) {
    const setting = await request.get(`/setting/project/${projectId}/local`);
    return setting ? normalizeSetting(setting) : null;
}

export function createLocalSetting(projectId, setting) {
    return request.post(`/setting/project/${projectId}/local`, normalizeSettingPayload(setting));
}

export function updateLocalSetting(projectId, setting) {
    return request.put(`/setting/project/${projectId}/local`, normalizeSettingPayload(setting));
}

export function deleteLocalSetting(projectId) {
    return request.delete(`/setting/project/${projectId}/local`);
}
