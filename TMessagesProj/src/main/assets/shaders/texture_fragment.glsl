precision highp float;

uniform vec2 u_resolution;
uniform sampler2D u_texture;
uniform bool u_fade;
uniform float u_fade_from;
uniform float u_fade_to;

void main() {
    vec2 uv = vec2(gl_FragCoord.xy / u_resolution.xy);
    uv.y = 1.0 - uv.y;
    vec4 clr = texture2D(u_texture, uv);
    if (u_fade) {
        float y = u_resolution.y - gl_FragCoord.y;
        if (y >= u_fade_from && y <= u_fade_to) {
            clr = mix(clr, vec4(0.), (y - u_fade_from) / (u_fade_to - u_fade_from));
        } else if (y > u_fade_to) {
            clr = vec4(0.);
        }
    }
    gl_FragColor = clr;
}