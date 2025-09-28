package GUI.Events;

import GUI.GUIComponent;

public class MouseExitEvent extends Event {
    private final int mouseX, mouseY;

    public MouseExitEvent(GUIComponent source, int mouseX, int mouseY) {
        super(source);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }
}
