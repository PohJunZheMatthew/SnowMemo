package GUI;

public class AspectRatio {
    GUIComponent object;
    private float widthRatio, heightRatio;
    private float maxWidth, maxHeight;
    private AspectRatioSizeRule aspectRatioSizeRule = AspectRatioSizeRule.USE_WIDTH;
    private AspectRatioPositionRule aspectRatioPositionRule = AspectRatioPositionRule.CENTER;

    public enum AspectRatioSizeRule {
        USE_WIDTH,
        USE_HEIGHT,
        USE_MAXSIZE,
    }

    public enum AspectRatioPositionRule {
        LEFT,
        RIGHT,
        CENTER,
        NONE
    }

    public AspectRatio(GUIComponent object, float widthRatio, float heightRatio) {
        this.object = object;
        this.widthRatio = widthRatio;
        this.heightRatio = heightRatio;
        init();
    }

    public AspectRatio(GUIComponent object, float widthRatio, float heightRatio, AspectRatioSizeRule aspectRatioSizeRule) {
        this.object = object;
        this.widthRatio = widthRatio;
        this.heightRatio = heightRatio;
        this.aspectRatioSizeRule = aspectRatioSizeRule;
        init();
    }

    public AspectRatio(GUIComponent object, float widthRatio, float heightRatio, AspectRatioPositionRule aspectRatioPositionRule) {
        this.object = object;
        this.widthRatio = widthRatio;
        this.heightRatio = heightRatio;
        this.aspectRatioPositionRule = aspectRatioPositionRule;
        init();
    }

    public AspectRatio(GUIComponent object, float widthRatio, float heightRatio, AspectRatioSizeRule aspectRatioSizeRule, AspectRatioPositionRule aspectRatioPositionRule) {
        this.object = object;
        this.widthRatio = widthRatio;
        this.heightRatio = heightRatio;
        this.aspectRatioSizeRule = aspectRatioSizeRule;
        this.aspectRatioPositionRule = aspectRatioPositionRule;
        init();
    }

    public void init() {
        updateSize();
    }

    public void updateSize() {
        float ratio = widthRatio / heightRatio;

        switch (aspectRatioSizeRule) {
            case USE_WIDTH -> {
                // Calculate height based on width
                float widthPx = object.parent != null
                        ? object.width * object.parent.widthPx
                        : object.width * object.windowParent.getWidth();
                float newHeightPx = widthPx / ratio;
                float parentHeight = object.parent != null
                        ? object.parent.heightPx
                        : object.windowParent.getHeight();
                object.setHeight(newHeightPx / parentHeight);
                updatePosition();
            }
            case USE_HEIGHT -> {
                // Calculate width based on height in pixels
                float heightPx = object.parent != null
                        ? object.height * object.parent.heightPx
                        : object.height * object.windowParent.getHeight();
                float newWidthPx = heightPx * ratio;
                float parentWidth = object.parent != null
                        ? object.parent.widthPx
                        : object.windowParent.getWidth();
                object.setWidth(newWidthPx / parentWidth);
                updatePosition();
            }
            case USE_MAXSIZE -> {
                // Fit within max bounds while maintaining aspect ratio
                if (maxWidth > 0 && maxHeight > 0) {
                    float widthBasedHeight = maxWidth / ratio;
                    float heightBasedWidth = maxHeight * ratio;

                    if (widthBasedHeight <= maxHeight) {
                        object.setWidth(maxWidth);
                        object.setHeight(widthBasedHeight);
                    } else {
                        object.setWidth(heightBasedWidth);
                        object.setHeight(maxHeight);
                    }
                    updatePosition();
                }
            }
        }
    }
    private void updatePosition() {
        // This assumes the parent container dimensions are available
        // Adjust based on your GUIComponent implementation
        switch (aspectRatioPositionRule) {
            case LEFT -> {
                // Position at left (x = 0 or relative to parent)
                // object.setX(0);
            }
            case RIGHT -> {
                // Position at right
                // object.setX(parentWidth - object.widthPx);
            }
            case CENTER -> {
                // Position at center
                // object.setX((parentWidth - object.widthPx) / 2);
            }
            case NONE -> {
                // Don't change position
            }
        }
    }

    // Getters and Setters
    public AspectRatioSizeRule getAspectRatioSizeRule() {
        return aspectRatioSizeRule;
    }

    public AspectRatio setAspectRatioSizeRule(AspectRatioSizeRule aspectRatioSizeRule) {
        this.aspectRatioSizeRule = aspectRatioSizeRule;
        updateSize();
        return this;
    }

    public AspectRatioPositionRule getAspectRatioPositionRule() {
        return aspectRatioPositionRule;
    }

    public AspectRatio setAspectRatioPositionRule(AspectRatioPositionRule aspectRatioPositionRule) {
        this.aspectRatioPositionRule = aspectRatioPositionRule;
        updatePosition();
        return this;
    }

    public float getWidthRatio() {
        return widthRatio;
    }

    public AspectRatio setWidthRatio(float widthRatio) {
        this.widthRatio = widthRatio;
        updateSize();
        return this;
    }

    public float getHeightRatio() {
        return heightRatio;
    }

    public AspectRatio setHeightRatio(float heightRatio) {
        this.heightRatio = heightRatio;
        updateSize();
        return this;
    }

    public float getMaxWidth() {
        return maxWidth;
    }

    public AspectRatio setMaxWidth(float maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    public float getMaxHeight() {
        return maxHeight;
    }

    public AspectRatio setMaxHeight(float maxHeight) {
        this.maxHeight = maxHeight;
        return this;
    }
}