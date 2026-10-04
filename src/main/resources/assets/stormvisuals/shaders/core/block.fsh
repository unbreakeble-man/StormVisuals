#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
layout(std140) uniform StormLight { vec4 StormOriginRadius; };
in vec3 stormViewPosition;
in vec2 stormLightUv;
in vec4 stormRawColor;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec4 stormDynamicLight() {
    float radius = StormOriginRadius.w;
    float distanceToLight = length(stormViewPosition - StormOriginRadius.xyz);
    float strength = 1.0 - smoothstep(0.0, max(radius, 0.0001), distanceToLight);
    vec2 uv = clamp(stormLightUv, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    uv.x = max(uv.x, (strength * 15.0 + 0.5) / 16.0);
    return texture(Sampler2, uv);
}
void main() {
    vec4 litColor = vertexColor;
    if (StormOriginRadius.w > 0.0) litColor.rgb = max(litColor.rgb, stormRawColor.rgb * stormDynamicLight().rgb);
    vec4 color = texture(Sampler0, texCoord0) * litColor * ColorModulator;
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
