package Light;

import Main.ShaderProgram;
import Main.Window;
import com.mongodb.lang.NonNull;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class DirectionalLight extends Light {
    private final Vector3f direction;
    private final Vector4f ambient;
    private final Vector4f diffuse;
    private final Vector4f specular;
    private final float strength;

    public DirectionalLight(Window window, Vector3f direction) {
        this(window, direction,
                new Vector4f(1f, 1f, 1f, 1f),
                new Vector4f(1f, 1f, 1f, 1f),
                new Vector4f(1f, 1f, 1f, 1f),
                5f);
    }

    public DirectionalLight(Window window, Vector3f direction, Vector4f ambient, Vector4f diffuse, Vector4f specular, float strength) {
        super(window);
        this.direction = direction;
        this.ambient = ambient;
        this.diffuse = diffuse;
        this.specular = specular;
        this.strength = strength;
    }

    @Override
    public void addValuesToShaderProgram(int index, @NonNull ShaderProgram shader) {
        try {
            String prefix = "dirLights[" + index + "].";

            if (!shader.hasUniform(prefix + "direction")) {
                shader.createUniform(prefix + "direction");
            }
            shader.setUniform(prefix + "direction", direction);

            if (!shader.hasUniform(prefix + "ambient")) {
                shader.createUniform(prefix + "ambient");
            }
            shader.setUniform(prefix + "ambient", ambient);

            if (!shader.hasUniform(prefix + "diffuse")) {
                shader.createUniform(prefix + "diffuse");
            }
            shader.setUniform(prefix + "diffuse", diffuse);

            if (!shader.hasUniform(prefix + "specular")) {
                shader.createUniform(prefix + "specular");
            }
            shader.setUniform(prefix + "specular", specular);

            if (!shader.hasUniform(prefix + "strength")) {
                shader.createUniform(prefix + "strength");
            }
            shader.setUniform(prefix + "strength", strength);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Getters (no setters since fields are final)
    public Vector3f getDirection() {
        return direction;
    }

    public Vector4f getAmbient() {
        return ambient;
    }

    public Vector4f getDiffuse() {
        return diffuse;
    }

    public Vector4f getSpecular() {
        return specular;
    }

    public float getStrength() {
        return strength;
    }
}