package Main;
import imgui.ImGui;

public class ImGuiLayer {
    private boolean showText = true;

    public void imgui() {
        ImGui.begin("Cool Window");
        ImGui.text("Hello ImGui!");

        if (ImGui.button("I am a button")) {
            showText = true;
        }

        if (showText) {
            ImGui.text("You clicked a button");
            ImGui.sameLine();
            if (ImGui.button("Stop showing text")) {
                showText = false;
            }
        }

        ImGui.end();
    }
}