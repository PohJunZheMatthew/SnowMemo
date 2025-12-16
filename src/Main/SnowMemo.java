package Main;

import GUI.Events.*;
import GUI.GUIComponent;
import GUI.Label;
import Light.AmbientLight;
import Light.DirectionalLight;
import Main.FileChooser.FileChooser;
import Main.Shadow.ShadowMap;
import Main.Node.Node;
import Main.Theme.Theme;
import Main.User.UserGUIComponent;
import imgui.*;
import imgui.flag.*;
import imgui.type.ImBoolean;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.*;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;

public class SnowMemo {
    Window window;
    Thread updateThread;
    List<Renderable> renderable = new ArrayList<>();
    float mouseWheelVelocity = 0;
    public static Camera camera = new Camera();
    double preposx,preposy = 0.0;
    @SuppressWarnings("unused")
    static AmbientLight ambientLight = new AmbientLight(0.7f,0.7f,0.7f,1.0f);
    static float zoom = 6;
    float perZoom = 1.0f;
    static final int MAX_ZOOM = 10,MIN_ZOOM = -15;
    static DirectionalLight sun;
    @SuppressWarnings("unused")
    static int timeOfDay = 0;
    static FileChooser fileChooser;
    public static ShadowMap shadowMap;
    private static final Font quickSandFont;
    private static final Font sanserifFont;
    private AddMenu addMenu = new AddMenu(this);

    // Auto-save variables
    private static final long AUTO_SAVE_INTERVAL_MS = 30000; // 30 seconds
    private long lastAutoSaveTime = System.currentTimeMillis();
    private SaveIndicator saveIndicator;
    private static final byte[] emojiFontBytes;
    private static final byte[] notoEmojiFontBytes;
    private static final byte[] symbolaFontBytes;
    static{
        InputStream emojiFontInputStream = Objects.requireNonNull(SnowMemo.class.getResourceAsStream("Resources/Fonts/OpenMoji-black-glyf/OpenMoji-black-glyf.ttf"));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n = 0;
        while (true) {
            try {
                if ((n = emojiFontInputStream.read(tmp)) == -1) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            buffer.write(tmp, 0, n);
        }
        emojiFontBytes = buffer.toByteArray();

        emojiFontInputStream = Objects.requireNonNull(SnowMemo.class.getResourceAsStream("Resources/Fonts/Noto_Emoji/NotoEmoji-Regular.ttf"));
        buffer = new ByteArrayOutputStream();
        tmp = new byte[4096];
        n = 0;
        while (true) {
            try {
                if ((n = emojiFontInputStream.read(tmp)) == -1) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            buffer.write(tmp, 0, n);
        }
        notoEmojiFontBytes = buffer.toByteArray();

        emojiFontInputStream = Objects.requireNonNull(SnowMemo.class.getResourceAsStream("Resources/Fonts/symbola/Symbola.ttf"));
        buffer = new ByteArrayOutputStream();
        tmp = new byte[4096];
        n = 0;
        while (true) {
            try {
                if ((n = emojiFontInputStream.read(tmp)) == -1) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            buffer.write(tmp, 0, n);
        }
        symbolaFontBytes = buffer.toByteArray();
    }
    public static byte[] getEmojiFontBytes(){
        return emojiFontBytes;
    }
    static {
        try {
            quickSandFont = Font.createFont(Font.TRUETYPE_FONT,SnowMemo.class.getResourceAsStream("Resources/Fonts/QuicksandFont/Quicksand-VariableFont_wght.ttf"));
            sanserifFont = new Font("SansSerif",Font.PLAIN,12);
        } catch (FontFormatException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Black Theme
    private static final Theme blackTheme = new Theme(
            Color.BLACK,
            new Color[]{ Color.WHITE, Color.GRAY, Color.DARK_GRAY },
            new Color[]{ Color.WHITE },
            new Font[]{quickSandFont},
            Objects.requireNonNull(SnowMemo.class.getResourceAsStream("Resources/Fonts/SanSerif/Open_Sans/static/OpenSans-Regular.ttf")),
            Objects.requireNonNull(SnowMemo.class.getResourceAsStream("Resources/Fonts/SanSerif/Open_Sans/OpenSans-VariableFont_wdth,wght.ttf"))
    );

    // White Theme
    private static final Theme whiteTheme = new Theme(
            Color.WHITE,
            new Color[]{ Color.BLACK, Color.LIGHT_GRAY, Color.DARK_GRAY },
            new Color[]{ Color.BLACK } ,
            new Font[]{sanserifFont},
            Objects.requireNonNull(SnowMemo.class.getResourceAsStream("Resources/Fonts/SanSerif/Open_Sans/static/OpenSans-Regular.ttf")),
            Objects.requireNonNull(SnowMemo.class.getResourceAsStream("Resources/Fonts/SanSerif/Open_Sans/OpenSans-VariableFont_wdth,wght.ttf"))
    );

    public static Theme currentTheme = whiteTheme;
    public static boolean draggable = true;
    private MainMenuScreen menuScreen;
    private List<Memo> memos;

    public SnowMemo(){
        window = new Window("Snow Memo");
        fileChooser = new FileChooser();
        sun = new DirectionalLight(
                window,
                new Vector3f(0.0f, -1.0f, -1.0f).normalize(),
                new Vector4f(1, 1, 1, 1.0f),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                10f
        );

        try {
            shadowMap = new ShadowMap();
            shadowMap.setOrthoBounds(new ShadowMap.OrthoBounds(100f, 0.01f, 100f));
            shadowMap.setShadowDistance(75f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        memos = Memo.refreshMemos(window);
        renderable.add(new Baseplate().setVisible(true));

        try {
            window.setImGuiToRender(addMenu);
            saveIndicator = new SaveIndicator();
            window.setImGuiToRender(saveIndicator);
            init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        run();
    }

    public static void resetScroll() {
        zoom = 6;
    }

    public static byte[] getNotoEmojiFontBytes() {
        return notoEmojiFontBytes;
    }

    public static byte[] getSymbolaFontBytes() {
        return symbolaFontBytes;
    }

    public void init() throws Exception{
        GLFWErrorCallback.createPrint(System.out).set();
        renderable.forEach(mesh -> {
            try {
                mesh.init();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        window.KeyCallbacks.add(new GLFWKeyCallback() {
            @Override
            public void invoke(long windowHandle, int key, int scancode, int action, int mods) {
                if (action == GLFW_PRESS) {
                    // Ctrl+S or Cmd+S for manual save
                    if ((mods == GLFW_MOD_CONTROL || mods == GLFW_MOD_SUPER) && key == GLFW_KEY_S) {
                        if (Memo.getCurrentMemo() != null && !menuScreen.isVisible()) {
                            Memo.getCurrentMemo().quickSave();
                            saveIndicator.showSaved();
                            System.out.println("Manual save triggered");
                        }
                    }

                    if (mods == GLFW_MOD_SHIFT) {
                        if (key == GLFW_KEY_A && !menuScreen.isVisible()) {
                            addMenu.nodeTypes.clear();
                            for (Class<? extends Node> registeredNode : Node.getAllRegisteredNodes()) {
                                addMenu.nodeTypes.add(registeredNode);
                            }
                            addMenu.show();
                        }
                    }
                }
            }
        });

        menuScreen = new MainMenuScreen(window);
        menuScreen.init();
        renderable.add(menuScreen);
        new UserGUIComponent(window);
    }

    public void update() {
        draggable = !menuScreen.isVisible() && draggable;
        Updatable.updateAll(window);

        // Auto-save logic
        if (!menuScreen.isVisible() && Memo.getCurrentMemo() != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastAutoSaveTime >= AUTO_SAVE_INTERVAL_MS) {
                performAutoSave();
                lastAutoSaveTime = currentTime;
            }
        }

        if (mouseWheelVelocity != 0) {
            float zoomSpeed = 0.5f;
            float desiredZoom = zoom + mouseWheelVelocity * zoomSpeed;
            desiredZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, desiredZoom));
            float delta = desiredZoom - zoom;
            if (delta != 0) {
                if (camera.cameraMovement == Camera.CameraMovement.ZoomInAndOut) {
                    camera.move(new Vector3f(0, 0, -delta));
                } else if (camera.cameraMovement == Camera.CameraMovement.ScrollUpAndDown) {
                    camera.move(new Vector3f(0, delta, 0));
                }
                zoom = desiredZoom;
                perZoom = (zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM);
            }
            mouseWheelVelocity = 0;
        }
    }

    private void performAutoSave() {
        Memo currentMemo = Memo.getCurrentMemo();
        if (currentMemo != null && currentMemo.isLoaded()) {
            try {
                currentMemo.autoSave();
                saveIndicator.showAutoSaved();
                System.out.println("Auto-saved memo: " + currentMemo.getName());
            } catch (Exception e) {
                System.err.println("Auto-save failed: " + e.getMessage());
                saveIndicator.showError();
            }
        }
    }

    public void run(){
        updateThread = new Thread(){
            @Override
            public void run(){
                while (!glfwWindowShouldClose(window.window)){
                    update();
                    try {
                        this.sleep(1000/60);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
        updateThread.start();

        window.ScrollCallbacks.add(new GLFWScrollCallback() {
            @Override public void invoke (long win, double dx, double dy) {
                if (menuScreen.visible) return;
                boolean inGUI = false;
                for (Object guiComponent:GUIComponent.getGUIComponents(window)){
                    inGUI = ((GUIComponent)guiComponent).getHitBox().contains(window.mouseX,window.mouseY)&&((GUIComponent)guiComponent).isVisible();
                    if (inGUI) break;
                }
                if (!inGUI) mouseWheelVelocity = (float) dy;
            }
        });

        window.CursorPosCallbacks.add(new GLFWCursorPosCallback() {
            @Override
            public void invoke(long win, double xpos, double ypos) {
                if (glfwGetMouseButton(win, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS && draggable) {
                    double diffxpos = xpos - preposx;
                    double diffypos = preposy - ypos;
                    float dx = -(float) (diffxpos / window.width);
                    float dy = -(float) (diffypos / window.height);
                    float moveSpeed = 1.5f * (Math.max(10-zoom, 1.0f));
                    camera.move(new Vector3f(dx * moveSpeed, dy * moveSpeed, 0));
                }
                preposx = xpos;
                preposy = ypos;
                if (xpos >= 0 && xpos < window.getWidth() && ypos >= 0 && ypos < window.getHeight()) {
                    Vector3f worldPos = getWorldCoordinatesFromScreen(xpos, ypos);
                }
            }
        });

        window.show();
        while (!glfwWindowShouldClose(window.window)){
            List<Mesh> meshes = new ArrayList<>();
            for (Renderable renderable1 : renderable){
                if (renderable1 instanceof Mesh){
                    meshes.add((Mesh) renderable1);
                }
            }

            Vector3f sceneCenter = new Vector3f(2, -2.5f, 0);
            shadowMap.render(window, meshes, sceneCenter);

            Matrix4f lightSpaceMatrix = shadowMap.getLightSpaceMatrix();

            try {
                Mesh.sharedShaderProgram.bind();
                if (!Mesh.sharedShaderProgram.hasUniform("lightSpaceMatrix")) {
                    Mesh.sharedShaderProgram.createUniform("lightSpaceMatrix");
                }
                Mesh.sharedShaderProgram.setUniform("lightSpaceMatrix", lightSpaceMatrix);
                Mesh.sharedShaderProgram.unbind();
            } catch (Exception e) {
                System.err.println("Error setting light space matrix: " + e.getMessage());
            }
            window.render(renderable);
        }

        // Save before closing
        if (Memo.getCurrentMemo() != null) {
            System.out.println("Saving before exit...");
            Memo.getCurrentMemo().save(false);
        }

        cleanUp();
        window.close();
    }

    public void cleanUp(){
        renderable.forEach(Renderable::cleanUp);
    }

    public Vector3f getWorldCoordinatesFromScreen(double xpos, double ypos) {
        int[] winW = new int[1], winH = new int[1];
        int[] fbW  = new int[1], fbH  = new int[1];
        glfwGetWindowSize(window.window, winW, winH);
        glfwGetFramebufferSize(window.window, fbW, fbH);

        if (winW[0] == 0 || winH[0] == 0) return new Vector3f(0, 0, 0);

        float scaleX = (float) fbW[0] / (float) winW[0];
        float scaleY = (float) fbH[0] / (float) winH[0];
        float fbX = (float) xpos * scaleX;
        float fbY = (float) ypos * scaleY;

        float ndcX = (2.0f * fbX) / (float) fbW[0] - 1.0f;
        float ndcY = 1.0f - (2.0f * fbY) / (float) fbH[0];

        Vector4f clipNear = new Vector4f(ndcX, ndcY, -1f, 1f);
        Vector4f clipFar  = new Vector4f(ndcX, ndcY,  1f, 1f);

        Matrix4f projectionMatrix = window.getCurrentWindow().getProjectionMatrix();
        Matrix4f viewMatrix = camera.getViewMatrix();
        Matrix4f invViewProj = new Matrix4f(projectionMatrix).mul(viewMatrix).invert();

        Vector4f worldNear4 = new Vector4f(clipNear).mul(invViewProj);
        Vector4f worldFar4  = new Vector4f(clipFar).mul(invViewProj);

        if (worldNear4.w != 0f) worldNear4.div(worldNear4.w);
        if (worldFar4.w  != 0f) worldFar4.div(worldFar4.w);

        Vector3f rayStart = new Vector3f(worldNear4.x, worldNear4.y, worldNear4.z);
        Vector3f rayEnd   = new Vector3f(worldFar4.x,  worldFar4.y,  worldFar4.z);

        Vector3f rayDirection = new Vector3f(rayEnd).sub(rayStart);
        if (rayDirection.lengthSquared() == 0f) return new Vector3f(rayStart);

        rayDirection.normalize();

        float denom = rayDirection.z;
        if (Math.abs(denom) < 1e-6f) {
            return new Vector3f(rayStart.x, rayStart.y, 0f);
        }

        float t = -rayStart.z / denom;
        if (t < 0f) {
            return new Vector3f(rayStart.x, rayStart.y, 0f);
        }

        Vector3f worldPosition = new Vector3f(rayStart).add(new Vector3f(rayDirection).mul(t));
        return worldPosition;
    }

    // Save indicator ImGui component
    private static class SaveIndicator implements Renderable {
        private String message = "";
        private long displayUntil = 0;
        private boolean isError = false;

        public void showSaved() {
            message = "Saved";
            isError = false;
            displayUntil = System.currentTimeMillis() + 2000;
        }

        public void showAutoSaved() {
            message = "Auto-saved";
            isError = false;
            displayUntil = System.currentTimeMillis() + 2000;
        }

        public void showError() {
            message = "Save failed!";
            isError = true;
            displayUntil = System.currentTimeMillis() + 3000;
        }

        @Override
        public void render() {
            if (System.currentTimeMillis() > displayUntil || message.isEmpty()) {
                return;
            }

            ImGuiViewport vp = ImGui.getMainViewport();
            ImGui.setNextWindowPos(
                    vp.getWorkPosX() + vp.getWorkSizeX() - 150,
                    vp.getWorkPosY() + vp.getWorkSizeY() - 50,
                    ImGuiCond.Always
            );
            ImGui.setNextWindowSize(140, 40, ImGuiCond.Always);

            ImGui.begin("##SaveIndicator",
                    ImGuiWindowFlags.NoTitleBar |
                            ImGuiWindowFlags.NoResize |
                            ImGuiWindowFlags.NoMove |
                            ImGuiWindowFlags.NoScrollbar |
                            ImGuiWindowFlags.NoCollapse |
                            ImGuiWindowFlags.NoBackground
            );

            ImGuiStyle style = ImGui.getStyle();
            ImVec4 oldColor = style.getColor(ImGuiCol.Text);

            if (isError) {
                style.setColor(ImGuiCol.Text, 1.0f, 0.2f, 0.2f, 1.0f);
            } else {
                style.setColor(ImGuiCol.Text, 0.2f, 0.8f, 0.2f, 1.0f);
            }

            ImGui.text(message);

            style.setColor(ImGuiCol.Text, oldColor.x, oldColor.y, oldColor.z, oldColor.w);
            ImGui.end();
        }

        @Override
        public void init() {}

        @Override
        public void cleanUp() {}
    }

    private static class AddMenu implements Renderable {
        private ImBoolean visible = new ImBoolean(false);
        private List<Class<? extends Node>> nodeTypes = new ArrayList<>();
        private float x, y = 0;
        private SnowMemo app;

        public AddMenu(SnowMemo app) {
            this.app = app;
        }

        @Override
        public void render() {
            if (!visible.get()) return;

            ImGuiViewport vp = ImGui.getMainViewport();
            ImGui.setNextWindowPos(vp.getWorkSizeX() * x, vp.getWorkSizeY() * y, ImGuiCond.Appearing);
            ImGui.setNextWindowSize(vp.getWorkSizeX() * 0.15f, vp.getWorkSizeY() * 0.3f, ImGuiCond.Always);

            ImGui.begin("Add node", visible, ImGuiWindowFlags.NoResize);
            ImGuiStyle style = ImGui.getStyle();

            ImVec4 oldTextColor = new ImVec4(style.getColor(ImGuiCol.Text));

            Color themeColor = SnowMemo.currentTheme.getSecondaryColors()[0];
            style.setColor(
                    ImGuiCol.Text,
                    themeColor.getRed() / 255f,
                    themeColor.getGreen() / 255f,
                    themeColor.getBlue() / 255f,
                    themeColor.getAlpha() / 255f
            );

            ImGui.pushFont(Window.bigFont);
            ImGui.text("Add nodes");
            ImGui.popFont();

            style.setColor(
                    ImGuiCol.Text,
                    oldTextColor.x,
                    oldTextColor.y,
                    oldTextColor.z,
                    oldTextColor.w
            );

            for (Class<? extends Node> nodeClass : nodeTypes) {
                if (ImGui.button(nodeClass.getSimpleName(), -1, 0)) {
                    spawnNode(nodeClass);
                    visible.set(false);

                    // Mark memo as dirty when node is added
                    if (Memo.getCurrentMemo() != null) {
                        Memo.getCurrentMemo().markDirty();
                    }
                }

                if (ImGui.isItemHovered()) {
                    ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                }
            }

            ImGui.end();
        }

        @Override
        public void init() {}

        @Override
        public void cleanUp() {}

        public void show() {
            visible.set(false);
            Window w = Window.getCurrentWindow();
            ImGuiViewport vp = ImGui.getMainViewport();

            int[] winW = new int[1], winH = new int[1];
            int[] fbW = new int[1], fbH = new int[1];

            glfwGetWindowSize(w.window, winW, winH);
            glfwGetFramebufferSize(w.window, fbW, fbH);

            float scaleX = (float) winW[0] / fbW[0];
            float scaleY = (float) winH[0] / fbH[0];

            float logicalMouseX = (float) w.getMouseX() * scaleX;
            float logicalMouseY = (float) w.getMouseY() * scaleY;

            x = logicalMouseX / vp.getWorkSizeX();
            y = logicalMouseY / vp.getWorkSizeY();

            visible.set(true);
        }

        public void hide(){
            visible.set(false);
        }

        public ImBoolean getVisible(){
            return visible;
        }

        private void spawnNode(Class<? extends Node> nodeClass) {
            try {
                Window window = Window.getCurrentWindow();
                Node newNode = nodeClass
                        .getDeclaredConstructor(Window.class)
                        .newInstance(window);
                System.out.println(newNode);
                Vector3f worldPos = app.getWorldCoordinatesFromScreen(
                        window.getLogicalMouseX(),
                        window.getLogicalMouseY()
                );
                newNode.setPosition(worldPos);
                app.renderable.add(newNode);
                newNode.init();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        System.setProperty("https.protocols", "TLSv1.2");
        try {
            System.setProperty("apple.awt.application.name", "SnowMemo");
            System.setProperty("java.awt.headless", "true");
            new SnowMemo();
        }
        catch (OutOfMemoryError e){
            System.gc();
            System.out.println("Java OutOfMemoryError: Did you set memory limits?");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("QUIT!");
    }

}