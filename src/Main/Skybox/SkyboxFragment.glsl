#version 330 core

in vec3 passNormal;
in vec2 passTex;

uniform vec4 ambientLight;
uniform vec4 gradientBottom;
uniform vec4 gradientTop;

out vec4 fragColor;

void main() {
    float t = (passNormal.y + 1.0) * 0.5;
    vec4 gradient = mix(gradientBottom, gradientTop, t);
    fragColor = gradient * ambientLight;
}
