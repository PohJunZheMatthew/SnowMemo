package Main.FileChooser;

import GUI.*;
import GUI.BillBoardGUI.BillboardGUI;
import GUI.Events.*;
import GUI.Label;
import GUI.TextField;
import Main.*;
import Main.Window;
import aurelienribon.tweenengine.*;
import aurelienribon.tweenengine.equations.Linear;
import aurelienribon.tweenengine.equations.Quad;
import aurelienribon.tweenengine.equations.Sine;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.*;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class FileChooser extends Window {
    private int fps = 0;
    private final FileChooser self = this;
    private static FileChooser currentFileChooser;
    @SuppressWarnings("FieldCanBeLocal")
    private GUIComponent chooseFileButton, chooseFileFrame, cancelFileButton;
    private TextField fileNameTextField;
    private File currentDirectory = new File(System.getProperty("user.home")),previousDirectory = new File("");
    @SuppressWarnings("FieldCanBeLocal")
    private ScrollableFrame suggestionsFrame, folderFrame;
    private final Mesh backgroundCube;
    private final List<Label> suggestionLabels = new ArrayList<>();
    private int selectedSuggestionIndex = -1;
    private Camera camera;
    private final ArrayList<FileChooserIcon> fileChooserIcons = new ArrayList<>();
    float mouseWheelVelocity = 0;
    float scroll = 1;
    float perScroll = 1.0f;
    static int MAX_SCROLL = 10;
    static final int MIN_SCROLL = 1;
    private final float[] originalBackgroundCubeVerts;
    private int selection = 0;
    private Mesh selectionMesh;
    private TweenManager tweenManager;
    long lastUpdate = System.nanoTime();
    private FileFilter fileFilter;
    private FileHistory fileHistory = new FileHistory();
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

        GLFW.glfwSetFramebufferSizeCallback(window, ((_, width, height) -> {
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
                    File f = new File(path);
                    if (f.isFile()) {
                        fileNameTextField.setText("");
                        fileNameTextField.setText(f.getName());
                        currentDirectory = new File(f.getParent());
                    }else{
                        currentDirectory = f;
                    }
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
            mouseWheelVelocity = (float) -yoffset;
        });

        GLFW.glfwMakeContextCurrent(oldContext);
        backgroundCube = Utils.loadObj(SnowMemo.class.getResourceAsStream("Cube.obj"), FileChooser.class)
                .setScale(new Vector3f(1,3f,3f)).setPosition(new Vector3f(1.25f,-2f,0f));
        backgroundCube.outline = false;
        originalBackgroundCubeVerts = backgroundCube.getVertices();
        CursorPosCallbacks.add(new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                Vector3f position = getWorldCoordinatesFromScreen(xpos,ypos);
                for (int i = 0;i<fileChooserIcons.size();i++){
                    FileChooserIcon fileChooserIcon = fileChooserIcons.get(i);
                    if (fileChooserIcon.collides(position)){
                        selection = i;
                    }
                }
            }
        });
        MouseButtonCallbacks.add(new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (button==GLFW_MOUSE_BUTTON_1){
                    if (action==GLFW_PRESS){
                        if (selection >= 0 && selection < fileChooserIcons.size()) {
                            FileChooserIcon fi = fileChooserIcons.get(selection);
                            File file = fi.file;
                            if (file.isFile()){
                                fileNameTextField.setText(file.getName());
                            } else if (file.isDirectory()) {
                                currentDirectory = new File(file.getPath());
                            }
                        }
                    }
                }
            }
        });
        tweenManager = new TweenManager();
        Tween.registerAccessor(Mesh.class, new MeshAccessor());
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
        camera = new Camera();
        camera.cameraMovement = Camera.CameraMovement.ScrollUpAndDown;
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
        fileNameTextField.onChange(event -> {
            String fileName = event.getNewText().trim();
            suggestionsFrame.clearChildren();
            suggestionLabels.clear();
            selectedSuggestionIndex = -1;

            File[] files = Optional.ofNullable(currentDirectory.listFiles())
                    .map(fList -> Arrays.stream(fList)
                            .filter(f -> !f.isHidden())
                            .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                            .toArray(File[]::new))
                    .orElse(new File[0]);

            // Reset selection
            selection = -1;

            if (!fileName.isEmpty()) {
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    if (file.getName().toLowerCase(Locale.ROOT).contains(fileName.toLowerCase(Locale.ROOT))&&file.isFile()) {
                        Label suggestionLabel = new Label(self, file.getName());
                        suggestionLabel
                                .setCornerRadius(12)
                                .setBaseFont(SnowMemo.currentTheme.getFonts()[0].deriveFont(Font.PLAIN, 16f))
                                .setPadding(10)
                                .setWidth(1.0f)
                                .setHeight(0.15f);
                        suggestionLabel.setBackgroundColor(new Color(245, 245, 245, 200));
                        suggestionLabel.setTextColor(new Color(33, 37, 41));
                        suggestionLabel.addCallBack((MouseEnterCallBack) _ -> suggestionLabel.setBackgroundColor(new Color(230, 240, 255)));
                        suggestionLabel.addCallBack((MouseExitCallBack) _ -> suggestionLabel.setBackgroundColor(new Color(245, 245, 245, 200)));
                        if (suggestionLabels.size() == selectedSuggestionIndex) {
                            suggestionLabel.setBackgroundColor(SnowMemo.currentTheme.getSecondaryColors()[1]);
                            suggestionLabel.setTextColor(Color.WHITE);
                        }
                        suggestionLabel.addCallBack((MouseClickCallBack) _ -> {
                            fileNameTextField.setText(suggestionLabel.getText());
                            fileNameTextField.setCursorPosition(-1);
                        });
                        suggestionsFrame.addChild(suggestionLabel);
                        suggestionLabels.add(suggestionLabel);
                    }
                    if (file.getName().toLowerCase(Locale.ROOT).equals(fileNameTextField.getText().toLowerCase(Locale.ROOT))) {
                        fileNameTextField.setText(file.getName());
                        selection = i;
                    }
                }
                suggestionsFrame.setVisible(true);
            }else{
                suggestionsFrame.setVisible(false);
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
        cancelFileButton.addCallBack((MouseClickCallBack) _ -> {

        });
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
        selectionMesh = Utils.loadObj(getClass().getResourceAsStream("FileSelector.obj"));
        selectionMesh.init();
        selectionMesh.setPosition(new Vector3f(0,0,1.1f));
    }
    public void resetCamera() {
        if (camera != null) {
            camera.setPosition(new Vector3f(0f, 0f, 5f));
            camera.setRotation(new Vector3f(0f, 0f, 0f));
            scroll = MIN_SCROLL;
            perScroll = 1.0f;
        }
    }
    Vector3f lastSelectorPos = new Vector3f();
    public void updateSelectorPosition(Vector3f newPos) {
        if (!newPos.equals(lastSelectorPos)) {
            setSelectorPosition(newPos);
            lastSelectorPos.set(newPos);
        }
    }
    public void render() {
        long currentUpdate = System.nanoTime();
        float deltaTime = (currentUpdate - lastUpdate) / 1_000_000_000.0f; // Convert nanoseconds to seconds
        lastUpdate = currentUpdate;
        tweenManager.update(deltaTime);
        long oldContext = GLFW.glfwGetCurrentContext();
        if (GLFW.glfwWindowShouldClose(oldContext)) {
            this.close();
            return;
        }
        GLFW.glfwMakeContextCurrent(this.window);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        GLFW.glfwPollEvents();

        Color bg = SnowMemo.currentTheme.getMainColor();
        glClearColor(bg.getRed() / 255f, bg.getGreen() / 255f,
                bg.getBlue() / 255f, bg.getAlpha() / 255f);

        float aspectRatio = (float) width / height;
        projectionMatrix = new Matrix4f().perspective(45f, aspectRatio, Z_NEAR, Z_FAR);

        glClear(GL11.GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // 1. Render background first
        backgroundCube.render(camera);

        Frustum frustum = new Frustum();
        Matrix4f projView = new Matrix4f(projectionMatrix).mul(camera.getViewMatrix());
        frustum.update(projView);

        for (FileChooserIcon f : fileChooserIcons) {
            if (f.mesh != null) {
                f.mesh.updateCulling(frustum);
                f.billboardGUI.updateCulling(frustum);
            }
        }
        if (!fileChooserIcons.isEmpty() && selection >= 0 && selection < fileChooserIcons.size()) {
            Vector3f iconPos = fileChooserIcons.get(selection).mesh.getPosition();
            Vector3f targetPos = new Vector3f(iconPos.x - 0.025f, iconPos.y - 0.15f, iconPos.z + 0.1f);
            updateSelectorPosition(targetPos);
            selectionMesh.setScale(new Vector3f(0.6f, 0.6f, 0.6f));
            selectionMesh.setVisible(true);
        } else {
            selectionMesh.setVisible(false);
        }
        selectionMesh.setVisible(!fileChooserIcons.isEmpty());
        // 4. Render the file icons
        for (FileChooserIcon fileChooserIcon : fileChooserIcons) {
            if (fileChooserIcon.mesh.isRenderVisible()) { // Check visibility first
                fileChooserIcon.render();
            }
        }

        selectionMesh.updateCulling(frustum);
        selectionMesh.render(camera);
        // 5. Render GUI on top
        GUIComponent.renderGUIs(this);

        GLFW.glfwSwapBuffers(window);
        GLFW.glfwMakeContextCurrent(oldContext);
        fps++;
    }
    public void update() {
        if (mouseWheelVelocity != 0) {
            float zoomSpeed = 0.5f;
            float desiredZoom = scroll + mouseWheelVelocity * zoomSpeed;
            desiredZoom = Math.max(MIN_SCROLL, Math.min(MAX_SCROLL, desiredZoom));
            float delta = desiredZoom - scroll;
            if (delta != 0) {
                if (camera.cameraMovement == Camera.CameraMovement.ZoomInAndOut) {
                    camera.move(new Vector3f(0, 0, -delta)); // negative to scroll in
                } else if (camera.cameraMovement == Camera.CameraMovement.ScrollUpAndDown) {
                    camera.move(new Vector3f(0, -delta, 0));
                }
                scroll = desiredZoom;
                perScroll = (scroll - MIN_SCROLL) / (MAX_SCROLL - MIN_SCROLL);
            }
            mouseWheelVelocity = 0;
        }
        if (!previousDirectory.getAbsolutePath().equals(currentDirectory.getAbsolutePath())) {
            resetCamera();
            fileHistory.add(fileHistory.size(), currentDirectory);
            scroll = MIN_SCROLL;

            selection = 0;
            for (FileChooserIcon icon : fileChooserIcons) {
                if (icon.mesh != null) {
                    icon.mesh.cleanUp();
                    icon.billboardGUI.cleanUp();
                }
            }
            fileChooserIcons.clear();

            File[] allFiles = Objects.requireNonNull(currentDirectory.listFiles());
            List<File> files = Arrays.stream(allFiles)
                    .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            int columns = 4;
            float spacing = 1.25f;
            int rows = 0;
            int visibleIndex = 0;
            if (currentDirectory.getParent()!=null) {
                ReturnFolderIcon returnFolderIcon = new ReturnFolderIcon(new File(currentDirectory.getParent()));
                int col = visibleIndex % columns;
                int row = visibleIndex / columns;
                float x = (col - (columns / 2f)) * spacing + 1.45f;
                float y = -(row * spacing) + 1.5f;
                float z = 1f;
                returnFolderIcon.mesh
                        .setPosition(new Vector3f(x, y, z))
                        .setScale(new Vector3f(0.375f, 0.375f, 0.375f))
                        .setRotation(new Vector3f(0, (float) Math.PI * 1.5f, 0));
                fileChooserIcons.add(returnFolderIcon);
                visibleIndex++;
            }
            for (File file : files) {
                if (!file.isHidden()) {
                    FileChooserIcon fileChooserIcon;
                    if (file.isFile()) {
                        fileChooserIcon = new FileIcon(file);
                    } else {
                        fileChooserIcon = new FolderIcon(file);
                    }

                    int col = visibleIndex % columns;
                    int row = visibleIndex / columns;
                    float x = (col - (columns / 2f)) * spacing + 1.45f;
                    float y = -(row * spacing) + 1.5f;
                    float z = 1f;
                    fileChooserIcon.mesh
                            .setPosition(new Vector3f(x, y, z))
                            .setScale(new Vector3f(0.375f, 0.375f, 0.375f))
                            .setRotation(new Vector3f(0, (float) Math.PI * 1.5f, 0));
                    fileChooserIcons.add(fileChooserIcon);
                    visibleIndex++;
                }
            }

            rows = (visibleIndex + columns - 1) / columns;
            int actualRows = rows;
            MAX_SCROLL = Math.max(MIN_SCROLL, (int) (Math.max(0, actualRows - 3) * 1.5f) + 1);
            float lowerOffset = spacing * Math.max(0, actualRows - 4) / 3;
            float[] verts = originalBackgroundCubeVerts.clone();
            for (int i = 0; i < verts.length; i += 6) {
                float y = verts[i + 1];
                if (y <= 0.0f) {
                    verts[i + 1] = y - lowerOffset;
                }
            }
            backgroundCube.updateVertices(verts);
            backgroundCube.setPosition(new Vector3f(1f, -1.25f, 0f));
            previousDirectory = currentDirectory;
        }
    }
    MouseClickCallBack cancelMouseClickCallback;
    public File chooseFile() {
        if (cancelMouseClickCallback != null) {
            cancelFileButton.removeCallBack(cancelMouseClickCallback);
        }
        fileNameTextField.setText("");

        final File[] returnFile = {null};
        final boolean[] running = {true};

        MouseClickCallBack mouseClickCallBack = _ -> {
            File rf = new File(currentDirectory.getPath() + "/" + fileNameTextField.getText());
            boolean acceptingFile = true;
            if (fileFilter!=null&&!fileFilter.accept(rf)) acceptingFile = false;
            returnFile[0] = rf;
            if (returnFile[0].exists() && returnFile[0].isFile()&&acceptingFile) {
                running[0] = false;
            }
        };

        cancelMouseClickCallback = e -> {
            running[0] = false;
            returnFile[0] = null;
        };

        cancelFileButton.addCallBack(cancelMouseClickCallback);
        chooseFileButton.addCallBack(mouseClickCallBack);

        // FPS counter thread
        new Thread(() -> {
            while (running[0]) {
                System.out.println("fps = " + fps);
                fps = 0;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();

        render();
        glfwShowWindow(window);

        // Optimized render loop
        while (running[0]) {
            update();
            render();

            // Optional: Cap at 144 FPS to prevent CPU spinning
            // Remove this block for unlimited FPS
            try {
                Thread.sleep(1);  // Small sleep to prevent 100% CPU usage
            } catch (InterruptedException e) {
                break;
            }

            if (GLFW.glfwWindowShouldClose(window)) {
                running[0] = false;
                break;
            }
        }

        running[0] = false;

        // Reset window size
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor != 0L) {
            GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
            if (vidMode != null) {
                int w = vidMode.width() / 2;
                int h = vidMode.height() / 2;
                GLFW.glfwSetWindowSize(window, w, h);
            }
        }

        GLFW.glfwHideWindow(window);
        GLFW.glfwSetWindowShouldClose(window, false);
        chooseFileButton.removeCallBack(mouseClickCallBack);
        return returnFile[0];
    }
    public Vector3f getWorldCoordinatesFromScreen(double xpos, double ypos) {
        // --- 1) Get window size (logical window coords) and framebuffer size (physical pixels)
        int[] winW = new int[1], winH = new int[1];
        int[] fbW  = new int[1], fbH  = new int[1];
        glfwGetWindowSize(window, winW, winH);
        glfwGetFramebufferSize(window, fbW, fbH);

        // Guard against divide by zero
        if (winW[0] == 0 || winH[0] == 0) return new Vector3f(0, 0, 0);

        // Convert cursor (window coords) -> framebuffer coords (handles HiDPI)
        float scaleX = (float) fbW[0] / (float) winW[0];
        float scaleY = (float) fbH[0] / (float) winH[0];
        float fbX = (float) xpos * scaleX;
        float fbY = (float) ypos * scaleY;

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
    public Mesh setSelectorPosition(Vector3f endPosition){
        Tween.to(selectionMesh, MeshAccessor.POSITION_XYZ, 1f) // Tween myObject's X position over 1 second
                .target(endPosition.x,endPosition.y,endPosition.z) // To a target value of 100
                .ease(Quad.INOUT) // With a linear easing function
                .setCallbackTriggers(TweenCallback.COMPLETE)
                .setCallback((type, source) -> System.out.println("Tween complete"))
                .start(tweenManager);
        return selectionMesh;
    }
    private static abstract class FileChooserIcon implements Renderable {
        protected Mesh mesh;
        protected File file;
        protected BillboardGUI billboardGUI;
        protected BufferedImage cachedImage;
        protected final GUIComponent guiComponent;

        public FileChooserIcon(File file, String meshPath) {
            this.mesh = Utils.loadObj(this.getClass().getResourceAsStream(meshPath), FileChooser.class);
            this.file = file;
//            this.mesh.setMaterial(new Mesh.Material(
//                    new Vector4f(245, 245, 250, 255),
//                    new Vector4f(220, 220, 225, 255),
//                    new Vector4f(255, 255, 255, 255),
//                    8f
//            ));

            this.guiComponent = new GUIComponent(currentFileChooser, 0.1f, 0.1f) {
                @Override
                protected void paintComponent(Graphics g) {
                    if (cachedImage != null && widthPx == cachedImage.getWidth() && heightPx == cachedImage.getHeight()) {
                        g.drawImage(cachedImage, 0, 0, null);
                    } else {
                        createCachedImage(widthPx, heightPx);
                        if (cachedImage != null) {
                            g.drawImage(cachedImage, 0, 0, null);
                        }
                    }
                }

                @Override
                public void init() { }
            };

            billboardGUI = new BillboardGUI(currentFileChooser, guiComponent);
            billboardGUI.setMaterial(new Mesh.Material(
                    new Vector4f(255, 255, 255, 255),
                    new Vector4f(0, 0, 0, 0),
                    new Vector4f(0, 0, 0, 0),
                    32f
            ));
        }

        protected void createCachedImage(int width, int height) {
            if (width <= 0 || height <= 0) {
                System.err.println("Invalid dimensions for cachedImage: " + width + "x" + height);
                return;
            }

            cachedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics g = cachedImage.createGraphics();
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, cachedImage.getWidth(), cachedImage.getHeight());
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Font basefont = SnowMemo.currentTheme.getFonts()[0].deriveFont(128f);
            String text = file.getName();

            float fontSize = basefont.getSize2D();
            Font scaledFont = basefont.deriveFont(fontSize);
            FontMetrics fm = g2d.getFontMetrics(scaledFont);
            float padding = 15;
            while ((fm.stringWidth(text) > cachedImage.getWidth() - padding || fm.getHeight() > cachedImage.getHeight() - padding)
                    && fontSize > 1) {
                fontSize -= 0.5f;
                scaledFont = basefont.deriveFont(fontSize);
                fm = g2d.getFontMetrics(scaledFont);
            }

            g2d.setFont(scaledFont);

            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();

            int x = (cachedImage.getWidth() - textWidth) / 2;

            int y = (cachedImage.getHeight() - textHeight) / 2 + fm.getAscent();

            g2d.setColor(Color.BLACK);
            g2d.drawString(text, x, y);

            g2d.dispose();
            g.dispose();
        }

        @Override
        public void render() {
            // Distance-based culling
            if (Math.abs(mesh.getPosition().y - currentFileChooser.camera.getPosition().y) > 2.9f) {
                return;
            }

            // Render the 3D mesh
//            if (mesh.isRenderVisible()) {
//            }
            mesh.render(currentFileChooser.camera);

            // Setup billboard text label
            float texWidth = guiComponent.getWidthPx();
            float texHeight = guiComponent.getHeightPx();

            if (texWidth <= 0 || texHeight <= 0) {
                return; // Skip if dimensions invalid
            }

            float aspect = texWidth / texHeight;

            billboardGUI.setPosition(new Vector3f(
                    mesh.getPosition().x - 0.025f,
                    mesh.getPosition().y - 0.6f,
                    mesh.getPosition().z
            ));
            billboardGUI.setRotation(new Vector3f(0, (float) Math.PI, (float) Math.PI));

            float baseScale = 0.15f;
            billboardGUI.setScale(new Vector3f(baseScale * aspect, baseScale, baseScale - 0.125f));

            // Render billboard
//            if (billboardGUI.isRenderVisible()) {
//                billboardGUI.render(currentFileChooser.camera);
//            }
            billboardGUI.render(currentFileChooser.camera);
        }

        @Override
        public void cleanUp() {
            if (mesh != null) mesh.cleanUp();
            if (billboardGUI != null) billboardGUI.cleanUp();
        }

        @Override
        public void init() { }
        public boolean collides(Vector3f cursorPos) {
            if (mesh == null || billboardGUI == null) return false;

            Vector3f meshPos = mesh.getPosition();
            float meshHalfSize = 0.5f * 0.375f; // mesh scale

            // Mesh bounds
            float meshMinX = meshPos.x - meshHalfSize;
            float meshMaxX = meshPos.x + meshHalfSize;
            float meshMinY = meshPos.y - meshHalfSize;
            float meshMaxY = meshPos.y + meshHalfSize;

            // Billboard bounds
            float texWidth = guiComponent.getWidthPx();
            float texHeight = guiComponent.getHeightPx();
            if (texWidth <= 0 || texHeight <= 0) texWidth = texHeight = 1; // fallback

            float aspect = texWidth / texHeight;
            float baseScale = 0.15f;
            float halfWidth = (baseScale * aspect) / 2;
            float halfHeight = baseScale / 2;

            Vector3f billboardPos = new Vector3f(meshPos.x, meshPos.y - 0.6f, meshPos.z);
            float bbMinX = billboardPos.x - halfWidth;
            float bbMaxX = billboardPos.x + halfWidth;
            float bbMinY = billboardPos.y - halfHeight;
            float bbMaxY = billboardPos.y + halfHeight;

            // Combine mesh + billboard bounds
            float minX = Math.min(meshMinX, bbMinX);
            float maxX = Math.max(meshMaxX, bbMaxX);
            float minY = Math.min(meshMinY, bbMinY);
            float maxY = Math.max(meshMaxY, bbMaxY);

            // --- Add padding for sensitivity ---
            float padding = 0.2f; // tweak this: increase to make selection more sensitive
            minX -= padding;
            maxX += padding;
            minY -= padding;
            maxY += padding;

            // Check collision in 2D
            return cursorPos.x >= minX && cursorPos.x <= maxX
                    && cursorPos.y >= minY && cursorPos.y <= maxY;
        }
    }
    private static class FolderIcon extends FileChooserIcon {
        public FolderIcon(File file) {
            super(file, "Folder.obj");
        }
    }
    private static class ReturnFolderIcon extends FolderIcon{
        public ReturnFolderIcon(File file) {
            super(file);
        }
        @Override
        protected void createCachedImage(int width, int height) {
            if (width <= 0 || height <= 0) {
                System.err.println("Invalid dimensions for cachedImage: " + width + "x" + height);
                return;
            }

            cachedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics g = cachedImage.createGraphics();
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, cachedImage.getWidth(), cachedImage.getHeight());
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Font basefont = SnowMemo.currentTheme.getFonts()[0].deriveFont(64f);
            String text = "↩Parent dir";

            float fontSize = basefont.getSize2D();
            Font scaledFont = basefont.deriveFont(fontSize);
            FontMetrics fm = g2d.getFontMetrics(scaledFont);
            float padding = 15;
            while ((fm.stringWidth(text) > cachedImage.getWidth() - padding || fm.getHeight() > cachedImage.getHeight() - padding)
                    && fontSize > 1) {
                fontSize -= 0.5f;
                scaledFont = basefont.deriveFont(fontSize);
                fm = g2d.getFontMetrics(scaledFont);
            }

            g2d.setFont(scaledFont);

            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();

            int x = (cachedImage.getWidth() - textWidth) / 2;

            int y = (cachedImage.getHeight() - textHeight) / 2 + fm.getAscent();

            g2d.setColor(Color.BLACK);
            g2d.drawString(text, x, y);

            g2d.dispose();
            g.dispose();
        }
    }
    private static class FileIcon extends FileChooserIcon {
        public FileIcon(File file) {
            super(file, "File.obj");
        }
    }
    public FileFilter getFileFilter() {
        return fileFilter;
    }
    public FileChooser setFileFilter(FileFilter fileFilter) {
        this.fileFilter = fileFilter;
        return this;
    }
}
