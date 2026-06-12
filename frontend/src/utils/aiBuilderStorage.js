const AI_BUILDER_STATE_KEY = "flowplan.aiBuilderState";

export function loadAiBuilderState() {
    try {
        const raw = localStorage.getItem(AI_BUILDER_STATE_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

export function saveAiBuilderState(state) {
    localStorage.setItem(AI_BUILDER_STATE_KEY, JSON.stringify(state));
}

export function clearAiBuilderState() {
    localStorage.removeItem(AI_BUILDER_STATE_KEY);
}
