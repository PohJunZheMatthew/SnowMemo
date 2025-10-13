#version 330 core
#define MAX_POINT_LIGHTS 8
#define MAX_DIR_LIGHTS 2

in vec3 FragPos;
in vec3 Normal;
in vec2 outTexCoord;
in vec4 FragPosLightSpace;

out vec4 FragColor;

uniform vec3 viewPosition;

struct Material {
    vec4 ambient;
    vec4 diffuse;
    vec4 specular;
    float shininess;
    float metallic;
    float roughness;
};
uniform Material material;

struct PointLight {
    vec4 ambient;
    vec4 diffuse;
    vec4 specular;
    vec3 position;
    float strength;
    float radius;
};
struct DirLight {
    vec4 ambient;
    vec4 diffuse;
    vec4 specular;
    vec3 direction;
    float strength;
};

uniform int numPointLights;
uniform int numDirLights;
uniform PointLight pointLights[MAX_POINT_LIGHTS];
uniform DirLight dirLights[MAX_DIR_LIGHTS];

uniform sampler2D diffuseSampler;
uniform sampler2D shadowMap;
uniform bool useTextures;
uniform int useLighting;
uniform vec4 overrideColor;
uniform vec3 blockLight;
uniform float emission;

const float PI = 3.14159265359;
const float EPSILON = 1e-6;
const float INV_PI = 0.31830988618;

// --- Utilities ---
vec3 srgbToLinear(vec3 c) { return pow(c, vec3(2.2)); }
vec3 linearToSrgb(vec3 c) { return pow(c, vec3(1.0 / 2.2)); }

// --- Shadow ---
float calculateShadow(vec4 fragPosLightSpace, vec3 N, vec3 L) {
    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;
    projCoords = projCoords * 0.5 + 0.5;

    if (projCoords.z > 1.0 || any(lessThan(projCoords.xy, vec2(0.0))) || any(greaterThan(projCoords.xy, vec2(1.0))))
        return 0.0;

    float bias = max(0.005 * (1.0 - dot(N, L)), 0.001);
    float shadow = 0.0;
    float currentDepth = projCoords.z;
    vec2 texelSize = 1.0 / textureSize(shadowMap, 0);
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            float pcfDepth = texture(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
            shadow += currentDepth - bias > pcfDepth ? 1.0 : 0.0;
        }
    }
    return shadow / 9.0;
}

// --- PBR Functions ---
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    float f5 = pow(1.0 - clamp(cosTheta, 0.0, 1.0), 5.0);
    return F0 + (1.0 - F0) * f5;
}
float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a2 = roughness * roughness;
    float NdotH = max(dot(N, H), 0.0);
    float denom = (NdotH * NdotH) * (a2 - 1.0) + 1.0;
    return a2 / max(PI * denom * denom, EPSILON);
}
float geometrySchlickGGX(float NdotV, float roughness) {
    float k = (roughness + 1.0);
    k = k * k / 8.0;
    return NdotV / max(NdotV * (1.0 - k) + k, EPSILON);
}
float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    return geometrySchlickGGX(max(dot(N, V), 0.0), roughness) *
           geometrySchlickGGX(max(dot(N, L), 0.0), roughness);
}
vec3 cookTorranceBRDF(vec3 N, vec3 V, vec3 L, vec3 radiance, vec3 albedo, float metallic, float roughness, vec3 F0) {
    vec3 H = normalize(V + L);
    float NdotL = max(dot(N, L), 0.0);
    if (NdotL <= 0.0) return vec3(0.0);

    float NDF = distributionGGX(N, H, roughness);
    float G   = geometrySmith(N, V, L, roughness);
    vec3 F    = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 kS = F;
    vec3 kD = (1.0 - metallic) * (vec3(1.0) - kS);
    vec3 diffuse = kD * albedo * INV_PI;
    vec3 specular = (NDF * G * F) / max(4.0 * max(dot(N,V),0.0) * NdotL, EPSILON);

    return (diffuse + specular) * radiance * NdotL;
}
float calculateAttenuation(float distance, float radius) {
    float att = 1.0 / max(1.0 + 0.09*distance + 0.032*distance*distance, EPSILON);
    if (radius > 0.0) {
        float x = clamp(1.0 - distance / radius, 0.0, 1.0);
        att *= x * x * (3.0 - 2.0 * x);
    }
    return att;
}

// --- Main ---
void main() {
    if (useLighting == 0) { FragColor = overrideColor; return; }

    vec4 baseColor = useTextures ? texture(diffuseSampler, outTexCoord) : material.diffuse;
    if (baseColor.a < 0.01) discard;

    vec3 albedo = srgbToLinear(baseColor.rgb);
    float alpha = baseColor.a;

    float metallic = clamp(material.metallic, 0.0, 1.0);
    float roughness = clamp(material.roughness, 0.04, 1.0);
    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    vec3 N = normalize(Normal);
    vec3 V = normalize(viewPosition - FragPos);
    if (!gl_FrontFacing) N = -N;

    vec3 Lo = vec3(0.0);

    // Point Lights
    for (int i = 0; i < numPointLights; i++) {
        PointLight l = pointLights[i];
        vec3 L = l.position - FragPos;
        float dist = length(L);
        L /= max(dist, EPSILON);

        vec3 radiance = l.diffuse.rgb * l.strength * calculateAttenuation(dist, l.radius);
        Lo += cookTorranceBRDF(N, V, L, radiance, albedo, metallic, roughness, F0);
    }

    // Directional Lights
    for (int i = 0; i < numDirLights; i++) {
        DirLight l = dirLights[i];
        vec3 L = normalize(-l.direction);
        vec3 radiance = l.diffuse.rgb * l.strength;
        float shadowFactor = (i==0) ? (1.0 - calculateShadow(FragPosLightSpace, N, L) * 0.8) : 1.0;
        Lo += cookTorranceBRDF(N, V, L, radiance, albedo, metallic, roughness, F0) * shadowFactor;
    }

    // Ambient + block + emission
    vec3 ambient = srgbToLinear(material.ambient.rgb) * albedo * 0.3;
    vec3 blockLightLinear = srgbToLinear(blockLight);
    vec3 emissionContribution = emission * albedo;
    vec3 color = ambient + Lo + blockLightLinear * albedo + emissionContribution;

    // Reinhard tone mapping + gamma correction
    color = linearToSrgb(color / (color + vec3(1.0)));
    FragColor = vec4(color * alpha, alpha);
}
