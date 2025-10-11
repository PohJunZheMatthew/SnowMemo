#version 330 core
#define MAX_POINT_LIGHTS 8
#define MAX_DIR_LIGHTS 2

in vec3 FragPos;
in vec3 Normal;
in vec2 outTexCoord;
out vec4 FragColor;

uniform vec3 viewPosition;
struct Material{
    vec4 ambient;
    vec4 diffuse;
    vec4 specular;
    float shininess;
    float metallic;
    float roughness;
};
// Material
uniform Material material;

// Lighting
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
uniform bool useTextures;
uniform int useLighting;
uniform vec4 overrideColor;
uniform vec3 blockLight;
uniform float emission;

const float PI = 3.14159265359;
const float EPSILON = 1e-6;
const float INV_PI = 0.31830988618;

// --- PBR Utilities ---
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    float f = clamp(1.0 - cosTheta, 0.0, 1.0);
    float f5 = f*f*f*f*f;
    return F0 + (1.0 - F0) * f5;
}

float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N,H),0.0);
    float denom = (NdotH*NdotH)*(a2-1.0)+1.0;
    return a2 / max(PI*denom*denom, EPSILON);
}

float geometrySchlickGGX(float NdotV, float roughness) {
    float r = roughness + 1.0;
    float k = (r*r)/8.0;
    return NdotV / max(NdotV*(1.0-k)+k, EPSILON);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N,V),0.0);
    float NdotL = max(dot(N,L),0.0);
    return geometrySchlickGGX(NdotV,roughness)*geometrySchlickGGX(NdotL,roughness);
}

vec3 cookTorranceBRDF(vec3 N, vec3 V, vec3 L, vec3 radiance, vec3 albedo, float metallic, float roughness, vec3 F0) {
    vec3 H = normalize(V+L);
    float NdotV = max(dot(N,V),0.0);
    float NdotL = max(dot(N,L),0.0);
    float HdotV = max(dot(H,V),0.0);
    if(NdotL<=0.0) return vec3(0.0);

    float NDF = distributionGGX(N,H,roughness);
    float G = geometrySmith(N,V,L,roughness);
    vec3 F = fresnelSchlick(HdotV,F0);

    vec3 kS = F;
    vec3 kD = (1.0-metallic)*(vec3(1.0)-kS);
    vec3 diffuse = kD * albedo * INV_PI;
    vec3 specular = (NDF*G*F)/max(4.0*NdotV*NdotL,EPSILON);

    return (diffuse + specular) * radiance * NdotL;
}

float calculateAttenuation(float distance, float radius) {
    float constant = 1.0;
    float linear = 0.09;
    float quadratic = 0.032;
    float att = 1.0 / max(constant + linear*distance + quadratic*distance*distance, EPSILON);
    if(radius>0.0){
        float x = clamp(1.0 - distance/radius,0.0,1.0);
        att *= x*x*(3.0-2.0*x);
    }
    return att;
}

vec3 gammaCorrect(vec3 color){
    return pow(color, vec3(1.0/2.2));
}

// --- Main ---
void main(){
    if(useLighting==0){
        FragColor = overrideColor;
        return;
    }

    // Base color - convert to linear space
    vec4 baseColor = useTextures ? texture(diffuseSampler,outTexCoord) : material.diffuse;
    vec3 albedo = pow(baseColor.rgb, vec3(2.2));
    float metallic = material.metallic;
    float roughness = max(material.roughness, 0.04); // Clamp roughness to avoid artifacts
    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    vec3 N = normalize(Normal);
    vec3 V = normalize(viewPosition - FragPos);
    vec3 Lo = vec3(0.0);

    // --- Point Lights ---
    for(int i=0;i<numPointLights;i++){
        PointLight l = pointLights[i];
        vec3 L = l.position - FragPos;
        float dist = length(L);
        L /= max(dist,EPSILON);
        float att = calculateAttenuation(dist,l.radius);
        vec3 radiance = l.diffuse.rgb*l.strength*att;
        Lo += cookTorranceBRDF(N,V,L,radiance,albedo,metallic,roughness,F0);
    }

    // --- Directional Lights ---
    for(int i=0;i<numDirLights;i++){
        DirLight l = dirLights[i];
        vec3 L = normalize(-l.direction);
        vec3 radiance = l.diffuse.rgb*l.strength;
        Lo += cookTorranceBRDF(N,V,L,radiance,albedo,metallic,roughness,F0);
    }

    // Ambient light - convert to linear space and multiply by albedo
    vec3 ambientLinear = pow(material.ambient.rgb, vec3(2.2));
    vec3 ambient = ambientLinear * albedo;

    // Block light - treat as indirect lighting (already in linear space presumably)
    vec3 blockLightContribution = blockLight * albedo;

    // Emission - pure additive light, not multiplied by albedo
    vec3 emissionContribution = emission * albedo; // If emission should be colored by surface
    // OR use: vec3 emissionContribution = vec3(emission); // If emission is white light

    // Combine all lighting in linear space
    vec3 color = ambient + Lo + blockLightContribution + emissionContribution;

    // Apply gamma correction at the very end
    color = gammaCorrect(color);

    FragColor = vec4(color, baseColor.a);
}