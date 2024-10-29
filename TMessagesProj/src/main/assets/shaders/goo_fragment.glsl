#version 300 es

precision highp float;

uniform vec2 u_resolution;
uniform sampler2D u_texture;

in vec2 tex_coord;
out vec4 fragColor;

void main() {
    vec2 uv = vec2(gl_FragCoord.xy / u_resolution.xy);
    uv.y = 1.0 - uv.y;
    vec4 color = texture(u_texture, uv);
    color.a = min(1.0, max(0.0, color.a * 120.0 - 60.0));
    if (color.a < 1.0) {
        color = vec4(0.);
    }
    fragColor = color;
}