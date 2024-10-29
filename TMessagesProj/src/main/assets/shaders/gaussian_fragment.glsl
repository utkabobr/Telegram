precision highp float;
precision highp sampler2D;

uniform vec2 u_resolution;
uniform sampler2D u_texture;
uniform vec2 u_direction;
uniform vec4 u_rect;
uniform vec4 u_bgcolor;

vec4 resolve_colored(vec4 color, vec4 near) {
    if (color.a == 0.0) color = u_bgcolor;
    return near.a == 0.0 ? vec4(color.rgb, near.a) : near;
}

vec4 blur(sampler2D image, vec2 uv, vec2 resolution, vec2 direction) {
    vec4 pix = texture2D(image, uv);
    vec4 color = vec4(0.0);
    vec2 off1 = vec2(1.3333333333333333) * direction;
    color += resolve_colored(pix, pix * 0.29411764705882354);
    color += resolve_colored(pix, texture2D(image, uv + (off1 / resolution)) * 0.35294117647058826);
    color += resolve_colored(pix, texture2D(image, uv - (off1 / resolution)) * 0.35294117647058826);
    return color;
}

void main() {
    vec2 uv = vec2(gl_FragCoord.xy / u_resolution.xy);
    if (gl_FragCoord.x < u_rect.x || gl_FragCoord.x > u_rect.z || gl_FragCoord.y < u_rect.y || gl_FragCoord.y > u_rect.w) {
        gl_FragColor = vec4(0.);
        return;
    }

    gl_FragColor = blur(u_texture, uv, u_resolution.xy, u_direction);
}