import { Compartment, EditorSelection, EditorState } from "@codemirror/state";
import {
    EditorView, highlightActiveLine, highlightActiveLineGutter, highlightSpecialChars,
    keymap, lineNumbers,
} from "@codemirror/view";
import {
    defaultKeymap, history, historyKeymap, indentLess, indentMore, indentWithTab, redo, undo,
    cursorCharLeft, cursorCharRight,
} from "@codemirror/commands";
import { bracketMatching, foldGutter, foldKeymap, indentOnInput, indentUnit } from "@codemirror/language";
import {
    autocompletion, closeBrackets, closeBracketsKeymap, completeAnyWord, completionKeymap, insertBracket,
    snippetCompletion,
} from "@codemirror/autocomplete";
import { gotoLine, highlightSelectionMatches, openSearchPanel, search, searchKeymap } from "@codemirror/search";
import { forEachDiagnostic, lintGutter, linter, setDiagnostics, setDiagnosticsEffect } from "@codemirror/lint";
import { languageFor } from "./languages.js";
import { themeFor } from "./themes.js";
import { lintSource } from "./lint.js";
import { beautify } from "./beautify.js";
import { tapToShowDiagnostics } from "./diagnostics.js";
import { keepFirstTouchSelection } from "./touch.js";

const sessions = new Map();
const settings = { theme: "tomorrow_night_eighties", fontSize: 15, wrap: false };
const themeCompartment = new Compartment();
const fontCompartment = new Compartment();
const wrapCompartment = new Compartment();
const languageCompartment = new Compartment();

let currentId = null;
let apis = new Map();
const snippets = new Map();

const fontTheme = (size) => EditorView.theme({
    ".cm-scroller": { fontFamily: "\"JetBrains Mono\", monospace", fontSize: `${size}px` },
});

const apiCompletions = (context) => {
    const member = context.matchBefore(/([A-Za-z_$][\w$]*)\.[\w$]*$/);
    if (member) {
        const [owner] = member.text.split(".");
        const api = apis.get(owner);
        if (!api) return null;
        return {
            from: member.from + owner.length + 1,
            options: api.members.map((m) => ({ label: m.name, detail: `${m.caption.slice(m.name.length)}: ${m.returns}`, type: m.caption.includes("(") ? "function" : "property", boost: 10 })),
            validFor: /^[\w$]*$/,
        };
    }
    const word = context.matchBefore(/[A-Za-z_$][\w$]*/);
    if (!word && !context.explicit) return null;
    return {
        from: word ? word.from : context.pos,
        options: [...apis.keys()].map((name) => ({ label: name, type: "class", detail: "api", boost: 5 })),
        validFor: /^[\w$]*$/,
    };
};

const snippetCompletions = (mode) => (context) => {
    const word = context.matchBefore(/[A-Za-z_$][\w$]*/);
    const list = snippets.get(mode);
    if (!list || (!word && !context.explicit)) return null;
    return { from: word ? word.from : context.pos, options: list, validFor: /^[\w$]*$/ };
};

const documentWords = (context) => {
    const result = completeAnyWord(context);
    return result && { ...result, options: result.options.map(({ type, ...option }) => option) };
};

const reportDirty = (id) => {
    const session = sessions.get(id);
    if (!session) return;
    const doc = id === currentId ? view.state.doc : session.state.doc;
    const dirty = session.saved === null || !doc.eq(session.saved);
    if (session.reported === dirty) return;
    session.reported = dirty;
    WebviewCallback.onDirtyChanged(id, dirty);
};

let annotationTimer = null;
const scheduleAnnotations = () => {
    clearTimeout(annotationTimer);
    annotationTimer = setTimeout(() => window.requestAnnotations(), 300);
};

let dirtyTimer = null;
const scheduleDirty = () => {
    clearTimeout(dirtyTimer);
    const id = currentId;
    dirtyTimer = setTimeout(() => reportDirty(id), 150);
};

const createState = (code, mode) => {
    const completionSources = [
        { autocomplete: apiCompletions },
        { autocomplete: snippetCompletions(mode) },
        { autocomplete: documentWords },
    ];
    return EditorState.create({
        doc: code,
        extensions: [
            lineNumbers(),
            foldGutter(),
            lintGutter(),
            tapToShowDiagnostics,
            keepFirstTouchSelection,
            highlightActiveLineGutter(),
            highlightSpecialChars(),
            history(),
            indentOnInput(),
            bracketMatching(),
            closeBrackets(),
            highlightActiveLine(),
            highlightSelectionMatches(),
            search({ top: true }),
            autocompletion({ icons: false }),
            EditorState.languageData.of(() => completionSources),
            indentUnit.of("    "),
            EditorState.tabSize.of(4),
            EditorView.contentAttributes.of({ autocorrect: "off", autocapitalize: "off", spellcheck: "false" }),
            keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, ...foldKeymap, ...completionKeymap, indentWithTab]),
            languageCompartment.of(languageFor(mode)),
            linter(lintSource(mode), { delay: 500 }),
            themeCompartment.of(themeFor(settings.theme)),
            fontCompartment.of(fontTheme(settings.fontSize)),
            wrapCompartment.of(settings.wrap ? EditorView.lineWrapping : []),
            EditorView.updateListener.of((update) => {
                if (update.docChanged) scheduleDirty();
                if (update.transactions.some((tr) => tr.effects.some((e) => e.is(setDiagnosticsEffect))))
                    scheduleAnnotations();
            }),
        ],
    });
};

const view = new EditorView({
    state: createState("", "plain_text"),
    parent: document.getElementById("editor"),
});

const applySettings = () => view.dispatch({
    effects: [
        themeCompartment.reconfigure(themeFor(settings.theme)),
        fontCompartment.reconfigure(fontTheme(settings.fontSize)),
        wrapCompartment.reconfigure(settings.wrap ? EditorView.lineWrapping : []),
    ],
});

const syncBackground = () => requestAnimationFrame(() => {
    document.body.style.background = getComputedStyle(view.dom).backgroundColor;
});

const storeCurrent = () => {
    const session = sessions.get(currentId);
    if (!session) return;
    session.state = view.state;
    session.scrollTop = view.scrollDOM.scrollTop;
};

window.setSession = (id, mode, code) => {
    if (id === currentId && sessions.has(id)) return;
    storeCurrent();
    let session = sessions.get(id);
    if (!session) {
        const state = createState(code ?? "", mode);
        session = { state, mode, saved: state.doc, reported: false, scrollTop: 0 };
        sessions.set(id, session);
    }
    currentId = id;
    view.setState(session.state);
    applySettings();
    view.scrollDOM.scrollTop = session.scrollTop;
    scheduleAnnotations();
};

window.closeSession = (id) => {
    sessions.delete(id);
    if (id === currentId) currentId = null;
};

window.collectDirty = (ids) => {
    storeCurrent();
    const result = {};
    for (const [id, session] of sessions) {
        if (ids && !ids.includes(id)) continue;
        const doc = session.state.doc;
        if (session.saved !== null && doc.eq(session.saved)) continue;
        result[id] = doc.toString();
        session.saved = doc;
        session.reported = false;
    }
    return result;
};

window.markDirty = (id) => {
    const session = sessions.get(id);
    if (!session) return;
    session.saved = null;
    reportDirty(id);
};

window.requestAnnotations = () => {
    const list = [];
    forEachDiagnostic(view.state, (d, from) => {
        const line = view.state.doc.lineAt(from);
        list.push({ row: line.number - 1, column: from - line.from, text: d.message, type: d.severity, raw: d.message });
    });
    WebviewCallback.onAnnotationUpdated(JSON.stringify(list));
};

window.beautifyCode = () => {
    const mode = sessions.get(currentId)?.mode;
    const code = view.state.doc.toString();
    const formatted = beautify(code, mode);
    if (formatted == null || formatted === code) return;
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: formatted } });
};

window.undo = () => undo(view);

window.redo = () => redo(view);

const COMMANDS = {
    find: (v) => openSearchPanel(v),
    replace: (v) => openSearchPanel(v),
    gotoline: (v) => gotoLine(v),
    indent: (v) => v.state.selection.ranges.some((r) => !r.empty)
        ? indentMore(v)
        : v.dispatch(v.state.update(v.state.replaceSelection(v.state.facet(indentUnit)), { scrollIntoView: true, userEvent: "input" })),
    blockoutdent: (v) => indentLess(v),
};

window.execCommand = (name) => {
    COMMANDS[name]?.(view);
};

window.moveCursor = (dx) => {
    const move = dx < 0 ? cursorCharLeft : cursorCharRight;
    for (let i = 0; i < Math.abs(dx); i++) move(view);
    view.focus();
};

window.setFontSize = (size) => {
    settings.fontSize = size;
    applySettings();
};

window.setWordWrap = (wrap) => {
    settings.wrap = wrap;
    applySettings();
};

window.setTheme = (name) => {
    settings.theme = name;
    applySettings();
    syncBackground();
};

window.setLanguage = (mode) => view.dispatch({ effects: languageCompartment.reconfigure(languageFor(mode)) });

window.setBottomInset = (px) => {
    document.getElementById("editor").style.height = `calc(100% - ${px}px)`;
    view.requestMeasure();
};

window.insertText = (text) => {
    const bracket = text.length === 1 ? insertBracket(view.state, text) : null;
    view.dispatch(bracket ?? view.state.update(view.state.replaceSelection(text), { scrollIntoView: true, userEvent: "input" }));
    view.focus();
};

const moveTo = (row, column) => {
    const line = view.state.doc.line(Math.min(Math.max(row, 1), view.state.doc.lines));
    const pos = Math.min(line.from + Math.max(column, 0), line.to);
    view.dispatch({ selection: EditorSelection.cursor(pos), effects: EditorView.scrollIntoView(pos, { y: "center" }) });
    return pos;
};

window._gotoLine = (row, column) => moveTo(row, column);

window.markError = (row, column, text) => {
    const pos = moveTo(row + 1, column);
    const line = view.state.doc.lineAt(pos);
    view.dispatch(setDiagnostics(view.state, [{ from: pos, to: Math.max(pos, line.to), severity: "error", message: text }]));
};

window.setApis = (list) => {
    apis = new Map(list.map((api) => [api.name, api]));
};

window.setSnippets = (mode, list) => {
    snippets.set(mode, list.map((s) => snippetCompletion(
        s.content.replace(/\$\{0\}/g, "${}").replace(/\\\$/g, "$"),
        { label: s.tabTrigger ?? s.name, detail: "snippet", type: "function" },
    )));
};

window.addEventListener("resize", () => {
    if (!view.hasFocus) return;
    view.dispatch({ effects: EditorView.scrollIntoView(view.state.selection.main.head, { y: "nearest", yMargin: 24 }) });
});

syncBackground();
WebviewCallback.onLoadComplete();
document.getElementById("placeholder").style.display = "none";
