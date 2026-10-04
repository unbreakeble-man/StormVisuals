#version 330

out vec2 texCoord;

void main() {
    // A single oversized triangle covers the target without a vertex buffer.
    vec2 position = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    texCoord = position;
    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
}
