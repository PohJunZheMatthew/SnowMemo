package GUI;

import Main.Texture;
import Main.Window;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector2f;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * A billboard GUI component that can be placed in 3D space.
 * The billboard always faces the camera and maintains its orientation.
 */
public abstract class BillboardGUIComponent extends GUIComponent {
    // 3D position in world space
    protected Vector3f worldPosition = new Vector3f(0, 0, 0);

    // Physical size in world units (not screen pixels)
    protected float worldWidth = 1.0f;
    protected float worldHeight = 1.0f;

    // Billboard rotation (if you want manual rotation override)
    protected float billboardRotation = 0.0f;

    // Whether to lock to Y-axis (cylindrical billboard)
    protected boolean lockYAxis = false;

    // Pixel dimensions for texture rendering
    protected int textureWidthPx = 256;
    protected int textureHeightPx = 256;

    public BillboardGUIComponent(Window window) {
        super(window);
        initBillboard();
    }

    public BillboardGUIComponent(Window window, float worldWidth, float worldHeight) {
        super(window, 0, 0);
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        initBillboard();
    }

    public BillboardGUIComponent(Window window, Vector3f position, float worldWidth, float worldHeight) {
        super(window, 0, 0);
        this.worldPosition = new Vector3f(position);
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        initBillboard();
    }
    public BillboardGUIComponent(Window window,Vector3f positon,Vector2f worldSize){
        this(window,positon,worldSize.x,worldSize.y);
    }

    private void initBillboard() {
        // Disable normal GUI mouse events for 3D billboards
        this.CustomMouseEvents = true;
        this.visible = true;
    }

    /**
     * Renders the billboard in 3D space
     * @param viewMatrix The camera view matrix
     * @param projectionMatrix The projection matrix
     */
    public void render3D(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (!visible) return;

        // Create the billboard texture from 2D graphics
        BufferedImage bi = new BufferedImage(textureWidthPx, textureHeightPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Clear with transparency
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, textureWidthPx, textureHeightPx);
        g.setComposite(AlphaComposite.SrcOver);

        paintComponent(g);
        g.dispose();

        // Update texture
        if (texture != null) {
            texture.cleanup();
        }
        texture = Texture.loadTexture(bi);

        // Calculate billboard orientation matrix
        Matrix4f modelMatrix = createBillboardMatrix(viewMatrix);

        // Enable blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Enable depth testing but disable depth writing for proper transparency
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);

        // Bind shader and set uniforms
        shaderProgram.bind();

        // For 3D rendering, we need to pass model-view-projection matrix
        Matrix4f mvp = new Matrix4f(projectionMatrix).mul(viewMatrix).mul(modelMatrix);
        shaderProgram.setUniform("projection", mvp);

        // Set position and size (normalized for the billboard quad)
        shaderProgram.setUniform("position", new Vector2f(0, 0));
        shaderProgram.setUniform("size", new Vector2f(1, 1));

        // Bind texture
        glActiveTexture(GL_TEXTURE0);
        texture.bind();
        shaderProgram.setUniform("guiTexture", 0);

        // Draw billboard
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        // Cleanup
        texture.unbind();
        shaderProgram.unbind();

        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    /**
     * Creates a model matrix that makes the quad face the camera
     */
    private Matrix4f createBillboardMatrix(Matrix4f viewMatrix) {
        Matrix4f modelMatrix = new Matrix4f();

        if (lockYAxis) {
            // Cylindrical billboard - only rotate around Y axis
            Vector3f cameraPos = extractCameraPosition(viewMatrix);
            Vector3f toCamera = new Vector3f(cameraPos).sub(worldPosition);
            toCamera.y = 0; // Project to XZ plane
            toCamera.normalize();

            Vector3f right = new Vector3f(toCamera).cross(new Vector3f(0, 1, 0));

            modelMatrix.identity()
                    .translate(worldPosition)
                    .rotate((float) Math.atan2(toCamera.x, toCamera.z), new Vector3f(0, 1, 0))
                    .scale(worldWidth, worldHeight, 1.0f);
        } else {
            // Spherical billboard - face camera completely
            // Extract rotation from view matrix and invert it
            Matrix4f billboardRot = new Matrix4f(viewMatrix);
            billboardRot.m30(0); billboardRot.m31(0); billboardRot.m32(0); // Clear translation
            billboardRot.transpose(); // Invert rotation (orthogonal matrix)

            modelMatrix.identity()
                    .translate(worldPosition)
                    .mul(billboardRot)
                    .rotateZ(billboardRotation)
                    .scale(worldWidth, worldHeight, 1.0f);
        }

        return modelMatrix;
    }

    /**
     * Extracts camera position from view matrix
     */
    private Vector3f extractCameraPosition(Matrix4f viewMatrix) {
        Matrix4f invView = new Matrix4f(viewMatrix).invert();
        return new Vector3f(invView.m30(), invView.m31(), invView.m32());
    }

    @Override
    public void render() {
        // Override to prevent normal 2D GUI rendering
        // Use render3D() instead
    }

    @Override
    protected void renderGUIImage() {
        // Not used in 3D mode
    }

    // Getters and setters for 3D properties
    public Vector3f getWorldPosition() {
        return new Vector3f(worldPosition);
    }

    public BillboardGUIComponent setWorldPosition(Vector3f position) {
        this.worldPosition = new Vector3f(position);
        return this;
    }

    public BillboardGUIComponent setWorldPosition(float x, float y, float z) {
        this.worldPosition.set(x, y, z);
        return this;
    }

    public float getWorldWidth() {
        return worldWidth;
    }

    public BillboardGUIComponent setWorldWidth(float worldWidth) {
        this.worldWidth = worldWidth;
        return this;
    }

    public float getWorldHeight() {
        return worldHeight;
    }

    public BillboardGUIComponent setWorldHeight(float worldHeight) {
        this.worldHeight = worldHeight;
        return this;
    }

    public BillboardGUIComponent setWorldSize(float width, float height) {
        this.worldWidth = width;
        this.worldHeight = height;
        return this;
    }

    public boolean isLockYAxis() {
        return lockYAxis;
    }

    public BillboardGUIComponent setLockYAxis(boolean lockYAxis) {
        this.lockYAxis = lockYAxis;
        return this;
    }

    public float getBillboardRotation() {
        return billboardRotation;
    }

    public BillboardGUIComponent setBillboardRotation(float rotation) {
        this.billboardRotation = rotation;
        return this;
    }

    public int getTextureWidthPx() {
        return textureWidthPx;
    }

    public BillboardGUIComponent setTextureWidthPx(int width) {
        this.textureWidthPx = Math.max(1, width);
        return this;
    }

    public int getTextureHeightPx() {
        return textureHeightPx;
    }

    public BillboardGUIComponent setTextureHeightPx(int height) {
        this.textureHeightPx = Math.max(1, height);
        return this;
    }

    public BillboardGUIComponent setTextureResolution(int width, int height) {
        this.textureWidthPx = Math.max(1, width);
        this.textureHeightPx = Math.max(1, height);
        return this;
    }
}