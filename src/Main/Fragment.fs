#version 330 core

in vec3 FragPos;
in vec3 Normal;
in vec2 outTexCoord;

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

struct Light {
    vec4 ambient;
    vec4 diffuse;
    vec4 specular;
    vec3 position;
    float strength;
    float radius;
};
uniform Light light;

struct DirectionalLight {
    vec4 ambient;
    vec4 diffuse;
    vec4 specular;
    vec3 direction;
    float strength;
};
uniform DirectionalLight sun;

uniform float lightConstant;
uniform float lightLinear;
uniform float lightQuadratic;

uniform vec3 blockLight;
uniform float emission;

uniform int useLighting;
uniform vec4 overrideColor;

uniform sampler2D diffuseSampler;
uniform bool useTextures;

// -----------------------------------------------------------------------------
// Constants
// -----------------------------------------------------------------------------
const float PI       = 3.14159265359;
const float EPSILON  = 1e-6;
const float INV_PI   = 0.31830988618;

// -----------------------------------------------------------------------------
// Utility Functions
// -----------------------------------------------------------------------------
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    // Slightly optimized version with clamp for stability
    float f = clamp(1.0 - cosTheta, 0.0, 1.0);
    float f2 = f * f;
    float f5 = f2 * f2 * f;
    return F0 + (1.0 - F0) * f5;
}

float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float denom = (NdotH * NdotH) * (a2 - 1.0) + 1.0;
    return a2 / max(PI * denom * denom, EPSILON);
}

float geometrySchlickGGX(float NdotV, float roughness) {
    float r = roughness + 1.0;
    float k = (r * r) / 8.0;
    return NdotV / max(NdotV * (1.0 - k) + k, EPSILON);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    return geometrySchlickGGX(NdotV, roughness) * geometrySchlickGGX(NdotL, roughness);
}

float calculateAttenuation(float distance, float radius) {
    float att = 1.0 / max(lightConstant + lightLinear * distance + lightQuadratic * distance * distance, EPSILON);
    if (radius > 0.0) {
        float x = clamp(1.0 - distance / radius, 0.0, 1.0);
        att *= x * x * (3.0 - 2.0 * x);
    }
    return att;
}

vec3 toneMapACES(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 gammaCorrect(vec3 color) {
    return pow(color, vec3(1.0 / 2.2));
}

// ----------------------------------------------------------------------------
// Hemisphere Ambient (improved balance)
// ----------------------------------------------------------------------------
vec3 calculateHemisphereAmbient(vec3 N, vec3 skyColor, vec3 groundColor) {
    // Bias upward slightly to reduce dark bottom band
    float h = N.y * 0.5 + 0.6;
    h = clamp(h, 0.0, 1.0);
    h = h * h * (3.0 - 2.0 * h); // smooth interpolation
    return mix(groundColor, skyColor, h);
}

// ----------------------------------------------------------------------------
// Cook-Torrance BRDF (energy-conserving)
// ----------------------------------------------------------------------------
vec3 cookTorranceBRDF(vec3 N, vec3 V, vec3 L, vec3 radiance, vec3 albedo, float metallic, float roughness, vec3 F0) {
    vec3 H = normalize(V + L);
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float HdotV = max(dot(H, V), 0.0);
    if (NdotL <= 0.0) return vec3(0.0);

    float NDF = distributionGGX(N, H, roughness);
    float G   = geometrySmith(N, V, L, roughness);
    vec3  F   = fresnelSchlick(HdotV, F0);

    vec3 kS = F;
    vec3 kD = (1.0 - metallic) * (vec3(1.0) - kS);
    vec3 diffuse  = kD * albedo * INV_PI;
    vec3 specular = (NDF * G * F) / max(4.0 * NdotV * NdotL, EPSILON);

    return (diffuse + specular) * radiance * NdotL;
}

// -----------------------------------------------------------------------------
// Main
// -----------------------------------------------------------------------------
void main() {
    // Unlit fallback
    if (useLighting == 0) {
        FragColor = overrideColor;
        return;
    }

    // Base color
    vec4 baseColor = useTextures ? texture(diffuseSampler, outTexCoord) : material.diffuse;
    if (useTextures && baseColor.a < EPSILON) {
        baseColor = vec4(1.0, 0.0, 1.0, 1.0); // magenta fallback
    }

    vec3 albedo = pow(baseColor.rgb, vec3(2.2)); // linearize

    // Surface properties
    vec3 N = normalize(Normal);
    vec3 V = normalize(viewPosition - FragPos);
    float metallic = clamp(material.metallic, 0.0, 1.0);
    float roughness = clamp(material.roughness, 0.04, 1.0);
    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    vec3 Lo = vec3(0.0);

    // ------------------ Point Light ------------------
    vec3 L = light.position - FragPos;
    float dist = length(L);
    L /= max(dist, EPSILON);
    float attenuation = calculateAttenuation(dist, light.radius);
    vec3 radiance = light.diffuse.rgb * light.strength * attenuation;
    Lo += cookTorranceBRDF(N, V, L, radiance, albedo, metallic, roughness, F0);

    // ---------------- Directional Light ---------------
    vec3 sunDir = normalize(-sun.direction);
    vec3 sunRadiance = sun.diffuse.rgb * sun.strength;
    Lo += cookTorranceBRDF(N, V, sunDir, sunRadiance, albedo, metallic, roughness, F0);

    // ---------------- Ambient Lighting ----------------
    vec3 kS = fresnelSchlick(max(dot(N, V), 0.0), F0);
    vec3 kD = (1.0 - metallic) * (vec3(1.0) - kS);

    vec3 skyColor    = sun.ambient.rgb * 1.1;
    vec3 groundColor = sun.ambient.rgb * 0.55; // increased ground light
    vec3 hemisphereAmbient = calculateHemisphereAmbient(N, skyColor, groundColor);

    vec3 materialAmbient = kD * albedo * material.ambient.rgb;
    vec3 ambient = (materialAmbient + hemisphereAmbient * kD * albedo) * 0.6;

    // Block light & emission
    ambient += blockLight * albedo * kD;
    vec3 emissive = emission * albedo;

    // ---------------- Compose Final -------------------
    vec3 color = ambient + Lo + emissive;

    // Tone map + gamma
    color = toneMapACES(color);
    color = gammaCorrect(color);

    FragColor = vec4(color, baseColor.a);
}
