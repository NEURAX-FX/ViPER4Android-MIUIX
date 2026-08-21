# Daemon-Owned Effect Owner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move ViPER's load-bearing session-0 effect ownership and playback-session observation out of the App into an init-supervised ART owner process, while preserving the App-owned backend as an explicit fallback.

**Architecture:** `viper-daemon` remains the native root control plane and continues to apply snapshots directly to the driver over `@viper4android.driver.v1`. A new small ART process, `viper-owner`, is spawned and supervised by the daemon; it owns the session-0 `AudioEffect` handle and reports playback-session deltas over `@viper4android.owner.v1`. The App remains the editable-state authority and uses the daemon first, falling back to its frozen `ViperEffect` path only when the daemon is unavailable or `DriverOnly` is selected.

**Tech Stack:** C++20 daemon and host CTest, Android NDK/CMake, Android Java framework APIs launched by `app_process64`, D8, KernelSU initrc/module scripts, Kotlin `LocalSocket`, existing snapshot protocol and `DaemonBackend`.

**Spec:** `docs/superpowers/specs/2026-08-20-daemon-owned-effect-and-session-tracking-design.md`

## Global Constraints

- Keep `ViperContext::Process()` free of socket operations, file I/O, locks, allocations, snapshot parsing, and graph preparation.
- Do not link the daemon to HIDL/AIDL generated code or hidden AudioFlinger Binder clients.
- Do not edit MiuiX library source.
- Keep the App's direct `AudioEffect` backend available as the no-daemon fallback.
- The owner must hold session 0 only in the first implementation; playback-session observations are diagnostic and policy input, not extra effect creation.
- Use the existing 36-byte frame header, CRC32, bounded payload rules, and abstract `SOCK_SEQPACKET` conventions.
- Accept only uid 0 on the owner socket; reject malformed, unknown, oversized, and arbitrary-effect requests.
- Do not enable owner spawning by default until host tests and device verification pass.
- Do not reboot or alter live audio configuration until host tests and the owner smoke test pass.
- Every permanent behavior change requires a focused observable-contract test before implementation.

## File Map

### Native protocol and owner link

- Create `protocol/OwnerProtocol.h` and `protocol/OwnerProtocol.cpp`: owner message ids, bounded payload codecs, effect-type selector validation, and owner wire payloads.
- Create `tests/OwnerProtocolTest.cpp`: round-trip, CRC/framing integration, selector rejection, malformed/bounded payload rejection.
- Modify `CMakeLists.txt`: register protocol sources and `owner_protocol_test`.

### Native owner supervision

- Create `daemon/OwnerServer.h` and `daemon/OwnerServer.cpp`: abstract owner socket listener, uid-0 peer check, frame decode, owner handshake/state events, and daemon-to-owner commands.
- Create `daemon/OwnerSupervisor.h` and `daemon/OwnerSupervisor.cpp`: spawn command construction, pid tracking, bounded restart backoff, owner adoption, and state transitions.
- Create `tests/OwnerSupervisorTest.cpp`: deterministic process-adapter tests for spawn, timeout, bounded restart, adoption, and no restart storm.
- Modify `daemon/DaemonRuntime.h/.cpp`: own the server/supervisor, request session-0 ownership, process owner events, expose owner diagnostics, and write owner state fields.
- Modify `daemon/SessionRegistry.h/.cpp`: retain context-generation restore logic and add bounded playback-session entries/deltas without changing driver context ownership semantics.
- Modify `tests/SessionRegistryTest.cpp` and `tests/DaemonRuntimeTest.cpp`: owner/session and restore regressions.
- Modify `CMakeLists.txt`: register owner daemon sources and tests.

### ART owner and packaging

- Create `owner/src/com/llsl/viper4android/owner/OwnerMain.java`: ART entry point, owner protocol client, command loop, and clean shutdown.
- Create `owner/src/com/llsl/viper4android/owner/EffectOwner.java`: reflected four-argument `AudioEffect` constructor, session-0 creation, enable, release, and bounded failure reporting.
- Create `owner/src/com/llsl/viper4android/owner/SessionObserver.java`: system `Context`, `MODIFY_AUDIO_ROUTING` check, `AudioPlaybackCallback`, session/client-uid delta generation.
- Create `owner/src/com/llsl/viper4android/owner/OwnerWire.java`: byte-compatible owner frame/payload codec; reuse the same constants and little-endian layout as native protocol.
- Create `owner/build-owner.sh`: compile against the device/API `android.jar`, run D8 with the configured minimum API, and produce `owner/classes.dex` without Gradle or App dependencies.
- Create `tests/owner/owner_wire_smoke.sh`: compile/package validation and protocol smoke test on a host fixture.
- Modify `Makefile`: build/copy owner dex for each module package; fail packaging when the owner dex is missing or stale.
- Modify `module/common/install.sh`: copy owner dex beside the daemon binary with root-readable permissions.
- Modify `module/initrc/viper-daemon.rc`: pass the literal owner dex path and owner socket/config flags to the daemon.
- Modify `module/uninstall.sh`: remove owner dex and owner state artifacts while preserving snapshots.
- Modify module packaging tests: assert owner dex, owner socket name, and daemon command-line wiring.

### App fallback integration

- Modify `app/src/main/java/com/llsl/viper4android/service/ViperService.kt`: start fallback session monitoring only when daemon ownership is not active; release App-owned handles when daemon accepts state; recreate fallback handles only after daemon degradation; keep `DriverOnly` behavior unchanged.
- Modify `app/src/main/java/com/llsl/viper4android/daemon/DaemonBackend.kt` only where owner status must distinguish daemon snapshot acceptance from owner readiness.
- Modify `app/src/main/java/com/llsl/viper4android/daemon/DaemonStatusReader.kt`: parse optional owner state fields without rejecting older daemon state files.
- Modify `app/src/main/java/com/llsl/viper4android/ui/screens/status/DriverStatusDialog.kt` and strings: show owner state as diagnostics, without changing existing fallback wording.
- Add focused Kotlin tests for owner-state parsing and fallback/daemon mutual exclusion.

### Documentation and acceptance

- Modify `docs/superpowers/plans/2026-08-18-viper-root-audio-daemon.md`: mark the context-restore and rescan defects complete, then add owner tasks with their verification commands.
- Keep the approved design spec unchanged except for factual test evidence discovered during implementation.

---

### Task 1: Lock the owner wire contract

**Files:**
- Create: `protocol/OwnerProtocol.h`, `protocol/OwnerProtocol.cpp`
- Create: `tests/OwnerProtocolTest.cpp`
- Modify: `CMakeLists.txt`

**Interfaces:**

```cpp
namespace viper::owner {
constexpr uint16_t kOwnerProtocolVersion = 1;
constexpr const char *kOwnerSocketName = "viper4android.owner.v1";
constexpr uint32_t kMaxOwnerPayloadBytes = 4096;

enum class OwnerMessage : uint16_t {
    OWNER_HELLO = 300,
    OWNER_HELLO_ACK = 301,
    OWN_SESSION = 302,
    OWNED = 303,
    OWN_FAILED = 304,
    RELEASE_SESSION = 305,
    RELEASED = 306,
    SESSION_DELTA = 307,
};

enum class EffectTypeSelector : uint16_t {
    HIDL = 1,
    AIDL = 2,
};

struct OwnerHello { uint64_t owner_pid; uint64_t boot_id; };
struct OwnerHelloAck { bool accepted; uint64_t daemon_generation; };
struct OwnSession { uint32_t audio_session_id; EffectTypeSelector selector; };
struct Owned { uint32_t audio_session_id; uint32_t effect_id; bool has_control; };
struct OwnerFailed { uint32_t audio_session_id; uint32_t reason_code; };
struct ReleaseSession { uint32_t audio_session_id; };
struct Released { uint32_t audio_session_id; };
struct SessionDelta { uint32_t audio_session_id; uint32_t client_uid; bool appeared; };

bool EncodeOwnerPayload(OwnerMessage, std::span<const uint8_t> fields,
                        std::vector<uint8_t> *, std::string *error);
bool DecodeOwnerPayload(OwnerMessage, std::span<const uint8_t>,
                        std::vector<uint8_t> *, std::string *error);
bool IsAllowedEffectSelector(uint16_t) noexcept;
}
```

- [x] Write tests for every message's little-endian field layout and round trip.
- [x] Write tests rejecting arbitrary selector `0`, selector `3`, oversized payload, truncated fields, and trailing bytes.
- [x] Add the target to CMake and run it to confirm the new test fails because the codec is absent.
- [x] Implement only the fixed-size codecs and selector validation.
- [x] Run `cmake --build /root/tmp/viperfx-clang -j1 --target owner_protocol_test` and execute the binary.
- [x] Keep the existing frame codec as the outer transport; do not duplicate CRC implementation.

### Task 2: Add deterministic native owner server

**Files:**
- Create: `daemon/OwnerServer.h`, `daemon/OwnerServer.cpp`
- Create: `tests/OwnerServerTest.cpp`
- Modify: `CMakeLists.txt`

**Interfaces:**

```cpp
class OwnerServer final {
public:
    explicit OwnerServer(std::string socket_name = kOwnerSocketName);
    bool Listen(std::string *error);
    void Close() noexcept;
    void Poll(OwnerDelegate *delegate, std::size_t max_frames = 32);
    bool Connected() const noexcept;
    bool Send(OwnerMessage, std::span<const uint8_t>, uint64_t request_id);
    const OwnerServerStats &Statistics() const noexcept;
};

class OwnerDelegate {
public:
    virtual ~OwnerDelegate() = default;
    virtual void OnOwnerHello(const OwnerHello &) = 0;
    virtual void OnOwnered(const Owned &) = 0;
    virtual void OnOwnerFailed(const OwnerFailed &) = 0;
    virtual void OnReleased(const Released &) = 0;
    virtual void OnSessionDelta(const SessionDelta &) = 0;
};
```

- [x] Test abstract socket bind, uid-0 acceptance, non-root rejection using the existing peer-credential test seam.
- [x] Test hello/owned/session-delta decode and server command encode.
- [x] Test malformed frame handling never stalls later valid frames.
- [x] Run the focused test and observe expected failure before implementation.
- [x] Implement the nonblocking single-client server by copying the `DriverEventServer` control-thread pattern, not its snapshot logic.
- [x] Run `owner_server_test` and the existing `driver_daemon_bridge_test`.

### Task 3: Implement owner supervisor state machine

**Files:**
- Create: `daemon/OwnerSupervisor.h`, `daemon/OwnerSupervisor.cpp`
- Create: `tests/OwnerSupervisorTest.cpp`
- Modify: `daemon/DaemonRuntime.h/.cpp`, `CMakeLists.txt`

**Interfaces:**

```cpp
enum class OwnerState { ABSENT, STARTING, OWNED, FAILED };
struct OwnerStatus {
    OwnerState state = OwnerState::ABSENT;
    int64_t pid = 0;
    uint32_t effect_id = 0;
    uint64_t restarts = 0;
    uint64_t tracked_sessions = 0;
};

class OwnerProcessAdapter {
public:
    virtual ~OwnerProcessAdapter() = default;
    virtual int Spawn(std::string *error) = 0;
    virtual bool IsAlive(int pid) const = 0;
    virtual void Kill(int pid) = 0;
};

class OwnerSupervisor final {
public:
    OwnerSupervisor(std::unique_ptr<OwnerProcessAdapter>, std::string dex_path);
    void Poll(bool should_own_session_zero, std::chrono::steady_clock::time_point now);
    void OnConnected();
    void OnOwned(const Owned &);
    void OnFailed(const OwnerFailed &);
    OwnerStatus Status() const noexcept;
    bool Ready() const noexcept;
};
```

- [x] Write tests for absent owner spawning once, owner timeout, one bounded restart, give-up state, and adoption of a connected owner after daemon restart.
- [x] Write a test proving repeated `Poll()` calls while `STARTING` do not spawn repeatedly.
- [x] Run the test before implementation and verify the failures describe missing state behavior.
- [x] Implement a process adapter that executes `/system/bin/app_process64 <dex-dir> OwnerMain` with `CLASSPATH=<dex>` and no shell interpolation.
- [x] Use a monotonic timeout for hello/owned acknowledgment; never block the daemon loop waiting for owner.
- [x] Add `--owner-dex`, `--owner-socket`, and the disable override as daemon CLI options. The override is spelled `--no-owner`, and ownership is opt-in: it enables only when `--owner-dex` names a readable dex.
- [x] Expose owner status through `DaemonStatus` and write the five state keys from the approved spec.
- [x] Run `owner_supervisor_test` and `daemon_runtime_test`.

### Task 4: Fix owner lifecycle restore and session registry integration

**Files:**
- Modify: `daemon/DaemonRuntime.h/.cpp`
- Modify: `daemon/SessionRegistry.h/.cpp`
- Modify: `tests/SessionRegistryTest.cpp`, `tests/DaemonRuntimeTest.cpp`, `tests/RouteRestoreIntegrationTest.cpp`

**Interfaces:**

```cpp
uint64_t SessionRegistry::ContextGeneration() const noexcept;
uint64_t SessionRegistry::NewestCreatedSequence() const noexcept;
```

- [x] Add daemon-local context generation and restore on a new driver connection/context.
- [x] Require route announce ACK before restore, preserving the old-driver bounded retry behavior.
- [x] Keep `restore_enabled=false` as a hard gate.
- [x] Add rescan terminator handling and driver terminator emission; stale phantom contexts are removed only after the terminator.
- [x] Add tests for owner reconnect before `CONTEXT_RELEASED` arrives and ensure one restore occurs after the new connection, not on every poll.
  - `TestReconnectBeforeReleaseRestoresExactlyOnce` in `tests/RouteRestoreIntegrationTest.cpp`. Red-green verified: removing the `restored_context_generation_` write in `DaemonRuntime::RunOnce()` fails it.
- [x] Run `session_registry_test`, `driver_event_publisher_test`, `daemon_runtime_test`, and `route_restore_integration_test` with the exact executable paths from the active CMake generator.
- [x] Update the plan status only after the full focused CTest selection passes.

### Task 5: Build the ART owner with TDD at the protocol boundary

**Files:**
- Create: `owner/src/com/llsl/viper4android/owner/OwnerWire.java`
- Create: `owner/src/com/llsl/viper4android/owner/EffectOwner.java`
- Create: `owner/src/com/llsl/viper4android/owner/SessionObserver.java`
- Create: `owner/src/com/llsl/viper4android/owner/OwnerMain.java`
- Create: `owner/build-owner.sh`
- Create: `tests/owner/owner_wire_smoke.sh`

**Interfaces:**

```java
final class EffectOwner {
    EffectOwner(int audioSessionId, EffectTypeSelector selector);
    boolean createAndEnable();
    int effectId();
    boolean hasControl();
    void release();
}

final class SessionObserver {
    void start(Consumer<SessionDelta> sink);
    void stop();
}
```

- [x] Add a host-side Java test or shell fixture that checks exact bytes for hello, owned, failure, release, and session delta before production owner code.
  - `tests/owner/owner_wire_smoke.sh` diffs `tests/OwnerWireVectors.cpp` (native codec) against `tests/owner/OwnerWireVectors.java` (production `OwnerWire`) over all 10 framed messages. Red-green verified: perturbing one effect id in the Java emitter fails the script with exit 1.
- [x] Make the fixture fail because the owner sources/build script are absent.
- [x] Implement `OwnerWire` with little-endian fixed fields and the existing 36-byte outer frame layout; reject unknown selectors and payload lengths.
- [x] Implement `EffectOwner` with the same UUID constants as `ViperEffect`, using the reflected four-argument constructor and `setEnabled(true)`.
- [x] Implement `SessionObserver` using `ActivityThread.systemMain()`, system `Context`, `MODIFY_AUDIO_ROUTING`, `AudioManager.registerAudioPlaybackCallback`, and `getActivePlaybackConfigurations` only for initial state.
- [x] Do not create per-playback effects; report session id/client uid deltas only.
- [x] Implement `OwnerMain` as a blocking owner socket loop with bounded read sizes, hello first, own-session command handling, and release on SIGTERM.
  - Deviation from the original wording, forced by device evidence: socket EOF must **not** release the handle. init restarts the daemon on every update and crash, and releasing on EOF silenced audio each time (`verify_daemon_death.sh` reproduced it). The owner now keeps the effect and reconnects within a bounded window; release happens only on explicit `RELEASE_SESSION` or process exit.
- [x] Implement `build-owner.sh` using the device/API `android.jar`, `javac --release 17`, D8, and a deterministic output directory. The script must accept `ANDROID_JAR`, `D8_JAR`, and `OUT_DIR` arguments or environment values.
- [x] Run `sh tests/owner/owner_wire_smoke.sh` and inspect the produced dex with `unzip -l`/D8 output.

### Task 6: Wire owner supervisor into the module and daemon runtime

**Files:**
- Modify: `daemon/main.cpp`, `daemon/DaemonRuntime.cpp`, `daemon/DaemonRuntime.h`
- Modify: `module/initrc/viper-daemon.rc`, `module/daemon-start.sh`, `module/common/install.sh`, `module/uninstall.sh`, `Makefile`
- Create/modify: `tests/module/OwnerPackagingTest.sh`, `tests/module/owner_start_smoke.sh`

- [x] Add owner server polling to `DaemonRuntime::RunOnce()` before restore arbitration.
- [x] On owner connection, send hello ACK, then `OWN_SESSION{session=0, selector}` exactly once per owner connection.
- [x] On `OWNED`, retain owner status; do not apply DSP parameters through the owner socket.
- [x] On owner failure or disconnect, clear owner status, retain driver/snapshot state, and let the supervisor restart with bounded backoff.
- [x] At startup, adopt an owner that outlived the previous daemon instead of spawning a duplicate.
  - `DaemonRuntime::ReadPublishedOwnerPid()` reads `owner_pid` from the predecessor's `daemon.state` before it is overwritten, and `OwnerSupervisor::SeedSurvivingOwner()` waits for that pid within the existing handshake window. A stale pid is rejected by an `IsAlive` check, and an adopted owner is never killed. Without this the replacement daemon created a second owner and a duplicate AudioFlinger module.
- [x] Add owner state fields to `daemon.state` and preserve parsing compatibility for older App versions.
- [x] Add `common/bin/viper-owner.dex` to the module package and install it with mode `0644`, owner root.
- [x] Pass literal module paths from init to daemon; never use `$MODDIR` in initrc.
- [x] Keep daemon startup functional when the owner dex is absent: log degraded owner state and preserve App fallback.
- [x] Add shell tests that fail if the package omits dex, owner socket, or CLI wiring.
- [x] Run all module shell tests plus `cmake --build /root/tmp/viperfx-clang -j1 --target viper-daemon`.

### Task 7: Make App and daemon ownership mutually exclusive

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/service/ViperService.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/daemon/DaemonBackend.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/daemon/DaemonStatusReader.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/status/DriverStatusDialog.kt`
- Modify: `app/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-zh-rCN/strings.xml`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/OwnerStatusTest.kt`

- [x] Write tests for these observable transitions: daemon owner ready causes no App global effect creation; daemon unavailable starts the existing fallback; daemon owner loss releases no longer-needed daemon-mode App handles and starts fallback; `DriverOnly` never probes or starts owner-related code.
- [x] Run the tests before implementation and confirm they fail on the current `applyStateWithDaemon`/`applyState` behavior.
- [x] Add an explicit ownership decision derived from owner readiness and snapshot acceptance; do not infer readiness from socket connectivity alone.
  - Implemented as `EffectOwnership.decide()` returning a `Decision`, rather than a `DaemonOwnershipState` enum: the caller needs two independent outputs (own the effect, track sessions) plus a diagnostic reason, which a single state value cannot carry without the caller re-deriving them.
- [x] In `Auto`, use daemon state first and keep fallback available only after daemon refusal/degradation. Do not create an App effect concurrently with a daemon-owned session-0 effect.
- [x] A live owner keeps the App out even when the daemon is unreachable.
  - Follows from the owner surviving daemon death: `daemonAccepted=false` no longer implies "no handle exists". `decide()` still reads the published state and confirms the claim with a `/proc/<owner_pid>` liveness check, so an init respawn cannot produce a duplicate session-0 module, while a state file that outlived its owner still hands ownership back. Red-green verified by forcing `ownerConfirmed = daemonAccepted`.
- [x] In `DaemonOnly`, do not call `initGlobalEffect` or start `AudioSessionMonitor` after owner readiness; keep status reads through daemon state or a read-only legacy probe only when no owner exists.
- [x] In `DriverOnly`, preserve current App-owned global/per-session behavior exactly. Asserted to perform no owner probe at all.
- [x] Keep `AudioSessionMonitor` as fallback-only and start it only when no daemon backend is active.
- [x] Parse optional owner state keys as absent/default values so an older daemon state file remains valid.
- [x] Add concise status UI strings for owner absent/starting/owned/failed; do not hide an owner failure behind generic "daemon connected".
- [x] Run `./gradlew :app:testDebugUnitTest --tests 'com.llsl.viper4android.daemon.*' --max-workers=1 --no-daemon` on the remote project copy. 131 tests, 0 failures.

### Task 8: Add end-to-end acceptance tooling and device verification

**Files:**
- Create: `tests/owner/verify_owner_device.sh`
- Create: `tests/owner/verify_app_death.sh`
- Create: `tests/owner/verify_daemon_death.sh` (added: daemon death is a distinct failure mode from App death and needed its own acceptance script)
- Create: `tests/owner/verify_streaming_without_app.sh`, `tests/owner/StreamingProbe.java`, `tests/owner/build_streaming_probe.sh` (added: the device ships no `tinyplay`, so the streaming assertion has to generate its own tone)
- Create: `tests/owner/verify_audioserver_restart.sh` (added: an audioserver restart kills every effect client reference, and the resulting failure is silent)
- Create: `tests/module/probe_init_restart.sh` (added: closes the non-reboot half of the init-import check in the 2026-08-18 plan)
- Modify: `docs/superpowers/plans/2026-08-18-viper-root-audio-daemon.md`
- Modify: `docs/superpowers/specs/2026-08-20-daemon-owned-effect-and-session-tracking-design.md` only for measured results

- [x] Build the owner dex and arm64 daemon/module without installing it.
- [x] Run host protocol, supervisor, daemon, registry, route-restore, and existing realtime-audit tests. 62/62 pass (`iem_halo_stft_test` excluded: pre-existing heap corruption in `IEMDSP`, untouched by this work).
- [x] Verify owner socket, daemon state, and owner pid/effect id on device.
  - Done with isolated daemons on unique abstract socket names and their own state roots, **not** by installing the module. The on-device module is v2.0.1 (predating owner support) and its initrc passes no `--owner-dex`; installing is a separate, higher-risk step and is deliberately not part of this verification. Every acceptance script restores whatever it stopped.
- [x] Start a root owner with the same dex and command-line shape in a controlled test; verify `OWNED`, effect id, `hasControl`, and session delta output.
  - `verify_owner_device.sh`: `owner_effect_id=76067 owner_has_control=1`, module count 1 → 2.
- [x] Play a tone and assert driver `PARAM_GET_STREAMING=1` while the App is absent.
  - `verify_streaming_without_app.sh`: `app_pid=none`, `streaming=1`. The probe is a bare `app_process64` process, so the reading cannot be attributed to the App.
- [x] Assert session tracking now comes from the owner, not the App.
  - Same script, while the tone is still playing: the daemon's `tracked_sessions` moves `3 -> 4` for the probe's own `AudioTrack`. This is the only place the migration is observable, because `EffectOwnership` sets `appTracksSessions=false` whenever an owner holds the handle, so the App's `AudioSessionMonitor` is stopped. The probe holds the tone for 6s after confirming streaming; without that hold it exited before the sample and the count never moved.
- [x] Start the real App, confirm daemon owner state is `owned`, and assert `dumpsys media.audio_flinger` has exactly one ViPER session-0 module rather than an App duplicate.
  - `verify_app_death.sh`: module count is unchanged by the App's presence (`with_app modules=2` == owner baseline).
- [x] Kill only the App process; assert owner pid remains alive, effect module count remains unchanged, and streaming remains `1`.
  - `verify_app_death.sh`: `owner_pid=28502 effect=76107 modules=2` before and after, `after_app_kill_streaming=1`. The pid assertion is on the *original* App process, because `ViperService` returns `START_STICKY` and the package may already be back under a new pid.
- [x] Kill the owner; assert bounded respawn and a new effect id.
  - `verify_owner_device.sh`: `27381/76067` → `27488/76075`, `restarts=1`, module count steady at 2.
- [x] Kill the daemon; assert audio continues and the restarted daemon reports the existing owner.
  - `verify_daemon_death.sh`, SIGKILL (an update or crash gives no handover chance): owner `27605` and effect `76083` survive, module count stays 2, and the replacement daemon adopts the same pid with `spawn_failures=0`. This script was written red first: it reproduced the EOF-release defect and the duplicate-spawn defect before either fix.
- [x] Restart `audioserver`; assert the handle is rebuilt rather than silently dead.
  - Not in the original list, and it found a real defect. An `AudioEffect` handle is a
    client reference into audioserver, so all of them die when it does — but the Java
    object survives and keeps reporting its old id and `getEnabled()==true`. Measured
    with a throwaway probe: `id` and `enabled` never change, while `setEnabled()` goes
    `0` → `-7` (`ERROR_DEAD_OBJECT`) and `hasControl()` flips to false. So the owner
    held a corpse, the daemon kept publishing `owner_state=owned` with a stale effect
    id, `EffectOwnership` saw "a handle exists" and kept the App out, and nothing
    processed audio. `verify_audioserver_restart.sh` reproduced it
    (`modules 2 -> 1`) before the fix.
  - Fix: `EffectOwner.isAlive()` probes for `ERROR_DEAD_OBJECT` — the only signal that
    distinguishes a dead binder from merely having lost control — and `recreate()`
    rebuilds with the remembered selector. A watchdog thread in `OwnerMain` polls it
    every second and reports the new effect id as a fresh `OWNED`.
  - The watchdog is a thread, not a socket read timeout. `LocalSocket.setSoTimeout()`
    surfaces as a generic `IOException`, indistinguishable from a dropped link, so the
    first attempt counted roughly one bogus `owner_restarts` per second against a
    perfectly healthy audioserver.
  - After the fix: owner pid `13843` survives, effect `187` → `35`, module count steady
    at 2, `owner_restarts=0`. Red-green verified by stubbing `isAlive()` to `true`.
- [x] Corrupt `current.snapshot`; assert the daemon falls back to `previous.snapshot`.
  - Also not in the original list, and it also found a real defect.
    `DaemonRuntime::RestoreCurrentRoute()` only called `LoadCurrent()`, so a torn or
    truncated write bypassed to driver defaults and the user lost their settings for
    that route — even though `SnapshotStore` keeps `previous.snapshot` for exactly this
    case and is unit-tested for it. The gap was between two correct components.
  - Fix: fall back to `LoadPrevious()`, with the same device-hash re-check applied to
    the fallback so a forged `previous.snapshot` cannot become a second cross-device
    inheritance path.
  - `TestCorruptCurrentSnapshotFallsBackToPrevious` and
    `TestCorruptCurrentDoesNotInheritForeignPrevious` in
    `tests/RouteRestoreIntegrationTest.cpp`. Red-green verified both ways: the first
    fails before the fix, and short-circuiting the fallback re-fails it.
- [x] Close the non-reboot half of the init-import check from the 2026-08-18 plan.
  - `tests/module/probe_init_restart.sh`: SIGKILL the init-owned daemon, then assert a
    new pid under PPID 1, `init.svc.viper_daemon=running`, both listeners rebound, and
    `app_listening=1` in the restarted daemon's state. Listeners are counted by socket
    state, not by name: `/proc/net/unix` also lists live client connections under the
    same abstract name, so a raw name count moves when a peer connects and proves
    nothing about rebinding (it produced a false failure on the first run).
- [x] Fix repeated `EFFECT_CMD_SET_CONFIG` before audio consumes the pending graph.
  - Device evidence: the live A2DP effect instance was configured as 96000 Hz / 4096
    frames / PCM_FLOAT, but `PARAM_GET_CONFIGURE=0`, `PARAM_GET_SAMPLING_RATE=0`, and
    `PARAM_GET_ENABLED=1`. Host tracing reproduced the exact cause: the first rate
    change stages a pending graph; a second SET_CONFIG arrives before `Process()` and
    `DspGraphSlots::PreparePending()` rejects it because a pending graph already exists.
    `HandleSetConfig()` then left `disable_reason_` at UNKNOWN, so the effect remained
    enabled but bypassed.
  - Fix: `ViperContext::HandleSetConfig()` calls `DspGraphSlots::RetractPending()`
    before preparing the newest graph. The active graph continues processing until the
    newest pending graph is consumed, so repeated route/rate notifications no longer
    turn a valid effect into `configure=0`.
  - `ViperContextConfigureTest` covers 48000/PCM16, initial 96000/PCM_FLOAT, a rate
    change, and the repeated-pending sequence. Red-green verified: before the fix the
    final assertion observed `configure=0 rate=48000`; after the fix it passes.
  - Host result after the fix: 63/63 tests pass, excluding the known pre-existing
    `iem_halo_stft_test` heap-corruption test from the full ctest command.
  - A rebuilt zip was installed through KernelSU. The new library is staged in
    `/data/adb/modules_update`, but the running AudioFlinger still maps the old
    `/vendor/lib64/soundfx/libv4a_re.so` inode. The production 96 kHz/streaming
    assertion therefore remains pending a reboot; no reboot was performed.
- [x] Remove all probe artifacts and verify no temporary dex/process/socket remains. Confirmed: `PROBE_DIRS=0 OWNER_PROCS=0 PROBE_PROCS=0 STRAY_SOCKETS=0 MODULES=1`, `init.svc.viper_daemon=running`.
- [x] Run the full applicable CTest and Kotlin test selections, then remote `assembleDebug`. 62/62 native, 131/131 Kotlin, `BUILD SUCCESSFUL`.

## Verification Commands

Native focused tests on the current host filesystem require an executable build directory because `/root/AndroidIDEProjects` is a `noexec` FUSE mount:

```bash
cmake -S . -B /root/tmp/viperfx-clang \
  -DBUILD_ANALYZER_TESTS=ON \
  -DCMAKE_C_COMPILER=clang \
  -DCMAKE_CXX_COMPILER=clang++
cmake --build /root/tmp/viperfx-clang -j1 --target \
  owner_protocol_test owner_server_test owner_supervisor_test \
  session_registry_test driver_event_publisher_test daemon_runtime_test \
  daemon_driver_e2e_test route_restore_integration_test
ctest --test-dir /root/tmp/viperfx-clang -R \
  'owner_protocol_test|owner_server_test|owner_supervisor_test|session_registry_test|driver_event_publisher_test|daemon_runtime_test|daemon_driver_e2e_test|route_restore_integration_test' \
  --output-on-failure
```

Kotlin daemon tests:

```bash
sh gradlew :app:testDebugUnitTest \
  --tests 'com.llsl.viper4android.daemon.*' \
  --max-workers=1 --no-daemon
```

Remote Android build:

```bash
sh gradlew assembleDebug --stacktrace --no-daemon
```

Owner wire contract (host; diffs the ART owner's Java codec against the native one):

```bash
sh tests/owner/owner_wire_smoke.sh
```

Device acceptance, run as root on the device after `make ABI=arm64-v8a module`.
Each script uses its own daemon on unique abstract socket names and restores
anything it stopped; none of them installs the module:

```bash
# once, for the streaming assertion
ANDROID_JAR=... D8_JAR=... bash tests/owner/build_streaming_probe.sh

sh tests/owner/verify_owner_device.sh          # owner takes session 0; bounded respawn
sh tests/owner/verify_daemon_death.sh          # owner survives the daemon; restart adopts it
sh tests/owner/verify_audioserver_restart.sh   # dead handle is rebuilt, not left as a corpse
sh tests/owner/verify_streaming_without_app.sh # PARAM_GET_STREAMING=1 and session deltas, no App
sh tests/owner/verify_app_death.sh             # App death changes nothing; still streaming

sh tests/module/probe_init_restart.sh          # init respawns the daemon and rebinds its sockets
```

`verify_app_death.sh` takes over the production sockets and state root, because the
App reads that state root to decide whether to create a fallback handle; it backs the
directory up and restores it, along with `init.svc.viper_daemon`, on exit.

`verify_audioserver_restart.sh` and `probe_init_restart.sh` both SIGKILL a system
service. Both are respawned by init, but audio is interrupted system-wide for a few
seconds in the first case, so neither is folded into the other scripts.

The App must not be claimed independent of the owner until device evidence shows the
owner process, not the App, retains the AudioFlinger client and the driver continues
streaming after the App process is killed. That evidence is recorded in Task 8:
`owner_pid`/`owner_effect_id` unchanged across App death, module count steady, and
`streaming=1` measured afterwards.
