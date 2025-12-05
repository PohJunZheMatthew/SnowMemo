package GUI;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenAccessor;

public class GUIComponentAccessor implements TweenAccessor<GUIComponent> {
    static{
        Tween.registerAccessor(GUIComponent.class,new GUIComponentAccessor());
    }
    public static final int POSITION_X_ABS = 0;
    public static final int POSITION_Y_ABS = 1;
    public static final int POSITION_X = 2;
    public static final int POSITION_Y = 3;
    public static final int POSITION_XY_ABS = 4;
    public static final int POSITION_XY = 5;

    public static final int SIZE_X_ABS = 6;
    public static final int SIZE_Y_ABS = 7;
    public static final int SIZE_X = 8;
    public static final int SIZE_Y = 9;
    public static final int SIZE_XY_ABS = 10;
    public static final int SIZE_XY = 11;

    @Override
    public int getValues(GUIComponent target, int tweenType, float[] returnValues) {
        switch (tweenType) {
            case POSITION_X_ABS:
                returnValues[0] = target.getxPx();
                return 1;
            case POSITION_Y_ABS:
                returnValues[0] = target.getyPx();
                return 1;
            case POSITION_X:
                returnValues[0] = target.getX();
                return 1;
            case POSITION_Y:
                returnValues[0] = target.getY();
                return 1;
            case POSITION_XY_ABS:
                returnValues[0] = target.getxPx();
                returnValues[1] = target.getyPx();
                return 2;
            case POSITION_XY:
                returnValues[0] = target.getX();
                returnValues[1] = target.getY();
                return 2;

            case SIZE_X_ABS:
                returnValues[0] = target.getWidthPx();
                return 1;
            case SIZE_Y_ABS:
                returnValues[0] = target.getHeightPx();
                return 1;
            case SIZE_X:
                returnValues[0] = target.getWidth();
                return 1;
            case SIZE_Y:
                returnValues[0] = target.getHeight();
                return 1;
            case SIZE_XY_ABS:
                returnValues[0] = target.getWidthPx();
                returnValues[1] = target.getHeightPx();
                return 2;
            case SIZE_XY:
                returnValues[0] = target.getWidth();
                returnValues[1] = target.getHeight();
                return 2;
        }
        return 0;
    }

    @Override
    public void setValues(GUIComponent target, int tweenType, float[] newValues) {
        switch (tweenType) {
            case POSITION_X:
                target.setX( newValues[0]);
                break;
            case POSITION_Y:
                target.setY( newValues[0]);
                break;
            case POSITION_XY:
                target.setX( newValues[0]);
                target.setY( newValues[1]);
                break;
            case SIZE_X:
                target.setWidth( newValues[0]);
                break;
            case SIZE_Y:
                target.setHeight( newValues[0]);
                break;
            case SIZE_XY:
                target.setWidth( newValues[0]);
                target.setHeight( newValues[1]);
                break;
        }
    }
}
