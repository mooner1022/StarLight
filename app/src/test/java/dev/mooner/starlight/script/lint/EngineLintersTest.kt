package dev.mooner.starlight.script.lint

import dev.mooner.neonjs.lint.NeonLint
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.TranslationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ImporterTopLevel

class EngineLintersTest {

    /** `L<line> <e|w> <code>` of each finding, in the order of the source. */
    private fun brief(source: String, found: List<ScriptLint.Diagnostic>): List<String> =
        found.map { "L${RhinoSyntax.lineOf(source, it.start)} ${if (it.severity == ScriptLint.Severity.ERROR) "e" else "w"} ${it.code}" }

    private fun neon(source: String, apis: List<String> = listOf("Chat", "Log")) =
        brief(source, EngineLinters.lintNeon(source, apis))

    private val rhinoNames: Set<String> by lazy {
        val cx = ContextFactory().enterContext()
        try {
            EngineLinters.namesOf(cx.initStandardObjects(ImporterTopLevel(cx)))
        } finally {
            Context.exit()
        }
    }

    private fun rhino(source: String, globals: Collection<String>? = rhinoNames + listOf("Api", "Utils")) =
        brief(source, EngineLinters.lintRhino(source, globals, Context.VERSION_ES6))

    // ---- NeonJS -------------------------------------------------------------------------------------------------

    @Test
    fun `a NeonJS module with new syntax and the project's APIs has no findings`() {
        assertEquals(emptyList<String>(), neon("""
            import { helper } from "./lib/helper.js";
            class Counter { #count = 0; static limit = 3600_000; next() { return ++this.#count; } }
            let value; value ??= await fetch("https://example.com");
            export async function onMessage(event) {
                Log.i(helper(value));
                setTimeout(() => toast("hi"), 100);
                console.log(Java.type("java.lang.String"), new Counter().next());
                Chat.findRoom("a")?.send(event.message);
            }
        """.trimIndent()))
    }

    @Test
    fun `NeonJS findings come all at once, syntax errors with mistakes and names nothing defines`() {
        val source = """
            export function onMessage(event) {
                let a = 0;
                const b = 1;
                b = 2;
                const a = 1;
                fizz;
                new Test().#foo();
                let c = ;
                if (event.x = 1) {}
                Store.set("k", 1);
            }
            class Test { #foo() {} }
        """.trimIndent()
        assertEquals(listOf(
            "L4 e const-assign",
            "L5 e syntax/redeclared",
            "L6 w undefined-name",
            "L7 e syntax/private-name-undeclared",
            "L8 e syntax/missing-expression",
            "L9 w cond-assign",
            // Not among this project's APIs.
            "L10 w undefined-name",
        ), neon(source))
    }

    @Test
    fun `a finding is at the editor's offsets, whatever comes before it`() {
        val source = "const 인사 = \"안녕 😀🎉\";\nconst k = 1; k = 2;\n"
        // (The module reads neither name: each is told as unused too.)
        val found = EngineLinters.lintNeon(source, emptyList()).single { it.code == "const-assign" }
        assertEquals("k", source.substring(found.start, found.end))
        assertEquals(source.lastIndexOf("k = 2"), found.start)
    }

    @Test
    fun `NeonJS mistakes that run are told, an unused name too`() {
        val source = """
            import { helper } from "./lib/helper.js";
            const PATTERN = "\d+";
            const ids = [1, , 2];
            const big = 9007199254740993;
            function onMessage(event) {
                let count = 0;
                count = event.message.length;
                if ("debug") Log.i(PATTERN + ids + big);
                return typeof event === "object" ? 1 : 2;
            }
        """.trimIndent()
        assertEquals(listOf(
            "L1 w unused-variable",
            "L2 w useless-escape",
            "L3 w sparse-array",
            "L4 w precision-loss",
            // Not exported: the app never calls it.
            "L5 w unused-variable",
            "L6 w unused-variable",
            "L8 w constant-condition",
        ), neon(source))
    }

    @Test
    fun `a NeonJS module that only looks unused has no findings`() {
        assertEquals(emptyList<String>(), neon("""
            const rooms = new Map();
            let started = false;
            function remember(room, _reserved) { rooms.set(room.name, room); }
            const { secret, ...shown } = { secret: 1, name: "a" };
            export function onStartCompile() { started = true; }
            export function onMessage(event, unusedParameter) {
                try { remember(event.room); } catch (e) { }
                while (true) { if (started) break; }
                const tried = 0.1 + 0.2, wide = 5123000000000000000000000000000;
                Log.i("\\d \n \' " + String(shown) + tried + wide + /\d+\./.source);
            }
        """.trimIndent()))
    }

    // ---- Rhino --------------------------------------------------------------------------------------------------

    @Test
    fun `a Rhino script with what Rhino reads has no findings`() {
        assertEquals(emptyList<String>(), rhino("""
            var cache = {};
            const PREFIX = "!";
            function response(room, msg, sender, isGroupChat, replier) {
                with (Math) { var n = floor(random() * 6); }
                for each (var key in Object.keys(cache)) { Api.replyRoom(room, key); }
                try { java.lang.Thread.sleep(10); } catch (e if e instanceof TypeError) { }
                var f = function (x) x * 2;
                let [a, b] = [1, 2];
                replier.reply(`${'$'}{msg} ${'$'}{n} ${'$'}{f(a + b)}` + Utils.getWebText("https://example.com") + PREFIX);
                importClass(java.io.File);
                setTimeout(() => toast("hi"), 10);
            }
        """.trimIndent(), rhinoNames + listOf("Api", "Utils", "toast", "setTimeout")))
    }

    @Test
    fun `syntax Rhino can't read is told as such, each once`() {
        val source = """
            class A { }
            function f(a = 1) { return a; }
            var o = {};
            var v = o?.x ?? 1;
            async function g() { }
            var s = [...[1, 2]];
        """.trimIndent()
        assertEquals(listOf(
            "L1 e rhino/unsupported", "L2 e rhino/unsupported", "L4 e rhino/unsupported",
            "L5 e rhino/unsupported", "L6 e rhino/unsupported",
        ), rhino(source))
        val found = EngineLinters.lintRhino(source, null, Context.VERSION_ES6)
        assertEquals("class", source.substring(found[0].start, found[0].end))
    }

    @Test
    fun `a Rhino script's syntax errors and mistakes come together, the errors as the linter names them`() {
        val source = """
            function response(room, msg) {
                var a = ;
                const k = 1;
                k = 2;
                if (a = 1) { }
                fizz(room);
                var b = (1;
            }
            class Helper { }
        """.trimIndent()
        assertEquals(listOf(
            "L2 e syntax/missing-expression",
            // Rhino passes over it outside strict code: a warning there.
            "L4 w const-assign",
            "L5 w cond-assign",
            "L6 w undefined-name",
            "L7 e syntax/missing-paren",
            // Past the linter's errors, what only Rhino refuses is still told as newer than Rhino.
            "L9 e rhino/unsupported",
        ), rhino(source))
    }

    @Test
    fun `a Rhino script's mistakes that run are told, not its globals`() {
        val source = """
            var cache = [1, , 2];
            var unusedGlobal = 1;
            function helper() { }
            function response(room, msg, sender, isGroupChat, replier) {
                var pattern = "\d+";
                var left = 1;
                var big = 0.1234567890123456789;
                if (!"") replier.reply(pattern + big);
            }
        """.trimIndent()
        assertEquals(listOf(
            "L1 w sparse-array",
            "L5 w useless-escape",
            "L6 w unused-variable",
            "L7 w precision-loss",
            "L8 w constant-condition",
        ), rhino(source))
    }

    @Test
    fun `what only Rhino reads brings none of the linter's guesses after it`() {
        val source = """
            function response(room, msg, sender, isGroupChat, replier) {
                var seen = 0;
                for each (var key in [1, 2]) { replier.reply(key); }
                var f = function (x) x * 2;
                try { f(1); } catch (e if e instanceof TypeError) { }
                var list = [1, 2,];
                if (msg) replier.reply(list);
            }
        """.trimIndent()
        assertEquals(emptyList<String>(), rhino(source))
    }

    @Test
    fun `Rhino's positions are the editor's after an emoji`() {
        val source = "var s = \"😀🎉 안녕\";\nvar t = \"😀\"; class B { }\n"
        val found = EngineLinters.lintRhino(source, null, Context.VERSION_ES6).first()
        assertEquals("rhino/unsupported", found.code)
        assertEquals("class", source.substring(found.start, found.end))
    }

    @Test
    fun `without the names a script is given, names aren't checked`() {
        assertEquals(emptyList<String>(), rhino("function response() { fizz(); }", globals = null))
    }

    @Test
    fun `Rhino's global object gives Java's packages and the standard objects`() {
        for (name in listOf("Packages", "java", "importClass", "JSON", "Math", "Object", "parseInt", "JavaAdapter"))
            assertTrue(name, name in rhinoNames)
        assertFalse("fetch" in rhinoNames)
    }

    // ---- Messages -----------------------------------------------------------------------------------------------

    @Test
    fun `every code the linter has is told in Korean`() {
        val missing = NeonLint.rules.map { it.code }.filter { LintMessages.korean(it, List(3) { "x" }, "english") == null }
        assertEquals(emptyList<String>(), missing)
    }

    /** What the linter really says of [source], in Korean; of names nothing reads only when [unused]. */
    private fun koreanOf(source: String, unused: Boolean = false): List<String> =
        NeonLint.lint(source, dev.mooner.neonjs.lint.LintOptions()).diagnostics
            .filter { unused || it.code != "unused-variable" }
            .map { LintMessages.korean(it.code, it.args, it.message) ?: "(none) ${'$'}{it.message}" }

    @Test
    fun `a code with several meanings is told as the one it is`() {
        // A private name outside its class: the class does declare it.
        assertEquals(listOf("'#foo'은(는) 그것을 선언한 클래스 안에서만 쓸 수 있어요."),
            koreanOf("class Test { #foo() {} }\nnew Test().#foo();"))
        assertEquals(listOf("'#bar'은(는) 그것을 선언한 클래스 안에서만 쓸 수 있어요."),
            koreanOf("class Test { m() { return this.#bar; } }"))
        assertEquals(listOf("'k'은(는) 상수라서 다시 대입할 수 없어요."), koreanOf("const k = 1; k = 2;"))
        assertEquals(listOf("'x'은(는) import한 이름이라 대입할 수 없어요."), koreanOf("import { x } from './a.js'; x = 2;"))
        assertEquals(listOf("클래스 이름 'A'은(는) 그 클래스 안에서 대입할 수 없어요."), koreanOf("class A { m() { A = 1; } }"))
        assertEquals(listOf("함수 'f'에 값을 대입하고 있어요."), koreanOf("function f() {} f = 1;"))
        assertEquals(listOf("함수 안에서 그 함수의 이름 'g'에 대입해도 바뀌지 않아요."), koreanOf("(function g() { g = 1; });"))
        assertEquals(listOf("typeof는 'strnig'을(를) 돌려주지 않아요."), koreanOf("let a; if (typeof a === 'strnig') {}"))
        assertEquals(listOf("typeof의 결과는 문자열이에요. undefined가 아니라 \"undefined\"와 비교하세요."),
            koreanOf("let a; if (typeof a === undefined) {}"))
        assertEquals(listOf("숫자 표기가 잘못됐어요. (Numeric separators are not allowed at the end of numeric literals)"),
            koreanOf("let n = 1_;"))
        assertEquals(listOf("'a'은(는) 선언만 하고 쓰지 않아요."), koreanOf("function f() { let a; } f();", unused = true))
        assertEquals(listOf("'a'은(는) 값을 넣기만 하고 읽지 않아요."), koreanOf("function f() { let a; a = 1; } f();", unused = true))
        assertEquals(listOf("이 조건은 항상 참(true)이에요."), koreanOf("if ([]) {}"))
        assertEquals(listOf("이 조건은 항상 거짓(false)이에요."), koreanOf("if (null) {}"))
        assertEquals(listOf("이 숫자는 적은 그대로 저장되지 않아요. 실제 값: 9007199254740992"), koreanOf("f(9007199254740993);"))
        assertEquals(listOf("배열에 빈 칸이 있어요. ','가 하나 더 들어갔거나 값이 빠졌어요."), koreanOf("f([1, , 2]);"))
        assertEquals(listOf("문자열에서 '\\d'의 \\는 사라지고 'd'만 남아요. 정규식에 쓸 것이라면 \\를 두 번 적으세요: '\\\\d'"),
            koreanOf("f(\"\\d\");"))
    }

    @Test
    fun `Korean messages take the particle that fits the name`() {
        fun ko(code: String, vararg args: String) = LintMessages.korean(code, args.toList(), "english")
        assertEquals("'count'은(는) 상수라서 다시 대입할 수 없어요.", ko("const-assign", "count"))
        assertEquals("'값'은 상수라서 다시 대입할 수 없어요.", ko("const-assign", "값"))
        assertEquals("'사과'를 선언하기 전에 사용했어요.", ko("use-before-define", "사과"))
        assertEquals("'fizz'이(가) 정의되지 않았어요.", ko("undefined-name", "fizz"))
        assertEquals("'값2'가 정의되지 않았어요.", ko("undefined-name", "값2"))
        assertEquals("')'가 와야 하는데 '{'가 왔어요.", ko("syntax/missing-token", ")", "'{'"))
        assertEquals("','가 와야 하는데 문자열이 왔어요.", ko("syntax/missing-token", ",", "string"))
        assertEquals("여기에 올 수 없어요: ';'", ko("syntax/unexpected-token", "token ';'"))
        assertEquals("여기에 올 수 없어요: 이름 'bar'", ko("syntax/unexpected-token", "identifier 'bar'"))
        assertEquals("Rhino 엔진이 읽지 못하는 문법이에요. (english)", ko(LintMessages.RHINO_UNSUPPORTED))
        assertEquals("the linter's", LintMessages.korean("a-code-of-later", emptyList(), "the linter's") ?: "the linter's")
        assertEquals("'k'은(는) 상수라서 다시 대입할 수 없어요.",
            TranslationManager.translateTo(Locale.KOREAN) {
                Locale.ENGLISH { "x" }
                Locale.KOREAN { LintMessages.korean("const-assign", listOf("k"), "x")!! }
            })
    }
}
