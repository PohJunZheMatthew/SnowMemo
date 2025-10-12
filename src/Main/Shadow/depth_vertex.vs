#version 330

layout (location=0) in vec3 position;
layout (location=1) in vec3 vertexNormal;  // Match your mesh layout
layout (location=2) in vec2 texCoord;      // Match your mesh layout

uniform mat4 modelLightViewMatrix;
uniform mat4 orthoProjectionMatrix;

void main()
{
    gl_Position = orthoProjectionMatrix * modelLightViewMatrix * vec4(position, 1.0);
}