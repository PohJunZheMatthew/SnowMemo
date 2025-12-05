package Main.Settings;

import GUI.BillBoardGUI.BillboardGUI;
import GUI.BillBoardGUI.LabelBillboardGUI;
import GUI.GUIComponent;
import Main.Camera;
import Main.SnowMemo;
import Main.Utils;
import Main.Window;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class LinkSettingsAttribute extends BillboardGUI implements SettingsAttribute<Object> {
    private boolean isHovered = false;
    private boolean isPressed = false;
    private final String buttonText;
    private final URI uri;
    private long handCursor;
    private long defaultCursor;
    private LabelBillboardGUI label;
    private final Vector3f originalPosition = new Vector3f(-0f,0,0.3f);
    private Vector3f offsetPosition = new Vector3f();
    private int index;

    public LinkSettingsAttribute(Window window, String text, String btnText, URI uri){
        super(window, new LinkButtonComponent(window, btnText));
        label = new LabelBillboardGUI(window,text);
        label.setAutoScale(false);
        label.setVisible(true);
        this.buttonText = btnText;
        this.uri = uri;

        // Create hand cursor for hover
        handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
        defaultCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);

        LinkButtonComponent component = (LinkButtonComponent) mainGUIComponent;
        component.setParent(this);

        setRotation(new Vector3f((float) 0, (float) Math.PI, (float) Math.PI));
        setPosition(new Vector3f(0, 0, 0.3f));
        label.setRotation(new Vector3f((float) 0, (float) Math.PI, (float) Math.PI));
        label.setPosition(new Vector3f(-1,0,0.3f));
        window.MouseButtonCallbacks.add(new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long windowHandle, int button, int action, int mods) {
                Vector3f cursorPos = window.getCursorWorldPosAtZ(getPosition().z);
                boolean hovering = collidesWith(cursorPos)&&isVisible()&&isRenderVisible();

                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (action == GLFW.GLFW_PRESS && hovering) {
                        isPressed = true;
                        markDirty();
                    } else if (action == GLFW.GLFW_RELEASE) {
                        if (isPressed && hovering) {
                            // Button was clicked
                            Utils.BrowserUtil.openURI(uri);
                            System.out.println("Opening link: " + uri);
                        }
                        isPressed = false;
                        markDirty();
                    }
                }
            }
        });

        // Track hover state
        window.CursorPosCallbacks.add(new org.lwjgl.glfw.GLFWCursorPosCallback() {
            @Override
            public void invoke(long windowHandle, double xpos, double ypos) {
                Vector3f cursorPos = window.getCursorWorldPosAtZ(getPosition().z);
                boolean nowHovering = collidesWith(cursorPos)&&isVisible()&&isRenderVisible();

                if (nowHovering != isHovered) {
                    isHovered = nowHovering;

                    // Change cursor based on hover state
                    if (isHovered) {
                        GLFW.glfwSetCursor(window.getWindowHandle(), handCursor);
                    } else {
                        GLFW.glfwSetCursor(window.getWindowHandle(), defaultCursor);
                    }

                    markDirty();
                }
            }
        });
    }

    private static class LinkButtonComponent extends GUIComponent {
        private LinkSettingsAttribute parent;
        private String buttonText;

        public LinkButtonComponent(Window window, String btnText) {
            super(window, 256, 64);
            this.buttonText = btnText;
        }

        public void setParent(LinkSettingsAttribute parent) {
            this.parent = parent;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (parent == null) return;

            Graphics2D g2d = (Graphics2D) g.create();

            try {
                // Clear background (transparent)
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, getWidthPx(), getHeightPx());
                g2d.setComposite(AlphaComposite.SrcOver);

                // Setup rendering hints
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);

                int panelWidth = getWidthPx();
                int panelHeight = getHeightPx();

                // Button dimensions
                int buttonX = 10;
                int buttonY = 0;
                int buttonWidth = panelWidth - 20;
                int buttonHeight = panelHeight;
                int cornerRadius = 25;

                RoundRectangle2D buttonRect = new RoundRectangle2D.Float(
                        buttonX, buttonY, buttonWidth, buttonHeight, cornerRadius, cornerRadius
                );

                // Determine button colors based on state
                Color bgColor, borderColor, textColor;

                if (parent.isPressed) {
                    // Pressed state - darker
                    bgColor = new Color(100, 150, 255);
                    borderColor = new Color(50, 100, 200);
                    textColor = Color.WHITE;
                } else if (parent.isHovered) {
                    // Hover state - brighter
                    bgColor = new Color(150, 180, 255);
                    borderColor = new Color(80, 120, 220);
                    textColor = Color.WHITE;
                } else {
                    // Default state
                    bgColor = new Color(180, 200, 255);
                    borderColor = new Color(100, 140, 230);
                    textColor = new Color(40, 40, 40);
                }

                // Draw shadow
                if (!parent.isPressed) {
                    g2d.setColor(new Color(0, 0, 0, 50));
                    g2d.fillRoundRect(
                            buttonX + 3, buttonY + 3,
                            buttonWidth, buttonHeight,
                            cornerRadius, cornerRadius
                    );
                }

                // Draw button background
                g2d.setColor(bgColor);
                g2d.fill(buttonRect);

                // Draw button border
                g2d.setColor(borderColor);
                g2d.setStroke(new BasicStroke(2.5f));
                g2d.draw(buttonRect);

                // Draw icon (link symbol)
                int iconSize = buttonHeight / 3;
                int iconX = buttonX + 15;
                int iconY = buttonY + buttonHeight / 2 - iconSize / 2;

                g2d.setColor(textColor);
                g2d.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                // Draw chain link icon
                int link1X = iconX;
                int link1Y = iconY + iconSize / 4;
                int link2X = iconX + iconSize / 2;
                int link2Y = iconY + (iconSize * 3) / 4;

                g2d.drawArc(link1X - iconSize / 4, link1Y - iconSize / 4,
                        iconSize / 2, iconSize / 2, 45, 180);
                g2d.drawArc(link2X - iconSize / 4, link2Y - iconSize / 4,
                        iconSize / 2, iconSize / 2, 225, 180);
                g2d.drawLine(link1X, link1Y, link2X, link2Y);

                // Draw text
                float PADDING_RATIO = 0.65f;
                int maxWidth = (int) (buttonWidth * PADDING_RATIO);
                int maxHeight = (int) (buttonHeight * 0.6f);

                g2d.setColor(textColor);
                renderAutoScaled(g2d, buttonText, maxWidth, maxHeight,
                        buttonX + iconSize + 30, buttonY,
                        buttonWidth - iconSize - 40, buttonHeight);

            } finally {
                g2d.dispose();
            }
        }

        private void renderAutoScaled(Graphics2D g2d, String text, int maxWidth, int maxHeight,
                                      int regionX, int regionY, int regionWidth, int regionHeight) {
            Font baseFont = SnowMemo.currentTheme.getFonts()[0];
            float minSize = 12;
            float maxSize = Math.max(maxWidth, maxHeight);
            float optimalSize = maxSize;

            int fontStyle = Font.BOLD;

            while (maxSize - minSize > 1) {
                float testSize = (minSize + maxSize) / 2;
                Font testFont = baseFont.deriveFont(fontStyle, testSize);

                FontMetrics fm = g2d.getFontMetrics(testFont);
                Rectangle2D bounds = fm.getStringBounds(text, g2d);
                int textWidth = (int) bounds.getWidth();
                int textHeight = fm.getAscent();

                if (textWidth <= maxWidth && textHeight <= maxHeight) {
                    optimalSize = testSize;
                    minSize = testSize;
                } else {
                    maxSize = testSize;
                }
            }

            Font font = baseFont.deriveFont(fontStyle, optimalSize);
            g2d.setFont(font);

            FontMetrics fm = g2d.getFontMetrics();
            Rectangle2D bounds = fm.getStringBounds(text, g2d);
            int textWidth = (int) bounds.getWidth();
            int textHeight = fm.getAscent();

            // Center the text in the region
            int x = regionX + (regionWidth - textWidth) / 2;
            int y = regionY + (regionHeight - textHeight) / 2 + textHeight;

            g2d.drawString(text, x, y);
        }
    }

    @Override
    public SettingsAttribute<Object> setValue(Object value) {
        return this;
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public void toDefault() {
        setValue(null);
    }

    @Override
    public float getHeight() {
        return Math.max(label.getScale().y,scale.y)-0.5f;
    }

    @Override
    public SettingsAttribute<Object> setY(float y) {
        // Store the base Y position
        originalPosition.y = y;
        // Apply Y position including any offset
        Vector3f newPos = new Vector3f(originalPosition).add(offsetPosition);
        setPosition(newPos);
        return this;
    }

    @Override
    public SettingsAttribute<Object> setOffsetPos(Vector3f pos) {
        offsetPosition.set(pos);
        // Apply offset to the actual position
        Vector3f newPos = new Vector3f(originalPosition).add(offsetPosition);
        setPosition(newPos);
        return this;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public SettingsAttribute<Object> setIndex(int index) {
        this.index = index;
        return this;
    }

    /**
     * Clean up cursor resources
     */
    public void cleanup() {
        if (handCursor != 0) {
            GLFW.glfwDestroyCursor(handCursor);
        }
        if (defaultCursor != 0) {
            GLFW.glfwDestroyCursor(defaultCursor);
        }
    }

    @Override
    public void render(Camera c) {
        updateHitBox();
        if(Math.abs(c.getPosition().y-getPosition().y)>3f) return;
        this.beginOcclusionQuery(c);
        mainGUIComponent.markDirty();
        label.setPosition(new Vector3f(getPosition()).add(-1.25f,0,0));
        markDirty();
        forceUpdateNow();
        super.render(c);
        label.render(c);
    }
    @Override
    public void cleanUp(){
        super.cleanUp();
        label.cleanUp();
    }
}