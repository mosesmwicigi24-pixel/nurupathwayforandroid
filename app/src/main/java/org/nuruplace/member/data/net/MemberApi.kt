// Retrofit surface for the member app — the versioned API (§3.1). Auth + /me for
// Phase 0; the remaining endpoints (pathway, grow, community, events, giving)
// land as their screens are ported. Suspend functions run on OkHttp's dispatcher.
package org.nuruplace.member.data.net

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface MemberApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): LoginResponse

    @POST("auth/login/mfa")
    suspend fun completeMfa(@Body body: MfaBody): Session

    @POST("auth/register")
    suspend fun register(@Body body: RegisterBody): Session

    @POST("auth/password/forgot")
    suspend fun forgotPassword(@Body body: ForgotBody): ForgotRes

    @POST("auth/password/reset")
    suspend fun resetPassword(@Body body: ResetBody): Unit

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

    // --- Grow: daily rhythm & Word (§1.7 writes are online-first for now) ---
    @GET("growth/devotional")
    suspend fun devotional(): Devotional

    @POST("growth/devotional/reflection")
    suspend fun saveDevotionalReflection(@Body body: DevotionalReflectionBody): SavedFlag

    @GET("growth/memory-verses")
    suspend fun memoryVerses(): Envelope<MemoryVerseRow>

    @POST("growth/memory-verses/practice")
    suspend fun practiceVerse(@Body body: PracticeBody): Unit

    @GET("growth/plans")
    suspend fun plans(): Envelope<ReadingPlanRow>

    @GET("growth/plans/{id}")
    suspend fun plan(@Path("id") planId: String): ReadingPlanDetail

    @POST("growth/plans/{id}/start")
    suspend fun startPlan(@Path("id") planId: String): Unit

    @POST("growth/plans/{id}/complete-day")
    suspend fun completePlanDay(@Path("id") planId: String, @Body body: CompleteDayBody): Unit

    @POST("growth/segments/{id}/complete")
    suspend fun completeSegment(@Path("id") segmentId: String): Unit

    // --- Prayer journal (private, §5.4) ---
    @GET("me/prayers")
    suspend fun prayers(): Envelope<PrayerEntry>

    @PUT("me/prayers")
    suspend fun upsertPrayer(@Body body: PrayerUpsertBody): Unit

    @DELETE("me/prayers/{id}")
    suspend fun deletePrayer(@Path("id") entryId: String): Unit

    // --- Verse library ---
    @GET("me/verses")
    suspend fun verses(): Envelope<SavedVerse>

    @PUT("me/verses")
    suspend fun saveVerse(@Body body: VerseUpsertBody): Unit

    @DELETE("me/verses/{id}")
    suspend fun deleteVerse(@Path("id") savedVerseId: String): Unit

    // --- Community: Prayer wall (public, opt-in) ---
    @GET("prayer-wall")
    suspend fun prayerWall(@retrofit2.http.Query("sort") sort: String = "latest"): Envelope<PrayerWallPost>

    @GET("prayer-wall/{id}")
    suspend fun prayerWallGet(@Path("id") postId: String): PrayerWallDetail

    @POST("prayer-wall")
    suspend fun createPrayerWallPost(@Body body: CreatePrayerBody): Unit

    @POST("prayer-wall/{id}/reactions")
    suspend fun prayerWallReact(@Path("id") postId: String, @Body body: ReactBody): ReactOn

    @POST("prayer-wall/{id}/comments")
    suspend fun prayerWallComment(@Path("id") postId: String, @Body body: PrayerCommentBody): Unit

    @POST("prayer-wall/{id}/answered")
    suspend fun prayerWallAnswered(@Path("id") postId: String, @Body body: AnsweredBody): Unit

    @DELETE("prayer-wall/{id}")
    suspend fun deletePrayerWallPost(@Path("id") postId: String): Unit

    // --- Chat ---
    @GET("chat/conversations")
    suspend fun chatInbox(@retrofit2.http.Query("scope") scope: String = "mine"): ChatInbox

    @GET("chat/conversations/{id}")
    suspend fun chatConversation(@Path("id") conversationId: String): ChatThreadDetail

    @POST("chat/conversations/{id}/messages")
    suspend fun sendChatMessage(@Path("id") conversationId: String, @Body body: SendMessageBody): Unit

    @POST("chat/conversations/{id}/read")
    suspend fun markChatRead(@Path("id") conversationId: String): Unit

    @GET("chat/people")
    suspend fun chatPeople(@retrofit2.http.Query("q") query: String? = null): PeopleRes

    @POST("chat/dms")
    suspend fun createDm(@Body body: DmBody): DmRes

    @POST("chat/spaces/{id}/join")
    suspend fun joinChatSpace(@Path("id") conversationId: String): Unit

    @POST("chat/messages/{id}/reactions")
    suspend fun toggleChatReaction(@Path("id") messageId: String, @Body body: ReactBody): ReactOn

    // --- Events / calendar ---
    @GET("calendar")
    suspend fun calendar(@retrofit2.http.Query("from") from: String, @retrofit2.http.Query("to") to: String): Envelope<CalendarOccurrence>

    @GET("events/{id}")
    suspend fun event(@Path("id") eventId: String): EventDetail

    @POST("events/{id}/rsvp")
    suspend fun rsvp(@Path("id") eventId: String, @Body body: RsvpBody): Unit

    // --- Notification center ---
    @GET("me/notifications")
    suspend fun notifications(): NotificationsRes

    @POST("me/notifications/read")
    suspend fun markNotificationsRead(@Body body: MarkReadBody): Unit

    // --- Giving (online-only, §5.6 — money is never queued) ---
    @GET("giving/history")
    suspend fun givingHistory(): Envelope<GivingRecord>

    @POST("giving/intents")
    suspend fun giving(@Body body: GiveBody): GivingIntentResult

    @GET("giving/transactions/{id}")
    suspend fun givingDetail(@Path("id") transactionId: String): GivingDetail

    // --- Profile / growth: scores, gifts, resources, assistant ---
    @GET("me/scores")
    suspend fun scores(): ScoresSummary

    @GET("me/gifts")
    suspend fun myGifts(): MyGifts

    @GET("gifts/questions")
    suspend fun giftQuestions(): GiftQuestionSet

    @POST("gifts/assessments")
    suspend fun submitGifts(@Body body: GiftSubmitBody): MyGifts

    @GET("growth/resources")
    suspend fun resources(): Envelope<ResourceRow>

    @GET("assistant/history")
    suspend fun assistantHistory(): AssistantHistoryRes

    @POST("assistant/chat")
    suspend fun assistantChat(@Body body: AssistantChatBody): AssistantReplyRes

    // --- Home dashboard ---
    @GET("me/rhythm/today")
    suspend fun rhythmToday(): RhythmToday

    @POST("me/rhythm/complete")
    suspend fun completeRhythm(@Body body: RhythmBody): RhythmToday

    @GET("me/home/next-action")
    suspend fun nextAction(): NextActionEnvelope

    @GET("me/home/verse")
    suspend fun homeVerse(): TailoredVerse

    @GET("me/achievements")
    suspend fun achievements(): Achievements

    // --- Recurring giving schedules ---
    @GET("giving/schedules")
    suspend fun schedules(): Envelope<GivingSchedule>

    @POST("giving/schedules/{id}/cancel")
    suspend fun cancelSchedule(@Path("id") scheduleId: String): Unit

    // --- Announcements ---
    @GET("me/announcements")
    suspend fun myAnnouncements(): Envelope<MyAnnouncement>

    @GET("announcements/{id}")
    suspend fun announcement(@Path("id") announcementId: String): AnnouncementDetail

    @POST("announcements/{id}/open")
    suspend fun openAnnouncement(@Path("id") announcementId: String): Unit

    @GET("home/featured-announcement")
    suspend fun featuredAnnouncement(): FeaturedAnnouncementEnv

    // --- Event series + buzz posts ---
    @GET("calendar/series")
    suspend fun eventSeries(): Envelope<EventSeries>

    @POST("calendar/series/{id}/follow")
    suspend fun toggleSeriesFollow(@Path("id") seriesId: String): SeriesFollowResult

    @GET("events/{id}/posts")
    suspend fun eventPosts(@Path("id") eventId: String): Envelope<EventPost>

    @POST("events/{id}/posts")
    suspend fun createEventPost(@Path("id") eventId: String, @Body body: EventPostBody): retrofit2.Response<Unit>

    @POST("events/{id}/posts/{postId}/react")
    suspend fun reactToEventPost(@Path("id") eventId: String, @Path("postId") postId: String, @Body body: EventReactBody): EventPostReactionResult

    // --- Home extras ---
    @GET("home/featured-cell")
    suspend fun featuredCell(): FeaturedCellEnv

    @GET("home/disciplers")
    suspend fun disciplers(): Envelope<Discipler>

    @GET("moments")
    suspend fun moments(): Envelope<Moment>

    @GET("growth/mentor")
    suspend fun mentor(): MentorInfo

    @GET("me/cell-summary")
    suspend fun cellSummary(): CellSummary

    @GET("me/scores/{pillar}")
    suspend fun scoreDetail(@Path("pillar") pillar: String): ScoreBreakdown

    // --- Home welcome video + media reactions ---
    @GET("home/welcome-video")
    suspend fun welcomeVideo(): WelcomeVideo?

    @POST("media/{id}/reactions")
    suspend fun toggleMediaReaction(@Path("id") mediaAssetId: String, @Body body: MediaReactBody): ReactionToggleResult

    // --- Profile: notification prefs + MFA ---
    @GET("me/notification-preferences")
    suspend fun notificationPreferences(): NotificationPreferences

    @PUT("me/notification-preferences")
    suspend fun updateNotificationPreferences(@Body body: NotificationPreferences): Unit

    @POST("auth/mfa/enroll")
    suspend fun enrollMfa(@Body body: EmptyBody = EmptyBody()): MfaEnrollment

    @POST("auth/mfa/verify")
    suspend fun verifyMfa(@Body body: MfaCodeBody): Unit

    @POST("auth/mfa/disable")
    suspend fun disableMfa(@Body body: MfaCodeBody): Unit
}
