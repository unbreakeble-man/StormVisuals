#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    float distanceToEdge = length(texCoord0 - vec2(0.5)) - 0.5;
    float coverage = clamp(0.5 - distanceToEdge / max(fwidth(distanceToEdge), 0.00001), 0.0, 1.0);
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 1.0 / 255.0) discard;
    fragColor = color;
}
