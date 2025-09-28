package Main;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class BasePlateTextureGenerator {

    public static void main(String[] args) {
        int width = 200;
        int height = 200;
        double scale = 10;

        // Create a buffered image
        BufferedImage image = new BufferedImage((int) (width * scale), (int) (height * scale), BufferedImage.TYPE_INT_ARGB);

        // Get graphics context
        Graphics2D g = image.createGraphics();

        // Fill background with white
        g.setColor(Color.white);
        g.fillRect(0, 0, (int)(width*scale), (int)(height*scale));

        // Apply scaling
        g.scale(scale, scale);

        // Draw square outline
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke((float)(3.0 ))); // compensate for scale
        g.drawRect(0, 0, width, height);

        // Draw a line cutting through the square (diagonal)
        g.drawLine(0, 0, width, height);

        g.dispose();

        // Save the image
        try {
            File outputFile = new File("BasePlateTexture.png");
            ImageIO.write(image, "png", outputFile);
            System.out.println("Image saved as " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
