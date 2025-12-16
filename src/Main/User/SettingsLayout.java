package Main.User;

import Main.SnowMemo;
import Main.Window;
import imgui.*;
import imgui.flag.*;
import imgui.type.ImString;
import org.apache.commons.validator.routines.EmailValidator;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.linux.X11;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;

public class SettingsLayout {
    private boolean visible = false;
    private volatile ImString bioString;
    private volatile User user;
    private boolean showPass;
    private boolean showPassPop;
    private ImString password = new ImString();
    private ImString emailString = new ImString();
    private boolean refreshNow = false;

    {
        Thread thread = new Thread() {
            @Override public void run() {
                while (!GLFW.glfwWindowShouldClose(Window.getCurrentWindow().getWindowHandle())) {
                    if (user != User.getCurrentUser()||refreshNow) {
                        bioString = new ImString(256);
                        bioString.set((User.getCurrentUser() != null) ? User.getCurrentUser().getBio() : "");
                        password = new ImString();
                        password.set((User.getCurrentUser() != null) ? User.getCurrentUser().getPassword() : "");
                        emailString = new ImString();
                        emailString.set((User.getCurrentUser() != null) ? User.getCurrentUser().getEmail() : "");
                    }
                    user = User.getCurrentUser();
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }
    public SettingsLayout(){
        bioString = new ImString((User.getCurrentUser()!=null) ? User.getCurrentUser().getBio() : "");
    }
    public void render(){
        if (!visible) return;
        ImGuiViewport vp = ImGui.getMainViewport();

        ImGui.setNextWindowSize(new ImVec2(vp.getWorkSizeX() * 0.8f, vp.getWorkSizeY() * 0.8f), ImGuiCond.Always);
        ImGui.setNextWindowPos(new ImVec2(vp.getWorkSizeX() * 0.1f, vp.getWorkSizeY() * 0.1f), ImGuiCond.Always);

        imgui.type.ImBoolean open = new imgui.type.ImBoolean(true);
        ImGui.begin("Account Settings", open, ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse);

        if (!open.get()) {
            setVisible(false);
            ImGui.end();
            return;
        }

        float windowWidth = ImGui.getWindowWidth();
        float windowHeight = ImGui.getWindowHeight();
        ImGuiStyle style = ImGui.getStyle();
        style.setColor(ImGuiCol.Text, SnowMemo.currentTheme.getSecondaryColors()[0].getRed(),SnowMemo.currentTheme.getSecondaryColors()[0].getGreen(),SnowMemo.currentTheme.getSecondaryColors()[0].getBlue(),SnowMemo.currentTheme.getSecondaryColors()[0].getAlpha());

        ImGui.setWindowFontScale(1.25f);

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getWindowHeight()*0.02f, ImGui.getWindowHeight()*0.02f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getWindowHeight()*0.02f, ImGui.getWindowHeight()*0.02f);
        //start
        if (ImGui.beginTabBar("SettingsTabs")){
            if (ImGui.beginTabItem("Home")) {
                ImGui.pushFont(Window.bigFont);
                String text = "Welcome! "+User.getCurrentUser().getUsername()+"!";
                ImVec2 size = ImGui.calcTextSize(text);
                float inputPosX = (windowWidth - size.x) * 0.5f;
                ImGui.setCursorPosX(inputPosX);
                ImGui.text(text);
                ImGui.popFont();
                style.setChildBorderSize(1f);
                ImGui.beginChild("AccountDate",windowWidth*0.5f-(style.getItemSpacingX())*1.5f,windowHeight*0.25f,ImGuiChildFlags.Border);
                ImGui.pushFont(Window.h4Font);
                ImGui.text("⏰ Account creation date");
                ImGui.popFont();
                ImGui.textWrapped("Your account was created on: "+ new Date(User.getCurrentUser().getCreationTime()));
                ImGui.endChild();
                ImGui.sameLine();
                ImGui.beginChild("##AccountBio",windowWidth*0.5f-(style.getItemSpacingX())*1.5f,windowHeight*0.5f,ImGuiChildFlags.Border);
                ImGui.pushFont(Window.bigFont);
                ImGui.text("Your bio:");
                ImGui.popFont();
                ImGui.setNextItemWidth(-1);
                ImGui.inputTextMultiline("##bio", bioString,ImGuiInputTextFlags.AllowTabInput);
                ImGui.text(bioString.getLength() + "/" + (bioString.getBufferSize() - 1));
                if (ImGui.button("Update bio")) {
                    User.changeBio(bioString.get());
                }
                ImGui.endChild();
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("General")) {
                ImGui.pushFont(Window.bigFont);
                ImGui.text("Your email:");
                ImGui.popFont();
                ImGui.inputText("##emailString", emailString);
                if (ImGui.button("Update email address")){
                    if (EmailValidator.getInstance().isValid(emailString.get())){
                        User.changeEmail(emailString.get(),User.getCurrentUser().getPassword());
                    }
                }

                ImGui.pushFont(Window.bigFont);
                ImGui.text("Your bio:");
                ImGui.popFont();
                ImGui.inputTextMultiline("##bio", bioString,ImGuiInputTextFlags.AllowTabInput);
                ImGui.text(bioString.getLength() + "/" + (bioString.getBufferSize() - 1));
                if (ImGui.button("Update bio")) {
                    User.changeBio(bioString.get());
                }
                ImVec4 color = ImGui.getStyle().getColor(ImGuiCol.Button);
                ImGui.getStyle().setColor(ImGuiCol.Button,1f,0.5f,0.5f,1f);
                ImVec4 colorHovered = ImGui.getStyle().getColor(ImGuiCol.ButtonHovered);
                ImGui.getStyle().setColor(ImGuiCol.ButtonHovered,1f,0.25f,0.25f,1f);
                ImVec4 colorActive = ImGui.getStyle().getColor(ImGuiCol.ButtonActive);
                ImGui.getStyle().setColor(ImGuiCol.ButtonActive,1f,0.25f,0.25f,1f);
                style.setColor(ImGuiCol.PopupBg,0f,0f,0f,1f);
                if (ImGui.button("DELETE ACCOUNT")){
                    ImGui.openPopup("DELETEPASSWARNING");
                }
                if (ImGui.beginPopup("DELETEPASSWARNING")){

                    ImGui.text("Please key in your password to DELETE your account");
                    String buttonText = showPassPop ? "Hide" : "Show";
                    ImGui.inputText("##password", password, showPassPop ? ImGuiInputTextFlags.CharsNoBlank : (ImGuiInputTextFlags.Password | ImGuiInputTextFlags.CharsNoBlank));
                    if (ImGui.isItemHovered()){
                        ImGui.setMouseCursor(ImGuiMouseCursor.TextInput);
                    }

                    ImGui.sameLine();
                    if (ImGui.button(buttonText, -1, 0)){
                        showPassPop = !showPassPop;
                    }
                    if (ImGui.isItemHovered()){
                        ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                    }
                    if (ImGui.button("Delete account")){
                        if (User.deleteAccount()){
                            System.out.println("Account deleted");
                        }
                    }
                    ImGui.endPopup();
                }
                ImGui.getStyle().setColor(ImGuiCol.Button,color.x,color.y,color.z,color.w);
                ImGui.getStyle().setColor(ImGuiCol.ButtonHovered,colorHovered.x,colorHovered.y,colorHovered.z,colorHovered.w);
                ImGui.getStyle().setColor(ImGuiCol.ButtonActive,colorActive.x,colorActive.y,colorActive.z,colorActive.w);
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Security")) {

                String passwordLabel = "Password:";
                ImGui.text(passwordLabel);

                String buttonText = showPass ? "Hide" : "Show";

                ImGui.inputText("##password", password, showPass ? ImGuiInputTextFlags.CharsNoBlank : (ImGuiInputTextFlags.Password | ImGuiInputTextFlags.CharsNoBlank));
                if (ImGui.isItemHovered()){
                    ImGui.setMouseCursor(ImGuiMouseCursor.TextInput);
                }

                ImGui.sameLine();
                if (ImGui.button(buttonText, -1, 0)){
                    showPass = !showPass;
                }
                if (ImGui.isItemHovered()){
                    ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                }
                if (ImGui.button("Update password")){
                    User.changePassword(User.getCurrentUser().getPassword(),password.get());
                }
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        //end
        ImGui.popStyleVar(2);
        ImGui.setWindowFontScale(1.0f);

        style.setColor(ImGuiCol.Text, 255,255,255,255);
        ImGui.end();
    }

    public boolean isVisible() {
        return visible;
    }

    public SettingsLayout setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }
}
