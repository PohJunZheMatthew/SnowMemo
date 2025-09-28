package GUI;

import GUI.Events.Event;
import GUI.Events.EventCallBack;
import GUI.Events.MouseClickCallBack;
import GUI.Events.MouseClickEvent;
import Main.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;

public class ScrollableFrame extends GUIComponent {

    private float scrollX = 0f;
    private float scrollY = 0f;
    private float maxScrollX = 0f;
    private float maxScrollY = 0f;

    private float contentWidth = 0f;
    private float contentHeight = 0f;

    private boolean showHorizontalScrollbar = true;
    private boolean showVerticalScrollbar = true;
    private final int scrollbarWidth = 12;
    private final Color scrollbarColor = new Color(180, 180, 180);
    private final Color scrollbarHandleColor = new Color(120, 120, 120);
    private final Color scrollbarHandleHoverColor = new Color(100, 100, 100);

    private boolean isDraggingVertical = false;
    private boolean isDraggingHorizontal = false;
    private boolean isHoveringVerticalHandle = false;
    private boolean isHoveringHorizontalHandle = false;
    private int dragStartY = 0;
    private int dragStartX = 0;
    private float dragStartScrollY = 0f;
    private float dragStartScrollX = 0f;

    private float wheelSensitivity = 30f;

    private static final HashMap<Window, GLFWScrollCallback> scrollCallbacks = new HashMap<>();
    private static final Set<Window> scrollInitializedWindows = new HashSet<>();

    private Color backgroundColor = Color.WHITE;

    public ScrollableFrame(Window window) {
        super(window);
        initializeScrolling(window);
        setupScrollbarInteraction();
    }

    public ScrollableFrame(Window window, float width, float height) {
        super(window, width, height);
        initializeScrolling(window);
        setupScrollbarInteraction();
    }

    public ScrollableFrame(Window window, float x, float y, float width, float height) {
        super(window, x, y, width, height);
        initializeScrolling(window);
        setupScrollbarInteraction();
    }
    private static synchronized void initializeScrolling(Window window) {
        if (scrollInitializedWindows.contains(window)) return;
        GLFWScrollCallback scrollCallback = new GLFWScrollCallback() {
            @Override
            public void invoke(long windowHandle, double xoffset, double yoffset) {
                Point2D pos = GUIComponent.getMousePos(window);
                if (pos == null) return;
                double mx = pos.getX();
                double my = pos.getY();
                GUIComponent top = GUIComponent.getTopComponentAt(window, mx, my);
                if (top == null) return;
                ScrollableFrame target = GUIComponent.findScrollableAncestor(top);
                if (target != null) target.handleMouseWheel(yoffset);
            }
        };
        window.ScrollCallbacks.add(scrollCallback);
        scrollCallbacks.put(window, scrollCallback);
        scrollInitializedWindows.add(window);
    }


    private void setupScrollbarInteraction() {
        this.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent event) {
                handleMouseClick(event);
            }
        });
    }

    private void handleMouseClick(MouseClickEvent event) {
        int mouseX = event.getMouseX() - getxPx();
        int mouseY = event.getMouseY() - getyPx();

        if (showVerticalScrollbar && mouseX >= getWidthPx() - scrollbarWidth && mouseX <= getWidthPx()) {
            Rectangle verticalHandle = getVerticalScrollbarHandle();
            if (verticalHandle.contains(mouseX, mouseY)) {
                isDraggingVertical = true;
                dragStartY = event.getMouseY();
                dragStartScrollY = scrollY;
            } else {
                float clickRatio = (float) mouseY / (getHeightPx() - (showHorizontalScrollbar ? scrollbarWidth : 0));
                scrollY = clickRatio * maxScrollY;
                clampScroll();
            }
        }

        if (showHorizontalScrollbar && mouseY >= getHeightPx() - scrollbarWidth && mouseY <= getHeightPx()) {
            Rectangle horizontalHandle = getHorizontalScrollbarHandle();
            if (horizontalHandle.contains(mouseX, mouseY)) {
                isDraggingHorizontal = true;
                dragStartX = event.getMouseX();
                dragStartScrollX = scrollX;
            } else {
                float clickRatio = (float) mouseX / (getWidthPx() - (showVerticalScrollbar ? scrollbarWidth : 0));
                scrollX = clickRatio * maxScrollX;
                clampScroll();
            }
        }
    }

    public void handleMouseWheel(double yoffset) {
        scrollY -= (float) yoffset * wheelSensitivity;
        clampScroll();
    }

    private void calculateContentBounds() {
        contentWidth = 0f;
        contentHeight = 0f;

        for (GUIComponent child : getChildren()) {
            float childRight = child.getX() + child.getWidth();
            float childBottom = child.getY() + child.getHeight();

            if (childRight > contentWidth) {
                contentWidth = childRight;
            }
            if (childBottom > contentHeight) {
                contentHeight = childBottom;
            }
        }

        float contentWidthPx = contentWidth * getWindowParent().getWidth();
        float contentHeightPx = contentHeight * getWindowParent().getHeight();

        maxScrollX = Math.max(0, contentWidthPx - getWidthPx() + (showVerticalScrollbar ? scrollbarWidth : 0));
        maxScrollY = Math.max(0, contentHeightPx - getHeightPx() + (showHorizontalScrollbar ? scrollbarWidth : 0));

        showHorizontalScrollbar = maxScrollX > 0;
        showVerticalScrollbar = maxScrollY > 0;
    }

    private void clampScroll() {
        scrollX = Math.max(0, Math.min(scrollX, maxScrollX));
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
    }

    private Rectangle getVerticalScrollbarHandle() {
        if (!showVerticalScrollbar) return new Rectangle(0, 0, 0, 0);

        int trackHeight = getHeightPx() - (showHorizontalScrollbar ? scrollbarWidth : 0);
        int handleHeight = Math.max(20, (int) ((float) trackHeight * getHeightPx() / (getHeightPx() + maxScrollY)));
        int handleY = (int) ((scrollY / maxScrollY) * (trackHeight - handleHeight));

        return new Rectangle(getWidthPx() - scrollbarWidth, handleY, scrollbarWidth, handleHeight);
    }

    private Rectangle getHorizontalScrollbarHandle() {
        if (!showHorizontalScrollbar) return new Rectangle(0, 0, 0, 0);

        int trackWidth = getWidthPx() - (showVerticalScrollbar ? scrollbarWidth : 0);
        int handleWidth = Math.max(20, (int) ((float) trackWidth * getWidthPx() / (getWidthPx() + maxScrollX)));
        int handleX = (int) ((scrollX / maxScrollX) * (trackWidth - handleWidth));

        return new Rectangle(handleX, getHeightPx() - scrollbarWidth, handleWidth, scrollbarWidth);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!visible) return;

        calculateContentBounds();
        clampScroll();
        Graphics2D g2d = (Graphics2D) g;

        g2d.setColor(backgroundColor);
        g2d.fillRect(0, 0, getWidthPx(), getHeightPx());

        int contentAreaWidth = getWidthPx() - (showVerticalScrollbar ? scrollbarWidth : 0);
        int contentAreaHeight = getHeightPx() - (showHorizontalScrollbar ? scrollbarWidth : 0);

        Rectangle2D originalClip = g2d.getClipBounds();
        g2d.setClip(0, 0, contentAreaWidth, contentAreaHeight);

        g2d.translate(-(int) scrollX, -(int) scrollY);

        for (GUIComponent child : getChildren()) {
            if (child.visible) {
                Graphics2D childGraphics = (Graphics2D) g2d.create();
                childGraphics.translate(
                        (int)(child.getX() * getWindowParent().getWidth()),
                        (int)(child.getY() * getWindowParent().getHeight())
                );
                child.paintComponent(childGraphics);
                childGraphics.dispose();
            }
        }

        paintScrollableContent(g2d);

        g2d.translate((int) scrollX, (int) scrollY);
        g2d.setClip(originalClip);

        drawScrollbars(g2d);
    }

    protected void paintScrollableContent(Graphics2D g) {

    }

    private void drawScrollbars(Graphics2D g) {
        if (showVerticalScrollbar) {
            g.setColor(scrollbarColor);
            g.fillRect(getWidthPx() - scrollbarWidth, 0, scrollbarWidth,
                    getHeightPx() - (showHorizontalScrollbar ? scrollbarWidth : 0));

            Rectangle handleRect = getVerticalScrollbarHandle();
            g.setColor(isHoveringVerticalHandle ? scrollbarHandleHoverColor : scrollbarHandleColor);
            g.fillRect(handleRect.x, handleRect.y, handleRect.width, handleRect.height);
        }

        if (showHorizontalScrollbar) {
            g.setColor(scrollbarColor);
            g.fillRect(0, getHeightPx() - scrollbarWidth,
                    getWidthPx() - (showVerticalScrollbar ? scrollbarWidth : 0), scrollbarWidth);

            Rectangle handleRect = getHorizontalScrollbarHandle();
            g.setColor(isHoveringHorizontalHandle ? scrollbarHandleHoverColor : scrollbarHandleColor);
            g.fillRect(handleRect.x, handleRect.y, handleRect.width, handleRect.height);
        }

        if (showVerticalScrollbar && showHorizontalScrollbar) {
            g.setColor(scrollbarColor);
            g.fillRect(getWidthPx() - scrollbarWidth, getHeightPx() - scrollbarWidth,
                    scrollbarWidth, scrollbarWidth);
        }
    }

    @Override
    public void init() {

    }

    @Override
    public GUIComponent addChild(GUIComponent child) {
        super.addChild(child);

        if (child != null) {
            adjustChildForScroll(child);
        }

        return this;
    }

    private void adjustChildForScroll(GUIComponent child) {

    }

    public float getScrollX() { return scrollX; }
    public void setScrollX(float scrollX) {
        this.scrollX = scrollX;
        clampScroll();
    }

    public float getScrollY() { return scrollY; }
    public void setScrollY(float scrollY) {
        this.scrollY = scrollY;
        clampScroll();
    }

    public Color getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public float getWheelSensitivity() { return wheelSensitivity; }
    public void setWheelSensitivity(float wheelSensitivity) {
        this.wheelSensitivity = wheelSensitivity;
    }

    public boolean isShowHorizontalScrollbar() { return showHorizontalScrollbar; }
    public void setShowHorizontalScrollbar(boolean show) {
        this.showHorizontalScrollbar = show;
    }

    public boolean isShowVerticalScrollbar() { return showVerticalScrollbar; }
    public void setShowVerticalScrollbar(boolean show) {
        this.showVerticalScrollbar = show;
    }

    public void scrollToTop() {
        setScrollY(0);
    }

    public void scrollToBottom() {
        setScrollY(maxScrollY);
    }

    public void scrollToLeft() {
        setScrollX(0);
    }

    public void scrollToRight() {
        setScrollX(maxScrollX);
    }

    public static void cleanupScrollWindow(Window window) {
        GLFWScrollCallback scrollCallback = scrollCallbacks.remove(window);
        if (scrollCallback != null) {
            scrollCallback.free();
        }
        scrollInitializedWindows.remove(window);
    }

    @Override
    public void cleanUp() {
        super.cleanUp();
        cleanupScrollWindow(getWindowParent());
    }

}