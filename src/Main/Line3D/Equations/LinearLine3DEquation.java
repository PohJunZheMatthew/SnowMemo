package Main.Line3D.Equations;

import org.joml.Vector2f;

public class LinearLine3DEquation implements Line3DEquation {

    @Override
    public Vector2f equate(float x) {
        return new Vector2f(x,x);
    }
}
