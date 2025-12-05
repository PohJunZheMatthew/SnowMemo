package Main.Resources.Icons.User;

import Main.Resources.IconLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class UserIcons implements IconLoader {
    public static final BufferedImage ACCOUNT,ACCOUNT_BOX,ACCOUNT_PROFILE_EDIT,ACCOUNT_SETTINGS,ACCOUNT_SWITCH,LOGIN,LOGOUT;
    static{
        try {
            ACCOUNT = ImageIO.read(Objects.requireNonNull(UserIcons.class.getResourceAsStream("Account.png")));
            ACCOUNT_BOX = ImageIO.read(Objects.requireNonNull(UserIcons.class.getResourceAsStream("AccountBox.png")));
            ACCOUNT_PROFILE_EDIT = ImageIO.read(Objects.requireNonNull(UserIcons.class.getResourceAsStream("AccountProfileEdit.png")));
            ACCOUNT_SETTINGS = ImageIO.read(Objects.requireNonNull(UserIcons.class.getResourceAsStream("AccountSettings.png")));
            ACCOUNT_SWITCH = ImageIO.read(Objects.requireNonNull(UserIcons.class.getResourceAsStream("AccountSwitch.png")));
            LOGIN = ImageIO.read(Objects.requireNonNull(UserIcons.class.getResourceAsStream("LogIn.png")));
            LOGOUT = ImageIO.read(Objects.requireNonNull(UserIcons.class.getResourceAsStream("LogOut.png")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
