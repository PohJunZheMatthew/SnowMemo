package Main.Node;

import Main.Renderable;
import Main.SnowMemo;
import Main.Window;
import imgui.*;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class NodeCustomizeGUI implements Renderable {
    private ImBoolean visible = new ImBoolean(false);
    private List<Node> nodes = new ArrayList<>();
    private static List<Vector4f> colors = new ArrayList<>();
    private Vector4f nodeHeaderColor = new Vector4f(),nodeBodyColor = new Vector4f();
    private boolean allowNodeTitleEditing = false;
    private final ImString nodeTitleBuffer = new ImString(10);
    static{
        colors.add(new Vector4f(1f,0.5f,0.5f,1f)); // Bright Red
        colors.add(new Vector4f(1f, 0.7f, 0.4f, 1f)); // Orange
        colors.add(new Vector4f(1f,1f,0.5f,1f)); // Yellow
        colors.add(new Vector4f(0.5f,1f,0.5f,1f)); // Green
        colors.add(new Vector4f(0.3f, 0.7f, 0.7f, 1f)); // Teal
        colors.add(new Vector4f(0.75f, 0.75f, 1f, 1f)); // Light Blue
        colors.add(new Vector4f(0.5f, 0.5f, 1f, 1f));   // Medium Blue
        colors.add(new Vector4f(0.6f, 0.4f, 0.8f, 1f)); // Purple
        colors.add(new Vector4f(1f, 0.6f, 0.8f, 1f));   // Pink
        colors.add(new Vector4f(0.7f, 0.7f, 0.7f, 1f)); // Gray
        colors.add(new Vector4f(0.4f, 0.4f, 0.4f, 1f)); // Dark Gray
    }

    public List<Vector4f> getColors() {
        return new ArrayList<>(colors);
    }
    public boolean isVisible() {
        return visible.get();
    }

    public NodeCustomizeGUI setVisible(boolean visible) {
        this.visible.set(visible);
        return this;
    }

    public void applyInputTextStyle(){
        ImGuiStyle style = ImGui.getStyle();
        style.setColor(ImGuiCol.FrameBg, SnowMemo.currentTheme.getMainColor().getRed(),SnowMemo.currentTheme.getMainColor().getGreen(),SnowMemo.currentTheme.getMainColor().getBlue(),SnowMemo.currentTheme.getMainColor().getAlpha());
        style.setFrameBorderSize(1f);
    }

    private String msg = "-";
    float x = 0.025f,y = 0.025f;
    public void render() {
        if (!visible.get()) return;
        ImGuiViewport vp = ImGui.getMainViewport();
        if (visible.get()) {
            ImGui.setNextWindowSize(new ImVec2(vp.getWorkSizeX() * 0.25f, vp.getWorkSizeY() * 0.25f), ImGuiCond.Appearing);
            ImGui.setNextWindowPos(new ImVec2(vp.getWorkSizeX() * x, vp.getWorkSizeY() * y), ImGuiCond.Appearing);
        }
        ImGui.getIO().addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        ImGui.setNextWindowSizeConstraints(new ImVec2(vp.getWorkSizeX() * 0.2f, vp.getWorkSizeY() * 0.2f), new ImVec2(Float.MAX_VALUE - 1000, Float.MAX_VALUE - 1000));
        ImGui.begin("Customize node", visible);
        if (!visible.get()) {
            setVisible(false);
            ImGui.end();
            return;
        }

        float windowWidth = ImGui.getWindowWidth();
        float windowHeight = ImGui.getWindowHeight();
        ImGuiStyle style = ImGui.getStyle();
        style.setColor(ImGuiCol.Text, SnowMemo.currentTheme.getSecondaryColors()[0].getRed(), SnowMemo.currentTheme.getSecondaryColors()[0].getGreen(), SnowMemo.currentTheme.getSecondaryColors()[0].getBlue(), SnowMemo.currentTheme.getSecondaryColors()[0].getAlpha());

        // Increase font scale for everything
        ImGui.setWindowFontScale(1.25f);

        // Increase padding for input fields and buttons
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getWindowHeight() * 0.02f, ImGui.getWindowHeight() * 0.02f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getWindowHeight() * 0.02f, ImGui.getWindowHeight() * 0.02f);
        if (allowNodeTitleEditing) {
            ImGui.text("Node's title");

            ImGui.setNextItemWidth(-1);

            if (ImGui.inputText("##nodeTitle",
                    nodeTitleBuffer,
                    ImGuiInputTextFlags.EnterReturnsTrue)) {

                String newTitle = nodeTitleBuffer.get();
                System.out.println(newTitle);
                for (Node node : nodes) {
                    if (node.isAllowUserTitleChange()) {
                        node.setTitle(newTitle);
                    }
                }
            }
        }
        ImGui.pushFont(Window.bigFont);
        ImGui.text("Node's header");
        ImGui.popFont();
        ImGui.setCursorPosX(Window.getCurrentWindow().getWidth() * 0.01f);
        ImGui.text("Background color");
        ImGui.setCursorPosX(Window.getCurrentWindow().getWidth() * 0.01f);
        System.out.println(vp.getWorkSizeY() * 0.075f);
        ImGui.beginChild("NodeHeaderBgColor", new ImVec2(-1, Math.min(vp.getWorkSizeY() * 0.075f, 50f)), ImGuiChildFlags.Border);
        ImVec4 oriVec4 = style.getColor(ImGuiCol.Button);
        ImVec4 oriVec4Act = style.getColor(ImGuiCol.ButtonActive);
        ImVec4 oriVec4Hover = style.getColor(ImGuiCol.ButtonHovered);

        if (!colors.contains(nodeHeaderColor)) {
            colors.addFirst(nodeHeaderColor);
        }
        ImVec2 content_region_avail = ImGui.getContentRegionAvail();
        float desired_height = content_region_avail.y;
        ImVec2 child_size = new ImVec2(desired_height, desired_height);
        float oriRound = style.getFrameRounding();
        for (Vector4f color : colors) {
            if (color == nodeHeaderColor) {
                style.setFrameBorderSize(2.5f);
            } else {
                style.setFrameBorderSize(1f);
            }

            style.setColor(ImGuiCol.Button, color.x, color.y, color.z, color.w);
            style.setColor(ImGuiCol.ButtonActive, color.x, color.y, color.z, color.w);
            style.setColor(ImGuiCol.ButtonHovered, color.x, color.y, color.z, color.w);
            style.setFrameRounding(Short.MAX_VALUE);


            if (ImGui.button("##color_swatch_btn_" + color.toString(), child_size.x, child_size.y)) {
                nodeHeaderColor = color;
                for (Node node : nodes) {
                    node.setHeaderBackgroundColor(nodeHeaderColor);
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }

            ImGui.sameLine();
        }
        style.setFrameBorderSize(1f);
        style.setColor(ImGuiCol.Button, oriVec4.x, oriVec4.y, oriVec4.z, oriVec4.w);
        style.setColor(ImGuiCol.ButtonActive, oriVec4Act.x, oriVec4Act.y, oriVec4Act.z, oriVec4Act.w);
        style.setColor(ImGuiCol.ButtonHovered, oriVec4Hover.x, oriVec4Hover.y, oriVec4Hover.z, oriVec4Hover.w);
        ImGui.endChild();
        ImGui.pushFont(Window.bigFont);
        ImGui.text("Node's body");
        ImGui.popFont();
        ImGui.setCursorPosX(Window.getCurrentWindow().getWidth() * 0.01f);
        ImGui.text("Background color");
        ImGui.setCursorPosX(Window.getCurrentWindow().getWidth() * 0.01f);
        ImGui.beginChild("NodeBodyBgColor", new ImVec2(-1, Math.min(vp.getWorkSizeY() * 0.075f, 50f)), ImGuiChildFlags.Border);
        oriVec4 = style.getColor(ImGuiCol.Button);
        oriVec4Act = style.getColor(ImGuiCol.ButtonActive);
        oriVec4Hover = style.getColor(ImGuiCol.ButtonHovered);

        if (!colors.contains(nodeBodyColor)) {
            colors.addFirst(nodeBodyColor);
        }
        content_region_avail = ImGui.getContentRegionAvail();
        desired_height = content_region_avail.y;
        child_size = new ImVec2(desired_height, desired_height);
        for (Vector4f color : colors) {
            if (color == nodeBodyColor) {
                style.setFrameBorderSize(2.5f);
            } else {
                style.setFrameBorderSize(1f);
            }

            style.setColor(ImGuiCol.Button, color.x, color.y, color.z, color.w);
            style.setColor(ImGuiCol.ButtonActive, color.x, color.y, color.z, color.w);
            style.setColor(ImGuiCol.ButtonHovered, color.x, color.y, color.z, color.w);
            style.setFrameRounding(Short.MAX_VALUE);


            if (ImGui.button("##color_swatch_btn_" + color.toString(), child_size.x, child_size.y)) {
                nodeBodyColor = color;
                for (Node node : nodes) {
                    node.setBodyBackgroundColor(nodeBodyColor);
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            }

            ImGui.sameLine();
        }
        style.setFrameBorderSize(1f);
        style.setColor(ImGuiCol.Button, oriVec4.x, oriVec4.y, oriVec4.z, oriVec4.w);
        style.setColor(ImGuiCol.ButtonActive, oriVec4Act.x, oriVec4Act.y, oriVec4Act.z, oriVec4Act.w);
        style.setColor(ImGuiCol.ButtonHovered, oriVec4Hover.x, oriVec4Hover.y, oriVec4Hover.z, oriVec4Hover.w);
        ImGui.endChild();
        // Pop style modifications
        ImGui.popStyleVar(2);
        ImGui.setWindowFontScale(1.0f);
        style.setFrameRounding(oriRound);

        style.setColor(ImGuiCol.Text, 255, 255, 255, 255);
        ImGui.end();
        ImGui.getIO().removeConfigFlags(ImGuiConfigFlags.ViewportsEnable);
    }
    public void reset(){
    }

    @Override
    public void init() {

    }

    @Override
    public void cleanUp() {

    }
    public void show(){
        visible.set(true);
        System.out.println(visible);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public NodeCustomizeGUI setNodes(List<Node> nodes) {
        this.nodes = nodes;

        if (nodes.isEmpty()) {
            return this;
        }

        Vector4f hc = new Vector4f(nodes.getFirst().getHeaderBackgroundColor());
        Vector4f bc = new Vector4f(nodes.getFirst().getBodyBackgroundColor());

        boolean sameNodeHeaderColor = true;
        boolean sameNodeBodyColor = true;

        for (Node n : nodes) {
            if (!hc.equals(n.getHeaderBackgroundColor())) {
                sameNodeHeaderColor = false;
            }
            if (!bc.equals(n.getBodyBackgroundColor())) {
                sameNodeBodyColor = false;
            }
        }

        if (sameNodeHeaderColor) {
            nodeHeaderColor = hc;
        }
        if (sameNodeBodyColor) {
            nodeBodyColor = bc;
        }
        boolean sameTitle = true;
        String firstTitle = nodes.getFirst().getTitle(); // you have title field on Node
        for (Node n : nodes) {
            if (!firstTitle.equals(n.getTitle())) {
                sameTitle = false;
                break;
            }
        }

        if (sameTitle) {
            nodeTitleBuffer.set(firstTitle != null ? firstTitle : "");
        } else {
            nodeTitleBuffer.set(""); // mixed titles -> show empty or some placeholder
        }

        // Allow editing only if at least one selected node allows title changes
        allowNodeTitleEditing = nodes.stream().anyMatch(Node::isAllowUserTitleChange);

        return this;
    }

    public void hide() {
        visible.set(false);
    }
}
