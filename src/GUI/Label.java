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
    private int padding = 0;
    private boolean bold = false;
    private boolean italic = false;
    private float CornerRadius = 0f;
    public enum AutoResizeMode {
        NONE,
        FIT_WIDTH,
        FIT_HEIGHT,
        FIT_BOTH,
        SCALE_BY_SIZE
    }

    private AutoResizeMode autoResizeMode = AutoResizeMode.NONE;
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
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
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

    private void updateFontScale() {
        Font f = bold ? baseFont.deriveFont(Font.BOLD) : baseFont;
        f = italic ? f.deriveFont(Font.ITALIC) : f;
        if (autoResizeMode == AutoResizeMode.NONE || text.isEmpty()) {
            scaledFont = f;
            return;
        }
        int availWidth = Math.max(1, getWidthPx() - 2 * padding);
        int availHeight = Math.max(1, getHeightPx() - 2 * padding);
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        g.dispose();
        float scale;
        switch (autoResizeMode) {
            case FIT_WIDTH -> scale = (float) availWidth / textWidth;
            case FIT_HEIGHT -> scale = (float) availHeight / textHeight;
            case SCALE_BY_SIZE -> {
                float widthScale = (float) availWidth / 100f;
                float heightScale = (float) availHeight / 100f;
                scale = Math.max(widthScale, heightScale);
            }
            default -> {
                float widthScale = (float) availWidth / textWidth;
                float heightScale = (float) availHeight / textHeight;
                scale = Math.min(widthScale, heightScale);
            }
        }
        float newSize = Math.max(8, f.getSize2D() * scale);
        scaledFont = f.deriveFont(newSize);
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
            default -> (containerHeight + fm.getAscent() - fm.getDescent()) / 2;
        };
    }

    private int calculateVerticalStartPosition(int totalTextHeight, int containerHeight, FontMetrics fm) {
        return switch (verticalAlignment) {
            case SwingConstants.TOP -> padding + fm.getAscent();
            case SwingConstants.BOTTOM -> containerHeight - padding - totalTextHeight + fm.getAscent();
            default -> (containerHeight - totalTextHeight) / 2 + fm.getAscent();
        };
    }

    public Label setBold(boolean bold) {
        this.bold = bold;
        refresh();
        return this;
    }

    public Label setItalic(boolean italic){
        this.italic = italic;
        refresh();
        return this;
    }

    public Label setText(String text) {
        this.text = text != null ? text : "";
        refresh();
        return this;
    }

    public Label setFontSize(float size) {
        this.baseFont = baseFont.deriveFont(size);
        refresh();
        return this;
    }

    public Label setBaseFont(Font baseFont) {
        this.baseFont = baseFont;
        refresh();
        return this;
    }

    public Label setAutoResizeMode(AutoResizeMode mode) {
        this.autoResizeMode = mode;
        refresh();
        return this;
    }

    public Label setPadding(int padding) {
        this.padding = padding;
        refresh();
        return this;
    }

    public Label setTextColor(Color textColor) {
        this.textColor = textColor;
        return this;
    }

    public Label setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    public Label setHorizontalAlignment(int alignment) {
        this.horizontalAlignment = alignment;
        return this;
    }

    public Label setVerticalAlignment(int alignment) {
        this.verticalAlignment = alignment;
        return this;
    }

    public Label setWordWrap(boolean wordWrap) {
        this.wordWrap = wordWrap;
        return this;
    }

    public void refresh() {
        updateFontScale();
    }

    @Override
    public void init() {}

    public String getText() {
        return text;
    }

    public float getCornerRadius() {
        return CornerRadius;
    }

    public Label setCornerRadius(float cornerRadius) {
        CornerRadius = cornerRadius;
        return this;
    }
    public Label setCornerRadius(int cornerRadius){
        CornerRadius = cornerRadius;
        return this;
    }

}
