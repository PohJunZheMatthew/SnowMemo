package Main.FileChooser;

import GUI.Events.MouseClickCallBack;
import GUI.Events.MouseClickEvent;
import GUI.GUIComponent;
import GUI.TextField;
import Main.SnowMemo;
import Main.Window;
import org.joml.Matrix4f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glClearColor;

public class FileChooser extends Window {
    private static FileChooser currentFileChooser;
    private GUIComponent chooseFileButton,chooseFileFrame;
    private TextField fileNameTextField;
    public FileChooser(){
        // Don't call glfwInit() again - it's already initialized by the main Window
        long monitor = GLFW.glfwGetPrimaryMonitor();
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
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
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

        // Create window as child of main window (shared context)
        Window parentWindow = Window.getCurrentWindow();
        long parentHandle = (parentWindow != null) ? parentWindow.getWindowHandle() : MemoryUtil.NULL;

        window = GLFW.glfwCreateWindow(w, h, "Choose File", MemoryUtil.NULL, parentHandle);
        if (window == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create FileChooser window");
        }

        GLFW.glfwSetFramebufferSizeCallback(window, ((windowHandle, width, height) -> {
            this.width = width;
            this.height = height;
        }));

        if (maximised) {
            GLFW.glfwMaximizeWindow(window);
        } else {
            GLFW.glfwSetWindowPos(window, (vidMode.width() - w) / 2, (vidMode.height() - h) / 2);
            GLFW.glfwSetWindowAspectRatio(window, vidMode.width(), vidMode.height());
        }

        currentFileChooser = this;
        GLFW.glfwSetDropCallback(window,(long windowHandle, int count, long names)->{
            for (GLFWDropCallback callback: DropCallbacks) {
                callback.invoke(windowHandle,count,names);
            }
        });
        // Set up drop callback for file dropping
        DropCallbacks.add(new GLFWDropCallback() {
            @Override
            public void invoke(long windowHandle, int count, long names) {
                if (count > 0) {
                    String path = GLFWDropCallback.getName(names, 0);
                    System.out.println("File dropped: " + path);
                }
            }
        });

        // Store old context and make this window current
        long oldContext = GLFW.glfwGetCurrentContext();
        GLFW.glfwMakeContextCurrent(window);

        // Get framebuffer size for proper initialization
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
        GL11.glDisable(GL11.GL_CULL_FACE);

        float aspectRatio = (float) width / height;
        projectionMatrix = new Matrix4f().perspective(FOV, aspectRatio, Z_NEAR, Z_FAR);
        GLFW.glfwSetCursorPosCallback(window, ((windowHandle, xpos, ypos) -> {
            CursorPosCallbacks.forEach(c -> {
                try {
                    c.invoke(windowHandle, xpos, ypos);
                } catch (Exception e) {
                    System.err.println("Error in FileChooser cursor position callback: " + e.getMessage());
                }
            });
        }));

        GLFW.glfwSetMouseButtonCallback(window, (long windowHandle, int button, int action, int mods) -> {
            for (GLFWMouseButtonCallback callback : MouseButtonCallbacks) {
                try {
                    callback.invoke(windowHandle, button, action, mods);
                } catch (Exception e) {
                    System.err.println("Error in FileChooser mouse button callback: " + e.getMessage());
                }
            }
        });

        GLFW.glfwSetKeyCallback(window, (long windowHandle, int key, int scancode, int action, int mods) -> {
            for (GLFWKeyCallback callback : KeyCallbacks) {
                try {
                    callback.invoke(windowHandle, key, scancode, action, mods);
                } catch (Exception e) {
                    System.err.println("Error in key callback: " + e.getMessage());
                }
            }
        });

        GLFW.glfwSetCharCallback(window, (long windowHandle, int codepoint) -> {
            for (GLFWCharCallback callback : CharCallbacks) {
                try {
                    callback.invoke(windowHandle, codepoint);
                } catch (Exception e) {
                    System.err.println("Error in char callback: " + e.getMessage());
                }
            }
        });
        // Restore old context
        GLFW.glfwMakeContextCurrent(oldContext);

        System.out.println("FileChooser window created with handle: " + window);

        initGUI();
    }

    public void initGUI(){
        fileNameTextField = new TextField(this, 0.1f, 0.1f, 0.6f, 0.08f);
        fileNameTextField.setPlaceholder("Enter filename...").setBaseFont(SnowMemo.currentTheme.getFonts()[0]).setFocused(true);
        chooseFileFrame = new GUIComponent(this,0.05f,0.875f,0.9f,0.1f) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidthPx();
                int height = getHeightPx();

                g2d.setColor(SnowMemo.currentTheme.getSecondaryColors()[1]);
                int arc = (int) Math.min(width * 0.25, height * 0.25);
                g2d.fillRoundRect(0, 0, width, height, arc, arc);

                g2d.setStroke(new BasicStroke(2.5f));
                g2d.setColor(SnowMemo.currentTheme.getFontColors()[0]);
                g2d.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
                g2d.dispose();
            }

            @Override
            public void init() {

            }
        };
        chooseFileFrame.setZ_Index(-1);
        chooseFileButton = new GUIComponent(this, 0.825f, 0.9f, 0.1f, 0.05f) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidthPx();
                int height = getHeightPx();

                g2d.setColor(SnowMemo.currentTheme.getMainColor());
                int arc = (int) Math.min(width * 0.5, height * 0.5);
                g2d.fillRoundRect(0, 0, width, height, arc, arc);

                g2d.setStroke(new BasicStroke(2.5f));
                g2d.setColor(SnowMemo.currentTheme.getFontColors()[0]);
                g2d.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);

                float fontSize = (float) (height * 0.5);
                Font font = SnowMemo.currentTheme.getFonts()[0].deriveFont(Font.BOLD, fontSize);
                g2d.setFont(font);

                String text = "Choose file";
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int textHeight = fm.getAscent();

                int x = (width - textWidth) / 2;
                int y = (height - fm.getHeight()) / 2 + textHeight;

                g2d.drawString(text, x, y);
                g2d.dispose();
            }

            @Override
            public void init() {
                // Implementation not needed for this example
            }
        };

        chooseFileButton.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent e) {
                System.out.println("FileChooser button clicked!");
            }
        });

        System.out.println("FileChooser GUI initialized");
    }

    public void render(){
        long oldContext = GLFW.glfwGetCurrentContext();
        GLFW.glfwMakeContextCurrent(this.window);
        GLFW.glfwPollEvents();

        glClearColor(SnowMemo.currentTheme.getMainColor().getRed(),
                SnowMemo.currentTheme.getMainColor().getGreen(),
                SnowMemo.currentTheme.getMainColor().getBlue(),
                SnowMemo.currentTheme.getMainColor().getAlpha());

        float aspectRatio = (float) width / height;
        projectionMatrix = new Matrix4f().perspective(FOV, aspectRatio, Z_NEAR, Z_FAR);

        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GUIComponent.renderGUIs(this);
        GLFW.glfwSwapBuffers(window);

        GLFW.glfwMakeContextCurrent(oldContext);
    }

    public File chooseFile(){
        GLFW.glfwShowWindow(window);
        return null;
    }

    public static FileChooser getCurrentFileChooser(){
        return currentFileChooser;
    }

    @Override
    public void close() {
        // Clean up GUI components for this window
        GUIComponent.cleanupWindow(this);

        // Don't call glfwTerminate() here since we're sharing context
        Callbacks.glfwFreeCallbacks(window);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwSetWindowShouldClose(window, true);
    }
}