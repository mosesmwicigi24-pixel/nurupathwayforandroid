// Retrofit surface for the member app — the versioned API (§3.1). Auth + /me for
// Phase 0; the remaining endpoints (pathway, grow, community, events, giving)
// land as their screens are ported. Suspend functions run on OkHttp's dispatcher.
package org.nuruplace.member.data.net

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MemberApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): LoginResponse

    // 204; revokes the refresh token server-side on sign-out.
    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutBody): Unit

    @POST("auth/login/mfa")
    suspend fun completeMfa(@Body body: MfaBody): Session

    @POST("auth/register")
    suspend fun register(@Body body: RegisterBody): Session

    @POST("auth/password/forgot")
    suspend fun forgotPassword(@Body body: ForgotBody): ForgotRes

    @POST("auth/password/reset")
    suspend fun resetPassword(@Body body: ResetBody): Unit

    // Step-up for the broadcast routes: same lockout as login; the returned
    // access token carries pwd_at (and any MFA stamp, carried across).
    @POST("auth/confirm-password")
    suspend fun confirmPassword(@Body body: ConfirmPasswordBody): ConfirmPasswordRes

    @GET("me")
    suspend fun me(): MeResponse

    // PATCH /me — profile self-edit (identity module). Body is a JsonObject so unset
    // fields are OMITTED: the server's strict zod schema rejects explicit nulls
    // (same precedent as reportModuleEngagement). Include row_version (optimistic
    // concurrency) on every call. Wire truth (identity/service.ts updateMe): the
    // response is ONLY {user_id, row_version} — refetch GET /me for the profile.
    @PATCH("me")
    suspend fun updateMe(@Body body: kotlinx.serialization.json.JsonObject): UpdateMeRes

    // --- Pathway (server-authoritative gating §1.9) ---
    @GET("me/pathway")
    suspend fun pathway(): PathwaySummary

    @GET("levels/{n}/modules")
    suspend fun levelModules(@Path("n") levelNumber: Int): Envelope<LevelModule>

    // A level's active encouragements in trail order (after_module_sequence,
    // sort_order) — empty until content is authored (iOS LevelDetailView parity).
    @GET("levels/{n}/encouragements")
    suspend fun levelEncouragements(@Path("n") levelNumber: Int): Envelope<LevelEncouragement>

    @GET("modules/{id}")
    suspend fun module(@Path("id") moduleId: String): ModuleDetail

    @POST("modules/{id}/complete")
    suspend fun completeModule(@Path("id") moduleId: String, @Body body: CompleteBody): CompleteResult

    // Standalone reflection (the "Reflect" gate step; persists even for quiz modules,
    // which complete via the quiz not completeModule). Reuses SaveReflectionBody.
    @POST("modules/{id}/reflection")
    suspend fun submitModuleReflection(@Path("id") moduleId: String, @Body body: SaveReflectionBody): Unit

    // --- Module engagement heartbeat (reading/audio/video seconds + resume page) ---
    @GET("modules/{id}/engagement")
    suspend fun moduleEngagement(@Path("id") moduleId: String): ModuleEngagement

    // Body is a JsonObject so null deltas are OMITTED (the server's zod schema
    // rejects an explicit null for its optional numbers).
    @POST("modules/{id}/engagement")
    suspend fun reportModuleEngagement(@Path("id") moduleId: String, @Body body: kotlinx.serialization.json.JsonObject): ModuleEngagement

    @GET("modules/{id}/quiz")
    suspend fun quiz(@Path("id") moduleId: String): AssembledQuiz

    @POST("modules/{id}/quiz/attempts")
    suspend fun submitQuiz(@Path("id") moduleId: String, @Body body: SubmitBody): QuizResult

    @GET("levels/{n}/exam")
    suspend fun levelExam(@Path("n") levelNumber: Int): AssembledExam

    @POST("levels/{n}/exam/attempts")
    suspend fun submitLevelExam(@Path("n") levelNumber: Int, @Body body: SubmitBody): ExamResult

    // This level's mastery out of 100: exam (50) + module quizzes (30) + participation (20).
    @GET("me/levels/{n}/score")
    suspend fun levelScore(@Path("n") levelNumber: Int): LevelScore

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
    suspend fun completeSegment(@Path("id") segmentId: String): SegmentCompleteResult

    @GET("growth/plans/{id}/days/{n}/reflection")
    suspend fun dayReflection(@Path("id") planId: String, @Path("n") dayNumber: Int): PlanDayReflectionEnv

    @POST("growth/plans/{id}/days/{n}/reflection")
    suspend fun saveDayReflection(@Path("id") planId: String, @Path("n") dayNumber: Int, @Body body: SaveReflectionBody): PlanDayReflection

    // --- Talk it Over (shared plan-day conversation) ---
    @GET("growth/plans/{id}/days/{n}/talk")
    suspend fun talkList(@Path("id") planId: String, @Path("n") dayNumber: Int): Envelope<TalkPost>

    @POST("growth/plans/{id}/days/{n}/talk")
    suspend fun talkPost(@Path("id") planId: String, @Path("n") dayNumber: Int, @Body body: TalkPostBody): TalkPost

    @POST("growth/talk/{id}/like")
    suspend fun talkLike(@Path("id") postId: String): TalkLikeRes

    @POST("growth/plans/{id}/days/{n}/talk/assist")
    suspend fun talkAssist(@Path("id") planId: String, @Path("n") dayNumber: Int, @Body body: TalkAssistBody): TalkAssistRes

    // --- Prayer journal (private, §5.4) ---
    @GET("me/prayers")
    suspend fun prayers(): Envelope<PrayerEntry>

    @PUT("me/prayers")
    suspend fun upsertPrayer(@Body body: PrayerUpsertBody): Unit

    @DELETE("me/prayers/{id}")
    suspend fun deletePrayer(@Path("id") entryId: String): Unit

    // --- Selah — My Thoughts (private, §5.4 — no leader/admin read path exists) ---
    @GET("me/thoughts")
    suspend fun thoughts(): Envelope<Thought>

    @GET("me/thoughts/{id}")
    suspend fun thought(@Path("id") thoughtId: String): Thought

    @PUT("me/thoughts")
    suspend fun upsertThought(@Body body: ThoughtUpsertBody): Unit

    @DELETE("me/thoughts/{id}")
    suspend fun deleteThought(@Path("id") thoughtId: String): Unit

    // --- AI Prayer Points (consent-gated, §1.1 — words only, never gates/scores) ---
    @POST("me/prayer/assist")
    suspend fun prayerAssist(@Body body: PrayerAssistBody): PrayerAssistRes

    @POST("me/prayer/points")
    suspend fun prayerPoints(): PrayerPointsRes

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

    // Same endpoint, voice shape (attachment_url + attachment_meta) — see
    // SendVoiceBody in CommunityDtos.kt for why this is a separate method.
    @POST("chat/conversations/{id}/messages")
    suspend fun sendChatVoice(@Path("id") conversationId: String, @Body body: SendVoiceBody): Unit

    @POST("chat/conversations/{id}/read")
    suspend fun markChatRead(@Path("id") conversationId: String): Unit

    // PUT/DELETE chat/conversations/{id}/mute — per-member mute (Chat Redesign
    // C4). Wired from the pastoral ⋮ menu's Mute/Unmute; `muted` then rides
    // back on chatInbox()/chatConversation() rows.
    @PUT("chat/conversations/{id}/mute")
    suspend fun muteChatConversation(@Path("id") conversationId: String, @Body body: MuteConversationBody = MuteConversationBody()): Unit

    @DELETE("chat/conversations/{id}/mute")
    suspend fun unmuteChatConversation(@Path("id") conversationId: String): Unit

    @GET("chat/people")
    suspend fun chatPeople(@retrofit2.http.Query("q") query: String? = null): PeopleRes

    @POST("chat/dms")
    suspend fun createDm(@Body body: DmBody): DmRes

    // --- Chat connections (Chat Redesign C1/C2 backend, C3a client) ---
    // "No unsolicited DMs": createDm above now 403s CONSENT_REQUIRED for a
    // brand-new thread between two ordinary members unless one of these
    // requests was accepted first. Existing threads are unaffected.
    @POST("chat/connections/requests")
    suspend fun requestConnection(@Body body: RequestConnectionBody): RequestConnectionRes

    @GET("chat/connections/requests")
    suspend fun listConnectionRequests(@Query("direction") direction: String): ConnectionRequestsRes

    @POST("chat/connections/requests/{id}/accept")
    suspend fun acceptConnectionRequest(@Path("id") requestId: String): ConnectionRequestDecision

    @POST("chat/connections/requests/{id}/decline")
    suspend fun declineConnectionRequest(@Path("id") requestId: String): ConnectionRequestDecision

    @DELETE("chat/connections/requests/{id}")
    suspend fun cancelConnectionRequest(@Path("id") requestId: String): ConnectionRequestDecision

    @GET("chat/connections")
    suspend fun listConnections(): ConnectionsRes

    @POST("chat/connections/{user_id}/remove")
    suspend fun removeConnection(@Path("user_id") userId: String): ConnectionActionRes

    @POST("chat/connections/{user_id}/block")
    suspend fun blockConnection(@Path("user_id") userId: String): ConnectionActionRes

    @POST("chat/connections/{user_id}/unblock")
    suspend fun unblockConnection(@Path("user_id") userId: String): ConnectionActionRes

    // --- Reading & Social R1 — "Read with a Friend" (spec §3/§6) ---
    // Wire shapes: packages/backend/src/modules/reading-social/{groups,invites}.ts.
    // The public https://pathway.nuruplace.org/join/{token} landing page is
    // server-rendered (publicPage.ts) — the app never fetches it; it only
    // mints the token and hands the URL to the system share sheet.

    @POST("reading/groups")
    suspend fun createOrGetReadingGroup(@Body body: CreateReadingGroupBody): ReadingGroupRow

    @GET("reading/groups")
    suspend fun myReadingGroups(): ReadingGroupsRes

    @GET("reading/groups/{id}")
    suspend fun readingGroup(@Path("id") groupId: String): ReadingGroupRow

    @POST("reading/groups/{id}/archive")
    suspend fun archiveReadingGroup(@Path("id") groupId: String): Unit

    @POST("reading/groups/{id}/leave")
    suspend fun leaveReadingGroup(@Path("id") groupId: String): Unit

    @POST("reading/groups/{id}/invites")
    suspend fun createReadingInvite(@Path("id") groupId: String, @Body body: CreateReadingInviteBody): ReadingInviteRow

    @GET("reading/groups/{id}/invites")
    suspend fun listReadingInvites(@Path("id") groupId: String): ReadingInvitesRes

    @POST("reading/groups/{id}/invites/{invite_id}/revoke")
    suspend fun revokeReadingInvite(@Path("id") groupId: String, @Path("invite_id") inviteId: String): Unit

    @GET("reading/invites/{token}")
    suspend fun readingInvitePreview(@Path("token") token: String): ReadingInvitePreview

    @POST("reading/invites/{token}/accept")
    suspend fun acceptReadingInvite(@Path("token") token: String): ReadingInviteAcceptResult

    @POST("reading/invites/{token}/decline")
    suspend fun declineReadingInvite(@Path("token") token: String): Unit

    // Staff-only (Instructor+ — Students get 403): one message delivered to every
    // active member as an individual DM from the sender. Idempotent on
    // client_mutation_id; returns how many members it reached.
    @POST("chat/broadcast")
    suspend fun broadcast(@Body body: BroadcastBody): BroadcastRes

    // Same step-up gate as the send (§5.3) — a fresh pwd_at is required to read
    // what was sent, not only to send it.
    @GET("chat/broadcasts")
    suspend fun broadcasts(@Query("limit") limit: Int = 4): BroadcastListRes

    @GET("chat/broadcasts/{id}")
    suspend fun broadcastDetail(@Path("id") broadcastId: String): BroadcastDetailRes

    @POST("chat/spaces/{id}/join")
    suspend fun joinChatSpace(@Path("id") conversationId: String): Unit

    // Review-gated join (Chat Redesign C1/C2, already live) — the "My Space"
    // discover flow now goes through this instead of the immediate join above,
    // so a space that requires leader review gets a pending state rather than
    // silent immediate membership.
    @POST("chat/spaces/{id}/join-requests")
    suspend fun requestJoinSpace(@Path("id") conversationId: String, @Body body: RequestJoinSpaceBody): JoinSpaceRequestRes

    // --- My Discipler / Talk with My Pastor (Chat Redesign C3b) ---
    @GET("chat/discipler/conversation")
    suspend fun disciplerConversation(): DisclerConversationRes

    @POST("chat/pastoral")
    suspend fun openPastoralThread(): PastoralOpenRes

    // Pastor/SuperAdmin-facing — password step-up gated (§5.3), same posture
    // as the broadcast routes below.
    @GET("chat/pastoral/inbox")
    suspend fun pastoralInbox(): PastoralInboxRes

    // Side-effect-free "have I ever been assigned as a pastor" probe (Chat
    // Redesign C4). No step-up — lets the Chat tab show the Pastoral Inbox
    // segment to an assigned non-SuperAdmin pastor.
    @GET("chat/pastoral/eligibility")
    suspend fun pastoralEligibility(): PastoralEligibilityRes

    @POST("chat/messages/{id}/reactions")
    suspend fun toggleChatReaction(@Path("id") messageId: String, @Body body: ReactBody): ReactOn

    // Author-only (server 404s otherwise). PATCH sets is_edited = true; DELETE
    // soft-deletes — the message stops coming back on the next chatConversation().
    @PATCH("chat/messages/{id}")
    suspend fun editChatMessage(@Path("id") messageId: String, @Body body: EditMessageBody): EditMessageRes

    @DELETE("chat/messages/{id}")
    suspend fun deleteChatMessage(@Path("id") messageId: String): DeleteMessageRes

    // --- Events / calendar ---
    @GET("calendar")
    suspend fun calendar(@retrofit2.http.Query("from") from: String, @retrofit2.http.Query("to") to: String): Envelope<CalendarOccurrence>

    // Up to 5 soonest curated occurrences for Home — server-capped, sorted; the
    // client must render exactly what arrives (no re-sort/re-cap).
    @GET("home/events")
    suspend fun homeEvents(): Envelope<HomeEventRow>

    @GET("events/{id}")
    suspend fun event(@Path("id") eventId: String): EventDetail

    @POST("events/{id}/rsvp")
    suspend fun rsvp(@Path("id") eventId: String, @Body body: RsvpBody): Unit

    @GET("me/rsvps")
    suspend fun myRsvps(): Envelope<MyRsvp>

    // --- Notification center ---
    @GET("me/notifications")
    suspend fun notifications(): NotificationsRes

    @POST("me/notifications/read")
    suspend fun markNotificationsRead(@Body body: MarkReadBody): Unit

    // Screen-view dwell telemetry batch — best-effort, silent (iOS ScreenTracker parity).
    @POST("me/activity/screens")
    suspend fun screenActivity(@Body body: ScreenActivityBody): Unit

    // --- Giving (online-only, §5.6 — money is never queued) ---
    @GET("giving/history")
    suspend fun givingHistory(): Envelope<GivingRecord>

    @POST("giving/intents")
    suspend fun giving(@Body body: GiveBody): GivingIntentResult

    // Settle an approved PayPal order (order_id = the intent's provider_ref).
    @POST("giving/paypal/capture")
    suspend fun capturePayPal(@Body body: PayPalCaptureBody): PayPalCaptureRes

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

    // Nuru's warm daily-greeting line (cached per day server-side).
    @GET("me/home/greeting")
    suspend fun homeGreeting(): DailyGreeting

    // Community reactions on today's shared verse — one per member per day.
    @GET("me/home/verse/reactions")
    suspend fun verseReactions(): VerseReactions

    @POST("me/home/verse/reactions")
    suspend fun reactToVerse(@Body body: VerseReactionBody): VerseReactions

    // The one admin-featured event for the mobile Home (portal toggle; may be null).
    @GET("home/featured-event")
    suspend fun featuredEvent(): FeaturedEventEnv

    // The student's Discipleship Hub — one read-aggregation call (§1.9 pure read).
    @GET("me/discipleship")
    suspend fun discipleship(): DiscipleshipEnv

    // Discipler-facing (Instructor+): roster + one student's dossier. The server
    // enforces the role and disciple-set scope (403 FORBIDDEN_SCOPE outside it);
    // the client only hides the entry point for students.
    @GET("disciples")
    suspend fun disciples(): RosterRes

    @GET("disciples/{id}")
    suspend fun disciple(@Path("id") studentId: String): DossierEnv

    // Copy a private journal prayer onto the wall. Idempotent (re-share returns
    // the existing post). NEVER queued offline — it creates member-visible
    // content, and the server 404s an entry it hasn't synced yet.
    @POST("me/prayers/{id}/share-to-wall")
    suspend fun sharePrayerToWall(@Path("id") entryId: String): ShareToWallRes

    // Certificate PDF — /certificates emits a RELATIVE download_url brokered by
    // the media module; it needs the authed session, so it must be fetched
    // through this client (a browser ACTION_VIEW would 401).
    @retrofit2.http.Streaming
    @GET("media/certificates/{code}")
    suspend fun certificatePdf(@Path("code") code: String): okhttp3.ResponseBody

    // Giving statement / single-gift receipt as PDFs (financial/index.ts:71,88).
    // Fetched through the authed client (never a ?token= browser URL — that
    // would leak the JWT into browser history).
    @retrofit2.http.Streaming
    @GET("giving/statement.pdf")
    suspend fun givingStatementPdf(): okhttp3.ResponseBody

    @retrofit2.http.Streaming
    @GET("giving/transactions/{id}/receipt.pdf")
    suspend fun givingReceiptPdf(@Path("id") txId: String): okhttp3.ResponseBody

    @GET("me/achievements")
    suspend fun achievements(): Achievements

    // Full badge catalogue (gamification module) — merged with /me/achievements
    // so the profile rail can show locked badges too, as iOS does.
    @GET("badges")
    suspend fun badgesCatalogue(): Envelope<Badge>

    @GET("certificates")
    suspend fun certificates(): Envelope<Certificate>

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

    // Home's own prayer-wall preview (distinct from the community/prayer-wall
    // feed's general `sort` query) — iOS HomeView.prayerWallHome parity.
    @GET("home/prayer-wall")
    suspend fun prayerWallHome(): Envelope<PrayerWallPost>

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

    // --- Account: avatar (multipart, field "file", images ≤5 MB) + password ---
    @retrofit2.http.Multipart
    @POST("me/avatar")
    suspend fun uploadAvatar(@retrofit2.http.Part file: okhttp3.MultipartBody.Part): AvatarResult

    // Voice notes (multipart, field "file", AAC .m4a ≤5 MB) → { url } — shared
    // by chat voice messages and prayer-wall voice prayers.
    @retrofit2.http.Multipart
    @POST("me/media/audio")
    suspend fun uploadVoiceNote(@retrofit2.http.Part file: okhttp3.MultipartBody.Part): VoiceUploadRes

    // Post images (multipart, field "file", images ≤5 MB) → { url } — a one-off
    // attachment for composers (event wall "Hype the room" image_url).
    @retrofit2.http.Multipart
    @POST("me/media/image")
    suspend fun uploadPostImage(@retrofit2.http.Part file: okhttp3.MultipartBody.Part): VoiceUploadRes

    // Sunday Letters + AI consent (intelligence layer)
    @GET("me/letters/latest")
    suspend fun latestLetter(): LatestLetterRes

    @GET("me/letters")
    suspend fun letters(): Envelope<PastoralLetter>

    @POST("me/letters/{id}/read")
    suspend fun markLetterRead(@Path("id") letterId: String): LetterReadRes

    @GET("me/ai")
    suspend fun aiConsent(): AiConsentRes

    @POST("me/ai/consent")
    suspend fun setAiConsent(@Body body: AiConsentBody): AiConsentRes

    // Living curriculum (intelligence Phase 3) — the lesson re-rendered
    // (simple/swahili/story, server-cached per module+style) and "Review with
    // Nuru" composed from the member's latest FAILED quiz attempt.
    @GET("modules/{id}/explain")
    suspend fun explainLesson(@Path("id") moduleId: String, @Query("style") style: String): LessonExplanation

    @POST("modules/{id}/quiz/remediation")
    suspend fun quizRemediation(@Path("id") moduleId: String): QuizRemediationRes

    // Wave 3 — footprints on the trail + Your Walk (all counted, never guessed).
    @GET("modules/{id}/footprints")
    suspend fun moduleFootprints(@Path("id") moduleId: String): FootprintsRes

    @GET("me/walk")
    suspend fun myWalk(): WalkRes

    // Wave 2 — a discipler's voice on the lesson + studying-together presence.
    @POST("modules/{id}/voice-note")
    suspend fun setModuleVoiceNote(@Path("id") moduleId: String, @Body body: VoiceNoteBody): VoiceNoteRes

    @DELETE("modules/{id}/voice-note")
    suspend fun deleteModuleVoiceNote(@Path("id") moduleId: String): kotlinx.serialization.json.JsonObject

    @GET("community/presence")
    suspend fun communityPresence(): CommunityPresence

    // The liturgy Home + community intelligence (Phase 4)
    @GET("home/liturgy")
    suspend fun homeLiturgy(): HomeLiturgy

    // Phase 2 — admin-only, the pastor's own recorded liturgy per band.
    // Backend requireRole("Admin") — Admin or SuperAdmin, narrower than the
    // Instructor+ gate used for module discipler voice notes above. This ONE
    // request both uploads the bytes AND attaches them to `band` (unlike the
    // two-step me/media/audio -> modules/{id}/voice-note pattern) — an
    // upsert; calling it again for the same band replaces the recording.
    @retrofit2.http.Multipart
    @POST("admin/liturgy/recordings/{band}")
    suspend fun uploadLiturgyRecording(
        @Path("band") band: String,
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("duration_sec") durationSec: okhttp3.RequestBody,
    ): LiturgyRecordingUploadRes

    // ALWAYS 7 rows, clock order (sunrise..midnight) — a band with nothing
    // recorded still gets a row, with null audioUrl/durationSec/recordedAt.
    @GET("admin/liturgy/recordings")
    suspend fun liturgyRecordings(): Envelope<LiturgyRecordingStatus>

    @DELETE("admin/liturgy/recordings/{band}")
    suspend fun deleteLiturgyRecording(@Path("band") band: String): DeleteLiturgyRecordingRes

    @GET("home/echo")
    suspend fun homeEcho(): HomeEchoEnvelope

    @GET("community/moments")
    suspend fun communityMoments(): Envelope<CommunityMoment>

    @POST("community/moments/{id}/bless")
    suspend fun blessMoment(@Path("id") momentId: String, @Body body: BlessBody): BlessRes

    @POST("me/password")
    suspend fun changePassword(@Body body: ChangePasswordBody): Unit

    @POST("events/{id}/attendance")
    suspend fun checkInEvent(@Path("id") eventId: String, @Body body: CheckInBody): EventCheckInResult

    // --- Approximate location sharing (opt-in) ---
    @POST("me/location")
    suspend fun shareLocation(@Body body: LocationBody): Unit

    @DELETE("me/location")
    suspend fun stopSharingLocation(): Unit

    // --- Device registration (FCM push token) ---
    @POST("me/devices")
    suspend fun registerDevice(@Body body: DeviceBody): Unit

    // --- Radio (member player) ---
    @GET("radio/now-playing")
    suspend fun radioNowPlaying(): RadioProgram?

    @GET("radio/programs")
    suspend fun radioPrograms(): List<RadioProgram>

    @GET("radio/programs/{id}")
    suspend fun radioProgram(@Path("id") programId: String): RadioProgram

    // kind ∈ heart | amen | fire; idempotent per client_event_id → same counts on replay.
    @POST("radio/programs/{id}/react")
    suspend fun radioReact(@Path("id") programId: String, @Body body: RadioReactBody): RadioReactRes

    // Bare array on the wire (not enveloped).
    @GET("radio/programs/{id}/comments")
    suspend fun radioComments(@Path("id") programId: String): List<RadioComment>

    @POST("radio/programs/{id}/comments")
    suspend fun addRadioComment(@Path("id") programId: String, @Body body: RadioCommentBody): RadioComment

    // Live-listener presence heartbeat — the player pings this ~every 20s while it
    // is actually playing a live program, so the studio roster shows real names.
    @POST("radio/programs/{id}/listening")
    suspend fun radioListening(@Path("id") programId: String): retrofit2.Response<Unit>

    // --- Nuru Live — viewer surfaces (L2). ---
    @GET("live/now")
    suspend fun getLiveNow(): Envelope<LiveNowRow>

    // Empty body; server just bumps the stream's last-seen-viewer clock.
    @POST("live/streams/{id}/heartbeat")
    suspend fun postLiveHeartbeat(@Path("id") streamId: String): Unit

    @GET("live/recordings")
    suspend fun getLiveRecordings(
        @Query("scope") scope: String? = null,
        @Query("cell_id") cellId: String? = null,
    ): Envelope<LiveRecordingRow>

    // "My Broadcasts" (Live hub taste pass) — recordings the caller started,
    // regardless of scope. Same row shape as GET /live/recordings; recording_id
    // == stream_id (see deleteLiveRecording below).
    @GET("live/recordings/mine")
    suspend fun getMyLiveRecordings(): Envelope<LiveRecordingRow>

    // Broadcaster-only server-side. Idempotent — a repeat delete of an
    // already-gone recording is a no-op, never surfaced as an error to the UI.
    @DELETE("live/recordings/{id}")
    suspend fun deleteLiveRecording(@Path("id") recordingId: String): Unit

    // --- Nuru Live — broadcaster routes (L3). RBAC-gated server-side
    // (live:go — 403 FORBIDDEN_SCOPE if missing); the client only mirrors the
    // gate to hide the UI (permissions.contains("live:go")), never trusts it.
    // 409 CONFLICT means another stream is already running for that scope.
    @POST("live/streams")
    suspend fun postLiveStreams(@Body body: CreateLiveStreamBody): CreatedLiveStream

    // Idempotent — allowed for the starter or a live:manage holder.
    @POST("live/streams/{id}/end")
    suspend fun postLiveStreamEnd(@Path("id") streamId: String): EndedLiveStream

    // --- Nuru Live — L5 interactions (docs/LIVE_INTERACTIVE.md) ---
    // Append-only, server rate-limited to >=1s/user (any emoji) — 204 on
    // success, RATE_LIMITED (429-shaped ApiError) if the caller is too fast.
    @POST("live/streams/{id}/reactions")
    suspend fun postLiveReaction(@Path("id") streamId: String, @Body body: LiveReactionBody): Unit

    // One hand state per (stream, user); idempotent upsert.
    @POST("live/streams/{id}/hand")
    suspend fun postLiveHand(@Path("id") streamId: String, @Body body: LiveHandBody): Unit

    @GET("live/streams/{id}/messages")
    suspend fun getLiveMessages(@Path("id") streamId: String, @Query("since") since: String? = null): LiveMessagesRes

    @POST("live/streams/{id}/messages")
    suspend fun postLiveMessage(@Path("id") streamId: String, @Body body: LiveSendMessageBody): LiveMessageRow

    // One poll for the whole overlay — viewer_count, reactions, recent
    // reactions (ambient particles), raised hands, active guest invites.
    @GET("live/streams/{id}/pulse")
    suspend fun getLivePulse(@Path("id") streamId: String): LivePulse

    // Broadcaster-only server-side (403 FORBIDDEN_SCOPE otherwise); cap 6 active.
    @POST("live/streams/{id}/guests/{userId}")
    suspend fun postLiveGuestInvite(@Path("id") streamId: String, @Path("userId") userId: String): Unit

    // Invitee only — accept/decline a pending invite.
    @POST("live/streams/{id}/guests/respond")
    suspend fun postLiveGuestRespond(@Path("id") streamId: String, @Body body: LiveGuestRespondBody): Unit

    // Broadcaster (remove) or the guest themselves (leave) — idempotent.
    @DELETE("live/streams/{id}/guests/{userId}")
    suspend fun deleteLiveGuest(@Path("id") streamId: String, @Path("userId") userId: String): Unit

    // L6b (docs/LIVE_INTERACTIVE.md) — accepted-guest-only server-side; mints
    // a fresh WHIP publish credential for MY OWN guest slot on this stream.
    @GET("live/streams/{id}/guests/me/ingest")
    suspend fun getLiveGuestIngest(@Path("id") streamId: String): LiveGuestIngest

    // --- Offline sync: ordered mutation replay (§1.7, §3.6) ---
    @POST("sync/push")
    suspend fun syncPush(@Body body: SyncPushBody): SyncPushResult

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
