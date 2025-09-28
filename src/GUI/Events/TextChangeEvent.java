package GUI.Events;

import GUI.GUIComponent;

public class TextChangeEvent extends Event {
    private final String oldText;
    private final String newText;

    public TextChangeEvent(GUIComponent source, String oldText, String newText) {
        super(source);
        this.oldText = oldText;
        this.newText = newText;
    }

    public String getOldText() {
        return oldText;
    }

    public String getNewText() {
        return newText;
    }
}