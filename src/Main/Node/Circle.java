package Main.Node;

import Main.*;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class Circle extends Mesh implements Updatable {

    public enum CircleType {
        INPUT,
        OUTPUT
    }

    private CircleType circleType;
    private Node parent;
    private static final float COLLISION_EXPANSION = 1.5f; // Make hitbox 50% larger for easier clicking
    private static boolean DEBUG_RENDER = true; // Toggle debug rendering
    private Mesh debugBox = null;

    public Circle(Node parent, CircleType circleType, Window window) {
        super(Utils.loadObj(window, Circle.class.getResourceAsStream("Circle.obj"), Circle.class), window);
        setScale(new Vector3f(0.25f));
        setRotation(new Vector3f((float) Math.PI * 0.5f, (float) Math.PI, (float) Math.PI));
        this.circleType = circleType;
        this.parent = parent;
        if (DEBUG_RENDER) {
            createDebugBox();
        }
    }

    private void createDebugBox() {
        // Create a unit cube for debug visualization
        float[] boxVertices = {
                // Bottom face
                -0.5f, -0.5f, -0.5f,  0, -1, 0,  0, 0,
                0.5f, -0.5f, -0.5f,  0, -1, 0,  1, 0,
                0.5f, -0.5f,  0.5f,  0, -1, 0,  1, 1,
                -0.5f, -0.5f,  0.5f,  0, -1, 0,  0, 1,
                // Top face
                -0.5f,  0.5f, -0.5f,  0, 1, 0,  0, 0,
                0.5f,  0.5f, -0.5f,  0, 1, 0,  1, 0,
                0.5f,  0.5f,  0.5f,  0, 1, 0,  1, 1,
                -0.5f,  0.5f,  0.5f,  0, 1, 0,  0, 1,
        };

        int[] boxIndices = {
                // Bottom face
                0, 1, 1, 2, 2, 3, 3, 0,
                // Top face
                4, 5, 5, 6, 6, 7, 7, 4,
                // Vertical edges
                0, 4, 1, 5, 2, 6, 3, 7
        };

        debugBox = new Mesh(boxVertices, boxIndices, win, 8);
        debugBox.setMaterial(new Mesh.Material(
                new Vector4f(1, 0, 0, 1),  // Red for debug
                new Vector4f(1, 0, 0, 1),
                new Vector4f(1, 0, 0, 1),
                32.0f
        ));
        debugBox.cullFace = false;
        debugBox.outline = false;
    }

    /**
     * Get the AABB bounds in world space with collision expansion
     */
    private void getWorldAABB(Vector3f min, Vector3f max) {
        float[] verts = getVertices();
        Vector3f pos = getPosition();
        Vector3f scale = new Vector3f(getScale()).mul(COLLISION_EXPANSION);

        min.set(Float.POSITIVE_INFINITY);
        max.set(Float.NEGATIVE_INFINITY);

        // Transform all vertices to world space and find min/max
        for (int i = 0; i < verts.length; i += 3) {
            float x = pos.x + verts[i] * scale.x;
            float y = pos.y + verts[i + 1] * scale.y;
            float z = pos.z + verts[i + 2] * scale.z;

            if (x < min.x) min.x = x;
            if (y < min.y) min.y = y;
            if (z < min.z) min.z = z;

            if (x > max.x) max.x = x;
            if (y > max.y) max.y = y;
            if (z > max.z) max.z = z;
        }
    }

    public boolean collideWithMouse(Camera camera) {
        Vector3f mouseWorld = win.getCursorWorldPos();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        getWorldAABB(min, max);

        // Simple AABB point intersection test
        return mouseWorld.x >= min.x && mouseWorld.x <= max.x &&
                mouseWorld.y >= min.y && mouseWorld.y <= max.y &&
                mouseWorld.z >= min.z && mouseWorld.z <= max.z;
    }

    // Overload for compatibility
    public boolean collideWithMouse() {
        return collideWithMouse(win.camera);
    }

    @Override
    public void render(Camera camera) {
        super.render(camera);

        // Render debug box if enabled
        if (DEBUG_RENDER && debugBox != null) {
            Vector3f min = new Vector3f();
            Vector3f max = new Vector3f();
            getWorldAABB(min, max);

            // Calculate center and size of AABB
            Vector3f center = new Vector3f(min).add(max).mul(0.5f);
            Vector3f size = new Vector3f(max).sub(min);

            debugBox.setPosition(center);
            debugBox.setRotation(new Vector3f(0, 0, 0)); // No rotation for AABB
            debugBox.setScale(size);

            // Render as lines
            org.lwjgl.opengl.GL11.glPolygonMode(
                    org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK,
                    org.lwjgl.opengl.GL11.GL_LINE
            );
            org.lwjgl.opengl.GL11.glLineWidth(2.0f);
            debugBox.render(camera);
            org.lwjgl.opengl.GL11.glPolygonMode(
                    org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK,
                    org.lwjgl.opengl.GL11.GL_FILL
            );
        }
    }

    @Override
    public void cleanUp() {
        super.cleanUp();
        if (debugBox != null) {
            debugBox.cleanUp();
        }
    }

    public static void setDebugRender(boolean enabled) {
        DEBUG_RENDER = enabled;
    }

    @Override
    public void update() { }
}