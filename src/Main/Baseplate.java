package Main;

import org.joml.Vector3f;

public class Baseplate extends Mesh{
    static float repeat = Short.MAX_VALUE;
    static final float[] positions = new float[]{
            // x, y, z,      nx, ny, nz,       u, v
            -0.5f, 0f, -0.5f,   0f, 1f, 0f,   0.0f*repeat, 0.0f*repeat,
            0.5f, 0f, -0.5f,   0f, 1f, 0f,   repeat, 0.0f,
            0.5f, 0f,  0.5f,   0f, 1f, 0f,   repeat, repeat,
            -0.5f, 0f,  0.5f,   0f, 1f, 0f,   0.0f*repeat, repeat,
    };

    static final int[] indices = new int[]{
            0, 1, 2,
            2, 3, 0
    };
    public Baseplate() {
        super(positions, indices, Window.getCurrentWindow(),Texture.loadTexture(SnowMemo.class.getResourceAsStream("BasePlateTexture.png")));
        setPosition(new Vector3f(0,-5,0));
        setScale(new Vector3f(repeat,repeat,repeat));
        setRotation(new Vector3f(0, (float) Math.PI,0));
        material.roughness = 0;
    }
}
