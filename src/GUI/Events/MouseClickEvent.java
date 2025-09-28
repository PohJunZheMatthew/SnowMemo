package GUI.Events;

import GUI.GUIComponent;

public class MouseClickEvent extends Event {
    private final int mouseX, mouseY;
    private final int button;

    public MouseClickEvent(GUIComponent source, int mouseX, int mouseY, int button) {
        super(source);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.button = button;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public int getButton() {
        return button;
    }
}
