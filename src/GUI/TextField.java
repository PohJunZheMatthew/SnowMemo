package GUI;

import GUI.Events.*;
import Main.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFWKeyCallback;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA;
import static org.lwjgl.glfw.GLFW.glfwSetDropCallback;

public class TextField extends GUIComponent {

    private String text = "", placeholder = "";
    private Font baseFont = new Font("SansSerif", Font.PLAIN, 16);
    private Font scaledFont = baseFont;
    private Color textColor = new Color(33, 37, 41);
    private Color backgroundColor = new Color(245, 245, 245);
    private Color borderColor = new Color(200, 200, 200);
    private Color focusedBorderColor = new Color(0, 123, 255);
    private Color placeholderColor = new Color(150, 150, 150);
    private Color selectionColor = new Color(0, 123, 255, 50);
    private Color cursorColor = new Color(33, 37, 41);

    private int borderWidth = 2;
    private int padding = 10;
    private int cornerRadius = 12;

    private boolean focused = false;
    private int cursorPosition = 0;
    private int selectionStart = -1, selectionEnd = -1;
    private boolean showCursor = true;
    private long lastBlink = System.currentTimeMillis();
    private static final long CURSOR_BLINK = 500;

    private int scrollOffset = 0;

    public TextField(Window window) { this(window, 0.3f, 0.06f); }
    public TextField(Window window, float width, float height) { this(window, 0, 0, width, height); }
    public TextField(Window window, float x, float y, float width, float height) {
        super(window, x, y, width, height);
        initCallbacks(window);
    }

    private void initCallbacks(Window window) {
        window.DropCallbacks.add(new GLFWDropCallback() {
            @Override
            public void invoke(long window, int count, long names) {
                for (int i = 0; i < count; i++) {
                    String droppedText = GLFWDropCallback.getName(names, i);
                    insertText(droppedText); // insert into textfield
                }
            }
        });
        window.KeyCallbacks.add(new GLFWKeyCallback() {
            @Override
            public void invoke(long win, int key, int scancode, int action, int mods) {
                if (!focused) return;
                if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return;
                handleKey(key, mods);
            }
        });

        window.CharCallbacks.add(new GLFWCharCallback() {
            @Override
            public void invoke(long win, int codepoint) {
                if (!focused) return;
                if (!Character.isDefined(codepoint)) return;
                insertText(new String(Character.toChars(codepoint)));
            }
        });

        addCallBack((MouseClickCallBack) e -> {
            focused = true;
            int clickX = e.getMouseX() - getxPx() - padding + scrollOffset;
            int newCursor = 0, textWidth = 0;
            for (int i = 0; i < text.length(); i++) {
                int w = measureTextWidth(text.substring(i, i + 1));
                if (textWidth + w / 2 > clickX) break;
                textWidth += w;
                newCursor = i + 1;
            }
            cursorPosition = Math.min(newCursor, text.length());
            clearSelection();
            resetCursorBlink();
            updateScrollOffset();
        });
    }

    private void handleKey(int key, int mods) {
        boolean ctrl = (mods & 0x0002) != 0 || mods == GLFW.GLFW_MOD_SUPER;
        boolean shift = (mods & 0x0001) != 0;

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) deleteSelection();
                else if (cursorPosition > 0) {
                    text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                    cursorPosition--;
                }
                if (!shift) clearSelection();
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) deleteSelection();
                else if (cursorPosition < text.length()) text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1);
                if (!shift) clearSelection();
            }
            case GLFW.GLFW_KEY_LEFT -> moveCursorLeft(shift);
            case GLFW.GLFW_KEY_RIGHT -> moveCursorRight(shift);
            case GLFW.GLFW_KEY_HOME -> moveCursorHome(shift);
            case GLFW.GLFW_KEY_END -> moveCursorEnd(shift);
            case GLFW.GLFW_KEY_A -> { if (ctrl) selectAll(); }
            case GLFW.GLFW_KEY_C -> { if (ctrl && hasSelection()) copyToClipboard(getSelectedText()); }
            case GLFW.GLFW_KEY_V -> { if (ctrl) { String clip = getFromClipboard(); if (clip != null) insertText(clip); } }
            case GLFW.GLFW_KEY_X -> { if (ctrl && hasSelection()) { copyToClipboard(getSelectedText()); deleteSelection(); } }
        }
        resetCursorBlink();
        updateScrollOffset();
    }


    private void handleChar(char c) { if (c >= 32 && c != 127) insertText(String.valueOf(c)); }

    private void insertText(String str) {
        if (str == null || str.isEmpty()) return;
        if (hasSelection()) deleteSelection();
        text = text.substring(0, cursorPosition) + str + text.substring(cursorPosition);
        cursorPosition += str.length();
        resetCursorBlink();
        updateScrollOffset();
    }

    // Copy string to clipboard
    private void copyToClipboard(String s) {
        if (s == null || s.isEmpty()) return;
        GLFW.glfwSetClipboardString(getWindowParent().getWindowHandle(), s);
    }

    // Get string from clipboard
    private String getFromClipboard() {
        String clip = GLFW.glfwGetClipboardString(getWindowParent().getWindowHandle());
        return clip != null ? clip : "";
    }


    private boolean hasSelection() { return selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd; }
    private void selectAll() { selectionStart = 0; selectionEnd = text.length(); cursorPosition = text.length(); }
    private String getSelectedText() { if (!hasSelection()) return ""; int s = Math.min(selectionStart, selectionEnd), e = Math.max(selectionStart, selectionEnd); return text.substring(s, e); }
    private void deleteSelection() { int s = Math.min(selectionStart, selectionEnd), e = Math.max(selectionStart, selectionEnd); text = text.substring(0, s) + text.substring(e); cursorPosition = s; clearSelection(); }

    private void resetCursorBlink() { showCursor = true; lastBlink = System.currentTimeMillis(); }
    private void updateCursorBlink() { if (System.currentTimeMillis() - lastBlink > CURSOR_BLINK) { showCursor = !showCursor; lastBlink = System.currentTimeMillis(); } }

    private int measureTextWidth(String s) {
        var g = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.setFont(scaledFont);
        int w = g.getFontMetrics().stringWidth(s);
        g.dispose();
        return w;
    }

    private void updateScrollOffset() {
        int cursorX = measureTextWidth(text.substring(0, cursorPosition));
        int visibleWidth = getWidthPx() - 2 * padding;
        int cursorPadding = (int) scaledFont.getSize2D();
        if (cursorX - scrollOffset > visibleWidth - cursorPadding) scrollOffset = cursorX - visibleWidth * cursorPadding;
        if (cursorX - scrollOffset < cursorPadding) scrollOffset = cursorX - cursorPadding;
        if (scrollOffset < 0) scrollOffset = 0;
        int textWidth = measureTextWidth(text);
        if (textWidth < visibleWidth) scrollOffset = 0;
        else if (scrollOffset > textWidth - visibleWidth) scrollOffset = textWidth - visibleWidth;
    }

    private void updateFontScale() {
        int availWidth = getWidthPx() - 2 * padding;
        int availHeight = getHeightPx() - 2 * padding;
        if (availWidth <= 0 || availHeight <= 0) { scaledFont = baseFont; return; }
        float scale = (float) availHeight / baseFont.getSize();
        scaledFont = baseFont.deriveFont(baseFont.getSize2D() * scale);
        int textWidth = measureTextWidth(text.isEmpty() ? placeholder : text);
        if (textWidth > availWidth) {
            float widthScale = (float) availWidth / textWidth;
            scaledFont = scaledFont.deriveFont(scaledFont.getSize2D() * widthScale);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        updateCursorBlink();
        updateFontScale();
        updateScrollOffset();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(scaledFont);

        int w = getWidthPx(), h = getHeightPx();
        int textY = (h - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();

        g2.setColor(backgroundColor);
        g2.fill(new RoundRectangle2D.Float(0,0,w,h,cornerRadius,cornerRadius));

        g2.setColor(focused ? focusedBorderColor : borderColor);
        g2.setStroke(new BasicStroke(borderWidth));
        g2.draw(new RoundRectangle2D.Float(borderWidth/2f,borderWidth/2f,w-borderWidth,h-borderWidth,cornerRadius,cornerRadius));

        String display = text.isEmpty() ? placeholder : text;
        boolean isPlaceholder = text.isEmpty() && !placeholder.isEmpty();

        if (hasSelection()) {
            int s = Math.min(selectionStart, selectionEnd);
            int e = Math.max(selectionStart, selectionEnd);
            int selStartX = padding + measureTextWidth(text.substring(0, s)) - scrollOffset;
            int selWidth  = measureTextWidth(text.substring(s, e));
            g2.setColor(selectionColor);
            g2.fillRect(selStartX, 0, selWidth, h);
        }

        g2.setColor(isPlaceholder ? placeholderColor : textColor);
        g2.clipRect(padding, 0, w - 2*padding, h);
        g2.drawString(display, padding - scrollOffset, textY);

        if (focused && showCursor && !isPlaceholder) {
            int cursorX = padding + measureTextWidth(text.substring(0, cursorPosition)) - scrollOffset;
            g2.setColor(cursorColor);
            g2.drawLine(cursorX, textY - g2.getFontMetrics().getAscent(), cursorX, textY + g2.getFontMetrics().getDescent());
        }

        g2.dispose();
    }

    public String getText() { return text; }
    public TextField setText(String t) { text = t != null ? t : ""; cursorPosition = Math.min(cursorPosition, text.length()); resetCursorBlink(); updateScrollOffset(); return this; }
    public TextField setPlaceholder(String p) { placeholder = p; return this; }
    public Font getBaseFont() { return baseFont; }
    public TextField setBaseFont(Font baseFont) { this.baseFont = baseFont; this.scaledFont = baseFont; return this; }
    public Color getTextColor() { return textColor; }
    public TextField setTextColor(Color textColor) { this.textColor = textColor; return this; }
    public Color getBackgroundColor() { return backgroundColor; }
    public TextField setBackgroundColor(Color backgroundColor) { this.backgroundColor = backgroundColor; return this; }
    public Color getBorderColor() { return borderColor; }
    public TextField setBorderColor(Color borderColor) { this.borderColor = borderColor; return this; }
    public Color getFocusedBorderColor() { return focusedBorderColor; }
    public TextField setFocusedBorderColor(Color focusedBorderColor) { this.focusedBorderColor = focusedBorderColor; return this; }
    public Color getPlaceholderColor() { return placeholderColor; }
    public TextField setPlaceholderColor(Color placeholderColor) { this.placeholderColor = placeholderColor; return this; }
    public Color getSelectionColor() { return selectionColor; }
    public TextField setSelectionColor(Color selectionColor) { this.selectionColor = selectionColor; return this; }
    public Color getCursorColor() { return cursorColor; }
    public TextField setCursorColor(Color cursorColor) { this.cursorColor = cursorColor; return this; }
    public int getBorderWidth() { return borderWidth; }
    public TextField setBorderWidth(int borderWidth) { this.borderWidth = borderWidth; return this; }
    public int getPadding() { return padding; }
    public TextField setPadding(int padding) { this.padding = padding; return this; }
    public int getCornerRadius() { return cornerRadius; }
    public TextField setCornerRadius(int cornerRadius) { this.cornerRadius = cornerRadius; return this; }
    @Override
    public void init() {}
    public TextField setFocused(boolean b) { this.focused = true; return this; }
    private int selectionAnchor = -1;

    private void moveCursorLeft(boolean shift) {
        if (shift && selectionAnchor == -1) selectionAnchor = cursorPosition; // first Shift press
        if (cursorPosition > 0) cursorPosition--;
        updateSelection(shift);
    }

    private void moveCursorRight(boolean shift) {
        if (shift && selectionAnchor == -1) selectionAnchor = cursorPosition; // first Shift press
        if (cursorPosition < text.length()) cursorPosition++;
        updateSelection(shift);
    }

    private void moveCursorHome(boolean shift) {
        if (shift && selectionAnchor == -1) selectionAnchor = cursorPosition;
        cursorPosition = 0;
        updateSelection(shift);
    }

    private void moveCursorEnd(boolean shift) {
        if (shift && selectionAnchor == -1) selectionAnchor = cursorPosition;
        cursorPosition = text.length();
        updateSelection(shift);
    }

    private void updateSelection(boolean shift) {
        if (shift) {
            selectionStart = selectionAnchor;
            selectionEnd = cursorPosition;
            if (selectionStart == selectionEnd) clearSelection();
        } else {
            clearSelection();
        }
    }

    private void clearSelection() {
        selectionStart = selectionEnd = selectionAnchor = -1;
    }
}
