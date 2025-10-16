package GUI.BillBoardGUI;

import GUI.GUIComponent;
import Main.*;
import Main.Window;
import com.mongodb.lang.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

/**
 * Optimized BillboardGUI that reuses the same texture ID
 * and only updates the pixel data when needed.
 * Automatically calculates screen-space size and adjusts GUI component dimensions.
 */
public class BillboardGUI extends Mesh {
    private static final float[] vertices = {
            -0.5f, -0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    0.0f, 1.0f,
            0.5f, -0.5f, 0.0f,     0.0f, 0.0f, 1.0f,    1.0f, 1.0f,
            0.5f, 0.5f, 0.0f,      0.0f, 0.0f, 1.0f,    1.0f, 0.0f,
            -0.5f, 0.5f, 0.0f,     0.0f, 0.0f, 1.0f,    0.0f, 0.0f
    };

    private static final int[] indices = {
            0, 1, 2,  2, 3, 0
    };

    public GUIComponent mainGUIComponent;
    private int textureWidth = 512;
    private int textureHeight = 512;
    private boolean needsUpdate = true;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 16; // ~60 FPS

    public BillboardGUI(Window currentWindow, GUIComponent mainGuiComponent) {
        super(vertices, indices, currentWindow, createInitialTexture(mainGuiComponent));
        this.mainGUIComponent = mainGuiComponent;
        mainGUIComponent.setVisible(false);

        this.outline = false;
        this.material = new Material(
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector4f(0.0f, 0.0f, 0.0f, 1.0f),
                1.0f
        );
    }

    private static Texture createInitialTexture(GUIComponent component) {
        BufferedImage img = component.print();
        return Texture.loadTexture(img);
    }

    /**
     * Mark that the GUI needs to be re-rendered to texture
     */
    public void markDirty() {
        needsUpdate = true;
    }

    /**
     * Projects a 3D world position to 2D screen coordinates
     */
    @NonNull
    private Vector2f projectToScreen(@NonNull Vector3f worldPos, @NonNull Camera camera,@NonNull Matrix4f projectionMatrix, int screenWidth, int screenHeight) {
        // Create model matrix for this billboard
        Matrix4f modelMatrix = new Matrix4f()
                .identity()
                .translate(position)
                .rotateXYZ(rotation.x, rotation.y, rotation.z)
                .scale(scale);

        // Transform the local position by the model matrix
        Vector4f localPos = new Vector4f(worldPos.x, worldPos.y, worldPos.z, 1.0f);
        Vector4f worldPosition = modelMatrix.transform(localPos);

        // Create MVP matrix
        Matrix4f viewMatrix = camera.getViewMatrix();
        Matrix4f mvp = new Matrix4f(projectionMatrix).mul(viewMatrix);

        // Transform to clip space
        Vector4f clipSpace = mvp.transform(new Vector4f(worldPosition.x, worldPosition.y, worldPosition.z, 1.0f));

        // Perspective divide
        if (Math.abs(clipSpace.w) < 0.0001f) {
            return new Vector2f(-1, -1); // Behind camera or invalid
        }

        float ndcX = clipSpace.x / clipSpace.w;
        float ndcY = clipSpace.y / clipSpace.w;

        // Convert NDC [-1,1] to screen coordinates [0, width/height]
        float screenX = (ndcX + 1.0f) * 0.5f * screenWidth;
        float screenY = (1.0f - ndcY) * 0.5f * screenHeight; // Flip Y

        return new Vector2f(screenX, screenY);
    }

    /**
     * Calculate screen-space dimensions of the billboard and update GUI component size
     */
    private void updateScreenSpaceSize(Camera camera) {
        // Get projection matrix from window
        Matrix4f projectionMatrix = win.getProjectionMatrix();
        int screenWidth = win.getWidth();
        int screenHeight = win.getHeight();

        // Calculate the corners of the billboard in local space
        // Billboard is centered at origin with size 1x1, scaled by scale vector
        Vector3f topLeft = new Vector3f(-0.5f, 0.5f, 0.0f);
        Vector3f topRight = new Vector3f(0.5f, 0.5f, 0.0f);
        Vector3f bottomLeft = new Vector3f(-0.5f, -0.5f, 0.0f);

        // Project all corners to screen space
        Vector2f topLeftScreen = projectToScreen(topLeft, camera, projectionMatrix, screenWidth, screenHeight);
        Vector2f topRightScreen = projectToScreen(topRight, camera, projectionMatrix, screenWidth, screenHeight);
        Vector2f bottomLeftScreen = projectToScreen(bottomLeft, camera, projectionMatrix, screenWidth, screenHeight);

        // Calculate pixel dimensions
        float pixelWidth = Math.abs(topRightScreen.x - topLeftScreen.x);
        float pixelHeight = Math.abs(bottomLeftScreen.y - topLeftScreen.y);

        // Convert to percentage of screen dimensions
        float widthPercentage = pixelWidth / screenWidth;
        float heightPercentage = pixelHeight / screenHeight;

        // Clamp to reasonable values
        widthPercentage = Math.max(0.01f, Math.min(1.0f, widthPercentage));
        heightPercentage = Math.max(0.01f, Math.min(1.0f, heightPercentage));

        // Update GUI component size if it changed significantly
        float currentWidth = mainGUIComponent.getWidth();
        float currentHeight = mainGUIComponent.getHeight();

        float widthDiff = Math.abs(currentWidth - widthPercentage);
        float heightDiff = Math.abs(currentHeight - heightPercentage);

        // Only update if difference is significant (more than 1% change)
        if (widthDiff > 0.01f || heightDiff > 0.01f) {
            mainGUIComponent.setWidth(widthPercentage*1.5f);
            mainGUIComponent.setHeight(heightPercentage*1.5f);
            markDirty(); // Mark for texture update
        }
    }

    /**
     * Updates texture data directly without recreating the texture object
     */
    private void updateTextureData() {
        long currentTime = System.currentTimeMillis();

        if (!needsUpdate || (currentTime - lastUpdateTime) < UPDATE_INTERVAL_MS) {
            return;
        }

        BufferedImage image = mainGUIComponent.print();
        if (image == null) return;

        // Extract RGBA data
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        byte[] data = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            int row = height - 1 - y; // flip Y
            for (int x = 0; x < width; x++) {
                int pixel = pixels[row * width + x];
                int i = (y * width + x) * 4;
                data[i]     = (byte) ((pixel >> 16) & 0xFF); // R
                data[i + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
                data[i + 2] = (byte) (pixel & 0xFF);         // B
                data[i + 3] = (byte) ((pixel >> 24) & 0xFF); // A
            }
        }

        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
        buffer.put(data).flip();

        // Update existing texture - DO NOT create new onex
        texture.bind();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glGenerateMipmap(GL_TEXTURE_2D);
        texture.unbind();

        needsUpdate = false;
        lastUpdateTime = currentTime;
    }

    @Override
    public void render(Camera camera) {
        // Calculate and update screen-space size before rendering
        updateScreenSpaceSize(camera);
        mainGUIComponent.updateHitBox();
        // Update texture if needed
        updateTextureData();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        super.render(camera);

        glDepthMask(true);
        glEnable(GL_CULL_FACE);
    }

    @Override
    public void cleanUp() {
        if (texture != null) {
            texture.cleanup();
        }
        super.cleanUp();
    }
}