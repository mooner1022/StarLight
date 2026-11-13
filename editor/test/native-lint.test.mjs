// Run with `npm test`: the page's side of the app's own linter, with the bridge played by the test.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { EditorState } from "@codemirror/state";
import { javascript } from "@codemirror/lang-javascript";

new Function(readFileSync(new URL("../node_modules/eslint-linter-browserify/linter.min.js", import.meta.url), "utf8")).call(globalThis);
globalThis.document = { createElement: () => ({}), head: { appendChild: (script) => script.onload() } };
globalThis.window = globalThis;

const sent = [];
const app = { available: true, lintsNatively: () => app.available, requestLint: (id, session, code) => sent.push({ id, session, code }) };
globalThis.WebviewCallback = app;

const { lintSource } = await import("../src/lint.js");
const { dropNativeLint } = await import("../src/native-lint.js");

const stateOf = (code) => EditorState.create({ doc: code, extensions: [javascript()] });
const check = (code, session = "1", mode = "javascript") => {
    const state = stateOf(code);
    return { state, result: lintSource(mode, () => session)({ state }) };
};
const brief = (diagnostics) => diagnostics.map((d) => `${d.from}-${d.to} ${d.severity} ${d.message}`);

test("the app's findings are shown, with the text as it was sent", async () => {
    sent.length = 0;
    const code = "let 가 = '😀';\nconst k = 1; k = 2;";
    const { result } = check(code, "7");
    assert.deepEqual(sent.map((s) => [s.session, s.code]), [["7", code]]);
    window.onNativeLint(sent[0].id, [
        { start: 27, end: 28, severity: "error", message: "'k' is constant.", code: "const-assign" },
        { start: 4, end: 5, severity: "warning", message: "이름" },
    ]);
    assert.deepEqual(brief(await result), ["27-28 error 'k' is constant.", "4-5 warning 이름"]);
});

test("findings outside the text are brought inside it", async () => {
    sent.length = 0;
    const { result } = check("abc");
    window.onNativeLint(sent[0].id, [
        { start: -5, end: 99, severity: "error", message: "a" },
        { start: 2, end: 1, severity: "weird", message: "b" },
        { start: "x", severity: "error" },
    ]);
    assert.deepEqual(brief(await result), ["0-3 error a", "2-2 warning b", "0-0 error "]);
});

test("the page checks the text itself when the app has no linter, answers null, or throws", async () => {
    const code = "const k = 1; k = 2;";
    sent.length = 0;
    app.available = false;
    assert.deepEqual(brief(await check(code).result), ["13-14 error 'k' is constant."]);
    assert.equal(sent.length, 0);

    app.available = true;
    const { result } = check(code);
    window.onNativeLint(sent[0].id, null);
    assert.deepEqual(brief(await result), ["13-14 error 'k' is constant."]);

    const request = app.requestLint;
    app.requestLint = () => { throw new Error("gone"); };
    assert.deepEqual(brief(await check(code).result), ["13-14 error 'k' is constant."]);
    app.lintsNatively = () => { throw new Error("gone"); };
    assert.deepEqual(brief(await check(code).result), ["13-14 error 'k' is constant."]);
    app.requestLint = request;
    app.lintsNatively = () => app.available;
});

test("an app that never answers is waited for 4 seconds, then the page checks", async (t) => {
    t.mock.timers.enable({ apis: ["setTimeout"] });
    sent.length = 0;
    const { result } = check("const k = 1; k = 2;");
    t.mock.timers.tick(3999);
    let done = false;
    result.then(() => { done = true; });
    await Promise.resolve();
    assert.equal(done, false);
    t.mock.timers.tick(1);
    assert.deepEqual(brief(await result), ["13-14 error 'k' is constant."]);
    // An answer that comes after all is passed over.
    window.onNativeLint(sent[0].id, [{ start: 0, end: 1, severity: "error", message: "late" }]);
});

test("a tab's earlier request ends when it asks again or closes, another tab's doesn't", async () => {
    sent.length = 0;
    const first = check("let a = 1;", "1");
    const other = check("let b = 1;", "2");
    const second = check("let a = 12;", "1");
    assert.deepEqual(await first.result, []);
    window.onNativeLint(sent[0].id, [{ start: 0, end: 1, severity: "error", message: "old" }]);
    window.onNativeLint(sent[2].id, [{ start: 0, end: 3, severity: "error", message: "new" }]);
    assert.deepEqual(brief(await second.result), ["0-3 error new"]);
    dropNativeLint("2");
    assert.deepEqual(await other.result, []);
});

test("a jsx file is never sent to the app", async () => {
    sent.length = 0;
    assert.deepEqual(brief(await check("const a = <b/>;\nconst k = 1; k = 2;", "1", "jsx").result), ["29-30 error 'k' is constant."]);
    assert.equal(sent.length, 0);
});
