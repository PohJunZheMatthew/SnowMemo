package Main;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Frustum {
    private final Plane[] planes = new Plane[6];

    public Frustum() {
        for (int i = 0; i < 6; i++) {
            planes[i] = new Plane();
        }
    }

    public void update(Matrix4f projViewMatrix) {
        float[] m = new float[16];
        projViewMatrix.get(m);

        planes[0].set(m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12]);
        planes[1].set(m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12]);
        planes[2].set(m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13]);
        planes[3].set(m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13]);
        planes[4].set(m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14]);
        planes[5].set(m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14]);

        for (Plane p : planes) p.normalize();
    }

    public boolean isBoxInFrustum(Vector3f min, Vector3f max) {
        for (Plane p : planes) {
            if (p.distanceToPoint(new Vector3f(
                    p.normal.x > 0 ? max.x : min.x,
                    p.normal.y > 0 ? max.y : min.y,
                    p.normal.z > 0 ? max.z : min.z
            )) < 0) {
                return false;
            }
        }
        return true;
    }

    public static class Plane {
        Vector3f normal = new Vector3f();
        float d;

        void set(float a, float b, float c, float d) {
            normal.set(a, b, c);
            this.d = d;
        }

        void normalize() {
            float length = normal.length();
            normal.div(length);
            d /= length;
        }

        float distanceToPoint(Vector3f point) {
            return normal.dot(point) + d;
        }
    }
}
