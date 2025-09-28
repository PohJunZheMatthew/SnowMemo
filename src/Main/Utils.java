package Main;

import org.joml.Vector3f;

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
            String text = "";
            while ((line = reader.readLine()) != null) {
                text += line+"\n";
            }
            return text;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static Mesh loadObj(InputStream inputStream) {
        List<Vector3f> tempPositions = new ArrayList<>();
        List<Vector3f> tempNormals = new ArrayList<>();
        List<Vertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Map<Vertex, Integer> vertexMap = new HashMap<>(); // to avoid duplicate vertices

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");

                switch (parts[0].toLowerCase()) {
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
                        // Collect all vertex indices of the face
                        List<Vertex> faceVertices = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            String[] faceData = parts[i].split("/");
                            int vIndex  = Integer.parseInt(faceData[0]) - 1;
                            int vnIndex = faceData.length >= 3 && !faceData[2].isEmpty() ? Integer.parseInt(faceData[2]) - 1 : -1;
                            Vertex vertex = new Vertex(vIndex, -1, vnIndex); // vt is ignored here

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

        // Return new Mesh (stride = 6)
        return new Mesh(vertexData, idxArray, Window.getCurrentWindow(), 6);
    }

}
