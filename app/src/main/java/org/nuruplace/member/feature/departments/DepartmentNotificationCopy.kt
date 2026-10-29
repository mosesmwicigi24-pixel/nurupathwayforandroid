// The words on a department's notification in the notification centre — the
// server's push copy (workers/dispatch.ts PUSH_TEMPLATE_COPY,
// docs/PARTNERS_PROGRAMME.md §4), so the tray, the centre and iOS say the same
// thing. Before this the centre showed the template's name, with no line, for
// every serve_request_* and department_post notice (audit, 2026-09-28). Kept
// pure so DepartmentNotificationCopyTest pins it against dispatch.ts.
package org.nuruplace.member.feature.departments

import org.nuruplace.member.data.net.NotifPayload

/** dispatch.ts `str`: a non-empty string exactly as sent (not trimmed), else null. */
private fun String?.said(): String? = this?.takeIf { it.isNotEmpty() }

/** The headline for serve_request_* and department_post; null for any other
 *  template (the centre's own fallback applies — department_need_* are a
 *  giving target and are not worded here). The caller checks the payload's
 *  own `title` first, as dispatch.ts pushCopy() does; these payloads carry
 *  none. */
internal fun departmentNotificationTitle(template: String, p: NotifPayload?): String? {
    val department = p?.department.said()
    return when (template) {
        // To the department's LEADER; `name` is the member who asked.
        "serve_request_received" -> "${p?.name.said() ?: "Someone"} wants to serve in ${department ?: "your department"}"
        "serve_request_approved" -> "Welcome to ${department ?: "the department"}"
        "serve_request_declined" -> "About ${department ?: "the department"}"
        "department_post" -> department ?: "Your department"
        else -> null
    }
}

/** What serve_request_* and department_post say; null for any other template. */
internal fun departmentNotificationBody(template: String, p: NotifPayload?): String? = when (template) {
    "serve_request_received" -> "Open the portal to welcome them in."
    "serve_request_approved" -> "Your request to serve was approved. Open Departments to see what's next."
    "serve_request_declined" -> "The leader couldn't take you on right now. Other departments would love your hands — open Departments."
    "department_post" -> p?.preview.said() ?: "A new post from your department."
    else -> null
}
