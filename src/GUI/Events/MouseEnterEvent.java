package GUI.Events;

import GUI.GUIComponent;

public class MouseEnterEvent extends Event {
    private final int mouseX, mouseY;

    public MouseEnterEvent(GUIComponent source, int mouseX, int mouseY) {
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
