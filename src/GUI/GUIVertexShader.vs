#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;

uniform mat4 projection;
uniform vec2 position;
uniform vec2 size;

out vec2 outTexCoord;

void main()
{
    vec2 scaledPos = aPos.xy * size + position;
    gl_Position = projection * vec4(scaledPos, 0.0, 1.0);
    outTexCoord = aTexCoord;
}
