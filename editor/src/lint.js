// What the linter tells of besides syntax errors: mistakes that are valid code. No style rules, and nothing about
// names the script doesn't define, as the app's APIs are globals the editor doesn't know.
const RULES = {
    "no-const-assign": "error",
    "no-use-before-define": ["error", { functions: false, classes: false, variables: false }],
    "no-import-assign": "error",
    "no-this-before-super": "error",
    "constructor-super": "error",
    "no-cond-assign": "warn",
    "no-unreachable": "warn",
    "no-fallthrough": "warn",
    "no-dupe-keys": "warn",
    "no-dupe-args": "warn",
    "no-dupe-class-members": "warn",
    "no-dupe-else-if": "warn",
    "no-duplicate-case": "warn",
    "no-self-assign": "warn",
    "no-func-assign": "warn",
    "no-class-assign": "warn",
    "no-unsafe-negation": "warn",
    "use-isnan": "warn",
    "valid-typeof": "warn",
    "no-debugger": "warn",
};

const LINTER_URL = "./eslint.js";

let linter = null;
let loading = null;

// The linter is large, so it is loaded after the editor is up, on the first check. Until then, and if it can't be
// loaded, nothing is reported.
const loadLinter = () => {
    loading ??= new Promise((resolve) => {
        const script = document.createElement("script");
        script.src = LINTER_URL;
        script.onload = () => {
            if (globalThis.eslint?.Linter) linter = new globalThis.eslint.Linter();
            resolve();
        };
        script.onerror = () => resolve();
        document.head.appendChild(script);
    });
    return loading;
};

const verify = (code, sourceType, jsx) =>
    linter.verify(code, [{
        linterOptions: { reportUnusedDisableDirectives: "off" },
        languageOptions: {
            ecmaVersion: "latest",
            sourceType,
            parserOptions: { ecmaFeatures: { jsx } },
        },
        rules: RULES,
    }]);

const fatalAt = (messages, doc) => {
    const fatal = messages.find((message) => message.fatal);
    if (!fatal) return Infinity;
    return positionOf(doc, fatal.line, fatal.column);
};

const positionOf = (doc, line, column) => {
    if (!line || line > doc.lines) return doc.length;
    const at = doc.line(line);
    return Math.min(at.from + Math.max((column ?? 1) - 1, 0), at.to);
};

// A NeonJS file is a module, with top-level await, and a Rhino or legacy script is not, and may use what a module
// forbids (`with`, a name such as `package`). The editor isn't told which a file is, so a file that has no import or
// export and doesn't parse as a module is tried as a script; the reading that gets further in the file is shown.
export const lintMessages = (code, doc, jsx = false) => {
    const asModule = verify(code, "module", jsx);
    if (fatalAt(asModule, doc) === Infinity || /^\s*(import|export)\b/m.test(code)) return asModule;
    const asScript = verify(code, "script", jsx);
    return fatalAt(asScript, doc) > fatalAt(asModule, doc) ? asScript : asModule;
};

const lintJavaScript = (jsx) => async (view) => {
    if (!linter) await loadLinter();
    if (!linter) return [];
    const doc = view.state.doc;
    const diagnostics = [];
    for (const message of lintMessages(doc.toString(), doc, jsx)) {
        const from = positionOf(doc, message.line, message.column);
        const to = message.endLine ? Math.max(positionOf(doc, message.endLine, message.endColumn), from) : from;
        diagnostics.push({
            from,
            to,
            severity: message.fatal || message.severity === 2 ? "error" : "warning",
            message: message.fatal ? message.message.replace(/^Parsing error: /, "") : message.message,
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
            return lintJavaScript(false);
        case "jsx":
            return lintJavaScript(true);
        case "json":
            return lintJson;
        default:
            return () => [];
    }
};
