package Light;

import Main.ShaderProgram;
import Main.Window;
import com.mongodb.lang.NonNull;

import java.util.Collection;
import java.util.HashMap;

public abstract class Light {
    static HashMap<Window, HashMap<Integer, Light>> lights = new HashMap<>();
    public Light(@NonNull Window window) {
        lights.computeIfAbsent(window, _ -> new HashMap<>());
        lights.get(window).put(hashCode(), this);
    }

    // Each subclass sets its uniforms in the shader
    public abstract void addValuesToShaderProgram(int index, ShaderProgram shader);

    public static void setLightsToShader(Window window, ShaderProgram shader) {
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
}
