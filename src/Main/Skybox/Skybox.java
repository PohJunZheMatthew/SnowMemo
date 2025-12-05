package Main.Skybox;

import Main.Renderable;
import Main.ShaderProgram;
import Main.Utils;
import org.joml.Vector4f;
import org.lwjgl.opengl.*;

import java.util.Objects;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

public class Skybox implements Renderable {
    private final ShaderProgram shaderProgram;
    private final Vector4f ambientColor;
    private final Vector4f gradientBottom;
    private final Vector4f gradientTop;

    private final float[] vertices = {
            -1,-1,1, 0,0,1, 0,0,
            1,-1,1, 0,0,1, 1,0,
            1, 1,1, 0,0,1, 1,1,
            -1, 1,1, 0,0,1, 0,1,

            -1,-1,-1, 0,0,-1, 1,0,
            1,-1,-1, 0,0,-1, 0,0,
            1, 1,-1, 0,0,-1, 0,1,
            -1, 1,-1, 0,0,-1, 1,1,

            -1,-1,-1,-1,0,0, 0,0,
            -1,-1, 1,-1,0,0, 1,0,
            -1, 1, 1,-1,0,0, 1,1,
            -1, 1,-1,-1,0,0, 0,1,

            1,-1,-1,1,0,0, 1,0,
            1,-1, 1,1,0,0, 0,0,
            1, 1, 1,1,0,0, 0,1,
            1, 1,-1,1,0,0, 1,1,

            -1, 1,-1,0,1,0, 0,1,
            1, 1,-1,0,1,0, 1,1,
            1, 1, 1,0,1,0, 1,0,
            -1, 1, 1,0,1,0, 0,0,

            -1,-1,-1,0,-1,0, 0,0,
            1,-1,-1,0,-1,0, 1,0,
            1,-1, 1,0,-1,0, 1,1,
            -1,-1, 1,0,-1,0, 0,1
    };

    private final int[] indices = {
            0,1,2,2,3,0,
            4,5,6,6,7,4,
            8,9,10,10,11,8,
            12,13,14,14,15,12,
            16,17,18,18,19,16,
            20,21,22,22,23,20
    };

    private int vao, vbo, ebo;

    public Skybox(Vector4f ambientColor, Vector4f gradientBottom, Vector4f gradientTop) {
        this.ambientColor = ambientColor;
        this.gradientBottom = gradientBottom;
        this.gradientTop = gradientTop;
        GL.createCapabilities();
        try {
            shaderProgram = new ShaderProgram();
            shaderProgram.createVertexShader(Utils.loadResource(Objects.requireNonNull(Skybox.class.getResourceAsStream("SkyboxVertex.vs"))));
            shaderProgram.createFragmentShader(Utils.loadResource(Objects.requireNonNull(Skybox.class.getResourceAsStream("SkyboxFragment.glsl"))));
            shaderProgram.link();
            shaderProgram.createUniform("modelViewMatrix");
            shaderProgram.createUniform("projectionMatrix");
            shaderProgram.createUniform("ambientLight");
            shaderProgram.createUniform("gradientBottom");
            shaderProgram.createUniform("gradientTop");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init() {
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 8 * 4, 0);
        GL20.glEnableVertexAttribArray(0);

        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 8 * 4, 3 * 4);
        GL20.glEnableVertexAttribArray(1);

        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 8 * 4, 6 * 4);
        GL20.glEnableVertexAttribArray(2);

        glBindVertexArray(0);
    }

    @Override
    public void render() {
        shaderProgram.bind();
        shaderProgram.setUniform("ambientLight", ambientColor);
        shaderProgram.setUniform("gradientBottom", gradientBottom);
        shaderProgram.setUniform("gradientTop", gradientTop);
        glBindVertexArray(vao);
        GL11.glDrawElements(GL11.GL_TRIANGLES, indices.length, GL11.GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        shaderProgram.unbind();
    }

    @Override
    public void cleanUp() {
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
        shaderProgram.cleanup();
    }
}
