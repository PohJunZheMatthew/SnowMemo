package GUI;

import GUI.Events.EventCallBack;
import GUI.Events.MouseClickEvent;
import Main.PerformanceProfiler;
import Main.Settings.Settings;
import Main.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class ScrollableFrame extends GUIComponent {
    private int contentHeight;
    private int contentWidth;
    private int scrollY;
    private int scrollX;
    private int targetScrollY;
    private int targetScrollX;

    private static final int SCROLLBAR_WIDTH = 12;
    private static final int MIN_THUMB_SIZE = 20;
    private static final int SCROLL_SPEED = 20;
    private static final int CONTENT_PADDING = 10;
    private static final float SMOOTH_SCROLL_FACTOR = 0.3f;
    private static final int CULLING_MARGIN = 50; // Extra pixels for smoother scrolling

    private static final Color SCROLLBAR_TRACK_COLOR = new Color(240, 240, 240);
    private static final Color SCROLLBAR_THUMB_COLOR = new Color(180, 180, 180);
    private static final Color SCROLLBAR_THUMB_HOVER_COLOR = new Color(150, 150, 150);
    private static final Color SCROLLBAR_THUMB_DRAG_COLOR = new Color(120, 120, 120);

    private boolean verticalScrollbarVisible;
    private boolean horizontalScrollbarVisible;
    private boolean isDraggingVerticalScrollbar;
    private boolean isDraggingHorizontalScrollbar;
    private boolean smoothScrollEnabled;
    private boolean autoUpdateContent = true;
    private boolean enableHorizontalScroll = false;

    private int dragStartY;
    private int dragStartX;
    private int dragStartScrollY;
    private int dragStartScrollX;

    private final Rectangle verticalThumbBounds = new Rectangle();
    private final Rectangle verticalTrackBounds = new Rectangle();
    private final Rectangle horizontalThumbBounds = new Rectangle();
    private final Rectangle horizontalTrackBounds = new Rectangle();

    private Color backgroundColor = Color.WHITE;
    private boolean showBorder = true;
    private Color borderColor = Color.LIGHT_GRAY;
    private float borderThickness = 1f;
    private int scrollSpeed = SCROLL_SPEED;
    private int contentPadding = CONTENT_PADDING;
    private float nextChildY = 0f;
    private int cornerRadius = 0;
    private boolean showScrollProgress = false;
    private Color progressColor = new Color(100, 150, 255);
    private int progressBarHeight = 3;

    // Performance optimization caches
    private int lastScrollY = -1;
    private int lastScrollX = -1;
    private int lastViewportWidth = -1;
    private int lastViewportHeight = -1;
    private boolean dimensionsChanged = true;
    private long lastContentUpdate = 0;
    private static final long CONTENT_UPDATE_THROTTLE_MS = 16; // ~60fps max update rate

    private GLFWScrollCallback scrollCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWCursorPosCallback cursorPosCallback;

    @SuppressWarnings("unused")
    public ScrollableFrame(Window window) {
        super(window);
        init();
    }

    @SuppressWarnings("unused")
    public ScrollableFrame(Window window, float width, float height) {
        super(window, width, height);
        init();
    }

    public ScrollableFrame(Window window, float x, float y, float width, float height) {
        super(window, x, y, width, height);
        init();
    }

    @Override
    public void init() {
        setupScrollCallback();
        setupMouseButtonCallback();
        setupCursorPosCallback();
        targetScrollY = scrollY;
        targetScrollX = scrollX;
    }

    @Override
    public void render() {
        if (!visible) return;

        // 1. Calculate Screen Positions
        int oldWidthPx = widthPx;
        int oldHeightPx = heightPx;

        if (parent != null) {
            widthPx = (int) (parent.getWidthPx() * width);
            heightPx = (int) (parent.getHeightPx() * height);
            xPx = parent.getxPx() + (int) (parent.getWidthPx() * x);
            yPx = parent.getyPx() + (int) (parent.getHeightPx() * y);
        } else {
            widthPx = (int) (windowParent.getWidth() * width);
            heightPx = (int) (windowParent.getHeight() * height);
            xPx = (int) (windowParent.getWidth() * x);
            yPx = (int) (windowParent.getHeight() * y);
        }

        dimensionsChanged = (oldWidthPx != widthPx || oldHeightPx != heightPx);
        updateHitBox();

        // 2. Only mark dirty if something actually changed
        if (dimensionsChanged || scrollY != lastScrollY || scrollX != lastScrollX) {
            markDirty();
            lastScrollY = scrollY;
            lastScrollX = scrollX;
        }

        // 3. Setup OpenGL State
        boolean wasDepthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        if (wasDepthTestEnabled) glDisable(GL_DEPTH_TEST);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 4. Draw the internal AWT image to the screen
        renderGUIImage();

        // 5. Cleanup
        glDisable(GL_BLEND);
        if (wasDepthTestEnabled) glEnable(GL_DEPTH_TEST);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (getWidthPx() <= 1 || getHeightPx() <= 1) {
            return;
        }

        // Throttle content updates to improve performance
        long currentTime = System.currentTimeMillis();
        boolean shouldUpdateContent = (currentTime - lastContentUpdate) > CONTENT_UPDATE_THROTTLE_MS;

        if (autoUpdateContent && (dimensionsChanged || shouldUpdateContent)) {
            updateContentSize();
            updateScrollbarVisibility();
            lastContentUpdate = currentTime;
        }

        if (smoothScrollEnabled) {
            applySmoothScroll();
        }

        Graphics2D g2 = (Graphics2D) g.create();
        if (Settings.getValue("SnowMemo.Defaults.AntiAliasing") != null &&
                (boolean)Settings.getValue("SnowMemo.Defaults.AntiAliasing")) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);

        int viewportWidth = getViewportWidth();
        int viewportHeight = getViewportHeight();

        // Draw Background
        g2.setColor(backgroundColor);
        if (cornerRadius > 0) {
            g2.fillRoundRect(0, 0, viewportWidth, viewportHeight, cornerRadius, cornerRadius);
        } else {
            g2.fillRect(0, 0, viewportWidth, viewportHeight);
        }

        // Setup Clipping for children
        Shape oldClip = g2.getClip();
        if (cornerRadius > 0) {
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, viewportWidth, viewportHeight, cornerRadius, cornerRadius));
        } else {
            g2.clipRect(0, 0, viewportWidth, viewportHeight);
        }

        g2.translate(-scrollX, -scrollY);
        renderChildren(g2);
        g2.translate(scrollX, scrollY);

        g2.setClip(oldClip);

        // Draw Border
        if (showBorder) {
            drawBorder(g2, viewportWidth, viewportHeight);
        }

        if (verticalScrollbarVisible) drawVerticalScrollbar(g2, viewportWidth, viewportHeight);
        if (horizontalScrollbarVisible) drawHorizontalScrollbar(g2, viewportWidth, viewportHeight);
        if (showScrollProgress && verticalScrollbarVisible) drawScrollProgress(g2, viewportWidth, viewportHeight);

        g2.dispose();
    }

    private void drawBorder(Graphics2D g2, int viewportWidth, int viewportHeight) {
        Stroke s = g2.getStroke();
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(borderThickness));
        float half = borderThickness / 2f;
        if (cornerRadius > 0) {
            g2.drawRoundRect(
                    (int)half, (int)half,
                    (int)(viewportWidth - borderThickness),
                    (int)(viewportHeight - borderThickness),
                    cornerRadius, cornerRadius
            );
        } else {
            g2.drawRect((int)half, (int)half,
                    (int)(viewportWidth - borderThickness),
                    (int)(viewportHeight - borderThickness));
        }
        g2.setStroke(s);
    }

    private void renderChildren(Graphics2D g2) {
        if (getChildren() == null || getChildren().isEmpty()) return;

        int contentAreaWidth = getWidthPx();
        int contentAreaHeight = getHeightPx();

        if (contentAreaWidth <= 0 || contentAreaHeight <= 0) return;

        int viewportHeight = getViewportHeight();
        int viewportWidth = getViewportWidth();

        // Pre-calculate scroll bounds with margin for better culling
        int scrollTop = scrollY - CULLING_MARGIN;
        int scrollBottom = scrollY + viewportHeight + CULLING_MARGIN;
        int scrollLeft = scrollX - CULLING_MARGIN;
        int scrollRight = scrollX + viewportWidth + CULLING_MARGIN;

        synchronized (getChildren()) {
            // Avoid creating new ArrayList if possible
            List<GUIComponent> children = new ArrayList<>(getChildren());
            for (GUIComponent child : children) {
                if (child == null || !child.isVisible()) continue;

                child.threadRendering = false;

                // Calculate child bounds
                int childWidth = (int) (child.getWidth() * contentAreaWidth);
                int childHeight = (int) (child.getHeight() * contentAreaHeight);
                int childX = (int) (child.getX() * contentAreaWidth);
                int childY = (int) (child.getY() * contentAreaHeight);

                // Optimized culling check
                if (childY + childHeight < scrollTop || childY > scrollBottom) continue;
                if (childX + childWidth < scrollLeft || childX > scrollRight) continue;

                // Save old dimensions
                int oldW = child.widthPx;
                int oldH = child.heightPx;

                // Ensure minimum dimensions
                child.widthPx = Math.max(1, childWidth);
                child.heightPx = Math.max(1, childHeight);

                Graphics2D childGraphics = (Graphics2D) g2.create();
                childGraphics.translate(childX, childY);

                try {
                    child.print(childGraphics);
                } catch (Exception e) {
                    System.err.println("Error rendering child in ScrollableFrame: " + e.getMessage());
                } finally {
                    childGraphics.dispose();
                    // Restore dimensions
                    child.widthPx = oldW;
                    child.heightPx = oldH;
                }
            }
        }
    }

    private void setupScrollCallback() {
        scrollCallback = new GLFWScrollCallback() {
            @Override
            public void invoke(long windowHandle, double xoffset, double yoffset) {
                if (!visible || hitBox == null) return;
                handleScroll(xoffset, yoffset);
            }
        };
        if (getWindowParent() != null && getWindowParent().ScrollCallbacks != null)
            getWindowParent().ScrollCallbacks.add(scrollCallback);
    }

    private void setupMouseButtonCallback() {
        mouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long windowHandle, int button, int action, int mods) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (action == GLFW.GLFW_PRESS) {
                        handleMousePress();
                        handleChildMousePress(button);
                    } else if (action == GLFW.GLFW_RELEASE) {
                        isDraggingVerticalScrollbar = false;
                        isDraggingHorizontalScrollbar = false;
                    }
                }
            }
        };
        if (getWindowParent() != null && getWindowParent().MouseButtonCallbacks != null)
            getWindowParent().MouseButtonCallbacks.add(mouseButtonCallback);
    }

    private void handleScroll(double xoffset, double yoffset) {
        Point2D mousePos = new Point2D.Double(getMousePos(getWindowParent()).getX() * 2, getMousePos(getWindowParent()).getY() * 2);
        if (mousePos == null || !hitBox.contains(mousePos)) return;

        if (verticalScrollbarVisible && yoffset != 0) {
            int newScroll = scrollY - (int) (yoffset * scrollSpeed);
            if (smoothScrollEnabled) {
                targetScrollY = Math.max(0, Math.min(getMaxScrollY(), newScroll));
            } else {
                scrollY = Math.max(0, Math.min(getMaxScrollY(), newScroll));
            }
        }

        if (horizontalScrollbarVisible && (xoffset != 0 || (!verticalScrollbarVisible && yoffset != 0))) {
            double scrollAmount = xoffset != 0 ? xoffset : yoffset;
            int newScroll = scrollX + (int) (scrollAmount * scrollSpeed);
            if (smoothScrollEnabled) {
                targetScrollX = Math.max(0, Math.min(getMaxScrollX(), newScroll));
            } else {
                scrollX = Math.max(0, Math.min(getMaxScrollX(), newScroll));
            }
        }
    }

    private void handleMousePress() {
        if (!visible) return;
        Point2D mousePos = getMousePos(getWindowParent());
        if (mousePos == null) return;

        if (verticalScrollbarVisible && verticalTrackBounds.contains(mousePos)) {
            if (verticalThumbBounds.contains(mousePos)) {
                isDraggingVerticalScrollbar = true;
                dragStartY = (int) mousePos.getY();
                dragStartScrollY = scrollY;
            } else {
                int thumbY = verticalThumbBounds.y;
                if (mousePos.getY() < thumbY) {
                    scrollY = Math.max(0, scrollY - getViewportHeight());
                } else {
                    scrollY = Math.min(getMaxScrollY(), scrollY + getViewportHeight());
                }
                targetScrollY = scrollY;
            }
        }

        if (horizontalScrollbarVisible && horizontalTrackBounds.contains(mousePos)) {
            if (horizontalThumbBounds.contains(mousePos)) {
                isDraggingHorizontalScrollbar = true;
                dragStartX = (int) mousePos.getX();
                dragStartScrollX = scrollX;
            } else {
                int thumbX = horizontalThumbBounds.x;
                if (mousePos.getX() < thumbX) {
                    scrollX = Math.max(0, scrollX - getViewportWidth());
                } else {
                    scrollX = Math.min(getMaxScrollX(), scrollX + getViewportWidth());
                }
                targetScrollX = scrollX;
            }
        }
    }

    private void handleMouseDrag(int mouseX, int mouseY) {
        if (isDraggingVerticalScrollbar) {
            int deltaY = mouseY - dragStartY;
            int trackHeight = getViewportHeight() - verticalThumbBounds.height;
            if (trackHeight > 0) {
                int maxScroll = getMaxScrollY();
                int newScroll = dragStartScrollY + (int) ((double) deltaY / trackHeight * maxScroll);
                scrollY = Math.max(0, Math.min(maxScroll, newScroll));
                targetScrollY = scrollY;
            }
        }

        if (isDraggingHorizontalScrollbar) {
            int deltaX = mouseX - dragStartX;
            int trackWidth = getViewportWidth() - horizontalThumbBounds.width;
            if (trackWidth > 0) {
                int maxScroll = getMaxScrollX();
                int newScroll = dragStartScrollX + (int) ((double) deltaX / trackWidth * maxScroll);
                scrollX = Math.max(0, Math.min(maxScroll, newScroll));
                targetScrollX = scrollX;
            }
        }
    }

    private void handleChildMousePress(int button) {
        if (!visible) return;
        Point2D mousePos = new Point((int) getMousePos(getWindowParent()).getX() * 2, (int) getMousePos(getWindowParent()).getY() * 2);

        if (!hitBox.contains(mousePos)) return;
        if (verticalScrollbarVisible && verticalTrackBounds.contains(mousePos)) return;
        if (horizontalScrollbarVisible && horizontalTrackBounds.contains(mousePos)) return;

        int adjustedMouseX = (int) (mousePos.getX() - xPx + scrollX);
        int adjustedMouseY = (int) (mousePos.getY() - yPx + scrollY);

        for (GUIComponent child : getChildren()) {
            if (child == null || !child.isVisible()) continue;

            int childX = (int) (child.getX() * getWidthPx());
            int childY = (int) (child.getY() * getHeightPx());
            int childWidth = (int) (child.getWidth() * getWidthPx());
            int childHeight = (int) (child.getHeight() * getHeightPx());

            if (adjustedMouseX >= childX && adjustedMouseX <= childX + childWidth &&
                    adjustedMouseY >= childY && adjustedMouseY <= childY + childHeight) {

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
                break;
            }
        }
    }

    private void applySmoothScroll() {
        if (scrollY != targetScrollY) {
            int diff = targetScrollY - scrollY;
            int delta = (int) (diff * SMOOTH_SCROLL_FACTOR);
            if (Math.abs(diff) < 2) scrollY = targetScrollY;
            else scrollY += Math.max(1, Math.abs(delta)) * Integer.signum(delta);
        }

        if (scrollX != targetScrollX) {
            int diff = targetScrollX - scrollX;
            int delta = (int) (diff * SMOOTH_SCROLL_FACTOR);
            if (Math.abs(diff) < 2) scrollX = targetScrollX;
            else scrollX += Math.max(1, Math.abs(delta)) * Integer.signum(delta);
        }
    }

    private void drawVerticalScrollbar(Graphics2D g2d, int viewWidth, int viewHeight) {
        int y = 0;
        int width = SCROLLBAR_WIDTH;
        int height = horizontalScrollbarVisible ? viewHeight - SCROLLBAR_WIDTH : viewHeight;

        verticalTrackBounds.setBounds(getxPx() + viewWidth, getyPx() + y, width, height);
        g2d.setColor(SCROLLBAR_TRACK_COLOR);
        g2d.fillRect(viewWidth, y, width, height);

        int maxScroll = getMaxScrollY();
        if (maxScroll > 0) {
            int thumbHeight = Math.max(MIN_THUMB_SIZE, (int) ((double) viewHeight / contentHeight * height));
            thumbHeight = Math.min(thumbHeight, height - 4);
            int availableTrack = height - thumbHeight;
            int thumbY = y + (availableTrack > 0 ? (int) ((double) scrollY / maxScroll * availableTrack) : 0);

            verticalThumbBounds.setBounds(getxPx() + viewWidth + 2, getyPx() + thumbY, width - 4, thumbHeight);

            Point2D mousePos = getMousePos(getWindowParent());
            boolean hovering = mousePos != null && verticalThumbBounds.contains(mousePos);
            Color thumbColor = isDraggingVerticalScrollbar ? SCROLLBAR_THUMB_DRAG_COLOR :
                    hovering ? SCROLLBAR_THUMB_HOVER_COLOR : SCROLLBAR_THUMB_COLOR;

            g2d.setColor(thumbColor);
            g2d.fillRoundRect(viewWidth + 2, thumbY, width - 4, thumbHeight, 4, 4);
        }
    }

    private void drawHorizontalScrollbar(Graphics2D g2d, int viewWidth, int viewHeight) {
        int x = 0;
        int width = verticalScrollbarVisible ? viewWidth - SCROLLBAR_WIDTH : viewWidth;
        int height = SCROLLBAR_WIDTH;

        horizontalTrackBounds.setBounds(getxPx() + x, getyPx() + viewHeight, width, height);
        g2d.setColor(SCROLLBAR_TRACK_COLOR);
        g2d.fillRect(x, viewHeight, width, height);

        int maxScroll = getMaxScrollX();
        if (maxScroll > 0) {
            int thumbWidth = Math.max(MIN_THUMB_SIZE, (int) ((double) viewWidth / contentWidth * width));
            thumbWidth = Math.min(thumbWidth, width - 4);
            int availableTrack = width - thumbWidth;
            int thumbX = x + (availableTrack > 0 ? (int) ((double) scrollX / maxScroll * availableTrack) : 0);

            horizontalThumbBounds.setBounds(getxPx() + thumbX, getyPx() + viewHeight + 2, thumbWidth, height - 4);

            Point2D mousePos = getMousePos(getWindowParent());
            boolean hovering = mousePos != null && horizontalThumbBounds.contains(mousePos);
            Color thumbColor = isDraggingHorizontalScrollbar ? SCROLLBAR_THUMB_DRAG_COLOR :
                    hovering ? SCROLLBAR_THUMB_HOVER_COLOR : SCROLLBAR_THUMB_COLOR;

            g2d.setColor(thumbColor);
            g2d.fillRoundRect(thumbX, viewHeight + 2, thumbWidth, height - 4, 4, 4);
        }

        if (verticalScrollbarVisible) {
            g2d.setColor(SCROLLBAR_TRACK_COLOR);
            g2d.fillRect(viewWidth, viewHeight, SCROLLBAR_WIDTH, height);
        }
    }

    @SuppressWarnings("unused")
    private void drawScrollProgress(Graphics2D g2d, int viewWidth, int viewHeight) {
        int maxScroll = getMaxScrollY();
        if (maxScroll <= 0) return;

        float progress = (float) scrollY / maxScroll;
        int progressWidth = (int) (viewWidth * progress);

        g2d.setColor(progressColor);
        g2d.fillRect(0, 0, progressWidth, progressBarHeight);
    }

    public void updateContentSize() {
        int minHeight = getHeightPx();
        int minWidth = getWidthPx();
        contentHeight = minHeight;
        contentWidth = minWidth;

        if (getChildren() == null || getChildren().isEmpty()) return;

        int contentAreaHeight = getHeightPx();
        int contentAreaWidth = getWidthPx();

        for (GUIComponent child : getChildren()) {
            if (child == null) continue;

            float childY = child.getY();
            float childH = child.getHeight();
            int childBottom = (int) ((childY + childH) * contentAreaHeight);
            contentHeight = Math.max(contentHeight, childBottom);

            if (enableHorizontalScroll) {
                float childX = child.getX();
                float childW = child.getWidth();
                int childRight = (int) ((childX + childW) * contentAreaWidth);
                contentWidth = Math.max(contentWidth, childRight);
            }
        }

        contentHeight += contentPadding;
        if (enableHorizontalScroll) {
            contentWidth += contentPadding;
        }
    }

    public void updateScrollbarVisibility() {
        int viewHeight = getHeightPx();
        int viewWidth = getWidthPx();

        boolean needsVertical = contentHeight > viewHeight;
        boolean needsHorizontal = enableHorizontalScroll && contentWidth > viewWidth;

        verticalScrollbarVisible = needsVertical;
        horizontalScrollbarVisible = needsHorizontal;

        scrollY = Math.max(0, Math.min(getMaxScrollY(), scrollY));
        targetScrollY = Math.max(0, Math.min(getMaxScrollY(), targetScrollY));
        scrollX = Math.max(0, Math.min(getMaxScrollX(), scrollX));
        targetScrollX = Math.max(0, Math.min(getMaxScrollX(), targetScrollX));
    }

    private int getMaxScrollY() {
        return Math.max(0, contentHeight - getViewportHeight());
    }

    private int getMaxScrollX() {
        return Math.max(0, contentWidth - getViewportWidth());
    }

    @Override
    public void cleanUp() {
        if (getWindowParent() != null) {
            if (scrollCallback != null && getWindowParent().ScrollCallbacks != null) {
                getWindowParent().ScrollCallbacks.remove(scrollCallback);
                scrollCallback.free();
                scrollCallback = null;
            }
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

    public void clearChildren() {
        getChildren().clear();
        nextChildY = 0f;
        if (autoUpdateContent) {
            updateContentSize();
            updateScrollbarVisibility();
        }
    }

    private void setupCursorPosCallback() {
        cursorPosCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long windowHandle, double xpos, double ypos) {
                handleMouseDrag((int) xpos, (int) ypos);
                handleChildMouseMove((int) xpos, (int) ypos);
            }
        };
        if (getWindowParent() != null && getWindowParent().CursorPosCallbacks != null)
            getWindowParent().CursorPosCallbacks.add(cursorPosCallback);
    }

    private void handleChildMouseMove(int mouseX, int mouseY) {
        if (!visible) return;
        Point2D mousePos = new Point(mouseX * 2, mouseY * 2);

        if (!hitBox.contains(mousePos)) {
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

        if (verticalScrollbarVisible && verticalTrackBounds.contains(mousePos)) return;
        if (horizontalScrollbarVisible && horizontalTrackBounds.contains(mousePos)) return;

        int adjustedMouseX = (int) (mousePos.getX() - xPx + scrollX);
        int adjustedMouseY = (int) (mousePos.getY() - yPx + scrollY);

        for (GUIComponent child : getChildren()) {
            if (child == null || !child.isVisible()) continue;

            int childX = (int) (child.getX() * getWidthPx());
            int childY = (int) (child.getY() * getHeightPx());
            int childWidth = (int) (child.getWidth() * getWidthPx());
            int childHeight = (int) (child.getHeight() * getHeightPx());

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

    // Getters and Setters
    @SuppressWarnings("unused")
    public int getScrollY() { return scrollY; }
    public void setScrollY(int scrollY) {
        this.scrollY = Math.max(0, Math.min(getMaxScrollY(), scrollY));
        this.targetScrollY = this.scrollY;
    }
    @SuppressWarnings("unused")
    public int getScrollX() { return scrollX; }
    public void setScrollX(int scrollX) {
        this.scrollX = Math.max(0, Math.min(getMaxScrollX(), scrollX));
        this.targetScrollX = this.scrollX;
    }

    @SuppressWarnings("unused")
    public void scrollToTop() { setScrollY(0); }
    @SuppressWarnings("unused")
    public void scrollToBottom() { setScrollY(getMaxScrollY()); }
    @SuppressWarnings("unused")
    public void scrollToLeft() { setScrollX(0); }
    @SuppressWarnings("unused")
    public void scrollToRight() { setScrollX(getMaxScrollX()); }
    @SuppressWarnings("unused")
    public float getBorderThickness() {
        return borderThickness;
    }

    public ScrollableFrame setBorderThickness(float borderThickness) {
        this.borderThickness = borderThickness;
        return this;
    }

    public Color getBackgroundColor() { return backgroundColor; }
    public ScrollableFrame setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public boolean isShowBorder() { return showBorder; }
    public ScrollableFrame setShowBorder(boolean showBorder) {
        this.showBorder = showBorder;
        return this;
    }

    public Color getBorderColor() { return borderColor; }
    public ScrollableFrame setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    public boolean isSmoothScrollEnabled() { return smoothScrollEnabled; }
    public ScrollableFrame setSmoothScrollEnabled(boolean smoothScrollEnabled) {
        this.smoothScrollEnabled = smoothScrollEnabled;
        return this;
    }

    public boolean isEnableHorizontalScroll() { return enableHorizontalScroll; }
    public ScrollableFrame setEnableHorizontalScroll(boolean enableHorizontalScroll) {
        this.enableHorizontalScroll = enableHorizontalScroll;
        return this;
    }

    public int getScrollSpeed() { return scrollSpeed; }
    public ScrollableFrame setScrollSpeed(int scrollSpeed) {
        this.scrollSpeed = Math.max(1, scrollSpeed);
        return this;
    }

    public int getContentPadding() { return contentPadding; }
    public ScrollableFrame setContentPadding(int contentPadding) {
        this.contentPadding = Math.max(0, contentPadding);
        return this;
    }

    public boolean isAutoUpdateContent() { return autoUpdateContent; }
    public ScrollableFrame setAutoUpdateContent(boolean autoUpdateContent) {
        this.autoUpdateContent = autoUpdateContent;
        return this;
    }

    public int getViewportWidth() {
        return getWidthPx();
    }

    public int getViewportHeight() {
        return Math.max(1, getHeightPx());
    }

    public int getContentHeight() { return contentHeight; }
    public int getContentWidth() { return contentWidth; }
    public boolean isVerticalScrollbarVisible() { return verticalScrollbarVisible; }
    public boolean isHorizontalScrollbarVisible() { return horizontalScrollbarVisible; }

    public int getCornerRadius() { return cornerRadius; }
    public ScrollableFrame setCornerRadius(int cornerRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
        return this;
    }

    public boolean isShowScrollProgress() { return showScrollProgress; }
    public ScrollableFrame setShowScrollProgress(boolean showScrollProgress) {
        this.showScrollProgress = showScrollProgress;
        return this;
    }

    public Color getProgressColor() { return progressColor; }
    public ScrollableFrame setProgressColor(Color progressColor) {
        this.progressColor = progressColor;
        return this;
    }
    @SuppressWarnings("unused")
    public int getProgressBarHeight() { return progressBarHeight; }
    @SuppressWarnings("unused")
    public ScrollableFrame setProgressBarHeight(int progressBarHeight) {
        this.progressBarHeight = Math.max(1, progressBarHeight);
        return this;
    }
    @SuppressWarnings("unused")
    public float getScrollProgress() {
        int maxScroll = getMaxScrollY();
        return maxScroll > 0 ? (float) scrollY / maxScroll : 0f;
    }

    @Override
    public ScrollableFrame addChild(GUIComponent child) {
        child.setY(nextChildY);
        child.CustomMouseEvents = true;
        nextChildY += child.getHeight();
        super.addChild(child);
        child.threadRendering = false;
        if (autoUpdateContent) {
            updateContentSize();
            updateScrollbarVisibility();
        }
        return this;
    }

    @Override
    public ScrollableFrame removeChild(GUIComponent child) {
        super.removeChild(child);
        if (autoUpdateContent) {
            updateContentSize();
            updateScrollbarVisibility();
        }
        return this;
    }
}