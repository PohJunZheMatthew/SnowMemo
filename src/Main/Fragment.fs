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

const float PI = 3.14159265359;
const float EPSILON = 1e-6;
const float INV_PI = 0.31830988618;

// Optimized Fresnel-Schlick with single pow() call
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    float f = clamp(1.0 - cosTheta, 0.0, 1.0);
    return F0 + (1.0 - F0) * pow(f, 5.0);
}

// Roughness remapping for more intuitive control
float remapRoughness(float roughness) {
    return roughness * roughness;
}

// Optimized GGX NDF with better numerical stability
float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = remapRoughness(roughness);
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;

    float denom = NdotH2 * (a2 - 1.0) + 1.0;
    return a2 / max(PI * denom * denom, EPSILON);
}

// Optimized geometry function using direct calculation
float geometrySchlickGGX(float NdotV, float roughness) {
    float a = remapRoughness(roughness);
    float k = a * 0.5; // Direct lighting k
    float denom = NdotV * (1.0 - k) + k;
    return NdotV / max(denom, EPSILON);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    return geometrySchlickGGX(NdotV, roughness) * geometrySchlickGGX(NdotL, roughness);
}

// Improved attenuation with smoother falloff
float calculateAttenuation(float distance, float radius) {
    // Inverse square law with linear and quadratic terms
    float attenuation = 1.0 / max(lightConstant + lightLinear * distance + lightQuadratic * distance * distance, EPSILON);

    // Smooth radius cutoff with improved falloff curve
    if (radius > 0.0) {
        float normalizedDist = distance / radius;
        float falloff = clamp(1.0 - normalizedDist, 0.0, 1.0);
        // Smoother falloff curve using smoothstep-like function
        falloff = falloff * falloff * (3.0 - 2.0 * falloff);
        attenuation *= falloff;
    }

    return attenuation;
}

// Optimized Cook-Torrance BRDF
vec3 cookTorranceBRDF(vec3 N, vec3 V, vec3 L, vec3 radiance, vec3 albedo, float metallic, float roughness, vec3 F0) {
    vec3 H = normalize(V + L);

    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float HdotV = max(dot(H, V), 0.0);

    // Early exit for back-facing surfaces
    if (NdotL <= 0.0) return vec3(0.0);

    float NDF = distributionGGX(N, H, roughness);
    float G = geometrySmith(N, V, L, roughness);
    vec3 F = fresnelSchlick(HdotV, F0);

    // Energy conservation
    vec3 kS = F;
    vec3 kD = (1.0 - metallic) * (vec3(1.0) - kS);

    // Lambertian diffuse
    vec3 diffuse = kD * albedo * INV_PI;

    // Specular BRDF
    float denominator = max(4.0 * NdotV * NdotL, EPSILON);
    vec3 specular = (NDF * G * F) / denominator;

    return (diffuse + specular) * radiance * NdotL;
}

// Improved ACES tone mapping with slight contrast adjustment
vec3 toneMapACES(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

// Accurate sRGB gamma correction
vec3 gammaCorrect(vec3 color) {
    return pow(color, vec3(1.0 / 2.2));
}

// Enhanced hemisphere ambient lighting
vec3 calculateHemisphereAmbient(vec3 N, vec3 skyColor, vec3 groundColor) {
    float hemisphere = N.y * 0.5 + 0.5;
    return mix(groundColor, skyColor, hemisphere * hemisphere); // Smoother transition
}

void main() {
    // Early exit for unlit rendering
    if (useLighting == 0) {
        FragColor = overrideColor;
        return;
    }

    // Sample base color with improved fallback
    vec4 baseColor = useTextures ? texture(diffuseSampler, outTexCoord) : material.diffuse;
    if (useTextures && baseColor.a < EPSILON) {
        baseColor = vec4(1.0, 0.0, 1.0, 1.0); // Magenta fallback for missing textures
    }

    vec3 albedo = pow(baseColor.rgb, vec3(2.2)); // Convert to linear space if needed

    // Calculate surface properties
    vec3 N = normalize(Normal);
    vec3 V = normalize(viewPosition - FragPos);

    // Clamp material properties for stability
    float metallic = clamp(material.metallic, 0.0, 1.0);
    float roughness = clamp(material.roughness, 0.04, 1.0);

    // Base reflectance (F0)
    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    vec3 Lo = vec3(0.0);

    // --- Point Light Contribution ---
    vec3 lightDir = light.position - FragPos;
    float lightDistance = length(lightDir);
    lightDir /= max(lightDistance, EPSILON);

    float attenuation = calculateAttenuation(lightDistance, light.radius);
    vec3 pointRadiance = light.diffuse.rgb * light.strength * attenuation;
    Lo += cookTorranceBRDF(N, V, lightDir, pointRadiance, albedo, metallic, roughness, F0);

    // --- Directional Light Contribution ---
    vec3 sunDir = normalize(-sun.direction);
    vec3 sunRadiance = sun.diffuse.rgb * sun.strength;
    Lo += cookTorranceBRDF(N, V, sunDir, sunRadiance, albedo, metallic, roughness, F0);

    // --- Enhanced Ambient Lighting ---
    vec3 kS = fresnelSchlick(max(dot(N, V), 0.0), F0);
    vec3 kD = (1.0 - metallic) * (vec3(1.0) - kS);

    // Improved hemisphere ambient with better sky/ground colors
    vec3 skyColor = sun.ambient.rgb * 1.2;
    vec3 groundColor = sun.ambient.rgb * 0.3;
    vec3 hemisphereAmbient = calculateHemisphereAmbient(N, skyColor, groundColor);

    // Combine ambient sources
    vec3 materialAmbient = kD * albedo * material.ambient.rgb;
    vec3 ambient = (materialAmbient + hemisphereAmbient * kD * albedo) * 0.5;

    // Block light contribution (game-specific lighting)
    ambient += blockLight * albedo * kD;

    // Emissive contribution
    vec3 emissive = emission * albedo;

    // Final color composition
    vec3 finalColor = ambient + Lo + emissive;

    // Apply tone mapping and gamma correction
    finalColor = toneMapACES(finalColor);
    finalColor = gammaCorrect(finalColor);

    FragColor = vec4(finalColor, baseColor.a);
}