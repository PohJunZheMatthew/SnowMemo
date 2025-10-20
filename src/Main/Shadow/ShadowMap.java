package Main.Shadow;

import Light.DirectionalLight;
import Light.Light;
import Light.PointLight;
import Main.Mesh;
import Main.ShaderProgram;
import Main.Texture;
import Main.Utils;
import Main.Window;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public class ShadowMap {

    public static final int SHADOW_MAP_WIDTH = 2048;
    public static final int SHADOW_MAP_HEIGHT = 2048;

    private final int depthMapFBO;
    private final ShadowTexture depthMap;
    private ShaderProgram depthShaderProgram;

    // Shadow map configuration
    private Vector3f lightDirection;
    private float shadowDistance = 100f;

    // Orthographic projection bounds
    public static class OrthoBounds {
        public float left = -50f;
        public float right = 50f;
        public float bottom = -50f;
        public float top = 50f;
        public float near = 0.1f;
        public float far = 200f;

        public OrthoBounds() {}

        public OrthoBounds(float size, float near, float far) {
            this.left = -size;
            this.right = size;
            this.bottom = -size;
            this.top = size;
            this.near = near;
            this.far = far;
        }
    }

    private OrthoBounds orthoBounds = new OrthoBounds(50f, 0.1f, 200f);
    private Matrix4f lightViewMatrix = new Matrix4f();
    private Matrix4f orthoProjMatrix = new Matrix4f();
    private Matrix4f lightSpaceMatrix = new Matrix4f();

    public ShadowMap() throws Exception {
        // Create a FBO to render the depth map
        depthMapFBO = glGenFramebuffers();

        // Create the depth map texture
        depthMap = new ShadowTexture(SHADOW_MAP_WIDTH, SHADOW_MAP_HEIGHT, GL_DEPTH_COMPONENT);

        // Attach the depth map texture to the FBO
        glBindFramebuffer(GL_FRAMEBUFFER, depthMapFBO);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthMap.getId(), 0);

        // Set only depth
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new Exception("Could not create FrameBuffer");
        }

        setupDepthShader();

        // Unbind
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Render shadow map for a list of meshes
     */
    public void render(Window window, List<Mesh> meshes, Vector3f sceneCenter) {
        // Get primary light direction
        updateLightDirection(window, sceneCenter);

        if (lightDirection == null) {
            return; // No lights to cast shadows
        }

        // Update matrices
        updateLightViewMatrix(lightDirection, sceneCenter);
        updateOrthoProjectionMatrix();

        // Calculate light space matrix
        lightSpaceMatrix.set(orthoProjMatrix).mul(lightViewMatrix);

        // Begin shadow map rendering
        glBindFramebuffer(GL_FRAMEBUFFER, depthMapFBO);
        glViewport(0, 0, SHADOW_MAP_WIDTH, SHADOW_MAP_HEIGHT);
        glClear(GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        glCullFace(GL_FRONT); // Prevent peter panning

        depthShaderProgram.bind();
        depthShaderProgram.setUniform("orthoProjectionMatrix", orthoProjMatrix);

        // Render each mesh
        for (Mesh mesh : meshes) {
            Matrix4f modelLightViewMatrix = buildModelViewMatrix(mesh, lightViewMatrix);
            depthShaderProgram.setUniform("modelLightViewMatrix", modelLightViewMatrix);

            // Draw the mesh
            glBindVertexArray(mesh.getVaoId());
            glEnableVertexAttribArray(0);
            glDrawElements(GL_TRIANGLES, mesh.getVertexCount(), GL_UNSIGNED_INT, 0);
            glDisableVertexAttribArray(0);
        }

        depthShaderProgram.unbind();
        glCullFace(GL_BACK);

        // Restore default framebuffer
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Updates light direction from the window's lights
     */
    private void updateLightDirection(Window window, Vector3f target) {
        // Try to get directional light first
        List<DirectionalLight> dirLights = Light.getDirectionalLights(window);
        if (!dirLights.isEmpty()) {
            lightDirection = new Vector3f(dirLights.get(0).getDirection()).normalize();
            return;
        }

        // Fallback to point light
        List<PointLight> pointLights = Light.getPointLights(window);
        if (!pointLights.isEmpty()) {
            PointLight pl = pointLights.get(0);
            lightDirection = new Vector3f(pl.getPosition()).sub(target).normalize();
            return;
        }

        // Default light direction if no lights exist
        lightDirection = new Vector3f(1f, -1f, 1f).normalize();
    }

    /**
     * Updates the light view matrix
     */
    private void updateLightViewMatrix(Vector3f lightDir, Vector3f target) {
        Vector3f lightPos = new Vector3f(lightDir).normalize().mul(-shadowDistance).add(target);
        Vector3f up = new Vector3f(0, 1, 0);

        // If light direction is parallel to up vector, use different up
        if (Math.abs(lightDir.dot(up)) > 0.99f) {
            up.set(1, 0, 0);
        }

        lightViewMatrix.identity();
        lightViewMatrix.lookAt(lightPos, target, up);
    }

    /**
     * Updates orthographic projection matrix
     */
    private void updateOrthoProjectionMatrix() {
        orthoProjMatrix.identity();
        orthoProjMatrix.ortho(
                orthoBounds.left,
                orthoBounds.right,
                orthoBounds.bottom,
                orthoBounds.top,
                orthoBounds.near,
                orthoBounds.far
        );
    }

    /**
     * Builds model-view matrix for shadow rendering
     * This can be used from Mesh class as well
     */
    public static Matrix4f buildModelViewMatrix(Mesh mesh, Matrix4f viewMatrix) {
        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.identity()
                .translate(mesh.getPosition())
                .rotateXYZ(mesh.getRotation().x, mesh.getRotation().y, mesh.getRotation().z)
                .scale(mesh.getScale());

        Matrix4f modelViewMatrix = new Matrix4f(viewMatrix);
        modelViewMatrix.mul(modelMatrix);

        return modelViewMatrix;
    }

    public Texture getDepthMapTexture() {
        return depthMap;
    }

    public int getDepthMapFBO() {
        return depthMapFBO;
    }

    public Matrix4f getLightSpaceMatrix() {
        return lightSpaceMatrix;
    }

    public void setOrthoBounds(OrthoBounds bounds) {
        this.orthoBounds = bounds;
    }

    public void setShadowDistance(float distance) {
        this.shadowDistance = distance;
    }

    public void cleanup() {
        glDeleteFramebuffers(depthMapFBO);
        depthMap.cleanup();
        if (depthShaderProgram != null) {
            depthShaderProgram.cleanup();
        }
    }

    private void setupDepthShader() throws Exception {
        depthShaderProgram = new ShaderProgram();
        depthShaderProgram.createVertexShader(Utils.loadResource(
                Objects.requireNonNull(this.getClass().getResourceAsStream("depth_vertex.vs"))));
        depthShaderProgram.createFragmentShader(Utils.loadResource(
                Objects.requireNonNull(this.getClass().getResourceAsStream("depth_fragment.fs"))));
        depthShaderProgram.link();

        depthShaderProgram.createUniform("orthoProjectionMatrix");
        depthShaderProgram.createUniform("modelLightViewMatrix");
    }

    private static class ShadowTexture extends Texture {
        private final int width;
        private final int height;

        public ShadowTexture(int width, int height, int pixelFormat) throws Exception {
            super(width,height,glGenTextures(),true);
            this.width = width;
            this.height = height;

            glBindTexture(GL_TEXTURE_2D, this.getId());
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, this.width, this.height,
                    0, pixelFormat, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }

        public int getId() {
            return getTextureid();
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}