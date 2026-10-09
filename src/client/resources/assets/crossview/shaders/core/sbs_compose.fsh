#version 450

// CrossView side by side: builds one image twice as wide from two eye images.
// The left half shows Sampler0 (eye 0), the right half shows Sampler1 (eye 1).
// texCoord runs 0..1 across the WHOLE output, so each half remaps it back to 0..1.
// Both eyes are sampled unconditionally (the sampler clamps at the edges) and the right one is
// picked afterwards, which keeps the texture reads out of divergent branches.

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

void main() {
    vec4 leftEye = texture(Sampler0, vec2(texCoord.x * 2.0, texCoord.y));
    vec4 rightEye = texture(Sampler1, vec2(texCoord.x * 2.0 - 1.0, texCoord.y));
    fragColor = texCoord.x < 0.5 ? leftEye : rightEye;
}
