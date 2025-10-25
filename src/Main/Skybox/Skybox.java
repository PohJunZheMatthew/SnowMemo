package Main.Skybox;

import Main.Renderable;
import Main.ShaderProgram;
import Main.Utils;
import org.joml.Vector4f;

import java.util.Objects;

public class Skybox implements Renderable {
    private final ShaderProgram shaderProgram;
    private final Vector4f ambientColor;
    private final float[] vertices = new float[]{
            // Front face
            -1.0f, -1.0f,  1.0f,   0f,  0f,  1f,   0f, 0f,
            1.0f, -1.0f,  1.0f,   0f,  0f,  1f,   1f, 0f,
            1.0f,  1.0f,  1.0f,   0f,  0f,  1f,   1f, 1f,
            -1.0f,  1.0f,  1.0f,   0f,  0f,  1f,   0f, 1f,

            // Back face
            -1.0f, -1.0f, -1.0f,   0f,  0f, -1f,   1f, 0f,
            1.0f, -1.0f, -1.0f,   0f,  0f, -1f,   0f, 0f,
            1.0f,  1.0f, -1.0f,   0f,  0f, -1f,   0f, 1f,
            -1.0f,  1.0f, -1.0f,   0f,  0f, -1f,   1f, 1f,

            // Left face
            -1.0f, -1.0f, -1.0f,  -1f,  0f,  0f,   0f, 0f,
            -1.0f, -1.0f,  1.0f,  -1f,  0f,  0f,   1f, 0f,
            -1.0f,  1.0f,  1.0f,  -1f,  0f,  0f,   1f, 1f,
            -1.0f,  1.0f, -1.0f,  -1f,  0f,  0f,   0f, 1f,

            // Right face
            1.0f, -1.0f, -1.0f,   1f,  0f,  0f,   1f, 0f,
            1.0f, -1.0f,  1.0f,   1f,  0f,  0f,   0f, 0f,
            1.0f,  1.0f,  1.0f,   1f,  0f,  0f,   0f, 1f,
            1.0f,  1.0f, -1.0f,   1f,  0f,  0f,   1f, 1f,

            // Top face
            -1.0f,  1.0f, -1.0f,   0f,  1f,  0f,   0f, 1f,
            1.0f,  1.0f, -1.0f,   0f,  1f,  0f,   1f, 1f,
            1.0f,  1.0f,  1.0f,   0f,  1f,  0f,   1f, 0f,
            -1.0f,  1.0f,  1.0f,   0f,  1f,  0f,   0f, 0f,

            // Bottom face
            -1.0f, -1.0f, -1.0f,   0f, -1f,  0f,   0f, 0f,
            1.0f, -1.0f, -1.0f,   0f, -1f,  0f,   1f, 0f,
            1.0f, -1.0f,  1.0f,   0f, -1f,  0f,   1f, 1f,
            -1.0f, -1.0f,  1.0f,   0f, -1f,  0f,   0f, 1f
    };
    private final float[] indices = new float[]{
            0,  1,  2,  2,  3,  0,       // Front
            4,  5,  6,  6,  7,  4,       // Back
            8,  9, 10, 10, 11,  8,       // Left
            12, 13, 14, 14, 15, 12,      // Right
            16, 17, 18, 18, 19, 16,      // Top
            20, 21, 22, 22, 23, 20       // Bottom
    };
    public Skybox(Vector4f ambientColor){
        this.ambientColor = ambientColor;
        try {
            shaderProgram = new ShaderProgram();
            shaderProgram.createVertexShader(Utils.loadResource(Objects.requireNonNull(Skybox.class.getResourceAsStream("SkyboxVertex.vs"))));
            shaderProgram.createFragmentShader(Utils.loadResource(Objects.requireNonNull(Skybox.class.getResourceAsStream("SkyboxFragment.glsl"))));
            shaderProgram.link();
            shaderProgram.createUniform("modelViewMatrix");
            shaderProgram.createUniform("projectionMatrix");
            shaderProgram.createUniform("ambientLight");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally{

        }
    }

    @Override
    public void render() {

    }

    @Override
    public void init() {

    }

    @Override
    public void cleanUp() {

    }
}
