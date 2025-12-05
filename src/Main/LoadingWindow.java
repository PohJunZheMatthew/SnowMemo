package Main;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class LoadingWindow {
    private long window;
    private long parentWindow;
    private int width, height;

    private float value = 0f;
    private float maxValue = 100f;
    private String description = "Loading...";

    private int vao, vbo, textVao, textVbo;
    private int shaderProgram;
    private int textShaderProgram;
    private BufferedImage descriptionImage;
    private Texture descriptionTexture;

    public LoadingWindow(long parent, int width, int height) {
        this.width = width;
        this.height = height;
        this.parentWindow = parent;

        if (!GLFW.glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(width, height, "Loading...", MemoryUtil.NULL, parent);
        if (window == MemoryUtil.NULL) throw new RuntimeException("Failed to create GLFW window");

        var vidMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        if (vidMode != null) {
            GLFW.glfwSetWindowPos(window,
                    (vidMode.width() - width) / 2,
                    (vidMode.height() - height) / 2
            );
        }

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);

        GL.createCapabilities();

        glClearColor(0.95f, 0.95f, 0.95f, 1f);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        initShader();
        initTextShaderAndBuffers();

        if (parentWindow != MemoryUtil.NULL) {
            GLFW.glfwMakeContextCurrent(parentWindow);
            GL.createCapabilities();
        }
    }

    private void initShader() {
        float[] vertices = {
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 0f,
                1f, 1f,
                0f, 1f
        };

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.length);
        vertexBuffer.put(vertices).flip();

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        String vertexShaderSource = """
                #version 330 core
                layout(location = 0) in vec2 pos;
                uniform vec2 scale;
                uniform vec2 offset;
                void main() {
                    vec2 p = pos * scale + offset;
                    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """;

        String fragmentShaderSource = """
                #version 330 core
                out vec4 fragColor;
                uniform vec3 color;
                void main() {
                    fragColor = vec4(color, 1.0);
                }
                """;

        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexShaderSource);
        glCompileShader(vertexShader);
        checkCompileErrors(vertexShader, "VERTEX");

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(fragmentShader);
        checkCompileErrors(fragmentShader, "FRAGMENT");

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);
        checkCompileErrors(shaderProgram, "PROGRAM");

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void initTextShaderAndBuffers() {
        float[] quad = {
                // x, y,   u, v
                0f, 0f,   0f, 0f,
                1f, 0f,   1f, 0f,
                1f, 1f,   1f, 1f,
                0f, 0f,   0f, 0f,
                1f, 1f,   1f, 1f,
                0f, 1f,   0f, 1f
        };

        FloatBuffer buf = BufferUtils.createFloatBuffer(quad.length);
        buf.put(quad).flip();

        textVao = glGenVertexArrays();
        glBindVertexArray(textVao);

        textVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);

        int stride = 4 * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        String vs = """
                #version 330 core
                layout(location = 0) in vec2 pos;
                layout(location = 1) in vec2 tex;
                out vec2 vTex;
                uniform vec2 scale;
                uniform vec2 offset;
                void main() {
                    vTex = tex;
                    vec2 p = pos * scale + offset;
                    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """;

        String fs = """
                #version 330 core
                in vec2 vTex;
                out vec4 fragColor;
                uniform sampler2D tex;
                void main() {
                    fragColor = texture(tex, vTex);
                }
                """;

        int vsId = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vsId, vs);
        glCompileShader(vsId);
        checkCompileErrors(vsId, "VERTEX");

        int fsId = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fsId, fs);
        glCompileShader(fsId);
        checkCompileErrors(fsId, "FRAGMENT");

        textShaderProgram = glCreateProgram();
        glAttachShader(textShaderProgram, vsId);
        glAttachShader(textShaderProgram, fsId);
        glLinkProgram(textShaderProgram);
        checkCompileErrors(textShaderProgram, "PROGRAM");

        glDeleteShader(vsId);
        glDeleteShader(fsId);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void checkCompileErrors(int shader, String type) {
        int success;
        if (type.equals("PROGRAM")) {
            success = glGetProgrami(shader, GL_LINK_STATUS);
            if (success == 0) System.err.println(glGetProgramInfoLog(shader));
        } else {
            success = glGetShaderi(shader, GL_COMPILE_STATUS);
            if (success == 0) System.err.println(glGetShaderInfoLog(shader));
        }
    }

    public void setValue(float value) {
        this.value = value;
    }

    public void setMaxValue(float maxValue) {
        this.maxValue = maxValue;
    }

    public void setDescription(String description) {
        GLFW.glfwSetWindowTitle(window, description);
        this.description = description;

        int texW = 128;
        int texH = 64;

        BufferedImage img = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font baseFont = SnowMemo.currentTheme.getFonts()[0];
        int fontSize = Math.max(4, baseFont.getSize());
        Font font = baseFont.deriveFont((float) fontSize);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        while ((fm.stringWidth(description) > texW - 8 || fm.getHeight() > texH - 8) && fontSize > 4) {
            fontSize--;
            font = baseFont.deriveFont((float) fontSize);
            g.setFont(font);
            fm = g.getFontMetrics();
        }

        g.setColor(new Color(0,0,0,255));
        int textWidth = fm.stringWidth(description);
        int textHeight = fm.getAscent();
        int x = (texW - textWidth) / 2;
        int y = (texH + textHeight) / 2 - fm.getDescent();
        g.drawString(description, x, y);
        g.dispose();

        if (descriptionTexture != null) {
            descriptionTexture.cleanup();
            descriptionTexture = null;
        }

        descriptionImage = img;
        descriptionTexture = Texture.loadTexture(descriptionImage);
    }

    public void render() {
        if (GLFW.glfwWindowShouldClose(parentWindow)){
            return;
        }
        long oldContext = GLFW.glfwGetCurrentContext();
        GLFW.glfwMakeContextCurrent(window);
//        GL.createCapabilities();
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE) {
            System.err.println("Warning: Trying to render invisible window");
        }

        glClearColor(0.95f, 0.95f, 0.95f, 1f);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glClear(GL_COLOR_BUFFER_BIT);

        float barWidth = 0.85f * width;
        float barHeight = 0.15f * height;
        float barX = 0.075f * width;
        float barY = (height - barHeight) / 2f;

        float percentage = (maxValue > 0) ? Math.min(1f, value / maxValue) : 0f;

        glUseProgram(shaderProgram);
        glBindVertexArray(vao);
        int scaleLoc = glGetUniformLocation(shaderProgram, "scale");
        int offsetLoc = glGetUniformLocation(shaderProgram, "offset");
        int colorLoc = glGetUniformLocation(shaderProgram, "color");

        glUniform2f(scaleLoc, barWidth / width, barHeight / height);
        glUniform2f(offsetLoc, barX / width, barY / height);
        glUniform3f(colorLoc, 0.86f, 0.86f, 0.86f);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glUniform2f(scaleLoc, barWidth * percentage / width, barHeight / height);
        glUniform2f(offsetLoc, (barX / width) + 0.015f, (barY / height) + 0.015f);
        glUniform3f(colorLoc, 0.3f, 0.7f, 0.3f);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        if (descriptionTexture != null) {
            glUseProgram(textShaderProgram);
            glBindVertexArray(textVao);

            int scaleLocT = glGetUniformLocation(textShaderProgram, "scale");
            int offsetLocT = glGetUniformLocation(textShaderProgram, "offset");

            float texDisplayW = 128f;
            float texDisplayH = 64f;

            float posX = (width - texDisplayW) / 2f;
            float posY = barY - texDisplayH - 10f;
            if (posY < 10f) posY = barY + barHeight + 10f;

            glUniform2f(scaleLocT, texDisplayW / width, texDisplayH / height);
            glUniform2f(offsetLocT, posX / width, posY / height-0.25f);

            glActiveTexture(GL_TEXTURE0);
            descriptionTexture.bind();
            int samplerLoc = glGetUniformLocation(textShaderProgram, "tex");
            glUniform1i(samplerLoc, 0);

            glDrawArrays(GL_TRIANGLES, 0, 6);
            descriptionTexture.unbind();

            glBindVertexArray(0);
        }

        GLFW.glfwSwapBuffers(window);
        GLFW.glfwPollEvents();

        if (oldContext != MemoryUtil.NULL && oldContext != window) {
            GLFW.glfwMakeContextCurrent(oldContext);
            GL.createCapabilities();
        }
    }

    public void show() {
        GLFW.glfwShowWindow(window);
        render();
    }

    public void hide() {
        GLFW.glfwShowWindow(parentWindow);
        GLFW.glfwHideWindow(window);
        if (parentWindow != MemoryUtil.NULL) {
            GLFW.glfwMakeContextCurrent(parentWindow);
            GL.createCapabilities();
        }
    }

    public void close() {
        GLFW.glfwMakeContextCurrent(window);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteProgram(shaderProgram);
        glDeleteVertexArrays(textVao);
        glDeleteBuffers(textVbo);
        glDeleteProgram(textShaderProgram);
        if (descriptionTexture != null) descriptionTexture.cleanup();
        GLFW.glfwDestroyWindow(window);
        if (parentWindow != MemoryUtil.NULL) {
            GLFW.glfwMakeContextCurrent(parentWindow);
            GL.createCapabilities();
        }
    }

    public float getValue() {
        return value;
    }

    private void rebindGL() {
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glUseProgram(shaderProgram);
    }
}
