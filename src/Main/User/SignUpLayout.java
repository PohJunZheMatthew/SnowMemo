package Main.User;

import Main.SnowMemo;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImGuiViewport;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImString;
import org.apache.commons.validator.routines.EmailValidator;

public class SignUpLayout {
    private ImString username = new ImString(20);
    private ImString email = new ImString();
    private ImString password = new ImString();
    private boolean showPass = false;
    private boolean visible = false;

    public boolean isVisible() {
        return visible;
    }

    public SignUpLayout setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public void applyInputTextStyle(){
        ImGuiStyle style = ImGui.getStyle();
        style.setColor(ImGuiCol.FrameBg,SnowMemo.currentTheme.getMainColor().getRed(),SnowMemo.currentTheme.getMainColor().getGreen(),SnowMemo.currentTheme.getMainColor().getBlue(),SnowMemo.currentTheme.getMainColor().getAlpha());
        style.setFrameBorderSize(1f);
    }

    private String msg = "-";

    public void render() {
        if (!visible) return;
        ImGuiViewport vp = ImGui.getMainViewport();

        ImGui.setNextWindowSize(new ImVec2(vp.getWorkSizeX() * 0.8f, vp.getWorkSizeY() * 0.8f), ImGuiCond.Always);
        ImGui.setNextWindowPos(new ImVec2(vp.getWorkSizeX() * 0.1f, vp.getWorkSizeY() * 0.1f), ImGuiCond.Always);

        imgui.type.ImBoolean open = new imgui.type.ImBoolean(true);
        ImGui.begin("Sign up", open, ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoCollapse);

        if (!open.get()) {
            setVisible(false);
            ImGui.end();
            return;
        }

        float windowWidth = ImGui.getWindowWidth();
        float windowHeight = ImGui.getWindowHeight();
        ImGuiStyle style = ImGui.getStyle();
        style.setColor(ImGuiCol.Text, SnowMemo.currentTheme.getSecondaryColors()[0].getRed(),SnowMemo.currentTheme.getSecondaryColors()[0].getGreen(),SnowMemo.currentTheme.getSecondaryColors()[0].getBlue(),SnowMemo.currentTheme.getSecondaryColors()[0].getAlpha());

        // Increase font scale for everything
        ImGui.setWindowFontScale(1.25f);

        // Increase padding for input fields and buttons
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getWindowHeight()*0.02f, ImGui.getWindowHeight()*0.02f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getWindowHeight()*0.02f, ImGui.getWindowHeight()*0.02f);

        ImGui.spacing();
        ImGui.spacing();
        ImGui.spacing();
        ImGui.spacing();
        applyInputTextStyle();

        // Calculate centered input position
        float inputWidth = windowWidth * 0.5f; // Made wider
        float inputPosX = (windowWidth - inputWidth) * 0.5f;

        // Username field
        String usernameLabel = "Username:";
        ImGui.setCursorPosX(inputPosX);
        ImGui.text(usernameLabel);

        ImGui.setCursorPosX(inputPosX);
        ImGui.setNextItemWidth(inputWidth);
        ImGui.inputText("##username", username, ImGuiInputTextFlags.CharsNoBlank);
        if (ImGui.isItemHovered()){
            ImGui.setMouseCursor(ImGuiMouseCursor.TextInput);
        }

        ImGui.spacing();

        //Email field
        String emailLabel = "Email:";
        ImGui.setCursorPosX(inputPosX);
        ImGui.text(emailLabel);

        ImGui.setCursorPosX(inputPosX);
        ImGui.setNextItemWidth(inputWidth);
        ImGui.inputText("##email", email, ImGuiInputTextFlags.CharsNoBlank);
        if (ImGui.isItemHovered()){
            ImGui.setMouseCursor(ImGuiMouseCursor.TextInput);
        }
        if (ImGui.isItemDeactivatedAfterEdit()){
            EmailValidator validator = EmailValidator.getInstance();
            if (validator.isValid(email.get())) {
                System.out.println("E");
            }
        }
        ImGui.spacing();

        // Password field with button
        String passwordLabel = "Password:";
        ImGui.setCursorPosX(inputPosX);
        ImGui.text(passwordLabel);

        String buttonText = showPass ? "Hide" : "Show";
        float buttonWidth = ImGui.calcTextSize(buttonText).x + ImGui.getStyle().getFramePaddingX() * 2 + 20.0f; // Extra padding for button
        float passwordInputWidth = inputWidth - buttonWidth - ImGui.getStyle().getItemSpacingX();

        ImGui.setCursorPosX(inputPosX);
        ImGui.setNextItemWidth(passwordInputWidth);
        ImGui.inputText("##password", password, showPass ? ImGuiInputTextFlags.CharsNoBlank : (ImGuiInputTextFlags.Password | ImGuiInputTextFlags.CharsNoBlank));
        if (ImGui.isItemHovered()){
            ImGui.setMouseCursor(ImGuiMouseCursor.TextInput);
        }

        ImGui.sameLine();
        if (ImGui.button(buttonText, buttonWidth, 0)){
            showPass = !showPass;
        }
        if (ImGui.isItemHovered()){
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }

        ImGui.spacing();
        ImGui.spacing();

        // Center submit button - make it bigger
        String submitText = "Sign up";
        float submitWidth = inputWidth * 0.5f; // Made button wider
        ImGui.setCursorPosX((windowWidth - submitWidth) * 0.5f);
        if (ImGui.button(submitText, submitWidth, 0)) {
            if (User.checkForUser(username.get())){
                msg="Success!";
                User.signUp(username.get(),email.get(),password.get());
                setVisible(false);
                reset();
            }else{
                msg="Sign up failed! The account already exist";
            }
        }
        if (ImGui.isItemHovered()){
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }

        ImGui.spacing();

        float msgWidth = ImGui.calcTextSize(msg).x + ImGui.getStyle().getFramePaddingX() * 2;
        ImGui.setCursorPosX((windowWidth - msgWidth) * 0.5f);
        ImGui.text(msg);
        ImGui.spacing();
        ImGui.setCursorPosX((windowWidth - (ImGui.calcTextSize("Have an account? Log in!").x + ImGui.getStyle().getFramePaddingX() * 2)) * 0.5f);
        if (ImGui.button("Have an account? Log in!",ImGui.calcTextSize("Have an account? Log in!").x + ImGui.getStyle().getFramePaddingX() * 2,0)){
            System.out.println("Redirect to logInPage");
            User.getLogInLayout().setVisible(true);
            User.getSignUpLayout().setVisible(false);
            setVisible(false);
            reset();
        }
        if (ImGui.isItemHovered()){
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        // Pop style modifications
        ImGui.popStyleVar(2);
        ImGui.setWindowFontScale(1.0f);

        style.setColor(ImGuiCol.Text, 255,255,255,255);
        ImGui.end();
    }
    public void reset(){
        username.set("");
        password.set("");
        email.set("");
        msg = "-";
    }
}