const JSHINT_OPTIONS = {
    esversion: 11,
    undef: false,
    unused: false,
    expr: true,
    laxbreak: true,
    laxcomma: true,
    lastsemic: true,
    multistr: true,
    loopfunc: true,
    evil: true,
    devel: true,
    browser: true,
    maxerr: 100,
};

const IGNORED = new Set(["W117"]);

const SEVERITY = { E: "error", W: "warning", I: "info" };

const lintJavaScript = (view) => {
    if (typeof JSHINT === "undefined") return [];
    const doc = view.state.doc;
    const code = doc.toString();
    JSHINT(code, { ...JSHINT_OPTIONS, module: /^\s*(import|export)\b/m.test(code) });
    const diagnostics = [];
    for (const error of JSHINT.errors) {
        if (!error || !error.line || error.line > doc.lines || IGNORED.has(error.code)) continue;
        const line = doc.line(error.line);
        const from = Math.min(line.from + Math.max((error.character ?? 1) - 1, 0), line.to);
        diagnostics.push({
            from,
            to: from,
            severity: SEVERITY[error.code?.[0]] ?? "warning",
            message: error.reason,
        });
    }
    return diagnostics;
};

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

export const lintSource = (mode) => {
    switch (mode) {
        case "javascript":
        case "jsx":
            return lintJavaScript;
        case "json":
            return lintJson;
        default:
            return () => [];
    }
};
