package GUI.BillBoardGUI;

import GUI.GUIComponent;
import Main.Settings.Settings;
import Main.SnowMemo;
import Main.Window;
import org.joml.Vector3f;
import org.lwjgl.*;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.Map;

public class LabelBillboardGUI extends BillboardGUI {
    private String text;
    private boolean italic = false;
    private boolean bold = false;
    private boolean underline = false;
    private boolean autoScale = true; // New option
    private float fixedFontSize = 32; // Font size to use when autoScale is false

    public LabelBillboardGUI(Window window, String text){
        super(window, new LabelComponent(window));
        ((LabelComponent)mainGUIComponent).setLabel(this);
        this.text = text;
        setRotation(new Vector3f((float) 0, (float) Math.PI, (float) Math.PI));
    }
    public LabelBillboardGUI(Window window, LabelComponent labelComponent){
        super(window, labelComponent);
        ((LabelComponent)mainGUIComponent).setLabel(this);
        this.text = text;
        setRotation(new Vector3f((float) 0, (float) Math.PI, (float) Math.PI));
    }
    public static class LabelComponent extends GUIComponent {
        private LabelBillboardGUI label;

        public LabelComponent(Window window) {
            super(window, 256, 256);
        }
        public LabelComponent(Window window,int widthPx,int heightPx){
            super(window,widthPx,heightPx);
        }
        public void setLabel(LabelBillboardGUI label) {
            this.label = label;
        }

        @Override
        protected void paintComponent(Graphics g) {
            System.out.println(label);
            if (label == null) return;
            Graphics2D g2d = (Graphics2D) g.create();

            try {
                // Clear background (transparent)
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, getWidthPx(), getHeightPx());
                g2d.setComposite(AlphaComposite.SrcOver);

                // Setup rendering hints
                if (Settings.getValue("SnowMemo.Defaults.AntiAliasing") != null &&
                        (boolean)Settings.getValue("SnowMemo.Defaults.AntiAliasing")) {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                }
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);

                int panelWidth = getWidthPx();
                int panelHeight = getHeightPx();

                // Calculate maximum available space with padding
                float PADDING_RATIO = 0.85f;
                int maxWidth = (int) (panelWidth * PADDING_RATIO);
                int maxHeight = (int) (panelHeight * PADDING_RATIO);

                g2d.setColor(Color.BLACK);

                if (label.autoScale) {
                    // Original autoscaling behavior
                    renderAutoScaled(g2d, maxWidth, maxHeight, panelWidth, panelHeight);
                } else {
                    // New multi-line wrapping behavior
                    renderWrapped(g2d, maxWidth, maxHeight, panelWidth, panelHeight);
                }

            } finally {
                g2d.dispose();
            }
        }

        private void renderAutoScaled(Graphics2D g2d, int maxWidth, int maxHeight, int panelWidth, int panelHeight) {
            // Binary search for optimal font size
            Font baseFont = SnowMemo.currentTheme.getFonts()[0];
            float minSize = 12;
            float maxSize = Math.max(maxWidth, maxHeight);
            float optimalSize = maxSize;

            // Calculate font style
            int fontStyle = 0;
            if (label.bold) fontStyle |= Font.BOLD;
            if (label.italic) fontStyle |= Font.ITALIC;

            while (maxSize - minSize > 1) {
                float testSize = (minSize + maxSize) / 2;

                Font testFont = baseFont.deriveFont(fontStyle, testSize);
                if (label.underline) {
                    Map<TextAttribute, Object> attributes = new HashMap<>();
                    attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                    testFont = testFont.deriveFont(attributes);
                }

                FontMetrics fm = g2d.getFontMetrics(testFont);
                Rectangle2D bounds = fm.getStringBounds(label.text, g2d);
                int textWidth = (int) bounds.getWidth();
                int textHeight = fm.getAscent();

                if (textWidth <= maxWidth && textHeight <= maxHeight) {
                    optimalSize = testSize;
                    minSize = testSize;
                } else {
                    maxSize = testSize;
                }
            }

            // Create final styled font
            Font font = baseFont.deriveFont(fontStyle, optimalSize);
            if (label.underline) {
                Map<TextAttribute, Object> attributes = new HashMap<>();
                attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                font = font.deriveFont(attributes);
            }

            g2d.setFont(font);

            FontMetrics fm = g2d.getFontMetrics();
            Rectangle2D bounds = fm.getStringBounds(label.text, g2d);
            int textWidth = (int) bounds.getWidth();
            int textHeight = fm.getAscent();

            // Center the text
            int x = (panelWidth - textWidth) / 2;
            int y = (panelHeight - textHeight) / 2 + textHeight;

            g2d.drawString(label.text, x, y);

            // Draw additional underline for emphasis (only if underline is enabled)
            if (label.underline) {
                int underlineY = y + 3;
                float strokeWidth = Math.max(2, optimalSize * 0.06f);
                g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.drawLine(x, underlineY, x + textWidth, underlineY);
            }
        }

        private void renderWrapped(Graphics2D g2d, int maxWidth, int maxHeight, int panelWidth, int panelHeight) {
            Font baseFont = SnowMemo.currentTheme.getFonts()[0];

            // Calculate font style
            int fontStyle = 0;
            if (label.bold) fontStyle |= Font.BOLD;
            if (label.italic) fontStyle |= Font.ITALIC;

            // Create font with fixed size
            Font font = baseFont.deriveFont(fontStyle, label.fixedFontSize);

            // Apply underline attribute if needed
            Map<TextAttribute, Object> attributes = new HashMap<>();
            if (label.underline) {
                attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            }
            attributes.put(TextAttribute.FONT, font);

            AttributedString attributedText = new AttributedString(label.text);
            attributedText.addAttributes(attributes, 0, label.text.length());

            g2d.setFont(font);
            FontRenderContext frc = g2d.getFontRenderContext();
            LineBreakMeasurer measurer = new LineBreakMeasurer(attributedText.getIterator(), frc);

            float wrappingWidth = maxWidth;
            float drawPosY = 0;

            // First pass: measure total height
            java.util.List<TextLayout> layouts = new java.util.ArrayList<>();
            measurer.setPosition(0);
            while (measurer.getPosition() < label.text.length()) {
                TextLayout layout = measurer.nextLayout(wrappingWidth);
                layouts.add(layout);
                drawPosY += layout.getAscent() + layout.getDescent() + layout.getLeading();
            }

            // Calculate starting Y position to center text vertically
            float startY = (panelHeight - drawPosY) / 2;
            drawPosY = startY;

            // Second pass: draw the text
            for (TextLayout layout : layouts) {
                drawPosY += layout.getAscent();

                // Center each line horizontally
                float drawPosX = (panelWidth - layout.getAdvance()) / 2;

                layout.draw(g2d, drawPosX, drawPosY);

                drawPosY += layout.getDescent() + layout.getLeading();
            }
        }
    }

    public String getText() {
        return text;
    }

    public LabelBillboardGUI setText(String text) {
        this.text = text;
        markDirty();
        return this;
    }

    public boolean isItalic() {
        return italic;
    }

    public LabelBillboardGUI setItalic(boolean italic) {
        this.italic = italic;
        markDirty();
        return this;
    }

    public boolean isBold() {
        return bold;
    }

    public LabelBillboardGUI setBold(boolean bold) {
        this.bold = bold;
        markDirty();
        return this;
    }

    public boolean isUnderline() {
        return underline;
    }

    public LabelBillboardGUI setUnderline(boolean underline) {
        this.underline = underline;
        markDirty();
        return this;
    }

    public boolean isAutoScale() {
        return autoScale;
    }

    public LabelBillboardGUI setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
        markDirty();
        return this;
    }

    public float getFixedFontSize() {
        return fixedFontSize;
    }

    public LabelBillboardGUI setFixedFontSize(float fixedFontSize) {
        this.fixedFontSize = fixedFontSize;
        markDirty();
        return this;
    }
}