package Main;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.memFree;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL40;
import org.lwjgl.system.MemoryUtil;

public class Mesh implements Renderable, Cloneable {
    private static HashMap<Window,ArrayList<Mesh>> meshes = new HashMap<>();
    public static ShaderProgram sharedShaderProgram;
    private static boolean shaderInitialized = false;

    public boolean cullFace = true;
    private int queryId = -1;
    protected boolean visible = true;
    protected boolean renderVisible = true;
    private boolean queryPending = false;
    public boolean outline = true;
    public float outlineThickness = 1.02f; // Customizable outline scale
    public Vector4f outlineColor = new Vector4f(0, 0, 0, 1); // Customizable outline color (default black)

    protected final int vaoId;
    protected final int vboId;
    protected final int idxVboId;
    protected final int vertexCount;
    protected Window win;

    protected Matrix4f modelMatrix = new Matrix4f();
    protected Matrix3f normMatrix = new Matrix3f();
    protected Vector3f position = new Vector3f(0,0,0);
    protected Vector3f rotation = new Vector3f(0, (float) (Math.PI/2), 0);
    protected Vector3f scale = new Vector3f(1,1f,1);

    protected float[] vertices;
    protected Texture texture;
    public int[] indices;
    protected int stride;

    protected Material material = new Material(
            new Vector4f(1f, 1f, 1f, 1.0f),
            new Vector4f(1f, 1f, 1f, 1.0f),
            new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
            32.0f
    );
    protected boolean customScaling = false;

    private static void initSharedShader() throws Exception {
        if (!shaderInitialized) {
            sharedShaderProgram = new ShaderProgram();
            sharedShaderProgram.createVertexShader(Utils.loadResource("Vertex.vs"));
            sharedShaderProgram.createFragmentShader(Utils.loadResource("Fragment.fs"));
            sharedShaderProgram.link();
            initShaderUniforms(sharedShaderProgram);

            if (occlusionShader == null) {
                initOcclusionShader();
            }

            shaderInitialized = true;
        }
    }

    private static void initShaderUniforms(ShaderProgram shader) throws Exception {
        shader.createUniform("projectionMatrix");
        shader.createUniform("modelMatrix");
        shader.createUniform("normalMatrix");
        shader.createUniform("lightSpaceMatrix");
        shader.createUniform("viewMatrix");
        shader.createUniform("viewPosition");
        shader.createUniform("useLighting");
        shader.createUniform("blockLight");
        shader.createUniform("emission");
        shader.createUniform("useTextures");
        shader.createUniform("diffuseSampler");
        shader.createUniform("overrideColor");
        shader.createUniform("shadowMap");
        shader.createUniform("material.ambient");
        shader.createUniform("material.diffuse");
        shader.createUniform("material.specular");
        shader.createUniform("material.shininess");
        shader.createUniform("material.metallic");
        shader.createUniform("material.roughness");
    }

    private static int nextStencilValue = 1;
    private int stencilValue;

    public Mesh(Mesh other, Window currentWindow) {
        this.win = currentWindow;
        this.stencilValue = nextStencilValue++;
        if (nextStencilValue > 255) nextStencilValue = 1;

        meshes.computeIfAbsent(win, (window) -> new ArrayList<>());
        meshes.get(win).add(this);

        this.position = new Vector3f(other.position);
        this.rotation = new Vector3f(other.rotation);
        this.scale = new Vector3f(other.scale);
        this.stride = other.stride;
        this.vertexCount = other.vertexCount;
        this.texture = other.texture;

        // ✅ Deep copy arrays to avoid shared buffer corruption
        this.vertices = other.vertices.clone();
        this.indices = other.indices.clone();

        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;

        try {
            initSharedShader();

            verticesBuffer = MemoryUtil.memAllocFloat(vertices.length);
            verticesBuffer.put(vertices).flip();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            setupVertexAttributes(stride);

            idxVboId = glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(indices.length);
            indicesBuffer.put(indices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindVertexArray(0);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Mesh from existing Mesh", e);
        } finally {
            if (verticesBuffer != null) memFree(verticesBuffer);
            if (indicesBuffer != null) memFree(indicesBuffer);
        }

        this.material = new Material(
                new Vector4f(other.material.ambient),
                new Vector4f(other.material.diffuse),
                new Vector4f(other.material.specular),
                other.material.shininess,
                other.material.metallic,
                other.material.roughness
        );
    }
    public Mesh(float[] vertexData, int[] indices, Window currentWindow, int stride) {
        this.win = currentWindow;
        this.stencilValue = nextStencilValue++;
        if (nextStencilValue > 255) nextStencilValue = 1;
        meshes.computeIfAbsent(win, (window) -> new ArrayList<>());
        meshes.get(win).add(this);
        this.indices = indices;
        this.vertices = vertexData;
        this.stride = stride;
        this.vertexCount = indices.length;

        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;

        try {
            initSharedShader();

            verticesBuffer = MemoryUtil.memAllocFloat(vertexData.length);
            verticesBuffer.put(vertexData).flip();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            setupVertexAttributes(stride);

            idxVboId = glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(indices.length);
            indicesBuffer.put(indices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindVertexArray(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (verticesBuffer != null) memFree(verticesBuffer);
            if (indicesBuffer != null) memFree(indicesBuffer);
        }
    }

    public Mesh(float[] vertexData, int[] indices, Window currentWindow, Texture texture) {
        this.win = currentWindow;
        this.stencilValue = nextStencilValue++;
        if (nextStencilValue > 255) nextStencilValue = 1;
        meshes.computeIfAbsent(win, (window) -> new ArrayList<>());
        meshes.get(win).add(this);
        this.indices = indices;
        this.vertexCount = indices.length;
        this.texture = texture;
        this.vertices = vertexData;
        this.stride = 8;

        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;

        try {
            initSharedShader();

            verticesBuffer = MemoryUtil.memAllocFloat(vertexData.length);
            verticesBuffer.put(vertexData).flip();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            setupVertexAttributes(8);

            idxVboId = glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(indices.length);
            indicesBuffer.put(indices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindVertexArray(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (verticesBuffer != null) memFree(verticesBuffer);
            if (indicesBuffer != null) memFree(indicesBuffer);
        }
    }

    public Mesh(float[] positions, int[] indices, Window currentWindow) {
        this.win = currentWindow;
        this.stencilValue = nextStencilValue++;
        if (nextStencilValue > 255) nextStencilValue = 1;
        meshes.computeIfAbsent(win, (window) -> new ArrayList<>());
        meshes.get(win).add(this);
        this.stride = 6;
        this.indices = indices;
        this.vertices = positions;
        this.vertexCount = indices.length;

        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;

        try {
            initSharedShader();

            verticesBuffer = MemoryUtil.memAllocFloat(positions.length);
            verticesBuffer.put(positions).flip();

            vaoId = glGenVertexArrays();
            glBindVertexArray(vaoId);

            vboId = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);

            glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);

            idxVboId = glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(indices.length);
            indicesBuffer.put(indices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindVertexArray(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (verticesBuffer != null) memFree(verticesBuffer);
            if (indicesBuffer != null) memFree(indicesBuffer);
        }
    }

    public static List<Mesh> getAllMeshes(Window window) {
        meshes.computeIfAbsent(window, (win) -> new ArrayList<>());
        return meshes.get(window);
    }

    private void setupVertexAttributes(int stride) {
        int offset = 0;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride * Float.BYTES, offset);
        glEnableVertexAttribArray(0);
        offset += 3 * Float.BYTES;

        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride * Float.BYTES, offset);
        glEnableVertexAttribArray(1);
        offset += 3 * Float.BYTES;

        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride * Float.BYTES, offset);
        glEnableVertexAttribArray(2);
    }

    public static Matrix4f buildModelViewMatrix(Mesh mesh, Matrix4f lightViewMatrix) {
        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.identity()
                .translate(mesh.getPosition())
                .rotateXYZ(mesh.getRotation().x, mesh.getRotation().y, mesh.getRotation().z);
        if (!mesh.customScaling) modelMatrix.scale(mesh.scale);
        return new Matrix4f(lightViewMatrix).mul(modelMatrix);
    }

    @Override
    public void init() {
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

    public void render(Camera camera) {
        if (!renderVisible || !visible) return;

        GL11.glLineWidth(1);

        modelMatrix.identity().translate(position).rotateXYZ(rotation);
        if (!customScaling) modelMatrix.scale(scale);
        sharedShaderProgram.bind();

        sharedShaderProgram.setUniform("viewPosition", camera.getPosition());
        sharedShaderProgram.setUniform("blockLight", new Vector3f(0.0f, 0.0f, 0.0f));
        sharedShaderProgram.setUniform("emission", 0.0f);
        sharedShaderProgram.setUniform("useTextures", texture != null);

        Light.Light.setLightsToShader(win, sharedShaderProgram);
        sharedShaderProgram.setUniform("projectionMatrix", win.getProjectionMatrix());
        sharedShaderProgram.setUniform("viewMatrix", camera.getViewMatrix());

        Matrix4f modelViewMatrix = new Matrix4f(camera.getViewMatrix()).mul(modelMatrix);
        modelViewMatrix.invert().transpose().get3x3(normMatrix);
        sharedShaderProgram.setUniform("normalMatrix", normMatrix);

        try {
            camera.setViewMatrixUniform(sharedShaderProgram);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        material.setUniformVal(sharedShaderProgram);

        glBindVertexArray(vaoId);

        glActiveTexture(GL_TEXTURE1);
        if (SnowMemo.shadowMap != null) {
            SnowMemo.shadowMap.getDepthMapTexture().bind();
            sharedShaderProgram.setUniform("shadowMap", 1);
        }

        if (texture != null) {
            glActiveTexture(GL_TEXTURE0);
            texture.bind();
            sharedShaderProgram.setUniform("diffuseSampler", 0);
        }

        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);

        boolean hasTransparency = (texture != null && texture.hasAlpha());

        // Configure blending/culling
        if (texture != null) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            glDisable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            if (cullFace) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                glDisable(GL_CULL_FACE);
            }
        }

        // Use stencil outline for all cases when outline is enabled
        if (outline && !hasTransparency) {
            renderStencilOutline();
            return;
        }

        // Main render without outline
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        sharedShaderProgram.setUniform("modelMatrix", modelMatrix);
        sharedShaderProgram.setUniform("useLighting", 1);
        sharedShaderProgram.setUniform("overrideColor", new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));

        if (!hasTransparency && cullFace) {
            glCullFace(GL_BACK);
        } else if (!cullFace) {
            glDisable(GL_CULL_FACE);
        }

        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);

        cleanup();
    }

    private void renderStencilOutline() {
        // Enable stencil test (don't clear - let objects layer properly)
        glEnable(GL_STENCIL_TEST);

        // Step 1: Render object normally, writing unique stencil value
        glStencilFunc(GL_ALWAYS, stencilValue, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
        glStencilMask(0xFF);
        glDepthMask(true); // Ensure depth writing is enabled

        sharedShaderProgram.setUniform("modelMatrix", modelMatrix);
        sharedShaderProgram.setUniform("useLighting", 1);
        sharedShaderProgram.setUniform("overrideColor", new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));

        if (cullFace) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
        } else {
            glDisable(GL_CULL_FACE);
        }

        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);

        // Step 2: Render scaled outline where stencil != this mesh's value
        glStencilFunc(GL_NOTEQUAL, stencilValue, 0xFF);
        glStencilMask(0x00);
        glDepthFunc(GL_LEQUAL); // Use depth test but allow equal depth values

        Matrix4f outlineMatrix = new Matrix4f(modelMatrix).scale(outlineThickness); // Reduced from 1.05f to 1.02f for thinner outline
        sharedShaderProgram.setUniform("modelMatrix", outlineMatrix);
        sharedShaderProgram.setUniform("useLighting", 0);
        sharedShaderProgram.setUniform("overrideColor", outlineColor);

        glDisable(GL_CULL_FACE);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);

        // Step 3: Reset stencil state and disable stencil test
        glStencilMask(0xFF);
        glStencilFunc(GL_ALWAYS, 0, 0xFF);
        glDisable(GL_STENCIL_TEST);
        glEnable(GL_DEPTH_TEST);

        cleanup();
    }

    private void cleanup() {
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glDisableVertexAttribArray(2);
        glBindVertexArray(0);

        if (texture != null) texture.unbind();

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);

        sharedShaderProgram.unbind();
    }

    public void cleanUp() {
        if (queryId != -1) {
            glDeleteQueries(queryId);
        }
        glDisableVertexAttribArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDeleteBuffers(vboId);
        glDeleteBuffers(idxVboId);
        glBindVertexArray(0);
        glDeleteVertexArrays(vaoId);
    }

    public Vector3f getPosition() { return position; }
    public Mesh setPosition(Vector3f position) { this.position = position; return this; }
    public Vector3f getRotation() { return rotation; }
    public Mesh setRotation(Vector3f rotation) { this.rotation = rotation; return this; }
    public Vector3f getScale() { return scale; }
    public Mesh setScale(Vector3f scale) { this.scale = scale; return this; }

    public static class Material {
        Vector4f ambient, diffuse, specular;
        float shininess, metallic, roughness;

        public Material(Vector4f ambient, Vector4f diffuse, Vector4f specular, float shininess, float metallic, float roughness) {
            this.ambient = ambient;
            this.diffuse = diffuse;
            this.specular = specular;
            this.shininess = shininess;
            this.metallic = metallic;
            this.roughness = roughness;
        }

        public Material(Vector4f ambient, Vector4f diffuse, Vector4f specular, float shininess) {
            this(ambient, diffuse, specular, shininess, 0.0f, 0.5f);
        }

        public void setUniformVal(ShaderProgram shaderProgram) {
            shaderProgram.setUniform("material.ambient", ambient);
            shaderProgram.setUniform("material.diffuse", diffuse);
            shaderProgram.setUniform("material.specular", specular);
            shaderProgram.setUniform("material.shininess", shininess);
            shaderProgram.setUniform("material.metallic", metallic);
            shaderProgram.setUniform("material.roughness", roughness);
        }

        @Override
        public String toString() {
            return "Material{ambient=" + ambient + ", diffuse=" + diffuse +
                    ", specular=" + specular + ", shininess=" + shininess +
                    ", metallic=" + metallic + ", roughness=" + roughness + '}';
        }
    }

    public void updateVertices(float[] newVertices) {
        this.vertices = newVertices;
        FloatBuffer verticesBuffer = null;
        try {
            verticesBuffer = MemoryUtil.memAllocFloat(newVertices.length);
            verticesBuffer.put(newVertices).flip();
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_DYNAMIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        } finally {
            if (verticesBuffer != null) memFree(verticesBuffer);
        }
    }

    public float[] getVertices() { return vertices; }
    public void updateIndices(int[] newIndices) {
        this.indices = newIndices;
        IntBuffer indicesBuffer = null;
        try {
            indicesBuffer = MemoryUtil.memAllocInt(newIndices.length);
            indicesBuffer.put(newIndices).flip();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVboId);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_DYNAMIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        } finally {
            if (indicesBuffer != null) memFree(indicesBuffer);
        }
    }

    public int[] getIndices() {
        return indices;
    }
    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }

    public static ShaderProgram occlusionShader;

    public static void initOcclusionShader() throws Exception {
        occlusionShader = new ShaderProgram();
        occlusionShader.createVertexShader(Utils.loadResource("Occlusion.vs"));
        occlusionShader.createFragmentShader(Utils.loadResource("Occlusion.fs"));
        occlusionShader.link();
        occlusionShader.createUniform("projectionMatrix");
        occlusionShader.createUniform("viewMatrix");
        occlusionShader.createUniform("modelMatrix");
    }

    public void renderBoundingBox(Camera camera, ShaderProgram shader) {
        Matrix4f model = new Matrix4f()
                .translation(position)
                .rotateXYZ(rotation);
        if (!customScaling) model.scale(scale);
        shader.bind();
        shader.setUniform("projectionMatrix", win.getProjectionMatrix());
        shader.setUniform("viewMatrix", camera.getViewMatrix());
        shader.setUniform("modelMatrix", model);

        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        shader.unbind();
    }

    public void beginOcclusionQuery(Camera camera) {
        if (occlusionShader == null) {
            try {
                initOcclusionShader();
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize occlusion shader", e);
            }
        }

        if (queryId == -1) queryId = glGenQueries();

        glEnable(GL_DEPTH_TEST);
        glColorMask(false, false, false, false);
        glDepthMask(false);
        glBeginQuery(GL40.GL_ANY_SAMPLES_PASSED, queryId);
        renderBoundingBox(camera, occlusionShader);
        glEndQuery(GL40.GL_ANY_SAMPLES_PASSED);
        glColorMask(true, true, true, true);
        glDepthMask(true);
        queryPending = true;
    }

    public void updateCulling(Frustum frustum) {
        modelMatrix.identity().translate(position).rotateXYZ(rotation);
        if (!customScaling) modelMatrix.scale(scale);
        Vector3f localMin = new Vector3f(Float.MAX_VALUE);
        Vector3f localMax = new Vector3f(-Float.MAX_VALUE);

        for (int i = 0; i + 2 < vertices.length; i += stride) {
            float x = vertices[i], y = vertices[i + 1], z = vertices[i + 2];
            if (x < localMin.x) localMin.x = x;
            if (y < localMin.y) localMin.y = y;
            if (z < localMin.z) localMin.z = z;
            if (x > localMax.x) localMax.x = x;
            if (y > localMax.y) localMax.y = y;
            if (z > localMax.z) localMax.z = z;
        }

        Vector3f worldMin = new Vector3f(Float.MAX_VALUE);
        Vector3f worldMax = new Vector3f(-Float.MAX_VALUE);

        for (int i = 0; i < 8; i++) {
            Vector3f corner = new Vector3f(
                    (i & 1) == 0 ? localMin.x : localMax.x,
                    (i & 2) == 0 ? localMin.y : localMax.y,
                    (i & 4) == 0 ? localMin.z : localMax.z
            );
            modelMatrix.transformPosition(corner);
            if (corner.x < worldMin.x) worldMin.x = corner.x;
            if (corner.y < worldMin.y) worldMin.y = corner.y;
            if (corner.z < worldMin.z) worldMin.z = corner.z;
            if (corner.x > worldMax.x) worldMax.x = corner.x;
            if (corner.y > worldMax.y) worldMax.y = corner.y;
            if (corner.z > worldMax.z) worldMax.z = corner.z;
        }

        if (!frustum.isBoxInFrustum(worldMin, worldMax)) {
            renderVisible = false;
            return;
        }

        if (queryPending) {
            int available = glGetQueryObjecti(queryId, GL_QUERY_RESULT_AVAILABLE);
            if (available != 0) {
                int samples = glGetQueryObjecti(queryId, GL_QUERY_RESULT);
                renderVisible = samples != 0;
                queryPending = false;
            }
        }

        if (!queryPending) renderVisible = true;
    }

    public boolean isRenderVisible() { return renderVisible; }
    public boolean isVisible() { return visible; }
    public Mesh setVisible(boolean visible) { this.visible = visible; return this; }

    public Vector3f getMin() {
        if (vertices == null || vertices.length < 3) return new Vector3f();
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        for (int i = 0; i + 2 < vertices.length; i += stride) {
            if (vertices[i] < min.x) min.x = vertices[i];
            if (vertices[i+1] < min.y) min.y = vertices[i+1];
            if (vertices[i+2] < min.z) min.z = vertices[i+2];
        }
        return modelMatrix.transformPosition(new Vector3f(min));
    }

    public Vector3f getMax() {
        if (vertices == null || vertices.length < 3) return new Vector3f();
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (int i = 0; i + 2 < vertices.length; i += stride) {
            if (vertices[i] > max.x) max.x = vertices[i];
            if (vertices[i+1] > max.y) max.y = vertices[i+1];
            if (vertices[i+2] > max.z) max.z = vertices[i+2];
        }
        return modelMatrix.transformPosition(new Vector3f(max));
    }

    @Override
    public Mesh clone() {
        try {
            Mesh copy = (Mesh) super.clone();
            copy.position = new Vector3f(this.position);
            copy.rotation = new Vector3f(this.rotation);
            copy.scale = new Vector3f(this.scale);
            copy.material = new Material(
                    new Vector4f(this.material.ambient),
                    new Vector4f(this.material.diffuse),
                    new Vector4f(this.material.specular),
                    this.material.shininess,
                    this.material.metallic,
                    this.material.roughness
            );
            copy.texture = this.texture;
            copy.renderVisible = true;
            copy.visible = true;
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}