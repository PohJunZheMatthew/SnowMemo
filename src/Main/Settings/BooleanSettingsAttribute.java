package Main.Settings;

import GUI.BillBoardGUI.BillboardGUI;
import GUI.BillBoardGUI.LabelBillboardGUI;
import GUI.GUIComponent;
import GUI.GUIComponentAccessor;
import Main.Camera;
import Main.Frustum;
import Main.Mesh;
import Main.Window;
import aurelienribon.tweenengine.BaseTween;
import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenCallback;
import aurelienribon.tweenengine.TweenManager;
import aurelienribon.tweenengine.equations.Quad;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.awt.*;
import java.util.Vector;

public class BooleanSettingsAttribute extends BillboardGUI implements SettingsAttribute<Boolean> {
    Boolean value = false;
    private TweenManager tweenManager = new TweenManager();
    private LabelBillboardGUI label;
    boolean tweening = false;
    private final Vector3f originalPos = new Vector3f(-0.25f,0,0.3f);
    private Vector3f offsetPosition = new Vector3f();
    private int index = 0;
    public BooleanSettingsAttribute(Window currentWindow, String text ) {
        super(currentWindow, new GUIComponent(currentWindow, 256, 128) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();

                try {
                    // Enable anti-aliasing
                    if (Settings.getValue("SnowMemo.Defaults.AntiAliasing") != null &&
                            (boolean)Settings.getValue("SnowMemo.Defaults.AntiAliasing")) {
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    }
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                    // Constants
                    final int PADDING = 8;
                    final int IDEAL_WIDTH = (int)(heightPx * 1.8f); // Compact toggle - 1.8x height
                    final int TRACK_RADIUS = heightPx; // Fully rounded pill shape
                    final int THUMB_SIZE = (int)(heightPx * 0.9f); // Larger thumb for compact look
                    final int THUMB_RADIUS = THUMB_SIZE; // Fully rounded circle

                    // Calculate positions - use ideal width instead of full width
                    float fx = Math.max(0f, Math.min(1f, getX()));
                    int trackWidth = Math.min(widthPx - (PADDING * 2), IDEAL_WIDTH);
                    int trackHeight = heightPx - (PADDING * 2);
                    int trackX = PADDING + (widthPx - trackWidth - PADDING * 2) / 2; // Center the track

                    // Thumb position
                    int thumbMaxX = trackWidth - THUMB_SIZE;
                    int thumbX = trackX + (int)(thumbMaxX * fx);
                    int thumbY = (heightPx - THUMB_SIZE) / 2;

                    // Track background - smooth color transition
                    Color trackColor = fx > 0.5f ?
                            new Color(52, 199, 89) :  // iOS green when on
                            new Color(120, 120, 128, 51); // Gray when off

                    g2d.setColor(trackColor);
                    g2d.fillRoundRect(trackX, PADDING, trackWidth, trackHeight, TRACK_RADIUS, TRACK_RADIUS);
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawRoundRect(trackX,PADDING,trackWidth,trackHeight,TRACK_RADIUS,TRACK_RADIUS);
                    // Thumb (white circle)
                    g2d.setColor(Color.WHITE);
                    g2d.fillRoundRect(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, THUMB_RADIUS, THUMB_RADIUS);

                    // Subtle thumb shadow
                    g2d.setColor(new Color(0, 0, 0, 15));
                    g2d.setStroke(new BasicStroke(1f));
                    g2d.drawRoundRect(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, THUMB_RADIUS, THUMB_RADIUS);

                } finally {
                    g2d.dispose();
                }
            }
        });

        // Set up billboard properties
        setRotation(new Vector3f(0, (float) Math.PI, (float) Math.PI));
        setPosition(new Vector3f(1, 1, 1));
        setFixedAspectRatio(2.0f);
        getScale().div(2f);

        label = new LabelBillboardGUI(currentWindow, text);
        label.init();
        label.setPosition(new Vector3f(-2,1,1));
        // Keep thread rendering enabled
        mainGUIComponent.threadRendering = true;

        // Register tween accessor
        if (Tween.getRegisteredAccessor(GUIComponent.class) == null) {
            Tween.registerAccessor(GUIComponent.class, new GUIComponentAccessor());
        }

        // Mouse click handler
        currentWindow.MouseButtonCallbacks.add(new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (action == GLFW.GLFW_PRESS&& collidesWith(win.getCursorWorldPosAtZ(getPosition().z))&&isVisible()&&isRenderVisible()) {
                    System.out.println();
                    value = !value;
                    tweening = true;
                    Tween.to(mainGUIComponent, GUIComponentAccessor.POSITION_X, 0.5f)
                            .target(value ? 1.0f : 0.0f)
                            .ease(Quad.INOUT)
                            .start(tweenManager)
                            .setCallback((type, baseTween) -> {
                                if (type == TweenCallback.COMPLETE) {
                                    boolean running = true;
                                    for (BaseTween<?> object : tweenManager.getObjects()) {
                                        if (!object.isFinished()){
                                            running = false;
                                            break;
                                        }
                                    }
                                    tweening = running;
                                }
                            });
                }
            }
        });
    }

    long lastTime = System.nanoTime();

    @Override
    public void render(Camera c) {
        long now = System.nanoTime();
        float delta = (now - lastTime) / 1_000_000_000f;
        lastTime = now;

        tweenManager.update(delta);
        if(Math.abs(c.getPosition().y-getPosition().y)>3f) return;

        this.beginOcclusionQuery(c);
        mainGUIComponent.markDirty();
        label.setPosition(new Vector3f(getPosition()).add(-1,0,0));
        markDirty();
        forceUpdateNow();
        super.render(c);
        label.render(c);
    }

    @Override
    public SettingsAttribute<Boolean> setValue(Boolean value) {
        this.value = value;
        mainGUIComponent.setX(value ? 1 : 0);
        mainGUIComponent.markDirty();
        markDirty();
        forceUpdateNow();
        return this;
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public void toDefault() {
        setValue(false);
    }

    @Override
    public float getHeight() {
        return Math.max(getScale().y,label.getScale().y)-0.5f;
    }

    @Override
    public SettingsAttribute<Boolean> setY(float y) {
        // Store the base Y position
        originalPos.y = y;
        // Apply Y position including any offset
        Vector3f newPos = new Vector3f(originalPos).add(offsetPosition);
        setPosition(newPos);
        return this;
    }

    @Override
    public SettingsAttribute<Boolean> setOffsetPos(Vector3f pos) {
        // Store the offset
        offsetPosition.set(pos);
        // Apply offset to the actual position
        Vector3f newPos = new Vector3f(originalPos).add(offsetPosition);
        setPosition(newPos);
        return this;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public SettingsAttribute<Boolean> setIndex(int index) {
        this.index = index;
        return this;
    }

    @Override
    public Mesh setPosition(Vector3f pos){
        markDirty();
        updateHitBox();
        return super.setPosition(pos);
    }
    @Override
    public void cleanUp(){
        super.cleanUp();
        label.cleanUp();
    }
}