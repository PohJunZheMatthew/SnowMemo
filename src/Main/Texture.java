package Main;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_LOD_BIAS;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

public class Texture {
    private final int textureId;

    protected Texture(int textureId) {
        this.textureId = textureId;
    }
    // --------- Public API ----------

    public static Texture loadTexture(InputStream inputStream) {
        try (inputStream) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IllegalArgumentException("ImageIO failed to read texture from stream");
            }
            return createTexture(image, true, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR, GL_REPEAT, GL_REPEAT, -0.5f);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load texture", e);
        }
    }

    public static Texture loadTexture(BufferedImage image) {
        return createTexture(image, true, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR, GL_REPEAT, GL_REPEAT, -0.5f);
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
    }

    public int getTextureid() {
        return textureId;
    }

    // --------- Internal Helpers ----------

    private static Texture createTexture(BufferedImage image,
                                         boolean flipY,
                                         int minFilter,
                                         int magFilter,
                                         int wrapS,
                                         int wrapT,
                                         float lodBias) {

        byte[] imgRGBA = getRGBA(image, flipY);
        ByteBuffer buffer = BufferUtils.createByteBuffer(imgRGBA.length);
        buffer.put(imgRGBA).flip();

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        // Upload texture
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8,
                image.getWidth(), image.getHeight(), 0,
                GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        // Filtering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

// Wrapping
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

// Stronger bias for sharper mipmaps
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, -1.0f);

// Anisotropic filtering (force maximum quality)
        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            float maxAniso = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            glTexParameterf(GL_TEXTURE_2D,
                    EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    Math.min(16.0f, maxAniso)); // cap at 16x for performance
        }

        // Generate mipmaps if needed
        if (minFilter == GL_LINEAR_MIPMAP_LINEAR ||
                minFilter == GL_NEAREST_MIPMAP_LINEAR ||
                minFilter == GL_LINEAR_MIPMAP_NEAREST ||
                minFilter == GL_NEAREST_MIPMAP_NEAREST) {
            glGenerateMipmap(GL_TEXTURE_2D);
        }

        // Unbind for safety
        glBindTexture(GL_TEXTURE_2D, 0);

        return new Texture(texId);
    }
    
    private static byte[] getRGBA(BufferedImage image, boolean flipY) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        byte[] data = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            int row = flipY ? (height - 1 - y) : y;
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                int i = (row * width + x) * 4;
                data[i]     = (byte) ((pixel >> 16) & 0xFF); // R
                data[i + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
                data[i + 2] = (byte) (pixel & 0xFF);         // B
                data[i + 3] = (byte) ((pixel >> 24) & 0xFF); // A
            }
        }
        return data;
    }
}
