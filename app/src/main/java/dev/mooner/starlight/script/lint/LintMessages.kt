/*
 * LintMessages.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.script.lint

import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.script.format.Josa

/**
 * The linter's findings in Korean, by their codes (`NeonLint.rules`): the linter's own text is English. A code this
 * doesn't know, as one a later version adds, keeps the linter's text.
 */
internal object LintMessages {

    /** [english] in the app's language. */
    fun text(code: String, args: List<String>, english: String): String =
        translate {
            Locale.ENGLISH { english }
            Locale.KOREAN  { korean(code, args, english) ?: english }
        }

    /** A name in quotes with the particle that fits it: `'사과'를`, and `'count'을(를)` for one in Latin letters. */
    private fun named(name: String, particle: String): String =
        withParticle("'$name'", particle)

    /**
     * [text] with [particle], as `을/를`. How a name in Latin letters is read can't be told from its spelling (`k` is
     * 케이, `count` 카운트), so such a name takes both forms, as `을(를)`.
     */
    private fun withParticle(text: String, particle: String): String {
        val last = text.lastOrNull { it.isLetterOrDigit() }
        if (last != null && last in 'A'..'z' && last.isLetter()) {
            val (afterConsonant, afterVowel) = particle.split('/')
            return "$text$afterConsonant($afterVowel)"
        }
        return Josa.attach(text, particle)
    }

    /** A token as the parser names it (`token ';'`, `identifier 'x'`, `'{'`, `string`), in Korean. */
    private fun token(described: String): String = when {
        described == "string" -> "문자열"
        described == "number" -> "숫자"
        described == "end of input" -> "코드의 끝"
        described.startsWith("token ") -> described.removePrefix("token ")
        described.startsWith("identifier ") -> "이름 " + described.removePrefix("identifier ")
        else -> described
    }

    fun korean(code: String, args: List<String>, english: String): String? {
        val first = args.getOrNull(0).orEmpty()
        return when (code) {
            // Rules
            // One code, three things that can't be assigned: the linter's text says which.
            "const-assign" -> when {
                "is an import" in english -> "${named(first, "은/는")} import한 이름이라 대입할 수 없어요."
                "inside its class" in english -> "클래스 이름 ${named(first, "은/는")} 그 클래스 안에서 대입할 수 없어요."
                else -> "${named(first, "은/는")} 상수라서 다시 대입할 수 없어요."
            }
            "use-before-define" -> "${named(first, "을/를")} 선언하기 전에 사용했어요."
            "this-before-super" -> "'super()'를 부르기 전에는 '$first'를 쓸 수 없어요."
            "missing-super-call" -> "상속받은 클래스의 생성자는 'super()'를 불러야 해요."
            "undefined-name" -> "${named(first, "이/가")} 정의되지 않았어요."
            "readonly-global-assign" -> "${named(first, "은/는")} 기본으로 제공되는 이름이에요. 대입하면 원래 기능을 잃어요."
            "var-before-define" -> "${named(first, "을/를")} var 선언보다 먼저 사용했어요. 이때의 값은 undefined예요."
            "class-assign" -> "클래스 '$first'에 값을 대입하고 있어요."
            "func-assign" ->
                if ("own name" in english) "함수 안에서 그 함수의 이름 '$first'에 대입해도 바뀌지 않아요."
                else "함수 '$first'에 값을 대입하고 있어요."
            "cond-assign" -> "조건 자리에 대입(=)이 있어요. 비교하려면 === 를 쓰세요."
            "unreachable" -> "실행되지 않는 코드예요."
            "fallthrough" -> "'$first' 앞에 break가 없어 앞의 case에서 그대로 넘어와요."
            "duplicate-key" -> "키 ${named(first, "이/가")} 중복됐어요."
            "duplicate-param" -> "매개변수 ${named(first, "이/가")} 중복됐어요."
            "duplicate-class-member" -> "클래스 멤버 ${named(first, "이/가")} 중복됐어요."
            "duplicate-case" -> "같은 case가 앞에 이미 있어요."
            "duplicate-else-if" -> "앞의 조건과 겹쳐서 이 분기는 실행되지 않아요."
            "self-assign" -> "${named(first, "을/를")} 자기 자신에게 대입하고 있어요."
            "nan-comparison" -> "NaN은 어떤 값과도 같지 않아서 이 비교는 맞는 일이 없어요. Number.isNaN을 쓰세요."
            "invalid-typeof" ->
                // Compared with the value undefined, not a text.
                if (first.startsWith("undefined (")) "typeof의 결과는 문자열이에요. undefined가 아니라 \"undefined\"와 비교하세요."
                else "typeof는 ${named(first, "을/를")} 돌려주지 않아요."
            "unsafe-negation" -> "'!'가 '$first'의 결과가 아니라 왼쪽 값에만 적용돼요. 괄호로 감싸세요."
            "debugger" -> "debugger 문이 남아 있어요."
            "sparse-array" -> "배열에 빈 칸이 있어요. ','가 하나 더 들어갔거나 값이 빠졌어요."
            "precision-loss" -> "이 숫자는 적은 그대로 저장되지 않아요. 실제 값: $first"
            "useless-escape" ->
                "문자열에서 '\\$first'의 \\는 사라지고 '$first'만 남아요. 정규식에 쓸 것이라면 \\를 두 번 적으세요: '\\\\$first'"
            "unused-variable" ->
                if (args.getOrNull(1) == "assigned") "${named(first, "은/는")} 값을 넣기만 하고 읽지 않아요."
                else "${named(first, "은/는")} 선언만 하고 쓰지 않아요."
            "constant-condition" ->
                if (first == "true") "이 조건은 항상 참(true)이에요."
                else "이 조건은 항상 거짓(false)이에요."
            "too-deep" -> "코드가 너무 깊게 중첩되어 검사할 수 없어요."
            "internal-error" -> "검사 중 문제가 생겨 일부만 검사했어요: $first"

            // Syntax errors with their own arguments
            "syntax/unexpected-end" -> "코드가 중간에 끝났어요. 닫지 않은 괄호가 있는지 확인하세요."
            "syntax/unexpected-token" -> "여기에 올 수 없어요: ${token(first)}"
            "syntax/missing-token" -> "${withParticle("'$first'", "이/가")} 와야 하는데 ${withParticle(token(args.getOrNull(1).orEmpty()), "이/가")} 왔어요."
            "syntax/unterminated-string" -> "문자열이 닫히지 않았어요."
            "syntax/unterminated-template" -> "템플릿 문자열(`)이 닫히지 않았어요."
            "syntax/unterminated-comment" -> "주석(/* */)이 닫히지 않았어요."
            "syntax/unterminated-regexp" -> "정규식이 닫히지 않았어요."
            "syntax/invalid-regexp" -> "잘못된 정규식이에요: ${args.getOrNull(2).orEmpty()}"
            "syntax/invalid-regexp-flags" -> "정규식의 플래그가 잘못됐어요."
            "syntax/invalid-character" -> "여기에 올 수 없는 문자예요."
            "syntax/invalid-escape" -> "잘못된 이스케이프(\\)예요. ($english)"
            "syntax/invalid-number" -> "숫자 표기가 잘못됐어요. ($english)"
            "syntax/strict-octal-escape" -> "strict 모드에서는 8진수 이스케이프를 쓸 수 없어요."
            // Used outside any class, or in a class that doesn't declare it.
            "syntax/private-name-undeclared" -> "${named("#$first", "은/는")} 그것을 선언한 클래스 안에서만 쓸 수 있어요."
            "syntax/redeclared" -> "${named(first, "은/는")} 이미 선언됐어요."
            "syntax/duplicate-parameter" -> "매개변수 이름이 중복됐어요."
            "syntax/duplicate-export" -> "${named(first, "을/를")} 두 번 export했어요."
            "syntax/undefined-export" -> "export한 ${named(first, "이/가")} 선언되지 않았어요."

            // Syntax errors that stand for several of the engine's messages: the engine's own is kept beside.
            "syntax/reserved-word" -> "예약어는 이름으로 쓸 수 없어요. ($english)"
            "syntax/invalid-assignment-target" -> "대입할 수 없는 대상이에요. ($english)"
            "syntax/missing-initializer" -> "선언에 초기값이 필요해요. ($english)"
            "syntax/illegal-return" -> "함수 밖에서는 return을 쓸 수 없어요."
            "syntax/illegal-break" -> "break나 continue가 갈 곳이 없어요. ($english)"
            "syntax/import-export-placement" -> "import와 export는 모듈의 최상위에서만 쓸 수 있어요. ($english)"
            "syntax/await-yield" -> "여기서는 await나 yield를 쓸 수 없어요. ($english)"
            "syntax/super-new-target" -> "여기서는 super나 new.target을 쓸 수 없어요. ($english)"
            "syntax/class" -> "클래스 정의가 잘못됐어요. ($english)"
            "syntax/strict-mode" -> "strict 모드에서는 쓸 수 없어요. ($english)"
            "syntax/declaration-placement" -> "이 자리에는 선언을 둘 수 없어요. ($english)"
            "syntax/statement" -> "문장이 잘못됐어요. ($english)"
            "syntax/module" -> "import나 export 구문이 잘못됐어요. ($english)"
            "syntax/error" -> "문법 오류예요. ($english)"

            // What the parser's recovery tells of a mistake itself
            "syntax/missing-expression" -> "값이 빠졌어요."
            "syntax/missing-paren" -> "')'가 빠졌어요."
            "syntax/missing-bracket" -> "']'가 빠졌어요."
            "syntax/missing-brace" -> "'}'가 빠졌어요."
            "syntax/missing-comma" -> "항목 사이에 ','가 빠졌어요."
            "syntax/missing-colon" -> "조건 연산자(? :)의 ':'가 빠졌어요."
            "syntax/else-without-if" -> "짝이 되는 if가 없는 else예요."
            "syntax/elseif" -> "'elseif'가 아니라 'else if'로 써야 해요."
            "syntax/missing-condition" -> "'$first'의 조건이 빠졌어요."
            "syntax/condition-parens" -> "'$first'의 조건은 괄호로 감싸야 해요."
            "syntax/catch-parens" -> "catch의 매개변수는 괄호로 감싸야 해요."
            "syntax/unclosed-parameters" -> "매개변수 목록의 ')'가 빠졌어요."

            // The app's own, for Rhino
            RHINO_UNSUPPORTED -> "Rhino 엔진이 읽지 못하는 문법이에요. ($english)"
            RHINO_SYNTAX -> "문법 오류예요. ($english)"
            else -> null
        }
    }

    /** Rhino's parser refuses what is valid JavaScript: syntax newer than Rhino knows. */
    const val RHINO_UNSUPPORTED = "rhino/unsupported"

    /** Rhino's parser refuses it, and so does the linter's, which had no finding on that line to show instead. */
    const val RHINO_SYNTAX = "rhino/syntax"
}
