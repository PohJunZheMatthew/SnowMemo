package GUI.BillBoardGUI;

import GUI.GUIComponent;
import Main.*;
import Main.Window;

/**
 * A billboard GUI component that can be placed in 3D space.
 * The billboard always faces the camera and maintains its orientation.
 */
public class BillboardGUI extends Mesh {
    private static final float[] vertices = {
            // Vertex 0: Bottom-left
            -0.5f, -0.5f, 0.0f,    // position
            0.0f, 0.0f, 1.0f,      // normal (facing forward)
            0.0f, 1.0f,            // UV (bottom-left in texture)

            // Vertex 1: Bottom-right
            0.5f, -0.5f, 0.0f,     // position
            0.0f, 0.0f, 1.0f,      // normal
            1.0f, 1.0f,            // UV (bottom-right in texture)

            // Vertex 2: Top-right
            0.5f, 0.5f, 0.0f,      // position
            0.0f, 0.0f, 1.0f,      // normal
            1.0f, 0.0f,            // UV (top-right in texture)

            // Vertex 3: Top-left
            -0.5f, 0.5f, 0.0f,     // position
            0.0f, 0.0f, 1.0f,      // normal
            0.0f, 0.0f             // UV (top-left in texture)
    };

    private static final int[] indices = {
            0, 1, 2,  // First triangle
            2, 3, 0   // Second triangle
    };
    public GUIComponent mainGUIComponent;
    public BillboardGUI(Window currentWindow,GUIComponent mainGuiComponent) {
        super(vertices, indices, currentWindow, Texture.loadTexture(mainGuiComponent.print()));
        this.mainGUIComponent = mainGuiComponent;
        mainGUIComponent.setVisible(false);
    }
    public void render(Camera camera){
        texture.cleanup();
        texture = Texture.loadTexture(mainGUIComponent.print());
        super.render(camera);
    }
}