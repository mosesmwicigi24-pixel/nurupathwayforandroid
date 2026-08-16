package org.nuruplace.member.feature.community

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** isPasswordRequired must tell the broadcast routes' §5.3 step-up 403 apart
 *  from an ordinary refusal — only the former should raise the step-up sheet
 *  instead of a plain error. */
class BroadcastStepUpTest {
    private fun httpError(code: Int, body: String): HttpException =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test fun `403 with password_required body needs step-up`() {
        assertTrue(isPasswordRequired(httpError(403, """{"error":{"code":"password_required","message":"Confirm your password"}}""")))
    }

    @Test fun `403 without password_required is an ordinary refusal`() {
        assertFalse(isPasswordRequired(httpError(403, """{"error":{"code":"FORBIDDEN_SCOPE","message":"Not allowed"}}""")))
    }

    @Test fun `401 is never treated as a step-up prompt`() {
        assertFalse(isPasswordRequired(httpError(401, """{"error":{"code":"password_required"}}""")))
    }

    @Test fun `non-http exceptions are never a step-up prompt`() {
        assertFalse(isPasswordRequired(RuntimeException("offline")))
    }
}
