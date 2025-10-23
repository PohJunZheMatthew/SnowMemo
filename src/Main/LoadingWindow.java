package Main;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class LoadingWindow {
    private float value;
    private float maxValue;
    float percentage = 0;
    String description = "";
    final long window;
    public LoadingWindow(int width,int height){
        if (!GLFW.glfwInit()){
            System.err.println("Can't init GLFW");
        }
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GL11.GL_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GL11.GL_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_CLASS_NAME, "Snow Memo");
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_INSTANCE_NAME, "Snow Memo");
        GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, 24);
        window = GLFW.glfwCreateWindow(width,height,"Loading", MemoryUtil.NULL, Window.getCurrentWindow().window);
        GLCapabilities glCapabilities = GL.createCapabilities();
        GLCapabilities.initialize();
    }
    public void setDescription(String description){
        this.description = description;
    }
    
    public float getValue() {
        return value;
    }

    public LoadingWindow setValue(float value) {
        this.value = value;
        return this;
    }

    public float getMaxValue() {
        return maxValue;
    }

    public LoadingWindow setMaxValue(float maxValue) {
        this.maxValue = maxValue;
        return this;
    }
}
