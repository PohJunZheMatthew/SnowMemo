package Main.Settings;

import GUI.BillBoardGUI.LabelBillboardGUI;
import Main.*;
import com.mongodb.lang.NonNull;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Settings extends Mesh implements Map<String,SettingsAttribute>, Renderable {
    private final static ConcurrentHashMap<String,SettingsAttribute> settingsAttributes = new ConcurrentHashMap<String,SettingsAttribute>();
    private LabelBillboardGUI title;
    float[] originalVerts = null;
    float scrollOffset = 0;
    float maxScroll = 10;
    public Settings(Window currentWindow) {
        super(Utils.loadObj(SnowMemo.class.getResourceAsStream("Cube.obj"),currentWindow), currentWindow);
        setPosition(new Vector3f(1f, 1.25f,0f));
        title = new LabelBillboardGUI(currentWindow,"Settings");
        title.setItalic(false);
        title.setScale(new Vector3f(1.5f));
        title.setRotation(new Vector3f((float) 0, (float) Math.PI, (float) Math.PI));
        title.setPosition(new Vector3f(-1.125f,2.125f,0.3f));
        title.setUnderline(true);
        originalVerts = getVertices();
        float[] updatingVerts = getVertices().clone();
        scaleVerticesDown(updatingVerts,new Vector3f(5,4,1));
        updateVertices(updatingVerts);
        outline = false;
        setUpDefaultAttributes();
        currentWindow.ScrollCallbacks.add(new GLFWScrollCallback() {
            @Override
            public void invoke(long handler, double xoffset, double yoffset) {
                if (!visible) return;
                float SCROLL_SPEED = 1.0f;
                scrollOffset += (float) -yoffset * SCROLL_SPEED;
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            }
        });
    }
    public void setUpDefaultAttributes(){
        settingsAttributes.put("SnowMemo.Defaults.AntiAliasing",new BooleanSettingsAttribute(win,"Antialiasing"));
        settingsAttributes.put("SnowMemo.Defaults.Vsync",new BooleanSettingsAttribute(win,"Vsync"));
        settingsAttributes.put("SnowMemo.Defaults.Dithering",new BooleanSettingsAttribute(win,"Dithering"));
        settingsAttributes.put("SnowMemo.Defaults.Billboard.DebugRender",new BooleanSettingsAttribute(win,"Billboard hitbox debug"));
        try {
            settingsAttributes.put("SnowMemo.Defaults.ReportBugLink",new LinkSettingsAttribute(win,"Found a bug?","Report it!",new URI("https://github.com/PohJunZheMatthew/SnowMemo/issues/new")).setIndex(1000));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void render(Camera camera){
        if (!visible) return;
        setPosition(new Vector3f(1f, 1.25f+scrollOffset,0f));
        title.setPosition(new Vector3f(-1.125f,2.125f+scrollOffset,0.3f));
        float dy = 1.5f;
        float dh = 0f;
        List<SettingsAttribute> settingsAttributesCollection =
                new ArrayList<>(settingsAttributes.values());

        settingsAttributesCollection.sort(Comparator.comparingInt(SettingsAttribute::getIndex));

        for (SettingsAttribute<?> attribute: settingsAttributesCollection) {
            if (attribute.isVisible()) {
                attribute.setY(dy);
                dh +=attribute.getHeight();
                dy -= attribute.getHeight();
                attribute.setOffsetPos(new Vector3f(0,scrollOffset,0));
                attribute.render(camera);
            }
        }
        maxScroll = Math.max(0,dh);
        float[] updatingVerts = originalVerts.clone();
        scaleVerticesDown(updatingVerts,new Vector3f(5,Math.max(dh,4),1));
        updateVertices(updatingVerts);
        title.render(camera);
        super.render(camera);
    }

    @Override
    public int size() {
        return settingsAttributes.size();
    }

    @Override
    public boolean isEmpty() {
        return settingsAttributes.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return settingsAttributes.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return settingsAttributes.containsValue(value);
    }

    @Override
    public SettingsAttribute<?> get(Object key) {
        return settingsAttributes.get(key);
    }

    @Override
    public SettingsAttribute<?> put(String key, SettingsAttribute value) {
        return settingsAttributes.put(key,value);
    }

    @Override
    public SettingsAttribute<?> remove(Object key) {
        return settingsAttributes.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends SettingsAttribute> m) {
        settingsAttributes.putAll(m);
    }

    @Override
    public void clear() {
        for (SettingsAttribute<?> value : settingsAttributes.values()) {
            value.toDefault();
        }
    }

    @Override
    public Set<String> keySet() {
        return Set.of();
    }

    @Override
    public Collection<SettingsAttribute> values() {
        return settingsAttributes.values();
    }

    @Override
    public Set<Entry<String, SettingsAttribute>> entrySet() {
        return settingsAttributes.entrySet();
    }

    @Override
    public void cleanUp() {
        super.cleanUp();
        title.cleanUp();
        for (SettingsAttribute<?> settingsAttribute : settingsAttributes.values()) {
            settingsAttribute.cleanUp();
        }
    }
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
                vertices[i + 1] = y;  // Keep top vertices at original position
            } else {
                vertices[i + 1] = y - (scale.y - 1);  // Extend bottom downward
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
    public static Object getValue(String key){
        if (settingsAttributes.get(key)!=null)
        return settingsAttributes.get(key).getValue(); else return null;
    }
    @Override
    public Mesh setVisible(boolean visibility) {
        for (SettingsAttribute settingsAttribute : settingsAttributes.values()) {
            if (settingsAttribute instanceof Mesh){
                ((Mesh) settingsAttribute).setVisible(visibility);
            }
        }
        return super.setVisible(visibility);
    }
}
