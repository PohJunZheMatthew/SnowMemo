package Main.Line3D.Equations;

import org.joml.Vector2f;

public class CubicLine3DEquation implements Line3DEquation {

    @Override
    public Vector2f equate(float x) {
        // Clamp x to [0, 1]
        x = Math.max(0f, Math.min(1f, x));

        float eased;
        if (x < 0.5f) {
            eased = 4f * x * x * x; // cubic in
        } else {
            eased = 1f - (float)Math.pow(-2f * x + 2f, 3f) / 2f; // cubic out
        }

        // You can use eased for y and z or some variation:
        return new Vector2f(eased, eased);
    }
}
