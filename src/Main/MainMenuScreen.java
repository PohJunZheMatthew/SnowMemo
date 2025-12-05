package Main;

import GUI.BillBoardGUI.BillboardGUI;
import GUI.GUIComponent;
import Main.Resources.Icons.MainMenu.MainMenuIcons;
import Main.Settings.Settings;
import com.sun.tools.javac.Main;
import org.apache.commons.io.FilenameUtils;
import org.joml.Vector3f;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.awt.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public class MainMenuScreen extends Mesh {
    private Vector3f originalPos = new Vector3f(1f, -0.25f, 0);
    private Window window;
    private Mesh menuButtonsBackgroundCube;

    private BillboardGUI announcementsBillboard;
    private BillboardGUI memoTitle,memoNew;
    private BillboardGUI snowMemoLogo;
    private BillboardGUI homeMenuButton;
    private BillboardGUI memoMenuButton;
    private BillboardGUI settingsMenuButton;
    private Vector3f announcementsOffset = new Vector3f(-0f, 1f, 0.3f);
    private Vector3f memoTitleOffset = new Vector3f(-2f, -0.5f, 0.3f),memoNewOffset = new Vector3f(-1f,-0.5f,0.3f);
    private Vector3f memoGridStartOffset = new Vector3f(-2f, -1.5f, 0.3f);

    private static final int MEMO_GRID_COLUMNS = 4;
    private static final float MEMO_SPACING_X = 1.25f;
    private static final float MEMO_SPACING_Y = 1.25f;
    private Stages currentStage = Stages.HOME;
    private float maxScroll = 0;
    private float currentScrollOffset = 0;
    private Settings settings;

    public enum Stages {
        HOME,
        MEMO,
        SETTINGS
    }

    public MainMenuScreen(Window window) {
        super(Utils.loadObj(SnowMemo.class.getResourceAsStream("Cube.obj"), window), window);
        setPosition(originalPos);
        this.window = window;

        window.ScrollCallbacks.add(new GLFWScrollCallback() {
            @Override
            public void invoke(long window, double xoffset, double yoffset) {
                float SCROLL_SPEED = 1.0f;
                currentScrollOffset += (float) -yoffset * SCROLL_SPEED;
                currentScrollOffset = Math.max(0, Math.min(currentScrollOffset, maxScroll));
            }
        });
        window.MouseButtonCallbacks.add(new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long win, int button, int action, int mods) {
                if (action== GLFW.GLFW_PRESS) {
                    if (homeMenuButton.collidesWith(window.getCursorWorldPosAtZ(homeMenuButton.getPosition().z))) {
                        currentStage = Stages.HOME;
                        currentScrollOffset = 0;
                    }else if (memoMenuButton.collidesWith(window.getCursorWorldPosAtZ(homeMenuButton.getPosition().z))){
                        currentStage = Stages.MEMO;
                        currentScrollOffset = 2;
                    }else if (settingsMenuButton.collidesWith(window.getCursorWorldPosAtZ(homeMenuButton.getPosition().z))){
                        currentStage = Stages.SETTINGS;
                    }
                }
                if (action==GLFW.GLFW_PRESS){
                    Vector3f cursorPos = window.getCursorWorldPosAtZ(memoGridStartOffset.z);
                    if (memoNew.collidesWith(window.getCursorWorldPosAtZ(memoNew.getPosition().z))){
                        Memo memo = Memo.createNewMemo(window);
                        SnowMemo.draggable = true;
                        memo.load();
                        Memo.setCurrentMemoGUITitle(memo.getMemoImGUITitle());
                        Memo.setCurrentMemo(memo);
                        Memo.setReturnFunc(()->{
                            if (Memo.getCurrentMemo() != null) {
                                Memo.getCurrentMemo().save(false);
                                Memo.getCurrentMemo().unload();
                            }
                            SnowMemo.draggable = false;
                            SnowMemo.resetScroll();
                            SnowMemo.camera.setPosition(new Vector3f(0f, 0, 5));
                            Memo.setCurrentMemo(null);
                            Memo.setCurrentMemoGUITitle(null);
                            setVisible(true);
                            Memo.refreshMemos(window);
                        });
                        setVisible(false);
                        SnowMemo.draggable = true;
                    }
                    for (Memo memo:Memo.getMemos()){
                        if (memo.getMainBillboard().collidesWith(cursorPos)&&isVisible()){
                            SnowMemo.draggable = true;
                            memo.load();
                            Memo.setCurrentMemoGUITitle(memo.getMemoImGUITitle());
                            Memo.setCurrentMemo(memo);
                            Memo.setReturnFunc(()->{
                                if (Memo.getCurrentMemo() != null) {
                                    Memo.getCurrentMemo().save(false);
                                    Memo.getCurrentMemo().unload();
                                }
                                SnowMemo.draggable = false;
                                SnowMemo.resetScroll();
                                SnowMemo.camera.setPosition(new Vector3f(0f, 0, 5));
                                Memo.setCurrentMemo(null);
                                Memo.setCurrentMemoGUITitle(null);
                                setVisible(true);
                                Memo.refreshMemos(window);
                            });
                            setVisible(false);
                            SnowMemo.draggable = true;
                        }
                    }
                }
            }
        });
        settings = new Settings(window);
        settings.setVisible(false);
    }

    float[] originalVerts;

    @Override
    public void render(Camera camera) {
        if (!isVisible()) return;
        settings.setVisible(currentStage.equals(Stages.SETTINGS));
        homeMenuButton.render(camera);
        memoMenuButton.render(camera);
        settingsMenuButton.render(camera);
        menuButtonsBackgroundCube.render(camera);
        settings.render(camera);
        if(currentStage==Stages.HOME||currentStage==Stages.MEMO) {
            int memoCount = Memo.getMemos().size();
            int rows = (int) Math.ceil((double) memoCount / MEMO_GRID_COLUMNS);
            float dynamicHeight = 4f + (rows - 1) * (2f);
            maxScroll = Math.max(0, (rows - 1) * (MEMO_SPACING_Y));
            Vector3f basePos = new Vector3f(
                    originalPos.x,
                    originalPos.y + currentScrollOffset,
                    originalPos.z
            );
            setPosition(basePos);

            updateBillboardPositions();

            memoTitle.render(camera);
            memoNew.render(camera);
            int memoIndex = 0;
            for (Memo memo : Memo.getMemos()) {
                Vector3f memoPos = calculateMemoGridPosition(memoIndex, basePos);
                memo.setMainBillboardPos(memoPos);
                memo.renderMainMemoBillboard(camera);
                memoIndex++;
            }

            Vector3f scale = new Vector3f(5f, dynamicHeight, 1);
            float[] vertices = originalVerts.clone();
            scaleVerticesDown(vertices, scale);
            this.updateVertices(vertices);

            super.render(camera);
            announcementsBillboard.render(camera);
        }
    }

    @Override
    public void updateCulling(Frustum frustum) {
        super.updateCulling(frustum);

        if (menuButtonsBackgroundCube != null) {
            menuButtonsBackgroundCube.updateCulling(frustum);
        }

        if (announcementsBillboard != null) {
            announcementsBillboard.updateCulling(frustum);
        }

        if (memoTitle != null) {
            memoTitle.updateCulling(frustum);
        }
        if (memoNew != null) {
            memoNew.updateCulling(frustum);
        }
        for (Memo memo : Memo.getMemos()) {
            if (memo.getMainBillboard() != null) {
                memo.getMainBillboard().updateCulling(frustum);
            }
        }
    }

    public void initMenuButtons() {
        initHomeButton();
        initMemoButton();
        initSettingsButton();
    }

    public void initHomeButton(){
        homeMenuButton = new BillboardGUI(window, new GUIComponent(window, 256, 384) {
            final String text = "Home";

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;

                g2d.drawImage(MainMenuIcons.HOME, 0, 0, 256, 256, null);
                g2d.setColor(Color.BLACK);

                int outerPaddingX = 25;
                int outerPaddingY = 15;

                int iconHeight = 256;

                int textOffsetY = -30;

                int areaX = outerPaddingX;
                int areaY = iconHeight + outerPaddingY + textOffsetY;

                int areaWidth = 256 - outerPaddingX * 2;
                int areaHeight = 128 - outerPaddingY * 2;

                Font themeFont = SnowMemo.currentTheme.getFonts()[0];
                Font baseFont = themeFont.deriveFont(48f);
                g2d.setFont(baseFont);

                FontMetrics fm = g2d.getFontMetrics();
                double scaleW = (double) areaWidth / fm.stringWidth(text);
                double scaleH = (double) areaHeight / (fm.getAscent() + fm.getDescent());
                double scale = Math.min(scaleW, scaleH);

                Font scaled = baseFont.deriveFont((float) (48f * scale));
                g2d.setFont(scaled);
                fm = g2d.getFontMetrics();

                int x = areaX + (areaWidth - fm.stringWidth(text)) / 2;
                int y = areaY + (areaHeight - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent();

                g2d.drawString(text, x, y);
            }
        });
        homeMenuButton.init();
        homeMenuButton.setPosition(new Vector3f(-3f,0.75f,0.3f));
        homeMenuButton.setScale(new Vector3f((float) 2 / 3,1f,1f));
        homeMenuButton.setRotation(new Vector3f((float) 0, (float) Math.PI, (float) Math.PI));
    }

    public void initMemoButton(){
        memoMenuButton = new BillboardGUI(window, new GUIComponent(window, 256, 384) {
            final String text = "Memo";

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;

                g2d.drawImage(MainMenuIcons.MEMO, 0, 25, 256, 256, null);
                g2d.setColor(Color.BLACK);

                int outerPaddingX = 25;
                int outerPaddingY = 15;

                int iconHeight = 256;

                int textOffsetY = -30;

                int areaX = outerPaddingX;
                int areaY = iconHeight + outerPaddingY + textOffsetY;

                int areaWidth = 256 - outerPaddingX * 2;
                int areaHeight = 128 - outerPaddingY * 2;

                Font themeFont = SnowMemo.currentTheme.getFonts()[0];
                Font baseFont = themeFont.deriveFont(48f);
                g2d.setFont(baseFont);

                FontMetrics fm = g2d.getFontMetrics();
                double scaleW = (double) areaWidth / fm.stringWidth(text);
                double scaleH = (double) areaHeight / (fm.getAscent() + fm.getDescent());
                double scale = Math.min(scaleW, scaleH);

                Font scaled = baseFont.deriveFont((float) (48f * scale));
                g2d.setFont(scaled);
                fm = g2d.getFontMetrics();

                int x = areaX + (areaWidth - fm.stringWidth(text)) / 2;
                int y = areaY + (areaHeight - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent();

                g2d.drawString(text, x, y);
            }
        });
        memoMenuButton.init();
        memoMenuButton.setPosition(new Vector3f(-3f,0f,0.3f));
        memoMenuButton.setScale(new Vector3f((float) 2/3,1f,1f));
        memoMenuButton.setRotation(new Vector3f((float) 0, (float) Math.PI, (float) Math.PI));
    }

    public void initSettingsButton(){
        settingsMenuButton = new BillboardGUI(window, new GUIComponent(window, 256, 384) {
            final String text = "Settings";

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;

                g2d.drawImage(MainMenuIcons.SETTINGS, 0, 25, 256, 256, null);
                g2d.setColor(Color.BLACK);

                int outerPaddingX = 25;
                int outerPaddingY = 15;

                int iconHeight = 256;

                int textOffsetY = -30;

                int areaX = outerPaddingX;
                int areaY = iconHeight + outerPaddingY + textOffsetY;

                int areaWidth = 256 - outerPaddingX * 2;
                int areaHeight = 128 - outerPaddingY * 2;

                Font themeFont = SnowMemo.currentTheme.getFonts()[0];
                Font baseFont = themeFont.deriveFont(48f);
                g2d.setFont(baseFont);

                FontMetrics fm = g2d.getFontMetrics();
                double scaleW = (double) areaWidth / fm.stringWidth(text);
                double scaleH = (double) areaHeight / (fm.getAscent() + fm.getDescent());
                double scale = Math.min(scaleW, scaleH);

                Font scaled = baseFont.deriveFont((float) (48f * scale));
                g2d.setFont(scaled);
                fm = g2d.getFontMetrics();

                int x = areaX + (areaWidth - fm.stringWidth(text)) / 2;
                int y = areaY + (areaHeight - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent();

                g2d.drawString(text, x, y);
            }
        });
        settingsMenuButton.init();
        settingsMenuButton.setPosition(new Vector3f(-3f,-0.8f,0.3f));
        settingsMenuButton.setScale(new Vector3f((float) 2/3,1f,1f));
        settingsMenuButton.setRotation(new Vector3f((float) 0, (float) Math.PI, (float) Math.PI));
    }

    @Override
    public void init() {
        initAnnouncementsBillboard();
        initMemoTitle();
        initMenuButtonsBackground();
        initMainMesh();
        initMenuButtons();

        super.init();
        menuButtonsBackgroundCube.init();
    }

    private void initAnnouncementsBillboard() {
        announcementsBillboard = new BillboardGUI(window, new GUIComponent(window, 1024, 512) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setStroke(new BasicStroke(3f));
                g2d.setColor(Color.WHITE);
                g2d.fillRoundRect((int) 1.5, (int) 1.5, (int) (widthPx - 1.5 * 2), (int) (heightPx - 1.5 * 2), 120, 120);
                g2d.setColor(Color.BLACK);
                g2d.drawRoundRect((int) 1.5, (int) 1.5, (int) (widthPx - 1.5 * 2), (int) (heightPx - 1.5 * 2), 120, 120);
                g2d.dispose();
            }
        });

        announcementsBillboard.init();
        announcementsBillboard.setPosition(new Vector3f(getPosition()).add(announcementsOffset));
        announcementsBillboard.setFixedAspectRatio((float) 512 / 256);
        announcementsBillboard.setScale(new Vector3f(((float) 512 / 256) * 2.5f, 2.5f, 1f));
        announcementsBillboard.setRotation(new Vector3f(0, (float) Math.PI, (float) Math.PI));
    }

    private void initMemoTitle() {
        memoNew = new BillboardGUI(window, new GUIComponent(window,256,256) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.drawImage(MainMenuIcons.MEMO_ADD,0,0,256,256,null);
            }
        });
        memoTitle = new BillboardGUI(window, new GUIComponent(window, 512, 512) {
            String text = "Memos";

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setColor(Color.BLACK);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int panelWidth = getWidthPx();
                int panelHeight = getHeightPx();
                int fontSize = Math.min(panelWidth, panelHeight) / 5;
                Font font = SnowMemo.currentTheme.getFonts()[0].deriveFont(Font.BOLD).deriveFont((float) fontSize);
                g2d.setFont(font);

                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int textHeight = fm.getAscent() - fm.getDescent();

                int x = (panelWidth - textWidth) / 2;
                int y = (panelHeight + textHeight) / 2;

                g2d.drawString(text, x, y);

                int underlineY = y + 2;
                g2d.setStroke(new BasicStroke(Math.max(2, fontSize / 15)));
                g2d.drawLine(x, underlineY, x + textWidth, underlineY);

                g2d.dispose();
            }
        });

        memoTitle.init();
        memoTitle.setPosition(new Vector3f(getPosition()).add(memoTitleOffset));
        memoTitle.setFixedAspectRatio(1);
        memoTitle.setScale(new Vector3f(1.5f));
        memoTitle.setRotation(new Vector3f(0, (float) Math.PI, (float) Math.PI));

        memoNew.init();;
        memoNew.setPosition(new Vector3f(getPosition()).add(memoNewOffset));
        memoNew.setFixedAspectRatio(1);
        memoNew.setScale(new Vector3f(0.25f));
        memoNew.setRotation(new Vector3f(0,(float) Math.PI,(float) Math.PI));
    }

    private void initMenuButtonsBackground() {
        menuButtonsBackgroundCube = new Mesh(
                Utils.loadObj(SnowMemo.class.getResourceAsStream("Cube.obj"), window),
                window
        ).setPosition(new Vector3f(-3f, -0.25f, 0f));

        menuButtonsBackgroundCube.outlineThickness = 1.0075f;

        Vector3f scale = new Vector3f(0.5f, 4f, 1);
        float[] vertices = menuButtonsBackgroundCube.getVertices().clone();
        scaleVertices(vertices, scale);
        menuButtonsBackgroundCube.updateVertices(vertices);
    }

    private void initMainMesh() {
        outlineThickness = 1.0075f;
        originalVerts = getVertices().clone();
    }

    private void scaleVertices(float[] vertices, Vector3f scale) {
        for (int i = 0; i < vertices.length; i += stride) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            if (z > 0) {
                vertices[i + 2] = z + (scale.x - 1) / 2;
            } else {
                vertices[i + 2] = z - (scale.x - 1) / 2;
            }
            if (x > 0) {
                vertices[i] = x + (scale.z - 1) / 2;
            } else {
                vertices[i] = x - (scale.z - 1) / 2;
            }
            if (y > 0) {
                vertices[i + 1] = y + (scale.y - 1) / 2;
            } else {
                vertices[i + 1] = y - (scale.y - 1) / 2;
            }

            float nx = vertices[i + 3];
            float ny = vertices[i + 4];
            float nz = vertices[i + 5];

            nx /= scale.z;
            ny /= scale.y;
            nz /= scale.x;

            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 0.0001f) {
                vertices[i + 3] = nx / length;
                vertices[i + 4] = ny / length;
                vertices[i + 5] = nz / length;
            }
        }
    }
    private float headOffset = 1.5f;
    private void scaleVerticesDown(float[] vertices, Vector3f scale) {
        for (int i = 0; i < vertices.length; i += stride) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            if (z > 0) {
                vertices[i + 2] = z + (scale.x - 1) / 2;
            } else {
                vertices[i + 2] = z - (scale.x - 1) / 2;
            }
            if (x > 0) {
                vertices[i] = x + (scale.z - 1) / 2;
            } else {
                vertices[i] = x - (scale.z - 1) / 2;
            }
            if (y > 0) {
                vertices[i + 1] = y + headOffset;
            }
            if (y<0){
                vertices[i + 1] = y - (scale.y);
            }

            float nx = vertices[i + 3];
            float ny = vertices[i + 4];
            float nz = vertices[i + 5];

            nx /= scale.z;
            ny /= scale.y;
            nz /= scale.x;

            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 0.0001f) {
                vertices[i + 3] = nx / length;
                vertices[i + 4] = ny / length;
                vertices[i + 5] = nz / length;
            }
        }
    }

    private void updateBillboardPositions() {
        Vector3f basePos = getPosition();
        announcementsBillboard.setPosition(new Vector3f(basePos).add(announcementsOffset));
        memoTitle.setPosition(new Vector3f(basePos).add(memoTitleOffset));
        memoNew.setPosition(new Vector3f(basePos).add(memoNewOffset));
        memoNew.updateHitBox();
    }

    private Vector3f calculateMemoGridPosition(int index, Vector3f basePos) {
        int row = index / MEMO_GRID_COLUMNS;
        int col = index % MEMO_GRID_COLUMNS;

        float xOffset = col * MEMO_SPACING_X;
        float yOffset = -row * MEMO_SPACING_Y;
        return new Vector3f(basePos)
                .add(memoGridStartOffset)
                .add(xOffset, yOffset, 0);
    }

    @Override
    public void cleanUp() {
        super.cleanUp();
        menuButtonsBackgroundCube.cleanUp();
        announcementsBillboard.cleanUp();
        settingsMenuButton.cleanUp();
        homeMenuButton.cleanUp();
        memoMenuButton.cleanUp();
        memoTitle.cleanUp();
        memoNew.cleanUp();
    }
}