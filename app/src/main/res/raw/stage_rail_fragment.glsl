// Nuru Live L6d — StageTileFilterRender.kt's fragment shader. A close
// derivative of RootEncoder 2.5.9's own encoder/src/main/res/raw/
// surface_fragment.glsl (verified via `unzip` on the pinned library-2.5.9.aar
// — the stock file is reproduced below as the control-flow skeleton,
// including its exact "outside the sprite's own bounds -> pass the
// background through untouched" guard), ADDING only the rounded-corner +
// subtle-border chrome the Zoom-style guest rail needs. The vertex shader
// (object_vertex.glsl) is UNCHANGED/untouched — RootEncoder's
// BaseObjectFilterRender always compiles against that fixed vertex shader,
// only `fragment` is swappable per-instance (see StageTileFilterRender.kt's
// header for the full verified mechanism and why a plain SurfaceFilterRender
// subclass can't do this) — so this file's varyings/uniforms MUST match
// object_vertex.glsl's outputs exactly: vTextureCoord/vTextureObjectCoord,
// both already normalized 0..1 by the vertex stage's own uSTMatrix multiply.
#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uObject;
uniform sampler2D uSampler;
uniform float uAlpha;

varying vec2 vTextureCoord;
varying vec2 vTextureObjectCoord;

// Every rail tile in GuestStageCompositor.kt is built with the SAME fixed
// shape (RAIL_TILE_ASPECT there) — so, unlike a per-instance uniform (which
// BaseObjectFilterRender's private `program` field makes unreachable from a
// subclass — see StageTileFilterRender.kt), a build-time GLSL constant here
// is exactly equivalent, not a compromise. Keep ASPECT in sync with
// RAIL_TILE_ASPECT if that ever changes.
const float ASPECT = 0.75;
const float CORNER_RADIUS = 0.16;
const float BORDER_WIDTH = 0.03;
const float AA = 0.01;

void main() {
  vec4 samplerPixel = texture2D(uSampler, vTextureCoord);
  if (vTextureObjectCoord.x < 0.0 || vTextureObjectCoord.x > 1.0 ||
      vTextureObjectCoord.y < 0.0 || vTextureObjectCoord.y > 1.0) {
    // Outside this tile's own sprite bounds entirely — same background
    // pass-through as the stock shader, untouched.
    gl_FragColor = samplerPixel;
  } else {
    // Local, aspect-corrected unit space: 1.0 unit == the tile's own width,
    // so a corner "radius" reads as a circular arc rather than an ellipse
    // even though the tile itself isn't square.
    vec2 sizeUnits = vec2(1.0, 1.0 / ASPECT);
    vec2 posUnits = (vTextureObjectCoord - 0.5) * sizeUnits;
    vec2 halfSize = sizeUnits * 0.5;
    vec2 d = abs(posUnits) - halfSize + vec2(CORNER_RADIUS);
    float dist = length(max(d, 0.0)) - CORNER_RADIUS; // <0 inside the rounded rect, >0 outside

    float shapeAlpha = 1.0 - smoothstep(-AA, AA, dist);
    vec4 objectPixel = texture2D(uObject, vTextureObjectCoord);
    float borderMix = smoothstep(-BORDER_WIDTH - AA, -BORDER_WIDTH + AA, dist) * shapeAlpha;
    // Subtle light border so the tile reads against any background — task
    // brief's own wording.
    vec3 content = mix(objectPixel.rgb, vec3(1.0), borderMix * 0.55);
    float finalAlpha = objectPixel.a * uAlpha * shapeAlpha;
    gl_FragColor = mix(samplerPixel, vec4(content, 1.0), finalAlpha);
  }
}
