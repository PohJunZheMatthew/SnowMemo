package Main.User;

import GUI.AspectRatio;
import GUI.Events.MouseClickCallBack;
import GUI.Events.MouseClickEvent;
import GUI.GUIComponent;
import Main.Camera;
import Main.Mesh;
import Main.Resources.Icons.User.UserIcons;
import Main.Window;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.awt.*;

public class UserGUIComponent extends GUIComponent {
    private final AspectRatio aspectRatio;

    public UserGUIComponent(Window window) {
        super(window,0.95f,0f,0.05f,0.05f);
        aspectRatio = new AspectRatio(this,0.05f,0.05f,AspectRatio.AspectRatioSizeRule.USE_WIDTH);
        window.MouseButtonCallbacks.add(new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                updateHitBox();
                if (action == GLFW.GLFW_PRESS) {
                    if (getHitBox().contains(windowParent.getMouseX(), windowParent.getMouseY())) {
                        User.getUserMenuLayout().setVisible(!User.getUserMenuLayout().isVisible());
                    }
                }
            }
        });
    }

    @Override
    public void render(){
        aspectRatio.updateSize();
        super.render();
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(0,0,widthPx,heightPx,30,30);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2.5f));
        g2d.drawRoundRect(0,0,widthPx-3,heightPx-3,30,30);
        g2d.drawImage(UserIcons.ACCOUNT_BOX,0,0,widthPx,heightPx,null);
    }
}
