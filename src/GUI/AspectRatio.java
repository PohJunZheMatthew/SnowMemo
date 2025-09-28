package GUI;

public class AspectRatio {
    GUIComponent object;
    private float width,height,MAX_Width,MAX_Height;
    private AspectRatioSizeRule aspectRatioSizeRule = AspectRatioSizeRule.USE_Width;
    private AspectRatioPositionRule aspectRatioPositionRule = AspectRatioPositionRule.CENTER;
    public enum AspectRatioSizeRule{
        USE_Width,
        USE_Height,
        USE_MAXSIZE,
    }
    public enum AspectRatioPositionRule{
        LEFT,
        RIGHT,
        CENTER,
        NONE
    }
    public AspectRatio(GUIComponent object,float width,float height){
        this.object = object;
        this.width = width;
        this.height = height;
        init();
    }
    public AspectRatio(GUIComponent object,float width,float height,AspectRatioSizeRule aspectRatioSizeRule){
        this.object = object;
        this.width = width;
        this.height = height;
        this.aspectRatioSizeRule = aspectRatioSizeRule;
        init();
    }
    public AspectRatio(GUIComponent object,float width,float height,AspectRatioPositionRule aspectRatioPositionRule){
        this.object = object;
        this.width = width;
        this.height = height;
        this.aspectRatioPositionRule = aspectRatioPositionRule;
        init();
    }
    public AspectRatio(GUIComponent object,float width,float height,AspectRatioSizeRule aspectRatioSizeRule,AspectRatioPositionRule aspectRatioPositionRule){
        this.object = object;
        this.width = width;
        this.height = height;
        this.aspectRatioSizeRule = aspectRatioSizeRule;
        this.aspectRatioPositionRule = aspectRatioPositionRule;
        init();
    }
    public void init(){

    }
}
