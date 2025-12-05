package Main.Theme;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class Theme {
    private final Color mainColor;
    private final Color[] secondaryColors;
    private final Color[] fontColors;
    private final Font[] fonts;
    private final byte[] mainFontBytes;
    private final Font mainMonoFont;
    private final byte[] mainMonoFontBytes;
    public Theme(Color mainColor, Color[] secondaryColors, Color[] fontColors, Font[] fonts, InputStream fontFileInputStream, InputStream fontMonoInputStream) {
        this.mainColor = Objects.requireNonNull(mainColor, "mainColor cannot be null");
        this.secondaryColors = secondaryColors != null ? secondaryColors.clone() : new Color[0];
        this.fontColors = fontColors != null ? fontColors.clone() : new Color[0];
        this.fonts = fonts != null ? fonts.clone() : new Font[0];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while (true) {
            try {
                if ((n = fontFileInputStream.read(tmp)) == -1) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            buffer.write(tmp, 0, n);
        }
        mainFontBytes = buffer.toByteArray();
        buffer = new ByteArrayOutputStream();
        tmp = new byte[4096];
        n = 0;
        while (true) {
            try {
                if ((n = fontMonoInputStream.read(tmp)) == -1) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            buffer.write(tmp, 0, n);
        }
        mainMonoFontBytes = buffer.toByteArray();
        try {
            mainMonoFont = Font.createFont(
                    Font.TRUETYPE_FONT,
                    new ByteArrayInputStream(mainMonoFontBytes)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public Color getMainColor() {
        return mainColor;
    }

    public Color[] getSecondaryColors() {
        return secondaryColors.clone();
    }

    public Color[] getFontColors() {
        return fontColors.clone();
    }

    public Font[] getFonts() {
        return fonts.clone();
    }

    public byte[] getMainFontBytes() {
        return mainFontBytes.clone();
    }

    public Font getMainMonoFont() {
        return mainMonoFont;
    }

    public byte[] getMainMonoFontBytes() {
        return mainMonoFontBytes;
    }
}
