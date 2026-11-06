import beautifier from "js-beautify";

const OPTIONS = { indent_size: 4, end_with_newline: false, preserve_newlines: true, max_preserve_newlines: 2 };

export const beautify = (code, mode) => {
    switch (mode) {
        case "javascript":
        case "typescript":
        case "jsx":
        case "json":
        case "json5":
            return beautifier.js(code, OPTIONS);
        case "css":
            return beautifier.css(code, OPTIONS);
        case "html":
        case "xml":
            return beautifier.html(code, OPTIONS);
        default:
            return null;
    }
};
