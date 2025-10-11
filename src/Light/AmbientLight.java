package Light;

import Main.ShaderProgram;
import org.joml.Vector4f;

public class AmbientLight {
    private Vector4f color;

    public AmbientLight() {
        this.color = new Vector4f(1f,1f,1f,1f);
    }

    public AmbientLight(Vector4f color) {
        this.color = color;
    }

    public AmbientLight(float v, float v1, float v2, float v3) {
        this.color = new Vector4f(v,v1,v2,v3);
    }

    public void setAmbientUniform(ShaderProgram shaderProgram) {
        if (!shaderProgram.hasUniform("globalAmbient")) {
            try {
                shaderProgram.createUniform("globalAmbient");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        shaderProgram.setUniform("globalAmbient", color);
    }
}
