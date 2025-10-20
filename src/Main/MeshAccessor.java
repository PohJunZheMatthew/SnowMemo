package Main;

import aurelienribon.tweenengine.TweenAccessor;
import org.joml.Vector3f;

public class MeshAccessor implements TweenAccessor<Mesh> {
    public static final int POSITION_X = 1;
    public static final int POSITION_Y = 2;
    public static final int POSITION_Z = 3;
    public static final int POSITION_XYZ = 4;
    @Override
    public int getValues(Mesh target, int tweenType, float[] returnValues) {
        switch (tweenType) {
            case POSITION_X: returnValues[0] = target.getPosition().x; return 1;
            case POSITION_Y: returnValues[0] = target.getPosition().y; return 1;
            case POSITION_Z: returnValues[0] = target.getPosition().z; return 1;
            case POSITION_XYZ:{
                returnValues[0] = target.getPosition().x;
                returnValues[1] = target.getPosition().y;
                returnValues[2] = target.getPosition().z;
                return 3;
            }
            default: assert false; return -1;
        }
    }

    @Override
    public void setValues(Mesh target, int tweenType, float[] newValues) {
        switch (tweenType) {
            case POSITION_X: target.setPosition(new Vector3f(newValues[0],target.getPosition().y,target.getPosition().z)); break;
            case POSITION_Y: target.setPosition(new Vector3f(target.getPosition().x,newValues[0],target.getPosition().z)); break;
            case POSITION_Z: target.setPosition(new Vector3f(target.getPosition().x,target.getPosition().y,newValues[0])); break;
            case POSITION_XYZ:{
                Vector3f pos = target.getPosition();
                pos.set(newValues[0], newValues[1], newValues[2]);
                target.setPosition(pos);
                break;
            }
            default: assert false; break;
        }
    }
}
