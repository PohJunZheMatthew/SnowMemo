package Main;

import com.mongodb.lang.NonNull;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {
    private Utils(){}

    public static String loadResource(@NonNull InputStream inputStream){
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            StringBuilder text = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
            return text.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String loadResource(String filePath){
        InputStream inputStream = SnowMemo.class.getResourceAsStream(filePath);
        if (inputStream == null) {
            try {
                throw new IOException("File not found in classpath!");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            StringBuilder text = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
            return text.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static Mesh.Material loadMaterial(Class<?> contextClass, String mtlPath, String materialName) {
        // Load the entire material library
        Map<String, Object[]> materials = loadMaterialLibrary(contextClass, mtlPath);

        // Get the specific material requested
        Object[] matData = materials.get(materialName);

        if (matData == null) {
            System.err.println("Material not found: " + materialName);
            return null;
        }

        return (Mesh.Material) matData[0];
    }

    private static Map<String, Object[]> loadMaterialLibrary(Class<?> contextClass, String mtlPath) {
        Map<String, Object[]> materials = new HashMap<>();

        InputStream inputStream = contextClass.getResourceAsStream(mtlPath);
        if (inputStream == null && !mtlPath.startsWith("/")) {
            inputStream = contextClass.getResourceAsStream("/" + mtlPath);
        }

        if (inputStream == null) {
            System.err.println("Material file not found: " + mtlPath);
            System.err.println("Tried with context class: " + contextClass.getName());
            return materials;
        }

        String currentMaterialName = null;
        Vector3f ambientColor = new Vector3f();
        Vector3f diffuseColor = new Vector3f();
        Vector3f specularColor = new Vector3f();
        float specularHighlights = 0;
        float opticalDensity = 0.001f;
        float dissolve = 1.0f;
        float illum = 0;
        Texture texture = null;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");

                switch (parts[0].toLowerCase()) {
                    case "newmtl":
                        // Save previous material if exists
                        if (currentMaterialName != null) {
                            Object[] matData = new Object[2];
                            matData[0] = new Mesh.Material(
                                    new Vector4f(ambientColor.x, ambientColor.y, ambientColor.z, 1.0f),
                                    new Vector4f(diffuseColor.x, diffuseColor.y, diffuseColor.z, 1.0f),
                                    new Vector4f(specularColor.x, specularColor.y, specularColor.z, 1.0f),
                                    specularHighlights
                            );
                            matData[1] = texture;
                            materials.put(currentMaterialName, matData);
                        }
                        // Start new material
                        currentMaterialName = parts[1];
                        ambientColor = new Vector3f();
                        diffuseColor = new Vector3f();
                        specularColor = new Vector3f();
                        specularHighlights = 0;
                        texture = null;
                        break;
                    case "ns":
                        specularHighlights = Float.parseFloat(parts[1]);
                        break;
                    case "ka":
                        ambientColor.set(
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        );
                        break;
                    case "kd":
                        diffuseColor.set(
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        );
                        break;
                    case "ks":
                        specularColor.set(
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        );
                        break;
                    case "ni":
                        opticalDensity = Float.parseFloat(parts[1]);
                        break;
                    case "d":
                        dissolve = Float.parseFloat(parts[1]);
                        break;
                    case "illum":
                        illum = Float.parseFloat(parts[1]);
                        break;
                    case "map_kd":
                        try {
                            texture = Texture.loadTexture(contextClass.getResourceAsStream(parts[1]));
                        } catch (Exception e) {
                            System.err.println("Failed to load texture: " + parts[1]);
                        }
                        break;
                }
            }

            // Save last material
            if (currentMaterialName != null) {
                Object[] matData = new Object[2];
                matData[0] = new Mesh.Material(
                        new Vector4f(ambientColor.x, ambientColor.y, ambientColor.z, 1.0f),
                        new Vector4f(diffuseColor.x, diffuseColor.y, diffuseColor.z, 1.0f),
                        new Vector4f(specularColor.x, specularColor.y, specularColor.z, 1.0f),
                        specularHighlights
                );
                matData[1] = texture;
                materials.put(currentMaterialName, matData);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load material library", e);
        }

        return materials;
    }// Overload for backward compatibility
    public static Mesh loadObj(InputStream inputStream,Window window) {
        return loadObj(window, inputStream, Utils.class);
    }
    public static Mesh loadObjFromString(String objContent, Class<?> contextClass, Window window) {
        List<Vector3f> tempPositions = new ArrayList<>();
        List<Vector3f> tempNormals = new ArrayList<>();
        List<Vertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Map<Vertex, Integer> vertexMap = new HashMap<>();

        Map<String, Object[]> materials = new HashMap<>();
        Mesh.Material currentMaterial = null;
        Texture currentTexture = null;

        try (BufferedReader br = new BufferedReader(new StringReader(objContent))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");

                switch (parts[0].toLowerCase()) {
                    case "mtllib":
                        String mtlPath = parts[1];
                        materials = loadMaterialLibrary(contextClass, mtlPath);
                        break;

                    case "usemtl":
                        String materialName = parts[1];
                        if (materials.containsKey(materialName)) {
                            Object[] matData = materials.get(materialName);
                            currentMaterial = (Mesh.Material) matData[0];
                            currentTexture = (Texture) matData[1];
                        }
                        break;

                    case "v":
                        tempPositions.add(new Vector3f(
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        ));
                        break;

                    case "vn":
                        tempNormals.add(new Vector3f(
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        ));
                        break;

                    case "f":
                        List<Vertex> faceVertices = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            String[] faceData = parts[i].split("/");
                            int vIndex = Integer.parseInt(faceData[0]) - 1;
                            int vnIndex = faceData.length >= 3 && !faceData[2].isEmpty() ? Integer.parseInt(faceData[2]) - 1 : -1;
                            Vertex vertex = new Vertex(vIndex, -1, vnIndex);

                            if (!vertexMap.containsKey(vertex)) {
                                vertexMap.put(vertex, vertices.size());
                                vertices.add(vertex);
                            }
                            faceVertices.add(vertex);
                        }

                        // Triangulate face (fan method)
                        for (int i = 1; i < faceVertices.size() - 1; i++) {
                            indices.add(vertexMap.get(faceVertices.get(0)));
                            indices.add(vertexMap.get(faceVertices.get(i)));
                            indices.add(vertexMap.get(faceVertices.get(i + 1)));
                        }
                        break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read OBJ string", e);
        }

        // Interleave vertex data (positions + normals)
        float[] vertexData = new float[vertices.size() * 6];
        for (int i = 0; i < vertices.size(); i++) {
            Vertex v = vertices.get(i);
            Vector3f pos = tempPositions.get(v.v);
            Vector3f norm = v.vn >= 0 ? tempNormals.get(v.vn) : new Vector3f(0, 0, 1);

            vertexData[i * 6] = pos.x;
            vertexData[i * 6 + 1] = pos.y;
            vertexData[i * 6 + 2] = pos.z;
            vertexData[i * 6 + 3] = norm.x;
            vertexData[i * 6 + 4] = norm.y;
            vertexData[i * 6 + 5] = norm.z;
        }

        int[] idxArray = indices.stream().mapToInt(Integer::intValue).toArray();
        Mesh mesh = new Mesh(vertexData, idxArray, window, 6);

        if (currentMaterial != null) {
            mesh.setMaterial(currentMaterial);
        }
        if (currentTexture != null) {
            mesh.texture = currentTexture;
        }

        return mesh;
    }

    public static Mesh loadObj(Window window,InputStream inputStream, Class<?> contextClass) {
        List<Vector3f> tempPositions = new ArrayList<>();
        List<Vector3f> tempNormals = new ArrayList<>();
        List<Vertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Map<Vertex, Integer> vertexMap = new HashMap<>();

        Map<String, Object[]> materials = new HashMap<>();
        Mesh.Material currentMaterial = null;
        Texture currentTexture = null;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");

                switch (parts[0].toLowerCase()) {
                    case "mtllib":
                        // Load material library using the context class for resource resolution
                        String mtlPath = parts[1];
                        materials = loadMaterialLibrary(contextClass, mtlPath);
                        break;

                    case "usemtl":
                        // Switch to specified material
                        String materialName = parts[1];
                        if (materials.containsKey(materialName)) {
                            Object[] matData = materials.get(materialName);
                            currentMaterial = (Mesh.Material) matData[0];
                            currentTexture = (Texture) matData[1];
                        } else {
                            System.err.println("Material not found: " + materialName);
                        }
                        break;

                    case "v": // position
                        tempPositions.add(new Vector3f(
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        ));
                        break;

                    case "vn": // normal
                        tempNormals.add(new Vector3f(
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        ));
                        break;

                    case "f": // face
                        List<Vertex> faceVertices = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            String[] faceData = parts[i].split("/");
                            int vIndex  = Integer.parseInt(faceData[0]) - 1;
                            int vnIndex = faceData.length >= 3 && !faceData[2].isEmpty() ? Integer.parseInt(faceData[2]) - 1 : -1;
                            Vertex vertex = new Vertex(vIndex, -1, vnIndex);

                            if (!vertexMap.containsKey(vertex)) {
                                vertexMap.put(vertex, vertices.size());
                                vertices.add(vertex);
                            }
                            faceVertices.add(vertex);
                        }

                        // Triangulate the face (fan method)
                        for (int i = 1; i < faceVertices.size() - 1; i++) {
                            indices.add(vertexMap.get(faceVertices.get(0)));
                            indices.add(vertexMap.get(faceVertices.get(i)));
                            indices.add(vertexMap.get(faceVertices.get(i + 1)));
                        }
                        break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load OBJ", e);
        }

        // Interleave vertex data: positions + normals
        float[] vertexData = new float[vertices.size() * 6];
        for (int i = 0; i < vertices.size(); i++) {
            Vertex v = vertices.get(i);
            Vector3f pos = tempPositions.get(v.v);
            Vector3f norm = v.vn >= 0 ? tempNormals.get(v.vn) : new Vector3f(0, 0, 1);

            vertexData[i * 6]     = pos.x;
            vertexData[i * 6 + 1] = pos.y;
            vertexData[i * 6 + 2] = pos.z;
            vertexData[i * 6 + 3] = norm.x;
            vertexData[i * 6 + 4] = norm.y;
            vertexData[i * 6 + 5] = norm.z;
        }

        // Convert indices to array
        int[] idxArray = indices.stream().mapToInt(Integer::intValue).toArray();

        // Create mesh with material and texture
        Mesh mesh = new Mesh(vertexData, idxArray,window, 6);

        // Apply material if loaded
        if (currentMaterial != null) {
            mesh.setMaterial(currentMaterial);
            System.out.println("Final material applied to mesh");
        } else {
            System.out.println("No material applied to mesh");
        }
        if (currentTexture != null) {
            mesh.texture = currentTexture;
        }

        return mesh;
    }
    /**
     * Utility class for opening URLs in the default system browser.
     * Works in headless mode (java.awt.headless=true) by using system commands.
     * Supports: Windows, macOS, Linux, Unix, BSD, Solaris, and more.
     */
    public class BrowserUtil {

        /**
         * Opens a URL in the default system browser.
         * @param url The URL to open (e.g., "https://www.google.com")
         */
        public static void openURL(String url) {
            try {
                // Validate and normalize the URL
                URI uri = new URI(url);
                openURI(uri);
            } catch (URISyntaxException e) {
                System.err.println("Invalid URL: " + url);
                e.printStackTrace();
            }
        }

        /**
         * Opens a URI in the default system browser.
         * @param uri The URI to open
         */
        public static void openURI(URI uri) {
            String url = uri.toString();
            String os = System.getProperty("os.name").toLowerCase();

            try {
                if (os.contains("win")) {
                    // Windows (all versions)
                    new ProcessBuilder("cmd", "/c", "start", "", url).start();

                } else if (os.contains("mac") || os.contains("darwin")) {
                    // macOS / Mac OS X / Darwin
                    new ProcessBuilder("open", url).start();

                } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                    // Linux, Unix, AIX
                    // Try xdg-open first (works on most modern Linux distros)
                    if (tryCommand("xdg-open", url)) {
                        return;
                    }
                    // Fallback to other common browsers
                    String[] browsers = {"gnome-open", "kde-open", "firefox", "mozilla",
                            "chromium", "google-chrome", "opera", "epiphany"};
                    for (String browser : browsers) {
                        if (tryCommand(browser, url)) {
                            return;
                        }
                    }
                    System.err.println("Could not find a suitable browser on Linux/Unix");

                } else if (os.contains("bsd")) {
                    // FreeBSD, OpenBSD, NetBSD
                    if (tryCommand("xdg-open", url)) {
                        return;
                    }
                    if (tryCommand("firefox", url)) {
                        return;
                    }
                    System.err.println("Could not find a suitable browser on BSD");

                } else if (os.contains("sunos") || os.contains("solaris")) {
                    // Solaris / SunOS
                    if (tryCommand("firefox", url)) {
                        return;
                    }
                    if (tryCommand("/usr/dt/bin/sdtwebclient", url)) {
                        return;
                    }
                    System.err.println("Could not find a suitable browser on Solaris");

                } else {
                    System.err.println("Unsupported operating system: " + os);
                }
            } catch (IOException e) {
                System.err.println("Failed to open URL: " + url);
                e.printStackTrace();
            }
        }

        /**
         * Try to execute a command to open a URL.
         * @param command The command to try
         * @param url The URL to open
         * @return true if command executed successfully, false otherwise
         */
        private static boolean tryCommand(String command, String url) {
            try {
                new ProcessBuilder(command, url).start();
                return true;
            } catch (IOException e) {
                return false;
            }
        }


        /**
         * Opens a URL with error feedback.
         * @param url The URL to open
         * @return true if the command was executed successfully, false otherwise
         */
        public static boolean openURLWithFeedback(String url) {
            try {
                URI uri = new URI(url);
                return openURIWithFeedback(uri);
            } catch (URISyntaxException e) {
                System.err.println("Invalid URL: " + url);
                e.printStackTrace();
                return false;
            }
        }

        /**
         * Opens a URI with error feedback.
         * @param uri The URI to open
         * @return true if the command was executed successfully, false otherwise
         */
        public static boolean openURIWithFeedback(URI uri) {
            String url = uri.toString();
            String os = System.getProperty("os.name").toLowerCase();

            try {
                if (os.contains("win")) {
                    new ProcessBuilder("cmd", "/c", "start", "", url).start();
                    return true;

                } else if (os.contains("mac") || os.contains("darwin")) {
                    new ProcessBuilder("open", url).start();
                    return true;

                } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                    if (tryCommand("xdg-open", url)) return true;
                    String[] browsers = {"gnome-open", "kde-open", "firefox", "mozilla",
                            "chromium", "google-chrome", "opera", "epiphany"};
                    for (String browser : browsers) {
                        if (tryCommand(browser, url)) return true;
                    }
                    return false;

                } else if (os.contains("bsd")) {
                    if (tryCommand("xdg-open", url)) return true;
                    if (tryCommand("firefox", url)) return true;
                    return false;

                } else if (os.contains("sunos") || os.contains("solaris")) {
                    if (tryCommand("firefox", url)) return true;
                    if (tryCommand("/usr/dt/bin/sdtwebclient", url)) return true;
                    return false;

                } else {
                    System.err.println("Unsupported operating system: " + os);
                    return false;
                }
            } catch (IOException e) {
                System.err.println("Failed to open URL: " + url);
                e.printStackTrace();
                return false;
            }
        }
    }
}