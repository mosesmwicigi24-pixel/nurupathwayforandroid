// Retrofit surface for the member app — the versioned API (§3.1). Auth + /me for
// Phase 0; the remaining endpoints (pathway, grow, community, events, giving)
// land as their screens are ported. Suspend functions run on OkHttp's dispatcher.
package org.nuruplace.member.data.net

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface MemberApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): LoginResponse

    @POST("auth/login/mfa")
    suspend fun completeMfa(@Body body: MfaBody): Session

    @GET("me")
    suspend fun me(): MeResponse
}
