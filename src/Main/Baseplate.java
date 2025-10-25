package Main;

import org.joml.Vector3f;
import org.joml.Vector4f;

public class Baseplate extends Mesh{
    static float repeat = 1000;
    static final float[] positions = new float[]{
            // x, y, z,      nx, ny, nz,       u, v
            -0.5f, 0f, -0.5f,   0f, 1f, 0f,   0.0f*repeat, 0.0f*repeat,
            0.5f, 0f, -0.5f,   0f, 1f, 0f,   repeat, 0.0f,
            0.5f, 0f,  0.5f,   0f, 1f, 0f,   repeat, repeat,
            -0.5f, 0f,  0.5f,   0f, 1f, 0f,   0.0f*repeat, repeat,
    };

    static final int[] indices = new int[]{
            0, 3, 2,
            2, 1, 0
    };
    public Baseplate() {
        super(positions, indices, Window.getCurrentWindow(),Texture.loadTexture(SnowMemo.class.getResourceAsStream("BasePlateTexture.png")));
        setPosition(new Vector3f(0,-5f,0));
        setScale(new Vector3f(repeat,1,repeat));
        material.roughness = 0;
        material.ambient = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);   // Full brightness
        material.diffuse = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
