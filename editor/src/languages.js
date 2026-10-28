import { StreamLanguage } from "@codemirror/language";
import { javascript } from "@codemirror/lang-javascript";
import { json } from "@codemirror/lang-json";
import { python } from "@codemirror/lang-python";
import { java } from "@codemirror/lang-java";
import { cpp } from "@codemirror/lang-cpp";
import { rust } from "@codemirror/lang-rust";
import { php } from "@codemirror/lang-php";
import { markdown } from "@codemirror/lang-markdown";
import { go } from "@codemirror/lang-go";
import { xml } from "@codemirror/lang-xml";
import { html } from "@codemirror/lang-html";
import { css } from "@codemirror/lang-css";
import { yaml } from "@codemirror/lang-yaml";
import { csharp, dart, kotlin, objectiveC, scala } from "@codemirror/legacy-modes/mode/clike";
import { swift } from "@codemirror/legacy-modes/mode/swift";
import { lua } from "@codemirror/legacy-modes/mode/lua";
import { ruby } from "@codemirror/legacy-modes/mode/ruby";
import { r } from "@codemirror/legacy-modes/mode/r";
import { perl } from "@codemirror/legacy-modes/mode/perl";
import { pascal } from "@codemirror/legacy-modes/mode/pascal";
import { fortran } from "@codemirror/legacy-modes/mode/fortran";
import { coffeeScript } from "@codemirror/legacy-modes/mode/coffeescript";
import { groovy } from "@codemirror/legacy-modes/mode/groovy";
import { vbScript } from "@codemirror/legacy-modes/mode/vbscript";
import { gas } from "@codemirror/legacy-modes/mode/gas";
import { diff } from "@codemirror/legacy-modes/mode/diff";
import { dockerFile } from "@codemirror/legacy-modes/mode/dockerfile";

const legacy = (parser) => () => StreamLanguage.define(parser);

// Keys are the Ace mode names sent by DefaultEditorActivity.Language.editorName.
const LANGUAGES = {
    javascript: () => javascript(),
    actionscript: () => javascript(),
    typescript: () => javascript({ typescript: true }),
    jsx: () => javascript({ jsx: true }),
    json: () => json(),
    json5: () => json(),
    python: () => python(),
    java: () => java(),
    c_cpp: () => cpp(),
    rust: () => rust(),
    php: () => php(),
    markdown: () => markdown(),
    golang: () => go(),
    xml: () => xml(),
    html: () => html(),
    css: () => css(),
    yaml: () => yaml(),
    csharp: legacy(csharp),
    dart: legacy(dart),
    kotlin: legacy(kotlin),
    objectivec: legacy(objectiveC),
    scala: legacy(scala),
    swift: legacy(swift),
    lua: legacy(lua),
    ruby: legacy(ruby),
    r: legacy(r),
    perl: legacy(perl),
    pascal: legacy(pascal),
    fortran: legacy(fortran),
    coffee: legacy(coffeeScript),
    groovy: legacy(groovy),
    vbscript: legacy(vbScript),
    assembly_x86: legacy(gas),
    diff: legacy(diff),
    dockerfile: legacy(dockerFile),
};

export const languageFor = (mode) => LANGUAGES[mode]?.() ?? [];
