package Main.Node;

import GUI.BillBoardGUI.BillboardGUI;
import GUI.GUIComponent;
import Main.*;
import Main.Line3D.Line3D;
import Main.Window;
import com.mongodb.lang.NonNull;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.json.JSONObject;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Node extends Mesh implements Updatable {
    private static final HashMap<Window,HashMap<Integer,Node>> nodes = new HashMap<>(){
        @Override
        public HashMap put(Window window, HashMap h){
            window.KeyCallbacks.add(new GLFWKeyCallback() {
                @Override
                public void invoke(long windowHandle, int key, int scancode, int action, int mods) {
                    mod = mods;
                    switch (key){
                        case moveKeyCode->{
                            if (action == GLFW.GLFW_PRESS) moveKeyPressed = true;
                            else if (action == GLFW.GLFW_RELEASE) moveKeyPressed = false;
                        }
                        case deleteKeyCode, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                            if (action == GLFW.GLFW_PRESS) {
                                deleteSelectedNodes(window);
                            }
                        }
                        default -> {}
                    }
                }
            });
            System.out.println("window.MouseButtonCallbacks = " + window.MouseButtonCallbacks);
            window.MouseButtonCallbacks.add(new GLFWMouseButtonCallback() {
                @Override
                public void invoke(long windowHandle, int button, int action, int mods) {
                    wasMousePressed = mousePressed;
                    mousePressed = action == GLFW.GLFW_PRESS;

                    // If a circle click was handled, don't process node selection
                    if (action == GLFW.GLFW_PRESS) {
                        synchronized (clickLock) {
                            if (circleClickHandled) {
                                circleClickHandled = false;
                                return;
                            }
                        }
                    }

                    // Don't process clicks while dragging preview line
                    if (previewLine3D != null) {
                        return;
                    }

                    switch(mods){
                        case selectKeyCode -> {
                            boolean inGUI = false;
                            for (Object guiComponent: Objects.requireNonNull(GUIComponent.getGUIComponents(window))){
                                inGUI = ((GUIComponent)guiComponent).getHitBox().contains(window.getMouseX(), window.getMouseY())&&((GUIComponent)guiComponent).isVisible();
                                if (inGUI) break;
                            }
                            if (inGUI) return;
                            nodeCustomizeGUI.getNodes().clear();
                            for (Node n : nodes.get(window).values()) {
                                if (n.collideWithMouse()) {
                                    n.selected = true;
                                    if (!selectedNodes.contains(n)) {
                                        selectedNodes.add(n);
                                        nodeCustomizeGUI.show();
                                    }
                                }
                            }
                            if (!selectedNodes.isEmpty()){
                                nodeCustomizeGUI.setNodes(new ArrayList<>(selectedNodes));
                                nodeCustomizeGUI.show();
                            }else nodeCustomizeGUI.hide();
                        }
                        default -> {
                            boolean inGUI = false;
                            for (Object guiComponent: Objects.requireNonNull(GUIComponent.getGUIComponents(window))){
                                inGUI = ((GUIComponent)guiComponent).getHitBox().contains(window.getMouseX(), window.getMouseY())&&((GUIComponent)guiComponent).isVisible();
                                if (inGUI) break;
                            }
                            if (inGUI) return;
                            selectedNodes.forEach(node -> {node.selected = false;});
                            selectedNodes.clear();
                            nodeCustomizeGUI.getNodes().clear();
                            nodeCustomizeGUI.hide();
                            for (Node n : nodes.get(window).values()) {
                                if (n.collideWithMouse()) {
                                    n.selected = true;
                                    selectedNodes.add(n);
                                    nodeCustomizeGUI.setNodes(new ArrayList<>(selectedNodes));
                                    nodeCustomizeGUI.show();
                                    break;
                                }
                            }
                        }
                    }
                }
            });
            return super.put(window,h);
        }
    };
    private static final List<Node> selectedNodes = new ArrayList<>();
    private volatile float[] updatingVert = new float[0];
    private volatile boolean updating = false;
    private final Vector3f preScale = new Vector3f(1,1,1);
    private boolean dirty = false;
    private final float[] originalVerts;
    private boolean selected = false;
    private final List<Connector> connectors = new ArrayList<>();
    private static final int moveKeyCode = GLFW.GLFW_KEY_G;
    private static final int deleteKeyCode = GLFW.GLFW_KEY_X;
    private static final int selectKeyCode = GLFW.GLFW_MOD_SHIFT;
    private static boolean moveKeyPressed;
    private static final boolean deleteKeyPressed = false;
    private static boolean mousePressed = false;
    private static boolean wasMousePressed = false;
    private final List<Node> connectedNodes = new ArrayList<Node>(){
        @Override
        public boolean add(Node node){
            handleAddingNode(node);
            return super.add(node);
        }
    };
    private static final List<Class<Node>> registeredNodes = new ArrayList<Class<Node>>();
    Circle inputCircle;
    Circle outputCircle;
    private static Line3D previewLine3D;
    private static Node previewLineSourceNode;
    private static boolean isDraggingFromOutput = false;
    private static int mod;
    private static volatile boolean circleClickHandled = false;
    private static final Object clickLock = new Object();
    private static volatile boolean needsPreviewLineCreation = false;
    private static volatile Vector3f pendingPreviewStart = null;
    private static volatile boolean pendingIsDraggingFromOutput = false;
    private static volatile Node pendingPreviewSourceNode = null;
    private static volatile boolean needsPreviewLineCleanup = false;
    private static volatile boolean needsConnectionFinalization = false;
    private static volatile Node pendingConnectionTarget = null;
    private Mesh nodeHeader;
    private BillboardGUI titleBillboard;
    private Vector4f headerBackgroundColor = new Vector4f(1f),bodyBackgroundColor = new Vector4f(1f);
    private String title = "";
    protected boolean allowUserTitleChange = true;
    private final String id = UUID.randomUUID().toString();
    static{
        registerNode(Node.class);
    }
    private static NodeCustomizeGUI nodeCustomizeGUI;
    static{
        nodeCustomizeGUI = new NodeCustomizeGUI();
        Window.getCurrentWindow().setImGuiToRender(nodeCustomizeGUI);
    }
    public Node(Window window){
        super(Utils.loadObj(Node.class.getResourceAsStream("NodeBody.obj"),window),window);

        Updatable.register(window,this);
        nodeHeader = Utils.loadObj(Node.class.getResourceAsStream("NodeHeader.obj"),window);
        nodeHeader.init();
        titleBillboard = new BillboardGUI(window, new GUIComponent(window, 512, 256) {
            String text = title;
            @Override
            protected void paintComponent(Graphics g) {
                text = title;
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
        originaltitleHeaderVerts = nodeHeader.getVertices();
        nodes.computeIfAbsent(window,w-> {
            window.KeyCallbacks.add(new GLFWKeyCallback() {
                @Override
                public void invoke(long windowHandle, int key, int scancode, int action, int mods) {
                    mod = mods;
                    switch (key){
                        case moveKeyCode->{
                            if (action == GLFW.GLFW_PRESS) moveKeyPressed = true;
                            else if (action == GLFW.GLFW_RELEASE) moveKeyPressed = false;
                        }
                        case deleteKeyCode, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                            if (action == GLFW.GLFW_PRESS) {
                                deleteSelectedNodes(window);
                            }
                        }
                        default -> {}
                    }
                }
            });
            System.out.println("window.MouseButtonCallbacks = " + window.MouseButtonCallbacks);
            window.MouseButtonCallbacks.add(new GLFWMouseButtonCallback() {
                @Override
                public void invoke(long windowHandle, int button, int action, int mods) {
                    wasMousePressed = mousePressed;
                    mousePressed = action == GLFW.GLFW_PRESS;

                    // If a circle click was handled, don't process node selection
                    if (action == GLFW.GLFW_PRESS) {
                        synchronized (clickLock) {
                            if (circleClickHandled) {
                                circleClickHandled = false;
                                return;
                            }
                        }
                    }

                    // Don't process clicks while dragging preview line
                    if (previewLine3D != null) {
                        return;
                    }

                    switch(mods){
                        case selectKeyCode -> {
                            boolean inGUI = false;
                            for (Object guiComponent: Objects.requireNonNull(GUIComponent.getGUIComponents(window))){
                                inGUI = ((GUIComponent)guiComponent).getHitBox().contains(window.getMouseX(), window.getMouseY())&&((GUIComponent)guiComponent).isVisible();
                                if (inGUI) break;
                            }
                            if (inGUI) return;
                            nodeCustomizeGUI.getNodes().clear();
                            for (Node n : nodes.get(window).values()) {
                                if (n.collideWithMouse()) {
                                    n.selected = true;
                                    if (!selectedNodes.contains(n)) {
                                        selectedNodes.add(n);
                                        nodeCustomizeGUI.show();
                                    }
                                }
                            }
                            if (!selectedNodes.isEmpty()){
                                nodeCustomizeGUI.setNodes(new ArrayList<>(selectedNodes));
                                nodeCustomizeGUI.show();
                            }else nodeCustomizeGUI.hide();
                        }
                        default -> {
                            boolean inGUI = false;
                            for (Object guiComponent: Objects.requireNonNull(GUIComponent.getGUIComponents(window))){
                                inGUI = ((GUIComponent)guiComponent).getHitBox().contains(window.getMouseX(), window.getMouseY())&&((GUIComponent)guiComponent).isVisible();
                                if (inGUI) break;
                            }
                            if (inGUI) return;
                            selectedNodes.forEach(node -> {node.selected = false;});
                            selectedNodes.clear();
                            nodeCustomizeGUI.getNodes().clear();
                            nodeCustomizeGUI.hide();
                            for (Node n : nodes.get(window).values()) {
                                if (n.collideWithMouse()) {
                                    n.selected = true;
                                    selectedNodes.add(n);
                                    nodeCustomizeGUI.setNodes(new ArrayList<>(selectedNodes));
                                    nodeCustomizeGUI.show();
                                    break;
                                }
                            }
                        }
                    }
                }
            });
            return new HashMap<Integer,Node>();
        });
        nodes.get(window).put(hashCode(),this);
        updatingVert = this.vertices.clone();
        originalVerts = this.vertices.clone();
        customScaling = true;
        outputCircle = new Circle(this, Circle.CircleType.OUTPUT,window);
        this.outputCircle.setScale(new Vector3f(0.25f));
        this.outputCircle.setRotation(new Vector3f((float) Math.PI/2, (float) Math.PI, (float) Math.PI));
        this.inputCircle = new Circle(this, Circle.CircleType.INPUT,window);
        this.outputCircle.setPosition(new Vector3f(this.getScale().x+this.getPosition().x,this.getScale().y*0.25f+this.getPosition().y,this.getPosition().z+0.25f));
        this.inputCircle.setPosition(new Vector3f(this.getPosition().x-this.getScale().x,this.getScale().y*0.25f+this.getPosition().y,this.getPosition().z+0.25f));
        titleBillboard.setRotation(new Vector3f((float) 0, (float) Math.PI, (float) Math.PI));
    }

    @Override
    public void update() {
        move();
        if (scale.x != preScale.x || scale.y != preScale.y || scale.z != preScale.z) dirty = true;
        updateDirty();
        if (selected) {
            outlineColor = new Vector4f(0f, 0f, 0f, 1f);            // subtle black rim
            outlineThickness = 1.02f;
        } else {
            outlineThickness = 1.0f;
        }


        if (this.outputCircle!=null) {
            this.outputCircle.setPosition(new Vector3f(this.getScale().x + this.getPosition().x, this.getScale().y * 0.25f + this.getPosition().y, this.getPosition().z + 0.25f));
        }
        if (this.inputCircle!=null){
            this.inputCircle.setPosition(new Vector3f(this.getPosition().x - this.getScale().x, this.getScale().y * 0.25f + this.getPosition().y, this.getPosition().z + 0.25f));
        }
        // Update mouse events for all nodes to detect circle hovers
        updateMouseEvents();
    }
    private float[] titleHeaderUpdatingVerts = new float[1];
    private float[] originaltitleHeaderVerts = new float[1];
    public void updateDirty(){
        if (!dirty) return;
        updatingVert = originalVerts.clone();
        for (int i = 0; i < originalVerts.length; i += 3){
            float x = originalVerts[i];
            float y = originalVerts[i+1];
            float z = originalVerts[i+2];
            if (z > 0) {
                updatingVert[i+2] = z + (scale.x - 1) / 2;
            } else {
                updatingVert[i+2] = z - (scale.x - 1) / 2;
            }
            if (x > 0) {
                updatingVert[i] = x + (scale.z - 1) / 2;
            } else {
                updatingVert[i] = x - (scale.z - 1) / 2;
            }
            if (y > 0) {
                updatingVert[i+1] = y + (scale.y - 1) / 2;
            } else {
                updatingVert[i+1] = y - (scale.y - 1) / 2;
            }
        }
        titleHeaderUpdatingVerts = originaltitleHeaderVerts.clone();
        Vector3f titleHeaderScale = new Vector3f(scale.sub(0,0f,0));
        for (int i = 0; i < originaltitleHeaderVerts.length; i += 3){
            float x = originaltitleHeaderVerts[i];
            float y = originaltitleHeaderVerts[i+1];
            float z = originaltitleHeaderVerts[i+2];
            if (z > 0) {
                titleHeaderUpdatingVerts[i+2] = z + (titleHeaderScale.x - 1) / 2;
            } else {
                titleHeaderUpdatingVerts[i+2] = z - (titleHeaderScale.x - 1) / 2;
            }
            if (x > 0) {
                titleHeaderUpdatingVerts[i] = x + (titleHeaderScale.z - 1) / 2;
            } else {
                titleHeaderUpdatingVerts[i] = x - (titleHeaderScale.z - 1) / 2;
            }
//            if (y > 0) {
//                titleHeaderUpdatingVerts[i+1] = y + (titleHeaderScale.y - 1) / 2;
//            } else {
//                titleHeaderUpdatingVerts[i+1] = y - (titleHeaderScale.y - 1) / 2;
//            }
        }
        preScale.set(scale);
        dirty = false;
        updating = true;
    }

    public void processMousePos (@NonNull Vector3f mousePos3D){
        Vector2f mousePos = mousePos3D.xy(new Vector2f());
    }

    @Override
    public void render(Camera camera){

        nodeHeader.setPosition(new Vector3f(getPosition()).add(0,0,0f));
        nodeHeader.setMaterial(nodeHeader.getMaterial().setAmbient(headerBackgroundColor).setDiffuse(headerBackgroundColor));
        setMaterial(getMaterial().setAmbient(bodyBackgroundColor).setDiffuse(bodyBackgroundColor));
        titleBillboard.setPosition(new Vector3f(nodeHeader.getPosition()).add(0,1.05f,0.2675f));
        titleBillboard.render(camera);
        nodeHeader.render(camera);
        nodeHeader.outline = outline;
        nodeHeader.outlineThickness = outlineThickness;
        nodeHeader.outlineColor = outlineColor;
        // Handle pending preview line creation (must be on render thread for OpenGL)
        if (needsPreviewLineCreation && pendingPreviewSourceNode == this) {
            synchronized (clickLock) {
                if (needsPreviewLineCreation && pendingPreviewSourceNode == this) {
                    previewLine3D = new Line3D(pendingPreviewStart, win.getCursorWorldPos(), win);
                    previewLineSourceNode = pendingPreviewSourceNode;
                    isDraggingFromOutput = pendingIsDraggingFromOutput;
                    SnowMemo.draggable = false;
                    needsPreviewLineCreation = false;
                    pendingPreviewSourceNode = null;
                    pendingPreviewStart = null;
                }
            }
        }

        // Handle pending preview line cleanup (must be on render thread for OpenGL)
        if (needsPreviewLineCleanup && previewLineSourceNode == this) {
            synchronized (clickLock) {
                if (needsPreviewLineCleanup && previewLineSourceNode == this) {
                    if (previewLine3D != null) {
                        previewLine3D.cleanUp();
                        previewLine3D = null;
                    }

                    // Finalize connection if there was a target
                    if (needsConnectionFinalization && pendingConnectionTarget != null) {
                        if (!connectedNodes.contains(pendingConnectionTarget)) {
                            System.out.println("EE");
                            connect(pendingConnectionTarget);
                        }
                        pendingConnectionTarget = null;
                        needsConnectionFinalization = false;
                    }

                    previewLineSourceNode = null;
                    isDraggingFromOutput = false;
                    SnowMemo.draggable = true;
                    needsPreviewLineCleanup = false;
                }
            }
        }

        if (updating){
            updateVertices(updatingVert);
            nodeHeader.updateVertices(titleHeaderUpdatingVerts);
            updating = false;
        }
        super.render(camera);
        for (Connector connector : connectors) {
            connector.render(camera);
        }
        if (inputCircle!=null) {
            inputCircle.render(camera);
        }
        if (outputCircle!=null) {
            outputCircle.render(camera);
        }
        if (previewLine3D != null && previewLineSourceNode == this) {
            previewLine3D.setVisible(true);
            Vector3f endPos = win.getCursorWorldPos();
            Vector3f mousePos = win.getCursorWorldPos();
            boolean snappedToCircle = false;

            for (Node node:getAllCurrentlyRenderedNodes(win)){
                if(node.isRenderVisible()&&node!=this) {
                    // Check if hovering over valid connection target
                    boolean targetInputHovered = node.inputCircle != null && meshCollidesWith(node.inputCircle, win.getCursorWorldPosAtZ(node.inputCircle.getPosition().z));
                    boolean targetOutputHovered = node.outputCircle != null && meshCollidesWith(node.outputCircle, win.getCursorWorldPosAtZ(node.outputCircle.getPosition().z));

                    // Check if connection is valid and not already connected
                    boolean validConnection = false;
                    Vector3f snapPosition = null;

                    if (isDraggingFromOutput && targetInputHovered) {
                        validConnection = true;
                        snapPosition = node.inputCircle.getPosition();
                    } else if (!isDraggingFromOutput && targetOutputHovered) {
                        validConnection = true;
                        snapPosition = node.outputCircle.getPosition();
                    }

                    // Only snap if valid connection and not already connected
                    if (validConnection && snapPosition != null && !connectedNodes.contains(node) && !node.connectedNodes.contains(this)) {
                        previewLine3D.getEndPos().set(snapPosition);
                        snappedToCircle = true;
                        break;
                    }
                }
            }

            // If not snapped to a valid circle, follow mouse
            if (!snappedToCircle) {
                previewLine3D.getEndPos().set(endPos);
            }

            previewLine3D.render(camera);
        }
    }

    private void handleAddingNode(Node node){
        Connector connector = new Connector(this,node,win);
        connectors.add(connector);
    }

    public void connect(Node node){
        System.out.println("E");
        if (!connectedNodes.contains(node)) {
            connectedNodes.add(node);
        }
    }

    public void disconnect(Node node){
        connectedNodes.remove(node);
        List<Connector> toRemove = new ArrayList<>();
        for (Connector connector : connectors) {
            if ((connector.getParentNode() == this && connector.getChildNode() == node) ||
                    (connector.getParentNode() == node && connector.getChildNode() == this)) {
                toRemove.add(connector);
            }
        }
        connectors.removeAll(toRemove);
    }

    private final Vector3f mouseWorldOriginalPos = new Vector3f();
    private boolean inputCircleHovered = false;
    private boolean outputCircleHovered = false;

    private void updateMouseEvents() {
        Vector3f mousePos = win.getCursorWorldPos();

        // Update hover states using Circle's own collision detection
        inputCircleHovered = inputCircle != null && inputCircle.collideWithMouse();
        outputCircleHovered = outputCircle != null && outputCircle.collideWithMouse();

        // Handle mouse press on circles (priority over node selection)
        if (mousePressed && !wasMousePressed) {
            // Check output circle first (give it priority if both overlap)
            if (outputCircleHovered && previewLine3D == null && !needsPreviewLineCreation) {
                synchronized (clickLock) {
                    // Queue preview line creation for render thread
                    pendingPreviewStart = new Vector3f(outputCircle.getPosition());
                    pendingIsDraggingFromOutput = true; // Dragging FROM output
                    pendingPreviewSourceNode = this;
                    needsPreviewLineCreation = true;
                    circleClickHandled = true;
                }
                return;
            } else if (inputCircleHovered && previewLine3D == null && !needsPreviewLineCreation) {
                synchronized (clickLock) {
                    // Queue preview line creation for render thread
                    pendingPreviewStart = new Vector3f(inputCircle.getPosition());
                    pendingIsDraggingFromOutput = false; // Dragging FROM input
                    pendingPreviewSourceNode = this;
                    needsPreviewLineCreation = true;
                    circleClickHandled = true;
                }
                return;
            }
        }

        // Handle mouse release to complete connection
        if (!mousePressed && wasMousePressed) {
            if (previewLine3D != null && previewLineSourceNode == this) {
                onCircleRelease();
            }
        }
    }

    private void onInputCircleClick() {
        // This method is now obsolete - logic moved to updateMouseEvents
    }

    private void onOutputCircleClick() {
        // This method is now obsolete - logic moved to updateMouseEvents
    }

    private void onCircleRelease(){
        if (previewLine3D != null && previewLineSourceNode != null) {
            Node targetNode = null;
            Vector3f mousePos = win.getCursorWorldPos();

            // Find the target node to connect to
            for (Node node : getAllCurrentlyRenderedNodes(win)) {
                if (node == previewLineSourceNode) continue;

                // Re-check hover states at release time to ensure accuracy
                boolean targetInputHovered = node.inputCircle != null && meshCollidesWith(node.inputCircle, win.getCursorWorldPosAtZ(node.inputCircle.getPosition().z));
                boolean targetOutputHovered = node.outputCircle != null && meshCollidesWith(node.outputCircle, win.getCursorWorldPosAtZ(node.outputCircle.getPosition().z));

                // Validate connection rules:
                // - If dragging from output, can only connect to input
                // - If dragging from input, can only connect to output
                // - Cannot connect if already connected (bidirectional check)
                boolean validConnection = false;
                if (isDraggingFromOutput && targetInputHovered) {
                    validConnection = true; // Output -> Input is valid
                } else if (!isDraggingFromOutput && targetOutputHovered) {
                    validConnection = true; // Input -> Output is valid
                }

                // Check if not already connected (check both directions)
                boolean alreadyConnected = previewLineSourceNode.connectedNodes.contains(node) ||
                        node.connectedNodes.contains(previewLineSourceNode);

                if (validConnection && !alreadyConnected) {
                    targetNode = node;
                    break;
                }
            }

            // Queue cleanup and connection for render thread
            synchronized (clickLock) {
                pendingConnectionTarget = targetNode;
                needsConnectionFinalization = (targetNode != null);
                needsPreviewLineCleanup = true;
            }
        }
    }

    private boolean collideWithMouse(){
        if (!renderVisible) return false;
        Vector3f pos = win.getCursorWorldPosAtZ(getPosition().z);
        return collidesWith(pos);
    }

    public boolean collidesWith(Vector3f worldPoint) {
        float[] verts = getVertices();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        getAABBFromVerts(verts, min, max);
        return worldPoint.x >= min.x && worldPoint.x <= max.x &&
                worldPoint.y >= min.y && worldPoint.y <= max.y &&
                worldPoint.z >= min.z && worldPoint.z <= max.z;
    }

    private void getAABBFromVerts(float[] verts, Vector3f min, Vector3f max) {
        min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (int i = 0; i < verts.length; i += 3) {
            float x = verts[i];
            float y = verts[i + 1];
            float z = verts[i + 2];
            x = position.x + x * scale.x;
            y = position.y + y * scale.y;
            z = position.z + z * scale.z;
            if (x < min.x) min.x = x;
            if (y < min.y) min.y = y;
            if (z < min.z) min.z = z;
            if (x > max.x) max.x = x;
            if (y > max.y) max.y = y;
            if (z > max.z) max.z = z;
        }
    }

    private static void deleteSelectedNodes(Window window) {
        if (selectedNodes.isEmpty()) return;
        HashMap<Integer, Node> windowNodes = nodes.get(window);
        if (windowNodes == null) return;
        List<Node> toDelete = new ArrayList<>(selectedNodes);

        for (Node node : toDelete) {
            for (Node otherNode : new ArrayList<>(windowNodes.values())) {
                if (otherNode != node) {
                    otherNode.disconnect(node);
                }
            }

            windowNodes.remove(node.hashCode());
            Updatable.unregister(window, node);
            try {
                node.cleanUp();
                if (node.outputCircle != null) node.outputCircle.cleanUp();
                if (node.inputCircle != null) node.inputCircle.cleanUp();
                for (Connector connector : new ArrayList<>(node.connectors)) {
                    if (connector != null) connector.cleanUp();
                }
            } catch (Exception e) {
                System.err.println("Error cleaning up node: " + e.getMessage());
            }
        }
        selectedNodes.clear();
    }

    private boolean moving = false;
    private final Vector3f moveStartMousePos = new Vector3f();
    private final HashMap<Node, Vector3f> originalNodePositions = new HashMap<>();

    private void move() {
        if (!selected) return;
        if (moveKeyPressed && !moving) {
            moving = true;
            moveStartMousePos.set(win.getCursorWorldPos());
            originalNodePositions.clear();
            for (Node n : selectedNodes) originalNodePositions.put(n, new Vector3f(n.getPosition()));
        }
        if (moving) {
            Vector3f currentMousePos = win.getCursorWorldPos();
            Vector3f delta = new Vector3f(currentMousePos).sub(moveStartMousePos);
            for (Node n : selectedNodes) {
                if (mod == GLFW.GLFW_MOD_SHIFT) {
                    n.setPosition(clamp(new Vector3f(originalNodePositions.get(n)).add(delta)));
                } else {
                    n.setPosition(new Vector3f(originalNodePositions.get(n)).add(delta));
                }
            }
        }
        if (!moveKeyPressed && moving) {
            moving = false;
            originalNodePositions.clear();
        }
    }
    @Override
    public void cleanUp() {
        selectedNodes.remove(this);
        if (selectedNodes.isEmpty()) nodeCustomizeGUI.hide();
        nodeHeader.cleanUp();
        HashMap<Integer, Node> windowNodes = nodes.get(win);
        if (windowNodes != null) windowNodes.remove(this.hashCode());

        Updatable.unregister(win, this);
        titleBillboard.cleanUp();
        for (Node other : new ArrayList<>(connectedNodes)) {
            other.disconnect(this);
        }
        connectedNodes.clear();

        for (Connector c : new ArrayList<>(connectors)) {
            try { c.cleanUp(); } catch (Exception ignored) {}
        }
        connectors.clear();

        if (inputCircle != null) {
            try { inputCircle.cleanUp(); } catch (Exception ignored) {}
            inputCircle = null;
        }

        if (outputCircle != null) {
            try { outputCircle.cleanUp(); } catch (Exception ignored) {}
            outputCircle = null;
        }

        synchronized (clickLock) {
            if (previewLineSourceNode == this) {
                if (previewLine3D != null) {
                    try { previewLine3D.cleanUp(); } catch (Exception ignored) {}
                    previewLine3D = null;
                }
                previewLineSourceNode = null;
                pendingPreviewSourceNode = null;
                pendingPreviewStart = null;
                isDraggingFromOutput = false;
                needsPreviewLineCreation = false;
                needsPreviewLineCleanup = false;
                needsConnectionFinalization = false;
                pendingConnectionTarget = null;
                SnowMemo.draggable = true;
            }
        }

        super.cleanUp();
    }

    public static void registerNode(Class<Node> node){
        registeredNodes.add(node);
    }

    public static List<Class<Node>> getAllRegisteredNodes(){
        return registeredNodes;
    }

    public static List<Node> getAllNodes(Window w){
        if (!nodes.containsKey(w)){
            nodes.put(w,new HashMap<>());
        }
        return new ArrayList<>(nodes.get(w).values());
    }

    public Vector3f clamp(Vector3f vector3f){
        return vector3f.set(Math.round(vector3f.x),Math.round(vector3f.y),Math.round(vector3f.z));
    }

    private boolean meshCollidesWith(Mesh mesh, Vector3f worldPoint){
        float[] verts = mesh.getVertices();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        getAABBFromVertsMesh(mesh, verts, min, max);
        return worldPoint.x >= min.x && worldPoint.x <= max.x &&
                worldPoint.y >= min.y && worldPoint.y <= max.y &&
                worldPoint.z >= min.z && worldPoint.z <= max.z;
    }

    private void getAABBFromVertsMesh(Mesh mesh, float[] verts, Vector3f min, Vector3f max){
        min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (int i = 0; i < verts.length; i += 3) {
            float x = mesh.getPosition().x + verts[i] * mesh.getScale().x;
            float y = mesh.getPosition().y + verts[i + 1] * mesh.getScale().y;
            float z = mesh.getPosition().z + verts[i + 2] * mesh.getScale().z;
            if (x < min.x) min.x = x;
            if (y < min.y) min.y = y;
            if (z < min.z) min.z = z;
            if (x > max.x) max.x = x;
            if (y > max.y) max.y = y;
            if (z > max.z) max.z = z;
        }
    }

    private static List<Node> getAllCurrentlyRenderedNodes(Window window){
        return new ArrayList<Node>(nodes.get(window).values()).stream().filter(node -> node.renderVisible).toList();
    }

    public List<Node> getConnectedNodes() {
        return new ArrayList<>(connectedNodes);
    }

    public List<Connector> getConnectors() {
        return new ArrayList<>(connectors);
    }

    private static final float MIN_PREVIEW_LINE_LENGTH = 0.5f;

    private static Vector3f clampPreviewLineEnd(Vector3f startPos, Vector3f endPos) {
        Vector3f direction = new Vector3f(endPos).sub(startPos);
        float distance = direction.length();

        if (distance < MIN_PREVIEW_LINE_LENGTH) {
            direction.normalize();
            return new Vector3f(startPos).add(direction.mul(MIN_PREVIEW_LINE_LENGTH));
        }

        return endPos;
    }

    public Vector4f getHeaderBackgroundColor() {
        return headerBackgroundColor;
    }

    public Node setHeaderBackgroundColor(Vector4f headerBackgroundColor) {
        this.headerBackgroundColor = headerBackgroundColor;
        return this;
    }

    public Vector4f getBodyBackgroundColor() {
        return bodyBackgroundColor;
    }

    public Node setBodyBackgroundColor(Vector4f bodyBackgroundColor) {
        this.bodyBackgroundColor = bodyBackgroundColor;
        return this;
    }
    public String getId() {
        return id;
    }

    // 4. REPLACE the entire toJson() method with this:
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("id", id); // Add unique ID
        obj.put("title", title);
        obj.put("position", positionToJson(position));
        obj.put("scale", scaleToJson(scale));

        // Serialize connected node references by ID instead of hashCode
        List<String> connections = new ArrayList<>();
        for (Node n : connectedNodes) {
            connections.add(n.getId());
        }
        obj.put("connections", connections);

        // Add header and body colors
        obj.put("headerBackgroundColor", colorToJson(headerBackgroundColor));
        obj.put("bodyBackgroundColor", colorToJson(bodyBackgroundColor));

        return obj;
    }

    // 5. REPLACE the entire fromJson() method with this:
    public static Node fromJson(JSONObject obj) {
        Node node = new Node(Window.getCurrentWindow());
        node.title = obj.optString("title", "Node");
        node.position = positionFromJson(obj.getJSONObject("position"));

        // Load scale if present
        if (obj.has("scale")) {
            node.scale = scaleFromJson(obj.getJSONObject("scale"));
            node.dirty = true; // Mark dirty to trigger vertex update
        }

        // Load colors if present
        if (obj.has("headerBackgroundColor")) {
            node.headerBackgroundColor = colorFromJson(obj.getJSONObject("headerBackgroundColor"));
        }
        if (obj.has("bodyBackgroundColor")) {
            node.bodyBackgroundColor = colorFromJson(obj.getJSONObject("bodyBackgroundColor"));
        }

        // Connections will be restored later after all nodes are loaded
        return node;
    }

    // 6. ADD THESE HELPER METHODS (add them near the positionToJson/positionFromJson methods):
    private static JSONObject colorToJson(Vector4f color) {
        JSONObject obj = new JSONObject();
        obj.put("r", color.x);
        obj.put("g", color.y);
        obj.put("b", color.z);
        obj.put("a", color.w);
        return obj;
    }

    private static Vector4f colorFromJson(JSONObject obj) {
        return new Vector4f(
                obj.getFloat("r"),
                obj.getFloat("g"),
                obj.getFloat("b"),
                obj.getFloat("a")
        );
    }

    private static JSONObject positionToJson(Vector3f pos) {
        JSONObject obj = new JSONObject();
        obj.put("x", pos.x);
        obj.put("y", pos.y);
        obj.put("z", pos.z);
        return obj;
    }

    private static JSONObject scaleToJson(Vector3f scale) {
        JSONObject obj = new JSONObject();
        obj.put("x", scale.x);
        obj.put("y", scale.y);
        obj.put("z", scale.z);
        return obj;
    }

    private static Vector3f scaleFromJson(JSONObject obj) {
        return new Vector3f(
                obj.getFloat("x"),
                obj.getFloat("y"),
                obj.getFloat("z")
        );
    }
    private static Vector3f positionFromJson(JSONObject obj) {
        return new Vector3f(
                obj.getFloat("x"),
                obj.getFloat("y"),
                obj.getFloat("z")
        );
    }

    public String getTitle() {
        return title;
    }

    public Node setTitle(String title) {
        this.title = title;
        return this;
    }

    public boolean isAllowUserTitleChange() {
        return allowUserTitleChange;
    }

    public Node setAllowUserTitleChange(boolean allowUserTitleChange) {
        this.allowUserTitleChange = allowUserTitleChange;
        return this;
    }
}