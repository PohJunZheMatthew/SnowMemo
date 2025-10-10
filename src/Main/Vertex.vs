#version 330 core

layout (location = 0) in vec3 position;
layout (location = 1) in vec3 vertNormal;
layout (location = 2) in vec2 texCoord;
layout (location = 3) in vec3 vertTangent;
layout (location = 4) in vec3 vertBitangent;

out vec2 outTexCoord;
out vec3 FragPos;
out vec3 Normal;
out vec3 Tangent;
out vec3 Bitangent;
out mat3 TBN;
out vec4 FragPosLightSpace;
out vec4 ClipPos;

uniform mat4 modelMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat3 normalMatrix;
uniform mat4 lightSpaceMatrix;

mat3 computeSafeNormalMatrix(mat4 model, mat4 view) {
    mat3 mv3 = mat3(view * model);
    return transpose(inverse(mv3));
}

void main() {
    vec4 worldPos = modelMatrix * vec4(position, 1.0);
    FragPos = worldPos.xyz;

    vec4 viewPos = viewMatrix * worldPos;
    ClipPos = projectionMatrix * viewPos;
    gl_Position = ClipPos;

    // Safe normal matrix
    mat3 nMatrix = normalMatrix;
    if (length(nMatrix[0]) + length(nMatrix[1]) + length(nMatrix[2]) < 0.001)
        nMatrix = computeSafeNormalMatrix(modelMatrix, viewMatrix);

    Normal = normalize(nMatrix * vertNormal);

    Tangent = normalize(mat3(modelMatrix) * vertTangent);
    Tangent = normalize(Tangent - dot(Tangent, Normal) * Normal);
    Bitangent = normalize(cross(Normal, Tangent));

    TBN = mat3(Tangent, Bitangent, Normal);
    FragPosLightSpace = lightSpaceMatrix * worldPos;
    outTexCoord = texCoord;
}
