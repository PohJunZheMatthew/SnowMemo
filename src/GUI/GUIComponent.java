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
/*
* It is recommanded to use IMGUI for the gui, but if you want to do java2d billboard you can use this class*/
@Deprecated(since = "25",forRemoval = false)
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
            0.0f, 0.0f, 0.0f,       0.0f, 1.0f,
            1.0f, 0.0f, 0.0f,       1.0f, 1.0f,
            1.0f, 1.0f, 0.0f,       1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,       0.0f, 0.0f
    };
    private static final int[] indices = {
            0, 3, 2,
            2, 1, 0
    };
    protected final Window windowParent;
    public final List<EventCallBack<? extends Event>> callBacks = new ArrayList<>();
    private final static HashMap<Window, HashMap<Integer, GUIComponent>> GUIComponents = new HashMap<>();
    protected GUIComponent parent;
    protected final List<GUIComponent> children = new ArrayList<GUIComponent>();

    private final static Set<Window> initializedWindows = new HashSet<>();
    private final static HashMap<Window, Point2D> mpos = new HashMap<>();
    private final static HashMap<Window, GLFWMouseButtonCallback> mouseButtonCallbacks = new HashMap<>();
    private final static HashMap<Window, GLFWCursorPosCallback> cursorPosCallbacks = new HashMap<>();
    protected int Z_Index = 0;
    protected boolean visible = true;
    protected boolean mouseInside = false;
    Shape hitBox;
    public boolean threadRendering = true;
    private volatile boolean rendering = true;
    private boolean pixelSized = false;
    private final Object imageLock = new Object();
    private volatile BufferedImage preRenderedImage;
    private final ScheduledExecutorService renderExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "GUIComponent-Renderer-" + hashCode());
                t.setDaemon(true);
                return t;
            });

    private boolean useAbsoluteSize = false;
    private int absoluteWidthPx = 0;
    private int absoluteHeightPx = 0;
    private boolean useAbsolutePosition = false;
    private int absoluteXPx = 0;
    private int absoluteYPx = 0;
    private int lastTextureWidth = -1;
    private int lastTextureHeight = -1;
    private List<GUIComponent> sortedChildren = new ArrayList<>();
    private boolean childrenDirty = true;

    // ALTERNATIVE: Even better approach - cache the texture and only update when dirty
// Add these fields:
    private boolean textureDirty = true;
    private BufferedImage lastRenderedImage = null;
    private boolean needsGPUUpload;
    private boolean needsPaint;

    // Add this method to mark GUI as needing re-render:
    public void markDirty() {
        synchronized (imageLock) {
            needsPaint = true;      // Background thread should call paintComponent()
            needsGPUUpload = true;  // GPU should upload when ready
            textureDirty = true;    // Backwards compatibility
        }
    }

    public GUIComponent(Window window) {
        this(window, 0.1f, 0.1f);
    }

    public GUIComponent(Window window, float width, float height) {
        this(window, 0, 0, width, height);
    }
    public GUIComponent(Window window, int widthPx,int heightPx){
        this(window,0,0,0,0);
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        pixelSized = true;
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
                if (!threadRendering) {
                    preRenderedImage = null;
                    return;
                }

                // Check if we need to paint
                boolean shouldPaint;
                synchronized (imageLock) {
                    shouldPaint = needsPaint || preRenderedImage == null;
                }

                if (!shouldPaint) {
                    return;  // Skip this frame - nothing changed
                }

                int w, h;
                synchronized (imageLock) {
                    w = widthPx;
                    h = heightPx;
                }

                if (w <= 0 || h <= 0) return;

                // Create new image
                BufferedImage tempImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = tempImage.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    paintComponent(g);
                } finally {
                    g.dispose();
                }

                synchronized (imageLock) {
                    preRenderedImage = tempImage;
                    needsPaint = false;      // Painting done
                    needsGPUUpload = true;   // But GPU upload still needed
                    textureDirty = true;     // Backwards compatibility
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Rendering error: " + e.getMessage());
            }
        }, 0, 16, TimeUnit.MILLISECONDS);
    }

    private static synchronized void initializeWindow(Window window) {
        if (initializedWindows.contains(window)) {
            return;
        }
        GUIComponents.put(window, new HashMap<>());
        mpos.put(window, new Point2D.Double(0, 0));
        GLFWCursorPosCallback cursorCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long windowHandle, double xpos, double ypos) {
                for (Map.Entry<Window, Point2D> entry : mpos.entrySet()) {
                    if (entry.getKey().getWindowHandle() == windowHandle) {
                        entry.setValue(new Point2D.Double(window.getMouseX(), window.getMouseY()));
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
                    if (comps != null) {
                        List<GUIComponent> componentsCopy;
                        synchronized (comps) {
                            componentsCopy = new ArrayList<>(comps.values());
                        }

                        for (GUIComponent g : componentsCopy) {
                            if (!g.CustomMouseEvents) {
                                final MouseEnterEvent mouseEnterEvent = new MouseEnterEvent(g, (int) window.getMouseX(), (int) window.getMouseY());
                                final MouseExitEvent mouseExitEvent = new MouseExitEvent(g, (int) window.getMouseX(), (int) window.getMouseY());

                                if (g.hitBox.contains(window.getMouseX(), window.getMouseY()) && !g.mouseInside) {
                                    g.mouseInside = true;
                                    List<EventCallBack<? extends Event>> callbacksCopy = new ArrayList<>(g.callBacks);
                                    callbacksCopy.forEach(callBack -> {
                                        if (callBack instanceof MouseEnterCallBack) {
                                            ((MouseEnterCallBack) callBack).onEvent(mouseEnterEvent);
                                        }
                                    });
                                }

                                if (!g.hitBox.contains(window.getMouseX() * 2, window.getMouseY() * 2) && g.mouseInside) {
                                    g.mouseInside = false;
                                    List<EventCallBack<? extends Event>> callbacksCopy = new ArrayList<>(g.callBacks);
                                    callbacksCopy.forEach(callBack -> {
                                        if (callBack instanceof MouseExitCallBack) {
                                            ((MouseExitCallBack) callBack).onEvent(mouseExitEvent);
                                        }
                                    });
                                }
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
                            double mx = window.getMouseX();
                            double my = window.getMouseY();

                            List<GUIComponent> componentsCopy = new ArrayList<>(windowComponents.values());

                            for (GUIComponent root : componentsCopy) {
                                if (root.getParent() == null && root.visible && root.hitBox.contains(mx, my)) {
                                    MouseClickEvent evt = new MouseClickEvent(root, (int) mx, (int) my, button);
                                    root.propagateEvent(evt, mx, my);
                                }
                            }
                        }
                    }
                }
            }
        };
        mouseButtonCallbacks.put(window, mouseCallback);
        window.MouseButtonCallbacks.add(mouseCallback);

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

    public void setAbsoluteSize(int widthPx, int heightPx) {
        this.useAbsoluteSize = true;
        this.absoluteWidthPx = widthPx;
        this.absoluteHeightPx = heightPx;
    }

    public void setAbsolutePosition(int xPx, int yPx) {
        this.useAbsolutePosition = true;
        this.absoluteXPx = xPx;
        this.absoluteYPx = yPx;
    }

    public void disableAbsoluteSize() {
        this.useAbsoluteSize = false;
    }

    public void disableAbsolutePosition() {
        this.useAbsolutePosition = false;
    }

    public boolean isUsingAbsoluteSize() {
        return useAbsoluteSize;
    }

    public boolean isUsingAbsolutePosition() {
        return useAbsolutePosition;
    }

    public int getAbsoluteWidthPx() {
        return absoluteWidthPx;
    }

    public int getAbsoluteHeightPx() {
        return absoluteHeightPx;
    }

    public int getAbsoluteXPx() {
        return absoluteXPx;
    }

    public int getAbsoluteYPx() {
        return absoluteYPx;
    }

    public void updateHitBox() {
        if (useAbsoluteSize) {
            widthPx = absoluteWidthPx;
            heightPx = absoluteHeightPx;
        } else if (!pixelSized) {
            if (parent != null) {
                widthPx = (int) (parent.getWidthPx() * width);
                heightPx = (int) (parent.getHeightPx() * height);
            } else {
                widthPx = (int) (windowParent.getWidth() * width);
                heightPx = (int) (windowParent.getHeight() * height);
            }
        }

        if (useAbsolutePosition) {
            xPx = absoluteXPx;
            yPx = absoluteYPx;
        } else if (!pixelSized) {
            if (parent != null) {
                xPx = parent.getxPx() + (int) (parent.getWidthPx() * x);
                yPx = parent.getyPx() + (int) (parent.getHeightPx() * y);
            } else {
                xPx = (int) (windowParent.getWidth() * x);
                yPx = (int) (windowParent.getHeight() * y);
            }
        }

        if (hitBox instanceof Rectangle) ((Rectangle)hitBox).setBounds(xPx,yPx,widthPx,heightPx);
    }

    public static void renderGUIs(Window window) {
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
    int calls = 0;
    public void render() {
        calls++;
        if (!visible) return;
        if (calls%5==0){
            markDirty();
        }
        int oldWidthPx = widthPx;
        int oldHeightPx = heightPx;
        updateHitBox();

        if (widthPx != oldWidthPx || heightPx != oldHeightPx) {
            markDirty();
        }

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

        // Use cached sorted list
        if (childrenDirty) {
            synchronized (children) {
                sortedChildren = new ArrayList<>(children);
                sortedChildren.sort(new ZSort());
                childrenDirty = false;
            }
        }

        for (GUIComponent child : sortedChildren) {
            child.render();
        }
    }
    protected void renderGUIImage() {
        BufferedImage imageToRender;
        boolean needsUpload;

        synchronized (imageLock) {
            imageToRender = preRenderedImage;
            needsUpload = needsGPUUpload;
        }

        // Nothing to draw yet
        if (imageToRender == null) {
            return;
        }

        int imgW = imageToRender.getWidth();
        int imgH = imageToRender.getHeight();

        boolean sizeChanged = texture == null ||
                texture.getWidth() != imgW ||
                texture.getHeight() != imgH;

        // Upload to GPU if needed
        if (sizeChanged) {
            if (texture != null) {
                texture.cleanup();
            }
            texture = Texture.loadTexture(imageToRender);
            synchronized (imageLock) {
                needsGPUUpload = false;  // Upload complete
                textureDirty = false;    // Backwards compatibility
            }
        } else if (needsUpload) {
            texture.update(imageToRender);
            synchronized (imageLock) {
                needsGPUUpload = false;  // Upload complete
                textureDirty = false;    // Backwards compatibility
            }
        }

        if (texture == null) return;

        // Render the texture to screen
        shaderProgram.bind();

        Matrix4f projection = new Matrix4f().ortho(
                0, windowParent.getWidth(),
                windowParent.getHeight(), 0,
                -1, 1
        );

        shaderProgram.setUniform("projection", projection);
        shaderProgram.setUniform("position", new Vector2f(xPx, yPx));
        shaderProgram.setUniform("size", new Vector2f(widthPx, heightPx));

        glActiveTexture(GL_TEXTURE0);
        texture.bind();
        shaderProgram.setUniform("guiTexture", 0);

        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indices.length, GL_UNSIGNED_INT, 0);

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

        if (windowParent != null) {
            HashMap<Integer, GUIComponent> windowComponents = GUIComponents.get(windowParent);
            if (windowComponents != null) {
                windowComponents.remove(hashCode());
            }
        }
    }

    public static void cleanupWindow(Window window) {
        if (window == null) return;

        HashMap<Integer, GUIComponent> windowComponents = GUIComponents.get(window);
        if (windowComponents != null) {
            new ArrayList<>(windowComponents.values()).forEach(GUIComponent::cleanUp);
            windowComponents.clear();
        }

        GUIComponents.remove(window);
        mpos.remove(window);

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
        if (this.Z_Index != z_Index) {
            this.Z_Index = z_Index;
            if (parent != null) {
                parent.childrenDirty = true;  // Parent needs to re-sort
            }
        }
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
        markDirty();
        List<EventCallBack<?>> listenersCopy = new ArrayList<>(callBacks);

        for (EventCallBack<?> listener : listenersCopy) {
            if (event instanceof MouseClickEvent && listener instanceof MouseClickCallBack) {
                ((MouseClickCallBack) listener).onEvent((MouseClickEvent) event);
            } else if (event instanceof TextChangeEvent && listener instanceof TextChangeCallBack) {
                ((TextChangeCallBack) listener).onEvent((TextChangeEvent) event);
            }
        }
    }

    public List<GUIComponent> getChildren() {
        return children;
    }

    public GUIComponent getParent() {
        return parent;
    }

    public GUIComponent setParent(GUIComponent parent) {
        if (this.parent == parent) return this;
        this.parent = parent;
        parent.addChild(this);
        return this;
    }


    public GUIComponent addChild(GUIComponent child) {
        if (child == null) return this;
        markDirty();
        synchronized (children) {
            if (child.parent != null) {
                markDirty();
                child.parent.removeChild(child);
            }
            children.add(child);
            child.parent = this;
            childrenDirty = true;  // Mark for re-sort
        }
        return this;
    }


    public GUIComponent removeChild(GUIComponent child) {
        if (child == null) return this;
        markDirty();
        synchronized (children) {
            if (children.remove(child)) {
                child.parent = null;
                childrenDirty = true;  // Mark for re-sort
            }
        }
        return this;
    }

    public void clearChildren() {
        for (GUIComponent child : new ArrayList<>(children)) {
            child.cleanUp();
            child.parent = null;
            GUIComponents.remove(child.hashCode());
        }
        children.clear();
    }

    public Shape getHitBox() {
        return hitBox;
    }

    protected static class ZSort implements Comparator<GUIComponent> {
        @Override
        public int compare(GUIComponent o1, GUIComponent o2) {
            int cmp = Integer.compare(o1.getZ_Index(), o2.getZ_Index());
            if (cmp != 0) return cmp;
            return Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));
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

        Graphics2D g2d = prebufferedimage.createGraphics();
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0,0,prebufferedimage.getWidth(),prebufferedimage.getHeight());
        g2d.setComposite(AlphaComposite.SrcOver);
        paintComponent(g2d);
        g2d.dispose();
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

    public void print(Graphics g) {
        updateHitBox();
        paintComponent(g);

        if (children == null || children.isEmpty()) return;

        children.sort(new ZSort());

        for (GUIComponent child : children) {
            if (child != null && child.isVisible()) {
                Graphics2D g2 = (Graphics2D) g.create();

                int childX = (int) (child.getX() * widthPx);
                int childY = (int) (child.getY() * heightPx);
                int childWidth = (int) (child.getWidth() * widthPx);
                int childHeight = (int) (child.getHeight() * heightPx);

                g2.translate(childX, childY);

                g2.setClip(0, 0, childWidth, childHeight);

                int oldWidthPx = child.widthPx;
                int oldHeightPx = child.heightPx;
                child.widthPx = childWidth;
                child.heightPx = childHeight;

                child.print(g2);

                child.widthPx = oldWidthPx;
                child.heightPx = oldHeightPx;

                g2.dispose();
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected <E extends Event> void propagateEvent(E event, double mouseX, double mouseY) {
        List<GUIComponent> childrenCopy = new ArrayList<>(children);

        for (GUIComponent child : childrenCopy) {
            if (!child.visible) continue;
            if (child.hitBox != null && child.hitBox.contains(mouseX, mouseY)) {
                child.propagateEvent(event, mouseX, mouseY);
            }
        }

        fireEvent(event);
    }
    @Override
    public void init(){markDirty();}
    public static Collection getGUIComponents(Window window){
        if (!GUIComponents.containsKey(window)) return null;
        return GUIComponents.get(window).values();
    }
}