package Main;

import GUI.GUIComponent;
import Main.Node.Node;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static Main.SnowMemo.camera;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Window {
    public Camera camera;
    protected long window;
    protected int width,height;
    boolean vSync = true;
    public static final float FOV = (float) Math.toRadians(60f);
    static Window currentWindow;
    public static final float Z_NEAR = 0.01f;

    public static final float Z_FAR = 100.f;

    protected Matrix4f projectionMatrix;
    public final List<GLFWCursorPosCallback> CursorPosCallbacks = new ArrayList<GLFWCursorPosCallback>();
    public final List<GLFWMouseButtonCallback> MouseButtonCallbacks = new ArrayList<>();
    public final List<GLFWKeyCallback> KeyCallbacks = new ArrayList<>();
    public final List<GLFWCharCallback> CharCallbacks = new ArrayList<>();
    public final List<GLFWDropCallback> DropCallbacks = new ArrayList<>();
    public final List<GLFWScrollCallback> ScrollCallbacks = new ArrayList<>();
    protected double mouseX;
    protected double mouseY;
    public double getMouseX() { return mouseX; }
    public double getMouseY() { return mouseY; }

    public Window(){}
    public Window(String title){
        camera = SnowMemo.camera;
        currentWindow = this;
        if (!GLFW.glfwInit()) throw new RuntimeException("Can't init glfw");
        long monitor =GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            throw new IllegalStateException("No primary monitor found");
        }
        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
        if (vidMode == null) {
            throw new IllegalStateException("Failed to get video mode");
        }
        int w = vidMode.width()/2;
        int h = vidMode.height()/2;
        this.width = w;
        this.height = h;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GL11.GL_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GL11.GL_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_CLASS_NAME, "Snow Memo");
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_INSTANCE_NAME, "Snow Memo");
        GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, 24);
        boolean maximised = false;
        if (w == 0 || h == 0) {
            width = 100;
            height = 100;
            GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, GLFW.GLFW_TRUE);
            maximised = true;
        }
        window = GLFW.glfwCreateWindow(w, h, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) throw new RuntimeException("Failed to create window");
        GLFW.glfwSetWindowSizeLimits(window,w/2,h/2,GLFW.GLFW_DONT_CARE,GLFW.GLFW_DONT_CARE);
        GLFW.glfwSetFramebufferSizeCallback(window, ((window, width, height) -> {
            this.width = width;
            this.height = height;
        }));
        if (maximised) GLFW.glfwMaximizeWindow(window);
        else {
            GLFW.glfwSetWindowPos(window, (vidMode.width() - w) / 2, (vidMode.height() - h) / 2);
            GLFW.glfwSetWindowAspectRatio(window,vidMode.width(),vidMode.height());
        }
        GLFW.glfwMakeContextCurrent(window);
        if (isvSync()) GLFW.glfwSwapInterval(1);
        IntBuffer fbWidth = MemoryUtil.memAllocInt(1);
        IntBuffer fbHeight = MemoryUtil.memAllocInt(1);
        GLFW.glfwGetFramebufferSize(window, fbWidth, fbHeight);
        this.width = fbWidth.get(0);
        this.height = fbHeight.get(0);
        MemoryUtil.memFree(fbWidth);
        MemoryUtil.memFree(fbHeight);
        GL.createCapabilities();

        glClearColor(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL_CULL_FACE);
        float aspectRatio = (float) width / height;
        projectionMatrix = new Matrix4f().perspective(FOV, aspectRatio,
                Z_NEAR, Z_FAR);

        // Set up the callback forwarders that will call registered callbacks
        GLFW.glfwSetCursorPosCallback(window,((windowHandle, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
            // Forward to all registered callbacks
            CursorPosCallbacks.forEach(c -> {
                try {
                    c.invoke(windowHandle, xpos, ypos);
                } catch (Exception e) {
                    System.err.println("Error in cursor position callback: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }));

        GLFW.glfwSetMouseButtonCallback(window,(long windowHandle, int button, int action, int mods) -> {
            // Forward to all registered callbacks
            for (GLFWMouseButtonCallback callback : MouseButtonCallbacks) {
                try {
                    callback.invoke(windowHandle, button, action, mods);
                } catch (Exception e) {
                    System.err.println("Error in mouse button callback: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        GLFW.glfwSetKeyCallback(window, (long windowHandle, int key, int scancode, int action, int mods) -> {
            for (GLFWKeyCallback callback : KeyCallbacks) {
                try {
                    callback.invoke(windowHandle, key, scancode, action, mods);
                } catch (Exception e) {
                    System.err.println("Error in key callback: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        GLFW.glfwSetCharCallback(window, (long windowHandle, int codepoint) -> {
            for (GLFWCharCallback callback : CharCallbacks) {
                try {
                    callback.invoke(windowHandle, codepoint);
                } catch (Exception e) {
                    System.err.println("Error in char callback: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        GLFW.glfwSetDropCallback(window,(long windowHandle, int count, long names)->{
            for (GLFWDropCallback callback: DropCallbacks) {
                callback.invoke(windowHandle,count,names);
            }
        });
        GLFW.glfwSetScrollCallback(window, (long windowHandle, double xoffset, double yoffset) -> {
            for (GLFWScrollCallback callback : ScrollCallbacks) {
                try {
                    callback.invoke(windowHandle, xoffset, yoffset);
                } catch (Exception e) {
                    System.err.println("Error in scroll callback: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        System.out.println("OpenGL version: " + GL11.glGetString(GL11.GL_VERSION));
        System.out.println("Renderer: " + GL11.glGetString(GL11.GL_RENDERER));
        System.out.println("GLSL version: " + GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));

        System.out.println("Window created with handle: " + window);
    }

    public void show(){
        GLFW.glfwShowWindow(window);
    }

    public boolean isvSync() {
        return vSync;
    }

    public void render(List<Renderable> meshes){
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW); // or GL_CW depending on your vertex order
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwPollEvents();
        GL11.glViewport(0, 0, width, height);
        float aspectRatio1 = (float) width / height;
        projectionMatrix = new Matrix4f().perspective(FOV, aspectRatio1, Z_NEAR, Z_FAR);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        Frustum frustum = new Frustum();
        Matrix4f projView = new Matrix4f(projectionMatrix)
                .mul(camera.getViewMatrix());
        frustum.update(projView);
        for (Renderable r : meshes) {
            if (r instanceof Mesh mesh) {
                mesh.updateCulling(frustum);
            }
        }
        for (Node node:Node.getAllNodes(this)){
            node.updateCulling(frustum);
        }
        for (Renderable r : meshes) {
            if (r instanceof Mesh mesh) {
                mesh.beginOcclusionQuery(camera);
            }
        }
        for (Node node:Node.getAllNodes(this)){
            node.beginOcclusionQuery(camera);
        }
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glDepthMask(true);        // allow writes
        glDisable(GL_BLEND);      // ensure blending isn't on
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        for (Renderable m:meshes) {
            if (m instanceof Mesh) ((Mesh) m).render(camera); else m.render();
        }
        for (Node node:Node.getAllNodes(this)){
            node.render();
        }
        GUIComponent.renderGUIs(this);
        GLFW.glfwSwapBuffers(window);
    }

    public void close(){
        // Clean up GUI components for this window
        GUIComponent.cleanupWindow(this);

        Callbacks.glfwFreeCallbacks(window);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwSetWindowShouldClose(window,true);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }

    public Matrix4f getProjectionMatrix(){
        return projectionMatrix;
    }

    public static Window getCurrentWindow(){
        return currentWindow;
    }

    public int getWidth() {
        return width;
    }

    public Window setWidth(int width) {
        this.width = width;
        return this;
    }

    public int getHeight() {
        return height;
    }

    public Window setHeight(int height) {
        this.height = height;
        return this;
    }

    @Deprecated(since="6.7")
    public Long getWindow(){
        return window;
    }

    /**
     * Get the GLFW window handle for this window.
     * This is used for event handling and window-specific operations.
     */
    public long getWindowHandle(){
        return window;
    }
    public Vector3f getCursorWorldPos(){
        // --- 1) Get window size (logical window coords) and framebuffer size (physical pixels)
        int[] winW = new int[1], winH = new int[1];
        int[] fbW  = new int[1], fbH  = new int[1];
        glfwGetWindowSize(window, winW, winH);
        glfwGetFramebufferSize(window, fbW, fbH);

        // Guard against divide by zero
        if (winW[0] == 0 || winH[0] == 0) return new Vector3f(0, 0, 0);

        /* Convert cursor (window coords) -> frame buffer coords (handles HiDPI) */
        float scaleX = (float) fbW[0] / (float) winW[0];
        float scaleY = (float) fbH[0] / (float) winH[0];
        float fbX = (float) mouseX * scaleX;
        float fbY = (float) mouseY * scaleY;

        // --- 2) NDC (-1..1)
        float ndcX = (2.0f * fbX) / (float) fbW[0] - 1.0f;
        float ndcY = 1.0f - (2.0f * fbY) / (float) fbH[0]; // flip Y for OpenGL

        // --- 3) Clip-space positions for near & far
        Vector4f clipNear = new Vector4f(ndcX, ndcY, -1f, 1f);
        Vector4f clipFar  = new Vector4f(ndcX, ndcY,  1f, 1f);

        // --- 4) Build inverse view-projection
        Matrix4f projectionMatrix = getProjectionMatrix();
        Matrix4f viewMatrix = camera.getViewMatrix(); // should be the camera view matrix
        Matrix4f invViewProj = new Matrix4f(projectionMatrix).mul(viewMatrix).invert();

        // --- 5) Transform clip -> world (and perspective divide)
        Vector4f worldNear4 = new Vector4f(clipNear).mul(invViewProj);
        Vector4f worldFar4  = new Vector4f(clipFar).mul(invViewProj);

        if (worldNear4.w != 0f) worldNear4.div(worldNear4.w);
        if (worldFar4.w  != 0f) worldFar4.div(worldFar4.w);

        Vector3f rayStart = new Vector3f(worldNear4.x, worldNear4.y, worldNear4.z);
        Vector3f rayEnd   = new Vector3f(worldFar4.x,  worldFar4.y,  worldFar4.z);

        Vector3f rayDirection = new Vector3f(rayEnd).sub(rayStart);
        if (rayDirection.lengthSquared() == 0f) return new Vector3f(rayStart);

        rayDirection.normalize();

        // --- 6) Intersect with ground plane z = 0 (plane normal (0,0,1))
        // For plane z=0 you can use simplified formula:
        // t = (0 - rayStart.z) / rayDirection.z
        float denom = rayDirection.z;
        if (Math.abs(denom) < 1e-6f) {
            // Ray parallel to plane → return projection of start onto plane (or some fallback)
            return new Vector3f(rayStart.x, rayStart.y, 0f);
        }

        float t = -rayStart.z / denom;
        if (t < 0f) {
            // Intersection is behind the camera; choose fallback (here we project start)
            return new Vector3f(rayStart.x, rayStart.y, 0f);
        }

        Vector3f worldPosition = new Vector3f(rayStart).add(new Vector3f(rayDirection).mul(t));
        // NOTE: do NOT multiply coordinates by 2 here — that caused the offset/scale.
        return worldPosition;
    }

    public float getAspectRatio() {
        return width/height;
    }
}