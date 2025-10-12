package Main;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.memFree;

import jdk.jfr.Name;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

public class Mesh implements Renderable {
    protected boolean outline = true;
    protected final int vaoId;
    protected final int vboId;
    protected final int idxVboId;
    protected final IntBuffer indicesBuffer;
    protected final int vertexCount;
    ShaderProgram shaderProgram;
    protected final Window win;
    protected Matrix4f modelMatrix = new Matrix4f();
    protected Matrix3f normMatrix = new Matrix3f();
    protected Vector3f position = new Vector3f(0,0,0),rotation = new Vector3f(0, (float) (Math.PI/2),0),scale = new Vector3f(1,1f,1);
    // In Mesh class, change the material to:
    protected float[] vertices; // stores current vertex data
    Texture texture;
    static final float[] CUBE_POS = new float[]{
        -0.5f, -0.5f,  0.5f,   0f,  0f,  1f,
        0.5f, -0.5f,  0.5f,   0f,  0f,  1f,
        0.5f,  0.5f,  0.5f,   0f,  0f,  1f,
        -0.5f,  0.5f,  0.5f,   0f,  0f,  1f,
        -0.5f, -0.5f, -0.5f,   0f,  0f, -1f,
        0.5f, -0.5f, -0.5f,   0f,  0f, -1f,
        0.5f,  0.5f, -0.5f,   0f,  0f, -1f,
        -0.5f,  0.5f, -0.5f,   0f,  0f, -1f,
        -0.5f, -0.5f, -0.5f,  -1f,  0f,  0f,
        -0.5f, -0.5f,  0.5f,  -1f,  0f,  0f,
        -0.5f,  0.5f,  0.5f,  -1f,  0f,  0f,
        -0.5f,  0.5f, -0.5f,  -1f,  0f,  0f,
        0.5f, -0.5f, -0.5f,   1f,  0f,  0f,
        0.5f, -0.5f,  0.5f,   1f,  0f,  0f,
        0.5f,  0.5f,  0.5f,   1f,  0f,  0f,
        0.5f,  0.5f, -0.5f,   1f,  0f,  0f,
        -0.5f,  0.5f, -0.5f,   0f,  1f,  0f,
        0.5f,  0.5f, -0.5f,   0f,  1f,  0f,
        0.5f,  0.5f,  0.5f,   0f,  1f,  0f,
        -0.5f,  0.5f,  0.5f,   0f,  1f,  0f,
        -0.5f, -0.5f, -0.5f,   0f, -1f,  0f,
        0.5f, -0.5f, -0.5f,   0f, -1f,  0f,
        0.5f, -0.5f,  0.5f,   0f, -1f,  0f,
        -0.5f, -0.5f,  0.5f,   0f, -1f,  0f,
    };
    static final float[] CUBE_POS_UV = new float[]{
            // Front face - position(3) + normal(3) + UV(2)
            -0.5f, -0.5f,  0.5f,   0f,  0f,  1f,   0.0f, 0.0f,
            0.5f, -0.5f,  0.5f,   0f,  0f,  1f,   1.0f, 0.0f,
            0.5f,  0.5f,  0.5f,   0f,  0f,  1f,   1.0f, 1.0f,
            -0.5f,  0.5f,  0.5f,   0f,  0f,  1f,   0.0f, 1.0f,

            // Back face
            -0.5f, -0.5f, -0.5f,   0f,  0f, -1f,   1.0f, 0.0f,
            0.5f, -0.5f, -0.5f,   0f,  0f, -1f,   0.0f, 0.0f,
            0.5f,  0.5f, -0.5f,   0f,  0f, -1f,   0.0f, 1.0f,
            -0.5f,  0.5f, -0.5f,   0f,  0f, -1f,   1.0f, 1.0f,

            // Left face
            -0.5f, -0.5f, -0.5f,  -1f,  0f,  0f,   0.0f, 0.0f,
            -0.5f, -0.5f,  0.5f,  -1f,  0f,  0f,   1.0f, 0.0f,
            -0.5f,  0.5f,  0.5f,  -1f,  0f,  0f,   1.0f, 1.0f,
            -0.5f,  0.5f, -0.5f,  -1f,  0f,  0f,   0.0f, 1.0f,

            // Right face
            0.5f, -0.5f, -0.5f,   1f,  0f,  0f,   1.0f, 0.0f,
            0.5f, -0.5f,  0.5f,   1f,  0f,  0f,   0.0f, 0.0f,
            0.5f,  0.5f,  0.5f,   1f,  0f,  0f,   0.0f, 1.0f,
            0.5f,  0.5f, -0.5f,   1f,  0f,  0f,   1.0f, 1.0f,

            // Top face
            -0.5f,  0.5f, -0.5f,   0f,  1f,  0f,   0.0f, 1.0f,
            0.5f,  0.5f, -0.5f,   0f,  1f,  0f,   1.0f, 1.0f,
            0.5f,  0.5f,  0.5f,   0f,  1f,  0f,   1.0f, 0.0f,
            -0.5f,  0.5f,  0.5f,   0f,  1f,  0f,   0.0f, 0.0f,

            // Bottom face
            -0.5f, -0.5f, -0.5f,   0f, -1f,  0f,   0.0f, 0.0f,
            0.5f, -0.5f, -0.5f,   0f, -1f,  0f,   1.0f, 0.0f,
            0.5f, -0.5f,  0.5f,   0f, -1f,  0f,   1.0f, 1.0f,
            -0.5f, -0.5f,  0.5f,   0f, -1f,  0f,   0.0f, 1.0f
    };
    static final int[] CUBE_INDICES = new int[]{
        0, 1, 2,   2, 3, 0,
        4, 6, 5,   6, 4, 7,
        8, 9, 10,  10, 11, 8,
        12, 14, 13, 14, 12, 15,
        16, 17, 18, 18, 19, 16,
        20, 22, 21, 22, 20, 23
    };
    protected Material material = new Material(
            new Vector4f(1f, 1f, 1f, 1.0f),      // Brighter ambient
            new Vector4f(1f, 1f, 1f, 1.0f),      // Brighter diffuse
            new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),      // Full specular
            32.0f                                        // Reasonable shininess
    );
    public Mesh(float[] vertexData, int[] indices, Window currentWindow, int stride) {
        win = currentWindow;
        FloatBuffer verticesBuffer = null;
        this.vertices = vertexData.clone(); // or positions.clone() in other constructors
        try {
            verticesBuffer = MemoryUtil.memAllocFloat(vertexData.length);
            vertexCount = indices.length;
            verticesBuffer.put(vertexData).flip();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            // === Tell OpenGL how to read the interleaved buffer ===
            int offset = 0;

            // position → location 0
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride * Float.BYTES, offset);
            glEnableVertexAttribArray(0);
            offset += 3 * Float.BYTES;

            // normal → location 1
            glVertexAttribPointer(1, 3, GL_FLOAT, false, stride * Float.BYTES, offset);
            glEnableVertexAttribArray(1);
            offset += 3 * Float.BYTES;

            // texcoords → location 2
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride * Float.BYTES, offset);
            glEnableVertexAttribArray(2);

            // === Index buffer ===
            idxVboId = glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(indices.length);
            indicesBuffer.put(indices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            // === Shader setup ===
            shaderProgram = new ShaderProgram();
            shaderProgram.createVertexShader(Utils.loadResource("Vertex.vs"));
            shaderProgram.createFragmentShader(Utils.loadResource("Fragment.fs"));
            shaderProgram.link();
            init();
            glBindVertexArray(0);
            memFree(indicesBuffer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (verticesBuffer != null) {
                memFree(verticesBuffer);
            }
        }
    }

    public Mesh(float[] vertexData, int[] indices, Window currentWindow, Texture texture) {
        this.win = currentWindow;
        this.vertexCount = indices.length;
        this.texture = texture;this.vertices = vertexData.clone(); // or positions.clone() in other constructors


        FloatBuffer verticesBuffer = null;

        try {
            // === Upload vertex data ===
            verticesBuffer = MemoryUtil.memAllocFloat(vertexData.length);
            verticesBuffer.put(vertexData).flip();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            // Vertex buffer
            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            // Stride = pos(3) + normal(3) + uv(2) = 8 floats per vertex
            int stride = 8 * Float.BYTES;

            // Position → location 0
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glEnableVertexAttribArray(0);

            // Normal → location 1
            glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);

            // TexCoord → location 2
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6 * Float.BYTES);
            glEnableVertexAttribArray(2);

            // === Upload indices ===
            idxVboId = glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(indices.length);
            indicesBuffer.put(indices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            // === Setup shader ===
            shaderProgram = new ShaderProgram();
            shaderProgram.createVertexShader(Utils.loadResource("Vertex.vs"));
            shaderProgram.createFragmentShader(Utils.loadResource("Fragment.fs"));
            shaderProgram.link();
            init();
            glBindVertexArray(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (verticesBuffer != null) MemoryUtil.memFree(verticesBuffer);
        }
    }
    public Mesh(float[] positions,int[] indices,Window currentWindow) {
        win = currentWindow;this.vertices = positions.clone(); // or positions.clone() in other constructors

        FloatBuffer verticesBuffer = null;
        try {
            verticesBuffer = MemoryUtil.memAllocFloat(positions.length);
            vertexCount = indices.length;
            verticesBuffer.put(positions).flip();
            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);
            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0); // positions
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES); // normals
            glEnableVertexAttribArray(1);
            idxVboId = glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(indices.length);
            indicesBuffer.put(indices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
            glBindVertexArray(0);
            shaderProgram = new ShaderProgram();
            shaderProgram.createVertexShader(Utils.loadResource("Vertex.vs"));
            shaderProgram.createFragmentShader(Utils.loadResource("Fragment.fs"));
            shaderProgram.link();
            init();
            memFree(indicesBuffer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (verticesBuffer  != null) {
                memFree(verticesBuffer);
            }
        }
    }

    public static Matrix4f buildModelViewMatrix(Mesh mesh, Matrix4f lightViewMatrix) {
        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.identity()
                .translate(mesh.getPosition())
                .rotateXYZ(mesh.getRotation().x, mesh.getRotation().y, mesh.getRotation().z)
                .scale(mesh.getScale());

        Matrix4f modelViewMatrix = new Matrix4f(lightViewMatrix);
        modelViewMatrix.mul(modelMatrix);

        return modelViewMatrix;
    }

    @Override
    public void init() {
        try {
            // Matrices
            shaderProgram.createUniform("projectionMatrix");
            shaderProgram.createUniform("modelMatrix");
            shaderProgram.createUniform("normalMatrix");
            shaderProgram.createUniform("lightSpaceMatrix");

            // Lighting
            shaderProgram.createUniform("useLighting");
            shaderProgram.createUniform("blockLight");
            shaderProgram.createUniform("emission");
            shaderProgram.createUniform("useTextures");
            shaderProgram.createUniform("diffuseSampler");
            shaderProgram.createUniform("overrideColor");

            // Material
            shaderProgram.createUniform("material.ambient");
            shaderProgram.createUniform("material.diffuse");
            shaderProgram.createUniform("material.specular");
            shaderProgram.createUniform("material.shininess");
            shaderProgram.createUniform("material.metallic");
            shaderProgram.createUniform("material.roughness");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize shader uniforms", e);
        }
    }

    public int getVaoId() {
        return vaoId;
    }

    public int getVertexCount() {
        return vertexCount;
    }
    public void render() {
        render(SnowMemo.camera);
    }
    public void render(Camera camera){
        GL11.glLineWidth(1);

        // Ensure all required uniforms exist
        try {
            if (!shaderProgram.hasUniform("useTextures")) {
                shaderProgram.createUniform("useTextures");
            }
            if (!shaderProgram.hasUniform("blockLight")) {
                shaderProgram.createUniform("blockLight");
            }
            if (!shaderProgram.hasUniform("emission")) {
                shaderProgram.createUniform("emission");
            }
            if (!shaderProgram.hasUniform("shadowMap")) {
                shaderProgram.createUniform("shadowMap");
            }
            if (!shaderProgram.hasUniform("viewMatrix")) {
                shaderProgram.createUniform("viewMatrix");
            }
            if (!shaderProgram.hasUniform("viewPosition")) {
                shaderProgram.createUniform("viewPosition");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        modelMatrix.identity().translate(position).rotateXYZ(rotation).scale(scale);
        shaderProgram.bind();

        // Set view position for PBR calculations
        shaderProgram.setUniform("viewPosition", camera.getPosition());

        // Lighting uniforms
        shaderProgram.setUniform("blockLight", new Vector3f(0.0f, 0.0f, 0.0f));
        shaderProgram.setUniform("emission", 0.0f);
        shaderProgram.setUniform("useTextures", texture != null);

        Light.Light.setLightsToShader(win, shaderProgram);
        shaderProgram.setUniform("projectionMatrix", win.getProjectionMatrix());
        shaderProgram.setUniform("viewMatrix", camera.getViewMatrix());

        Matrix4f modelViewMatrix = new Matrix4f(camera.getViewMatrix()).mul(modelMatrix);
        Matrix3f normalMatrix = new Matrix3f();
        modelViewMatrix.invert().transpose().get3x3(normalMatrix);
        shaderProgram.setUniform("normalMatrix", normalMatrix);

        try {
            camera.setViewMatrixUniform(shaderProgram);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Material
        material.setUniformVal(shaderProgram);

        glBindVertexArray(vaoId);

        // Bind shadow map to texture unit 1
        glActiveTexture(GL_TEXTURE1);
        if (SnowMemo.shadowMap != null) {
            SnowMemo.shadowMap.getDepthMapTexture().bind();
            shaderProgram.setUniform("shadowMap", 1);

            // Debug once
            if (Math.random() < 0.001) {
                System.out.println("Shadow map texture ID: " + SnowMemo.shadowMap.getDepthMapTexture().getTextureid());
                System.out.println("Bound to texture unit 1");
            }
        }

        // Bind diffuse texture to texture unit 0
        if (texture != null) {
            glActiveTexture(GL_TEXTURE0);
            texture.bind();
            shaderProgram.setUniform("diffuseSampler", 0);
        }

        // enable attributes: pos(0), normal(1), uv(2)
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);

        // ==========================
        // 1. Render outline
        // ==========================
        if (outline) {
            Matrix4f outlineMatrix = new Matrix4f(modelMatrix).scale(1.01f);
            shaderProgram.setUniform("modelMatrix", outlineMatrix);
            shaderProgram.setUniform("useLighting", 0);
            shaderProgram.setUniform("overrideColor", new Vector4f(0, 0, 0, 1));

            glCullFace(GL_FRONT);
            glDepthMask(false);
            glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
            glDepthMask(true);
        }
        // ==========================
        // 2. Render cube normally
        // ==========================
        shaderProgram.setUniform("modelMatrix", modelMatrix);
        shaderProgram.setUniform("useLighting", 1);

        // Bind texture if available
        if (texture != null) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            shaderProgram.setUniform("useTextures", true);
        } else {
            shaderProgram.setUniform("useTextures", false);
        }

        shaderProgram.setUniform("overrideColor", new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
        glCullFace(GL_BACK);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);

        // Cleanup
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glDisableVertexAttribArray(2);

        glBindVertexArray(0);
        if (texture != null) texture.unbind();

        // Unbind shadow map
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);

        shaderProgram.unbind();

    }
    public void cleanUp() {
        glDisableVertexAttribArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDeleteBuffers(vboId);      // Add this line
        glDeleteBuffers(idxVboId);
        glBindVertexArray(0);
        glDeleteVertexArrays(vaoId);
    }
    public Vector3f getPosition() {
        return position;
    }

    public Mesh setPosition(Vector3f position) {
        this.position = position;
        return this;
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public Mesh setRotation(Vector3f rotation) {
        this.rotation = rotation;
        return this;
    }

    public Vector3f getScale() {
        return scale;
    }

    public Mesh setScale(Vector3f scale) {
        this.scale = scale;
        return this;
    }
    public class Material {
        Vector4f ambient;
        Vector4f diffuse;
        Vector4f specular;
        float shininess;
        float metallic;
        float roughness;

        public Material(Vector4f ambient, Vector4f diffuse, Vector4f specular, float shininess, float metallic, float roughness) {
            this.ambient = ambient;
            this.diffuse = diffuse;
            this.specular = specular;
            this.shininess = shininess;
            this.metallic = metallic;
            this.roughness = roughness;
        }

        public Material(Vector4f ambient, Vector4f diffuse, Vector4f specular, float shininess) {
            this(ambient, diffuse, specular, shininess, 0.0f, 0.5f); // default metallic/roughness
        }

        public void setUniformVal(ShaderProgram shaderProgram) {
            // Only set values — do NOT create uniforms
            shaderProgram.setUniform("material.ambient", ambient);
            shaderProgram.setUniform("material.diffuse", diffuse);
            shaderProgram.setUniform("material.specular", specular);
            shaderProgram.setUniform("material.shininess", shininess);
            shaderProgram.setUniform("material.metallic", metallic);
            shaderProgram.setUniform("material.roughness", roughness);
        }
    }
    boolean collidePosition(Vector3f position){
        return false;
    }
    public void updateVertices(float[] newVertices) {
        this.vertices = newVertices.clone(); // store new data

        FloatBuffer verticesBuffer = null;
        try {
            verticesBuffer = MemoryUtil.memAllocFloat(vertices.length);
            verticesBuffer.put(vertices).flip();

            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW); // upload new data
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        } finally {
            if (verticesBuffer != null) memFree(verticesBuffer);
        }
    }

    public float[] getVertices() {
        return vertices;
    }
}