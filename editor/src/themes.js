import { EditorView } from "@codemirror/view";
import { HighlightStyle, syntaxHighlighting } from "@codemirror/language";
import { tags as t } from "@lezer/highlight";
import THEMES from "./generated/themes.json";

// Lezer tags and the Ace token classes they take their style from, most specific first.
const TOKEN_MAP = [
    [[t.comment, t.lineComment, t.blockComment], ["comment"]],
    [[t.docComment], ["comment.doc", "comment"]],
    [[t.controlKeyword, t.operatorKeyword], ["keyword.control", "keyword"]],
    [[t.moduleKeyword, t.keyword], ["keyword"]],
    [[t.definitionKeyword, t.modifier], ["storage.type", "storage", "keyword"]],
    [[t.operator, t.compareOperator, t.arithmeticOperator, t.logicOperator, t.bitwiseOperator, t.updateOperator, t.definitionOperator], ["keyword.operator"]],
    [[t.string, t.special(t.string), t.character, t.docString], ["string"]],
    [[t.regexp], ["string.regexp", "string"]],
    [[t.escape], ["constant.character.escape", "constant.character", "constant"]],
    [[t.number, t.integer, t.float], ["constant.numeric", "constant"]],
    [[t.bool, t.null, t.atom], ["constant.language", "constant"]],
    [[t.self, t.special(t.variableName)], ["variable.language", "constant.language"]],
    [[t.constant(t.variableName)], ["constant.other", "constant"]],
    [[t.function(t.definition(t.variableName)), t.function(t.variableName)], ["entity.name.function", "support.function"]],
    [[t.standard(t.variableName), t.function(t.propertyName)], ["support.function"]],
    [[t.definition(t.variableName)], ["variable.parameter", "variable"]],
    [[t.typeName, t.standard(t.typeName), t.namespace], ["support.type", "storage.type"]],
    [[t.className, t.definition(t.className)], ["support.class", "entity.name.class", "support.type"]],
    [[t.tagName], ["entity.name.tag", "meta.tag"]],
    [[t.attributeName], ["entity.other.attribute-name"]],
    [[t.angleBracket], ["meta.tag", "punctuation"]],
    [[t.meta, t.annotation, t.processingInstruction], ["meta", "keyword"]],
    [[t.heading], ["markup.heading", "heading"]],
    [[t.link, t.url], ["markup.underline", "string"]],
    [[t.invalid], ["invalid"]],
];

const pick = (tokens, keys) => {
    for (const key of keys)
        if (tokens[key]) return tokens[key];
    return null;
};

const translucent = (dark) => (dark ? "rgba(255, 255, 255, 0.06)" : "rgba(0, 0, 0, 0.05)");

const build = ({ dark, editor, tokens }) => {
    const background = editor.background ?? (dark ? "#1e1e1e" : "#ffffff");
    const foreground = editor.foreground ?? (dark ? "#d4d4d4" : "#000000");
    const gutterBackground = editor.gutterBackground ?? background;
    const selection = editor.selection ?? (dark ? "#264f78" : "#b5d5ff");
    const popupBackground = dark ? gutterBackground : background;

    const theme = EditorView.theme({
        "&": { color: foreground, backgroundColor: background, height: "100%" },
        ".cm-content": { caretColor: editor.cursor ?? foreground },
        ".cm-cursor, .cm-dropCursor": { borderLeftColor: editor.cursor ?? foreground },
        ".cm-content ::selection": { backgroundColor: selection },
        ".cm-gutters": { backgroundColor: gutterBackground, color: editor.gutterForeground ?? foreground, border: "none" },
        ".cm-activeLine": { backgroundColor: editor.activeLine ?? translucent(dark) },
        ".cm-activeLineGutter": { backgroundColor: editor.gutterActiveLine ?? editor.activeLine ?? translucent(dark) },
        "&.cm-focused .cm-matchingBracket": { backgroundColor: "transparent", outline: `1px solid ${editor.bracket ?? foreground}` },
        ".cm-selectionMatch": { backgroundColor: "transparent", outline: `1px solid ${editor.selectedWord ?? selection}` },
        ".cm-searchMatch": { backgroundColor: selection },
        ".cm-panels": { backgroundColor: gutterBackground, color: foreground },
        ".cm-panels input, .cm-panels button": { color: "inherit" },
        ".cm-tooltip": { backgroundColor: popupBackground, color: foreground, border: `1px solid ${selection}` },
        ".cm-tooltip-autocomplete > ul > li[aria-selected]": { backgroundColor: selection, color: foreground },
        ".cm-completionDetail, .cm-completionInfo": { opacity: 0.7 },
        ".cm-foldPlaceholder": { backgroundColor: selection, color: foreground, border: "none" },
    }, { dark });

    const specs = [];
    for (const [tagList, keys] of TOKEN_MAP) {
        const style = pick(tokens, keys);
        if (style) specs.push({ tag: tagList, ...style });
    }
    return [theme, syntaxHighlighting(HighlightStyle.define(specs))];
};

const cache = new Map();

export const themeFor = (name) => {
    const key = THEMES[name] ? name : "tomorrow_night_eighties";
    if (!cache.has(key)) cache.set(key, build(THEMES[key]));
    return cache.get(key);
};
