package Main;
/*
* This allows me to create a single list which contains GUIComponent and Mesh together for rendering.
* <code>render</code>
*/
public interface Renderable {
    void render();
    void init();
    void cleanUp();
}