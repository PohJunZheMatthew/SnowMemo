package GUI;

import GUI.Events.*;
import GUI.Events.Event;
import Main.*;
import Main.Window;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.Timer;

import static java.lang.Thread.sleep;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public abstract class GUIComponent implements Renderable {
    protected int widthPx = 1, heightPx = 1;
    protected int xPx = 1, yPx = 1;
    protected float x, y;
    protected float width, height;
    protected Texture texture;
    protected ShaderProgram shaderProgram;
    protected final int vaoId, vboId, idxVboId;
    protected boolean CustomMouseEvents = false;
    private static final float[] vertices = {
            0.0f, 0.0f, 0.0f,       0.0f, 1.0f,  // U: 1.0 -> 0.0
            1.0f, 0.0f, 0.0f,       1.0f, 1.0f,  // U: 0.0 -> 1.0
            1.0f, 1.0f, 0.0f,       1.0f, 0.0f,  // U: 0.0 -> 1.0
            0.0f, 1.0f, 0.0f,       0.0f, 0.0f   // U: 1.0 -> 0.0
    };
    private static final int[] indices = {
            0, 3, 2,
            2, 1, 0
    };
    protected final Window windowParent;
    protected final List<EventCallBack<? extends Event>> callBacks = new ArrayList<>();
    private final static HashMap<Window, HashMap<Integer, GUIComponent>> GUIComponents = new HashMap<>();
    protected GUIComponent parent;
    protected final List<GUIComponent> children = new ArrayList<GUIComponent>();

    // Track which windows have been initialized to prevent duplicate callbacks
    private final static Set<Window> initializedWindows = new HashSet<>();
    private final static HashMap<Window, Point2D> mpos = new HashMap<>();
    private final static HashMap<Window, GLFWMouseButtonCallback> mouseButtonCallbacks = new HashMap<>();
    private final static HashMap<Window, GLFWCursorPosCallback> cursorPosCallbacks = new HashMap<>();
    protected int Z_Index = 0;
    protected boolean visible = true;
    protected boolean mouseInside = false;
    Shape hitBox;
    protected boolean threadRendering = true;
    private volatile boolean rendering = true;
    private final Object imageLock = new Object();
    private volatile BufferedImage preRenderedImage;
    private final ScheduledExecutorService renderExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "GUIComponent-Renderer-" + hashCode());
                t.setDaemon(true);
                return t;
            });

    public GUIComponent(Window window) {
        this(window, 0.1f, 0.1f);
    }

    public GUIComponent(Window window, float width, float height) {
        this(window, 0, 0, width, height);
    }

    public GUIComponent(Window window, float x, float y, float width, float height) {
        this.windowParent = window;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;
        boolean success = false;
        try {
            verticesBuffer = MemoryUtil.memAllocFloat(vertices.length);
            verticesBuffer.put(vertices).flip();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            vboId = glGenBuffers();

            shaderProgram = new ShaderProgram();
            shaderProgram.createVertexShader(loadResource("GUIVertexShader.vs"));
            shaderProgram.createFragmentShader(loadResource("GUIFragmentShader.fs"));
            shaderProgram.link();

            shaderProgram.createUniform("position");
            shaderProgram.createUniform("size");
            shaderProgram.createUniform("projection");
            shaderProgram.createUniform("guiTexture");
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);

            glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);

            idxVboId = glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(indices.length);
            indicesBuffer.put(indices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindVertexArray(0);
            success = true;
        } catch (Exception e) {
            success = false;
            throw new RuntimeException("Failed to initialize GUI component", e);
        } finally {
            if (verticesBuffer != null) {
                MemoryUtil.memFree(verticesBuffer);
            }
            if (indicesBuffer != null) {
                MemoryUtil.memFree(indicesBuffer);
            }
        }
        if (success) {
            initializeWindow(window);
            GUIComponents.get(window).put(hashCode(), this);
            hitBox = new Rectangle(xPx,yPx,widthPx,heightPx);
        }
        renderExecutor.scheduleAtFixedRate(() -> {
            try {
                if (threadRendering) {
                    int w, h;
                    synchronized (imageLock) {
                        w = widthPx;
                        h = heightPx;
                    }

                    if (w <= 0 || h <= 0) return;

                    BufferedImage tempImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = tempImage.createGraphics();
                    try {
                        paintComponent(g);
                    } finally {
                        g.dispose();
                    }

                    synchronized (imageLock) {
                        preRenderedImage = tempImage;
                    }
                }else{
                    preRenderedImage = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Rendering error: " + e.getMessage());
            }
        }, 0, 16, TimeUnit.MILLISECONDS);
    }

    /**
     * Initialize window event callbacks if not already done
     */
    private static synchronized void initializeWindow(Window window) {
        if (initializedWindows.contains(window)) {
            return; // Already initialized
        }
        GUIComponents.put(window, new HashMap<>());
        mpos.put(window, new Point2D.Double(0, 0));
        GLFWCursorPosCallback cursorCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long windowHandle, double xpos, double ypos) {
                for (Map.Entry<Window, Point2D> entry : mpos.entrySet()) {
                    if (entry.getKey().getWindowHandle() == windowHandle) {
                        entry.setValue(new Point2D.Double(xpos, ypos));
                        break;
                    }
                }
                Window targetWindow = null;
                for (Window w : mpos.keySet()) {
                    if (w.getWindowHandle() == windowHandle) {
                        targetWindow = w;
                        break;
                    }
                }
                if (targetWindow != null) {
                    var comps = GUIComponents.get(targetWindow);
                    for (GUIComponent g : comps.values()) {
                        if (!g.CustomMouseEvents) {
                            final MouseEnterEvent mouseEnterEvent = new MouseEnterEvent(g, (int) xpos, (int) ypos);
                            final MouseExitEvent mouseExitEvent = new MouseExitEvent(g, (int) xpos, (int) ypos);
                            if (g.hitBox.contains(xpos * 2, ypos * 2) && !g.mouseInside) {
                                g.mouseInside = true;
                                g.callBacks.forEach(callBack -> {
                                    if (callBack instanceof MouseEnterCallBack) {
                                        ((MouseEnterCallBack) callBack).onEvent(mouseEnterEvent);
                                    }
                                });
                            }
                            if (!g.hitBox.contains(xpos * 2, ypos * 2) && g.mouseInside) {
                                g.mouseInside = false;
                                g.callBacks.forEach(callBack -> {
                                    if (callBack instanceof MouseExitCallBack) {
                                        ((MouseExitCallBack) callBack).onEvent(mouseExitEvent);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        };
        cursorPosCallbacks.put(window, cursorCallback);
        window.CursorPosCallbacks.add(cursorCallback);
        GLFWMouseButtonCallback mouseCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long windowHandle, int button, int action, int mods) {
                if (action == GLFW.GLFW_PRESS) {
                    Window targetWindow = null;
                    for (Window w : mpos.keySet()) {
                        if (w.getWindowHandle() == windowHandle) {
                            targetWindow = w;
                            break;
                        }
                    }
                    if (targetWindow != null) {
                        Point2D mousePos = mpos.get(targetWindow);
                        HashMap<Integer, GUIComponent> windowComponents = GUIComponents.get(targetWindow);
                        if (mousePos != null && windowComponents != null) {
                            new ArrayList<>(windowComponents.values()).forEach((GUIComponent comp) -> {
                                final MouseClickEvent mouseClickEvent = new MouseClickEvent(
                                        comp,
                                        (int) mousePos.getX(),
                                        (int) mousePos.getY(),
                                        button
                                );
                                if (comp.visible && (comp.hitBox != null) &&
                                        (comp.hitBox.contains(mousePos.getX()*2, mousePos.getY()*2) & !comp.CustomMouseEvents)) {
                                    comp.callBacks.forEach((callBack) -> {
                                        if (callBack instanceof MouseClickCallBack) {
                                            try {
                                                ((MouseClickCallBack) callBack).onEvent(mouseClickEvent);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    }
                }
            }
        };
        mouseButtonCallbacks.put(window, mouseCallback);
        window.MouseButtonCallbacks.add(mouseCallback);

        // Mark window as initialized
        initializedWindows.add(window);
    }

    private static String loadResource(String filePath) {
        InputStream inputStream = GUIComponent.class.getResourceAsStream(filePath);
        if (inputStream == null) {
            throw new RuntimeException("Shader file not found: " + filePath);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
            return text.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + filePath, e);
        }
    }

    public void updateHitBox() {
        if (parent != null) {
            widthPx  = (int) (parent.getWidthPx() * width);
            heightPx = (int) (parent.getHeightPx() * height);
            xPx = parent.getxPx() + (int) (parent.getWidthPx() * x);
            yPx = parent.getyPx() + (int) (parent.getHeightPx() * y);
        } else {
            widthPx  = (int) (windowParent.getWidth() * width);
            heightPx = (int) (windowParent.getHeight() * height);
            xPx = (int) (windowParent.getWidth() * x);
            yPx = (int) (windowParent.getHeight() * y);
        }
        if (hitBox instanceof Rectangle) ((Rectangle)hitBox).setBounds(xPx,yPx,widthPx,heightPx);
    }

    public static void renderGUIs(Window window) {
        // Ensure window is initialized
        if (!GUIComponents.containsKey(window)) {
            initializeWindow(window);
        }

        HashMap<Integer, GUIComponent> windowComponents = GUIComponents.get(window);
        if (windowComponents != null && !windowComponents.isEmpty()) {
            ArrayList<GUIComponent> guiComponents = new ArrayList<>(windowComponents.values());
            guiComponents.sort(new ZSort());
            for (GUIComponent g : guiComponents) {
                if (g.parent == null) {
                    g.render();
                }
            }
        }
    }

    public void render() {
        if (!visible) return;

        updateHitBox();

        boolean wasDepthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        if (wasDepthTestEnabled) {
            glDisable(GL_DEPTH_TEST);
        }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        renderGUIImage();

        glDisable(GL_BLEND);
        if (wasDepthTestEnabled) {
            glEnable(GL_DEPTH_TEST);
        }

        children.sort(new ZSort());
        for (GUIComponent child : children) {
            child.render();
        }
    }

    protected void renderGUIImage() {
        if (texture!=null){
            texture.cleanup();
        }
        BufferedImage imageToRender;
        synchronized (imageLock) {
            imageToRender = preRenderedImage;
        }

        if (imageToRender == null) {
            BufferedImage bi = new BufferedImage(Math.max(1, widthPx), Math.max(1, heightPx), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            paintComponent(g);
            g.dispose();
            texture = Texture.loadTexture(bi);
        } else {
            texture = Texture.loadTexture(imageToRender);
        }

        // Clear any existing errors
//        while (glGetError() != GL_NO_ERROR) { /* clear error queue */ }

        shaderProgram.bind();

//        int error = glGetError();
//        if (error != GL_NO_ERROR) {
//            System.err.println("ERROR after shader bind: " + error);
//            return;
//        }

        Matrix4f projection = new Matrix4f().ortho(0, windowParent.getWidth(),
                windowParent.getHeight(), 0, -1, 1);
        shaderProgram.setUniform("projection", projection);

//        error = glGetError();
//        if (error != GL_NO_ERROR) {
//            System.err.println("ERROR after projection uniform: " + error);
//            return;
//        }

        Vector2f pos = new Vector2f(xPx, yPx);
        Vector2f size = new Vector2f(widthPx, heightPx);
        shaderProgram.setUniform("position", pos);

//        error = glGetError();
//        if (error != GL_NO_ERROR) {
//            System.err.println("ERROR after position uniform: " + error);
//            return;
//        }

        shaderProgram.setUniform("size", size);

//        error = glGetError();
//        if (error != GL_NO_ERROR) {
//            System.out.println("ERROR after size uniform: " + error);
//            return;
//        }

        glActiveTexture(GL_TEXTURE0);

//        error = glGetError();
//        if (error != GL_NO_ERROR) {
//            System.out.println("ERROR after glActiveTexture: " + error);
//            return;
//        }

        texture.bind();

//        error = glGetError();
//        if (error != GL_NO_ERROR) {
//            System.out.println("ERROR after texture bind: " + error);
//            return;
//        }

        shaderProgram.setUniform("guiTexture", 0);

//        error = glGetError();
//        if (error != GL_NO_ERROR) {
//            System.out.println("ERROR after texture uniform: " + error);
//            return;
//        }
        glBindVertexArray(vaoId);

//        error = glGetError();
//        if (error != GL_NO_ERROR) {
//            System.out.println("ERROR after VAO bind: " + error);
//            return;
//        }

        if (vaoId == 0) {
            System.out.println("ERROR: VAO ID is 0!");
            return;
        }

        glDrawElements(GL_TRIANGLES, indices.length, GL_UNSIGNED_INT, 0);

//        error = glGetError();
//        if (error != GL_NO_ERROR) {
//            System.out.println("ERROR after draw: " + error);
//        }

        glBindVertexArray(0);
        texture.unbind();
        shaderProgram.unbind();
    }

    @Override
    public void cleanUp() {
        rendering = false;

        if (renderExecutor != null) {
            renderExecutor.shutdownNow();
            try {
                renderExecutor.awaitTermination(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // NOW clean up OpenGL resources
        if (texture != null) {
            texture.cleanup();
            texture = null;
        }
        if (shaderProgram != null) {
            shaderProgram.cleanup();
        }
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        glDeleteBuffers(idxVboId);

        // Remove from components map
        if (windowParent != null) {
            HashMap<Integer, GUIComponent> windowComponents = GUIComponents.get(windowParent);
            if (windowComponents != null) {
                windowComponents.remove(hashCode());
            }
        }
    }

    /**
     * Clean up window-specific resources when a window is closed
     */
    public static void cleanupWindow(Window window) {
        if (window == null) return;

        // Clean up all components for this window
        HashMap<Integer, GUIComponent> windowComponents = GUIComponents.get(window);
        if (windowComponents != null) {
            // Create a copy to avoid concurrent modification
            new ArrayList<>(windowComponents.values()).forEach(GUIComponent::cleanUp);
            windowComponents.clear();
        }

        // Remove window from all maps
        GUIComponents.remove(window);
        mpos.remove(window);

        // Clean up callbacks
        GLFWMouseButtonCallback mouseCallback = mouseButtonCallbacks.remove(window);
        if (mouseCallback != null) {
            mouseCallback.free();
        }

        GLFWCursorPosCallback cursorCallback = cursorPosCallbacks.remove(window);
        if (cursorCallback != null) {
            cursorCallback.free();
        }

        initializedWindows.remove(window);
    }

    // Getters and setters
    public int getWidthPx() { return widthPx; }
    public int getHeightPx() { return heightPx; }
    public float getWidth() { return width; }
    public GUIComponent setWidth(float width) { this.width = width; return this; }
    public float getHeight() { return height; }
    public GUIComponent setHeight(float height) { this.height = height; return this; }
    public int getxPx() { return xPx; }
    public int getyPx() { return yPx; }
    public float getX() { return x; }
    public GUIComponent setX(float x) { this.x = x; return this; }
    public float getY() { return y; }
    public GUIComponent setY(float y) { this.y = y; return this; }

    public int getZ_Index() {
        return Z_Index;
    }

    public GUIComponent setZ_Index(int z_Index) {
        Z_Index = z_Index;
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    public GUIComponent setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public Window getWindowParent() {
        return windowParent;
    }

    protected abstract void paintComponent(Graphics g);

    public <E extends Event> void addCallBack(EventCallBack<E> listener) {
        if (!callBacks.contains(listener)) {
            callBacks.add(listener);
        }
    }


    public <E extends Event> GUIComponent removeCallBack(EventCallBack<E> listener) {
        callBacks.remove(listener);
        return this;
    }
    @SuppressWarnings("unchecked")
    protected <E extends Event> void fireEvent(E event) {
        for (EventCallBack<?> listener : callBacks) {
            ((EventCallBack<E>) listener).onEvent(event);
        }
    }

    public List<GUIComponent> getChildren() {
        return children;
    }

    public GUIComponent getParent() {
        return parent;
    }

    public GUIComponent setParent(GUIComponent parent) {
        this.parent = parent;
        return this;
    }

    // Adds a child to this component
    public GUIComponent addChild(GUIComponent child) {
        if (child == null) return this;

        // Remove child from its current parent
        if (child.parent != null) {
            child.parent.removeChild(child);
        }

        children.add(child);
        child.parent = this;
        return this;
    }

    // Removes a child from this component
    public GUIComponent removeChild(GUIComponent child) {
        if (child == null) return this;

        if (children.remove(child)) {
            child.parent = null;
        }
        return this;
    }

    // Clears all children
    public void clearChildren() {
        for (GUIComponent child : new ArrayList<>(children)) {
            child.cleanUp();
            child.parent = null;
            GUIComponents.remove(child.hashCode());
        }
        children.clear();
    }


    protected static class ZSort implements Comparator<GUIComponent> {
        @Override
        public int compare(GUIComponent o1, GUIComponent o2) {
            return o1.Z_Index - o2.Z_Index;
        }
    }
    public static GUIComponent getTopComponentAt(Window window, double x, double y) {
        HashMap<Integer, GUIComponent> windowComponents = GUIComponents.get(window);
        if (windowComponents == null || windowComponents.isEmpty()) return null;
        List<GUIComponent> comps = new ArrayList<>(windowComponents.values());
        comps.sort(Comparator.comparingInt(GUIComponent::getZ_Index));
        Collections.reverse(comps);
        for (GUIComponent root : comps) {
            GUIComponent hit = getDeepestComponentAt(root, x, y);
            if (hit != null) return hit;
        }
        return null;
    }

    private static GUIComponent getDeepestComponentAt(GUIComponent comp, double x, double y) {
        if (!comp.visible) return null;
        int left = comp.getxPx();
        int top = comp.getyPx();
        int right = left + comp.getWidthPx();
        int bottom = top + comp.getHeightPx();
        if (x < left || x > right || y < top || y > bottom) return null;
        List<GUIComponent> childrenCopy = new ArrayList<>(comp.getChildren());
        childrenCopy.sort(Comparator.comparingInt(GUIComponent::getZ_Index));
        Collections.reverse(childrenCopy);
        for (GUIComponent child : childrenCopy) {
            GUIComponent deeper = getDeepestComponentAt(child, x, y);
            if (deeper != null) return deeper;
        }
        return comp;
    }

    public static ScrollableFrame findScrollableAncestor(GUIComponent comp) {
        GUIComponent cur = comp;
        while (cur != null) {
            if (cur instanceof ScrollableFrame) return (ScrollableFrame) cur;
            cur = cur.getParent();
        }
        return null;
    }
    public static Point2D getMousePos(Window w) {
        return mpos.get(w);
    }
    public void print(Graphics g){
        paintComponent(g);
    }
    public BufferedImage print(){
        return print(widthPx,heightPx);
    }
    float scale = 1f;
    private BufferedImage prebufferedimage = new BufferedImage((int) (widthPx*scale), (int) (heightPx*scale),BufferedImage.TYPE_INT_ARGB);
    public BufferedImage print(int width,int height){
        updateHitBox();
        if (prebufferedimage.getWidth()!=(int) (widthPx*scale) || prebufferedimage.getHeight()!=(int) (heightPx*scale)){
            prebufferedimage = new BufferedImage((int) (widthPx*scale), (int) (heightPx*scale),BufferedImage.TYPE_INT_ARGB);
        }
        paintComponent(prebufferedimage.createGraphics());
        return prebufferedimage;
    }
    public BufferedImage print(int width,int height,float resolution){
        updateHitBox();
        if (prebufferedimage.getWidth()!=(int) (widthPx*scale) || prebufferedimage.getHeight()!=(int) (heightPx*scale)){
            prebufferedimage = new BufferedImage((int) (widthPx*scale*resolution), (int) (heightPx*scale*resolution),BufferedImage.TYPE_INT_ARGB);
        }
        Graphics g = prebufferedimage.createGraphics();
        ((Graphics2D) g).scale(resolution,resolution);
        paintComponent(g);
        g.dispose();
        return prebufferedimage;
    }
}