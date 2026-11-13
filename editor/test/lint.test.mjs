// Run with `npm test`: checks src/lint.js against the ESLint build the app ships, without a browser.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { EditorState } from "@codemirror/state";
import { javascript } from "@codemirror/lang-javascript";

new Function(readFileSync(new URL("../node_modules/eslint-linter-browserify/linter.min.js", import.meta.url), "utf8")).call(globalThis);
// The linter is loaded by a script element: here it is there already.
globalThis.document = { createElement: () => ({}), head: { appendChild: (script) => script.onload() } };
const { lintSource } = await import("../src/lint.js");

/** What the editor marks in `code`, as `L<line> <e|w> <message>` in the order of the lines. */
const marks = async (code, mode = "javascript") => {
    const state = EditorState.create({ doc: code, extensions: [javascript({ jsx: mode === "jsx" })] });
    const diagnostics = await lintSource(mode)({ state });
    return diagnostics.map((d) => `L${state.doc.lineAt(d.from).number} ${d.severity[0]} ${d.message}`);
};

const CONSTANT = "L%d e 'k' is constant.";
const constant = (line) => CONSTANT.replace("%d", line);

test("syntax newer than ES2020 isn't marked", async () => {
    assert.deepEqual(await marks(`
        class A { #x = 1; static y = 3600_000; static { A.z = 1; } #m() { return this.#x; } has(o) { return #x in o; } }
        let v; v ??= await f(); v ||= 1n;
        { using r = open(); }
        export function onMessage(event) { event.room.send(v?.a ?? ""); }
    `), []);
});

test("a legacy script that is no module isn't marked", async () => {
    assert.deepEqual(await marks("function response(room, msg) { with (Math) { var package = floor(1.5); } replier.reply(msg); }"), []);
});

test("mistakes that are valid code are told", async () => {
    assert.deepEqual(await marks([
        "const k = 1; k = 2;",
        "let v; if (v = 1) {}",
        "function f(x) { return 1; f(); }",
        "function g(x) { switch (x) { case 1: f(); case 2: f(); } }",
        "const o = { a: 1, a: 2 };",
        "if (v === NaN) {}",
        "if (typeof v === 'strnig') {}",
        "debugger;",
        "late = 1; let late;",
    ].join("\n")), [
        constant(1),
        "L2 w Expected a conditional expression and instead saw an assignment.",
        "L3 w Unreachable code.",
        "L4 w Expected a 'break' statement before 'case'.",
        "L5 w Duplicate key 'a'.",
        "L6 w Use the isNaN function to compare with NaN.",
        "L7 w Invalid typeof comparison value.",
        "L8 w Unexpected 'debugger' statement.",
        "L9 e 'late' was used before it was defined.",
    ]);
});

test("style, and names the script doesn't define, aren't told", async () => {
    assert.deepEqual(await marks("let a = 1\nconst o = {}; o['a'] = new Date;;\nLog.i(fizz);\n"), []);
});

test("one syntax error doesn't hide the others, nor what the rules find", async () => {
    assert.deepEqual(await marks([
        "export function onMessage(event) {",
        "    let a = 0;",
        "    const k = 1;",
        "    k = 2;",
        "    const a = 1;",
        "    new Test().#foo();",
        "}",
        "class Test { #foo() {} }",
    ].join("\n")), [
        constant(4),
        "L5 e Identifier 'a' has already been declared",
        "L6 e Private field '#foo' must be declared in an enclosing class",
    ]);
});

test("each broken statement is told, and the code after them is still checked", async () => {
    const cases = {
        "function f() {\n  let a = ;\n  let b = 1;\n  let c = ;\n  const k = 1; k = 2;\n}\n":
            ["L2 e Unexpected token ;", "L4 e Unexpected token ;", constant(5)],
        "function f() {\n  if (a {\n    g();\n  }\n  if (b {\n    g();\n  }\n}\nconst k = 1; k = 2;\n":
            ["L2 e Unexpected token {", "L5 e Unexpected token {", constant(9)],
        "let a = 1;\n) ;\nlet b = 2 +;\nconst k = 1; k = 2;\n":
            ["L2 e Unexpected token )", "L3 e Unexpected token ;", constant(4)],
        "let a = \"abc;\nlet b = 1;\nconst k = 1; k = 2;\n":
            ["L1 e Unterminated string constant", constant(3)],
        // Told at what follows the open parameter list: the member with the list is the one left out.
        "class A {\n  foo( { }\n  bar() { const k = 1; k = 2; }\n  #x = ;\n}\n":
            ["L3 e Unexpected token bar", constant(3), "L4 e Unexpected token ;"],
        "function f(a, {\n  g();\n}\nfunction h() { const k = 1; k = 2; }\n":
            ["L2 e Unexpected token (", constant(4)],
        // Only the line is left out, not the object around it.
        "const o = {\n  a: 1\n  b: 2,\n};\nconst k = 1; k = 2;\n":
            ["L3 e Unexpected token b", constant(5)],
        "function f() {\n  if (a) { g(); }\n  else if { h(); }\n  const k = 1; k = 2;\n}\n":
            ["L3 e Unexpected token {", constant(4)],
        "export function a() {}\nexport function a() {}\nconst k = 1; k = 2;\n":
            ["L2 e Identifier 'a' has already been declared", constant(3)],
        "function response(room, msg) {\n  with (Math) { var x = ; }\n  const k = 1; k = 2;\n}\n":
            ["L2 e Unexpected token ;", constant(3)],
    };
    for (const [code, expected] of Object.entries(cases))
        assert.deepEqual(await marks(code), expected, code);
});

test("code that is all errors ends, with at most 10 of them", async () => {
    assert.ok((await marks("}}}} ((( ]]] === ;;; +++\n@@@ ### $$$\n")).length >= 1);
    const many = await marks("let z = ;\n".repeat(30));
    assert.ok(many.length >= 1 && many.length <= 10, String(many.length));
});

test("JSX is read in a jsx file", async () => {
    assert.deepEqual(await marks("const a = <div>{1}</div>;\nconst k = 1; k = 2;\n", "jsx"), [constant(2)]);
});
