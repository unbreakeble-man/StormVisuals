#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // Integrate four subpixel footprints using trilinear mipmap sampling.
    vec2 dx = dFdx(texCoord0);
    vec2 dy = dFdy(texCoord0);
    vec2 ox = dx * 0.25;
    vec2 oy = dy * 0.25;
    float coverage = textureGrad(Sampler0, texCoord0 - ox - oy, dx * 0.5, dy * 0.5).a;
    coverage += textureGrad(Sampler0, texCoord0 + ox - oy, dx * 0.5, dy * 0.5).a;
    coverage += textureGrad(Sampler0, texCoord0 - ox + oy, dx * 0.5, dy * 0.5).a;
    coverage += textureGrad(Sampler0, texCoord0 + ox + oy, dx * 0.5, dy * 0.5).a;
    // The monochrome PNG contributes coverage; transparent RGB cannot darken its edges.
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * coverage * 0.25);
}
