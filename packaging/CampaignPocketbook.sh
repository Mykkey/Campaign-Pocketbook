#!/bin/sh
# Starts Campaign Pocketbook. Needs Java 21 or newer (https://adoptium.net/).
cd "$(dirname "$0")" || exit 1
if ! command -v java >/dev/null 2>&1; then
    echo "Java was not found. Campaign Pocketbook needs Java 21 or newer."
    echo "Download it from https://adoptium.net/ , install it, and run this file again."
    exit 1
fi
exec java --module-path javafx --add-modules javafx.controls,javafx.swing -cp "CampaignPocketbook.jar:lib/*" dnd.App
