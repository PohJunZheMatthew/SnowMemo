package Main.Line3D;

import Main.*;
import Main.Line3D.Equations.CubicLine3DEquation;
import Main.Line3D.Equations.Line3DEquation;
import Main.Line3D.Equations.QuarticLine3DEquation;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.Objects;

public class Line3D extends Mesh implements Updatable {
    private static final int RES = 16; // number of subdivisions
    public Line3DEquation equation = new QuarticLine3DEquation();

    private static final String baseVertices = Utils.loadResource(
            Objects.requireNonNull(Line3D.class.getResourceAsStream("Line3D.obj"))
    );

    private final Vector3f startPos;
    private final Vector3f endPos;
    private final float[] originalVerts;
    private final int[] originalIndices;
    private final float[] updatingVerts;
    private Vector4f color = new Vector4f();

    private volatile float[] pendingVerts = null;
    private volatile int[] pendingIndices = null;

    private static final float MIN_LINE_LENGTH = 0.01f; // Minimum visible length

    // Track the last values we calculated from
    private final Vector3f lastStartPos = new Vector3f();
    private final Vector3f lastEndPos = new Vector3f();

    public Line3D(Vector3f startPos, Vector3f endPos, Window window) {
        super(Utils.loadObjFromString(baseVertices, Line3D.class, window), window);
        this.outline = false;
        this.startPos = new Vector3f(startPos);
        this.endPos = new Vector3f(endPos);
        this.originalVerts = getVertices().clone();
        this.originalIndices = getIndices().clone();
        this.updatingVerts = originalVerts.clone();

        Updatable.register(window, this);

        // Initialize tracking
        lastStartPos.set(Float.NaN, Float.NaN, Float.NaN); // Force first update
        lastEndPos.set(Float.NaN, Float.NaN, Float.NaN);

        setPosition(startPos);
        update();

        setMaterial(new Material(
                new Vector4f(1, 0.5f, 0.5f, 1),
                new Vector4f(1, 0.5f, 0.5f, 1),
                new Vector4f(1, 0.5f, 0.5f, 1),
                32
        ));

        makeVerticesUnique();
        blenderAutoSmooth(30f);
    }

    @Override
    public void update() {
        // Only recalculate if start or end position has changed
        if (startPos.equals(lastStartPos) && endPos.equals(lastEndPos)) {
            return;
        }

        // Update position first
        setPosition(startPos);

        // Calculate offset from start to end
        Vector3f offset = new Vector3f(endPos).sub(startPos);

        // Check if the line is too short and would disappear
        float length = offset.length();
        if (length < MIN_LINE_LENGTH) {
            // Keep the line at minimum visible length in the direction it was going
            if (length > 0) {
                offset.normalize().mul(MIN_LINE_LENGTH);
            } else {
                // If start and end are exactly the same, default to a small offset in X direction
                offset.set(MIN_LINE_LENGTH, 0, 0);
            }
        }

        // Determine if we need to flip based on X direction
        boolean flipWinding = offset.x < 0;

        // Transform vertices based on the offset
        // Use the actual offset values (including sign) for proper positioning
        for (int i = 0; i < originalVerts.length; i += 3) {
            float x = originalVerts[i + 2];
            Vector2f result = equation.equate(x);

            updatingVerts[i] = originalVerts[i] + result.x * offset.z;
            updatingVerts[i + 1] = originalVerts[i + 1] + result.y * offset.y;
            updatingVerts[i + 2] = originalVerts[i + 2] * offset.x;
        }

        // Handle winding order for negative X offset
        if (flipWinding) {
            int[] flippedIndices = new int[originalIndices.length];
            for (int i = 0; i < originalIndices.length; i += 3) {
                // Reverse the triangle winding order
                flippedIndices[i] = originalIndices[i + 2];
                flippedIndices[i + 1] = originalIndices[i + 1];
                flippedIndices[i + 2] = originalIndices[i];
            }
            pendingIndices = flippedIndices;
        } else {
            pendingIndices = originalIndices.clone();
        }

        pendingVerts = updatingVerts.clone();

        // Remember what we calculated from
        lastStartPos.set(startPos);
        lastEndPos.set(endPos);
    }

    @Override
    public void render(Camera camera) {
        blenderAutoSmooth(30f);
        // Check if we need to update before rendering
        if (!startPos.equals(lastStartPos) || !endPos.equals(lastEndPos)) {
            update();
        }

        if (pendingVerts != null) {
            updateVertices(pendingVerts);
            pendingVerts = null;
        }

        if (pendingIndices != null) {
            updateIndices(pendingIndices);
            pendingIndices = null;
        }

        super.render(camera);
    }

    public Vector3f getStartPos() {
        return startPos;
    }

    public Vector3f getEndPos() {
        return endPos;
    }

    // Helper method to force an update if you modify startPos/endPos directly
    public void forceUpdate() {
        lastStartPos.set(Float.NaN, Float.NaN, Float.NaN);
        lastEndPos.set(Float.NaN, Float.NaN, Float.NaN);
        update();
    }
}