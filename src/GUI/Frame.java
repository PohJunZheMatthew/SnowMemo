package GUI;

import GUI.Events.EventCallBack;
import GUI.Events.MouseClickEvent;
import Main.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class Frame extends GUIComponent {
    private Color backgroundColor = Color.WHITE;
    private boolean showBorder = true;
    private Color borderColor = Color.LIGHT_GRAY;
    private float borderThickness = 1f;
    private int cornerRadius = 0;
    private int contentPadding = 10;
    private float nextChildY = 0f;
    private boolean autoLayout = true;

    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWCursorPosCallback cursorPosCallback;

    @SuppressWarnings("unused")
    public Frame(Window window) {
        super(window);
        init();
    }

    @SuppressWarnings("unused")
    public Frame(Window window, float width, float height) {
        super(window, width, height);
        init();
    }

    public Frame(Window window, float x, float y, float width, float height) {
        super(window, x, y, width, height);
        init();
    }

    @Override
    public void init() {
        setupMouseButtonCallback();
        setupCursorPosCallback();
    }

    // --- RENDER (OPENGL THREAD) ---
    @Override
    public void render() {
        // 1. Visibility Check
        if (!visible) return;

        // 2. Calculate Screen Coordinates
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

        // 3. Update Hitbox for events
        updateHitBox();

        // 4. Mark Dirty (Crucial: tells background thread to regen texture)
        markDirty();

        // 5. Setup OpenGL
        boolean wasDepthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        if (wasDepthTestEnabled) glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 6. Draw the Frame's Texture
        renderGUIImage();

        // 7. Cleanup
        glDisable(GL_BLEND);
        if (wasDepthTestEnabled) glEnable(GL_DEPTH_TEST);

        // Note: We do NOT call child.render() here.
        // Children are baked into renderGUIImage() via paintComponent().
    }

    // --- PAINT (BACKGROUND THREAD) ---
    @Override
    protected void paintComponent(Graphics g) {
        // SAFETY: Prevent 0-size crash
        if (getWidthPx() <= 0 || getHeightPx() <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw background
        g2.setColor(backgroundColor);
        if (cornerRadius > 0) {
            g2.fillRoundRect(0, 0, getWidthPx(), getHeightPx(), cornerRadius, cornerRadius);
        } else {
            g2.fillRect(0, 0, getWidthPx(), getHeightPx());
        }

        // Clip content to frame bounds (handling corners)
        Shape oldClip = g2.getClip();
        if (cornerRadius > 0) {
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidthPx(), getHeightPx(), cornerRadius, cornerRadius));
        } else {
            g2.clipRect(0, 0, getWidthPx(), getHeightPx());
        }

        // Render children into this texture
        renderChildren(g2);

        // Restore clip
        g2.setClip(oldClip);

        // Draw border on top
        if (showBorder) {
            Stroke s = g2.getStroke();
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderThickness));
            float half = borderThickness / 2f;
            if (cornerRadius > 0) {
                g2.drawRoundRect(
                        Math.round(half),
                        Math.round(half),
                        Math.round(getWidthPx() - borderThickness),
                        Math.round(getHeightPx() - borderThickness),
                        cornerRadius,
                        cornerRadius
                );
            } else {
                g2.drawRect(
                        Math.round(half),
                        Math.round(half),
                        Math.round(getWidthPx() - borderThickness),
                        Math.round(getHeightPx() - borderThickness)
                );
            }
            g2.setStroke(s);
        }

        g2.dispose();
    }

    private void renderChildren(Graphics2D g2) {
        if (getChildren() == null) return;
        int contentAreaWidth = getWidthPx();
        int contentAreaHeight = getHeightPx();

        // Prevent math errors if frame is tiny
        if (contentAreaWidth <= 0 || contentAreaHeight <= 0) return;

        synchronized (getChildren()) {
            // Copy list to avoid concurrent modification issues
            List<GUIComponent> childList = new ArrayList<>(getChildren());

            for (GUIComponent child : childList) {
                if (child != null && child.isVisible()) {
                    child.threadRendering = false; // Optimization: Stop child's own render thread

                    int childWidth = (int) (child.getWidth() * contentAreaWidth);
                    int childHeight = (int) (child.getHeight() * contentAreaHeight);
                    int childX = (int) (child.getX() * contentAreaWidth);
                    int childY = (int) (child.getY() * contentAreaHeight);

                    Graphics2D childGraphics = (Graphics2D) g2.create();
                    childGraphics.translate(childX, childY);

                    // SAVE OLD STATE
                    int oldW = child.widthPx;
                    int oldH = child.heightPx;

                    // CRITICAL FIX: Ensure non-zero dimensions
                    child.widthPx = Math.max(1, childWidth);
                    child.heightPx = Math.max(1, childHeight);

                    try {
                        child.print(childGraphics);
                    } catch (Exception e) {
                        // Swallow print errors to keep the frame alive
                    }

                    // RESTORE STATE
                    child.widthPx = oldW;
                    child.heightPx = oldH;

                    childGraphics.dispose();
                }
            }
        }
    }

    // --- EVENTS ---

    private void setupMouseButtonCallback() {
        mouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long windowHandle, int button, int action, int mods) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                    handleChildMousePress(button);
                }
            }
        };
        if (getWindowParent() != null && getWindowParent().MouseButtonCallbacks != null)
            getWindowParent().MouseButtonCallbacks.add(mouseButtonCallback);
    }

    private void setupCursorPosCallback() {
        cursorPosCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long windowHandle, double xpos, double ypos) {
                handleChildMouseMove((int) xpos, (int) ypos);
            }
        };
        if (getWindowParent() != null && getWindowParent().CursorPosCallbacks != null)
            getWindowParent().CursorPosCallbacks.add(cursorPosCallback);
    }

    private void handleChildMousePress(int button) {
        if (!visible) return;
        Point2D mousePos = getMousePos(getWindowParent());
        if (mousePos == null) return;

        Point2D scaledMousePos = new Point((int) mousePos.getX() * 2, (int) mousePos.getY() * 2);
        if (!hitBox.contains(scaledMousePos)) return;

        int adjustedMouseX = (int)(scaledMousePos.getX() - xPx);
        int adjustedMouseY = (int)(scaledMousePos.getY() - yPx);

        // Iterate backwards (Top Z-Index first) if you had Z-sorting,
        // but standard loop is fine if painting order matches event order
        for (GUIComponent child : getChildren()) {
            if (child == null || !child.isVisible()) continue;

            int childX = (int)(child.getX() * getWidthPx());
            int childY = (int)(child.getY() * getHeightPx());
            int childWidth = (int)(child.getWidth() * getWidthPx());
            int childHeight = (int)(child.getHeight() * getHeightPx());

            boolean hit = adjustedMouseX >= childX && adjustedMouseX <= childX + childWidth &&
                    adjustedMouseY >= childY && adjustedMouseY <= childY + childHeight;

            if (hit) {
                var event = new MouseClickEvent(
                        child,
                        adjustedMouseX - childX,
                        adjustedMouseY - childY,
                        button
                );

                for (EventCallBack<?> callback : child.callBacks) {
                    if (callback instanceof GUI.Events.MouseClickCallBack) {
                        try {
                            ((GUI.Events.MouseClickCallBack) callback).onEvent(event);
                        } catch (Exception e) {
                            System.err.println("Error in child click callback: " + e.getMessage());
                        }
                    }
                }
                break; // Consume event
            }
        }
    }

    private void handleChildMouseMove(int mouseX, int mouseY) {
        if (!visible) return;
        Point2D mousePos = new Point(mouseX * 2, mouseY * 2);

        if (!hitBox.contains(mousePos)) {
            // Mouse is outside the frame - fire exit events
            for (GUIComponent child : getChildren()) {
                if (child.mouseInside) {
                    child.mouseInside = false;
                    GUI.Events.MouseExitEvent exitEvent = new GUI.Events.MouseExitEvent(child, mouseX, mouseY);
                    child.callBacks.forEach(callBack -> {
                        if (callBack instanceof GUI.Events.MouseExitCallBack) {
                            ((GUI.Events.MouseExitCallBack) callBack).onEvent(exitEvent);
                        }
                    });
                }
            }
            return;
        }

        int adjustedMouseX = (int)(mousePos.getX() - xPx);
        int adjustedMouseY = (int)(mousePos.getY() - yPx);

        for (GUIComponent child : getChildren()) {
            if (child == null || !child.isVisible()) continue;

            int childX = (int)(child.getX() * getWidthPx());
            int childY = (int)(child.getY() * getHeightPx());
            int childWidth = (int)(child.getWidth() * getWidthPx());
            int childHeight = (int)(child.getHeight() * getHeightPx());

            boolean mouseOverChild = adjustedMouseX >= childX && adjustedMouseX <= childX + childWidth &&
                    adjustedMouseY >= childY && adjustedMouseY <= childY + childHeight;

            if (mouseOverChild && !child.mouseInside) {
                child.mouseInside = true;
                GUI.Events.MouseEnterEvent enterEvent = new GUI.Events.MouseEnterEvent(
                        child,
                        adjustedMouseX - childX,
                        adjustedMouseY - childY
                );
                child.callBacks.forEach(callBack -> {
                    if (callBack instanceof GUI.Events.MouseEnterCallBack) {
                        ((GUI.Events.MouseEnterCallBack) callBack).onEvent(enterEvent);
                    }
                });
            } else if (!mouseOverChild && child.mouseInside) {
                child.mouseInside = false;
                GUI.Events.MouseExitEvent exitEvent = new GUI.Events.MouseExitEvent(
                        child,
                        adjustedMouseX - childX,
                        adjustedMouseY - childY
                );
                child.callBacks.forEach(callBack -> {
                    if (callBack instanceof GUI.Events.MouseExitCallBack) {
                        ((GUI.Events.MouseExitCallBack) callBack).onEvent(exitEvent);
                    }
                });
            }
        }
    }

    @Override
    public Frame addChild(GUIComponent child) {
        if (autoLayout) {
            child.setY(nextChildY);
            nextChildY += child.getHeight();
        }
        child.CustomMouseEvents = true;
        child.threadRendering = false; // Important: Stop child's standalone renderer
        super.addChild(child);
        return this;
    }

    @Override
    public Frame removeChild(GUIComponent child) {
        super.removeChild(child);
        return this;
    }

    public void clearChildren() {
        getChildren().clear();
        nextChildY = 0f;
    }

    @Override
    public void cleanUp() {
        if (getWindowParent() != null) {
            if (mouseButtonCallback != null && getWindowParent().MouseButtonCallbacks != null) {
                getWindowParent().MouseButtonCallbacks.remove(mouseButtonCallback);
                mouseButtonCallback.free();
                mouseButtonCallback = null;
            }
            if (cursorPosCallback != null && getWindowParent().CursorPosCallbacks != null) {
                getWindowParent().CursorPosCallbacks.remove(cursorPosCallback);
                cursorPosCallback.free();
                cursorPosCallback = null;
            }
        }
        super.cleanUp();
    }

    // --- GETTERS AND SETTERS ---

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public Frame setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public boolean isShowBorder() {
        return showBorder;
    }

    public Frame setShowBorder(boolean showBorder) {
        this.showBorder = showBorder;
        return this;
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public Frame setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    public float getBorderThickness() {
        return borderThickness;
    }

    public Frame setBorderThickness(float borderThickness) {
        this.borderThickness = borderThickness;
        return this;
    }

    public int getCornerRadius() {
        return cornerRadius;
    }

    public Frame setCornerRadius(int cornerRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
        return this;
    }

    public int getContentPadding() {
        return contentPadding;
    }

    public Frame setContentPadding(int contentPadding) {
        this.contentPadding = Math.max(0, contentPadding);
        return this;
    }

    public boolean isAutoLayout() {
        return autoLayout;
    }

    public Frame setAutoLayout(boolean autoLayout) {
        this.autoLayout = autoLayout;
        return this;
    }

    public float getNextChildY() {
        return nextChildY;
    }

    public Frame setNextChildY(float nextChildY) {
        this.nextChildY = nextChildY;
        return this;
    }
}