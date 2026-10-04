#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 cornerUv;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // Each quarter of the rectangle receives inward coordinates in radius units.
    vec2 outside = max(vec2(1.0) - cornerUv, vec2(0.0));
    float distanceToEdge = length(outside) - 1.0;
    float pixelWidth = max(length(vec2(dFdx(distanceToEdge), dFdy(distanceToEdge))), 0.0001);
    float coverage = clamp(0.5 - distanceToEdge / pixelWidth, 0.0, 1.0);

    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 1.0 / 255.0) {
        discard;
    }
    fragColor = color;
}
