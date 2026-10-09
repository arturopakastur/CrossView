#version 450

// CrossView desaturation: lerp between the original color and its channel average.
// Amount = 0 leaves the image alone, Amount = 1 makes it grayscale. This is exactly the original
// mod's matrix (t on the diagonal, (1 - t) / 2 elsewhere) written in a simpler form.

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

uniform sampler2D InSampler;

layout(std140) uniform CrossViewDesaturate {
    float Amount;
};

void main() {
    vec4 color = texture(InSampler, texCoord);
    float gray = (color.r + color.g + color.b) / 3.0;
    fragColor = vec4(mix(color.rgb, vec3(gray), Amount), color.a);
}
