package Main;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position = new Vector3f(0f, 0, 5);
    private Vector3f rotation = new Vector3f(0,0,0);
    private Matrix4f viewMatrix = new Matrix4f();

    public enum CameraMovement {
        ZoomInAndOut,
        ScrollUpAndDown
    }
    public Plane getCameraFacingPlane(float distance) {
        Vector3f planeCenter = new Vector3f(position);
        planeCenter.add(new Vector3f(0, 0, -distance)); // Move plane in front of camera

        // For a simple orthographic-like setup, the plane normal is just (0,0,1)
        Vector3f planeNormal = new Vector3f(0, 0, 1);

        // If you have camera rotation, apply it to the normal
        if (rotation.x != 0 || rotation.y != 0 || rotation.z != 0) {
            Matrix4f rotMatrix = new Matrix4f().identity()
                    .rotate((float) Math.toRadians(rotation.x), new Vector3f(1, 0, 0))
                    .rotate((float) Math.toRadians(rotation.y), new Vector3f(0, 1, 0))
                    .rotate((float) Math.toRadians(rotation.z), new Vector3f(0, 0, 1));
            rotMatrix.transformDirection(planeNormal);
        }

        return new Plane(planeCenter, planeNormal);
    }

    // Simple screen-to-world using camera-facing plane
    public Vector3f screenToWorldPlane(float screenX, float screenY, float fov, float aspectRatio,
                                       int windowWidth, int windowHeight, float planeDistance) {
        // Convert to NDC
        float normalizedX = (2.0f * screenX) / windowWidth - 1.0f;
        float normalizedY = 1.0f - (2.0f * screenY) / windowHeight;

        // Calculate the plane size at the given distance
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov * 0.5f));
        float planeHeight = 2.0f * tanHalfFov * planeDistance;
        float planeWidth = planeHeight * aspectRatio;

        // Calculate world position on the plane
        float worldX = position.x + normalizedX * (planeWidth * 0.5f);
        float worldY = position.y + normalizedY * (planeHeight * 0.5f);
        float worldZ = position.z - planeDistance; // Plane is in front of camera

        return new Vector3f(worldX, worldY, worldZ);
    }

    // Plane class for intersection calculations
    public static class Plane {
        public final Vector3f center;
        public final Vector3f normal;

        public Plane(Vector3f center, Vector3f normal) {
            this.center = new Vector3f(center);
            this.normal = new Vector3f(normal).normalize();
        }

        // Ray-plane intersection
        public Vector3f intersect(Ray ray) {
            float denom = normal.dot(ray.direction);
            if (Math.abs(denom) < 1e-6f) {
                return null; // Ray parallel to plane
            }

            Vector3f p0l0 = new Vector3f(center).sub(ray.origin);
            float t = p0l0.dot(normal) / denom;

            if (t < 0) {
                return null; // Intersection behind ray origin
            }

            return ray.getPoint(t);
        }
    }

    public CameraMovement cameraMovement = CameraMovement.ZoomInAndOut;

    public void setViewMatrixUniform(ShaderProgram shaderProgram) throws Exception {
        updateViewMatrix();

        if (shaderProgram.hasUniform("viewMatrix")) {
            shaderProgram.setUniform("viewMatrix", viewMatrix);
        } else {
            shaderProgram.createUniform("viewMatrix");
            shaderProgram.setUniform("viewMatrix", viewMatrix);
        }

        if (!shaderProgram.hasUniform("viewPosition")) {
            shaderProgram.createUniform("viewPosition");
        }
        shaderProgram.setUniform("viewPosition", position);
    }

    private void updateViewMatrix() {
        viewMatrix.identity();
        viewMatrix.rotate((float) Math.toRadians(rotation.x), new Vector3f(1, 0, 0))
                .rotate((float) Math.toRadians(rotation.y), new Vector3f(0, 1, 0))
                .rotate((float) Math.toRadians(rotation.z), new Vector3f(0, 0, 1));
        viewMatrix.translate(-position.x, -position.y, -position.z);
    }

    public Matrix4f getViewMatrix() {
        updateViewMatrix();
        return new Matrix4f(viewMatrix);
    }

    public Vector3f getPosition(Vector3f position) {
        return position.set(getPosition());
    }
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public Camera setPosition(Vector3f position) {
        this.position.set(position);
        return this;
    }

    public void move(Vector3f vector3f) {
        position.add(vector3f);
    }

    public Vector3f getRotation() {
        return new Vector3f(rotation);
    }

    public Camera setRotation(Vector3f rotation) {
        this.rotation.set(rotation);
        return this;
    }

    // Simple screen-to-world conversion that works with your orthographic-like setup
    public Vector3f screenToWorld(float screenX, float screenY, float fov, float aspectRatio, int windowWidth, int windowHeight) {
        // Convert screen coordinates to normalized device coordinates (-1 to 1)
        float normalizedX = (2.0f * screenX) / windowWidth - 1.0f;
        float normalizedY = 1.0f - (2.0f * screenY) / windowHeight; // Flip Y axis

        // Calculate the size of the view at the camera's current Z distance
        float distance = Math.abs(position.z);
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov * 0.5f));
        float viewHeight = 2.0f * tanHalfFov * distance;
        float viewWidth = viewHeight * aspectRatio;

        // Convert normalized coordinates to world coordinates
        float worldX = position.x + normalizedX * (viewWidth * 0.5f);
        float worldY = position.y + normalizedY * (viewHeight * 0.5f);

        return new Vector3f(worldX, worldY, 0.0f); // Assuming Z=0 ground plane
    }

    // Alternative ray-based method
    public Ray getScreenRay(float normalizedX, float normalizedY, float fov, float aspectRatio) {
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov * 0.5f));
        float viewX = normalizedX * tanHalfFov * aspectRatio;
        float viewY = normalizedY * tanHalfFov;

        // For your simple camera setup, create ray pointing forward
        Vector3f rayOrigin = new Vector3f(position);
        Vector3f rayDirection = new Vector3f(viewX, viewY, -1.0f).normalize();

        // Apply any camera rotation
        if (rotation.x != 0 || rotation.y != 0 || rotation.z != 0) {
            Matrix4f rotMatrix = new Matrix4f().identity()
                    .rotate((float) Math.toRadians(rotation.x), new Vector3f(1, 0, 0))
                    .rotate((float) Math.toRadians(rotation.y), new Vector3f(0, 1, 0))
                    .rotate((float) Math.toRadians(rotation.z), new Vector3f(0, 0, 1));
            rotMatrix.transformDirection(rayDirection);
        }

        return new Ray(rayOrigin, rayDirection);
    }

    public static class Ray {
        public final Vector3f origin;
        public final Vector3f direction;

        public Ray(Vector3f origin, Vector3f direction) {
            this.origin = new Vector3f(origin);
            this.direction = new Vector3f(direction).normalize();
        }

        public Vector3f getPoint(float distance) {
            return new Vector3f(origin).add(new Vector3f(direction).mul(distance));
        }

        public Vector3f projectToZ(float targetZ) {
            if (Math.abs(direction.z) < 1e-6f) {
                return null; // Ray parallel to Z plane
            }

            float t = (targetZ - origin.z) / direction.z;
            if (t < 0) {
                return null; // Intersection behind camera
            }

            return getPoint(t);
        }
    }
}