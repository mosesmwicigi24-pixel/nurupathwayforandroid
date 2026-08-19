#!/usr/bin/env bash
# Write a placeholder app/google-services.json so the build can run without secrets.
#
# WHY THIS EXISTS
# ---------------
# The Google Services Gradle plugin is applied unconditionally in
# app/build.gradle.kts, and it FAILS THE BUILD if app/google-services.json is
# missing. That file is git-ignored (.gitignore line 15), so a fresh checkout —
# which is exactly what CI does — cannot compile a single line without one.
#
# The values below are structurally valid and semantically meaningless. Nothing
# in a compile or a JVM unit test dials Firebase; the plugin only parses this
# file and turns it into string resources. A real config is needed to *run*
# against Firebase (FCM, Crashlytics, Auth), which is why this script refuses to
# clobber a real file if one is already present — run it locally and your own
# google-services.json survives.
#
# Consequence worth knowing: release builds are deliberately NOT covered by CI.
# They upload native symbols to Crashlytics, which needs credentials this stub
# cannot fake. CI covers debug compile + unit tests; releases stay a local task.
set -euo pipefail

DEST="${1:-app/google-services.json}"

if [ -s "$DEST" ]; then
  echo "note: $DEST already exists — leaving it alone."
  exit 0
fi

# Must match applicationId in app/build.gradle.kts. The plugin searches the
# client array for this exact string and fails with "No matching client found
# for package name" if it is absent — so a rename there means a rename here.
PACKAGE_NAME="com.nuruplace"

mkdir -p "$(dirname "$DEST")"
cat > "$DEST" <<JSON
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "nuru-ci-placeholder",
    "storage_bucket": "nuru-ci-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "${PACKAGE_NAME}"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "AIzaSyCIplaceholderCIplaceholderCIplaceh"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
JSON

echo "Wrote placeholder $DEST for package ${PACKAGE_NAME}."
