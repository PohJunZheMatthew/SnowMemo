package Main.Theme;

import java.awt.*;
import java.util.Objects;

public final class Theme {
    private final Color mainColor;
    private final Color[] secondaryColors;
    private final Color[] fontColors;
    private final Font[] fonts;

    public Theme(Color mainColor, Color[] secondaryColors, Color[] fontColors, Font[] fonts) {
        this.mainColor = Objects.requireNonNull(mainColor, "mainColor cannot be null");
        this.secondaryColors = secondaryColors != null ? secondaryColors.clone() : new Color[0];
        this.fontColors = fontColors != null ? fontColors.clone() : new Color[0];
        this.fonts = fonts != null ? fonts.clone() : new Font[0];
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
}
