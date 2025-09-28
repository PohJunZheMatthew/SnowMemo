package Light;

import Main.ShaderProgram;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class DirectionalLight {
    private Vector3f direction;
    private Vector4f ambient;
    private Vector4f diffuse;
    private Vector4f specular;
    private float strength;
    private String name;
    // Default constructor (white light, standard ambient)
    public DirectionalLight(String name,Vector3f direction) {
        this(name,direction,
                new Vector4f(1.0f, 1f, 1f, 1.0f),  // ambient
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),  // diffuse
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),  // specular
                5.0f);
    }

    public DirectionalLight(String name,Vector3f direction, Vector4f ambient, Vector4f diffuse, Vector4f specular, float strength) {
        this.direction = new Vector3f(direction); // copy for safety
        this.ambient   = new Vector4f(ambient);
        this.diffuse   = new Vector4f(diffuse);
        this.specular  = new Vector4f(specular);
        this.strength  = strength;
        this.name = name;
    }

    // Send values to the shader
    public void setUniforms(ShaderProgram shaderProgram) {
        shaderProgram.createUniformIfAbsent("sun.ambient");
        shaderProgram.createUniformIfAbsent("sun.diffuse");
        shaderProgram.createUniformIfAbsent("sun.specular");
        shaderProgram.createUniformIfAbsent("sun.direction");
        shaderProgram.createUniformIfAbsent("sun.strength");

        shaderProgram.setUniform("sun.ambient", ambient);
        shaderProgram.setUniform("sun.diffuse", diffuse);
        shaderProgram.setUniform("sun.specular", specular);
        shaderProgram.setUniform("sun.direction", direction);
        shaderProgram.setUniform("sun.strength", strength);
    }

    // Getters / setters
    public Vector3f getDirection() { return new Vector3f(direction); }
    public void setDirection(Vector3f direction) { this.direction.set(direction); }

    public Vector4f getAmbient() { return new Vector4f(ambient); }
    public void setAmbient(Vector4f ambient) { this.ambient.set(ambient); }

    public Vector4f getDiffuse() { return new Vector4f(diffuse); }
    public void setDiffuse(Vector4f diffuse) { this.diffuse.set(diffuse); }

    public Vector4f getSpecular() { return new Vector4f(specular); }
    public void setSpecular(Vector4f specular) { this.specular.set(specular); }

    public float getStrength() { return strength; }
    public void setStrength(float strength) { this.strength = strength; }
}
