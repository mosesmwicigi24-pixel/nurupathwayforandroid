// Nuru Live L6d — a RootEncoder object filter, mechanically identical to the
// library's own SurfaceFilterRender (the class GuestStageCompositor.kt's
// MAIN/speaker slot still uses unchanged — see that file's header for the
// full verified WebRTC-EglRenderer-into-a-Surface mechanism), except it
// compiles against a CUSTOM fragment shader (res/raw/stage_rail_fragment.glsl)
// instead of RootEncoder's stock surface_fragment, so the Zoom-style guest
// rail's tiles get rounded corners + a subtle border baked directly into the
// composited RTMP frame (task brief: "rounded corners, a subtle light
// border... so they read against any background").
//
// ── WHY THIS CAN'T JUST SUBCLASS SurfaceFilterRender ────────────────────────
// SurfaceFilterRender's own initGlFilter() (verified via `javap` against the
// pinned RootEncoder 2.5.9 encoder-2.5.9.aar's classes.jar) unconditionally
// does `this.fragment = R.raw.surface_fragment` BEFORE calling
// BaseObjectFilterRender.initGlFilter() (the call that actually compiles the
// GL program from whatever `fragment` currently holds) — so a subclass
// setting a different `fragment` value before calling super.initGlFilter()
// would just get clobbered by that same super call. Extending
// BaseObjectFilterRender DIRECTLY instead — and replicating
// SurfaceFilterRender's own (verified, decompiled) SurfaceTexture/external-
// OES-texture plumbing here — sidesteps that: `fragment` is
// BaseObjectFilterRender's OWN protected field, safe to set right before
// ITS initGlFilter() runs.
//
// ── WHY THE SHADER USES GLSL CONSTANTS, NOT UNIFORMS, FOR ITS CHROME KNOBS ──
// BaseObjectFilterRender's compiled-program handle (`program`) is a PRIVATE
// field with no accessor, so a subclass has no way to glGetUniformLocation()
// a uniform of its own — only the handles BaseObjectFilterRender itself
// already looks up (uAlphaHandle etc., all `protected`) are reachable from
// here. Every rail tile in GuestStageCompositor.kt is built with the exact
// same fixed shape (RAIL_TILE_ASPECT there) by design, so baking the corner
// radius/border width/aspect as GLSL `const float`s in
// stage_rail_fragment.glsl — rather than passing them as per-instance
// uniforms — is exactly equivalent here, not a workaround with a downside.
package org.nuruplace.member.feature.live

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import com.pedro.encoder.input.gl.render.filters.`object`.BaseObjectFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import org.nuruplace.member.R

class StageTileFilterRender : BaseObjectFilterRender() {

    private var surfaceTexture: SurfaceTexture? = null

    /** Non-null once [initGlFilter] has actually run on RootEncoder's GL
     *  thread (async relative to gl.addFilter() at bind() time — see
     *  GuestStageCompositor.ensureRenderer()'s own doc, which polls this the
     *  same way it already polls SurfaceFilterRender.surface). */
    var surface: Surface? = null
        private set

    // Mirrors SurfaceFilterRender's exact verified sequence: point `fragment`
    // at OUR custom raw resource before calling super (which compiles the
    // program from it, paired with the library's own unchanged
    // object_vertex.glsl), then set up the external-OES SurfaceTexture the
    // same way SurfaceFilterRender does — reusing the INHERITED
    // streamObjectTextureId array (already allocated by BaseObjectFilterRender's
    // own constructor), not a new one, to match that verified behavior
    // exactly.
    override fun initGlFilter(context: Context) {
        fragment = R.raw.stage_rail_fragment
        super.initGlFilter(context)
        GlUtil.createExternalTextures(streamObjectTextureId.size, streamObjectTextureId, 0)
        val st = SurfaceTexture(streamObjectTextureId[0])
        st.setDefaultBufferSize(getWidth(), getHeight())
        surfaceTexture = st
        surface = Surface(st)
    }

    // Identical to SurfaceFilterRender.drawFilter()'s verified sequence:
    // pull the latest frame, let the base class bind the background +
    // set up the standard vertex/uniform state (leaving GL_TEXTURE1 active,
    // per BaseObjectFilterRender.drawFilter()'s own verified tail), THEN
    // bind our external-OES object texture to that already-active unit and
    // (re)assert uAlpha.
    override fun drawFilter() {
        surfaceTexture?.updateTexImage()
        super.drawFilter()
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, streamObjectTextureId[0])
        GLES20.glUniform1f(uAlphaHandle, if (streamObjectTextureId[0] == -1) 0f else alpha)
    }

    override fun release() {
        super.release()
        surfaceTexture?.release()
        surface?.release()
        surfaceTexture = null
        surface = null
    }
}
