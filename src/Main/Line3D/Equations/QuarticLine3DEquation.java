package Main.Line3D.Equations;

import org.joml.Vector2f;

public class QuarticLine3DEquation implements Line3DEquation{
    @Override
    public Vector2f equate(float x) {
        if (x < 0.5f) {
            float ans = 8 * x * x * x * x;
            return new Vector2f(ans,ans);
        } else {
            float t2 = 1 - x;
            float ans = 1 - 8 * t2 * t2 * t2 * t2;
            return new Vector2f(ans,ans);
        }
    }
}
