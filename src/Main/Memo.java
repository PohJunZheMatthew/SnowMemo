package Main;

import GUI.BillBoardGUI.BillboardGUI;
import GUI.GUIComponent;
import Main.Node.Node;
import imgui.*;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import org.joml.Vector3f;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Memo {
    private static final List<Memo> memoList = new ArrayList<>();
    private static MemoImGUITitle currentMemoGUITitle = null;
    private static Memo currentMemo = null;
    private static Runnable returnFunc;
    private static Consumer<String> changeNameFunc;

    // Save system constants
    private static final String SAVES_FOLDER = "memoSaves";
    private static final String BACKUPS_FOLDER = "memoSaves/backups";
    private static final String TEMP_EXTENSION = ".tmp";
    private static final int MAX_BACKUPS = 5;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private BillboardGUI mainMemoBillboard;
    private List<Node> nodes = new ArrayList<>();
    private boolean availableOnline = false;
    private String name;
    private String jsonData;
    private LocalDateTime lastModified;
    private LocalDateTime created;
    private Map<String, Object> metadata = new HashMap<>();
    private boolean isDirty = false; // Track unsaved changes
    private String description = "";
    private List<String> tags = new ArrayList<>();

    private boolean isLoaded = false; // Track if memo nodes are loaded
    private Window window;

    public Memo(String name, String jsonData, Window window) {
        this.name = name;
        this.jsonData = jsonData;
        this.window = window;
        this.created = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
        initMainBillboard(window);
        memoImGUITitle.memoName = new ImString(name);
    }

    // Enhanced save with atomic writes and backups
    public void save(boolean uploadOnline) {
        try {
            // Update nodes list from currently rendered nodes before saving
            if (isLoaded && window != null) {
                syncNodesFromWindow();
            }

            saveWithBackup();
            this.isDirty = false;
            this.lastModified = LocalDateTime.now();

            if (uploadOnline) {
                uploadToOnline(buildJsonObject());
            }

            System.out.println("Saved memo: " + name + " with " + nodes.size() + " nodes");
        } catch (IOException e) {
            System.err.println("Failed to save memo: " + name);
            e.printStackTrace();
        }
    }

    // Sync nodes from the window's current rendered nodes
    private void syncNodesFromWindow() {
        if (window == null) return;

        List<Node> currentNodes = Node.getAllNodes(window);
        if (currentNodes != null) {
            this.nodes = new ArrayList<>(currentNodes);
            System.out.println("Synced " + nodes.size() + " nodes from window for memo: " + name);
        }
    }

    // Quick save - saves current state of memo
    public void quickSave() {
        save(false);
    }

    // Manual trigger for auto-save (called by SnowMemo)
    public void performAutoSave() {
        if (isDirty || isLoaded) {
            save(false);
        }
    }

    // Atomic save operation with backup
    private void saveWithBackup() throws IOException {
        File savesFolder = new File(SAVES_FOLDER);
        if (!savesFolder.exists()) {
            savesFolder.mkdirs();
        }

        File backupsFolder = new File(BACKUPS_FOLDER);
        if (!backupsFolder.exists()) {
            backupsFolder.mkdirs();
        }

        File targetFile = new File(savesFolder, name + ".json");
        File tempFile = new File(savesFolder, name + TEMP_EXTENSION);

        // Create backup of existing file if it exists
        if (targetFile.exists()) {
            createBackup(targetFile);
        }

        // Write to temp file first
        JSONObject memoJson = buildJsonObject();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(memoJson.toString(4));
        }

        // Atomic rename
        Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Cleanup old backups
        cleanupOldBackups();
    }

    // Create timestamped backup
    private void createBackup(File originalFile) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String backupName = name + "_backup_" + timestamp + ".json";
        File backupFile = new File(BACKUPS_FOLDER, backupName);
        Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // Remove old backups, keeping only MAX_BACKUPS most recent
    private void cleanupOldBackups() {
        File backupsFolder = new File(BACKUPS_FOLDER);
        File[] backups = backupsFolder.listFiles((dir, fileName) ->
                fileName.startsWith(name + "_backup_") && fileName.endsWith(".json"));

        if (backups != null && backups.length > MAX_BACKUPS) {
            // Sort by last modified
            java.util.Arrays.sort(backups, (a, b) ->
                    Long.compare(a.lastModified(), b.lastModified()));

            // Delete oldest backups
            for (int i = 0; i < backups.length - MAX_BACKUPS; i++) {
                backups[i].delete();
            }
        }
    }

    // Build complete JSON object
    private JSONObject buildJsonObject() {
        JSONObject memoJson = new JSONObject();
        memoJson.put("version", "1.0"); // Add versioning for future compatibility
        memoJson.put("name", name);
        memoJson.put("description", description);
        memoJson.put("availableOnline", availableOnline);
        memoJson.put("created", created.toString());
        memoJson.put("lastModified", lastModified.toString());

        // Save tags
        JSONArray tagsArray = new JSONArray(tags);
        memoJson.put("tags", tagsArray);

        // Save nodes
        JSONArray nodesArray = new JSONArray();
        for (Node n : nodes) {
            nodesArray.put(n.toJson());
        }
        memoJson.put("nodes", nodesArray);

        // Save metadata (extensible)
        JSONObject metadataJson = new JSONObject(metadata);
        memoJson.put("metadata", metadataJson);

        return memoJson;
    }

    // Auto-save functionality
    public void autoSave() {
        if (isDirty) {
            save(false);
        }
    }

    // Load memos with error recovery
    public static List<Memo> loadMemos(Window window) {
        List<Memo> memos = new ArrayList<>();
        File folder = new File(SAVES_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] files = folder.listFiles((dir, name) ->
                name.endsWith(".json") && !name.endsWith(TEMP_EXTENSION));

        if (files == null) return memos;

        for (File file : files) {
            try {
                Memo memo = loadMemoFromFile(file, window);
                if (memo != null) {
                    memos.add(memo);
                }
            } catch (Exception e) {
                System.err.println("Failed to load memo: " + file.getName());
                e.printStackTrace();

                // Try to recover from backup
                Memo recoveredMemo = tryRecoverFromBackup(file, window);
                if (recoveredMemo != null) {
                    memos.add(recoveredMemo);
                    System.out.println("Recovered memo from backup: " + file.getName());
                }
            }
        }

        memoList.clear();
        memoList.addAll(memos);
        return memos;
    }

    // Load individual memo from file
    private static Memo loadMemoFromFile(File file, Window window) throws IOException {
        String content = Files.readString(file.toPath());
        JSONObject memoJson = new JSONObject(content);

        String memoName = memoJson.getString("name");
        Memo memo = new Memo(memoName, content, window);

        // Load basic fields
        memo.setAvailableOnline(memoJson.optBoolean("availableOnline", false));
        memo.setDescription(memoJson.optString("description", ""));

        // Load timestamps
        if (memoJson.has("created")) {
            memo.created = LocalDateTime.parse(memoJson.getString("created"));
        }
        if (memoJson.has("lastModified")) {
            memo.lastModified = LocalDateTime.parse(memoJson.getString("lastModified"));
        }

        // Load tags
        if (memoJson.has("tags")) {
            JSONArray tagsArray = memoJson.getJSONArray("tags");
            for (int i = 0; i < tagsArray.length(); i++) {
                memo.tags.add(tagsArray.getString(i));
            }
        }

        // Store node data but DON'T create nodes yet (lazy loading)
        memo.jsonData = content;

        // Load metadata (extensible)
        if (memoJson.has("metadata")) {
            JSONObject metadataJson = memoJson.getJSONObject("metadata");
            for (String key : metadataJson.keySet()) {
                memo.metadata.put(key, metadataJson.get(key));
            }
        }

        memo.isDirty = false;
        memo.isLoaded = false; // Mark as not loaded yet
        return memo;
    }

    // Try to recover from most recent backup
    private static Memo tryRecoverFromBackup(File originalFile, Window window) {
        String memoName = originalFile.getName().replace(".json", "");
        File backupsFolder = new File(BACKUPS_FOLDER);

        File[] backups = backupsFolder.listFiles((dir, fileName) ->
                fileName.startsWith(memoName + "_backup_") && fileName.endsWith(".json"));

        if (backups != null && backups.length > 0) {
            // Sort by last modified, newest first
            java.util.Arrays.sort(backups, (a, b) ->
                    Long.compare(b.lastModified(), a.lastModified()));

            // Try to load most recent backup
            try {
                return loadMemoFromFile(backups[0], window);
            } catch (Exception e) {
                System.err.println("Failed to recover from backup: " + backups[0].getName());
            }
        }

        return null;
    }

    // Export memo to specific location
    public void exportTo(File destination) throws IOException {
        JSONObject memoJson = buildJsonObject();
        try (FileWriter writer = new FileWriter(destination)) {
            writer.write(memoJson.toString(4));
        }
    }

    // Import memo from file
    public static Memo importFrom(File source, Window window) throws IOException {
        return loadMemoFromFile(source, window);
    }

    // Load nodes from JSON data - called when memo is clicked
    public void load() {
        if (isLoaded) {
            System.out.println("Memo already loaded: " + name);
            return; // Already loaded
        }

        try {
            JSONObject memoJson = new JSONObject(jsonData);

            // Load nodes
            JSONArray nodesArray = memoJson.getJSONArray("nodes");
            List<Node> loadedNodes = new ArrayList<>();
            Map<String, Node> nodeMap = new HashMap<>(); // Changed to String for UUID

            // First pass: create all nodes
            for (int i = 0; i < nodesArray.length(); i++) {
                JSONObject nodeJson = nodesArray.getJSONObject(i);
                Node node = Node.fromJson(nodeJson);
                loadedNodes.add(node);
                // Use the node's ID from JSON
                String nodeId = nodeJson.optString("id", node.getId());
                nodeMap.put(nodeId, node);
            }

            // Second pass: restore connections
            for (int i = 0; i < nodesArray.length(); i++) {
                JSONObject nodeJson = nodesArray.getJSONObject(i);
                if (nodeJson.has("connections")) {
                    JSONArray connections = nodeJson.getJSONArray("connections");
                    Node sourceNode = loadedNodes.get(i);

                    for (int j = 0; j < connections.length(); j++) {
                        String targetId = connections.getString(j); // Changed to String
                        Node targetNode = nodeMap.get(targetId);
                        if (targetNode != null) {
                            sourceNode.connect(targetNode);
                        }
                    }
                }
            }

            this.nodes = loadedNodes;
            this.isLoaded = true;
            onAfterLoad(); // Call extension hook

            System.out.println("Loaded memo: " + name + " with " + nodes.size() + " nodes");
        } catch (Exception e) {
            System.err.println("Failed to load memo nodes: " + name);
            e.printStackTrace();
        }
    }

    // Unload nodes to free memory - called when returning to menu
    public void unload() {
        if (!isLoaded) {
            return; // Already unloaded
        }

        // Save before unloading to preserve changes
        if (isDirty) {
            save(false);
        }

        // Clean up all nodes
        for (Node node : new ArrayList<>(nodes)) {
            try {
                node.cleanUp();
            } catch (Exception e) {
                System.err.println("Error cleaning up node during unload: " + e.getMessage());
            }
        }

        nodes.clear();
        isLoaded = false;
        System.out.println("Unloaded memo: " + name);
    }

    public boolean isLoaded() {
        return isLoaded;
    }
    public void markDirty() {
        this.isDirty = true;
    }

    // Getters and setters
    public boolean isDirty() {
        return isDirty;
    }

    public String getDescription() {
        return description;
    }

    public Memo setDescription(String description) {
        this.description = description;
        markDirty();
        return this;
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public Memo addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
            markDirty();
        }
        return this;
    }

    public Memo removeTag(String tag) {
        tags.remove(tag);
        markDirty();
        return this;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    public Memo setMetadata(String key, Object value) {
        metadata.put(key, value);
        markDirty();
        return this;
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    // Extension point: Override this method in subclasses
    protected void onBeforeSave() {
        // Hook for subclasses to perform actions before save
    }

    // Extension point: Override this method in subclasses
    protected void onAfterLoad() {
        // Hook for subclasses to perform actions after load
    }

    // Rest of existing methods remain the same...

    public static Consumer<String> getChangeNameFunc() {
        return changeNameFunc;
    }

    public static void setChangeNameFunc(Consumer<String> changeNameFunc) {
        Memo.changeNameFunc = changeNameFunc;
    }

    public static Runnable getReturnFunc() {
        return returnFunc;
    }

    public static void setReturnFunc(Runnable returnFunc) {
        Memo.returnFunc = returnFunc;
        Memo.getCurrentMemoGUITitle().setOnReturn(returnFunc);
    }

    public static Memo getCurrentMemo() {
        return currentMemo;
    }

    public static void setCurrentMemo(Memo currentMemo) {
        Memo.currentMemo = currentMemo;
    }

    private void initMainBillboard(Window window) {
        mainMemoBillboard = new BillboardGUI(window, new GUIComponent(window, 256, 256) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int padding = 25;
                int rectX = padding;
                int rectY = padding;
                int rectW = getWidthPx() - padding * 2;
                int rectH = (int) ((getHeightPx() * 0.8f) - padding);
                g2d.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(5));
                g2d.drawRoundRect(rectX, rectY, rectW, rectH, 30, 30);
                String text = name != null ? name : "Untitled";
                Font baseFont = SnowMemo.currentTheme.getFonts()[0];
                int targetWidth = (int) (rectW * 0.9);
                int fontSize = calculateOptimalFontSize(g2d, baseFont, text, targetWidth);
                Font finalFont = baseFont.deriveFont((float) fontSize);
                FontMetrics fm = g2d.getFontMetrics(finalFont);
                g2d.setFont(finalFont);
                g2d.setColor(Color.BLACK);
                int x = rectX + (rectW - fm.stringWidth(text)) / 2;
                int y = getHeightPx() - fm.getDescent() - 10;
                g2d.drawString(text, x, y);
            }
        }) {
            @Override
            public Mesh setPosition(Vector3f pos) {
                updateHitBox();
                return super.setPosition(pos);
            }
        };
        mainMemoBillboard.setRotation(new Vector3f(0, (float) Math.PI, (float) Math.PI));
        mainMemoBillboard.setPosition(new Vector3f(0, 0, 1f));
    }

    private int calculateOptimalFontSize(Graphics2D g2d, Font baseFont, String text, int targetWidth) {
        int size = 10;
        Font font = baseFont.deriveFont((float) size);
        FontMetrics fm = g2d.getFontMetrics(font);
        while (fm.stringWidth(text) < targetWidth && size < 200) {
            size++;
            font = baseFont.deriveFont((float) size);
            fm = g2d.getFontMetrics(font);
        }
        return size - 1;
    }

    private void uploadToOnline(JSONObject memoJson) {
        // Implement MongoDB or other online upload logic here
    }

    public void renderMainMemoBillboard(Camera camera) {
        mainMemoBillboard.render(camera);
    }

    public void setMainBillboardPos(Vector3f pos) {
        mainMemoBillboard.setPosition(pos);
    }

    public Vector3f getMainBillboardPos() {
        return mainMemoBillboard.getPosition();
    }

    public BillboardGUI getMainBillboard() {
        return mainMemoBillboard;
    }

    public String getName() {
        return name;
    }

    public Memo setName(String name) {
        this.name = name;
        markDirty();
        return this;
    }

    public String getJsonData() {
        return jsonData;
    }

    public Memo setJsonData(String jsonData) {
        this.jsonData = jsonData;
        return this;
    }

    public boolean isAvailableOnline() {
        return availableOnline;
    }

    public Memo setAvailableOnline(boolean availableOnline) {
        this.availableOnline = availableOnline;
        markDirty();
        return this;
    }

    public List<Node> getNodes() {
        return new ArrayList<>(nodes);
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
        markDirty();
    }

    public void cleanUp() {
        // Unload nodes if loaded
        if (isLoaded) {
            unload();
        }

        if (mainMemoBillboard != null) {
            mainMemoBillboard.cleanUp();
        }
    }

    private MemoImGUITitle memoImGUITitle = new MemoImGUITitle(() -> {}, this::renameFile);

    public MemoImGUITitle getMemoImGUITitle() {
        return memoImGUITitle;
    }

    private static class MemoImGUITitle implements Renderable {
        private ImString memoName = new ImString(Short.MAX_VALUE);
        private ImBoolean visible = new ImBoolean(true);
        private Runnable onReturn;
        private Consumer<String> onNameChange;

        public MemoImGUITitle(Runnable onReturn, Consumer<String> onNameChange) {
            this.onReturn = onReturn;
            this.onNameChange = onNameChange;
        }

        @Override
        public void render() {
            if (!visible.get()) return;
            ImGuiViewport vp = ImGui.getMainViewport();
            ImGui.setNextWindowSize(new ImVec2(vp.getWorkSizeX() * 0.3f, vp.getWorkSizeY() * 0.08f), ImGuiCond.Always);
            ImGui.setNextWindowPos(new ImVec2(vp.getWorkSizeX() * 0.01f, vp.getWorkSizeY() * 0.01f), ImGuiCond.Always);
            ImGui.begin("MemoTitle", visible,
                    ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoTitleBar |
                            ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove);
            ImGuiStyle style = ImGui.getStyle();
            float windowHeight = ImGui.getWindowHeight();
            float contentHeight = windowHeight - (style.getWindowPaddingY() * 2);
            applyTheme(style);
            float returnBtnWidth = ImGui.calcTextSize("Return").x + style.getFramePaddingX() * 4;
            if (ImGui.button("Return", returnBtnWidth, contentHeight)) {
                if (onReturn != null) onReturn.run();
            }
            if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            ImGui.sameLine();
            float inputWidth = ImGui.getContentRegionAvailX();
            float vertPadding = (contentHeight - ImGui.getTextLineHeight()) / 2.0f;
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, style.getFramePaddingX(), vertPadding);
            ImGui.setNextItemWidth(inputWidth);
            if (ImGui.inputText("##memoName", memoName, ImGuiInputTextFlags.EnterReturnsTrue)) {
                if (onNameChange != null) onNameChange.accept(memoName.get());
            }
            ImGui.popStyleVar();
            resetStyles(style);
            ImGui.end();
        }

        private ImVec4 oriText, oriFrameBg;

        private void applyTheme(ImGuiStyle style) {
            oriText = style.getColor(ImGuiCol.Text);
            style.setColor(ImGuiCol.Text,
                    SnowMemo.currentTheme.getSecondaryColors()[0].getRed(),
                    SnowMemo.currentTheme.getSecondaryColors()[0].getGreen(),
                    SnowMemo.currentTheme.getSecondaryColors()[0].getBlue(),
                    SnowMemo.currentTheme.getSecondaryColors()[0].getAlpha());
            oriFrameBg = style.getColor(ImGuiCol.FrameBg);
            style.setColor(ImGuiCol.FrameBg,
                    SnowMemo.currentTheme.getMainColor().getRed(),
                    SnowMemo.currentTheme.getMainColor().getGreen(),
                    SnowMemo.currentTheme.getMainColor().getBlue(),
                    SnowMemo.currentTheme.getMainColor().getAlpha());
            style.setFrameBorderSize(1f);
        }

        private void resetStyles(ImGuiStyle style) {
            style.setColor(ImGuiCol.Text, oriText.x, oriText.y, oriText.z, oriText.w);
            style.setColor(ImGuiCol.FrameBg, oriFrameBg.x, oriFrameBg.y, oriFrameBg.z, oriFrameBg.w);
        }

        @Override
        public void init() {}

        @Override
        public void cleanUp() {
            memoName = null;
        }

        public Runnable getOnReturn() {
            return onReturn;
        }

        public MemoImGUITitle setOnReturn(Runnable onReturn) {
            this.onReturn = onReturn;
            return this;
        }

        public Consumer<String> getOnNameChange() {
            return onNameChange;
        }

        public MemoImGUITitle setOnNameChange(Consumer<String> onNameChange) {
            this.onNameChange = onNameChange;
            return this;
        }
    }

    public static MemoImGUITitle getCurrentMemoGUITitle() {
        return currentMemoGUITitle;
    }

    public static void setCurrentMemoGUITitle(MemoImGUITitle currentMemoGUITitle) {
        Window.getCurrentWindow().unregisterImGuiToRender(Memo.currentMemoGUITitle);
        if (currentMemoGUITitle != null) Window.getCurrentWindow().setImGuiToRender(currentMemoGUITitle);
        Memo.currentMemoGUITitle = currentMemoGUITitle;
    }

    public void renameFile(String newName) {
        File savesFolder = new File(SAVES_FOLDER);
        Path oldPath = new File(savesFolder, this.name + ".json").toPath();
        Path newPath = new File(savesFolder, newName + ".json").toPath();
        try {
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            this.name = newName;
            markDirty();
        } catch (IOException e) {
            System.err.println("Failed to rename memo file");
            e.printStackTrace();
        }
    }

    public static List<Memo> getMemos() {
        return new ArrayList<>(memoList);
    }

    public static List<Memo> refreshMemos(Window window) {
        return loadMemos(window);
    }

    /**
     * Creates a new empty memo with a timestamped name
     * @param window The window context
     * @return The newly created Memo
     */
    public static Memo createNewMemo(Window window) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String memoName = "Untitled_" + timestamp;

        // Create initial JSON structure
        JSONObject initialJson = new JSONObject();
        initialJson.put("version", "1.0");
        initialJson.put("name", memoName);
        initialJson.put("description", "");
        initialJson.put("availableOnline", false);
        initialJson.put("created", now.toString());
        initialJson.put("lastModified", now.toString());
        initialJson.put("tags", new JSONArray());
        initialJson.put("nodes", new JSONArray());
        initialJson.put("metadata", new JSONObject());

        // Create the memo object with valid JSON
        Memo newMemo = new Memo(memoName, initialJson.toString(), window);
        newMemo.setDescription("");
        newMemo.setAvailableOnline(false);
        newMemo.created = now;
        newMemo.lastModified = now;

        System.out.println("Created new memo: " + memoName);

        // Add to memo list

        memoList.add(newMemo);

        return newMemo;
    }

    /**
     * Creates a new memo with a custom name
     * @param name The name for the memo
     * @param window The window context
     * @return The newly created Memo
     */
    public static Memo createNewMemo(String name, Window window) {
        LocalDateTime now = LocalDateTime.now();

        // Check if memo with this name already exists
        File saveFile = new File(SAVES_FOLDER, name + ".json");
        if (saveFile.exists()) {
            throw new RuntimeException("Memo with name '" + name + "' already exists");
        }

        // Create initial JSON structure
        JSONObject initialJson = new JSONObject();
        initialJson.put("version", "1.0");
        initialJson.put("name", name);
        initialJson.put("description", "");
        initialJson.put("availableOnline", false);
        initialJson.put("created", now.toString());
        initialJson.put("lastModified", now.toString());
        initialJson.put("tags", new JSONArray());
        initialJson.put("nodes", new JSONArray());
        initialJson.put("metadata", new JSONObject());

        // Create the memo object with valid JSON
        Memo newMemo = new Memo(name, initialJson.toString(), window);
        newMemo.setDescription("");
        newMemo.setAvailableOnline(false);
        newMemo.created = now;
        newMemo.lastModified = now;

        // Save the empty memo to disk
        try {
            newMemo.saveWithBackup();
            System.out.println("Created new memo: " + name);
        } catch (IOException e) {
            System.err.println("Failed to create memo: " + e.getMessage());
            throw new RuntimeException("Unable to create memo file", e);
        }

        // Add to memo list
        memoList.add(newMemo);

        return newMemo;
    }
}