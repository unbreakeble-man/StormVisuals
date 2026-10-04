#version 330

uniform sampler2D HandMask;
layout(std140) uniform HandSettings {
    vec4 Settings; // animation time, framebuffer width/height, opacity
};
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 hand = texture(HandMask, texCoord);
    if (hand.a <= 0.001) discard;

    // Preserve the supplied tileable five-iteration water pattern.
    vec2 uv = gl_FragCoord.xy / Settings.yz;
    vec2 p = mod(uv * 6.28318530718, 6.28318530718) - 250.0;
    vec2 scaledP = p * 0.005;
    vec2 i = p;
    float c = 1.0;
    for (int n = 0; n < 5; ++n) {
        float t = Settings.x * (1.0 - 3.5 / float(n + 1));
        i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
        vec2 denominator = vec2(sin(i.x + t), cos(i.y + t));
        // Avoid NaNs at trigonometric zero crossings.
        denominator = mix(vec2(-1.0), vec2(1.0), step(vec2(0.0), denominator))
                * max(abs(denominator), vec2(0.00001));
        vec2 distance = scaledP / denominator;
        c += inversesqrt(max(dot(distance, distance), 0.00001));
    }
    c = abs(1.17 - pow(c * 0.2, 1.4));
    float c2 = c * c;
    float c4 = c2 * c2;
    float wave = clamp(c4 * c4, 0.0, 1.0);
    vec3 colour = clamp(vec3(wave) + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
    // Even at maximum opacity, bright crests retain 38% of the original item.
    float amount = Settings.w * (0.08 + 0.54 * wave);
    fragColor = vec4(mix(hand.rgb, colour * hand.a, amount), hand.a);
}
