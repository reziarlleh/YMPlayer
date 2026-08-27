# YMPlayer / YaPlay Agent Notes

- At the start of non-trivial work in this repository, query agentmemory for
  `YaPlay`, `YMPlayer`, `Yandex Music`, `My Wave`, `SideBar`, and
  `com.ts18.sidebar` before changing behavior.
- Use `github.com/reziarlleh/YMPlayer` as the canonical Agent Memory project
  identifier for project-scoped memories and actions.
- Use the local RepoWise index before broad code exploration: start with
  `repowise search`, `repowise context`, or the RepoWise MCP tools, then read
  the identified source files before editing them.
- Keep RepoWise synchronized with `repowise update` when the working tree has
  relevant changes. The repository post-commit hook provides the normal
  automatic update path; use `repowise doctor` when index health is unclear.
- Treat `D:\_codex\SideBar` and agentmemory SideBar observations as the source
  of truth for TS18 sidebar integration details.
- SideBar package: `com.ts18.sidebar`.
- SideBar persistence model: `SYSTEM_ALERT_WINDOW` plus foreground
  `SideBarOverlayService`, not AccessibilityService.
- YMPlayer owns the additional SideBar keep-alive while its playback service is
  alive. The periodic path should be quiet: explicit
  `android.intent.action.USER_PRESENT` to `SideBarHealthReceiver` plus
  `com.ts18.sidebar.action.CONFIG_CHANGED` without show/collapse extras. Use
  `com.ts18.sidebar.action.RESTART_FROM_WIDGET` only for the manual restart
  button, not for periodic pings.
- TS18 volume/mute controls should use native NWD broadcasts when a TS18
  environment is detected:
  `com.nwd.action.ACTION_KEY_VALUE` with only `extra_key_value`, or
  `com.nwd.can.action.ACTION_PLATFORM_SEND_CAN_VOLUME` when
  `can_use_amp_volume_key == 1`.
- Keep APK output names informative with version and build code.
- Preserve Android 10+ compatibility: `minSdk 29` is a hard requirement.
  Any API added after 29 must be protected with an SDK-version guard or have a
  safe Android 10 fallback.
- Keep permanent offline storage limited to global Yandex Music liked tracks.
  Non-liked playback may use only the limited temporary playback cache.
