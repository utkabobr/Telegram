#version 300 es

precision highp float;

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec2 inVelocity;
layout(location = 2) in float inSpawnTime;
layout(location = 3) in float inPhase;
layout(location = 4) in float inLifetime;
layout(location = 5) in vec4 inColor;
layout(location = 6) in float inFractionX;

out vec2 outPosition;
out vec2 outVelocity;
out float outSpawnTime;
out float outPhase;
out float outLifetime;
out vec4 outColor;
out float outFractionX;

out vec4 color;
flat out int visible;

uniform float deltaTime;
uniform vec2 size;
uniform float r;
uniform int increaseVelocity;
uniform float maxVelocity;

float particleEaseInWindowFunction(float t) {
    return t;
}

float particleEaseInValueAt(float fraction, float t) {
    float windowSize = 0.8;

    float effectiveT = t;
    float windowStartOffset = -windowSize;
    float windowEndOffset = 1.0;

    float windowPosition = (1.0 - fraction) * windowStartOffset + fraction * windowEndOffset;
    float windowT = max(0.0, min(windowSize, effectiveT - windowPosition)) / windowSize;
    float localT = 1.0 - particleEaseInWindowFunction(windowT);

    return localT;
}

void main() {
    float phase = inPhase;
    phase += deltaTime;
    float lifetime = inLifetime;

    visible = phase >= inSpawnTime && phase <= 4.0 ? 1 : 0;

    vec2 velocity = vec2(inVelocity);
    vec2 position = vec2(inPosition);

    if (visible == 1) {
        float easeInDuration = 0.3;
        float effectFraction = max(0.0, min(easeInDuration, phase)) / easeInDuration;

        float particleFraction = particleEaseInValueAt(effectFraction, inFractionX);

        position += (velocity * deltaTime) * particleFraction;
        if (increaseVelocity == 1) {
            velocity = velocity * (1.0 - particleFraction) + velocity * 1.001 * particleFraction;
            if (phase < easeInDuration) {
                velocity *= 1.001;
            }
        }
        if (phase < easeInDuration) {
            velocity -= normalize(velocity) * deltaTime * 2.0;
        }
        if (phase >= easeInDuration + 0.15) {
            velocity += vec2(0.0, deltaTime * 480.0);
        }
        if (length(velocity) > maxVelocity) {
            velocity = normalize(velocity) * maxVelocity;
        }
        lifetime = max(0.0, lifetime - deltaTime);
    }

    outPosition = position;
    outVelocity = velocity;
    outPhase = phase;
    outLifetime = lifetime;

    outSpawnTime = inSpawnTime;
    outColor = inColor;
    outFractionX = inFractionX;

    if (visible == 0) {
        return;
    }

    gl_PointSize = r;
    gl_Position = vec4((position / size * 2.0 - vec2(1.0)), 0.0, 1.0);
    color = vec4(inColor.rgb, inColor.a * max(0.0, min(0.3, lifetime) / 0.3));
}
