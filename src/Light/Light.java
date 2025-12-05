package Light;

import Main.ShaderProgram;
import Main.Window;
import com.mongodb.lang.NonNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public abstract class Light {
    static HashMap<Window, HashMap<Integer, Light>> lights = new HashMap<>();

    public Light(@NonNull Window window) {
        lights.computeIfAbsent(window, _ -> new HashMap<>());
        lights.get(window).put(hashCode(), this);
    }

    // Each subclass sets its uniforms in the shader
    public abstract void addValuesToShaderProgram(int index, ShaderProgram shader);

    public static void setLightsToShader(Window window, ShaderProgram shader) {
        if (!lights.containsKey(window)) return;
        Collection<Light> lightCollection = lights.get(window).values();
        int pointIndex = 0;
        int dirIndex = 0;
        for (Light l : lightCollection) {
            if (l instanceof PointLight) {
                l.addValuesToShaderProgram(pointIndex++, shader);
            } else if (l instanceof DirectionalLight) {
                l.addValuesToShaderProgram(dirIndex++, shader);
            }
        }
        shader.createUniformIfAbsent("numPointLights");
        shader.setUniform("numPointLights", pointIndex);
        shader.createUniformIfAbsent("numDirLights");
        shader.setUniform("numDirLights", dirIndex);
    }

    /**
     * Gets all light directions for shadow mapping
     * Point lights return direction from light to scene center
     * Directional lights return their direction vector
     */
    public static List<Vector3f> getLightDirections(Window window) {
        return getLightDirections(window, new Vector3f(0, 0, 0));
    }

    /**
     * Gets all light directions relative to a target point
     *
     * @param window The window containing the lights
     * @param target The target point (usually scene center)
     */
    public static List<Vector3f> getLightDirections(Window window, Vector3f target) {
        Collection<Light> lightCollection = lights.get(window).values();
        List<Vector3f> directions = new ArrayList<>();

        for (Light l : lightCollection) {
            if (l instanceof PointLight) {
                PointLight pl = (PointLight) l;
                // Direction from target to light position
                Vector3f dir = new Vector3f(pl.getPosition()).sub(target).normalize();
                directions.add(dir);
            } else if (l instanceof DirectionalLight) {
                DirectionalLight dl = (DirectionalLight) l;
                directions.add(new Vector3f(dl.getDirection()));
            }
        }

        return directions;
    }

    /**
     * Gets all point lights for a window
     */
    public static List<PointLight> getPointLights(Window window) {
        Collection<Light> lightCollection = lights.get(window).values();
        List<PointLight> pointLights = new ArrayList<>();

        for (Light l : lightCollection) {
            if (l instanceof PointLight) {
                pointLights.add((PointLight) l);
            }
        }

        return pointLights;
    }

    /**
     * Gets all directional lights for a window
     */
    public static List<DirectionalLight> getDirectionalLights(Window window) {
        Collection<Light> lightCollection = lights.get(window).values();
        List<DirectionalLight> dirLights = new ArrayList<>();

        for (Light l : lightCollection) {
            if (l instanceof DirectionalLight) {
                dirLights.add((DirectionalLight) l);
            }
        }

        return dirLights;
    }

    /**
     * Removes a light from the window
     */
    public void remove(Window window) {
        if (lights.containsKey(window)) {
            lights.get(window).remove(hashCode());
        }
    }

    /**
     * Clears all lights for a window
     */
    public static void clearLights(Window window) {
        if (lights.containsKey(window)) {
            lights.get(window).clear();
        }
    }
}