package Main;//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import GUI.Events.MouseClickCallBack;
import GUI.Events.MouseClickEvent;
import GUI.GUIComponent;
import Light.AmbientLight;
import Light.DirectionalLight;
import Light.PointLight;
import Main.FileChooser.FileChooser;
import Main.Shadow.ShadowMap;
import Main.Theme.Theme;
import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.*;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;

public class SnowMemo {
    Window window;
    Thread updateThread;
    List<Renderable> renderable = new ArrayList<>();
    float mouseWheelVelocity = 0;
    static Camera camera = new Camera();
    double preposx,preposy = 0.0;
    @SuppressWarnings("unused")
    static AmbientLight ambientLight = new AmbientLight(0.7f,0.7f,0.7f,1.0f);
    float zoom = 6;
    float perZoom = 1.0f;
    static final int MAX_ZOOM = 10,MIN_ZOOM = 1;
    @SuppressWarnings("unused")
    static PointLight pointLight;
    static DirectionalLight sun;
    @SuppressWarnings("unused")
    static int timeOfDay = 0;
    static GUIComponent backButton;
    static FileChooser fileChooser;
    public static ShadowMap shadowMap;
    private static final Font quickSandFont;

    static {
        try {
            quickSandFont = Font.createFont(Font.TRUETYPE_FONT,SnowMemo.class.getResourceAsStream("QuicksandFont/Quicksand-VariableFont_wght.ttf"));
        } catch (FontFormatException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Black Theme
    private static final Theme blackTheme = new Theme(
            Color.BLACK,
            new Color[]{ Color.WHITE, Color.GRAY, Color.DARK_GRAY },
            new Color[]{ Color.WHITE },
            new Font[]{quickSandFont}
    );

    // White Theme
    private static final Theme whiteTheme = new Theme(
            Color.WHITE,
            new Color[]{ Color.BLACK, Color.LIGHT_GRAY, Color.DARK_GRAY },
            new Color[]{ Color.BLACK } ,
            new Font[]{quickSandFont}
    );

    public static Theme currentTheme = whiteTheme;
    Mesh mesh;
    public SnowMemo(){
        window = new Window("Snow Memo");
        fileChooser = new FileChooser();
        sun = new DirectionalLight(
                window,
                new Vector3f(1f, -1f, 0.5f).normalize(), // Angled from the side
                new Vector4f(0.3f, 0.3f, 0.4f, 1.0f),
                new Vector4f(1.0f, 0.95f, 0.8f, 1.0f),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                2.5f
        );
        pointLight = new PointLight(
                window,
                new Vector3f(2, 2, 3),
                new Vector4f(0.3f, 0.3f, 0.3f, 1.0f),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                32f
        );
        try {
            shadowMap = new ShadowMap();
            shadowMap.setOrthoBounds(new ShadowMap.OrthoBounds(10f, 0.1f, 50f));
            shadowMap.setShadowDistance(20f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
//        renderable.add(new Mesh(Mesh.CUBE_POS_UV,Mesh.CUBE_INDICES,window,Texture.loadTexture(this.getClass().getResourceAsStream("2025-03-29T10:45:07.749198.png"))));
        renderable.add(new Baseplate());
        mesh = Utils.loadObj(this.getClass().getResourceAsStream("Cube.obj"));
        renderable.add(Utils.loadObj(this.getClass().getResourceAsStream("Cube.obj")));
        renderable.add(Utils.loadObj(this.getClass().getResourceAsStream("Cube.obj")).setPosition(new Vector3f(2f,0f,0f)));
        renderable.add(Utils.loadObj(this.getClass().getResourceAsStream("Cube.obj")).setPosition(new Vector3f(4f,0f,0f)));
        renderable.add(mesh);
        backButton = new GUIComponent(window,0.01f, 0.01f, 0.1f, 0.05f) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setStroke(new BasicStroke(5f));

                int w = getWidthPx();
                int h = getHeightPx();
                int arc = (int) (Math.min(w, h) * 0.25f);

                AffineTransform old = g2.getTransform();
                g2.rotate(Math.toRadians(180), w / 2.0, h / 2.0);
                g2.setColor(Color.decode("#bac3e0"));
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                g2.setTransform(old);

                String arrow = "➭";
                String label = "Back";

                Font quicksand;
                try {
                    quicksand = Font.createFont(Font.TRUETYPE_FONT,
                                    this.getClass().getResourceAsStream("QuicksandFont/Quicksand-VariableFont_wght.ttf"))
                            .deriveFont(Font.BOLD, h * 0.5f);
                } catch (Exception e) {
                    quicksand = g2.getFont().deriveFont(Font.BOLD, h * 0.5f);
                }

                Font arrowFont = g2.getFont().deriveFont(Font.BOLD, h ); // default font for arrow

                g2.setFont(quicksand);
                FontMetrics labelMetrics = g2.getFontMetrics();
                int textW = labelMetrics.stringWidth(label);
                int textAscent = labelMetrics.getAscent();
                int textDescent = labelMetrics.getDescent();

                g2.setFont(arrowFont);
                FontMetrics arrowMetrics = g2.getFontMetrics();
                int arrowW = arrowMetrics.stringWidth(arrow);
                int arrowAscent = arrowMetrics.getAscent();
                int arrowDescent = arrowMetrics.getDescent();

                int totalW = arrowW + textW + 10;
                if (totalW > w - 10) {
                    float scale = (w - 10f) / totalW;
                    arrowFont = arrowFont.deriveFont(Font.BOLD, arrowFont.getSize2D() * scale);
                    quicksand = quicksand.deriveFont(Font.BOLD, quicksand.getSize2D() * scale);
                    g2.setFont(arrowFont);
                    arrowMetrics = g2.getFontMetrics();
                    arrowW = arrowMetrics.stringWidth(arrow);
                    arrowAscent = arrowMetrics.getAscent();
                    arrowDescent = arrowMetrics.getDescent();
                    g2.setFont(quicksand);
                    labelMetrics = g2.getFontMetrics();
                    textW = labelMetrics.stringWidth(label);
                    textAscent = labelMetrics.getAscent();
                    textDescent = labelMetrics.getDescent();
                    totalW = arrowW + textW + 10;
                }

                int startX = (w - totalW) / 2;
                int centerY = h / 2;

                // Correct baseline for vertical centering
                int arrowBaseY = (int) ((((float) (h - (arrowAscent + arrowDescent)) / 2) + arrowAscent) * 1.3f);
                int labelBaseY = (h - (textAscent + textDescent)) / 2 + textAscent;

                // Shadow
                g2.setColor(new Color(0, 0, 0, 80));
                AffineTransform arrowOld = g2.getTransform();
                g2.translate(startX + arrowW / 2.0, arrowBaseY - arrowAscent / 2.0);
                g2.rotate(Math.toRadians(180));
                g2.setFont(arrowFont);
                g2.drawString(arrow, -arrowW / 2, arrowAscent / 2);
                g2.setTransform(arrowOld);

                g2.setFont(quicksand);
                g2.drawString(label, startX + arrowW + 10 + 2, labelBaseY + 2);

                // Foreground
                g2.setColor(Color.WHITE);
                arrowOld = g2.getTransform();
                g2.translate(startX + arrowW / 2.0, arrowBaseY - arrowAscent / 2.0);
                g2.rotate(Math.toRadians(180));
                g2.setFont(arrowFont);
                g2.drawString(arrow, -arrowW / 2, arrowAscent / 2);
                g2.setTransform(arrowOld);

                g2.setFont(quicksand);
                g2.drawString(label, startX + arrowW + 10, labelBaseY);
                g2.setColor(Color.WHITE);
                Arc2D cornerArc = new Arc2D.Float(getHeightPx()*0.1f, getHeightPx()*0.1f, arc, arc, 90,90, Arc2D.OPEN);
                g2.draw(cornerArc);
            }
            @Override
            public void init() {

            }
        };
        backButton.addCallBack(new MouseClickCallBack() {
            @Override
            public void onEvent(MouseClickEvent e) {
                System.out.println("fileChooser.chooseFile() = " + fileChooser.chooseFile());
            }
        });
        try {
            init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        run();
    }
    public void init() throws Exception{
        GLFWErrorCallback.createPrint(System.out).set();
        glfwSetCharCallback(window.window, (win, codepoint) -> {
            char c = (char) codepoint;
            if (Character.isISOControl(c)) return; // ignore backspace, etc. here
        });

        glfwSetKeyCallback(window.window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                System.out.println(key);
            }
        });
        renderable.forEach(mesh -> {
            try {
                mesh.init();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    public void update() {
        if (mouseWheelVelocity != 0) {
            float zoomSpeed = 0.5f;
            float desiredZoom = zoom + mouseWheelVelocity * zoomSpeed;
            desiredZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, desiredZoom));
            float delta = desiredZoom - zoom;
            if (delta != 0) {
                if (camera.cameraMovement == Camera.CameraMovement.ZoomInAndOut) {
                    camera.move(new Vector3f(0, 0, -delta)); // negative to zoom in
                } else if (camera.cameraMovement == Camera.CameraMovement.ScrollUpAndDown) {
                    camera.move(new Vector3f(0, delta, 0));
                }
                zoom = desiredZoom;
                System.out.println("zoom = " + zoom);
                perZoom = (zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM);
                System.out.println(perZoom);
            }
            mouseWheelVelocity = 0;
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
        glfwSetScrollCallback(window.window, new GLFWScrollCallback() {
            @Override public void invoke (long win, double dx, double dy) {
                mouseWheelVelocity = (float) dy;
            }
        });
        window.CursorPosCallbacks.add(new GLFWCursorPosCallback() {
            @Override
            public void invoke(long win, double xpos, double ypos) {
                if (glfwGetMouseButton(win, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS) {
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
                    mesh.setPosition(worldPos);
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

            // Adjust shadow bounds to capture your scene properly
            // Your objects span from y=-5 to y=0, x=0 to x=4
            shadowMap.setOrthoBounds(new ShadowMap.OrthoBounds(10f, 0.1f, 30f));
            shadowMap.setShadowDistance(15f);

            // Scene center should be in middle of your objects
            Vector3f sceneCenter = new Vector3f(2, -2.5f, 0); // Middle between baseplate and cubes

            boolean debug = Math.random() < 0.016;

            if (debug) {
                System.out.println("=== SHADOW DEBUG ===");
                System.out.println("Scene center: " + sceneCenter);
                System.out.println("Rendering " + meshes.size() + " meshes to shadow map");
                System.out.println("Light direction: " + sun.getDirection());
            }

            shadowMap.render(window, meshes, sceneCenter);
            if (shadowDebugDone) {
                testShadowMapVisibility();
                shadowDebugDone = true;
            }
            Matrix4f lightSpaceMatrix = shadowMap.getLightSpaceMatrix();

            // Set light space matrix for all meshes before rendering
            for (Mesh mesh : meshes) {
                try {
                    mesh.shaderProgram.bind();
                    if (!mesh.shaderProgram.hasUniform("lightSpaceMatrix")) {
                        mesh.shaderProgram.createUniform("lightSpaceMatrix");
                    }
                    mesh.shaderProgram.setUniform("lightSpaceMatrix", lightSpaceMatrix);
                    mesh.shaderProgram.unbind();
                } catch (Exception e) {
                    System.err.println("Error setting light space matrix: " + e.getMessage());
                }
            }

            window.render(renderable);
        }
        cleanUp();
        window.close();
    }
    public void cleanUp(){
        renderable.forEach(Renderable::cleanUp);
    }
    static boolean shadowDebugDone = true;

    public void testShadowMapVisibility() {
        System.out.println("=== SHADOW MAP TEST ===");

        // Bind the depth texture
        glBindFramebuffer(GL_FRAMEBUFFER, shadowMap.getDepthMapFBO());

        // Read center pixel
        float[] centerDepth = new float[1];
        glReadPixels(ShadowMap.SHADOW_MAP_WIDTH/2, ShadowMap.SHADOW_MAP_HEIGHT/2,
                1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, centerDepth);
        System.out.println("Center depth: " + centerDepth[0]);

        // Read sample of depths
        float[] depths = new float[25];
        glReadPixels(ShadowMap.SHADOW_MAP_WIDTH/2 - 2, ShadowMap.SHADOW_MAP_HEIGHT/2 - 2,
                5, 5, GL_DEPTH_COMPONENT, GL_FLOAT, depths);

        float minDepth = 1.0f, maxDepth = 0.0f;
        int nonOneCount = 0;
        for (float d : depths) {
            minDepth = Math.min(minDepth, d);
            maxDepth = Math.max(maxDepth, d);
            if (d < 0.999f) nonOneCount++;
        }

        System.out.println("Min depth: " + minDepth);
        System.out.println("Max depth: " + maxDepth);
        System.out.println("Non-1.0 pixels: " + nonOneCount + "/25");

        if (nonOneCount == 0) {
            System.out.println("WARNING: Shadow map appears empty (all depths = 1.0)");
            System.out.println("This means objects are not being rendered to shadow map");
        } else {
            System.out.println("SUCCESS: Shadow map contains geometry!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    public static void main(String[] args) {
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        System.setProperty("https.protocols", "TLSv1.2");
        String connectionString = "mongodb+srv://pohjunzhematthew:Inukacom1612@snowmemo.kyxexhg.mongodb.net/?retryWrites=true&w=majority&appName=SnowMemo";
        ServerApi serverApi = ServerApi.builder()
                .version(ServerApiVersion.V1)
                .build();
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .serverApi(serverApi)
                .build();
        try (MongoClient mongoClient = MongoClients.create(settings)) {
            try {
                MongoDatabase database = mongoClient.getDatabase("SnowMemo");
                database.runCommand(new Document("ping", 1));
                System.out.println("Pinged your deployment. You successfully connected to MongoDB!");
                MongoCollection<Document> collection = (MongoCollection<Document>) database.getCollection("Users");
                for (Document doc:collection.find()){
                    if (doc.containsKey("user")){
                        if (((Document)doc.get("user")).containsKey("userName")){
                            if (((Document)doc.get("user")).get("userName").equals("Admin")){
                                System.out.println(doc.toJson());
                            }
                        }
                    }
                }
                mongoClient.close();
            } catch (MongoException e) {
                e.printStackTrace();
            }
        }
        try {
            System.setProperty("apple.awt.application.name", "SnowMemo");
            System.setProperty("java.awt.headless", "true");
            new SnowMemo();
        }
        catch (OutOfMemoryError e){
            System.gc();
            System.out.println("Java OutOfMemoryError: Did you set ");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    public Vector3f getWorldCoordinatesFromScreen(double xpos, double ypos) {
        // --- 1) Get window size (logical window coords) and framebuffer size (physical pixels)
        int[] winW = new int[1], winH = new int[1];
        int[] fbW  = new int[1], fbH  = new int[1];
        glfwGetWindowSize(window.window, winW, winH);
        glfwGetFramebufferSize(window.window, fbW, fbH);

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
        Matrix4f projectionMatrix = window.getCurrentWindow().getProjectionMatrix();
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
}