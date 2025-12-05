package Main.Resources.Icons.MainMenu;

import Main.Resources.IconLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class MainMenuIcons implements IconLoader {
    public static final BufferedImage BUG_REPORT,HOME,SETTINGS,MEMO,MEMO_ADD;
    static{
        try{
            BUG_REPORT = ImageIO.read(Objects.requireNonNull(MainMenuIcons.class.getResourceAsStream("Bug_report.png")));
            HOME = ImageIO.read(Objects.requireNonNull(MainMenuIcons.class.getResourceAsStream("Home.png")));
            SETTINGS = ImageIO.read(Objects.requireNonNull(MainMenuIcons.class.getResourceAsStream("Settings.png")));
            MEMO = ImageIO.read(Objects.requireNonNull(MainMenuIcons.class.getResourceAsStream("Memo.png")));
            MEMO_ADD = ImageIO.read(Objects.requireNonNull(MainMenuIcons.class.getResourceAsStream("Memo_ADD.png")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private MainMenuIcons(){}
}
