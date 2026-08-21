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

## Build Environment Constraints

构建在设备本机进行(11 GB 内存,静息可用约 3 GB,ZRAM 8 GB 已用约 5 GB,
`swappiness=150`,`overcommit_memory=1`)。四个构建阶段的实测内存表现:

| 阶段 | 用时 | 可用内存最低 | 最大进程 RSS |
|---|---|---|---|
| host 全量(63 目标,thin LTO) | 95s | 2673 MB | 189 MB |
| NDK 双 ABI + 打包 | 56s | 2641 MB | 155 MB |
| Gradle 增量 | 18s | 2612 MB | 664 MB |
| Gradle 全量重编 | 104s | 793 MB | 2090 MB |

两个原生构建都不构成内存压力。压力全部来自 Gradle 全量重编:它并发运行
`GradleDaemon`(1955-2062 MB)与 `KotlinCompileDaemon`(未设上限时 1340 MB),
RSS 合计峰值约 2.5-3.0 GB,单次构建换出约 1.7 GB 到 swap。

因此 `gradle.properties` 为 Kotlin 守护进程设置了 `kotlin.daemon.jvmargs=-Xmx1024m`:
它原本是两个 JVM 中唯一完全无堆上限的一份。`overcommit_memory=1` 意味着超额分配
会先成功、之后才崩,所以必须显式设界,不能依赖分配失败来兜底。

`org.gradle.jvmargs` 保持 `-Xmx2048m`。实测将其降到 1536m 时 `GradleDaemon` 的 RSS
反而略升,因为其占用主要来自 metaspace、code cache、线程栈与 AAPT2/dexer 的 native
分配,均不受 `-Xmx` 约束;降堆无收益,却会增加 Java 堆 OOM 风险。

调查期间设备未发生 OOM(`oom_kill=0`,uptime 连续 1 小时无重启),四个阶段全部构建成功。

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

- [x] **Step 1: Write failing bridge tests**

Use a local test socket endpoint. Verify event serialization, sequence increment, reconnect behavior, bounded queue overflow, and that `Publish` returns quickly when no daemon is listening.

- [x] **Step 2: Implement control-thread-only socket bridge**

Use a non-blocking abstract `SOCK_SEQPACKET` connection and a bounded queue. The bridge thread owns connect/send/reconnect; callers enqueue a fixed-size event without touching the socket.

- [x] **Step 3: Emit context lifecycle events**

Add creation, configuration, enable/disable, release, resource-generation, and telemetry publication from existing control/lifecycle points. Do not add any bridge call to `Process()`.

- [x] **Step 4: Add rescan response plumbing**

Expose a control-thread snapshot of live contexts sufficient for the daemon to rebuild its registry after reconnect or sequence gaps.

- [x] **Step 5: Run native bridge and realtime audit tests**

```bash
cmake --build build-host -j1 --target driver_daemon_bridge_test driver_event_publisher_test iem_realtime_audit_test
ctest --test-dir build-host -R "driver_daemon_bridge_test|driver_event_publisher_test|iem_realtime_audit_test" --output-on-failure
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

- [x] **Step 1: Write failing registry tests**

Cover created/configured/active/released transitions, context identity by boot id plus instance id, sequence gaps marking stale, rescan completion, and removal of omitted contexts after a complete rescan.

- [x] **Step 2: Implement session registry**

Keep logical registry state independent from AudioFlinger ownership. Never create or release an effect from daemon code.

- [x] **Step 3: Implement driver event server**

Accept the driver bridge, validate peer credentials where available, decode frames, reject malformed input, and forward valid events to the registry.

- [x] **Step 4: Implement route watcher adapter boundary**

Provide a deterministic fake adapter for host tests and a device adapter that reads available Android/ALSA/sysfs route information without requiring hidden AudioFlinger Binder APIs.

- [x] **Step 5: Implement daemon lifecycle and signal handling**

Use foreground execution, `SIGTERM`/`SIGINT` shutdown, bounded reconnect loops, `/data/adb/viper4android/daemon.state`, and diagnostic status output. The daemon must remain observe-only in this task.

- [x] **Step 6: Add native daemon target**

Build `viper-daemon` as a host executable and as an Android executable with a separate target-specific link configuration. Do not link the daemon into `v4a_re`.

- [x] **Step 7: Run daemon host tests**

```bash
cmake --build build-host -j1 --target session_registry_test daemon_runtime_test daemon_driver_e2e_test viper-daemon
ctest --test-dir build-host -R "session_registry_test|daemon_runtime_test|daemon_driver_e2e_test" --output-on-failure
sh tests/daemon_smoke.sh
```

Expected: PASS.

### Task 5: Add Init-Owned Daemon Service and Boot-Completed Trigger

**Repository:** `/root/AndroidIDEProjects/ViPERFX_RE`

**Files:**
- Create: `module/initrc/viper-daemon.rc` (KernelSU injects `initrc/*.rc` into `init.rc`; the
  originally planned `module/common/etc/init/` path does not reach init)
- Create: `module/daemon-start.sh`
- Create: `module/boot-completed.sh`
- Create: `module/service.sh`
- Modify: `module/common/install.sh`
- Modify: `Makefile`
- Modify: `module/uninstall.sh`
- Create: `tests/module/DaemonInitServiceTest.sh`
- Create: `tests/module/daemon_start_smoke.sh`
- Create: `tests/module/probe_mutations.sh`
- Create: `tests/module/probe_ksu_initrc.sh`

- [x] **Step 1: Write failing shell tests**

Test that the package contains `viper-daemon`, an init service definition, and a boot-completed trigger. Verify the init service is not marked `oneshot`, uses `disabled` plus `class late_start`, and the boot script calls `start viper_daemon` instead of implementing a duplicate restart loop.

- [x] **Step 2: Add module packaging**

Copy the daemon binary under `common/bin/viper-daemon_<abi>`, have `install.sh` persist the
ABI-specific binary to `$MODPATH/bin/viper-daemon` (functions.sh `cleanup()` deletes
`$MODPATH/common`), ship `initrc/viper-daemon.rc`, install the start trigger, and preserve the
existing driver files and legacy fallback files.

- [x] **Step 3: Implement init service**

Define `service viper_daemon` with the native executable, `class late_start`, `user root`,
`group root`, and `disabled`. Do not use `oneshot`; init must restart an unexpected daemon exit.
The exec path must be literal: init cannot expand `$MODDIR`. `seclabel u:r:ksu:s0` is used
because this device's KernelSU root context is `u:r:ksu:s0` (verified via `su -c id`).

- [x] **Step 4: Implement boot-completed trigger**

`daemon-start.sh` only executes `start viper_daemon` and writes a small diagnostic marker.
KernelSU reaches it through `boot-completed.sh`; Magisk has no boot-completed stage, so
`service.sh` waits for `sys.boot_completed=1` and calls the same helper. It is idempotent:
calling it twice cannot create a second process because init owns the service. When init has no
`viper_daemon` service the helper reports that and leaves the legacy backend in place.

- [x] **Step 5: Implement uninstall cleanup**

Stop the init service, remove its PID/status files, preserve user snapshots unless explicit module removal cleanup is requested, and never modify App Room/DataStore files.

- [x] **Step 6: Verify init import on the target ROM before enabling by default**

Confirm `init.svc.viper_daemon`, `ctl.start`, crash restart, and socket permissions.

Verified without a reboot on this device (KernelSU 4.2.0-rc1-11-g829f61fb, uapi 2, Android 15 /
SDK 35): `ksud initrc refresh` picks `initrc/viper-daemon.rc` up into
`/metadata/watchdog/ksu/modules.rc` with the `service viper_daemon`, `class late_start`, and
`on property:sys.boot_completed=1` lines intact (`tests/module/probe_ksu_initrc.sh`, run against a
scratch module id and reverted afterwards).

The rest is now confirmed on the running system by `tests/module/probe_init_restart.sh`:
`init.svc.viper_daemon=running`, the daemon runs as init's own child (PPID 1), a SIGKILL is
followed by init respawning it under a new pid (21770 → 29312), both `@viper4android.driver.v1`
and `@viper4android.app.v1` listeners are rebound, and the restarted daemon reports
`app_listening=1 driver_connected=1`. The probe counts listening sockets by state field rather
than by name: `/proc/net/unix` also lists live client connections under the same abstract name,
so a raw name count moves whenever a peer connects and proves nothing about rebinding.

Not covered without a reboot, and deliberately left so: whether a cold boot reaches
`late_start` in the same way. That is a boot-path property, not a service-definition one, and
rebooting stays the user's decision.

- [x] **Step 7: Run shell packaging tests**

```bash
sh tests/module/DaemonInitServiceTest.sh
sh tests/module/daemon_start_smoke.sh
sh tests/module/probe_mutations.sh
```

Expected: PASS.

### Task 6: Add Snapshot Application Control Path

**Repository:** `/root/AndroidIDEProjects/ViPERFX_RE`

**Files:**
- Modify: `src/DriverDaemonBridge.h`
- Modify: `src/DriverDaemonBridge.cpp`
- Modify: `src/DriverEventPublisher.h`
- Modify: `src/DriverEventPublisher.cpp`
- Modify: `src/DspGraphSlots.h`
- Modify: `src/DspGraphSlots.cpp`
- Modify: `src/ViperContext.h`
- Modify: `src/ViperContext.cpp`
- Create: `src/SnapshotApplyController.h`
- Create: `src/SnapshotApplyController.cpp`
- Create: `protocol/ParameterStream.{h,cpp}` (wire format for `Snapshot::parameters`)
- Create: `protocol/SnapshotCommand.{h,cpp}` (BEGIN/CHUNK/COMMIT/ABORT frames)
- Create: `tests/SnapshotApplyControllerTest.cpp`
- Create: `tests/ParameterStreamTest.cpp`
- Create: `tests/SnapshotCommandTest.cpp`
- Create: `tests/ViperContextSnapshotTest.cpp`
- Create: `tests/SnapshotAckEndToEndTest.cpp`

**Interfaces:**
- `SnapshotApplyController::Begin(const SnapshotMetadata &)`
- `SnapshotApplyController::Append(std::span<const uint8_t>)`
- `SnapshotApplyController::Commit()`
- `SnapshotApplyController::Abort(ApplyError)`
- `SnapshotApplyController::LastAppliedGeneration() const`

- [x] **Step 1: Write failing apply tests**

Test complete snapshot staging, out-of-order chunks, CRC/hash mismatch, range rejection, graph preparation failure preserving the old graph, idempotent generation, and successful ACK contents.

- [x] **Step 2: Implement control-thread staging**

Keep bounded staged resources and parameters outside the realtime path. Validate every chunk
before accepting it. Chunks are strictly sequential: a gap would stage uninitialized bytes and a
rewind would accept a rewritten prefix.

- [x] **Step 3: Connect commit to existing graph slots/mailboxes**

Prepare the pending graph and resource generation on the control thread, then publish atomically
using existing `DspGraphSlots`, `DspResources`, and parameter mailbox mechanisms. Parameters and
resources are replayed onto scratch copies first, so a rejected record cannot leave live state
half-updated. Required an added `DspGraphSlots::RetractPending()`: only `Process()` consumes a
pending graph, so before playback starts a second apply could never prepare.

- [x] **Step 4: Add daemon command ACK/NACK**

Return explicit apply result, generation, resource generation, and failure reason to the daemon as
`SNAPSHOT_APPLIED_ACK`/`SNAPSHOT_APPLIED_NACK`, correlated by the command's `request_id`.
Successful chunks are not individually acknowledged. `ViperContext` now takes a `control_mutex_`
because the bridge thread and AudioFlinger command threads both mutate control state;
`Process()` never takes it.

- [x] **Step 5: Run native regression tests**

```bash
cmake --build build-host -j1 --target snapshot_apply_controller_test parameter_stream_test \
    snapshot_command_test viper_context_snapshot_test snapshot_ack_e2e_test \
    iem_context_test dsp_graph_slots_test
ctest --test-dir build-host -R "snapshot_apply_controller_test|parameter_stream_test|snapshot_command_test|viper_context_snapshot_test|snapshot_ack_e2e_test|iem_context_test|dsp_graph_slots_test" --output-on-failure
```

Expected: PASS.

### Task 7: Add App Daemon Client and Backend Selection

**Repository:** `/root/AndroidIDEProjects/ViPER4Android`

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonProtocol.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonSnapshotCodec.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonDriverEvent.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonClient.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonSnapshotMapper.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonBackend.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonRouteMapper.kt`
- Create: `app/src/main/java/com/llsl/viper4android/viper/ParamSink.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/service/ViperService.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/viper/ViperEffect.kt` (implements `ParamSink`)
- Modify: `app/src/main/java/com/llsl/viper4android/viper/ViperDispatcher.kt` (writes to `ParamSink`)
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `tools/DaemonGoldenVector.cpp` (ViPERFX_RE; generates the cross-language fixtures)
- Create: `app/src/test/resources/daemon-golden-vectors.txt`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/DaemonProtocolTest.kt`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/DaemonWireCompatibilityTest.kt`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/DaemonSnapshotMapperTest.kt`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/DaemonClientStateTest.kt`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/DaemonBackendTest.kt`

`AndroidManifest.xml` needed no change: `ViperService` is already declared and the
abstract-namespace socket requires no permission.

**Interfaces:**
- `DaemonClient.connect()` / `disconnect()` / `syncState()` / `observe()`
- `DaemonConnectionState`: `Disconnected`, `Connecting`, `Connected`, `Syncing`, `Ready`, `Degraded`
- `DaemonBackend.applyState(state: EffectState, masterEnabled: Boolean): Result<Unit>`
- `DaemonBackend.readStatus(): DriverTelemetry?`

- [x] **Step 1: Write failing Kotlin codec tests**

Test frame round-trip, CRC rejection, generation conflict mapping, bounded payload rejection, and
daemon error code mapping. Byte-identity with the native codecs is asserted against golden vectors
generated by `tools/DaemonGoldenVector.cpp`, so a layout drift fails instead of corrupting state.

- [x] **Step 2: Write failing state mapper tests**

Test canonical `EffectState` conversion including all scalar/array parameters, resources by content
hash, route identity, and app generation. The mapping is not duplicated: `ViperDispatcher` now
writes into a `ParamSink`, so the snapshot records exactly the writes the legacy `AudioEffect`
backend performs.

- [x] **Step 3: Implement `LocalSocket` client**

Use a dedicated coroutine dispatcher, request ids, bounded frames, reconnect backoff, and explicit ACK/NACK handling. Never perform socket IO on the main thread.

- [x] **Step 4: Add daemon backend with legacy fallback**

The daemon backend is used only after it actually accepts a snapshot. On absence, disconnect, NACK
or an unusable route it reports `Fallback` and the legacy direct `AudioEffect`/`ConfigChannel`
backend still applies the state, so audio processing never silently stops.

- [x] **Step 5: Integrate with `ViperService`**

On service start, connect and sync local state. On daemon route event, update device status and let daemon-first snapshot application complete before App reconciliation. Add the explicit `DAEMON_SYNC` action and ensure `START_STICKY` reconnects safely.

- [x] **Step 6: Run Kotlin daemon tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.llsl.viper4android.daemon.*" --max-workers=1 --no-daemon
```

Expected: PASS.

### Task 8: Integrate Route-First Snapshot Restore

**Repositories:** both

**Files:**
- Modify: `protocol/DeviceKey.h` (canonical route-identity format constants)
- Modify: `daemon/RouteWatcher.cpp` (`AndroidRouteAdapter` now fills the format fields)
- Modify: `daemon/DriverEventServer.{h,cpp}` (snapshot command send + apply-result capture)
- Modify: `daemon/DaemonRuntime.{h,cpp}` (debounce, route epoch, restore, state file)
- Create: `tests/RouteWatcherTest.cpp`
- Create: `tests/RouteRestoreIntegrationTest.cpp`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonRouteMapper.kt`
- Create: `app/src/main/java/com/llsl/viper4android/daemon/DaemonStatusReader.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/daemon/DaemonBackend.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/service/ViperService.kt`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/RouteKeyMappingTest.kt`
- Create: `app/src/test/java/com/llsl/viper4android/daemon/DaemonReconciliationTest.kt`

`SnapshotStore.cpp` and `AudioOutputDetector.kt` needed no change: the store already
addresses snapshots by device hash, and the detector already reports a stable per-route
id that `DaemonRouteMapper` maps to the canonical identity.

- [x] **Step 1: Write route restore integration tests**

Test route debounce, per-device snapshot selection, no cross-device inheritance, daemon-first
apply, App sync afterward, and missing snapshot safe bypass. `RouteRestoreIntegrationTest` drives
a real `DaemonRuntime` against a real `ViperContext` over the real socket; only the route adapter
is faked, because a host test cannot move an audio route.

- [x] **Step 2: Implement route watcher to snapshot selector flow**

Normalize route, increment route epoch, load only the matching device hash, and submit an apply
request or safe bypass. Two constraints the tests forced out:

- The restore is held until the driver connects. `viper-daemon` is `late_start`, so it detects its
  route long before AudioFlinger loads the effect; clearing the pending flag on the first settled
  route silently dropped every boot-time restore.
- The route identity format fields are fixed constants shared by `protocol/DeviceKey.h` and
  `DaemonRouteMapper`. Deriving them per side produced keys that never matched, so every snapshot
  would have been refused as `DEVICE_MISMATCH`.

- [x] **Step 3: Implement App reconciliation**

After route event ACK, send local canonical state with the current daemon generation. A
`STALE_GENERATION` refusal is retried once, after rereading the daemon's published
`daemon.state`: the driver event wire carries no daemon generation, so that file is the only
authoritative source. When it is unreadable or not newer, there is no retry, because resending at
the same generation would be refused again and the legacy backend already covers the user.

- [x] **Step 4: Run integration tests**

```bash
cmake --build build-host -j1 --target route_restore_integration_test route_watcher_test
ctest --test-dir build-host -R "route_restore_integration_test|route_watcher_test" --output-on-failure
./gradlew :app:testDebugUnitTest --tests "com.llsl.viper4android.daemon.RouteKeyMappingTest" --max-workers=1 --no-daemon
./gradlew :app:testDebugUnitTest --tests "com.llsl.viper4android.daemon.DaemonReconciliationTest" --max-workers=1 --no-daemon
```

Expected: PASS.

### Task 9: Device Build, Packaging, and Acceptance

**Repositories:** both

#### Task 9a: App Route Endpoint (added after device testing)

Device testing on MTK mt6989 / Android 15 / KernelSU 4.2.0-rc1 found two faults that
made the daemon backend unusable in practice, so this task was inserted:

1. `AndroidRouteAdapter` reads `/sys/class/switch/h2w/state`, which this device does not
   expose at all. `route_known` stayed 0 forever and no snapshot was ever restored. The App
   is the only component that can see the live output route
   (`AudioManager.getAudioDevicesForAttributes`), so the App now names the route and the
   daemon caches it on disk for the next boot. `daemon/main.cpp` no longer uses
   `AndroidRouteAdapter`.
2. The App connected to `@viper4android.driver.v1`, which admits only root/audioserver
   peers, so every App connection was refused (`rejected_frames` climbed, `snapshot_commands`
   stayed 0). The App now uses a separate endpoint, `@viper4android.app.v1`.

**Files:**
- Create: `protocol/AppCommand.{h,cpp}` (app.v1 wire format; snapshot streaming reuses
  `SnapshotCommandType` and the `SnapshotCommand` codecs unchanged)
- Create: `daemon/AppEventServer.{h,cpp}` (app endpoint with `SO_PEERCRED` admission)
- Create: `daemon/RouteCache.{h,cpp}` (`<state_root>/route.cache`, atomic, mode 0600)
- Modify: `daemon/RouteWatcher.{h,cpp}` (add `AppReportedRouteAdapter`)
- Modify: `daemon/DaemonRuntime.{h,cpp}` (app endpoint delegate, snapshot persistence,
  cached-route resolution in `Start()`)
- Modify: `daemon/main.cpp` (`--app-socket`, App-reported route source)
- Modify: `protocol/DeviceKey.h` (canonical route-identity constants)
- Create: `tests/AppEventServerTest.cpp`, `tests/RouteCacheTest.cpp`,
  `tests/AppDaemonDriverEndToEndTest.cpp`
- Create: `app/.../daemon/AppProtocol.kt`, `DaemonModePreference.kt`
- Modify: `app/.../daemon/DaemonClient.kt` (app.v1 socket, hello, route report),
  `DaemonBackend.kt` (handshake in probe, route report before apply),
  `DaemonStatusReader.kt` (app endpoint fields)
- Modify: `app/.../service/ViperService.kt` (mode preference, observed live)
- Modify: `app/.../ui/screens/settings/SettingsDialog.kt`,
  `ui/screens/status/DriverStatusDialog.kt`, `MainViewModel.kt`,
  `data/repository/ViperRepository.kt`
- Create: `app/src/test/.../AppProtocolTest.kt`, `DaemonModePreferenceTest.kt`

Two real defects were found by the new end-to-end test rather than by inspection:
`OnSnapshotCommand` relayed the App's snapshot to the driver but never stored it, so
nothing survived a reboot and route restore had nothing to restore; and `Start()` seeded
the cached route but never resolved it, so `Status()` and the state file claimed "no route"
while a valid cache existed.

- [x] **Step 1: Run complete host verification**

```bash
ctest --test-dir build-host --output-on-failure
```

60/60 pass.

- [x] **Step 2: Build both driver ABIs and daemon binary**

```bash
cmake --build build/arm64-v8a --target v4a_re viper-daemon -- -j1
cmake --build build/armeabi-v7a --target v4a_re viper-daemon -- -j1
```

- [x] **Step 3: Build and package the module**

```bash
make zip ABI=arm64-v8a,armeabi-v7a
sh tests/module/DaemonInitServiceTest.sh
sh tests/module/probe_mutations.sh
```

`module/common/functions.sh` was missing from the remote build copy, which silently
dropped the MMT installer helper from the zip; the packaging test now covers it.

- [x] **Step 4: Build App APK**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1 --no-daemon
```

344 unit tests pass. Lint holds at its pre-existing 9 errors; the new daemon strings needed
`values-ru` entries, without which lint reported 11 additional `MissingTranslation` errors.

- [x] **Step 5: Install only after hashes and rollback artifacts are recorded**

Hashes recorded and the previous APK preserved at `~/viper-rollback/installed-app-prev.apk`.
`/data/adb/viper4android` did not exist before this work, so there were no snapshots to
preserve, and `uninstall.sh` deliberately leaves user snapshots in place.

- [x] **Step 6: Run device acceptance**

Verify daemon late-start, App-before-music, music-before-App, route switching, App kill, daemon restart, audioserver restart, corrupt snapshot fallback, socket loss recovery, and legacy backend fallback. Do not claim success without logs showing route key, generations, registry state, and apply ACK/NACK.

Covered by the owner acceptance scripts added in the 2026-08-20 plan, which run isolated
daemons on unique abstract socket names and restore anything they stop. None of them
installs the module; the installed module is still v2.0.1 and predates owner support.

| Item | Evidence |
| --- | --- |
| daemon late-start | `tests/module/probe_init_restart.sh`: `init.svc.viper_daemon=running`, daemon is init's own child (PPID 1). Cold-boot ordering is still reboot-gated — see Task 5 Step 6. |
| App kill | `tests/owner/verify_app_death.sh`: owner pid and effect id unchanged, module count steady, `streaming=1` measured afterwards. |
| daemon restart | `tests/owner/verify_daemon_death.sh`: SIGKILL, owner and effect survive, replacement daemon adopts the same pid. Also `probe_init_restart.sh` for init's own respawn and socket rebinding. |
| audioserver restart | `tests/owner/verify_audioserver_restart.sh`: found and fixed a real defect — a dead `AudioEffect` handle keeps reporting its old id, so the owner held a corpse while the daemon still claimed `owned`. Now rebuilt via `ERROR_DEAD_OBJECT` detection. |
| corrupt snapshot fallback | `TestCorruptCurrentSnapshotFallsBackToPrevious` / `TestCorruptCurrentDoesNotInheritForeignPrevious` in `tests/RouteRestoreIntegrationTest.cpp`: found a real defect — `RestoreCurrentRoute()` only read `current.snapshot`, so a torn write silently bypassed to driver defaults and lost the user's settings despite a valid `previous.snapshot`. Now falls back, with the device-hash re-check applied to the fallback too. Red-green verified. |
| socket loss recovery | Driver side: `TestDriverReconnectIsAccepted`, `TestNewDriverContextTriggersRestore`, `TestReconnectBeforeReleaseRestoresExactlyOnce`. Owner side: the daemon-death script exercises owner socket loss and reconnect on real sockets. |
| music-before-App / App-before-music | `verify_streaming_without_app.sh` covers the load-bearing half: the driver reports `PARAM_GET_STREAMING=1` and the owner reports session deltas (`tracked_sessions 3 -> 4`) with the App force-stopped. Ordering against a real music app is not scripted. |
| route switching | Host only: `TestRouteChangeSelectsThatRoutesSnapshot`, `TestTransientRouteIsDebounced`, `TestDaemonGenerationAdvancesPerRestore`. Moving a real audio route needs physical accessory changes and is not scripted. |
| legacy backend fallback | Kotlin `OwnershipExclusionTest` (13 cases) plus `DaemonBackendTest`: no daemon, refused apply, failed owner, and a stale `owned` claim whose process is gone all hand ownership back to the App. |

Not claimed: cold-boot `late_start` ordering, route switching against real hardware, and
App-vs-music start ordering. The first needs a reboot, which stays the user's decision;
the other two need physical interaction rather than a script.
