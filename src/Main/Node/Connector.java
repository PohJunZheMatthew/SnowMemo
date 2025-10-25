package Main.Node;

import Main.*;
import Main.Line3D.Line3D;
import org.joml.Vector3f;

import java.util.Objects;

public class Connector implements Updatable {
    private final Line3D line;
    private final Node parentNode,childNode;
    private final String circleObjString = Utils.loadResource(Objects.requireNonNull(getClass().getResourceAsStream("Circle.obj")));

    public Connector(Node parentNode,Node childNode ,Window window) {
        Updatable.register(window,this);
        this.parentNode = parentNode;
        this.childNode = childNode;
        line = new Line3D(parentNode.outputCircle.getPosition(),childNode.inputCircle.getPosition(),window);
    }

    @Override
    public void update() {
    }
    public void render(Camera c){
        line.render(c);
        line.outline = false;
        line.getStartPos().set(parentNode.outputCircle.getPosition());
        line.getEndPos().set(childNode.inputCircle.getPosition());
    }

    public void cleanUp() {
        line.cleanUp();
    }

    public Node getParentNode() {
        return parentNode;
    }

    public Node getChildNode() {
        return childNode;
    }
}
