import request from "./request";
import { clearAiBuilderState } from "../utils/aiBuilderStorage";

export function startAiDraft(payload) {
    return request.post("/ai/draft/start", {
        sessionId: payload?.sessionId || null,
        message: payload?.message || "",
        messages: Array.isArray(payload?.messages) ? payload.messages : [],
        currentDraft: payload?.currentDraft || null
    }, { timeout: 30000 });
}

export async function startAiDraftStream(payload, handlers = {}) {
    const token = localStorage.getItem("token");
    const response = await fetch("/api/ai/draft/stream", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify({
            sessionId: payload?.sessionId || null,
            message: payload?.message || "",
            messages: Array.isArray(payload?.messages) ? payload.messages : [],
            currentDraft: payload?.currentDraft || null
        })
    });

    if (!response.ok || !response.body) {
        if (response.status === 401) {
            localStorage.removeItem("token");
            clearAiBuilderState();
            window.dispatchEvent(new Event("auth-expired"));
        }
        const text = await response.text();
        throw new Error(text || `AI 流式请求失败：${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    while (true) {
        const { value, done } = await reader.read();
        if (done) {
            break;
        }
        buffer += decoder.decode(value, { stream: true });
        buffer = buffer.replace(/\r\n/g, "\n");
        const blocks = buffer.split("\n\n");
        buffer = blocks.pop() || "";

        for (const block of blocks) {
            handleSseBlock(block, handlers);
        }
    }

    if (buffer.trim()) {
        handleSseBlock(buffer, handlers);
    }
}

function handleSseBlock(block, handlers) {
    const lines = block.replace(/\r\n/g, "\n").split("\n");
    let event = "message";
    const dataLines = [];

    for (const line of lines) {
        if (line.startsWith("event:")) {
            event = line.slice("event:".length).trim();
            continue;
        }
        if (line.startsWith("data:")) {
            dataLines.push(line.slice("data:".length).trimStart());
        }
    }

    const data = dataLines.join("\n");
    if (!data) {
        return;
    }

    if (event === "token") {
        handlers.onToken?.(parseTokenData(data));
        return;
    }
    if (event === "final") {
        handlers.onFinal?.(JSON.parse(data));
        return;
    }
    if (event === "error") {
        handlers.onError?.(data);
    }
}

function parseTokenData(data) {
    try {
        const parsed = JSON.parse(data);
        if (parsed && typeof parsed.text === "string") {
            return parsed.text;
        }
    } catch {
        // Backward compatible with old plain-text token events.
    }
    return data;
}

export function applyAiDraft(payload) {
    return request.post("/ai/draft/apply", {
        sessionId: payload?.sessionId || null,
        draft: payload?.draft
    }, { timeout: 30000 });
}

export function clearAiDraftSession(sessionId) {
    if (!sessionId) {
        return Promise.resolve();
    }
    return request.delete(`/ai/draft/session/${encodeURIComponent(sessionId)}`, { silent: true });
}
