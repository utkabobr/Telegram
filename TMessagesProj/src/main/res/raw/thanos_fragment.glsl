#version 300 es

precision highp float;

in vec4 color;
flat in int visible;
out vec4 fragColor;

void main() {
  vec2 circCoord = 2.0 * gl_PointCoord - 1.0;
  if (dot(circCoord, circCoord) > 1.0 || visible == 0) { // If out of screen bounds
    discard;
  }

  fragColor = color;
}