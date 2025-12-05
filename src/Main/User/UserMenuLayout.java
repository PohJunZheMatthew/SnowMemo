package Main.User;

import Main.SnowMemo;
import Main.Window;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImGuiViewport;
import imgui.ImVec2;
import imgui.flag.*;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserMenuLayout {
    private static final Logger log = LoggerFactory.getLogger(UserMenuLayout.class);
    private boolean visible = false;
    public void render(){


        // This gate ONLY controls the main UserMenu window visibility.
        if(!isVisible()) return;
        ImGuiViewport vp = ImGui.getMainViewport();
        ImGui.setNextWindowSize(new ImVec2(vp.getWorkSizeX() * 0.2f, vp.getWorkSizeY() * 0.75f), ImGuiCond.Always);

        float windowWidth = vp.getWorkSizeX() * 0.2f;
        float padding = 10f;

        ImGui.setNextWindowPos(new ImVec2(vp.getWorkSizeX() - windowWidth - padding, vp.getWorkSizeX() * 0.05f), ImGuiCond.Always);
        ImGui.begin("UserMenu", ImGuiWindowFlags.NoResize|ImGuiWindowFlags.NoMove|ImGuiWindowFlags.NoCollapse|ImGuiWindowFlags.NoTitleBar);
        renderLogOutPopup();
        ImGuiStyle style = ImGui.getStyle();
        style.setColor(ImGuiCol.Text, SnowMemo.currentTheme.getSecondaryColors()[0].getRed(),SnowMemo.currentTheme.getSecondaryColors()[0].getGreen(),SnowMemo.currentTheme.getSecondaryColors()[0].getBlue(),SnowMemo.currentTheme.getSecondaryColors()[0].getAlpha());
        style.setColor(ImGuiCol.FrameBg,SnowMemo.currentTheme.getMainColor().getRed(),SnowMemo.currentTheme.getMainColor().getGreen(),SnowMemo.currentTheme.getMainColor().getBlue(),SnowMemo.currentTheme.getMainColor().getAlpha());
        style.setFrameBorderSize(1f);

        ImGui.setWindowFontScale(1.25f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getWindowHeight()*0.02f, ImGui.getWindowHeight()*0.02f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getWindowHeight()*0.02f, ImGui.getWindowHeight()*0.02f);

        if (User.checkForConnection()) {
            if (User.getCurrentUser() != null) {
                ImGui.text("User:"+User.getCurrentUser().getUsername());
                if (ImGui.button("Account Settings",-1,0)){
                    User.getSettingsLayout().setVisible(true);
                    this.setVisible(false);
                }
            } else {
                ImGui.text("Not signed in!");
            }
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            style.setChildBorderSize(1f);
            style.setColor(ImGuiCol.Border,0f,0f,0f,1.0f);
            ImGui.beginChild("Accounts",0,ImGui.getWindowHeight()*0.5f, ImGuiChildFlags.Border);
            boolean showPopup = false;
            for (int i = 0;i<User.getUsers().size();i++){
                if(ImGui.button(User.getUsers().get(i).getUsername(), -1.0f, 0)){
                    if (User.getCurrentUser()!=null){
                        if (User.getCurrentUser().getUsername().equals(User.getUsers().get(i).getUsername())) {
                            ImGui.openPopup("Log out?");
                            showPopup = true;
                        }
                    }else{
                        User.getLogInLayout().setVisible(true);
                        User.getLogInLayout().logInto(User.getUsers().get(i).getUsername());
                        User.getSignUpLayout().setVisible(false);
                        setVisible(false);
                    }

                }
                if (ImGui.isItemHovered()){
                    ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                }
            }
            ImGui.endChild();

            ImGui.separator();
            ImGui.spacing();
            if (showPopup){
                ImGui.openPopup("Log out?");
            }
            if (User.getCurrentUser() == null) {
                if (ImGui.button("Sign up",-1,0)){
                    User.getSignUpLayout().setVisible(true);
                    User.getLogInLayout().setVisible(false);
                    setVisible(false);
                }
            }
            if (User.getCurrentUser()==null) {
                if (ImGui.button("Log in", -1, 0)) {
                    User.getLogInLayout().setVisible(true);
                    User.getSignUpLayout().setVisible(false);
                    setVisible(false);
                }
            }else{
                if (ImGui.button("+Add account", -1, 0)) {
                    User.getLogInLayout().setVisible(true);
                    User.getSignUpLayout().setVisible(false);
                }
            }

            if (User.getCurrentUser() != null) {
                if (ImGui.button("Log out",-1,0)){
                    ImGui.openPopup("Log out?");
                }
            }
        } else {
            ImGui.text("Not signed in!");
            ImGui.separator();
            ImGui.text("No internet connection!");
        }

        ImGui.getStyle().setColor(ImGuiCol.Text,1f,1f,1f,1f);

        ImGui.popStyleVar(2);
        ImGui.setWindowFontScale(1.0f);
        style.setColor(ImGuiCol.Text,1f,1f,1f,1f);
        ImGui.end();
    }
    private void renderLogOutPopup(){
        if (ImGui.beginPopupModal("Log out?")) {
            ImGui.getStyle().setColor(ImGuiCol.WindowBg,1f,1f,1f,1f);
            ImGui.getStyle().setColor(ImGuiCol.FrameBg,1f,1f,1f,1f);
            ImGui.getStyle().setColor(ImGuiCol.ChildBg,1f,1f,1f,1f);
            ImGui.getStyle().setColor(ImGuiCol.PopupBg,1f,1f,1f,1f);
            ImGui.getStyle().setColor(ImGuiCol.Text,0f,0f,0f,1f);
            ImGui.text("Are you sure you wanna log out?");

            if (ImGui.button("Yes")) {
                User.logOut();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }
    public boolean isVisible() {
        return visible;
    }

    public UserMenuLayout setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }
}