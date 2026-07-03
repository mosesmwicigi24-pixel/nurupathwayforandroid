// Retrofit surface for the member app — the versioned API (§3.1). Auth + /me for
// Phase 0; the remaining endpoints (pathway, grow, community, events, giving)
// land as their screens are ported. Suspend functions run on OkHttp's dispatcher.
package org.nuruplace.member.data.net

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MemberApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): LoginResponse

    @POST("auth/login/mfa")
    suspend fun completeMfa(@Body body: MfaBody): Session

    @GET("me")
    suspend fun me(): MeResponse

    // --- Pathway (server-authoritative gating §1.9) ---
    @GET("me/pathway")
    suspend fun pathway(): PathwaySummary

    @GET("levels/{n}/modules")
    suspend fun levelModules(@Path("n") levelNumber: Int): Envelope<LevelModule>

    @GET("modules/{id}")
    suspend fun module(@Path("id") moduleId: String): ModuleDetail

    @POST("modules/{id}/complete")
    suspend fun completeModule(@Path("id") moduleId: String, @Body body: CompleteBody): CompleteResult

    @GET("modules/{id}/quiz")
    suspend fun quiz(@Path("id") moduleId: String): AssembledQuiz

    @POST("modules/{id}/quiz/attempts")
    suspend fun submitQuiz(@Path("id") moduleId: String, @Body body: SubmitBody): QuizResult

    @GET("levels/{n}/exam")
    suspend fun levelExam(@Path("n") levelNumber: Int): AssembledExam

    @POST("levels/{n}/exam/attempts")
    suspend fun submitLevelExam(@Path("n") levelNumber: Int, @Body body: SubmitBody): ExamResult
}
