// Checks by the app's own linter, for the languages that have one, in place of this page's: the page sends the text
// over the bridge and is told the findings. The app says whether it has a linter for the project that is open; when it
// has none, or a check can't be made, `null` comes back and the page checks the text itself.

// How long the app is waited for. Its linter runs off this page's thread, so a slow one doesn't hold the editor up;
// one that never answers must not leave the text unchecked.
const TIMEOUT_MILLIS = 4000;

const pending = new Map();
let lastId = 0;

const bridge = () => (typeof WebviewCallback === "undefined" ? null : WebviewCallback);

export const nativeLintAvailable = () => {
    try {
        return bridge()?.lintsNatively?.() === true;
    } catch (_) {
        return false;
    }
};

const settle = (id, value) => {
    const request = pending.get(id);
    if (!request) return;
    pending.delete(id);
    clearTimeout(request.timer);
    request.resolve(value);
};

const toDiagnostics = (list, doc) => list.map((item) => {
    const from = Math.min(Math.max(Number(item.start) || 0, 0), doc.length);
    const to = Math.min(Math.max(Number(item.end) || 0, from), doc.length);
    return { from, to, severity: item.severity === "error" ? "error" : "warning", message: String(item.message ?? "") };
});

// The findings of the app's linter for the text `view` shows, or null when the page is to check it itself.
export const nativeLint = (view, sessionId) => new Promise((resolve) => {
    const doc = view.state.doc;
    const session = String(sessionId ?? "");
    // The tab asks again, as its text changed: what its last request finds is of a text that is gone. The editor
    // drops findings for a text it no longer shows, so an empty answer is enough.
    for (const [id, request] of pending) if (request.session === session) settle(id, []);
    const id = ++lastId;
    const timer = setTimeout(() => settle(id, null), TIMEOUT_MILLIS);
    pending.set(id, { resolve, doc, session, timer });
    try {
        bridge().requestLint(id, session, doc.toString());
    } catch (_) {
        settle(id, null);
    }
});

// Called by the app with the findings of request `id`: an array of { start, end, severity, message }, offsets in
// UTF-16 units as this page's own, or null.
globalThis.onNativeLint = (id, list) => {
    const request = pending.get(id);
    if (!request) return;
    settle(id, Array.isArray(list) ? toDiagnostics(list, request.doc) : null);
};

// The tab `sessionId` is closed: nothing waits for its checks.
export const dropNativeLint = (sessionId) => {
    const session = String(sessionId ?? "");
    for (const [id, request] of pending) if (request.session === session) settle(id, []);
};
