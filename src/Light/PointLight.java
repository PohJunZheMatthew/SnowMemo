package Light;

import Main.ShaderProgram;
import Main.Window;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fKt;

public class PointLight extends Light {
    private Vector3f position;
    private Vector4f ambient, diffuse, specular;
    private float strength;
    private float radius;

    public PointLight(Window window, Vector3f position, Vector4f ambient, Vector4f diffuse, Vector4f specular,float strength) {
        super(window);
        this.position = position;
        this.ambient = ambient;
        this.diffuse = diffuse;
        this.specular = specular;
        this.strength = strength;
        this.radius = 100f;
    }
    public PointLight(Window window,Vector3f position) {
        super(window);
        this.position = position;
        this.ambient = new Vector4f(1f,1f,1f,1f);
        this.diffuse = new Vector4f(1f,1f,1f,1f);
        this.specular = new Vector4f(1f,1f,1f,1f);
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
}
