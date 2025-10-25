package Main.Node;

import GUI.GUIComponent;
import Main.*;
import Main.Line3D.Line3D;
import Main.Window;
import com.mongodb.lang.NonNull;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.util.*;

public class Node extends Mesh implements Updatable {
    private static HashMap<Window,HashMap<Integer,Node>> nodes = new HashMap<Window,HashMap<Integer,Node>>();
    private static List<Node> selectedNodes = new ArrayList<Node>();
    private volatile float[] updatingVert = new float[0];
    private volatile boolean updating = false;
    private Vector3f preScale = new Vector3f(1,1,1);
    private boolean dirty = false;
    private final float[] originalVerts;
    private boolean selected = false;
    private List<Connector> connectors = new ArrayList<Connector>();
    private static final int moveKeyCode = GLFW.GLFW_KEY_G;
    private static final int deleteKeyCode = GLFW.GLFW_KEY_X;
    private static final int selectKeyCode = GLFW.GLFW_MOD_SHIFT;
    private static boolean moveKeyPressed, deleteKeyPressed = false;
    private static boolean mousePressed = false;
    private static boolean wasMousePressed = false;
    private List<Node> connectedNodes = new ArrayList<Node>(){
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
    private int mod;
    private static volatile boolean circleClickHandled = false;
    private static final Object clickLock = new Object();
    private static volatile boolean needsPreviewLineCreation = false;
    private static volatile Vector3f pendingPreviewStart = null;
    private static volatile boolean pendingIsDraggingFromOutput = false;
    private static volatile Node pendingPreviewSourceNode = null;
    private static volatile boolean needsPreviewLineCleanup = false;
    private static volatile boolean needsConnectionFinalization = false;
    private static volatile Node pendingConnectionTarget = null;

    static{
        registerNode(Node.class);
    }

    public Node(Window window){
        super(Utils.loadObj(SnowMemo.class.getResourceAsStream("Cube.obj"),window),window);
        Updatable.register(window,this);
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
                            for (Object guiComponent: GUIComponent.getGUIComponents(window)){
                                inGUI = ((GUIComponent)guiComponent).getHitBox().contains(window.getMouseX() *2, window.getMouseY() *2)&&((GUIComponent)guiComponent).isVisible();
                                if (inGUI) break;
                            }
                            if (inGUI) return;
                            for (Node n : nodes.get(window).values()) {
                                if (n.collideWithMouse()) {
                                    n.selected = true;
                                    if (!selectedNodes.contains(n)) {
                                        selectedNodes.add(n);
                                    }
                                }
                            }
                        }
                        default -> {
                            boolean inGUI = false;
                            for (Object guiComponent: Objects.requireNonNull(GUIComponent.getGUIComponents(window))){
                                inGUI = ((GUIComponent)guiComponent).getHitBox().contains(window.getMouseX() *2, window.getMouseY() *2)&&((GUIComponent)guiComponent).isVisible();
                                if (inGUI) break;
                            }
                            if (inGUI) return;
                            selectedNodes.forEach(node -> {node.selected = false;});
                            selectedNodes.clear();
                            for (Node n : nodes.get(window).values()) {
                                if (n.collideWithMouse()) {
                                    n.selected = true;
                                    selectedNodes.add(n);
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
        this.outputCircle.setPosition(new Vector3f(this.getScale().x+this.getPosition().x,this.getScale().y*0.75f+this.getPosition().y,this.getPosition().z+0.25f));
        this.inputCircle.setPosition(new Vector3f(this.getPosition().x-this.getScale().x,this.getScale().y*0.75f+this.getPosition().y,this.getPosition().z+0.25f));
    }

    @Override
    public void update() {
        move();
        if (scale.x != preScale.x || scale.y != preScale.y || scale.z != preScale.z) dirty = true;
        updateDirty();
        if (selected) {
            outlineColor = new Vector4f(0.5f,0.5f,0.5f,1);
            outlineThickness = 1.04f;
        } else {
            outlineThickness = 1.02f;
            outlineColor = new Vector4f(0,0,0, 1);
        }
        if (this.outputCircle!=null) {
            this.outputCircle.setPosition(new Vector3f(this.getScale().x + this.getPosition().x, this.getScale().y * 0.75f + this.getPosition().y, this.getPosition().z + 0.25f));
            this.inputCircle.setPosition(new Vector3f(this.getPosition().x - this.getScale().x, this.getScale().y * 0.75f + this.getPosition().y, this.getPosition().z + 0.25f));
        }

        // Update mouse events for all nodes to detect circle hovers
        updateMouseEvents();
    }

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
        preScale.set(scale);
        dirty = false;
        updating = true;
    }

    public void processMousePos (@NonNull Vector3f mousePos3D){
        Vector2f mousePos = mousePos3D.xy(new Vector2f());
    }

    @Override
    public void render(Camera camera){
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
                    boolean targetInputHovered = node.inputCircle != null && meshCollidesWith(node.inputCircle, mousePos);
                    boolean targetOutputHovered = node.outputCircle != null && meshCollidesWith(node.outputCircle, mousePos);

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

    private Vector3f mouseWorldOriginalPos = new Vector3f();
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
                boolean targetInputHovered = node.inputCircle != null && meshCollidesWith(node.inputCircle, mousePos);
                boolean targetOutputHovered = node.outputCircle != null && meshCollidesWith(node.outputCircle, mousePos);

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
        Vector3f pos = win.getCursorWorldPos();
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
    private Vector3f moveStartMousePos = new Vector3f();
    private HashMap<Node, Vector3f> originalNodePositions = new HashMap<>();

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

        HashMap<Integer, Node> windowNodes = nodes.get(win);
        if (windowNodes != null) windowNodes.remove(this.hashCode());

        Updatable.unregister(win, this);

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
}