package Light;

import Main.ShaderProgram;
import org.joml.Vector4f;

public class AmbientLight {
    float[] color = new float[]{1f,1f,1f,1f};
    public AmbientLight(){}

    public AmbientLight(float[] color) {
        this.color = color;
    }
    public AmbientLight(float r,float g,float b,float a){
        color = new float[]{r,g,b,a};
    }

    public void setRed(float r){
        color[0] = r;
    }
    public void setGreen(float g){
        color[1] = g;
    }
    public void setBlue(float b){
        color[2] = b;
    }
    public void setAlpha(float a){
        color[3] = a;
    }

    public float[] getColor() {
        return color;
    }
    public Vector4f getVec4Color(){
        return new Vector4f(color);
    }
    public void setAmbientUniform(ShaderProgram shaderProgram){
        if (!shaderProgram.hasUniform("globalAmbient")) {
            try {
                shaderProgram.createUniform("globalAmbient");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        shaderProgram.setUniform("globalAmbient",getVec4Color());
    }
}
