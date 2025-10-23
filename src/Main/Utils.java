package Main;

import com.mongodb.lang.NonNull;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private static Map<String, Object[]> loadMaterialLibrary(Class<?> contextClass, String mtlPath){
        Map<String, Object[]> materials = new HashMap<>();

        // Try loading with the provided path first
        InputStream inputStream = contextClass.getResourceAsStream(mtlPath);

        // If that fails and path doesn't start with /, try adding it
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

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))){
            String line;
            while ((line = br.readLine())!=null){
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");

                switch(parts[0].toLowerCase()){
                    case "newmtl":
                        // Save previous material if exists
                        if (currentMaterialName != null) {
                            Object[] matData = new Object[2];
                            matData[0] = new Mesh.Material(
                                    new Vector4f(ambientColor, 255),
                                    new Vector4f(diffuseColor, 255),
                                    new Vector4f(specularColor, 255),
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
                                Float.parseFloat(parts[1]),  // Keep 0-1 range
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        );
                        break;
                    case "kd":
                        diffuseColor.set(
                                Float.parseFloat(parts[1]),  // Keep 0-1 range
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                        );
                        break;
                    case "ks":
                        specularColor.set(
                                Float.parseFloat(parts[1]),  // Keep 0-1 range
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
                        new Vector4f(ambientColor, 1.0f),
                        new Vector4f(diffuseColor, 1.0f),
                        new Vector4f(specularColor, 1.0f),
                        specularHighlights
                );
                matData[1] = texture;
                materials.put(currentMaterialName, matData);
            }

        } catch (Exception e){
            throw new RuntimeException("Failed to load material library", e);
        }

        return materials;
    }

    // Overload for backward compatibility
    public static Mesh loadObj(InputStream inputStream) {
        return loadObj(inputStream, Utils.class);
    }

    public static Mesh loadObj(InputStream inputStream, Class<?> contextClass) {
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
                        System.out.println("Loading MTL file: " + mtlPath + " with context: " + contextClass.getName());
                        materials = loadMaterialLibrary(contextClass, mtlPath);
                        System.out.println("Loaded " + materials.size() + " materials: " + materials.keySet());
                        break;

                    case "usemtl":
                        // Switch to specified material
                        String materialName = parts[1];
                        System.out.println("Using material: " + materialName);
                        if (materials.containsKey(materialName)) {
                            Object[] matData = materials.get(materialName);
                            currentMaterial = (Mesh.Material) matData[0];
                            currentTexture = (Texture) matData[1];
                            System.out.println("Material found and applied");
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
        Mesh mesh = new Mesh(vertexData, idxArray, Window.getCurrentWindow(), 6);

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
}