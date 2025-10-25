package Main;

public class MainMenuScreen implements Renderable{
    protected boolean visible = true;
    private Mesh backgroundCube, menuButtonsBackgroundCube;
    private Window window;
    public MainMenuScreen(Window window) {
        this.window = window;
    }

    @Override
    public void render() {
        if (!visible) return;
        menuButtonsBackgroundCube.render(window.camera);
        backgroundCube.render(window.camera);
    }

    @Override
    public void init() {
        backgroundCube = Utils.loadObj(MainMenuScreen.class.getResourceAsStream("Cube.obj"),window);
        menuButtonsBackgroundCube = Utils.loadObj(MainMenuScreen.class.getResourceAsStream("Cube.obj"),window);
    }

    @Override
    public void cleanUp() {
        backgroundCube.cleanUp();
        menuButtonsBackgroundCube.cleanUp();
    }
}
