#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

float median(float a, float b, float c) {
    return max(min(a, b), min(max(a, b), c));
}

void main() {
    vec4 sampleValue = texture(Sampler0, texCoord);
    vec2 unitRange = vec2(8.0) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / max(fwidth(texCoord), vec2(0.000001));
    float screenRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float msdf = median(sampleValue.r, sampleValue.g, sampleValue.b);
    float signedDistance = mix(sampleValue.a, msdf, clamp(screenRange * 0.5, 0.0, 1.0));
    float coverage = clamp(screenRange * (signedDistance - 0.5) + 0.5, 0.0, 1.0);
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 1.0 / 255.0) discard;
    fragColor = color;
}
