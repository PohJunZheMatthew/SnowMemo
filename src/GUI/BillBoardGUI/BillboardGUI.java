package GUI.BillBoardGUI;

import GUI.GUIComponent;
import Main.*;
import Main.Window;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.opengl.GL41.GL_RGB565;

public class BillboardGUI extends Mesh {
    private static final float[] vertices = {
            -0.5f, -0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    0.0f, 1.0f,
            0.5f, -0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    1.0f, 1.0f,
            0.5f,  0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    1.0f, 0.0f,
            -0.5f,  0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    0.0f, 0.0f
    };
    private static final int[] indices = {0, 1, 2, 2, 3, 0};

    public GUIComponent mainGUIComponent;
    private final int MAX_TEXTURE_SIZE = 32;
    private final int MIN_TEXTURE_SIZE = 16;
    private int currentTextureWidth = 0;
    private int currentTextureHeight = 0;
    private BufferedImage reusableImage = null;
    private int[] reusableIntPixels = null;
    private ByteBuffer reusableByteBuffer = null;
    private int reusableByteBufferCapacity = 0;
    private volatile boolean needsUpdate = true;
    private long lastTextureUpdateNanos = 0;
    private final long MIN_UPDATE_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(200);
    private final long SIZE_CHECK_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(200);
    private long lastSizeCheckNanos = 0;
    private final float SUPERSAMPLE = 0.25f;
    private final Matrix4f tmpModel = new Matrix4f();
    private final Matrix4f tmpViewProjection = new Matrix4f();
    private final Vector4f tmpClip = new Vector4f();
    private final Vector2f tmpScreen = new Vector2f();
    private final boolean USE_MIPMAPS = true;
    private Method isDirtyMethod = null;
    private Method printIntoMethod = null;

    public BillboardGUI(Window currentWindow, GUIComponent mainGuiComponent) {
        super(vertices, indices, currentWindow, createInitialTexture(mainGuiComponent, 64, 64));
        this.mainGUIComponent = mainGuiComponent;
        mainGuiComponent.setVisible(false);
        this.outline = false;
        this.material = new Material(
                new Vector4f(1, 1, 1, 1),
                new Vector4f(1, 1, 1, 1),
                new Vector4f(0, 0, 0, 1),
                1.0f
        );
        currentTextureWidth = 64;
        currentTextureHeight = 64;
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        try {
            isDirtyMethod = mainGuiComponent.getClass().getMethod("isDirty");
        } catch (Exception ignored) {}
        try {
            printIntoMethod = mainGuiComponent.getClass().getMethod("printInto", BufferedImage.class);
        } catch (Exception ignored) {}
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

    private boolean componentIsDirty() {
        if (isDirtyMethod != null) {
            try {
                Object r = isDirtyMethod.invoke(mainGUIComponent);
                if (r instanceof Boolean) return (Boolean) r;
            } catch (Exception ignored) {}
        }
        return needsUpdate;
    }

    private void updateAspectRatio() {
        BufferedImage img = mainGUIComponent.print();
        if (img != null) {
            float aspectRatio = (float) img.getWidth() / img.getHeight();
            float w = 1.0f;
            float h = w / aspectRatio;
            this.scale.set(w, h, 1.0f);
        }
    }

    private void updateScale() {}

    private boolean isOffscreen(Camera camera) {
        tmpModel.identity().translate(position).rotateXYZ(rotation.x, rotation.y, rotation.z).scale(scale);
        Vector4f worldCenter = tmpModel.transform(new Vector4f(0,0,0,1));
        tmpViewProjection.set(win.getProjectionMatrix()).mul(camera.getViewMatrix());
        tmpViewProjection.transform(worldCenter, tmpClip);
        if (Math.abs(tmpClip.w) < 1e-6f) return true;
        float ndcX = tmpClip.x / tmpClip.w;
        float ndcY = tmpClip.y / tmpClip.w;
        float ndcZ = tmpClip.z / tmpClip.w;
        float margin = 0.6f;
        return ndcZ < -1f || ndcX < -1f - margin || ndcX > 1f + margin || ndcY < -1f - margin || ndcY > 1f + margin;
    }

    private void computeTargetTextureSize(Camera camera, int[] outWH) {
        Matrix4f proj = win.getProjectionMatrix();
        int screenW = win.getWidth();
        int screenH = win.getHeight();
        tmpModel.identity().translate(position).rotateXYZ(rotation.x, rotation.y, rotation.z).scale(scale);
        Matrix4f mvp = new Matrix4f(proj).mul(camera.getViewMatrix()).mul(tmpModel);
        Vector4f pTL = new Vector4f(-0.5f, 0.5f, 0f, 1f).mul(mvp);
        Vector4f pTR = new Vector4f(0.5f, 0.5f, 0f, 1f).mul(mvp);
        Vector4f pBL = new Vector4f(-0.5f, -0.5f, 0f, 1f).mul(mvp);
        if (Math.abs(pTL.w) < 1e-5f || Math.abs(pTR.w) < 1e-5f || Math.abs(pBL.w) < 1e-5f) {
            outWH[0] = MIN_TEXTURE_SIZE;
            outWH[1] = MIN_TEXTURE_SIZE;
            return;
        }
        float ndcTLx = pTL.x / pTL.w;
        float ndcTLy = pTL.y / pTL.w;
        float ndcTRx = pTR.x / pTR.w;
        float ndcBLy = pBL.y / pBL.w;
        float screenTLx = (ndcTLx + 1f) * 0.5f * screenW;
        float screenTLy = (1f - ndcTLy) * 0.5f * screenH;
        float screenTRx = (ndcTRx + 1f) * 0.5f * screenW;
        float screenBLy = (1f - ndcBLy) * 0.5f * screenH;
        float pixelWidth = Math.abs(screenTRx - screenTLx);
        float pixelHeight = Math.abs(screenBLy - screenTLy);
        int targetW = Math.round(pixelWidth * SUPERSAMPLE);
        int targetH = Math.round(pixelHeight * SUPERSAMPLE);
        targetW = Math.max(MIN_TEXTURE_SIZE, Math.min(MAX_TEXTURE_SIZE, targetW));
        targetH = Math.max(MIN_TEXTURE_SIZE, Math.min(MAX_TEXTURE_SIZE, targetH));
        outWH[0] = targetW;
        outWH[1] = targetH;
    }

    private boolean shouldCheckSize() {
        long now = System.nanoTime();
        if (now - lastSizeCheckNanos >= SIZE_CHECK_INTERVAL_NANOS) {
            lastSizeCheckNanos = now;
            return true;
        }
        return false;
    }

    private void updateTextureData(Camera camera) {
        if (!mainGUIComponent.isVisible()) return;
        if (isOffscreen(camera)) return;
        long now = System.nanoTime();
        if (!componentIsDirty() && (now - lastTextureUpdateNanos) < MIN_UPDATE_INTERVAL_NANOS) return;
        int[] wh = new int[2];
        if (!shouldCheckSize() && currentTextureWidth > 0 && currentTextureHeight > 0) {
            wh[0] = currentTextureWidth;
            wh[1] = currentTextureHeight;
        } else {
            computeTargetTextureSize(camera, wh);
        }
        int width = wh[0];
        int height = wh[1];
        if (width <= 0 || height <= 0) return;
        if (reusableImage == null || reusableImage.getWidth() != width || reusableImage.getHeight() != height) {
            reusableImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            reusableIntPixels = new int[width * height];
            reusableByteBufferCapacity = Math.max(reusableByteBufferCapacity, width * height * 4);
            reusableByteBuffer = BufferUtils.createByteBuffer(reusableByteBufferCapacity);
            currentTextureWidth = 0;
            currentTextureHeight = 0;
        }
        BufferedImage rendered = null;
        if (printIntoMethod != null) {
            try {
                printIntoMethod.invoke(mainGUIComponent, reusableImage);
                rendered = reusableImage;
            } catch (Exception ignored) {}
        }
        if (rendered == null) {
            rendered = mainGUIComponent.print(width, height, 1.0f);
            if (rendered == null) return;
            if (rendered != reusableImage) {
                Graphics g = reusableImage.getGraphics();
                g.drawImage(rendered, 0, 0, null);
                g.dispose();
            }
        }
        reusableImage.getRGB(0, 0, width, height, reusableIntPixels, 0, width);
        int needed = width * height * 4;
        if (reusableByteBuffer.capacity() < needed) {
            reusableByteBuffer = BufferUtils.createByteBuffer(needed);
            reusableByteBufferCapacity = needed;
        }
        reusableByteBuffer.clear();
        for (int y = height - 1; y >= 0; y--) {
            int rowStart = y * width;
            for (int x = 0; x < width; x++) {
                int pixel = reusableIntPixels[rowStart + x];
                reusableByteBuffer.put((byte)((pixel >> 16) & 0xFF));
                reusableByteBuffer.put((byte)((pixel >> 8) & 0xFF));
                reusableByteBuffer.put((byte)(pixel & 0xFF));
                reusableByteBuffer.put((byte)((pixel >> 24) & 0xFF));
            }
        }
        reusableByteBuffer.flip();
        texture.bind();
        if (currentTextureWidth != width || currentTextureHeight != height) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, reusableByteBuffer);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, USE_MIPMAPS ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            currentTextureWidth = width;
            currentTextureHeight = height;
            if (USE_MIPMAPS) glGenerateMipmap(GL_TEXTURE_2D);
        } else {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, reusableByteBuffer);
            if (USE_MIPMAPS) glGenerateMipmap(GL_TEXTURE_2D);
        }
        texture.unbind();
        needsUpdate = false;
        lastTextureUpdateNanos = now;
    }

    @Override
    public void render(Camera camera) {
        long befTime = System.nanoTime();
        updateAspectRatio();
        updateScale();
        if (mainGUIComponent.isVisible()) mainGUIComponent.updateHitBox();
        updateTextureData(camera);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        super.render(camera);
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        long took = System.nanoTime() - befTime;
        System.out.println(TimeUnit.NANOSECONDS.toMillis(took));
    }

    @Override
    public void cleanUp() {
        if (texture != null) texture.cleanup();
        super.cleanUp();
    }
}
