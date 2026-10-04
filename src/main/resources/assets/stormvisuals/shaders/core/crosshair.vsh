#version 330
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection { mat4 ProjMat; };
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
out vec2 point;
flat out vec2 halfSize;
flat out vec4 fillColor;
flat out vec3 borderColor;
flat out float stroke;
flat out float border;
flat out int kind;
void main() {
    gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
    point=UV0;
    halfSize=vec2(UV1)/128.0;
    kind=UV2.x/2048;
    stroke=float(UV2.x%2048)/128.0;
    border=float(UV2.y)/128.0;
    fillColor=Color*ColorModulator;
    borderColor=(Normal+1.0)*0.5*ColorModulator.rgb;
}
