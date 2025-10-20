package GUI.BillBoardGUI;

import GUI.GUIComponent;
import Main.*;
import Main.Window;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

public class BillboardGUI extends Mesh {
    private static final float[] vertices = {
            -0.5f, -0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    0.0f, 1.0f,
            0.5f, -0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    1.0f, 1.0f,
            0.5f,  0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    1.0f, 0.0f,
            -0.5f,  0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    0.0f, 0.0f
    };
    private static final int[] indices = {0, 1, 2, 2, 3, 0};

    public GUIComponent mainGUIComponent;
    private int textureWidth = 512;
    private int textureHeight = 512;
    private int currentTextureWidth = 512;
    private int currentTextureHeight = 512;
    private boolean needsUpdate = true;
    private float aspectRatio = 1f;
    private final int MAX_TEXTURE_SIZE = 4096;  // Increased for better quality
    private float resolution = 1f;
    private final float SUPERSAMPLE = 2.0f;  // Render at 2x for crisp text

    public BillboardGUI(Window currentWindow, GUIComponent mainGuiComponent) {
        super(vertices, indices, currentWindow, createInitialTexture(mainGuiComponent, 512, 512));
        this.mainGUIComponent = mainGuiComponent;
        mainGuiComponent.setVisible(false);
        this.outline = false;
        this.material = new Material(
                new Vector4f(1, 1, 1, 1),
                new Vector4f(1, 1, 1, 1),
                new Vector4f(0, 0, 0, 1),
                1.0f
        );
        updateAspectRatio();
        updateScale();
    }

    private static Texture createInitialTexture(GUIComponent component, int width, int height) {
        BufferedImage img = component.print(width, height);
        return Texture.loadTexture(img);
    }

    public void markDirty() {
        needsUpdate = true;
    }

    private void updateAspectRatio() {
        BufferedImage img = mainGUIComponent.print();
        if (img != null) {
            aspectRatio = (float) img.getWidth() / img.getHeight();
        }
    }

    private void updateScale() {
        float w = 1.0f;
        float h = w / aspectRatio;
        this.scale.set(w, h, 1.0f);
    }

    private void updateTextureData() {
        if (!needsUpdate) return;

        BufferedImage image = mainGUIComponent.print(textureWidth, textureHeight, resolution);
        if (image == null) return;

        int width = image.getWidth();
        int height = image.getHeight();
        
        // Check if texture size changed using our tracking variables
        boolean sizeChanged = (currentTextureWidth != width || currentTextureHeight != height);

        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        byte[] data = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            int row = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int pixel = pixels[row * width + x];
                int i = (y * width + x) * 4;
                data[i]     = (byte) ((pixel >> 16) & 0xFF);
                data[i + 1] = (byte) ((pixel >> 8) & 0xFF);
                data[i + 2] = (byte) (pixel & 0xFF);
                data[i + 3] = (byte) ((pixel >> 24) & 0xFF);
            }
        }

        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
        buffer.put(data).flip();

        texture.bind();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        if (sizeChanged) {
            // Recreate the texture with new size
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            // Update tracking variables
            currentTextureWidth = width;
            currentTextureHeight = height;
        } else {
            // Just update existing texture
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        }

        glGenerateMipmap(GL_TEXTURE_2D);
        texture.unbind();

        needsUpdate = false;
    }

    private Vector2f projectToScreen(Vector3f worldPos, Camera camera, Matrix4f projectionMatrix, int screenWidth, int screenHeight) {
        Matrix4f modelMatrix = new Matrix4f().identity().translate(position).rotateXYZ(rotation.x, rotation.y, rotation.z).scale(scale);
        Vector4f worldPosition = modelMatrix.transform(new Vector4f(worldPos, 1.0f));
        Matrix4f viewMatrix = camera.getViewMatrix();
        Vector4f clipSpace = new Matrix4f(projectionMatrix).mul(viewMatrix).transform(worldPosition);

        if (Math.abs(clipSpace.w) < 0.0001f) return new Vector2f(-1, -1);

        float ndcX = clipSpace.x / clipSpace.w;
        float ndcY = clipSpace.y / clipSpace.w;

        float screenX = (ndcX + 1f) * 0.5f * screenWidth;
        float screenY = (1f - ndcY) * 0.5f * screenHeight;

        return new Vector2f(screenX, screenY);
    }

    private void updateScreenSpaceSize(Camera camera) {
        Matrix4f projectionMatrix = win.getProjectionMatrix();
        int screenWidth = win.getWidth();
        int screenHeight = win.getHeight();

        Vector3f topLeft = new Vector3f(-0.5f, 0.5f, 0);
        Vector3f topRight = new Vector3f(0.5f, 0.5f, 0);
        Vector3f bottomLeft = new Vector3f(-0.5f, -0.5f, 0);

        Vector2f topLeftScreen = projectToScreen(topLeft, camera, projectionMatrix, screenWidth, screenHeight);
        Vector2f topRightScreen = projectToScreen(topRight, camera, projectionMatrix, screenWidth, screenHeight);
        Vector2f bottomLeftScreen = projectToScreen(bottomLeft, camera, projectionMatrix, screenWidth, screenHeight);

        float pixelWidth = Math.abs(topRightScreen.x - topLeftScreen.x);
        float pixelHeight = Math.abs(bottomLeftScreen.y - topLeftScreen.y);

        // Apply supersampling for crisp text
        int targetWidth = Math.round(pixelWidth * SUPERSAMPLE);
        int targetHeight = Math.round(pixelHeight * SUPERSAMPLE);

        // Clamp to reasonable bounds
        targetWidth = Math.max(256, Math.min(MAX_TEXTURE_SIZE, targetWidth));
        targetHeight = Math.max(256, Math.min(MAX_TEXTURE_SIZE, targetHeight));

        // Only update if significantly different (avoid constant regeneration)
        int threshold = 100; // pixels
        if (Math.abs(currentTextureWidth - targetWidth) > threshold ||
                Math.abs(currentTextureHeight - targetHeight) > threshold) {

            textureWidth = targetWidth;
            textureHeight = targetHeight;
            markDirty();
        }
    }

    @Override
    public void render(Camera camera) {
        updateAspectRatio();
        updateScale();
        updateScreenSpaceSize(camera);
        mainGUIComponent.updateHitBox();
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
        if (texture != null) texture.cleanup();
        super.cleanUp();
    }
}