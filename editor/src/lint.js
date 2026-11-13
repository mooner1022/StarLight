import { ensureSyntaxTree, syntaxTree } from "@codemirror/language";
import { nativeLint, nativeLintAvailable } from "./native-lint.js";

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
// export and doesn't parse as a module is tried as a script; the reading that gets further in the file is taken.
const firstRead = (code, doc, jsx) => {
    const asModule = verify(code, "module", jsx);
    const reached = fatalAt(asModule, doc);
    if (reached === Infinity || /^\s*(import|export)\b/m.test(code)) return { sourceType: "module", messages: asModule };
    const asScript = verify(code, "script", jsx);
    return fatalAt(asScript, doc) > reached
        ? { sourceType: "script", messages: asScript }
        : { sourceType: "module", messages: asModule };
};

const MAX_SYNTAX_ERRORS = 10;

// How long reading past syntax errors may take, as the editor waits meanwhile: past it, in a large file, the errors
// found so far are told and the rest once those are fixed.
const BUDGET_MILLIS = 150;

// What is left out of the code to read past a syntax error in it: a statement, or a member of a class.
const LEFT_OUT = /(Statement|Declaration)$|^StaticBlock$/;

const unitAround = (state, tree, pos, sides) => {
    for (const side of sides) {
        for (let node = tree.resolveInner(Math.min(pos, state.doc.length), side); node; node = node.parent)
            if (LEFT_OUT.test(node.name) && node.from <= pos && pos <= node.to) return { from: node.from, to: node.to };
    }
    return null;
};

// Where the editor's own syntax tree, which reads past errors, has its error marks.
const treeErrors = (tree) => {
    const marks = [];
    tree.iterate({ enter: (node) => { if (node.type.isError) marks.push(node.from); } });
    return marks;
};

// The parts of the code a syntax error at `pos` may spoil: the innermost statement or class member that ends at the
// tree's last error mark up to there, as the parser often tells of an error only at what follows it (a parameter list
// left open is told at the next member's name); the one around `pos`; and the line. `gone` are the parts left out
// already, whose marks are of earlier errors.
const spoiledBy = (state, tree, marks, gone, pos) => {
    const parts = [];
    const add = (part) => {
        if (part && !parts.some((other) => other.from === part.from && other.to === part.to)) parts.push(part);
    };
    for (let i = marks.length - 1; i >= 0; i--) {
        if (marks[i] > pos) continue;
        if (!gone.some((part) => part.from <= marks[i] && marks[i] < part.to))
            add(unitAround(state, tree, marks[i], [-1, 1]));
        break;
    }
    add(unitAround(state, tree, pos, [1, -1]));
    const line = state.doc.lineAt(Math.min(pos, state.doc.length));
    add({ from: line.from, to: line.to });
    return parts;
};

const blank = (code, from, to) => code.slice(0, from) + code.slice(from, to).replace(/[^\n\r]/g, " ") + code.slice(to);

// The parser stops at the first syntax error, and no rule runs on code that doesn't parse. So that one error doesn't
// hide the others, the statement each spoils is blanked out, in a copy that keeps every position, and the rest is read
// again: the syntax errors of up to MAX_SYNTAX_ERRORS statements are told at once, with what the rules find in the
// code around them. Of the parts an error may spoil, the one is left out without which the parser gets furthest.
export const lintMessages = (state, jsx = false, now = () => performance.now()) => {
    const doc = state.doc;
    let code = doc.toString();
    const started = now();
    let { sourceType, messages } = firstRead(code, doc, jsx);
    // Nearly always: nothing to read past.
    if (!messages.some((message) => message.fatal)) return messages;
    const tree = ensureSyntaxTree(state, doc.length, 200) ?? syntaxTree(state);
    const marks = treeErrors(tree);
    const syntaxErrors = [];
    const gone = [];
    for (;;) {
        const fatal = messages.find((message) => message.fatal);
        if (!fatal) return [...syntaxErrors, ...messages];
        syntaxErrors.push(fatal);
        if (syntaxErrors.length >= MAX_SYNTAX_ERRORS || now() - started > BUDGET_MILLIS) return syntaxErrors;
        const pos = positionOf(doc, fatal.line, fatal.column);
        let best = null;
        for (const part of spoiledBy(state, tree, marks, gone, pos)) {
            const without = blank(code, part.from, part.to);
            if (without === code) continue;
            const read = verify(without, sourceType, jsx);
            const reached = fatalAt(read, doc);
            if (!best || reached > best.reached) best = { code: without, messages: read, reached, part };
            // Nothing reads further than to the end; and in a large file the others aren't worth the wait.
            if (reached === Infinity || now() - started > BUDGET_MILLIS) break;
        }
        // Nothing left to leave out there, or leaving it out gets no further: the error is of what remains.
        if (!best || best.reached <= pos) return syntaxErrors;
        code = best.code;
        messages = best.messages;
        gone.push(best.part);
    }
};

const lintJavaScript = (jsx, sessionId) => async (view) => {
    // The app's own linter, where it has one for the project's language: it knows what the engine runs.
    if (!jsx && nativeLintAvailable()) {
        const found = await nativeLint(view, sessionId?.());
        if (found) return found;
    }
    if (!linter) await loadLinter();
    if (!linter) return [];
    const doc = view.state.doc;
    const diagnostics = [];
    for (const message of lintMessages(view.state, jsx)) {
        const from = positionOf(doc, message.line, message.column);
        const to = message.endLine ? Math.max(positionOf(doc, message.endLine, message.endColumn), from) : from;
        diagnostics.push({
            from,
            to,
            severity: message.fatal || message.severity === 2 ? "error" : "warning",
            message: message.fatal ? message.message.replace(/^Parsing error: /, "") : message.message,
        });
    }
    diagnostics.sort((a, b) => a.from - b.from);
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

// `sessionId` gives the tab the editor shows, for the app's linter to tell tabs apart.
export const lintSource = (mode, sessionId) => {
    switch (mode) {
        case "javascript":
            return lintJavaScript(false, sessionId);
        case "jsx":
            return lintJavaScript(true, sessionId);
        case "json":
            return lintJson;
        default:
            return () => [];
    }
};
