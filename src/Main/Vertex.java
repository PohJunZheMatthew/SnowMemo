package Main;

import java.util.Objects;

public class Vertex {
    int v,vt,vn;

    public Vertex(int v, int vt, int vn) {
        this.v = v;
        this.vt = vt;
        this.vn = vn;
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Vertex)) return false;
        Vertex other = (Vertex) o;
        return v == other.v && vt == other.vt && vn == other.vn;
    }
    @Override public int hashCode() {
        return Objects.hash(v, vt, vn);
    }
}
