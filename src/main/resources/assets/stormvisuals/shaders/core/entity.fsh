#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
layout(std140) uniform StormLight { vec4 StormOriginRadius; };
in vec3 stormViewPosition;
in vec2 stormLightUv;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif
in vec4 lightMapColor;
in vec4 overlayColor;
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
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
#ifdef PER_FACE_LIGHTING
    color *= (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack) * ColorModulator;
#else
    color *= vertexColor * ColorModulator;
#endif
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    vec4 illumination = lightMapColor;
    if (StormOriginRadius.w > 0.0) illumination.rgb = max(illumination.rgb, stormDynamicLight().rgb);
    color *= illumination;
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
