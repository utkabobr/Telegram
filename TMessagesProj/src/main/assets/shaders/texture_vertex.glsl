precision mediump float;

attribute vec2 v_position;

void main() {
    gl_Position = vec4(v_position, 1, 1);
}