#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
in vec2 cornerUv;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 outside = max(vec2(1.0) - cornerUv, vec2(0.0));
    float distanceToEdge = length(outside) - 1.0;
    float pixelWidth = max(length(vec2(dFdx(distanceToEdge), dFdy(distanceToEdge))), 0.0001);
    float coverage = clamp(0.5 - distanceToEdge / pixelWidth, 0.0, 1.0);
    if (coverage <= 1.0 / 255.0) discard;
    // Framebuffer UVs stay aligned with the world, including GUI scale changes.
    vec3 scene = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
    // Keep a subdued hint of world color without a strong grass or sky tint.
    float luminance = dot(scene, vec3(0.2126, 0.7152, 0.0722));
    vec3 glass = mix(vec3(luminance), scene, 0.55);
    fragColor = vec4(glass, coverage * vertexColor.a);
}
