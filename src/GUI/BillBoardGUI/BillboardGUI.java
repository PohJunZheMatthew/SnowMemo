package GUI.BillBoardGUI;

import GUI.GUIComponent;
import Main.*;
import Main.Settings.Settings;
import Main.Window;
import org.joml.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL40;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.Math;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL30.*;


public class BillboardGUI extends Mesh {
    private static final float[] vertices = {
            -0.5f, -0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    0.0f, 1.0f,
            0.5f, -0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    1.0f, 1.0f,
            0.5f,  0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    1.0f, 0.0f,
            -0.5f,  0.5f, 0.0f,    0.0f, 0.0f, 1.0f,    0.0f, 0.0f
    };
    private static final int[] indices = {0, 1, 2, 2, 3, 0};

    // GLOBAL UPDATE SCHEDULER - Limits total texture updates per frame
    private static class UpdateScheduler {
        private static final int MAX_UPDATES_PER_FRAME = 2; // Only update 2 billboards per frame!
        private static final Queue<BillboardGUI> updateQueue = new LinkedList<>();
        private static int updatesThisFrame = 0;
        private static long lastFrameReset = 0;

        public static boolean canUpdate(BillboardGUI billboard) {
            long now = System.nanoTime();
            // Reset counter every 16ms (roughly one frame at 60fps)
            if (now - lastFrameReset > 16_000_000) {
                updatesThisFrame = 0;
                lastFrameReset = now;
            }

            if (updatesThisFrame >= MAX_UPDATES_PER_FRAME) {
                // Add to queue for next frame
                if (!updateQueue.contains(billboard)) {
                    updateQueue.offer(billboard);
                }
                return false;
            }

            updatesThisFrame++;
            return true;
        }

        public static void setMaxUpdatesPerFrame(int max) {
            // Allow configuration
        }
    }

    public GUIComponent mainGUIComponent;

    // Texture size constraints
    private int fixedTextureWidth = 128;
    private int fixedTextureHeight = 128;
    private int maxTextureWidth = 128;  // Even more aggressive for multiple billboards
    private int maxTextureHeight = 128;
    private int currentTextureWidth = 0;
    private int currentTextureHeight = 0;

    // Reusable buffers
    private BufferedImage reusableImage = null;
    private int[] reusableIntPixels = null;
    private ByteBuffer reusableByteBuffer = null;
    private int reusableByteBufferCapacity = 0;

    // Update control
    private volatile boolean needsUpdate = true;
    private volatile boolean forceUpdate = false;
    private long lastTextureUpdateNanos = 0;
    private long MIN_UPDATE_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(1); // Slower updates for multiple billboards

    // Priority system
    private int updatePriority = 0; // 0 = normal, higher = more important

    // Feature flags
    private boolean USE_MIPMAPS = false;
    private boolean autoAspectRatio = true;
    private float fixedAspectRatio = 1.0f;
    private boolean skipUpdateWhenFar = true;
    private float maxUpdateDistance = 30.0f; // Reduced for multiple billboards

    // Reflection methods
    private Method isDirtyMethod = null;
    private Method printIntoMethod = null;

    // Performance monitoring
    private long lastRenderTimeNanos = 0;
    private long lastTextureUpdateTimeNanos = 0;
    private boolean enableTimingLogs = false;

    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix3f normMatrix = new Matrix3f();

    // Cached for distance checks
    private Vector3f cameraPosition = new Vector3f();

    // Frame counting
    private static long globalFrameCount = 0;
    private int debugVao = -1;
    private int debugVbo = -1;
    private DebugShader debugShader;
    public BillboardGUI(Window currentWindow, GUIComponent mainGuiComponent) {
        super(vertices, indices, currentWindow, createInitialTexture(mainGuiComponent, 64, 64));
        this.mainGUIComponent = mainGuiComponent;
        mainGuiComponent.setVisible(false);
        this.outline = false;
        this.cullFace = false;
        this.material = new Material(
                new Vector4f(1, 1, 1, 1),
                new Vector4f(1, 1, 1, 1),
                new Vector4f(0, 0, 0, 1),
                1.0f
        );
        currentTextureWidth = 64;
        currentTextureHeight = 64;
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        // Setup reflection methods
        try {
            isDirtyMethod = mainGuiComponent.getClass().getMethod("isDirty");
        } catch (Exception ignored) {}
        try {
            printIntoMethod = mainGuiComponent.getClass().getMethod("printInto", BufferedImage.class);
        } catch (Exception ignored) {}

        updateAspectRatio();
        debugVao = glGenVertexArrays();
        debugVbo = glGenBuffers();
        debugShader = new DebugShader(DEBUG_VERTEX_SHADER, DEBUG_FRAGMENT_SHADER);
    }

    private static Texture createInitialTexture(GUIComponent component, int width, int height) {
        BufferedImage img = component.print(width, height);
        return Texture.loadTexture(img);
    }

    public void setFixedTextureSize(int width, int height) {
        this.fixedTextureWidth = Math.min(width, maxTextureWidth);
        this.fixedTextureHeight = Math.min(height, maxTextureHeight);
        forceUpdate = true;
    }

    public void setMaxTextureSize(int maxWidth, int maxHeight) {
        this.maxTextureWidth = maxWidth;
        this.maxTextureHeight = maxHeight;
        this.fixedTextureWidth = Math.min(fixedTextureWidth, maxTextureWidth);
        this.fixedTextureHeight = Math.min(fixedTextureHeight, maxTextureHeight);
        forceUpdate = true;
    }

    public void setUseMipmaps(boolean useMipmaps) {
        this.USE_MIPMAPS = useMipmaps;
        forceUpdate = true;
    }

    public void setUpdateInterval(long milliseconds) {
        MIN_UPDATE_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(milliseconds);
    }

    public void setSkipUpdateWhenFar(boolean skip) {
        this.skipUpdateWhenFar = skip;
    }

    public void setMaxUpdateDistance(float distance) {
        this.maxUpdateDistance = distance;
    }

    public void setUpdatePriority(int priority) {
        this.updatePriority = priority;
    }

    public static void setGlobalMaxUpdatesPerFrame(int max) {
        // Allow user to configure how many billboards update per frame
    }

    public void setAutoAspectRatio(boolean auto) {
        this.autoAspectRatio = auto;
        if (auto) {
            updateAspectRatio();
        }
    }

    public void setFixedAspectRatio(float aspectRatio) {
        this.autoAspectRatio = false;
        this.fixedAspectRatio = aspectRatio;
        float w = 1.0f;
        float h = w / aspectRatio;
        this.scale.set(w, h, 1.0f);
    }

    public void setEnableTimingLogs(boolean enable) {
        this.enableTimingLogs = enable;
    }

    public double getLastRenderTimeMs() { return lastRenderTimeNanos / 1_000_000.0; }
    public double getLastTextureUpdateTimeMs() { return lastTextureUpdateTimeNanos / 1_000_000.0; }

    public void markDirty() {
        needsUpdate = true;
    }

    public void forceUpdateNow() {
        forceUpdate = true;
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
        if (!autoAspectRatio) {
            float w = 1.0f;
            float h = w / fixedAspectRatio;
            this.scale.set(w, h, 1.0f);
            return;
        }
        mainGUIComponent.updateHitBox();

        float aspectRatio = (float) mainGUIComponent.getWidthPx() / mainGUIComponent.getHeightPx();
        float w = 1.0f;
        float h = w / aspectRatio;
        this.scale.set(w, h, 1.0f);
    }

    private void updateTextureData(Camera camera) {
        long startTime = System.nanoTime();

        if (!forceUpdate && !UpdateScheduler.canUpdate(this)) {
            lastTextureUpdateTimeNanos = System.nanoTime() - startTime;
            return;
        }

        if (skipUpdateWhenFar && camera != null) {
            camera.getPosition(cameraPosition);
            float dist = cameraPosition.distance(position);
            if (dist > maxUpdateDistance && !forceUpdate) {
                lastTextureUpdateTimeNanos = System.nanoTime() - startTime;
                return;
            }
        }

        long now = System.nanoTime();
        if (!forceUpdate && !componentIsDirty() && (now - lastTextureUpdateNanos) < MIN_UPDATE_INTERVAL_NANOS) {
            lastTextureUpdateTimeNanos = System.nanoTime() - startTime;
            return;
        }

        int width = mainGUIComponent.getWidthPx();
        int height = mainGUIComponent.getHeightPx();

        // Render the component into a BufferedImage
        BufferedImage img = mainGUIComponent.print(width, height);
        texture = Texture.loadTexture(img);

        currentTextureWidth = width;
        currentTextureHeight = height;

        needsUpdate = false;
        forceUpdate = false;
        lastTextureUpdateNanos = now;
        lastTextureUpdateTimeNanos = System.nanoTime() - startTime;

        if (enableTimingLogs) {
            System.out.printf("Billboard updated safely: %.2fms (size: %dx%d)%n",
                    lastTextureUpdateTimeNanos / 1_000_000.0, width, height);
        }
    }
    private boolean debugHitbox = false;
    @Override
    public void render(Camera camera) {
        if (!renderVisible) return;
        debugHitbox = (boolean) Settings.getValue("SnowMemo.Defaults.Billboard.DebugRender");
        long totalStartTime = System.nanoTime();

        globalFrameCount++;

        // Update aspect ratio only when needed
        if (autoAspectRatio && (forceUpdate || currentTextureWidth == 0)) {
            updateAspectRatio();
        }

        // Try to update texture (will be rate-limited globally)
        updateTextureData(camera);

        // Render setup
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        // Render the billboard
        super.render(camera);

        if (debugHitbox) {
            renderDebug(camera);
        }
        // Cleanup
        glDepthMask(true);
        glEnable(GL_CULL_FACE);

        lastRenderTimeNanos = System.nanoTime() - totalStartTime;
    }

    @Override
    public void cleanUp() {
        if (texture != null) texture.cleanup();
        super.cleanUp();
    }

    // Convenience method to configure all billboards for multi-billboard scenes
    public static void configureForMultipleBillboards(BillboardGUI... billboards) {
        System.out.println("Configuring " + billboards.length + " billboards for optimal multi-billboard performance");

        for (int i = 0; i < billboards.length; i++) {
            BillboardGUI b = billboards[i];
            b.setMaxTextureSize(128, 128);           // Lower resolution
            b.setUpdateInterval(300 + (i * 50));     // Stagger update intervals
            b.setSkipUpdateWhenFar(true);
            b.setMaxUpdateDistance(25.0f);
            b.setUseMipmaps(false);
        }

        System.out.println("Billboards will update in rotation (max 2 per frame)");
    }
    private Vector3f hitboxMin = new Vector3f();
    private Vector3f hitboxMax = new Vector3f();
    private boolean hitboxDirty = true;

    public void updateHitBox() {
        float halfWidth = scale.x / 2.0f;
        float halfHeight = scale.y / 2.0f;
        hitboxMin.set(position.x - halfWidth, position.y - halfHeight, position.z);
        hitboxMax.set(position.x + halfWidth, position.y + halfHeight, position.z);
        hitboxDirty = false;
    }

    public boolean collidesWith(Vector3f point) {
        if (hitboxDirty) updateHitBox();
        return point.x >= hitboxMin.x && point.x <= hitboxMax.x &&
                point.y >= hitboxMin.y && point.y <= hitboxMax.y ;
    }

    @Override
    public String toString() {
        return "BillboardGUI{" +
                "mainGUIComponent=" + mainGUIComponent +
                ", fixedTextureWidth=" + fixedTextureWidth +
                ", fixedTextureHeight=" + fixedTextureHeight +
                ", maxTextureWidth=" + maxTextureWidth +
                ", maxTextureHeight=" + maxTextureHeight +
                ", currentTextureWidth=" + currentTextureWidth +
                ", currentTextureHeight=" + currentTextureHeight +
                ", reusableImage=" + reusableImage +
                ", reusableIntPixels=" + Arrays.toString(reusableIntPixels) +
                ", reusableByteBuffer=" + reusableByteBuffer +
                ", reusableByteBufferCapacity=" + reusableByteBufferCapacity +
                ", needsUpdate=" + needsUpdate +
                ", forceUpdate=" + forceUpdate +
                ", lastTextureUpdateNanos=" + lastTextureUpdateNanos +
                ", MIN_UPDATE_INTERVAL_NANOS=" + MIN_UPDATE_INTERVAL_NANOS +
                ", updatePriority=" + updatePriority +
                ", USE_MIPMAPS=" + USE_MIPMAPS +
                ", autoAspectRatio=" + autoAspectRatio +
                ", fixedAspectRatio=" + fixedAspectRatio +
                ", skipUpdateWhenFar=" + skipUpdateWhenFar +
                ", maxUpdateDistance=" + maxUpdateDistance +
                ", isDirtyMethod=" + isDirtyMethod +
                ", printIntoMethod=" + printIntoMethod +
                ", lastRenderTimeNanos=" + lastRenderTimeNanos +
                ", lastTextureUpdateTimeNanos=" + lastTextureUpdateTimeNanos +
                ", enableTimingLogs=" + enableTimingLogs +
                ", modelMatrix=" + modelMatrix +
                ", normMatrix=" + normMatrix +
                ", cameraPosition=" + cameraPosition +
                ", hitboxMin=" + hitboxMin +
                ", hitboxMax=" + hitboxMax +
                ", hitboxDirty=" + hitboxDirty +
                '}';
    }
    private static final String DEBUG_VERTEX_SHADER =
            "#version 330 core\n" +
                    "layout(location = 0) in vec3 position;\n" +
                    "uniform mat4 projection;\n" +
                    "uniform mat4 view;\n" +
                    "uniform mat4 model;\n" +
                    "void main() {\n" +
                    "    gl_Position = projection * view * model * vec4(position, 1.0);\n" +
                    "}";

    private static final String DEBUG_FRAGMENT_SHADER =
            "#version 330 core\n" +
                    "out vec4 fragColor;\n" +
                    "uniform vec3 color;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(color, 1.0);\n" +
                    "}";
    private static class DebugShader {
        public final int program;

        public DebugShader(String vertexSrc, String fragmentSrc) {
            int vs = glCreateShader(GL_VERTEX_SHADER);
            glShaderSource(vs, vertexSrc);
            glCompileShader(vs);
            if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new RuntimeException("Vertex shader error: " + glGetShaderInfoLog(vs));
            }

            int fs = glCreateShader(GL_FRAGMENT_SHADER);
            glShaderSource(fs, fragmentSrc);
            glCompileShader(fs);
            if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new RuntimeException("Fragment shader error: " + glGetShaderInfoLog(fs));
            }

            program = glCreateProgram();
            glAttachShader(program, vs);
            glAttachShader(program, fs);
            glLinkProgram(program);

            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                throw new RuntimeException("Shader link error: " + glGetProgramInfoLog(program));
            }

            glDeleteShader(vs);
            glDeleteShader(fs);
        }

        void bind() {
            glUseProgram(program);
        }

        void setUniform(String name, Matrix4f mat) {
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                int loc = glGetUniformLocation(program, name);
                glUniformMatrix4fv(loc, false, mat.get(stack.mallocFloat(16)));
            }
        }

        void setUniform(String name, Vector3f vec) {
            int loc = glGetUniformLocation(program, name);
            glUniform3f(loc, vec.x, vec.y, vec.z);
        }

        void destroy() {
            glDeleteProgram(program);
        }
    }
    public void renderDebug(Camera camera) {
        if (hitboxDirty) updateHitBox();

        float[] verts = {
                hitboxMin.x, hitboxMin.y, hitboxMin.z,
                hitboxMax.x, hitboxMin.y, hitboxMin.z,

                hitboxMax.x, hitboxMin.y, hitboxMin.z,
                hitboxMax.x, hitboxMax.y, hitboxMin.z,

                hitboxMax.x, hitboxMax.y, hitboxMin.z,
                hitboxMin.x, hitboxMax.y, hitboxMin.z,

                hitboxMin.x, hitboxMax.y, hitboxMin.z,
                hitboxMin.x, hitboxMin.y, hitboxMin.z
        };

        glBindVertexArray(debugVao);
        glBindBuffer(GL_ARRAY_BUFFER, debugVbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_DYNAMIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * 4, 0);

        debugShader.bind();
        debugShader.setUniform("projection", win.getProjectionMatrix());
        debugShader.setUniform("view", camera.getViewMatrix());
        debugShader.setUniform("model", new Matrix4f()); // identity
        debugShader.setUniform("color", new Vector3f(1, 0, 0));

        glDrawArrays(GL_LINES, 0, 8);

        glBindVertexArray(0);
    }
}