#!/bin/bash

export ZANIK_API_TOKEN="your_api_token_here"
export ZANIK_CLAIM_CHANNEL="123456789012345678"
export ZANIK_RESTART_FILE="/tmp/zanik-restart"

EXE_NAME="ZanikNet"
UPDATED_BIN_PATH="./updated-bin/$EXE_NAME"
BIN_PATH="./$EXE_NAME"
SHOULD_START=true

start() {
    if [ -f "$UPDATED_BIN_PATH" ]; then
        mv "$UPDATED_BIN_PATH" "$BIN_PATH"
    fi

    chmod +x "$BIN_PATH"
    "$BIN_PATH"
}

while $SHOULD_START
do
    SHOULD_START=false
    start
    if [ -f "$ZANIK_RESTART_FILE" ]; then
        rm "$ZANIK_RESTART_FILE"
        SHOULD_START=true
    fi
done
