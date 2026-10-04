#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 texel = texture(Sampler0, texCoord);
    // Tighten the PNG's soft edge without changing its silhouette or drawing a procedural shape.
    texel.a = smoothstep(0.08, 0.8, texel.a);
    // Transparent PNG pixels contain black RGB; sampling those must not darken a tinted soul's edge.
    vec4 color = vec4(vertexColor.rgb, texel.a * vertexColor.a) * ColorModulator;
    if (color.a <= 1.0 / 255.0) discard;
    fragColor = color;
}
