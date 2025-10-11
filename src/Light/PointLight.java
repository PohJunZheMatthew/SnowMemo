package Light;

import Main.ShaderProgram;
import Main.Window;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class PointLight extends Light {
    private Vector3f position;
    private Vector4f ambient, diffuse, specular;
    private float strength;
    private float radius;

    public PointLight(Window window, Vector3f position, Vector4f ambient, Vector4f diffuse, Vector4f specular, float strength) {
        super(window);
        this.position = position;
        this.ambient = ambient;
        this.diffuse = diffuse;
        this.specular = specular;
        this.strength = strength;
        this.radius = 100f;
    }

    public PointLight(Window window, Vector3f position) {
        super(window);
        this.position = position;
        this.ambient = new Vector4f(1f, 1f, 1f, 1f);
        this.diffuse = new Vector4f(1f, 1f, 1f, 1f);
        this.specular = new Vector4f(1f, 1f, 1f, 1f);
        this.strength = 1f;
        this.radius = 10f;
    }

    @Override
    public void addValuesToShaderProgram(int index, ShaderProgram shader) {
        try {
            String prefix = "pointLights[" + index + "].";

            if (!shader.hasUniform(prefix + "position")) {
                shader.createUniform(prefix + "position");
            }
            shader.setUniform(prefix + "position", position);

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

            if (!shader.hasUniform(prefix + "radius")) {
                shader.createUniform(prefix + "radius");
            }
            shader.setUniform(prefix + "radius", radius);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Getters and Setters
    public Vector3f getPosition() {
        return position;
    }

    public void setPosition(Vector3f position) {
        this.position = position;
    }

    public Vector4f getAmbient() {
        return ambient;
    }

    public void setAmbient(Vector4f ambient) {
        this.ambient = ambient;
    }

    public Vector4f getDiffuse() {
        return diffuse;
    }

    public void setDiffuse(Vector4f diffuse) {
        this.diffuse = diffuse;
    }

    public Vector4f getSpecular() {
        return specular;
    }

    public void setSpecular(Vector4f specular) {
        this.specular = specular;
    }

    public float getStrength() {
        return strength;
    }

    public void setStrength(float strength) {
        this.strength = strength;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }
}
