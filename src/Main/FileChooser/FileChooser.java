package Main.FileChooser;

import GUI.Events.*;
import GUI.GUIComponent;
import GUI.Label;
import GUI.ScrollableFrame;
import GUI.TextField;
import Main.Mesh;
import Main.SnowMemo;
import Main.Utils;
import Main.Window;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class FileChooser extends Window {
    private final FileChooser self = this;
    private static FileChooser currentFileChooser;
    private GUIComponent chooseFileButton, chooseFileFrame, cancelFileButton;
    private TextField fileNameTextField;
    private File currentDirectory = new File("/Users/polarbear1612/Downloads/");
    private ScrollableFrame suggestionsFrame, folderFrame;
    private Mesh backgroundCube;
    private final List<Label> suggestionLabels = new ArrayList<>();
    private int selectedSuggestionIndex = -1;

    public FileChooser() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) throw new IllegalStateException("No primary monitor found");
        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
        if (vidMode == null) throw new IllegalStateException("Failed to get video mode");

        int w = vidMode.width() / 2;
        int h = vidMode.height() / 2;
        this.width = w;
        this.height = h;

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GL11.GL_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GL11.GL_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
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

        Window parentWindow = Window.getCurrentWindow();
        long parentHandle = (parentWindow != null) ? parentWindow.getWindowHandle() : MemoryUtil.NULL;

        window = GLFW.glfwCreateWindow(w, h, "Choose File", MemoryUtil.NULL, parentHandle);
        if (window == MemoryUtil.NULL) throw new RuntimeException("Failed to create FileChooser window");

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
        GLFW.glfwSetDropCallback(window, (windowHandle, count, names) -> {
            for (GLFWDropCallback callback : DropCallbacks) callback.invoke(windowHandle, count, names);
        });

        DropCallbacks.add(new GLFWDropCallback() {
            @Override
            public void invoke(long windowHandle, int count, long names) {
                if (count > 0) {
                    String path = GLFWDropCallback.getName(names, 0);
                    System.out.println("File dropped: " + path);
                }
            }
        });

        long oldContext = GLFW.glfwGetCurrentContext();
        GLFW.glfwMakeContextCurrent(window);

        IntBuffer fbWidth = MemoryUtil.memAllocInt(1);
        IntBuffer fbHeight = MemoryUtil.memAllocInt(1);
        GLFW.glfwGetFramebufferSize(window, fbWidth, fbHeight);
        this.width = fbWidth.get(0);
        this.height = fbHeight.get(0);
        MemoryUtil.memFree(fbWidth);
        MemoryUtil.memFree(fbHeight);

        GL.createCapabilities();
        glClearColor(1f, 1f, 1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        float aspectRatio = (float) width / height;
        projectionMatrix = new Matrix4f().perspective(FOV, aspectRatio, Z_NEAR, Z_FAR);

        GLFW.glfwSetCursorPosCallback(window, ((windowHandle, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
            CursorPosCallbacks.forEach(c -> c.invoke(windowHandle, xpos, ypos));
        }));

        GLFW.glfwSetMouseButtonCallback(window, (windowHandle, button, action, mods) -> {
            for (GLFWMouseButtonCallback callback : MouseButtonCallbacks) callback.invoke(windowHandle, button, action, mods);
        });

        GLFW.glfwSetKeyCallback(window, (windowHandle, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_TAB && !suggestionLabels.isEmpty()) {
                    selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestionLabels.size();
                    updateHighlight();
                } else if (key == GLFW_KEY_ENTER && selectedSuggestionIndex >= 0 && selectedSuggestionIndex < suggestionLabels.size()) {
                    fileNameTextField.setText(suggestionLabels.get(selectedSuggestionIndex).getText());
                    fileNameTextField.setCursorPosition(-1);
                    selectedSuggestionIndex = -1;
                    updateHighlight();
                }
            }
            for (GLFWKeyCallback callback : KeyCallbacks) callback.invoke(windowHandle, key, scancode, action, mods);
        });

        GLFW.glfwSetCharCallback(window, (windowHandle, codepoint) -> {
            for (GLFWCharCallback callback : CharCallbacks) callback.invoke(windowHandle, codepoint);
        });

        GLFW.glfwSetScrollCallback(window, (windowHandle, xoffset, yoffset) -> {
            for (GLFWScrollCallback callback : ScrollCallbacks) callback.invoke(windowHandle, xoffset, yoffset);
        });

        GLFW.glfwMakeContextCurrent(oldContext);
        backgroundCube = Utils.loadObj(SnowMemo.class.getResourceAsStream("Cube.obj"))
                .setScale(new Vector3f(1,2.5f,3f)).setPosition(new Vector3f(1f,0,0f));
        initGUI();
    }

    private void updateHighlight() {
        for (int i = 0; i < suggestionLabels.size(); i++) {
            Label label = suggestionLabels.get(i);
            if (i == selectedSuggestionIndex) {
                label.setBackgroundColor(SnowMemo.currentTheme.getSecondaryColors()[1]);
            } else {
                label.setBackgroundColor(null);
            }
        }
    }

    public void initGUI() {
        suggestionsFrame = new ScrollableFrame(this, 0.075f, 0.7f, 0.625f, 0.2f);
        suggestionsFrame.setSmoothScrollEnabled(false);
        suggestionsFrame.setCornerRadius(15);
        suggestionsFrame.setVisible(false);
        folderFrame = new ScrollableFrame(this,0.05f,0.05f,0.2f,0.825f);
        folderFrame.setSmoothScrollEnabled(false);
        folderFrame.setCornerRadius(30);
        folderFrame.setBackgroundColor(SnowMemo.currentTheme.getSecondaryColors()[1]);
        folderFrame.setBorderColor(SnowMemo.currentTheme.getSecondaryColors()[0]);
        folderFrame.setShowBorder(true);
        folderFrame.setBorderThickness(2f);
        folderFrame.setZ_Index(Short.MIN_VALUE);
        suggestionsFrame.setZ_Index(Integer.MAX_VALUE);
        fileNameTextField = new TextField(this, 0.075f, 0.9f, 0.625f, 0.05f);
        fileNameTextField.setPlaceholder("Enter filename...")
                .setBaseFont(SnowMemo.currentTheme.getFonts()[0])
                .setFocused(true)
                .setCornerRadius(30);
        fileNameTextField.setZ_Index(Short.MAX_VALUE);
        fileNameTextField.onChange(new TextChangeCallBack() {
            @Override
            public void onEvent(TextChangeEvent event) {
                String fileName = event.getNewText().trim();
                suggestionsFrame.clearChildren();
                suggestionLabels.clear();
                selectedSuggestionIndex = -1;

                File[] files = currentDirectory.listFiles();
                if (files != null && !fileName.isEmpty()) {
                    for (File file : files) {
                        if (file.getName().toLowerCase(Locale.ROOT).contains(fileName.toLowerCase(Locale.ROOT))) {
                            Label suggestionLabel = new Label(self, file.getName());
                            suggestionLabel
                                    .setCornerRadius(12)
                                    .setBaseFont(SnowMemo.currentTheme.getFonts()[0].deriveFont(Font.PLAIN, 16f))
                                    .setPadding(10)
                                    .setWidth(1.0f)
                                    .setHeight(0.15f);
                            suggestionLabel.setBackgroundColor(new Color(245, 245, 245, 200));
                            suggestionLabel.setTextColor(new Color(33, 37, 41));
                            suggestionLabel.addCallBack(new MouseEnterCallBack(){
                                @Override
                            public void onEvent(MouseEnterEvent e) {
                                    System.out.println("Color change");
                                suggestionLabel.setBackgroundColor(new Color(230, 240, 255));
                            }
                            });
                            suggestionLabel.addCallBack(new MouseExitCallBack(){
                                @Override
                                public void onEvent(MouseExitEvent e){
                                    System.out.println("Change color");
                                    suggestionLabel.setBackgroundColor(new Color(245, 245, 245, 200));
                                }
                            });
                            if (suggestionLabels.size() == selectedSuggestionIndex) {
                                suggestionLabel.setBackgroundColor(SnowMemo.currentTheme.getSecondaryColors()[1]);
                                suggestionLabel.setTextColor(Color.WHITE);
                            }
                            suggestionLabel.addCallBack((MouseClickCallBack) e -> {
                                fileNameTextField.setText(suggestionLabel.getText());
                                fileNameTextField.setCursorPosition(-1);
                            });
                            suggestionsFrame.addChild(suggestionLabel);
                            suggestionLabels.add(suggestionLabel);
                        }
                    }
                    suggestionsFrame.setVisible(true);
                }else{
                    suggestionsFrame.setVisible(false);
                }
            }
        });

        cancelFileButton = new GUIComponent(this, 0.7125f, 0.9f, 0.1f, 0.05f) {
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
                String text = "Cancel";
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int textHeight = fm.getAscent();
                int x = (width - textWidth) / 2;
                int y = (height - fm.getHeight()) / 2 + textHeight;
                g2d.drawString(text, x, y);
                g2d.dispose();
            }

            @Override
            public void init() { }
        };

        chooseFileFrame = new GUIComponent(this, 0.05f, 0.875f, 0.9f, 0.1f) {
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
            public void init() { }
        };

        cancelFileButton.setZ_Index(1);
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
            public void init() { }
        };

        chooseFileButton.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent e) {
                System.out.println("FileChooser button clicked!");
            }
        });
    }

    public void render() {
        long oldContext = GLFW.glfwGetCurrentContext();
        GLFW.glfwMakeContextCurrent(this.window);
        GLFW.glfwPollEvents();

        glClearColor(SnowMemo.currentTheme.getMainColor().getRed(),
                SnowMemo.currentTheme.getMainColor().getGreen(),
                SnowMemo.currentTheme.getMainColor().getBlue(),
                SnowMemo.currentTheme.getMainColor().getAlpha());

        float aspectRatio = (float) width / height;
        projectionMatrix = new Matrix4f().perspective(45f, aspectRatio, Z_NEAR, Z_FAR);

        glClear(GL11.GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        backgroundCube.render();
        GUIComponent.renderGUIs(this);
        GLFW.glfwSwapBuffers(window);
        GLFW.glfwMakeContextCurrent(oldContext);
    }

    public File chooseFile() {
        glfwShowWindow(window);
        final File[] returnFile = {new File("")};
        final boolean[] running = {true};
        MouseClickCallBack mouseClickCallBack = (MouseClickCallBack) e -> {
            returnFile[0] = new File(currentDirectory.getPath()+"/"+fileNameTextField.getText());
            if (returnFile[0].exists()&&returnFile[0].isFile()) {
                running[0] = false;
            }
        };
        chooseFileButton.addCallBack(mouseClickCallBack);
        while (running[0]){
            render();
            if (GLFW.glfwWindowShouldClose(window)){
                break;
            }
        }
        GLFW.glfwHideWindow(window);
        chooseFileButton.removeCallBack(mouseClickCallBack);
        return returnFile[0];
    }

    public static FileChooser getCurrentFileChooser() {
        return currentFileChooser;
    }

    @Override
    public void close() {
        GUIComponent.cleanupWindow(this);
        Callbacks.glfwFreeCallbacks(window);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwSetWindowShouldClose(window, true);
    }
}
