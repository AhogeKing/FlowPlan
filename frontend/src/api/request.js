import axios from "axios";
import { clearAiBuilderState } from "../utils/aiBuilderStorage";

const request = axios.create({
    baseURL: "/api",
    timeout: 5000
});

function buildApiError(message, extra = {}) {
    const error = new Error(message || "请求失败");
    Object.assign(error, extra);
    error.msg = message || "请求失败";
    return error;
}

request.interceptors.request.use(config => {
    const token = localStorage.getItem("token");
    if (token) {
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

request.interceptors.response.use(
    response => {
        const res = response.data;
        const silent = response.config?.silent === true;

        if (res.code !== 0) {
            const error = buildApiError(res.msg, { code: res.code, data: res.data, result: res });
            if (!silent) {
                alert(error.message);
            }
            return Promise.reject(error);
        }
        return res.data;
    },
    error => {
        const status = error.response?.status;
        const message = error.response?.data?.msg;
        const silent = error.config?.silent === true;
        const apiError = buildApiError(message || error.message || "服务器连接失败", {
            status,
            response: error.response,
            originalError: error
        });

        if (status === 401) {
            localStorage.removeItem("token");
            clearAiBuilderState();
            window.dispatchEvent(new Event("auth-expired"));
            if (!silent) {
                alert(message || "登录状态已失效，请重新登录");
            }
            return Promise.reject(apiError);
        }

        if (!silent) {
            alert(apiError.message);
        }
        return Promise.reject(apiError);
    }
);

export default request;
