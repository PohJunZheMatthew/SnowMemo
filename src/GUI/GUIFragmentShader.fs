#version 330 core

in vec2 outTexCoord;
out vec4 FragColor;

uniform sampler2D guiTexture;

void main()
{
    FragColor = texture(guiTexture, outTexCoord);
}
