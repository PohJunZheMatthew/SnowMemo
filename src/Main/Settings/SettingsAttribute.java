package Main.Settings;

import Main.CameraRenderable;
import org.joml.Vector3f;

public interface SettingsAttribute<t> extends CameraRenderable {
    SettingsAttribute<t> setValue(t value);
    t getValue();
    void toDefault();
    float getHeight();
    boolean isVisible();
    SettingsAttribute<t> setY(float y);
    SettingsAttribute<t> setOffsetPos(Vector3f pos);
    int getIndex();
    SettingsAttribute<t> setIndex(int index);

}
