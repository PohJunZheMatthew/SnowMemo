package Main;

import GUI.GUIComponent;
import Main.Node.Node;
import Main.Settings.Settings;
import Main.Skybox.Skybox;
import Main.User.User;
import imgui.*;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiFreeTypeBuilderFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.*;

public class Window {
    public Camera camera;
    protected long window;
    protected int width,height;
    boolean vSync = (Settings.getValue("SnowMemo.Defaults.Vsync")!=null) ? ((boolean) Objects.requireNonNull(Settings.getValue("SnowMemo.Defaults.Vsync"))):false;
    public static final float FOV = (float) Math.toRadians(60f);
    static Window currentWindow;
    public static final float Z_NEAR = 0.01f;
    private boolean events = true;
    public static final float Z_FAR = 100.f;

    protected Matrix4f projectionMatrix;
    public final List<GLFWCursorPosCallback> CursorPosCallbacks = new ArrayList<GLFWCursorPosCallback>();
    public final List<GLFWMouseButtonCallback> MouseButtonCallbacks = new ArrayList<>();
    public final List<GLFWKeyCallback> KeyCallbacks = new ArrayList<>();
    public final List<GLFWCharCallback> CharCallbacks = new ArrayList<>();
    public final List<GLFWDropCallback> DropCallbacks = new ArrayList<>();
    public final List<GLFWScrollCallback> ScrollCallbacks = new ArrayList<>();
    protected double mouseX;
    protected double mouseY;
    public double getMouseX() { return mouseX; }
    public double getMouseY() { return mouseY; }
    private Skybox skybox;
    private final ImGuiImplGlfw imGuiImplGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiImplGl3 = new ImGuiImplGl3();
    private final ImGuiLayer imGuiLayer = new ImGuiLayer();
    public static ImFont bigFont;
    public static ImFont h1Font,h2Font,h3Font,h4Font,h5Font,h6Font;
    public static ImFont monoFont;
    private static List<Renderable> imGUIRenderables = new ArrayList<Renderable>();
    public void initImGUI() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        io.setIniFilename(null);

        byte[] mainFontBytes = SnowMemo.currentTheme.getMainFontBytes();
        byte[] openMojiFontBytes = SnowMemo.getEmojiFontBytes();
        byte[] monoFontBytes = SnowMemo.currentTheme.getMainMonoFontBytes();
        byte[] notoEmojiFontBytes = SnowMemo.getNotoEmojiFontBytes();
        byte[] symbolaFontBytes = SnowMemo.getSymbolaFontBytes();

        System.out.println("[DEBUG] Font bytes loaded:");
        System.out.println("  Main font: " + (mainFontBytes != null ? mainFontBytes.length + " bytes" : "NULL"));
        System.out.println("  OpenMoji font: " + (openMojiFontBytes != null ? openMojiFontBytes.length + " bytes" : "NULL"));
        System.out.println("  Mono font: " + (monoFontBytes != null ? monoFontBytes.length + " bytes" : "NULL"));
        System.out.println("  NotoEmoji font: " + (notoEmojiFontBytes != null ? notoEmojiFontBytes.length + " bytes" : "NULL"));
        System.out.println("  Symbola font: " + (symbolaFontBytes != null ? symbolaFontBytes.length + " bytes" : "NULL"));

        ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
        builder.addRanges(io.getFonts().getGlyphRangesDefault());

        // Only add ranges that fit in BMP (below 0x10000) for the loop
        // Emoji ranges 0x1F000+ are added via builder.addText() above instead
        int[] symbolRanges = {
                0x2000, 0x206F, 0x2190, 0x21FF, 0x2200, 0x22FF, 0x2300, 0x23FF,
                0x2460, 0x24FF, 0x2500, 0x257F, 0x2580, 0x259F, 0x25A0, 0x25FF,
                0x2600, 0x26FF, 0x2700, 0x27BF, 0x2900, 0x297F, 0x2B00, 0x2BFF,
                0xFE10, 0xFE1F,
                0 // Null terminator
        };

        for(int i = 0; i < symbolRanges.length && symbolRanges[i] != 0; i += 2) {
            for(int k = symbolRanges[i]; k <= symbolRanges[i + 1]; ++k) {
                builder.addChar((char) k);
            }
        }
        short[] finalRanges = builder.buildRanges();

        System.out.println("[DEBUG] Glyph ranges built: " + finalRanges.length + " entries");
        System.out.println("[DEBUG] Note: Emojis added via builder.addText() string");

        ImFontConfig mainCfg = new ImFontConfig();
        ImFont mainFont = io.getFonts().addFontFromMemoryTTF(mainFontBytes, 32f, mainCfg);

        if (symbolaFontBytes != null) {
            ImFontConfig symbolaCfg = new ImFontConfig();
            symbolaCfg.setMergeMode(true);
            io.getFonts().addFontFromMemoryTTF(symbolaFontBytes, 32f, symbolaCfg, finalRanges);
        }

        if (openMojiFontBytes != null) {
            ImFontConfig openMojiCfg = new ImFontConfig();
            openMojiCfg.setMergeMode(true);
            io.getFonts().addFontFromMemoryTTF(openMojiFontBytes, 32f, openMojiCfg, finalRanges);
        }

        if (notoEmojiFontBytes != null) {
            ImFontConfig notoEmojiCfg = new ImFontConfig();
            notoEmojiCfg.setMergeMode(true);
            io.getFonts().addFontFromMemoryTTF(notoEmojiFontBytes, 32f, notoEmojiCfg, finalRanges);
        }

        for (int i = 6; i > 0; i--) {
            float fontSize = 32f + 32f * (6 - i) / 6f;
            try {
                Field headerField = Window.class.getField("h" + i + "Font");
                ImFontConfig cfg = new ImFontConfig();
                ImFont headerFont = io.getFonts().addFontFromMemoryTTF(mainFontBytes, fontSize, cfg);
                headerField.set(null, headerFont);

                if (symbolaFontBytes != null) {
                    ImFontConfig mergeSymbola = new ImFontConfig();
                    mergeSymbola.setMergeMode(true);
                    io.getFonts().addFontFromMemoryTTF(symbolaFontBytes, fontSize, mergeSymbola, finalRanges);
                }

                if (openMojiFontBytes != null) {
                    ImFontConfig mergeOpenMoji = new ImFontConfig();
                    mergeOpenMoji.setMergeMode(true);
                    io.getFonts().addFontFromMemoryTTF(openMojiFontBytes, fontSize, mergeOpenMoji, finalRanges);
                }

                if (notoEmojiFontBytes != null) {
                    ImFontConfig mergeNotoEmoji = new ImFontConfig();
                    mergeNotoEmoji.setMergeMode(true);
                    io.getFonts().addFontFromMemoryTTF(notoEmojiFontBytes, fontSize, mergeNotoEmoji, finalRanges);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        ImFontConfig bigCfg = new ImFontConfig();
        bigFont = io.getFonts().addFontFromMemoryTTF(mainFontBytes, 48f, bigCfg);
        if (symbolaFontBytes != null) {
            ImFontConfig mergeSymbolaBig = new ImFontConfig();
            mergeSymbolaBig.setMergeMode(true);
            io.getFonts().addFontFromMemoryTTF(symbolaFontBytes, 48f, mergeSymbolaBig, finalRanges);
        }
        if (openMojiFontBytes != null) {
            ImFontConfig mergeOpenMojiBig = new ImFontConfig();
            mergeOpenMojiBig.setMergeMode(true);
            io.getFonts().addFontFromMemoryTTF(openMojiFontBytes, 48f, mergeOpenMojiBig, finalRanges);
        }
        if (notoEmojiFontBytes != null) {
            ImFontConfig mergeNotoEmojiBig = new ImFontConfig();
            mergeNotoEmojiBig.setMergeMode(true);
            io.getFonts().addFontFromMemoryTTF(notoEmojiFontBytes, 48f, mergeNotoEmojiBig, finalRanges);
        }

        ImFontConfig monoCfg = new ImFontConfig();
        monoFont = io.getFonts().addFontFromMemoryTTF(monoFontBytes, 32f, monoCfg);
        if (symbolaFontBytes != null) {
            ImFontConfig mergeSymbolaMono = new ImFontConfig();
            mergeSymbolaMono.setMergeMode(true);
            io.getFonts().addFontFromMemoryTTF(symbolaFontBytes, 32f, mergeSymbolaMono, finalRanges);
        }
        if (openMojiFontBytes != null) {
            ImFontConfig mergeOpenMojiMono = new ImFontConfig();
            mergeOpenMojiMono.setMergeMode(true);
            io.getFonts().addFontFromMemoryTTF(openMojiFontBytes, 32f, mergeOpenMojiMono, finalRanges);
        }
        if (notoEmojiFontBytes != null) {
            ImFontConfig mergeNotoEmojiMono = new ImFontConfig();
            mergeNotoEmojiMono.setMergeMode(true);
            io.getFonts().addFontFromMemoryTTF(notoEmojiFontBytes, 32f, mergeNotoEmojiMono, finalRanges);
        }

        io.getFonts().build();

        System.out.println("[DEBUG] Font atlas built successfully");

        imGuiImplGlfw.init(window, true);
        imGuiImplGl3.init("#version 330");

        float[] sx = new float[1], sy = new float[1];
        glfwGetWindowContentScale(window, sx, sy);
        ImGui.getIO().setFontGlobalScale(sx[0] * 0.5f);

        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(7.5f);
        style.setFrameRounding(7.5f);
        style.setPopupRounding(7.5f);
        style.setTabRounding(7.5f);
        style.setGrabRounding(7.5f);
        style.setChildRounding(7.5f);

        System.out.println("[DEBUG] ImGui initialized successfully");
        System.out.println("[DEBUG] Main font loaded: " + (mainFont != null));
        System.out.println("[DEBUG] Big font loaded: " + (bigFont != null));
        System.out.println("[DEBUG] Mono font loaded: " + (monoFont != null));
    }

    // Call this method in your render loop to test animal emoji rendering
    public void renderAnimalEmojiTest() {
        ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.0f, 0.0f, 1.0f); // Force red text

        if (ImGui.begin("Animal Emoji Test", ImGuiWindowFlags.AlwaysAutoResize)) {
            System.out.println("[DEBUG] Rendering test window...");

            // Check current font
            ImFont currentFont = ImGui.getFont();
            System.out.println("[DEBUG] Current font: " + (currentFont != null ? "Loaded" : "NULL"));
            if (currentFont != null) {
                System.out.println("[DEBUG] Font size: " + currentFont.getFontSize());
                System.out.println("[DEBUG] Font scale: " + ImGui.getFontSize());
            }

            // Test character limits
            ImGui.textColored(1, 1, 0, 1, "ImGui Version: 1.90.9");
            ImGui.textColored(1, 0.5f, 0, 1, "WCHAR32 Support: UNKNOWN (likely NO)");

            ImGui.separator();
            ImGui.text("Testing Animal Emoji Rendering:");
            ImGui.text("If you cannot see ANY text, check console");
            ImGui.separator();

            // Test BMP emojis (U+2000 to U+FFFF - should work)
            ImGui.text("BMP Symbols (should work):");
            ImGui.text("Hearts & symbols: ♥ ★ ☀ ☂ ☺ ♠ ♣ ♦");
            ImGui.text("Arrows: → ← ↑ ↓ ⇒ ⇐ ⇑ ⇓");
            ImGui.text("Math: ∀ ∃ ∈ ∑ ∏ √ ∞");
            ImGui.text("Dingbats: ✂ ✈ ✉ ✏ ✓ ✗");

            ImGui.separator();
            ImGui.text("Supplementary Plane (requires WCHAR32):");
            ImGui.text("Animal emojis: 🐵 🐶 🐱 🐭 🐰");
            ImGui.text("If you see ?? above, WCHAR32 is NOT enabled");

            ImGui.separator();
            ImGui.textColored(1, 0, 0, 1, "DIAGNOSIS:");
            ImGui.text("Emoji fonts ARE loaded (1.4MB + 418KB)");
            ImGui.text("But ImGui can't render 32-bit codepoints");

            ImGui.separator();
            ImGui.textColored(0, 1, 1, 1, "SOLUTION OPTIONS:");
            ImGui.text("1. Use imgui-java with WCHAR32 enabled");
            ImGui.text("2. Use image-based emoji textures instead");
            ImGui.text("3. Limit to BMP emojis only (very limited)");

            System.out.println("[DEBUG] Window rendered");
        }
        ImGui.end();

        ImGui.popStyleColor();
    }
    public Window(){}
    public Window(String title){
        camera = SnowMemo.camera;
        currentWindow = this;
        if (!GLFW.glfwInit()) throw new RuntimeException("Can't init glfw");
        long monitor =GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            throw new IllegalStateException("No primary monitor found");
        }
        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
        if (vidMode == null) {
            throw new IllegalStateException("Failed to get video mode");
        }
        int w = vidMode.width()/2;
        int h = vidMode.height()/2;
        this.width = w;
        this.height = h;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GL11.GL_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GL11.GL_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_CLASS_NAME, "Snow Memo");
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_INSTANCE_NAME, "Snow Memo");
        GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, 24);
        boolean maximised = false;
        if (w == 0 || h == 0) {
            width = 100;
            height = 100;
            GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, GLFW.GLFW_TRUE);
            maximised = true;
        }
        window = GLFW.glfwCreateWindow(w, h, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) throw new RuntimeException("Failed to create window");
        GLFW.glfwSetWindowSizeLimits(window,w/2,h/2,GLFW.GLFW_DONT_CARE,GLFW.GLFW_DONT_CARE);
        GLFW.glfwSetFramebufferSizeCallback(window, ((window, width, height) -> {
            this.width = width;
            this.height = height;
        }));
        if (maximised) GLFW.glfwMaximizeWindow(window);
        else {
            GLFW.glfwSetWindowPos(window, (vidMode.width() - w) / 2, (vidMode.height() - h) / 2);
            GLFW.glfwSetWindowAspectRatio(window,vidMode.width(),vidMode.height());
        }
        GLFW.glfwMakeContextCurrent(window);
        if (isvSync()) GLFW.glfwSwapInterval(1);
        IntBuffer fbWidth = MemoryUtil.memAllocInt(1);
        IntBuffer fbHeight = MemoryUtil.memAllocInt(1);
        GLFW.glfwGetFramebufferSize(window, fbWidth, fbHeight);
        this.width = fbWidth.get(0);
        this.height = fbHeight.get(0);
        MemoryUtil.memFree(fbWidth);
        MemoryUtil.memFree(fbHeight);
        GL.createCapabilities();

        glClearColor(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL_CULL_FACE);
        float aspectRatio = (float) width / height;
        projectionMatrix = new Matrix4f().perspective(FOV, aspectRatio,
                Z_NEAR, Z_FAR);

        // Set up the callback forwarders that will call registered callbacks
        GLFW.glfwSetCursorPosCallback(window,((windowHandle, xpos, ypos) -> {
            logicalMouseX = (float) xpos;
            logicalMouseY = (float) ypos;
            float[] sx = new float[1];
            float[] sy = new float[1];
            glfwGetWindowContentScale(windowHandle, sx, sy);

            mouseX = xpos * sx[0];
            mouseY = ypos * sy[0];
            if (!events) return;
            CursorPosCallbacks.forEach(c -> {
                try {
                    c.invoke(windowHandle, xpos, ypos);
                } catch (Exception e) {
                    System.err.println("Error in cursor position callback: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }));

        GLFW.glfwSetMouseButtonCallback(window,(long windowHandle, int button, int action, int mods) -> {
            if (!events) return;
            // Forward to all registered callbacks
            for (GLFWMouseButtonCallback callback : MouseButtonCallbacks) {
                try {
                    callback.invoke(windowHandle, button, action, mods);
                } catch (Exception e) {
                    System.err.println("Error in mouse button callback: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        GLFW.glfwSetKeyCallback(window, (long windowHandle, int key, int scancode, int action, int mods) -> {
            if (!events) return;
            for (GLFWKeyCallback callback : KeyCallbacks) {
                try {
                    callback.invoke(windowHandle, key, scancode, action, mods);
                } catch (Exception e) {
                    System.err.println("Error in key callback: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        GLFW.glfwSetCharCallback(window, (long windowHandle, int codepoint) -> {
            if (!events) return;
            for (GLFWCharCallback callback : CharCallbacks) {
                try {
                    callback.invoke(windowHandle, codepoint);
                } catch (Exception e) {
                    System.err.println("Error in char callback: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        GLFW.glfwSetDropCallback(window,(long windowHandle, int count, long names)->{
            if (!events) return;
            for (GLFWDropCallback callback: DropCallbacks) {
                callback.invoke(windowHandle,count,names);
            }
        });
        GLFW.glfwSetScrollCallback(window, (long windowHandle, double xoffset, double yoffset) -> {
            if (!events) return;
            for (GLFWScrollCallback callback : ScrollCallbacks) {
                try {
                    callback.invoke(windowHandle, xoffset, yoffset);
                } catch (Exception e) {
                    System.err.println("Error in scroll callback: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        System.out.println("OpenGL version: " + GL11.glGetString(GL11.GL_VERSION));
        System.out.println("Renderer: " + GL11.glGetString(GL11.GL_RENDERER));
        System.out.println("GLSL version: " + GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));

        System.out.println("Window created with handle: " + window);
        skybox = new Skybox(new Vector4f(1f),new Vector4f(0.5f),new Vector4f(1));
        initImGUI();
        System.out.println("ImGui version: " + ImGui.getVersion());
        System.out.println("ImGuiImplGl3 class: " + imGuiImplGl3.getClass().getName());
        System.out.println("ImGuiImplGlfw class: " + imGuiImplGlfw.getClass().getName());

    }

    public void show(){
        GLFW.glfwShowWindow(window);
    }

    public boolean isvSync() {
        return vSync;
    }
    private static final float REFERENCE_WIDTH = 1920.0f; // Adjust to your design size
    private static final float REFERENCE_HEIGHT = 1080.0f;
    public void render(List<Renderable> meshes) {
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwPollEvents();
        IntBuffer fbWidth = MemoryUtil.memAllocInt(1);
        IntBuffer fbHeight = MemoryUtil.memAllocInt(1);
        GLFW.glfwGetFramebufferSize(window, fbWidth, fbHeight);
        GL11.glViewport(0, 0, fbWidth.get(0), fbHeight.get(0));
        float aspect = (float) fbWidth.get(0) / fbHeight.get(0);
        projectionMatrix = new Matrix4f().perspective(FOV, aspect, Z_NEAR, Z_FAR);
        MemoryUtil.memFree(fbWidth);
        MemoryUtil.memFree(fbHeight);

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glClearColor(1f, 1f, 1f, 1.0f);

        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);

        skybox.render();
        for (Renderable m : meshes) {
            if (m instanceof Mesh) ((Mesh)m).render(camera);
        }
        for (Node node : Node.getAllNodes(this)) {
            node.render();
        }

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        GUIComponent.renderGUIs(this);

        while (glGetError() != GL_NO_ERROR);

        glUseProgram(0);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_STENCIL_TEST);
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glDepthMask(false);
        glDisable(GL_SCISSOR_TEST);
        int[] windowWidth = new int[1], windowHeight = new int[1];
        glfwGetWindowSize(window, windowWidth, windowHeight);

// Calculate scale based on window size change
        float scaleX = windowWidth[0] / REFERENCE_WIDTH;
        float scaleY = windowHeight[0] / REFERENCE_HEIGHT;
        float scale = Math.min(scaleX, scaleY); // Use minimum to maintain aspect ratio

        ImGui.getIO().setFontGlobalScale(scale);

// Also scale your style if needed:
        ImGuiStyle style = ImGui.getStyle();
        float baseRounding = 10f;
        style.setWindowRounding(baseRounding * scale);
        style.setFrameRounding(baseRounding * scale);
        style.setPopupRounding(baseRounding * scale);
        style.setTabRounding(baseRounding * scale);
        style.setGrabRounding(baseRounding * scale);
        style.setChildRounding(baseRounding * scale);
        style.setWindowBorderSize(1f);
        style.setColor(ImGuiCol.Border,SnowMemo.currentTheme.getSecondaryColors()[0].getRed(),SnowMemo.currentTheme.getSecondaryColors()[0].getGreen(),SnowMemo.currentTheme.getSecondaryColors()[0].getBlue(),SnowMemo.currentTheme.getSecondaryColors()[0].getAlpha());
        style.setColor(ImGuiCol.WindowBg,SnowMemo.currentTheme.getMainColor().getRed(),SnowMemo.currentTheme.getMainColor().getGreen(),SnowMemo.currentTheme.getMainColor().getBlue(),SnowMemo.currentTheme.getMainColor().getAlpha());
        imGuiImplGl3.newFrame();
        imGuiImplGlfw.newFrame();
        ImGui.getIO().removeConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        ImGui.newFrame();
        User.getLogInLayout().render();
        User.getSignUpLayout().render();
        User.getUserMenuLayout().render();
        User.getSettingsLayout().render();
        renderAnimalEmojiTest();
        for (Renderable guiRenderable : new ArrayList<>(imGUIRenderables)) {
            guiRenderable.render();
        }
        events = !ImGui.getIO().getWantCaptureMouse()&&!ImGui.isAnyItemHovered() && !ImGui.isAnyItemActive();

        ImGui.render();
        imGuiImplGl3.renderDrawData(ImGui.getDrawData());
        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            final long backupWindowPtr = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();
            GLFW.glfwMakeContextCurrent(backupWindowPtr);
        }
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.println("OpenGL error: " + error);
        }
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glEnable(GL_STENCIL_TEST);
        glDepthMask(true);

        GLFW.glfwSwapBuffers(window);
    }
    public void close(){
        // Clean up GUI components for this window
        GUIComponent.cleanupWindow(this);
        ImGui.destroyContext();
        Callbacks.glfwFreeCallbacks(window);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwSetWindowShouldClose(window,true);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }

    public Matrix4f getProjectionMatrix(){
        return projectionMatrix;
    }

    public static Window getCurrentWindow(){
        return currentWindow;
    }

    public int getWidth() {
        return width;
    }

    public Window setWidth(int width) {
        this.width = width;
        return this;
    }

    public int getHeight() {
        return height;
    }

    public Window setHeight(int height) {
        this.height = height;
        return this;
    }

    @Deprecated(since="6.7")
    public Long getWindow(){
        return window;
    }

    /**
     * Get the GLFW window handle for this window.
     * This is used for event handling and window-specific operations.
     */
    public long getWindowHandle(){
        return window;
    }
    public Vector3f getCursorWorldPos(){
        // --- 1) Get window size (logical window coords) and framebuffer size (physical pixels)
        int[] winW = new int[1], winH = new int[1];
        int[] fbW  = new int[1], fbH  = new int[1];
        glfwGetWindowSize(window, winW, winH);
        glfwGetFramebufferSize(window, fbW, fbH);

        // Guard against divide by zero
        if (winW[0] == 0 || winH[0] == 0) return new Vector3f(0, 0, 0);

        /* Convert cursor (window coords) -> frame buffer coords (handles HiDPI) */
        float fbX = (float) mouseX;
        float fbY = (float) mouseY;

        // --- 2) NDC (-1..1)
        float ndcX = (2.0f * fbX) / (float) fbW[0] - 1.0f;
        float ndcY = 1.0f - (2.0f * fbY) / (float) fbH[0]; // flip Y for OpenGL

        // --- 3) Clip-space positions for near & far
        Vector4f clipNear = new Vector4f(ndcX, ndcY, -1f, 1f);
        Vector4f clipFar  = new Vector4f(ndcX, ndcY,  1f, 1f);

        // --- 4) Build inverse view-projection
        Matrix4f projectionMatrix = getProjectionMatrix();
        Matrix4f viewMatrix = camera.getViewMatrix(); // should be the camera view matrix
        Matrix4f invViewProj = new Matrix4f(projectionMatrix).mul(viewMatrix).invert();

        // --- 5) Transform clip -> world (and perspective divide)
        Vector4f worldNear4 = new Vector4f(clipNear).mul(invViewProj);
        Vector4f worldFar4  = new Vector4f(clipFar).mul(invViewProj);

        if (worldNear4.w != 0f) worldNear4.div(worldNear4.w);
        if (worldFar4.w  != 0f) worldFar4.div(worldFar4.w);

        Vector3f rayStart = new Vector3f(worldNear4.x, worldNear4.y, worldNear4.z);
        Vector3f rayEnd   = new Vector3f(worldFar4.x,  worldFar4.y,  worldFar4.z);

        Vector3f rayDirection = new Vector3f(rayEnd).sub(rayStart);
        if (rayDirection.lengthSquared() == 0f) return new Vector3f(rayStart);

        rayDirection.normalize();

        // --- 6) Intersect with ground plane z = 0 (plane normal (0,0,1))
        // For plane z=0 you can use simplified formula:
        // t = (0 - rayStart.z) / rayDirection.z
        float denom = rayDirection.z;
        if (Math.abs(denom) < 1e-6f) {
            // Ray parallel to plane → return projection of start onto plane (or some fallback)
            return new Vector3f(rayStart.x, rayStart.y, 0f);
        }

        float t = -rayStart.z / denom;
        if (t < 0f) {
            // Intersection is behind the camera; choose fallback (here we project start)
            return new Vector3f(rayStart.x, rayStart.y, 0f);
        }

        Vector3f worldPosition = new Vector3f(rayStart).add(new Vector3f(rayDirection).mul(t));
        // NOTE: do NOT multiply coordinates by 2 here — that caused the offset/scale.
        return worldPosition;
    }

    public float getAspectRatio() {
        return width/height;
    }
    /**
     * Projects the current mouse cursor onto a plane at the given Z coordinate.
     * @param planeZ The Z coordinate of the plane to project onto.
     * @return The world position of the cursor on that Z plane.
     */
    public Vector3f getCursorWorldPosAtZ(float planeZ) {
        // --- 1) Get window size and framebuffer size
        int[] winW = new int[1], winH = new int[1];
        int[] fbW  = new int[1], fbH  = new int[1];
        glfwGetWindowSize(window, winW, winH);
        glfwGetFramebufferSize(window, fbW, fbH);

        if (winW[0] == 0 || winH[0] == 0) return new Vector3f(0, 0, planeZ);

        // --- 2) Convert cursor to framebuffer coords (HiDPI aware)
        float fbX = (float) mouseX;
        float fbY = (float) mouseY;

        // --- 3) Convert to NDC (-1..1)
        float ndcX = (2.0f * fbX) / fbW[0] - 1.0f;
        float ndcY = 1.0f - (2.0f * fbY) / fbH[0]; // flip Y for OpenGL

        // --- 4) Clip-space positions for near & far
        Vector4f clipNear = new Vector4f(ndcX, ndcY, -1f, 1f);
        Vector4f clipFar  = new Vector4f(ndcX, ndcY,  1f, 1f);

        // --- 5) Inverse view-projection
        Matrix4f invViewProj = new Matrix4f(getProjectionMatrix()).mul(camera.getViewMatrix()).invert();

        // --- 6) Transform clip -> world
        Vector4f worldNear4 = new Vector4f(clipNear).mul(invViewProj);
        Vector4f worldFar4  = new Vector4f(clipFar).mul(invViewProj);
        if (worldNear4.w != 0f) worldNear4.div(worldNear4.w);
        if (worldFar4.w  != 0f) worldFar4.div(worldFar4.w);

        Vector3f rayStart = new Vector3f(worldNear4.x, worldNear4.y, worldNear4.z);
        Vector3f rayEnd   = new Vector3f(worldFar4.x,  worldFar4.y,  worldFar4.z);

        // --- 7) Build ray
        Vector3f rayDir = new Vector3f(rayEnd).sub(rayStart).normalize();

        // --- 8) Intersect ray with plane Z = planeZ
        float denom = rayDir.z;
        if (Math.abs(denom) < 1e-6f) {
            // Parallel → just return ray start projected onto plane
            return new Vector3f(rayStart.x, rayStart.y, planeZ);
        }

        float t = (planeZ - rayStart.z) / denom;
        if (t < 0f) {
            // Intersection behind camera → fallback
            return new Vector3f(rayStart.x, rayStart.y, planeZ);
        }

        return new Vector3f(rayStart).fma(t, rayDir); // rayStart + t * rayDir
    }
    public Vector3f convert3DTo2D(Vector3f worldPos) {
        // --- 1) Get window size and framebuffer size (necessary for viewport info)
        // Use arrays to retrieve values from native GLFW functions
        int[] winW = new int[1], winH = new int[1];
        int[] fbW  = new int[1], fbH  = new int[1];
        glfwGetWindowSize(window, winW, winH);
        glfwGetFramebufferSize(window, fbW, fbH);

        int viewportX = 0;
        int viewportY = 0;
        // The viewport size should match the framebuffer size for correct pixel mapping
        int viewportWidth = fbW[0];
        int viewportHeight = fbH[0];

        // Combine Model-View and Projection matrices (MVp matrix)
        // JOML methods often use the "parameter as return value pattern"
        // so we create a new matrix to store the result of the multiplication chain.
        Matrix4f mvpMatrix = new Matrix4f();
        mvpMatrix.set(getProjectionMatrix()).mul(camera.getViewMatrix());

        // --- 2) Use the JOML project method with the correct Vector4f destination
        Vector4f screenCoordsDest = new Vector4f();

        // The project method signature expects a destination Vector4f
        mvpMatrix.project(
                worldPos.x, worldPos.y, worldPos.z,         // The input 3D world position
                new int[]{viewportX, viewportY, viewportWidth, viewportHeight,}, // The viewport dimensions
                screenCoordsDest                            // The output destination Vector4f
        );

        // The x and y components now hold the 2D pixel coordinates.
        // The z component holds the depth (0.0=near, 1.0=far).
        // The w component is used internally for homogeneous division.

        // Return as Vector3f if you need the depth component, or a Vector2f otherwise
        return new Vector3f(screenCoordsDest.x, screenCoordsDest.y, screenCoordsDest.z);
    }
    public void setImGuiToRender(Renderable renderable){
        imGUIRenderables.add(renderable);
    }
    public void unregisterImGuiToRender(Renderable renderable){
        imGUIRenderables.remove(renderable);
    }
    public Vector2f getMouseFramebufferPos() {
        int[] winW = new int[1], winH = new int[1];
        int[] fbW = new int[1], fbH = new int[1];

        glfwGetWindowSize(window, winW, winH);
        glfwGetFramebufferSize(window, fbW, fbH);

        float scaleX = (float) fbW[0] / winW[0];
        float scaleY = (float) fbH[0] / winH[0];

        return new Vector2f(
                (float) mouseX * scaleX,
                (float) mouseY * scaleY
        );
    }
    private float logicalMouseX,logicalMouseY = 0;

    public float getLogicalMouseX() {
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        glfwGetCursorPos(window, xpos, ypos);
        logicalMouseX = (float) xpos[0];
        logicalMouseY = (float) ypos[0];
        return logicalMouseX;
    }

    public float getLogicalMouseY() {
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        glfwGetCursorPos(window, xpos, ypos);
        logicalMouseX = (float) xpos[0];
        logicalMouseY = (float) ypos[0];
        return logicalMouseY;
    }
}