import { nativeLint } from "./native-lint.js";

// A script is checked by the app (native-lint.js), which knows what the project's engine reads and what its scripts
// are given. When the app gives no answer, nothing is marked.
const lintJavaScript = (sessionId) => async (view) => (await nativeLint(view, sessionId?.())) ?? [];

const lintJson = (view) => {
    const code = view.state.doc.toString();
    if (!code.trim()) return [];
    try {
        JSON.parse(code);
        return [];
    } catch (e) {
        const position = Number(/position (\d+)/.exec(e.message)?.[1] ?? 0);
        const from = Math.min(position, code.length);
        return [{ from, to: from, severity: "error", message: e.message }];
    }
};

// `sessionId` gives the tab the editor shows, for the app's linter to tell tabs apart.
export const lintSource = (mode, sessionId) => {
    switch (mode) {
        case "javascript":
            return lintJavaScript(sessionId);
        case "json":
            return lintJson;
        default:
            return () => [];
    }
};
