package GUI;

import Main.Window;

import java.awt.*;
import java.awt.image.BufferedImage;

public class Label extends GUIComponent {

    private String text = "";
    private Font baseFont = new Font("SansSerif", Font.PLAIN, 14);
    private Font scaledFont = baseFont;
    private Color textColor = new Color(33, 37, 41);
    private Color backgroundColor = Color.white;
    private int horizontalAlignment = SwingConstants.LEFT;
    private int verticalAlignment = SwingConstants.CENTER;
    private int padding = 5;
    private boolean autoResize = false;
    private boolean wordWrap = false;

    public static class SwingConstants {
        public static final int LEFT = 2;
        public static final int CENTER = 0;
        public static final int RIGHT = 4;
        public static final int TOP = 1;
        public static final int BOTTOM = 3;
    }

    public Label(Window window) {
        this(window, "");
    }

    public Label(Window window, String text) {
        this(window, text, 0.2f, 0.05f);
    }

    public Label(Window window, String text, float width, float height) {
        this(window, text, 0, 0, width, height);
    }

    public Label(Window window, String text, float x, float y, float width, float height) {
        super(window, x, y, width, height);
        this.text = text != null ? text : "";
        updateFontScale();
    }

    @Override
    protected void paintComponent(Graphics g) {
        updateFontScale();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidthPx();
        int h = getHeightPx();

        if (backgroundColor != null) {
            g2.setColor(backgroundColor);
            g2.fillRect(0, 0, w, h);
        }

        if (!text.isEmpty()) {
            g2.setFont(scaledFont);
            g2.setColor(textColor);

            if (wordWrap) {
                drawWrappedText(g2, w, h);
            } else {
                drawSingleLineText(g2, w, h);
            }
        }

        g2.dispose();
    }

    private void drawSingleLineText(Graphics2D g2, int w, int h) {
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();

        int x = calculateHorizontalPosition(textWidth, w);
        int y = calculateVerticalPosition(textHeight, h, fm);

        g2.drawString(text, x, y);
    }

    private void drawWrappedText(Graphics2D g2, int w, int h) {
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split(" ");
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        int availableWidth = w - 2 * padding;

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (fm.stringWidth(testLine) <= availableWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        int lineHeight = fm.getHeight();
        int totalTextHeight = lines.size() * lineHeight;
        int startY = calculateVerticalStartPosition(totalTextHeight, h, fm);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineWidth = fm.stringWidth(line);
            int x = calculateHorizontalPosition(lineWidth, w);
            int y = startY + (i * lineHeight);
            g2.drawString(line, x, y);
        }
    }

    private int calculateHorizontalPosition(int textWidth, int containerWidth) {
        return switch (horizontalAlignment) {
            case SwingConstants.CENTER -> (containerWidth - textWidth) / 2;
            case SwingConstants.RIGHT -> containerWidth - textWidth - padding;
            default -> padding;
        };
    }

    private int calculateVerticalPosition(int textHeight, int containerHeight, FontMetrics fm) {
        return switch (verticalAlignment) {
            case SwingConstants.TOP -> padding + fm.getAscent();
            case SwingConstants.BOTTOM -> containerHeight - padding - fm.getDescent();
            default -> (containerHeight - textHeight) / 2 + fm.getAscent();
        };
    }

    private int calculateVerticalStartPosition(int totalTextHeight, int containerHeight, FontMetrics fm) {
        return switch (verticalAlignment) {
            case SwingConstants.TOP -> padding + fm.getAscent();
            case SwingConstants.BOTTOM -> containerHeight - padding - totalTextHeight + fm.getAscent();
            default -> (containerHeight - totalTextHeight) / 2 + fm.getAscent();
        };
    }

    private void updateFontScale() {
        if (autoResize && !text.isEmpty()) {
            int availWidth = Math.max(1, getWidthPx() - 2 * padding);
            int availHeight = Math.max(1, getHeightPx() - 2 * padding);

            scaledFont = baseFont;

            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setFont(scaledFont);
            FontMetrics fm = g.getFontMetrics();

            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();

            float widthScale = (float) availWidth / textWidth;
            float heightScale = (float) availHeight / textHeight;
            float scale = Math.min(widthScale, heightScale);

            if (scale < 1.0f || scale > 1.0f) {
                float newSize = Math.max(8, baseFont.getSize2D() * scale);
                scaledFont = baseFont.deriveFont(newSize);
            }

            g.dispose();
        } else {
            scaledFont = baseFont;
        }
    }

    public String getText() {
        return text;
    }

    public Label setText(String text) {
        this.text = text != null ? text : "";
        updateFontScale();
        return this;
    }

    public Font getBaseFont() {
        return baseFont;
    }

    public Label setBaseFont(Font baseFont) {
        this.baseFont = baseFont;
        updateFontScale();
        return this;
    }

    public Color getTextColor() {
        return textColor;
    }

    public Label setTextColor(Color textColor) {
        this.textColor = textColor;
        return this;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public Label setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public int getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public Label setHorizontalAlignment(int horizontalAlignment) {
        this.horizontalAlignment = horizontalAlignment;
        return this;
    }

    public int getVerticalAlignment() {
        return verticalAlignment;
    }

    public Label setVerticalAlignment(int verticalAlignment) {
        this.verticalAlignment = verticalAlignment;
        return this;
    }

    public int getPadding() {
        return padding;
    }

    public Label setPadding(int padding) {
        this.padding = padding;
        updateFontScale();
        return this;
    }

    public boolean isAutoResize() {
        return autoResize;
    }

    public Label setAutoResize(boolean autoResize) {
        this.autoResize = autoResize;
        updateFontScale();
        return this;
    }

    public boolean isWordWrap() {
        return wordWrap;
    }

    public Label setWordWrap(boolean wordWrap) {
        this.wordWrap = wordWrap;
        return this;
    }

    @Override
    public void init() {}
}