package Main.FileChooser;

import GUI.*;
import GUI.BillBoardGUI.BillboardGUI;
import GUI.Events.*;
import GUI.Frame;
import GUI.Label;
import GUI.TextField;
import Light.DirectionalLight;
import Main.*;
import Main.Shadow.ShadowMap;
import Main.Window;
import aurelienribon.tweenengine.*;
import aurelienribon.tweenengine.equations.Quad;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.*;
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
    private GUIComponent chooseFileButton, chooseFileFrame, cancelFileButton,historyFrame , homeHistoryButton, backHistoryButton, forwardHistoryButton, refreshHistoryButton;
    private TextField fileNameTextField;
    private File currentDirectory = new File(System.getProperty("user.home")),previousDirectory = new File("");
    @SuppressWarnings("FieldCanBeLocal")
    private ScrollableFrame suggestionsFrame, homeFolder, recentFolder;
    private Frame folderFrame;
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
    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_THRESHOLD = 300_000_000L; // 300ms in nanoseconds
    private ShadowMap shadowMap;
    private DirectionalLight sun;
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

        MouseButtonCallbacks.add(new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS) {
                    Vector3f clickPos = getCursorWorldPosAtZ(0f);
                    int clickedIndex = -1;

                    // Find which icon was clicked
                    for (int i = 0; i < fileChooserIcons.size(); i++) {
                        FileChooserIcon icon = fileChooserIcons.get(i);
                        if (icon.collides(clickPos)) {
                            clickedIndex = i;
                            break;
                        }
                    }
                    Point2D mousePos = new Point2D.Double(mouseX,mouseY);
                    for (Object object: Objects.requireNonNull(GUIComponent.getGUIComponents(self))){
                        if (object instanceof GUIComponent) {
                            GUIComponent guiComponent = (GUIComponent) object;
                            guiComponent.updateHitBox();
                            if (guiComponent.getHitBox().contains(mousePos)&&guiComponent.isVisible()) {
                                return;
                            }
                        }
                    }
                    // Handle click
                    if (clickedIndex >= 0) {
                        long currentTime = System.nanoTime();
                        boolean isDoubleClick = (clickedIndex == selection) &&
                                (currentTime - lastClickTime < DOUBLE_CLICK_THRESHOLD);

                        if (isDoubleClick) {
                            // DOUBLE-CLICK: Confirm the action
                            FileChooserIcon icon = fileChooserIcons.get(clickedIndex);
                            File file = icon.file;

                            if (file.isDirectory()) {
                                // Open the directory
                                currentDirectory = file;
                                selection = -1; // Reset selection after navigating
                            } else if (file.isFile()) {
                                // Put filename in text field (confirm selection)
                                fileNameTextField.setText(file.getName());
                                // Keep selection on this file
                                selection = clickedIndex;
                            }
                        } else {
                            // SINGLE-CLICK: Just move the selection highlight
                            selection = clickedIndex;
                            // DON'T update text field here - only visual selection
                        }

                        lastClickTime = currentTime;
                    } else {
                        // Clicked empty space - deselect
                        selection = -1;
                        lastClickTime = 0;
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
        glfwSwapInterval(0);
        glClearColor(1f, 1f, 1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        float aspectRatio = (float) width / height;
        projectionMatrix = new Matrix4f().perspective(FOV, aspectRatio, Z_NEAR, Z_FAR);

        GLFW.glfwSetCursorPosCallback(window, ((windowHandle, xpos, ypos) -> {

            float[] sx = new float[1];
            float[] sy = new float[1];
            glfwGetWindowContentScale(windowHandle, sx, sy);

            mouseX = xpos * sx[0];
            mouseY = ypos * sy[0];
            CursorPosCallbacks.forEach(c -> c.invoke(windowHandle, xpos, ypos));
        }));

        GLFW.glfwSetMouseButtonCallback(window, (windowHandle, button, action, mods) -> {
            for (GLFWMouseButtonCallback callback : MouseButtonCallbacks) callback.invoke(windowHandle, button, action, mods);
        });

        GLFW.glfwSetKeyCallback(window, (windowHandle, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                // Handle autocomplete suggestions
                if (key == GLFW_KEY_TAB && !suggestionLabels.isEmpty()) {
                    selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestionLabels.size();
                    updateHighlight();
                } else if (key == GLFW_KEY_ENTER && selectedSuggestionIndex >= 0 && selectedSuggestionIndex < suggestionLabels.size()) {
                    fileNameTextField.setText(suggestionLabels.get(selectedSuggestionIndex).getText());
                    fileNameTextField.setCursorPosition(-1);
                    selectedSuggestionIndex = -1;
                    updateHighlight();
                }
                // Arrow key navigation through files
                else if (key == GLFW_KEY_DOWN && !fileChooserIcons.isEmpty() && !fileNameTextField.isFocused()) {
                    selection = Math.min(selection + 4, fileChooserIcons.size() - 1);
                    if (selection < 0) selection = 0;
                    updateSelectionTextField();
                } else if (key == GLFW_KEY_UP && !fileChooserIcons.isEmpty()&& !fileNameTextField.isFocused()) {
                    selection = Math.max(selection - 4, 0);
                    updateSelectionTextField();
                }else if (key == GLFW_KEY_RIGHT && !fileChooserIcons.isEmpty()&& !fileNameTextField.isFocused()) {
                    selection = Math.min(selection + 1, fileChooserIcons.size() - 1);
                    if (selection < 0) selection = 0;
                    updateSelectionTextField();
                } else if (key == GLFW_KEY_LEFT && !fileChooserIcons.isEmpty()&& !fileNameTextField.isFocused()) {
                    selection = Math.max(selection - 1, 0);
                    updateSelectionTextField();
                } else if (key == GLFW_KEY_ENTER && selection >= 0 && selection < fileChooserIcons.size()) {
                    // Enter key opens directory or selects file
                    handleDoubleClick(selection);
                }
            }

            for (GLFWKeyCallback callback : KeyCallbacks) {
                callback.invoke(windowHandle, key, scancode, action, mods);
            }
        });

        GLFW.glfwSetCharCallback(window, (windowHandle, codepoint) -> {
            for (GLFWCharCallback callback : CharCallbacks) callback.invoke(windowHandle, codepoint);
        });

        GLFW.glfwSetScrollCallback(window, (windowHandle, xoffset, yoffset) -> {
            for (GLFWScrollCallback callback : ScrollCallbacks) callback.invoke(windowHandle, xoffset, yoffset);
            Point2D mousePos = new Point2D.Double(mouseX,mouseY);
            for (Object object: Objects.requireNonNull(GUIComponent.getGUIComponents(self))){
                if (object instanceof GUIComponent) {
                    GUIComponent guiComponent = (GUIComponent) object;
                    guiComponent.updateHitBox();
                    if (guiComponent.getHitBox().contains(mousePos)&&guiComponent.isVisible()) {
                        return;
                    }
                }
            }
            mouseWheelVelocity = (float) -yoffset;
        });

        GLFW.glfwMakeContextCurrent(oldContext);
        backgroundCube = Utils.loadObj(this,SnowMemo.class.getResourceAsStream("Cube.obj"), FileChooser.class)
                .setScale(new Vector3f(1,3f,3f)).setPosition(new Vector3f(1.25f,-2f,0f));
        backgroundCube.outline = false;
        originalBackgroundCubeVerts = backgroundCube.getVertices();
        tweenManager = new TweenManager();
        Tween.registerAccessor(Mesh.class, new MeshAccessor());
        if (shadowMap == null) {
            try {
                shadowMap = new ShadowMap();
                shadowMap.setOrthoBounds(new ShadowMap.OrthoBounds(15f, 0.1f, 100f));
                shadowMap.setShadowDistance(100f);
                sun = new DirectionalLight(
                        this,
                        new Vector3f(0.0f, -1.0f, -1.0f).normalize(), // toward south and downward
                        new Vector4f(1, 1, 1, 1.0f),
                        new Vector4f(1.0f, 0.95f, 0.8f, 1.0f),
                        new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                        10f
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        initGUI();
    }
    private void handleDoubleClick(int index) {
        if (index < 0 || index >= fileChooserIcons.size()) return;

        FileChooserIcon icon = fileChooserIcons.get(index);
        File file = icon.file;

        if (file.isDirectory()) {
            // Navigate into directory
            currentDirectory = file;
            selection = -1; // Reset selection when changing directory
        } else if (file.isFile()) {
            // Put filename in text field
            fileNameTextField.setText(file.getName());
            selection = index+1;
        }
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
    private void updateSelectionTextField() {
        if (selection >= 0 && selection < fileChooserIcons.size()) {
            FileChooserIcon icon = fileChooserIcons.get(selection);
            if (icon.file.isFile()) {
                fileNameTextField.setText(icon.file.getName());
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
        folderFrame = new Frame(this,0.05f,0.05f,0.2f,0.825f);
        folderFrame.setCornerRadius(30);
        folderFrame.setBackgroundColor(SnowMemo.currentTheme.getSecondaryColors()[1]);
        folderFrame.setBorderColor(SnowMemo.currentTheme.getSecondaryColors()[0]);
        folderFrame.setShowBorder(true);
        folderFrame.setBorderThickness(2f);
        folderFrame.setZ_Index(Short.MIN_VALUE);
        homeFolder = new ScrollableFrame(this,0f,0.1f,1f,0.5f);
        homeFolder.setParent(folderFrame);
        homeFolder.setSmoothScrollEnabled(false);
        homeFolder.setBorderColor(SnowMemo.currentTheme.getSecondaryColors()[0]);
        homeFolder.setShowBorder(true);
        homeFolder.setBorderThickness(2f);
        homeFolder.setZ_Index(Short.MIN_VALUE);
        recentFolder = new ScrollableFrame(this,0f,0.1f,1f,0.45f);
        recentFolder.setParent(folderFrame);
        recentFolder.setSmoothScrollEnabled(false);
        recentFolder.setBorderColor(SnowMemo.currentTheme.getSecondaryColors()[0]);
        recentFolder.setShowBorder(true);
        recentFolder.setBorderThickness(2f);
        recentFolder.setZ_Index(Short.MIN_VALUE);
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
                                .setWidth(1.0f)
                                .setHeight(0.15f);
                        suggestionLabel.setBackgroundColor(new Color(245, 245, 245, 200));
                        suggestionLabel.setTextColor(new Color(33, 37, 41));
                        suggestionLabel.setAutoResizeMode(Label.AutoResizeMode.FIT_HEIGHT);
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
                        selection = i+1;
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
        historyFrame = new GUIComponent(this, 0f, 0f, 1f, 0.1f) {  // Make it taller too
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Color.white);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Path2D path = getPath2D();
                path.closePath();                                    // bottom edge
                g2.setStroke(new BasicStroke(2));
                g2.fill(path);
                g2.setColor(Color.BLACK);
                g2.draw(path);

            }

            private Path2D getPath2D() {
                float arc = 17.5f;
                Path2D path = new Path2D.Double();
                path.moveTo(1, heightPx - 1);                        // bottom-left
                path.lineTo(1, arc);                                 // up left side
                path.quadTo(1, 1, arc, 1);                           // top-left curve
                path.lineTo(widthPx - arc - 1, 1);                   // top edge
                path.quadTo(widthPx - 1, 1, widthPx - 1, arc);       // top-right curve
                path.lineTo(widthPx - 1, heightPx - 1);              // right edge down
                return path;
            }

            @Override
            public void init() {
            }
        };
        refreshHistoryButton = new GUIComponent(this,0.25f,0f,0.25f,1f) {
            private BufferedImage icon = null;
            @Override
            protected void paintComponent(Graphics g) {
                if (icon == null) {
                    try {
                        icon = ImageIO.read(Objects.requireNonNull(FileChooser.class.getResourceAsStream("Icons/RefreshIcon.png")));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (icon!=null) g.drawImage(icon,0,0,widthPx,heightPx,null);
            }

            @Override
            public void init() {

            }
        };
        refreshHistoryButton.setParent(historyFrame);
        backHistoryButton = new GUIComponent(this,0.5f,0f,0.25f,1.0f) {
            private BufferedImage icon = null;
            @Override
            protected void paintComponent(Graphics g) {
                if (icon == null) {
                    try {
                        icon = ImageIO.read(Objects.requireNonNull(FileChooser.class.getResourceAsStream("Icons/BackIcon.png")));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (icon!=null) g.drawImage(icon,0,0,widthPx,heightPx,null);
            }

            @Override
            public void init() {

            }
        };
        backHistoryButton.setParent(historyFrame);
        forwardHistoryButton = new GUIComponent(this,0.75f,0f,0.25f,1.0f) {
            private BufferedImage homeIcon = null;
            @Override
            protected void paintComponent(Graphics g) {
                if (homeIcon == null) {
                    try {
                        homeIcon = ImageIO.read(Objects.requireNonNull(FileChooser.class.getResourceAsStream("Icons/ForwardIcon.png")));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (homeIcon!=null) g.drawImage(homeIcon,0,0,widthPx,heightPx,null);
            }

            @Override
            public void init() {

            }
        };
        forwardHistoryButton.setParent(historyFrame);
        homeHistoryButton = new GUIComponent(this, 0f, 0f, 0.25f, 1.0f) {
            private BufferedImage homeIcon = null;
            @Override
            protected void paintComponent(Graphics g) {
                if (homeIcon == null) {
                    try {
                        homeIcon = ImageIO.read(Objects.requireNonNull(FileChooser.class.getResourceAsStream("Icons/HomeIcon.png")));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (homeIcon!=null) {
                    System.out.println("RENDERED");
                    g.drawImage(homeIcon,0,0,widthPx,heightPx,null);
                }
            }

            @Override
            public void init() {
            }
        };
        homeHistoryButton.setParent(historyFrame);
        forwardHistoryButton.setParent(historyFrame);  // Add this
        backHistoryButton.setParent(historyFrame);      // Add this
        refreshHistoryButton.setParent(historyFrame);   // Add this
        for (GUIComponent child : historyFrame.getChildren()) {
            child.setZ_Index(100);
        }
        homeHistoryButton.setVisible(true);
        backHistoryButton.setVisible(true);
        forwardHistoryButton.setVisible(true);
        refreshHistoryButton.setVisible(true);
        historyFrame.setVisible(true);
        refreshHistoryButton.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent e) {
                createFilesAndFolders();
            }
        });
        homeHistoryButton.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent e) {
                fileHistory.home();
                System.out.println("Going to home");
                currentDirectory = fileHistory.getCurrent();
            }
        });

        forwardHistoryButton.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent e) {
                System.out.println("Forward clicked");
                fileHistory.forward();
                currentDirectory = fileHistory.getCurrent();
            }
        });

        backHistoryButton.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent e) {
                System.out.println("Back clicked");
                fileHistory.back();  // Fix this too!
                currentDirectory = fileHistory.getCurrent();
            }
        });
        folderFrame.addChild(historyFrame);
        historyFrame.setY(0f);
        historyFrame.setZ_Index(1000);

        selectionMesh = Utils.loadObj(getClass().getResourceAsStream("FileSelector.obj"),this);
        selectionMesh.init();
        selectionMesh.setPosition(new Vector3f(0,0,1.1f));
        historyFrame.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent e) {
                System.out.println("historyFrame CLICKED at: " + e.getMouseX() + ", " + e.getMouseY());
            }
        });

// Add this RIGHT AFTER creating folderFrame (around line 261)
        folderFrame.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent e) {
                System.out.println("folderFrame CLICKED at: " + e.getMouseX() + ", " + e.getMouseY());
            }
        });
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
        PerformanceProfiler.startFrame();

        PerformanceProfiler.start("Tween Update");
        long currentUpdate = System.nanoTime();
        float deltaTime = (currentUpdate - lastUpdate) / 1_000_000_000.0f;
        lastUpdate = currentUpdate;
        tweenManager.update(deltaTime);
        PerformanceProfiler.end("Tween Update");

        long oldContext = GLFW.glfwGetCurrentContext();
        if (GLFW.glfwWindowShouldClose(oldContext)) {
            this.close();
            return;
        }
        GLFW.glfwMakeContextCurrent(this.window);

        PerformanceProfiler.start("Shadow Map Setup");
        shadowMap.setOrthoBounds(new ShadowMap.OrthoBounds(100f, 0.1f, 100f));
        shadowMap.setShadowDistance(100f);
        Vector3f sceneCenter = new Vector3f(0f, 0f, 0);
        PerformanceProfiler.end("Shadow Map Setup");

        PerformanceProfiler.start("Shadow Map Render");
        shadowMap.render(this, Mesh.getAllMeshes(this), sceneCenter);
        Matrix4f lightSpaceMatrix = shadowMap.getLightSpaceMatrix();
        PerformanceProfiler.end("Shadow Map Render");

        PerformanceProfiler.start("Shader Setup");
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
        PerformanceProfiler.end("Shader Setup");

        PerformanceProfiler.start("GL Setup");
        glViewport(0, 0, width, height);
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
        PerformanceProfiler.end("GL Setup");

        PerformanceProfiler.start("Background Render");
        backgroundCube.render(camera);
        PerformanceProfiler.end("Background Render");

        PerformanceProfiler.start("Frustum Culling");
        Frustum frustum = new Frustum();
        Matrix4f projView = new Matrix4f(projectionMatrix).mul(camera.getViewMatrix());
        frustum.update(projView);

        for (FileChooserIcon f : fileChooserIcons) {
            if (f.mesh != null) {
                f.mesh.updateCulling(frustum);
                if (f.billboardGUI != null) {
                    f.billboardGUI.updateCulling(frustum);
                }
            }
        }
        PerformanceProfiler.end("Frustum Culling");

        PerformanceProfiler.start("Selection Update");
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
        PerformanceProfiler.end("Selection Update");

        PerformanceProfiler.start("FileIcons Rendering");
        int renderedCount = 0;
        for (FileChooserIcon fileChooserIcon : fileChooserIcons) {
            if (fileChooserIcon.mesh.isRenderVisible()) {
                fileChooserIcon.render();
                renderedCount++;
            }
        }
        PerformanceProfiler.end("FileIcons Rendering");

        PerformanceProfiler.start("Selection Mesh");
        selectionMesh.updateCulling(frustum);
        selectionMesh.render(camera);
        PerformanceProfiler.end("Selection Mesh");

        PerformanceProfiler.start("GUI Rendering");
        GUIComponent.renderGUIs(this);
        PerformanceProfiler.end("GUI Rendering");

        PerformanceProfiler.start("Buffer Swap");
        GLFW.glfwSwapBuffers(window);
        PerformanceProfiler.end("Buffer Swap");

        GLFW.glfwMakeContextCurrent(oldContext);
        PerformanceProfiler.endFrame();

        if (fps % 120 == 0) { // Print every 2 seconds
            PerformanceProfiler.printReportFile();
            System.out.println("Rendered " + renderedCount + " / " + fileChooserIcons.size() + " icons");
        }
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
        if (!previousDirectory.getAbsolutePath().equals(currentDirectory.getAbsolutePath())) createFilesAndFolders();
    }
    LoadingWindow creationOfFilesAndFoldersLoadingWindow;
    public void createFilesAndFolders(){
        if (creationOfFilesAndFoldersLoadingWindow==null) {
            creationOfFilesAndFoldersLoadingWindow = new LoadingWindow(window,200,100);
        }

        resetCamera();
        fileHistory.add(fileHistory.size(), currentDirectory);
        scroll = MIN_SCROLL;
        selection = 0;

        creationOfFilesAndFoldersLoadingWindow.setMaxValue(fileChooserIcons.size());
        creationOfFilesAndFoldersLoadingWindow.setValue(0);
        creationOfFilesAndFoldersLoadingWindow.setDescription("Unloading files and folders...");
        creationOfFilesAndFoldersLoadingWindow.show();

        for (FileChooserIcon icon : fileChooserIcons) {
            if (icon.mesh != null) {
                icon.mesh.cleanUp();
                if (icon.billboardGUI != null) {
                    icon.billboardGUI.cleanUp();
                }
                creationOfFilesAndFoldersLoadingWindow.setValue(creationOfFilesAndFoldersLoadingWindow.getValue()+1);
            }
        }
        creationOfFilesAndFoldersLoadingWindow.hide();
        fileChooserIcons.clear();

        File[] allFiles = currentDirectory.listFiles();
        List<File> files = Arrays.stream(allFiles)
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .filter(file -> !file.isHidden())
                .toList();

        int columns = 4;
        float spacing = 1.25f;
        int visibleIndex = 0;

        if (currentDirectory.getParent() != null) {
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

        creationOfFilesAndFoldersLoadingWindow.setDescription("Loading files and folders...");
        creationOfFilesAndFoldersLoadingWindow.setMaxValue(files.size());
        creationOfFilesAndFoldersLoadingWindow.setValue(0);
        creationOfFilesAndFoldersLoadingWindow.show();

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

                // REMOVED: render(); // ← THIS WAS KILLING PERFORMANCE!
                // REMOVED: creationOfFilesAndFoldersLoadingWindow.render();

                // Update loading window every 10 files instead
                if (visibleIndex % 10 == 0) {
                    creationOfFilesAndFoldersLoadingWindow.setValue(creationOfFilesAndFoldersLoadingWindow.getValue() + 10);
                }
            }
        }

        int rows = (visibleIndex + columns - 1) / columns;
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
        creationOfFilesAndFoldersLoadingWindow.hide();
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
        return worldPosition;
    }
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
    public static FileChooser getCurrentFileChooser() {
        return currentFileChooser;
    }

    @Override
    public void close() {
        creationOfFilesAndFoldersLoadingWindow.close();
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
        private static final HashMap<String, Mesh> registeredMesh = new HashMap<>();
        private static void registerMeshData(String path){
            registeredMesh.put(path,Utils.loadObj(FileChooser.class.getResourceAsStream(path), FileChooser.getCurrentFileChooser()));
        };
        public FileChooserIcon(File file, String meshPath) {
            if (!registeredMesh.containsKey(meshPath))registerMeshData(meshPath);
            try {
//                this.mesh = Utils.loadObjFromString(registeredFileData.get(meshPath),FileChooser.class);
                this.mesh = new Mesh(registeredMesh.get(meshPath),currentFileChooser);
            }catch (Exception e){
                System.err.println(e);
                this.mesh = Utils.loadObj(FileChooser.getCurrentFileChooser(),this.getClass().getResourceAsStream(meshPath), FileChooser.class);
            }
            this.mesh.setMaterial(Utils.loadMaterial(FileChooser.class,"Folder.mtl","Material"));
            this.file = file;
            this.guiComponent = new GUIComponent(currentFileChooser, 256, 256) {
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
            billboardGUI.setMaxTextureSize(32, 32);      // Even lower for more FPS
            billboardGUI.setUpdateInterval(150);            // Update less frequently
            billboardGUI.setSkipUpdateWhenFar(true);        // Skip updates when far
            billboardGUI.setMaxUpdateDistance(30.0f);       // Adjust based on your scene

// Monitor performance
            billboardGUI.setEnableTimingLogs(true);
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

            float baseScale = 0.75f;
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
            if (mesh == null) return false; // Removed billboardGUI check - it's optional

            Vector3f meshPos = mesh.getPosition();
            Vector3f scale = mesh.getScale(); // Use actual scale instead of hardcoded 0.375f

            // Calculate mesh bounds using actual scale
            float meshHalfSize = 0.7f * scale.x;

            // Mesh bounds
            float meshMinX = meshPos.x - meshHalfSize;
            float meshMaxX = meshPos.x + meshHalfSize;
            float meshMinY = meshPos.y - meshHalfSize;
            float meshMaxY = meshPos.y + meshHalfSize;

            // Billboard bounds (if available)
            if (billboardGUI != null && guiComponent != null) {
                float texWidth = guiComponent.getWidthPx();
                float texHeight = guiComponent.getHeightPx();

                if (texWidth > 0 && texHeight > 0) {
                    float aspect = texWidth / texHeight;
                    float baseScale = 0.15f;
                    float halfWidth = (baseScale * aspect) / 2;
                    float halfHeight = baseScale / 2;

                    // Billboard is positioned below the mesh
                    Vector3f billboardPos = new Vector3f(meshPos.x - 0.025f, meshPos.y - 0.6f, meshPos.z);
                    float bbMinX = billboardPos.x - halfWidth;
                    float bbMaxX = billboardPos.x + halfWidth;
                    float bbMinY = billboardPos.y - halfHeight;
                    float bbMaxY = billboardPos.y + halfHeight;

                    // Expand bounds to include both mesh and billboard
                    meshMinX = Math.min(meshMinX, bbMinX);
                    meshMaxX = Math.max(meshMaxX, bbMaxX);
                    meshMinY = Math.min(meshMinY, bbMinY);
                    meshMaxY = Math.max(meshMaxY, bbMaxY);
                }
            }

            // ========================================
            // SENSITIVITY TUNING SECTION
            // ========================================

            // Base padding - increase this for more sensitivity
            float basePadding = 0.4f; // Increased from 0.2f to 0.4f (2x more sensitive)

            // Adaptive padding based on camera distance
            // Icons further away get bigger hitboxes for easier clicking
            float distanceFromCamera = Math.abs(meshPos.y - currentFileChooser.camera.getPosition().y);
            float distanceFactor = distanceFromCamera * 0.12f; // Adjust multiplier as needed

            float padding = basePadding + distanceFactor;

            // Apply padding to all sides
            meshMinX -= padding;
            meshMaxX += padding;
            meshMinY -= padding;
            meshMaxY += padding;

            // Check collision in 2D
            return cursorPos.x >= meshMinX && cursorPos.x <= meshMaxX
                    && cursorPos.y >= meshMinY && cursorPos.y <= meshMaxY;
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
            mesh.cullFace = false;
//            mesh.outline = false;
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
