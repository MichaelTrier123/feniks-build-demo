# OpenCode, Feniks and external tools

## Repository configuration

Checked against official OpenCode documentation on 2026-09-06. OpenCode 1.14.41 was found on the Mac. No Feniks/provider/MCP configuration was found in this checkout, the inspected standard user configuration directory, or the standard macOS managed configuration directory. Relevant config override environment variables were unset. This inspection did not inspect credentials or authenticate to any service.

`AGENTS.md` supplies project rules, `opencode.json` adds README.md to instructions, and `.opencode/commands/verify.md` provides `/verify`. These files contain no model, provider, MCP endpoint, token, or permission override. OpenCode loads project instructions from AGENTS.md; JSON configuration can reference additional instruction files. See [rules](https://opencode.ai/docs/rules/) and [configuration](https://opencode.ai/docs/config/).

Configuration layers are merged. Project configuration can override conflicting ordinary user/organization defaults; managed settings take precedence. Preserve existing corporate settings and review the effective configuration locally before adding anything. Do not run `/init` over the supplied instructions without reviewing its changes. The Markdown command format is documented in [custom commands](https://opencode.ai/docs/commands/). `/verify` is a prompt for the agent, not a standalone test runner; the Wrapper remains authoritative.

## Corporate Feniks and Toolkit/MCP prerequisites

Obtain the approved setup from the company owner: supported OpenCode/Feniks versions, installation/package or plugin source, provider/model selection, corporate config policy, and any certificate/proxy/VPN requirements. Stock OpenCode configuration is not evidence of Feniks compatibility.

For Toolkit specifically, obtain the approved MCP server name, transport, exact URL (remote) or executable plus arguments (local), authentication method, required access scopes, and tool schemas for reading a customer case/work package and updating verification results. Also obtain the actual customer-case/work-package IDs and writable evidence/status fields. None are known from the available Mac configuration.

Follow the organization's supplied configuration. OpenCode documents remote MCP with `type: remote` and `url`, or local MCP with `type: local` and `command`; OAuth and environment-based credentials depend on that server. Do not guess an address or embed secrets in Git. After valid configuration is installed, use `opencode mcp list`; for an OAuth server use `opencode mcp auth` with its actual configured name only when the organization requires login. Confirm package retrieval and an approved write operation before declaring integration readiness. See [MCP setup and authentication](https://opencode.ai/docs/mcp-servers/).

No active MCP configuration is committed because the corporate values are unavailable. No external connection or write has been verified on Mac.

## GitHub PR tooling

GitHub CLI (`gh`) is the proposed terminal PR tool; no extra custom integration is needed if the corporate policy permits it. On the target machine, obtain repository access, configure the correct host/account and Git identity, and verify `gh auth status` plus repository permissions. A cloneable repository and feature-branch push/PR permissions must exist first. If the company requires a GitHub MCP tool instead, obtain its approved server and tool configuration from the owner; none has been inferred.

After implementation and local verification, inspect `git diff`, commit the intended files, and publish the feature branch only when authorized. Then `gh pr create --base <actual-base> --head <actual-branch> --title <title> --body-file <reviewed-description-file>` creates the PR. Substitute real values; keep literal shell placeholders out of active configuration. Inspect the returned PR with `gh pr view` and record its URL and verified commit. `--body-file` preserves multiline descriptions. See the [official PR command](https://cli.github.com/manual/gh_pr_create).

No repository, remote, login, push or PR was created during this preparation.

## Windows checklist

1. Obtain the intended source checkout and approved corporate OpenCode/Feniks installation. Use one environment consistently (native Windows/PowerShell or corporate WSL); do not share a live H2 file between them.
2. Install/select JDK 21, check `java -version`, and confirm `.\gradlew.bat --version` uses Java 21 and Gradle 8.14.5. Ensure Git and approved `gh` are available.
3. Run `.\gradlew.bat test build --no-daemon`. Test execution on Windows is still outstanding, including path/locking behavior. The Unix symlink-specific test is intentionally OS-restricted.
4. With the app stopped, run `demoReset`, `flywayMigrate`, `flywayInfo`, then `bootRun` with the Windows Wrapper. Execute the README's create → fetch → cancel → fetch requests. Stop the app, reset again, and check fixture ID 1 and next new ID 201.
5. Preserve corporate settings, add only approved Feniks/provider/MCP details, launch OpenCode in the root, and confirm project rules and `/verify` are loaded. Execute `/verify` only after Java is selected in its inherited shell.
6. Complete the authorized GitHub and Toolkit access checks above. Never report a PR or package update as successful without inspecting its actual result.

Mac build/runtime verification does not verify Windows execution, the company Feniks distribution, authentication, Toolkit tools, or GitHub permissions.
