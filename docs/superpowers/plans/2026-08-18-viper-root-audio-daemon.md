# ViPER Root Audio Daemon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a native root daemon that restores route-specific ViPER state before the App starts, tracks driver sessions without blocking the audio thread, and provides an App synchronization path with legacy fallback.

**Architecture:** The driver publishes bounded observe-only lifecycle events over a non-blocking abstract `SOCK_SEQPACKET` bridge. This bridge is a project-private driver API and is the only daemon-to-driver control path; the daemon does not use HIDL/AIDL or AudioEffect Binder clients. The daemon owns route detection, composite device keys, atomic root-private snapshots, generation arbitration, and App IPC. The App remains the editable-state authority and connects through `ViperService`; actual AudioEffect creation/destruction remains in AudioFlinger and the driver/App legacy backend remains available during migration.

**Tech Stack:** C++17 existing DSP libraries plus a C++20 daemon/protocol target, CMake/CTest, Android NDK, KernelSU module shell scripts, Kotlin `LocalSocket`, coroutines, Room/DataStore, existing `ViperContext`, `ConfigChannel`, and `ViperService`.

## Global Constraints

- Keep `ViperContext::Process()` free of socket operations, file IO, locks, allocations, snapshot parsing, and graph preparation.
- Do not edit MiuiX library source.
- Keep the existing direct App-to-driver backend available until daemon health and protocol checks pass.
- Device snapshots live under `/data/adb/viper4android/`, owned by root, with atomic writes and `current.snapshot`/`previous.snapshot` recovery.
- Driver-to-daemon IPC uses a non-blocking abstract `SOCK_SEQPACKET` socket named `@viper4android.driver.v1`.
- The daemon control plane must not depend on HIDL, AIDL, hidden AudioFlinger Binder APIs, or generated HAL interfaces.
- The existing AudioEffect HAL is retained only as AudioFlinger's lifecycle boundary; `DriverDaemonBridge` is the daemon's private driver API.
- App-to-daemon IPC uses a separate abstract socket named `@viper4android.app.v1`.
- A lost driver event must trigger daemon rescan; event loss must not block or stall audio processing.
- A route change applies the selected valid snapshot before App reconciliation.
- App generation wins when App connects with a newer canonical desired state; stale base generations are rejected.
- Missing or corrupt snapshots must bypass safely and must never reuse another device's snapshot.
- All new protocol payloads must have bounded lengths, CRC validation, schema/version checks, and deterministic error codes.
- Do not install, restart audioserver, or alter live device state until host tests and build verification pass.

---

### Task 1: Add Phase 0 Protocol Primitives

**Repository:** `/root/AndroidIDEProjects/ViPERFX_RE`

**Files:**
- Create: `protocol/ViperDaemonProtocol.h`
- Create: `protocol/ViperDaemonProtocol.cpp`
- Create: `protocol/DeviceKey.h`
- Create: `protocol/DeviceKey.cpp`
- Create: `protocol/SnapshotSchema.h`
- Create: `protocol/SnapshotSchema.cpp`
- Create: `tests/ViperDaemonProtocolTest.cpp`
- Create: `tests/DeviceKeyTest.cpp`
- Create: `tests/SnapshotSchemaTest.cpp`
- Modify: `CMakeLists.txt`

**Interfaces:**
- `viper::daemon::FrameHeader`
- `viper::daemon::FrameError`
- `bool EncodeFrame(const FrameHeader &, std::string_view payload, std::vector<uint8_t> *out, std::string *error)`
- `bool DecodeFrame(std::span<const uint8_t> bytes, FrameHeader *header, std::vector<uint8_t> *payload, std::string *error)`
- `std::string NormalizeDeviceKey(const DeviceIdentity &identity)`
- `std::string HashDeviceKey(std::string_view normalized_key)`
- `bool ValidateSnapshot(const Snapshot &snapshot, std::string *error)`
- `bool EncodeSnapshot(const Snapshot &, std::vector<uint8_t> *out, std::string *error)`
- `bool DecodeSnapshot(std::span<const uint8_t>, Snapshot *, std::string *error)`

- [x] **Step 1: Write failing frame tests**

Test round-trip encoding, little-endian header fields, CRC rejection, bad magic, unsupported version, oversized payload rejection, and trailing-byte rejection.

- [x] **Step 2: Write failing device-key tests**

Test normalization of speaker, wired, USB, and Bluetooth identities, lowercasing, whitespace removal, stable ordering, and rejection of volatile session/process fields.

- [x] **Step 3: Write failing snapshot tests**

Test valid complete snapshots, missing required metadata, resource hash mismatch, invalid generation, unsupported schema, truncated payload, and deterministic round-trip bytes.

- [x] **Step 4: Implement bounded frame codec**

Use a fixed 36-byte header, maximum frame size 1 MiB, CRC32 for payloads, and explicit `FrameError` values. Do not use exceptions in the protocol path.

- [x] **Step 5: Implement device-key normalization**

Canonicalize route type/address/product/format/channel fields and hash the normalized UTF-8 key with the existing SHA-256 helper or a small protocol-local implementation.

- [x] **Step 6: Implement snapshot schema codec**

Use a versioned binary schema with explicit lengths and a canonical field order. Store resource references by ID/hash/size/kind instead of UI filesystem paths.

- [x] **Step 7: Register focused CTest targets**

Add `viper_daemon_protocol_test`, `device_key_test`, and `snapshot_schema_test` under `BUILD_ANALYZER_TESTS=ON`.

- [x] **Step 8: Run the focused tests**

```bash
cmake -S . -B build-host -DBUILD_ANALYZER_TESTS=ON
cmake --build build-host -j1 --target viper_daemon_protocol_test device_key_test snapshot_schema_test
ctest --test-dir build-host -R "viper_daemon_protocol_test|device_key_test|snapshot_schema_test" --output-on-failure
```

Expected: all selected tests PASS.

### Task 2: Add Atomic Root Snapshot Store and Generation Arbitration

**Repository:** `/root/AndroidIDEProjects/ViPERFX_RE`

**Files:**
- Create: `daemon/SnapshotStore.h`
- Create: `daemon/SnapshotStore.cpp`
- Create: `daemon/GenerationArbiter.h`
- Create: `daemon/GenerationArbiter.cpp`
- Create: `tests/SnapshotStoreTest.cpp`
- Create: `tests/GenerationArbiterTest.cpp`
- Modify: `CMakeLists.txt`

**Interfaces:**
- `SnapshotStore(std::filesystem::path root)`
- `bool LoadCurrent(std::string_view device_hash, Snapshot *, std::string *error)`
- `bool LoadPrevious(std::string_view device_hash, Snapshot *, std::string *error)`
- `bool Commit(std::string_view device_hash, const Snapshot &, std::string *error)`
- `ApplyDecision Decide(uint64_t base_daemon_generation, uint64_t app_generation, uint64_t stored_app_generation, uint64_t stored_daemon_generation)`

- [x] **Step 1: Write failing store tests**

Use a temporary directory. Test initial missing state, atomic current write, previous rotation, corrupt current fallback, truncated temporary file cleanup, permissions when supported, and per-device isolation.

- [x] **Step 2: Write failing generation tests**

Test first apply, newer App state acceptance, stale base rejection, daemon route restore, and equal-generation idempotence.

- [x] **Step 3: Implement atomic commit**

Write `current.snapshot.tmp`, flush and close it, rename current to previous, rename temp to current, and preserve the previous valid file when validation fails. Keep all file access on daemon control threads.

- [x] **Step 4: Implement generation decisions**

Return explicit `ACCEPT`, `IDEMPOTENT`, and `STALE_GENERATION` decisions with the reason and resulting generation values.

- [x] **Step 5: Run focused store tests**

```bash
cmake --build build-host -j1 --target snapshot_store_test generation_arbiter_test
ctest --test-dir build-host -R "snapshot_store_test|generation_arbiter_test" --output-on-failure
```

Expected: PASS.

### Task 3: Add Observe-Only Driver Event Bridge

**Repository:** `/root/AndroidIDEProjects/ViPERFX_RE`

**Files:**
- Create: `src/DriverDaemonBridge.h`
- Create: `src/DriverDaemonBridge.cpp`
- Create: `tests/DriverDaemonBridgeTest.cpp`
- Modify: `src/ViPER4Android.cpp`
- Modify: `src/ViperContext.h`
- Modify: `src/ViperContext.cpp`
- Modify: `CMakeLists.txt`

**Interfaces:**
- `DriverDaemonBridge::Start()`
- `DriverDaemonBridge::Stop()`
- `DriverDaemonBridge::Publish(const DriverEvent &)`
- `DriverDaemonBridge::RequestRescan()`
- `DriverDaemonBridge::Connected() const`
- `DriverEvent` fields: `boot_id`, `event_sequence`, `context_instance_id`, `session_id`, `io_id`, `sample_rate`, `channel_mask`, `state`, and bounded telemetry.

The bridge is implemented inside `libv4a_re.so` and communicates with the
separate daemon through the private `@viper4android.driver.v1` protocol. It
must not link to HIDL/AIDL generated code or create an AudioEffect client.

- [ ] **Step 1: Write failing bridge tests**

Use a local test socket endpoint. Verify event serialization, sequence increment, reconnect behavior, bounded queue overflow, and that `Publish` returns quickly when no daemon is listening.

- [ ] **Step 2: Implement control-thread-only socket bridge**

Use a non-blocking abstract `SOCK_SEQPACKET` connection and a bounded queue. The bridge thread owns connect/send/reconnect; callers enqueue a fixed-size event without touching the socket.

- [ ] **Step 3: Emit context lifecycle events**

Add creation, configuration, enable/disable, release, resource-generation, and telemetry publication from existing control/lifecycle points. Do not add any bridge call to `Process()`.

- [ ] **Step 4: Add rescan response plumbing**

Expose a control-thread snapshot of live contexts sufficient for the daemon to rebuild its registry after reconnect or sequence gaps.

- [ ] **Step 5: Run native bridge and realtime audit tests**

```bash
cmake --build build-host -j1 --target driver_daemon_bridge_test iem_realtime_audit_test
ctest --test-dir build-host -R "driver_daemon_bridge_test|iem_realtime_audit_test" --output-on-failure
```

Expected: PASS, with no new realtime-audit violations.

### Task 4: Add Native Daemon Observe-Only Runtime

**Repository:** `/root/AndroidIDEProjects/ViPERFX_RE`

**Files:**
- Create: `daemon/main.cpp`
- Create: `daemon/DaemonRuntime.h`
- Create: `daemon/DaemonRuntime.cpp`
- Create: `daemon/DriverEventServer.h`
- Create: `daemon/DriverEventServer.cpp`
- Create: `daemon/SessionRegistry.h`
- Create: `daemon/SessionRegistry.cpp`
- Create: `daemon/RouteWatcher.h`
- Create: `daemon/RouteWatcher.cpp`
- Create: `tests/SessionRegistryTest.cpp`
- Create: `tests/DaemonRuntimeTest.cpp`
- Modify: `CMakeLists.txt`
- Modify: `Makefile`

**Interfaces:**
- `DaemonRuntime::Run()` / `DaemonRuntime::Stop()`
- `SessionRegistry::Apply(const DriverEvent &)`
- `SessionRegistry::MarkStaleAfter(uint64_t sequence)`
- `SessionRegistry::RescanComplete(uint64_t sequence)`
- `SessionRegistry::Snapshot() const`
- `RouteWatcher::CurrentRoute()` / `RouteWatcher::Poll()`

- [ ] **Step 1: Write failing registry tests**

Cover created/configured/active/released transitions, context identity by boot id plus instance id, sequence gaps marking stale, rescan completion, and removal of omitted contexts after a complete rescan.

- [ ] **Step 2: Implement session registry**

Keep logical registry state independent from AudioFlinger ownership. Never create or release an effect from daemon code.

- [ ] **Step 3: Implement driver event server**

Accept the driver bridge, validate peer credentials where available, decode frames, reject malformed input, and forward valid events to the registry.

- [ ] **Step 4: Implement route watcher adapter boundary**

Provide a deterministic fake adapter for host tests and a device adapter that reads available Android/ALSA/sysfs route information without requiring hidden AudioFlinger Binder APIs.

- [ ] **Step 5: Implement daemon lifecycle and signal handling**

Use foreground execution, `SIGTERM`/`SIGINT` shutdown, bounded reconnect loops, `/data/adb/viper4android/daemon.state`, and diagnostic status output. The daemon must remain observe-only in this task.

- [ ] **Step 6: Add native daemon target**

Build `viper-daemon` as a host executable and as an Android executable with a separate target-specific link configuration. Do not link the daemon into `v4a_re`.

- [ ] **Step 7: Run daemon host tests**

```bash
cmake --build build-host -j1 --target session_registry_test daemon_runtime_test viper-daemon
ctest --test-dir build-host -R "session_registry_test|daemon_runtime_test" --output-on-failure
```

Expected: PASS.

### Task 5: Add Init-Owned Daemon Service and Boot-Completed Trigger

**Repository:** `/root/AndroidIDEProjects/ViPERFX_RE`

**Files:**
- Create: `module/common/bin/viper-daemon`
- Create: `module/common/etc/init/viper-daemon.rc`
- Create: `module/common/boot-completed.sh`
- Modify: `Makefile`
- Modify: `module/uninstall.sh`
- Create: `tests/module/DaemonInitServiceTest.sh`

- [ ] **Step 1: Write failing shell tests**

Test that the package contains `viper-daemon`, an init service definition, and a boot-completed trigger. Verify the init service is not marked `oneshot`, uses `disabled` plus `class late_start`, and the boot script calls `start viper_daemon` instead of implementing a duplicate restart loop.

- [ ] **Step 2: Add module packaging**

Copy the daemon binary under `common/bin/viper-daemon`, install the init fragment through the module overlay, install the boot-completed trigger, and preserve the existing driver files and legacy fallback files.

- [ ] **Step 3: Implement init service**

Define `service viper_daemon` with the native executable, `class late_start`, `user root`, `group root`, and `disabled`. Do not use `oneshot`; init must restart an unexpected daemon exit. Use an explicit `seclabel` only when the target ROM's policy provides a valid domain; otherwise fail installation/activation honestly instead of guessing a label.

- [ ] **Step 4: Implement boot-completed trigger**

Use the module's boot-completed hook only to execute `start viper_daemon` after `sys.boot_completed=1` and to write a small diagnostic marker. It must be idempotent; calling it twice must not create a second process because init owns the service.

- [ ] **Step 5: Implement uninstall cleanup**

Stop the init service, remove its PID/status files, preserve user snapshots unless explicit module removal cleanup is requested, and never modify App Room/DataStore files.

- [ ] **Step 6: Verify init import on the target ROM before enabling by default**

Install a test-only `.rc` and confirm `init.svc.viper_daemon`, `ctl.start`, crash restart, and socket permissions. If KernelSU does not import the module overlay into init's namespace, add the minimal module-specific import mechanism or keep the daemon backend disabled and report the reason.

- [ ] **Step 7: Run shell packaging tests**

```bash
sh tests/module/DaemonInitServiceTest.sh
```

Expected: PASS.

### Task 6: Add Snapshot Application Control Path

**Repository:** `/root/AndroidIDEProjects/ViPERFX_RE`

**Files:**
- Modify: `src/DriverDaemonBridge.h`
- Modify: `src/DriverDaemonBridge.cpp`
- Modify: `src/ViperContext.h`
- Modify: `src/ViperContext.cpp`
- Create: `src/SnapshotApplyController.h`
- Create: `src/SnapshotApplyController.cpp`
- Create: `tests/SnapshotApplyControllerTest.cpp`

**Interfaces:**
- `SnapshotApplyController::Begin(const SnapshotMetadata &)`
- `SnapshotApplyController::Append(std::span<const uint8_t>)`
- `SnapshotApplyController::Commit()`
- `SnapshotApplyController::Abort(ApplyError)`
- `SnapshotApplyController::LastAppliedGeneration() const`

- [ ] **Step 1: Write failing apply tests**

Test complete snapshot staging, out-of-order chunks, CRC/hash mismatch, range rejection, graph preparation failure preserving the old graph, idempotent generation, and successful ACK contents.

- [ ] **Step 2: Implement control-thread staging**

Keep bounded staged resources and parameters outside the realtime path. Validate every chunk before accepting it.

- [ ] **Step 3: Connect commit to existing graph slots/mailboxes**

Prepare the pending graph and resource generation on the control thread, then publish atomically using existing `DspGraphSlots`, `DspResources`, and parameter mailbox mechanisms.

- [ ] **Step 4: Add daemon command ACK/NACK**

Return explicit apply result, generation, resource generation, and failure reason to the daemon.

- [ ] **Step 5: Run native regression tests**

```bash
cmake --build build-host -j1 --target snapshot_apply_controller_test iem_context_test dsp_graph_slots_test
ctest --test-dir build-host -R "snapshot_apply_controller_test|iem_context_test|dsp_graph_slots_test" --output-on-failure
```

Expected: PASS.

### Task 7: Add App Daemon Client and Backend Selection

**Repository:** `/root/AndroidIDEProjects/ViPER4Android`

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonProtocol.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonClient.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonSnapshotMapper.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonBackend.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/service/ViperService.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/viper/ViperEffect.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/DaemonProtocolTest.kt`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/DaemonSnapshotMapperTest.kt`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/DaemonClientStateTest.kt`

**Interfaces:**
- `DaemonClient.connect()` / `disconnect()` / `syncState()` / `observe()`
- `DaemonConnectionState`: `Disconnected`, `Connecting`, `Connected`, `Syncing`, `Ready`, `Degraded`
- `DaemonBackend.applyState(state: EffectState, masterEnabled: Boolean): Result<Unit>`
- `DaemonBackend.readStatus(): DriverTelemetry?`

- [ ] **Step 1: Write failing Kotlin codec tests**

Test frame round-trip, CRC rejection, generation conflict mapping, bounded payload rejection, and daemon error code mapping.

- [ ] **Step 2: Write failing state mapper tests**

Test canonical `EffectState` conversion including all scalar/array parameters, resources by content hash, route identity, and app generation.

- [ ] **Step 3: Implement `LocalSocket` client**

Use a dedicated coroutine dispatcher, request ids, bounded frames, reconnect backoff, and explicit ACK/NACK handling. Never perform socket IO on the main thread.

- [ ] **Step 4: Add daemon backend with legacy fallback**

Use daemon backend only after `DAEMON_HELLO`, protocol, peer, and apply checks pass. On disconnect or NACK, keep the legacy direct `AudioEffect`/`ConfigChannel` backend available and expose degraded status.

- [ ] **Step 5: Integrate with `ViperService`**

On service start, connect and sync local state. On daemon route event, update device status and let daemon-first snapshot application complete before App reconciliation. Add the explicit `DAEMON_SYNC` action and ensure `START_STICKY` reconnects safely.

- [ ] **Step 6: Run Kotlin daemon tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.llsl.viper4android.daemon.*" --max-workers=1 --no-daemon
```

Expected: PASS.

### Task 8: Integrate Route-First Snapshot Restore

**Repositories:** both

**Files:**
- Modify: `daemon/RouteWatcher.cpp`
- Modify: `daemon/DaemonRuntime.cpp`
- Modify: `daemon/SnapshotStore.cpp`
- Modify: `app/src/main/java/com/llsl/viper4android/audio/AudioOutputDetector.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/service/ViperService.kt`
- Create: `tests/RouteRestoreIntegrationTest.cpp`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/RouteKeyMappingTest.kt`

- [ ] **Step 1: Write route restore integration tests**

Test route debounce, per-device snapshot selection, no cross-device inheritance, daemon-first apply, App sync afterward, and missing snapshot safe bypass.

- [ ] **Step 2: Implement route watcher to snapshot selector flow**

Normalize route, increment route epoch, load only the matching device hash, and submit an apply request or safe bypass.

- [ ] **Step 3: Implement App reconciliation**

After route event ACK, send local canonical state with the current daemon generation. Handle stale generation by rereading daemon status and resending.

- [ ] **Step 4: Run integration tests**

```bash
cmake --build build-host -j1 --target route_restore_integration_test
ctest --test-dir build-host -R route_restore_integration_test --output-on-failure
./gradlew :app:testDebugUnitTest --tests "com.llsl.viper4android.daemon.RouteKeyMappingTest" --max-workers=1 --no-daemon
```

Expected: PASS.

### Task 9: Device Build, Packaging, and Acceptance

**Repositories:** both

- [ ] **Step 1: Run complete host verification**

```bash
ctest --test-dir build-host --output-on-failure
```

- [ ] **Step 2: Build both driver ABIs and daemon binary**

```bash
cmake --build build/arm64-v8a --target v4a_re viper-daemon -- -j1
cmake --build build/armeabi-v7a --target v4a_re viper-daemon -- -j1
```

- [ ] **Step 3: Build and package the module**

```bash
make zip ABI=arm64-v8a,armeabi-v7a
```

Verify the zip contains both libraries, `common/bin/viper-daemon`, the init
fragment, `boot-completed.sh`, protocol version metadata, and legacy fallback
files.

- [ ] **Step 4: Build App APK**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1 --no-daemon
```

- [ ] **Step 5: Install only after hashes and rollback artifacts are recorded**

Record APK/module/library hashes and preserve the previous module, snapshots, audio config, and APK before touching the device.

- [ ] **Step 6: Run device acceptance**

Verify daemon late-start, App-before-music, music-before-App, route switching, App kill, daemon restart, audioserver restart, corrupt snapshot fallback, socket loss recovery, and legacy backend fallback. Do not claim success without logs showing route key, generations, registry state, and apply ACK/NACK.
