package Light;

import Main.ShaderProgram;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class PointLight {
    private Vector4f ambient;
    private Vector4f diffuse;
    private Vector4f specular;
    private float strength;
    private Vector3f position;
    float radius;
    public PointLight(Vector4f ambient, Vector4f diffuse, Vector4f specular, Vector3f position, float strength) {
        this.ambient = ambient;
        this.diffuse = diffuse;
        this.specular = specular;
        this.position = position;
        this.strength = strength;
    }

    // Defaults to strength = 1
    public PointLight(Vector4f ambient, Vector4f diffuse, Vector4f specular, Vector3f position) {
        this(ambient, diffuse, specular, position, 1f);
    }

    public PointLight(Vector3f position) {
        this(new Vector4f(1f, 1f, 1f, 1f),
                new Vector4f(1f, 1f, 1f, 1f),
                new Vector4f(1f, 1f, 1f, 1f),
                position,
                1f);
    }

    public void setUniformVal(ShaderProgram shaderProgram) {
        shaderProgram.createUniformIfAbsent("light.ambient");
        shaderProgram.createUniformIfAbsent("light.diffuse");
        shaderProgram.createUniformIfAbsent("light.specular");
        shaderProgram.createUniformIfAbsent("light.position");
        shaderProgram.createUniformIfAbsent("light.strength");

        shaderProgram.createUniformIfAbsent("lightConstant");
        shaderProgram.createUniformIfAbsent("lightLinear");
        shaderProgram.createUniformIfAbsent("lightQuadratic");

        shaderProgram.setUniform("light.ambient", ambient);
        shaderProgram.setUniform("light.diffuse", diffuse);
        shaderProgram.setUniform("light.specular", specular);
        shaderProgram.setUniform("light.position", position);
        shaderProgram.setUniform("light.strength", strength);

        shaderProgram.setUniform("lightConstant", 1.0f);
        shaderProgram.setUniform("lightLinear", 0.09f);
        shaderProgram.setUniform("lightQuadratic", 0.032f);
        shaderProgram.createUniformIfAbsent("light.radius");
        shaderProgram.setUniform("light.radius", radius);
    }

    public PointLight setAmbient(Vector4f ambient) {
        this.ambient = ambient;
        return this;
    }

    public PointLight setDiffuse(Vector4f diffuse) {
        this.diffuse = diffuse;
        return this;
    }

    public PointLight setSpecular(Vector4f specular) {
        this.specular = specular;
        return this;
    }

    public PointLight setStrength(float strength) {
        this.strength = strength;
        return this;
    }

    public PointLight setPosition(Vector3f position) {
        this.position = position;
        return this;
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

    public Vector3f getPosition() {
        return position;
    }
}
