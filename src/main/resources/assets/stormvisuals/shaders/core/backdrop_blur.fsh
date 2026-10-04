#version 330

uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
#ifdef DOWNSAMPLE
    // Four bilinear samples reduce aliasing before the small blur passes.
    vec3 color = texture(Sampler0, texCoord + texel * vec2(-1.0, -1.0)).rgb;
    color += texture(Sampler0, texCoord + texel * vec2(1.0, -1.0)).rgb;
    color += texture(Sampler0, texCoord + texel * vec2(-1.0, 1.0)).rgb;
    color += texture(Sampler0, texCoord + texel * vec2(1.0, 1.0)).rgb;
    fragColor = vec4(color * 0.25, 1.0);
#else
#ifdef HORIZONTAL
    vec2 direction = vec2(texel.x, 0.0);
#else
    vec2 direction = vec2(0.0, texel.y);
#endif
    // Dense 65-tap Gaussian (sigma 10.5), paired into 33 bilinear samples.
    // Each pair covers adjacent texels: no gaps from stretching a small kernel.
    vec3 color = texture(Sampler0, texCoord).rgb * 0.0380690728;
    color += (texture(Sampler0, texCoord + direction * 1.4965986919).rgb
             + texture(Sampler0, texCoord - direction * 1.4965986919).rgb) * 0.0752815185;
    color += (texture(Sampler0, texCoord + direction * 3.4920641585).rgb
             + texture(Sampler0, texCoord - direction * 3.4920641585).rgb) * 0.0719510499;
    color += (texture(Sampler0, texCoord + direction * 5.4875309305).rgb
             + texture(Sampler0, texCoord - direction * 5.4875309305).rgb) * 0.0663231133;
    color += (texture(Sampler0, texCoord + direction * 7.4829997528).rgb
             + texture(Sampler0, texCoord - direction * 7.4829997528).rgb) * 0.0589619264;
    color += (texture(Sampler0, texCoord + direction * 9.4784713689).rgb
             + texture(Sampler0, texCoord - direction * 9.4784713689).rgb) * 0.0505542179;
    color += (texture(Sampler0, texCoord + direction * 11.4739465206).rgb
             + texture(Sampler0, texCoord - direction * 11.4739465206).rgb) * 0.0418044077;
    color += (texture(Sampler0, texCoord + direction * 13.4694259472).rgb
             + texture(Sampler0, texCoord - direction * 13.4694259472).rgb) * 0.0333400057;
    color += (texture(Sampler0, texCoord + direction * 15.4649103854).rgb
             + texture(Sampler0, texCoord - direction * 15.4649103854).rgb) * 0.0256441403;
    color += (texture(Sampler0, texCoord + direction * 17.4604005686).rgb
             + texture(Sampler0, texCoord - direction * 17.4604005686).rgb) * 0.0190234576;
    color += (texture(Sampler0, texCoord + direction * 19.4558972262).rgb
             + texture(Sampler0, texCoord - direction * 19.4558972262).rgb) * 0.0136103582;
    color += (texture(Sampler0, texCoord + direction * 21.4514010837).rgb
             + texture(Sampler0, texCoord - direction * 21.4514010837).rgb) * 0.0093913579;
    color += (texture(Sampler0, texCoord + direction * 23.4469128617).rgb
             + texture(Sampler0, texCoord - direction * 23.4469128617).rgb) * 0.0062497969;
    color += (texture(Sampler0, texCoord + direction * 25.4424332758).rgb
             + texture(Sampler0, texCoord - direction * 25.4424332758).rgb) * 0.0040112712;
    color += (texture(Sampler0, texCoord + direction * 27.4379630358).rgb
             + texture(Sampler0, texCoord - direction * 27.4379630358).rgb) * 0.0024829998;
    color += (texture(Sampler0, texCoord + direction * 29.4335028458).rgb
             + texture(Sampler0, texCoord - direction * 29.4335028458).rgb) * 0.0014823468;
    color += (texture(Sampler0, texCoord + direction * 31.4290534031).rgb
             + texture(Sampler0, texCoord - direction * 31.4290534031).rgb) * 0.0008534957;
    fragColor = vec4(color, 1.0);
#endif
}
