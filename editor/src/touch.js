import { EditorView } from "@codemirror/view";

// A redraw while the editor is unfocused leaves DocView.forceSelection set, so the first
// selection after focusing gets rewritten from script. Chrome on Android then drops the
// native selection handles and loses the toolbar anchor. Clear it when focus arrives.
export const keepFirstTouchSelection = EditorView.domEventHandlers({
    focus(event, view) {
        view.docView.forceSelection = false;
        return false;
    },
});
