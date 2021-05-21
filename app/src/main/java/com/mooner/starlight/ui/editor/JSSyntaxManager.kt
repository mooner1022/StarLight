package com.mooner.starlight.ui.editor

import android.content.Context
import com.amrdeveloper.codeview.CodeView
import com.mooner.starlight.R
import java.util.regex.Pattern


class JSSyntaxManager {
    companion object {
        private val PATTERN_KEYWORDS: Pattern = Pattern.compile(
            "\\b(abstract|break|case|catch|var|let|const" +
                    "|class|continue|default|do|else" +
                    "|enum|final|finally|float|for|if" +
                    "|import|instanceof|interface" +
                    "|new|null|package|use strict" +
                    "|return|switch|" +
                    "|this|throw|try|void|while|undefined|NaN)\\b"
        )

        private val PATTERN_BUILTINS: Pattern = Pattern.compile("[,:;->{}()]")
        private val PATTERN_COMMENT: Pattern =
            Pattern.compile("//(?!TODO )[^\\n]*" + "|" + "/\\*(.|\\R)*?\\*/")
        private val PATTERN_ATTRIBUTE: Pattern = Pattern.compile("\\.[a-zA-Z0-9_]+")
        private val PATTERN_OPERATION: Pattern =
            Pattern.compile(":|==|>|<|!=|>=|<=|->|=|>|<|%|-|-=|%=|\\+|\\-=|\\+=|\\^|\\&|\\|::|\\?|\\*")
        private val PATTERN_GENERIC: Pattern = Pattern.compile("<[a-zA-Z0-9,<>]+>")
        private val PATTERN_ANNOTATION: Pattern = Pattern.compile("@.[a-zA-Z0-9]+")
        private val PATTERN_TODO_COMMENT: Pattern = Pattern.compile("//TODO[^\n]*")
        private val PATTERN_NUMBERS: Pattern = Pattern.compile("\\b(\\d*[.]?\\d+)\\b")
        private val PATTERN_CHAR: Pattern = Pattern.compile("'[a-zA-Z]'")
        private val PATTERN_STRING: Pattern = Pattern.compile("\".*\"")
        private val PATTERN_HEX: Pattern = Pattern.compile("0x[0-9a-fA-F]+")

        fun applyMonokaiTheme(context: Context, codeView: CodeView) {
            codeView.resetSyntaxPatternList()
            //View Background
            codeView.setBackgroundColor(context.getColor(R.color.monokai_pro_black))

            //Syntax Colors
            codeView.addSyntaxPattern(
                PATTERN_HEX,
                context.getColor(R.color.monokai_pro_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_CHAR,
                context.getColor(R.color.monokai_pro_green)
            )
            codeView.addSyntaxPattern(
                PATTERN_STRING,
                context.getColor(R.color.monokai_pro_orange)
            )
            codeView.addSyntaxPattern(
                PATTERN_NUMBERS,
                context.getColor(R.color.monokai_pro_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_KEYWORDS,
                context.getColor(R.color.monokai_pro_pink)
            )
            codeView.addSyntaxPattern(
                PATTERN_BUILTINS,
                context.getColor(R.color.monokai_pro_white)
            )
            codeView.addSyntaxPattern(
                PATTERN_COMMENT,
                context.getColor(R.color.monokai_pro_grey)
            )
            codeView.addSyntaxPattern(
                PATTERN_ANNOTATION,
                context.getColor(R.color.monokai_pro_pink)
            )
            codeView.addSyntaxPattern(
                PATTERN_ATTRIBUTE,
                context.getColor(R.color.monokai_pro_sky)
            )
            codeView.addSyntaxPattern(
                PATTERN_GENERIC,
                context.getColor(R.color.monokai_pro_pink)
            )
            codeView.addSyntaxPattern(
                PATTERN_OPERATION,
                context.getColor(R.color.monokai_pro_pink)
            )
            //Default Color
            codeView.setTextColor(context.getColor(R.color.monokai_pro_white))
            codeView.addSyntaxPattern(
                PATTERN_TODO_COMMENT,
                context.getColor(R.color.gold)
            )
            codeView.reHighlightSyntax()
        }

        fun applyNoctisWhiteTheme(context: Context, codeView: CodeView) {
            codeView.resetSyntaxPatternList()
            //View Background
            codeView.setBackgroundColor(context.getColor(R.color.noctis_white))

            //Syntax Colors
            codeView.addSyntaxPattern(
                PATTERN_HEX,
                context.getColor(R.color.noctis_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_CHAR,
                context.getColor(R.color.noctis_green)
            )
            codeView.addSyntaxPattern(
                PATTERN_STRING,
                context.getColor(R.color.noctis_green)
            )
            codeView.addSyntaxPattern(
                PATTERN_NUMBERS,
                context.getColor(R.color.noctis_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_KEYWORDS,
                context.getColor(R.color.noctis_pink)
            )
            codeView.addSyntaxPattern(
                PATTERN_BUILTINS,
                context.getColor(R.color.noctis_dark_blue)
            )
            codeView.addSyntaxPattern(
                PATTERN_COMMENT,
                context.getColor(R.color.noctis_grey)
            )
            codeView.addSyntaxPattern(
                PATTERN_ANNOTATION,
                context.getColor(R.color.monokai_pro_pink)
            )
            codeView.addSyntaxPattern(
                PATTERN_ATTRIBUTE,
                context.getColor(R.color.noctis_blue)
            )
            codeView.addSyntaxPattern(
                PATTERN_GENERIC,
                context.getColor(R.color.monokai_pro_pink)
            )
            codeView.addSyntaxPattern(
                PATTERN_OPERATION,
                context.getColor(R.color.monokai_pro_pink)
            )
            //Default Color
            codeView.setTextColor(context.getColor(R.color.noctis_orange))
            codeView.addSyntaxPattern(
                PATTERN_TODO_COMMENT,
                context.getColor(R.color.gold)
            )
            codeView.reHighlightSyntax()
        }

        fun applyFiveColorsDarkTheme(context: Context, codeView: CodeView) {
            codeView.resetSyntaxPatternList()
            //View Background
            codeView.setBackgroundColor(context.getColor(R.color.five_dark_black))

            //Syntax Colors
            codeView.addSyntaxPattern(
                PATTERN_HEX,
                context.getColor(R.color.five_dark_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_CHAR,
                context.getColor(R.color.five_dark_yellow)
            )
            codeView.addSyntaxPattern(
                PATTERN_STRING,
                context.getColor(R.color.five_dark_yellow)
            )
            codeView.addSyntaxPattern(
                PATTERN_NUMBERS,
                context.getColor(R.color.five_dark_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_KEYWORDS,
                context.getColor(R.color.five_dark_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_BUILTINS,
                context.getColor(R.color.five_dark_white)
            )
            codeView.addSyntaxPattern(
                PATTERN_COMMENT,
                context.getColor(R.color.five_dark_grey)
            )
            codeView.addSyntaxPattern(
                PATTERN_ANNOTATION,
                context.getColor(R.color.five_dark_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_ATTRIBUTE,
                context.getColor(R.color.five_dark_blue)
            )
            codeView.addSyntaxPattern(
                PATTERN_GENERIC,
                context.getColor(R.color.five_dark_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_OPERATION,
                context.getColor(R.color.five_dark_purple)
            )
            //Default Color
            codeView.setTextColor(context.getColor(R.color.five_dark_white))
            codeView.addSyntaxPattern(
                PATTERN_TODO_COMMENT,
                context.getColor(R.color.gold)
            )
            codeView.reHighlightSyntax()
        }

        fun applyOrangeBoxTheme(context: Context, codeView: CodeView) {
            codeView.resetSyntaxPatternList()
            //View Background
            codeView.setBackgroundColor(context.getColor(R.color.orange_box_black))

            //Syntax Colors
            codeView.addSyntaxPattern(PATTERN_HEX, context.getColor(R.color.gold))
            codeView.addSyntaxPattern(
                PATTERN_CHAR,
                context.getColor(R.color.orange_box_orange2)
            )
            codeView.addSyntaxPattern(
                PATTERN_STRING,
                context.getColor(R.color.orange_box_orange2)
            )
            codeView.addSyntaxPattern(
                PATTERN_NUMBERS,
                context.getColor(R.color.five_dark_purple)
            )
            codeView.addSyntaxPattern(
                PATTERN_KEYWORDS,
                context.getColor(R.color.orange_box_orange1)
            )
            codeView.addSyntaxPattern(
                PATTERN_BUILTINS,
                context.getColor(R.color.orange_box_grey)
            )
            codeView.addSyntaxPattern(
                PATTERN_COMMENT,
                context.getColor(R.color.orange_box_dark_grey)
            )
            codeView.addSyntaxPattern(
                PATTERN_ANNOTATION,
                context.getColor(R.color.orange_box_orange1)
            )
            codeView.addSyntaxPattern(
                PATTERN_ATTRIBUTE,
                context.getColor(R.color.orange_box_orange3)
            )
            codeView.addSyntaxPattern(
                PATTERN_GENERIC,
                context.getColor(R.color.orange_box_orange1)
            )
            codeView.addSyntaxPattern(PATTERN_OPERATION, context.getColor(R.color.gold))
            //Default Color
            codeView.setTextColor(context.getColor(R.color.five_dark_white))
            codeView.addSyntaxPattern(
                PATTERN_TODO_COMMENT,
                context.getColor(R.color.gold)
            )
            codeView.reHighlightSyntax()
        }
    }
}