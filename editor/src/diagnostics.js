import { StateEffect, StateField } from "@codemirror/state";
import { EditorView, showTooltip } from "@codemirror/view";
import { forEachDiagnostic } from "@codemirror/lint";

const showLineDiagnostics = StateEffect.define();

const lineTooltip = StateField.define({
    create: () => null,
    update(value, tr) {
        for (const effect of tr.effects)
            if (effect.is(showLineDiagnostics)) return effect.value;
        return tr.docChanged || tr.selection ? null : value;
    },
    provide: (field) => showTooltip.from(field),
});

const tooltipFor = (state, line) => {
    const messages = [];
    forEachDiagnostic(state, (d, from) => {
        if (from >= line.from && from <= line.to) messages.push(d);
    });
    if (!messages.length) return null;
    return {
        pos: line.from,
        above: false,
        create: () => {
            const dom = document.createElement("div");
            dom.className = "cm-line-diagnostics";
            for (const d of messages) {
                const row = document.createElement("div");
                row.className = `cm-diagnostic cm-diagnostic-${d.severity}`;
                row.textContent = d.message;
                dom.appendChild(row);
            }
            return { dom };
        },
    };
};

// Lint markers only show their message on hover, which touch screens never trigger.
export const tapToShowDiagnostics = [
    lineTooltip,
    EditorView.domEventHandlers({
        click(event, view) {
            if (!event.target.closest?.(".cm-lint-marker")) return false;
            const block = view.lineBlockAtHeight(event.clientY - view.documentTop);
            const line = view.state.doc.lineAt(block.from);
            view.dispatch({ effects: showLineDiagnostics.of(tooltipFor(view.state, line)) });
            return true;
        },
    }),
    EditorView.theme({
        ".cm-line-diagnostics": { padding: "2px 0", maxWidth: "90vw", fontSize: "0.85em" },
    }),
];
