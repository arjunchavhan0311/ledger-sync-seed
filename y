[1mdiff --git a/.github/CODEOWNERS b/.github/CODEOWNERS[m
[1mnew file mode 100644[m
[1mindex 0000000..d50ee0f[m
[1m--- /dev/null[m
[1m+++ b/.github/CODEOWNERS[m
[36m@@ -0,0 +1,2 @@[m
[32m+[m[32m# Every path requires review from the hiring team. Nothing merges from outside.[m
[32m+[m[32m*       @simplify-money/hiring[m
[1mdiff --git a/.github/pull_request_template.md b/.github/pull_request_template.md[m
[1mnew file mode 100644[m
[1mindex 0000000..fb9e23f[m
[1m--- /dev/null[m
[1m+++ b/.github/pull_request_template.md[m
[36m@@ -0,0 +1,15 @@[m
[32m+[m[32m## This repository does not accept pull requests[m
[32m+[m
[32m+[m[32mThis is assignment scaffolding. Pull requests opened here are closed[m
[32m+[m[32mautomatically and are **not** part of any submission — we will not see it as[m
[32m+[m[32myour work.[m
[32m+[m
[32m+[m[32m**Submit by email**, as the assignment says:[m
[32m+[m
[32m+[m[32m> `talent.acquisition@simplifymoney.in`[m
[32m+[m[32m> subject: `Simplify Money | Software Engg Intern - BE | <Your Name>`[m
[32m+[m
[32m+[m[32mWork in **your own fork**, not here.[m
[32m+[m
[32m+[m[32mFound a genuine problem with the scaffolding — a broken fixture, a typo in the[m
[32m+[m[32mbrief? Email us. We would rather fix it for everyone than have you guess.[m
[1mdiff --git a/.github/workflows/ci.yml b/.github/workflows/ci.yml[m
[1mnew file mode 100644[m
[1mindex 0000000..7468dd3[m
[1m--- /dev/null[m
[1m+++ b/.github/workflows/ci.yml[m
[36m@@ -0,0 +1,18 @@[m
[32m+[m[32mname: ci[m
[32m+[m
[32m+[m[32mon:[m
[32m+[m[32m  push:[m
[32m+[m[32m    branches: [main][m
[32m+[m[32m  workflow_dispatch:[m
[32m+[m
[32m+[m[32mjobs:[m
[32m+[m[32m  build:[m
[32m+[m[32m    runs-on: ubuntu-latest[m
[32m+[m[32m    steps:[m
[32m+[m[32m      - uses: actions/checkout@v4[m
[32m+[m[32m      - uses: actions/setup-java@v4[m
[32m+[m[32m        with:[m
[32m+[m[32m          distribution: temurin[m
[32m+[m[32m          java-version: '21'[m
[32m+[m[32m      - name: compile and self-check[m
[32m+[m[32m        run: ./verify.sh[m
[1mdiff --git a/.github/workflows/close-external-prs.yml b/.github/workflows/close-external-prs.yml[m
[1mnew file mode 100644[m
[1mindex 0000000..0f65d02[m
[1m--- /dev/null[m
[1m+++ b/.github/workflows/close-external-prs.yml[m
[36m@@ -0,0 +1,37 @@[m
[32m+[m[32mname: close external PRs[m
[32m+[m
[32m+[m[32mon:[m
[32m+[m[32m  pull_request_target:[m
[32m+[m[32m    types: [opened, reopened][m
[32m+[m
[32m+[m[32mpermissions:[m
[32m+[m[32m  pull-requests: write[m
[32m+[m
[32m+[m[32mjobs:[m
[32m+[m[32m  close:[m
[32m+[m[32m    runs-on: ubuntu-latest[m
[32m+[m[32m    steps:[m
[32m+[m[32m      - uses: actions/github-script@v7[m
[32m+[m[32m        with:[m
[32m+[m[32m          script: |[m
[32m+[m[32m            const pr = context.payload.pull_request;[m
[32m+[m[32m            await github.rest.issues.createComment({[m
[32m+[m[32m              owner: context.repo.owner,[m
[32m+[m[32m              repo: context.repo.repo,[m
[32m+[m[32m              issue_number: pr.number,[m
[32m+[m[32m              body: [[m
[32m+[m[32m                "Thanks for the interest, but this repository does not accept pull requests.",[m
[32m+[m[32m                "",[m
[32m+[m[32m                "It is assignment scaffolding. Work in your own fork and submit by email to",[m
[32m+[m[32m                "`talent.acquisition@simplifymoney.in` as the assignment describes — a PR here",[m
[32m+[m[32m                "is not seen as part of your submission.",[m
[32m+[m[32m                "",[m
[32m+[m[32m                "If you have found a genuine problem with the scaffolding, email us instead."[m
[32m+[m[32m              ].join("\n")[m
[32m+[m[32m            });[m
[32m+[m[32m            await github.rest.pulls.update({[m
[32m+[m[32m              owner: context.repo.owner,[m
[32m+[m[32m              repo: context.repo.repo,[m
[32m+[m[32m              pull_number: pr.number,[m
[32m+[m[32m              state: "closed"[m
[32m+[m[32m            });[m
[1mdiff --git a/.gitignore b/.gitignore[m
[1mnew file mode 100644[m
[1mindex 0000000..eda5654[m
[1m--- /dev/null[m
[1m+++ b/.gitignore[m
[36m@@ -0,0 +1,18 @@[m
[32m+[m[32mbuild/[m
[32m+[m[32m.gradle/[m
[32m+[m[32mout/[m
[32m+[m[32m*.class[m
[32m+[m[32mdata/*.mv.db[m
[32m+[m[32mdata/*.trace.db[m
[32m+[m[32msubmission/[m
[32m+[m[32m.idea/[m
[32m+[m[32m*.iml[m
[32m+[m[32m.DS_Store[m
[32m+[m[32m# Java / JVM crash logs[m
[32m+[m[32mhs_err_pid*.log[m
[32m+[m[32mreplay_pid*.log[m
[32m+[m
[32m+[m[32m# Build output[m
[32m+[m[32mbuild/[m
[32m+[m[32mbin/[m
[32m+[m[32m.gradle/[m
\ No newline at end of file[m
[1mdiff --git a/.vscode/launch.json b/.vscode/launch.json[m
[1mnew file mode 100644[m
[1mindex 0000000..2ba986f[m
[1m--- /dev/null[m
[1m+++ b/.vscode/launch.json[m
[36m@@ -0,0 +1,15 @@[m
[32m+[m[32m{[m
[32m+[m[32m    // Use IntelliSense to learn about possible attributes.[m
[32m+[m[32m    // Hover to view descriptions of existing attributes.[m
[32m+[m[32m    // For more information, visit: https://go.microsoft.com/fwlink/?linkid=830387[m
[32m+[m[32m    "version": "0.2.0",[m
[32m+[m[32m    "configurations": [[m
[32m+[m[32m        {[m
[32m+[m[32m            "type": "chrome",[m
[32m+[m[32m            "request": "launch",[m
[32m+[m[32m            "name": "Launch Chrome against localhost",[m
[32m+[m[32m            "url": "http://localhost:8080",[m
[32m+[m[32m            "webRoot": "${workspaceFolder}"[m
[32m+[m[32m        }[m
[32m+[m[32m    ][m
[32m+[m[32m}[m
\ No newline at end of file[m
[1mdiff --git a/LICENSE b/LICENSE[m
[1mnew file mode 100644[m
[1mindex 0000000..94004b0[m
[1m--- /dev/null[m
[1m+++ b/LICENSE[m
[36m@@ -0,0 +1,10 @@[m
[32m+[m[32mCopyright (c) 2026 Simplify Money Private Limited[m
[32m+[m
[32m+[m[32mThis repository is published solely so that candidates for the Software[m
[32m+[m[32mEngineering Intern (Backend) role at Simplify Money can complete a take-home[m
[32m+[m[32massignment. You may fork it and modify your fork for that purpose.[m
[32m+[m
[32m+[m[32mAll other rights are reserved. The fixtures and assignment materials may not be[m
[32m+[m[32mredistributed, republished, or used to train or evaluate any model or service.[m
[32m+[m
[32m+[m[32mTHE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.[m
[1mdiff --git a/README.md b/README.md[m
[1mnew file mode 100644[m
[1mindex 0000000..7b71a57[m
[1m--- /dev/null[m
[1m+++ b/README.md[m
[36m@@ -0,0 +1,197 @@[m
[32m+[m[32m# ledger-sync[m
[32m+[m
[32m+[m[32mScaffolding for the Simplify Money **Software Engineering Intern (Backend, Java)** take-home.[m
[32m+[m
[32m+[m[32mRead this file completely before you write any code. Then read[m
[32m+[m[32m`fixtures/corpus-a.jsonl` — not all 500 lines, but enough of them that you stop[m
[32m+[m[32mbeing surprised.[m
[32m+[m
[32m+[m[32m> **Do not open a pull request here.** Work in your own fork and submit by email.[m
[32m+[m[32m> PRs opened against this repository are closed automatically and are not seen[m
[32m+[m[32m> as part of your submission.[m
[32m+[m
[32m+[m[32m---[m
[32m+[m
[32m+[m[32m## What this service is for[m
[32m+[m
[32m+[m[32mSimplify Money tells a user where their money went. To do that, something has to[m
[32m+[m[32mread the bank SMS and bank emails sitting on their phone and turn them into a[m
[32m+[m[32mledger the user can trust.[m
[32m+[m
[32m+[m[32mThis repository is that something, half-finished, with a live incident open[m
[32m+[m[32magainst it.[m
[32m+[m
[32m+[m[32m---[m
[32m+[m
[32m+[m[32m## What you are being asked to do, exactly[m
[32m+[m
[32m+[m[32m**Input:** `fixtures/corpus-a.jsonl` — one JSON object per line, each