#version 330 core

layout (location = 0) in vec3 position;
layout (location = 1) in vec3 vertNormal;
layout (location = 2) in vec2 texCoord;
layout (location = 3) in vec3 vertTangent;    // For normal mapping
layout (location = 4) in vec3 vertBitangent;  // For normal mapping

// Outputs to fragment shader
out vec2 outTexCoord;
out vec3 FragPos;
out vec3 Normal;
out vec3 Tangent;
out vec3 Bitangent;
out mat3 TBN;           // Tangent-Bitangent-Normal matrix
out vec4 FragPosLightSpace; // For shadow mapping
out vec3 ViewPos;       // View space position for better depth calculations
out vec4 ClipPos;       // Clip space position for screen-space effects

// Uniforms
uniform mat4 modelMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat3 normalMatrix;     // Proper normal matrix (inverse transpose of model-view)
uniform mat4 lightSpaceMatrix; // For shadow mapping
uniform vec3 viewPosition;     // Camera position in world space

// Compute proper normal matrix if not provided
mat3 computeNormalMatrix(mat4 modelView) {
    return transpose(inverse(mat3(modelView)));
}

void main() {
    // Transform position to world space
    vec4 worldPos = modelMatrix * vec4(position, 1.0);
    FragPos = worldPos.xyz;

    // Transform to view space for better depth precision
    vec4 viewPos = viewMatrix * worldPos;
    ViewPos = viewPos.xyz;

    // Transform to clip space
    ClipPos = projectionMatrix * viewPos;
    gl_Position = ClipPos;

    // Transform normal to world space using proper normal matrix
    Normal = normalize(normalMatrix * vertNormal);

    // Transform tangent and bitangent for normal mapping
    Tangent = normalize(mat3(modelMatrix) * vertTangent);
    Bitangent = normalize(mat3(modelMatrix) * vertBitangent);

    // Ensure orthogonality (Gram-Schmidt process)
    Tangent = normalize(Tangent - dot(Tangent, Normal) * Normal);
    Bitangent = normalize(cross(Normal, Tangent));

    // Create TBN matrix for tangent space to world space transformation
    TBN = mat3(Tangent, Bitangent, Normal);

    // Transform position to light space for shadow mapping
    FragPosLightSpace = lightSpaceMatrix * worldPos;

    // Pass through texture coordinates
    outTexCoord = texCoord;

    // Optional: Apply texture coordinate transformations
    // outTexCoord = (textureMatrix * vec3(texCoord, 1.0)).xy;
}