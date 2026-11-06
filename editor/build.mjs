import { build, transform } from "esbuild";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const out = join(root, "../app/src/main/assets/editor");

// Ace theme names used by DefaultEditorActivity.Theme (persisted by ordinal on the Kotlin side).
const THEMES = [
    "github", "eclipse", "clouds", "xcode", "chrome", "dreamweaver", "iplastic", "katzenmilch",
    "solarized_light", "tomorrow", "github_dark", "dracula", "gruvbox", "clouds_midnight",
    "pastel_on_dark", "solarized_dark", "terminal", "tomorrow_night", "tomorrow_night_eighties",
    "monokai", "kr_theme", "nord_dark", "one_dark", "twilight",
];

const COLOR = /#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)|\b(?:white|black|transparent)\b/;

const firstColor = (value) => value?.match(COLOR)?.[0];

function parseAceTheme(name) {
    const source = readFileSync(join(root, `node_modules/ace-builds/src-noconflict/theme-${name}.js`), "utf8");
    const css = JSON.parse(source.match(/module\.exports = (".*?");\s*\}\);/s)[1]);
    const cssClass = source.match(/cssClass\s*=\s*"([^"]+)"/)[1];
    const isDark = /isDark\s*=\s*true/.test(source);

    const editor = {};
    const tokens = {};
    const rules = css.replace(/\/\*[\s\S]*?\*\//g, "").split("}");
    for (const rule of rules) {
        const [selectorText, body] = rule.split("{");
        if (!body) continue;
        const decls = {};
        for (const decl of body.split(";")) {
            const index = decl.indexOf(":");
            if (index < 0) continue;
            decls[decl.slice(0, index).trim()] = decl.slice(index + 1).trim();
        }
        const color = firstColor(decls["color"]);
        const background = firstColor(decls["background-color"] ?? decls["background"]);
        const border = firstColor(decls["border"] ?? decls["border-color"] ?? decls["border-bottom"]);

        for (const raw of selectorText.split(",")) {
            const selector = raw.trim().replace(/\s+/g, " ");
            if (!selector.startsWith(`.${cssClass}`)) continue;
            const rest = selector.slice(cssClass.length + 1).trim();
            switch (rest) {
                case "": editor.background ??= background; editor.foreground ??= color; continue;
                case ".ace_gutter": editor.gutterBackground ??= background; editor.gutterForeground ??= color; continue;
                case ".ace_cursor": editor.cursor ??= color; continue;
                case ".ace_marker-layer .ace_selection": editor.selection ??= background; continue;
                case ".ace_marker-layer .ace_active-line": editor.activeLine ??= background; continue;
                case ".ace_gutter-active-line": editor.gutterActiveLine ??= background; continue;
                case ".ace_marker-layer .ace_bracket": editor.bracket ??= border ?? background; continue;
                case ".ace_marker-layer .ace_selected-word": editor.selectedWord ??= border ?? background; continue;
            }
            const match = rest.match(/^((?:\.ace_[\w-]+)+)$/);
            if (!match) continue;
            const key = match[1].split(".ace_").filter(Boolean).join(".");
            const style = {};
            if (color) style.color = color;
            if (decls["font-style"]) style.fontStyle = decls["font-style"];
            if (decls["font-weight"]) style.fontWeight = decls["font-weight"];
            if (decls["text-decoration"]) style.textDecoration = decls["text-decoration"];
            if (Object.keys(style).length)
                tokens[key] = { ...tokens[key], ...style };
        }
    }
    return { dark: isDark, editor, tokens };
}

mkdirSync(join(root, "src/generated"), { recursive: true });
const themes = Object.fromEntries(THEMES.map((name) => [name, parseAceTheme(name)]));
writeFileSync(join(root, "src/generated/themes.json"), JSON.stringify(themes, null, 1));

await build({
    entryPoints: [join(root, "src/main.js")],
    bundle: true,
    minify: true,
    format: "iife",
    target: "chrome90",
    outfile: join(out, "editor.js"),
    legalComments: "none",
});

const jshint = await transform(readFileSync(join(root, "node_modules/jshint/dist/jshint.js"), "utf8"), {
    minify: true,
    target: "chrome90",
    legalComments: "none",
});
writeFileSync(join(out, "jshint.js"), jshint.code);
