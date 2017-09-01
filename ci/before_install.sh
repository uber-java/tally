#!/usr/bin/env bash
openssl aes-256-cbc -K $encrypted_258d8124f158_key -iv $encrypted_258d8124f158_iv -in ci/signing.gpg.enc -out ci/signing.gpg -d
gpg --fast-import ci/signing.gpg
