# ViPER4Android Root Audio Daemon Design

**Status:** Approved design, implementation not started

**Date:** 2026-08-18

## 1. Summary

Introduce a native C++ root daemon supplied by the KernelSU module. The daemon
owns driver control, route-aware snapshot selection, logical session tracking,
and recovery coordination. The Android App remains responsible for the UI,
editable Room/DataStore state, resource selection, and user-facing lifecycle.

The daemon does not replace AudioFlinger and does not create fake effect
instances. AudioFlinger remains the only owner of real effect instance
creation and destruction. The daemon observes driver context lifecycle events,
maintains a logical registry, and applies complete validated snapshots to
already-existing driver contexts through a control-thread protocol.

The design is intentionally staged. The first implementation must be able to
run in observe-only mode before it is allowed to apply snapshots.

## 2. Goals

- Keep effect state alive when the App process is stopped or killed.
- Apply the last valid state for a newly selected audio route before the App
  finishes starting.
- Distinguish speakers, wired devices, USB devices, and Bluetooth devices by a
  stable composite key.
- Track driver effect contexts and generated audio sessions without requiring
  an undocumented full AudioFlinger callback API.
- Recover lost driver events after daemon restart, socket overflow, or an
  audioserver restart.
- Keep the audio processing thread independent from daemon availability.
- Make driver snapshot application atomic: a bad or incomplete snapshot must
  never become the active DSP state.
- Preserve a legacy direct App-to-driver backend during migration and rollback.

## 3. Non-Goals

- The daemon will not replace AudioFlinger.
- The daemon will not create or destroy real AudioEffect instances.
- The first phase will not migrate all App Room/DataStore persistence into the
  daemon.
- The daemon will not process or store audio samples.
- The daemon will not require a hidden AudioFlinger Binder API.
- The daemon will not block, allocate, access files, or perform IPC from the
  real-time audio processing function.
- Parameter-level conflict merging is out of scope. State conflicts are
  resolved at complete-snapshot granularity.

## 4. Process Boundaries

```text
KernelSU module
  \- boot-completed.sh -> init: start viper_daemon
      \- viper-daemon (root, native C++)
          |- DriverEventServer
          |- AppControlServer
          |- RouteWatcher
          |- SessionRegistry
          |- DeviceSnapshotStore
          |- SnapshotSelector
          |- SnapshotApplier
          \- DaemonSupervisor/logging

audioserver
  \- libv4a_re.so
      |- ViperContext instances
      |- DriverDaemonBridge
      |- bounded event queue
      |- control-thread snapshot inbox
      \- real-time audio processing

Android App
  \- ViperService
      |- DaemonClient
      |- local state repository
      |- route-sync adapter
      \- UI and telemetry state
```

### 4.1 Daemon

The daemon is a native C++ executable installed by the module and owned by
Android init. Its init service runs in the foreground; init owns the process
lifecycle and restarts an unexpected exit. A boot-completed module hook only
executes `start viper_daemon` and writes a diagnostic marker. It must not
implement a second restart loop or double-fork the daemon.

The init service is `disabled` and belongs to `class late_start`, so the daemon
does not start before the module's boot-completed trigger requests it. The
service is not `oneshot`; an unexpected daemon exit is therefore restartable by
init. The module must verify that its init fragment is actually imported on the
target ROM before enabling this backend. If the ROM does not import module
`.rc` fragments, installation reports the daemon backend as unavailable and
retains the legacy backend rather than guessing an init import mechanism.

The daemon owns:

- driver and App IPC servers;
- route detection and debounce;
- logical session registry;
- snapshot selection and durable storage;
- complete-snapshot application requests;
- generation and protocol validation;
- recovery/rescan orchestration;
- bounded diagnostic telemetry.

### 4.2 Driver

The driver runs inside audioserver. It owns actual `ViperContext` instances and
their real-time processing. It exposes a control-thread bridge that:

- publishes context/configuration/release/telemetry events;
- accepts complete snapshot commands;
- validates and stages snapshot data;
- prepares DSP graphs and resources off the audio thread;
- atomically publishes a validated active snapshot;
- retains the last valid immutable snapshot in process memory.

### 4.3 App

The App remains the user-facing authority for desired editable state. It must
not write the daemon's private state directory or bypass the daemon while the
daemon backend is healthy. It connects through `ViperService`, uploads a
canonical complete snapshot, receives route and apply acknowledgements, and
updates its UI from daemon status plus local state.

During migration, a `DriverBackend` abstraction exposes:

```text
DriverBackend
  |- DaemonBackend
  \- LegacyDirectAudioEffectBackend
```

The legacy backend is retained for daemon absence, unsupported devices, module
rollback, and an explicit compatibility flag. The App must not concurrently
write through both backends.

## 5. IPC Architecture

There are two independent abstract Unix socket protocols.

### 5.1 Driver-to-Daemon Socket

Use a full-duplex, non-blocking `SOCK_SEQPACKET` socket in the abstract
namespace:

```text
@viper4android.driver.v1
```

The driver connects as a client. The daemon may send control commands on the
same connection.

Driver-side rules:

- `connect()` runs only on the driver bridge/control thread.
- `send()` uses `MSG_DONTWAIT`.
- The real-time processing function never performs a socket syscall.
- A missing daemon, broken connection, or full queue drops an event rather than
  blocking audio.
- The driver bridge uses a bounded outbound queue.
- The daemon performs a rescan after every reconnect and sequence gap.

### 5.2 App-to-Daemon Socket

Use a separate abstract Unix socket:

```text
@viper4android.app.v1
```

The App connects with Android `LocalSocket` using the abstract namespace. The
protocol uses request/response messages plus daemon-pushed route and status
events.

The daemon validates:

- `SO_PEERCRED`;
- the expected App UID/package association;
- protocol version;
- per-connection nonce;
- request id and sequence;
- maximum frame and resource sizes;
- CRC and content hash for payloads.

### 5.3 Frame Format

All messages use a fixed header followed by a bounded payload:

```text
magic              4 bytes, "V4AD"
protocol_version   u16
message_type       u16
flags              u32
request_id         u64
sequence           u64
payload_length     u32
payload_crc32      u32
payload            payload_length bytes
```

The implementation must reject unknown mandatory flags, oversized lengths,
invalid CRCs, duplicate request ids, and protocol versions outside the
supported compatibility range.

## 6. Driver Event Protocol

The driver event types are:

```text
DRIVER_HELLO
CONTEXT_CREATED
CONTEXT_CONFIGURED
CONTEXT_ENABLED
CONTEXT_DISABLED
CONTEXT_RELEASED
RESOURCE_GENERATION_CHANGED
TELEMETRY
RESCAN_RESPONSE
SNAPSHOT_APPLIED_ACK
SNAPSHOT_APPLIED_NACK
```

Each driver event carries:

```text
protocol_version
event_type
event_sequence
boot_id
bridge_nonce
context_instance_id
audio_session_id
io_id
sample_rate
channel_mask
enabled
session_generation
payload_length
payload_crc
```

`context_instance_id` is a driver-process monotonic identity. It is not
derived solely from `audio_session_id`, because OEM policies can generate,
reuse, or move non-zero sessions.

`event_sequence` is monotonic for the bridge lifetime. The daemon detects a
gap and marks the registry stale until a rescan completes.

## 7. App Protocol

### 7.1 App Requests

```text
APP_HELLO
APP_GET_STATUS
APP_GET_CURRENT_SNAPSHOT
APP_SYNC_STATE
APP_APPLY_PATCH
APP_UPLOAD_RESOURCE_BEGIN
APP_UPLOAD_RESOURCE_CHUNK
APP_UPLOAD_RESOURCE_END
APP_ACK_ROUTE_EVENT
APP_PING
```

### 7.2 Daemon Push Events

```text
DAEMON_HELLO
ROUTE_CHANGED
SNAPSHOT_SELECTED
SNAPSHOT_APPLIED
SYNC_REQUIRED
DRIVER_STATUS_CHANGED
SESSION_REGISTRY_CHANGED
TELEMETRY
DAEMON_ERROR
```

The App must ACK route events and snapshot applications through the socket.
Intent delivery alone is not considered synchronization.

## 8. Device Identity

Snapshots use a normalized composite device key. The key contains:

```text
route_type
stable_address_or_port
product_name_normalized
sample_rate
channel_mask
encoding
output_flags
```

Canonical examples:

```text
speaker|builtin|stereo|48000|pcm16
wired|jack:3.5mm|stereo|48000|pcm16
usb|card=2;device=0;port=usb-1|stereo|48000|pcm24
bluetooth|mac=aa:bb:cc:dd:ee:ff|stereo|48000|pcm16
```

Normalization rules:

- lowercase all textual fields;
- remove insignificant whitespace;
- normalize known ROM-specific address formats;
- use Bluetooth MAC when available;
- use USB card/device/port together;
- use a fixed `builtin` identity for internal speaker output;
- never include a volatile process id, track id, or audio session id;
- reject or migrate old keys when the key schema version changes.

Route changes are debounced, initially at 300 ms, to avoid applying several
intermediate states during an insert/remove transition. The debounce interval
is configurable for device acceptance testing but is not user-facing.

## 9. Snapshot Store

The daemon exclusively owns the following directory:

```text
/data/adb/viper4android/
  daemon.state
  boot.id
  routes/
    <device-key-hash>/
      current.snapshot
      previous.snapshot
      metadata
  resources/
    <sha256>
  journal/
    apply-<generation>.log
```

Permissions:

```text
directory: 0700 root:root
snapshot: 0600 root:root
resource: 0600 root:root
```

Snapshot writes are atomic:

```text
write current.snapshot.tmp
fsync(current.snapshot.tmp)
rename(current.snapshot.tmp, current.snapshot)
fsync(parent directory)
```

Before replacing `current.snapshot`, the previous valid file is moved to
`previous.snapshot`. A failed or incomplete write must never replace either
valid file.

## 10. Snapshot Schema

The snapshot header contains:

```text
magic
schema_version
driver_protocol_version
device_key
device_key_hash
boot_id
daemon_generation
app_generation
created_at
payload_length
payload_crc32
payload_sha256
```

The payload contains the complete canonical state:

```text
route identity
master enabled
global mode
effect enabled states
all scalar parameters
all array parameters
IEM parameters
DSP resource references
IEM resource references
resource generation
graph/config generation
driver compatibility flags
```

Resource references contain metadata rather than UI paths:

```text
resource_id
content_sha256
size
kind
format
channel/order metadata
```

Resources are stored content-addressably. A resource cannot be referenced by a
current snapshot until its complete upload has passed size and SHA-256
validation.

## 11. Generation and Conflict Rules

The system uses three identifiers:

```text
boot_id
daemon_generation
app_generation
```

`boot_id` separates device/daemon lifetimes. `daemon_generation` is a
daemon-side 64-bit monotonic value incremented after every accepted complete
state replacement or route application. `app_generation` is maintained by the
App per device key and increments when the user edits or submits a canonical
state.

The conflict policy is complete-state replacement, not parameter-level merge:

1. The daemon selects and applies the target device's last valid snapshot.
2. The App connects and sends its local canonical state plus `app_generation`.
3. The App state becomes the desired current state for that device.
4. The daemon writes the new snapshot atomically and applies it to the driver.
5. The daemon returns `APPLIED(app_generation, daemon_generation)`.
6. A request with an old `base_daemon_generation` returns `STALE_GENERATION` and
   does not overwrite newer state.

The daemon is the runtime authority after accepting a state. The App remains
the desired-state authority when it submits a newer generation.

## 12. Session Registry

The daemon registry tracks logical context state independently from actual
AudioFlinger ownership:

```text
UNKNOWN
  -> DISCOVERED
  -> CONFIGURED
  -> ACTIVE
  -> STALE
  -> RELEASED
```

Transitions:

```text
CONTEXT_CREATED      -> DISCOVERED
CONTEXT_CONFIGURED   -> CONFIGURED
CONTEXT_ENABLED      -> ACTIVE
CONTEXT_DISABLED     -> CONFIGURED
CONTEXT_RELEASED     -> RELEASED
rescan omission      -> STALE
reappearance         -> ACTIVE or CONFIGURED
```

The registry identity is:

```text
boot_id + context_instance_id
```

Each entry records:

```text
audio_session_id
io_id
route_key
sample_rate
channel_mask
enabled
last telemetry
last event sequence
last applied daemon generation
last applied resource generation
```

The daemon does not create contexts. When AudioFlinger creates a new context,
the driver reports it. If the current route has a valid snapshot, the driver
control thread restores it. Otherwise the context remains safely bypassed or
uses its normal default state until a valid complete snapshot arrives.

## 13. Route Change and Restore Flow

```text
RouteWatcher detects a change
  -> debounce and normalize route
  -> compute new device key
  -> increment route_epoch
  -> select current.snapshot
  -> valid snapshot:
       APPLY_SNAPSHOT to driver
       wait for ACK
     no valid snapshot:
       APPLY_SAFE_BYPASS
  -> notify/wake App FGS when synchronization is needed
  -> App reconnects and uploads local state
  -> daemon atomically stores and reapplies App state
  -> send SNAPSHOT_APPLIED and route ACK
```

The daemon applies the target snapshot first so an App startup delay does not
create an avoidable unconfigured window. If the target device has no valid
snapshot, it must not inherit another device's snapshot.

## 14. Atomic Driver Snapshot Application

The driver control protocol is staged and committed:

```text
APPLY_SNAPSHOT_BEGIN
  metadata, expected hash, generation
APPLY_SNAPSHOT_RESOURCE_CHUNK...
APPLY_SNAPSHOT_PARAMETER_CHUNK...
APPLY_SNAPSHOT_COMMIT
```

The driver control thread:

1. Receives data into a bounded staging representation.
2. Validates protocol version, lengths, ranges, CRC, and SHA-256.
3. Restores parameters, resources, and IEM state into control-thread objects.
4. Prepares the pending DSP graph.
5. Atomically publishes the new immutable snapshot and active graph.
6. Sends an ACK containing the applied generations.

On failure:

- discard staging data;
- keep the current active graph and snapshot;
- send a specific NACK reason;
- do not expose partial parameters;
- let the daemon retry or ask the App to resynchronize.

## 15. Real-Time Audio Constraints

The audio processing function must not perform:

- socket operations;
- file IO;
- mutex locking;
- daemon status queries;
- dynamic allocation;
- snapshot parsing or hashing;
- resource loading;
- DSP graph preparation;
- formatted logging.

It may only read already-published atomic state, use prepared graph slots and
mailboxes, and process or bypass audio according to the current active state.

All daemon commands enter the driver control thread. Resource and graph updates
are prepared there and published through the existing graph-slot/mailbox
mechanisms.

## 16. Route Watcher Adapters

The watcher uses several adapters because route information differs across ROMs:

1. Framework/shell route adapter as a compatibility fallback.
2. ALSA/sysfs adapter for sound cards, USB ports, and available uevents.
3. Driver telemetry adapter to correct the route actually seen by contexts.
4. App correction from `AudioDeviceInfo` after the App reconnects.

No hidden AudioFlinger API is required. If an adapter is unavailable, the
daemon reports reduced route confidence and waits for another adapter or App
correction rather than guessing a different device snapshot.

## 17. App Lifecycle and Wake-up

The App adds a `DaemonClient` owned by `ViperService` with these states:

```text
Disconnected
Connecting
Connected
Syncing
Ready
Degraded
```

Initial synchronization:

```text
APP_HELLO
  -> DAEMON_HELLO
  -> APP_GET_STATUS
  -> read current route and generations
  -> read local Room/DataStore state
  -> convert to canonical snapshot
  -> APP_SYNC_STATE
  -> wait for apply ACK
```

The daemon may explicitly wake the App FGS with:

```text
am start-foreground-service \
  -n com.llsl.viper4android/.service.ViperService \
  -a com.llsl.viper4android.action.DAEMON_SYNC
```

The Intent carries:

```text
route_key
route_epoch
daemon_generation
sync_reason
nonce
```

Wake-up is limited to:

- a route key change;
- no snapshot for the current route;
- generation mismatch;
- protocol mismatch;
- first daemon/App reconciliation;
- a driver NACK requiring App correction.

Normal session create/release events do not wake the App.

## 18. Failure and Degraded Modes

### Daemon unavailable

The driver bridge disconnects without blocking audio. The driver continues the
last valid in-memory immutable snapshot. If none exists, it bypasses safely.

### Daemon restart

The driver keeps its current state. The daemon reconnects, requests a rescan,
rebuilds the registry, and reapplies the current route snapshot if necessary.

### App killed

The daemon and driver continue using the applied snapshot. A later route change
may restore a different device snapshot before the App is restarted.

### audioserver restart

The daemon keeps disk state. New driver contexts reconnect and receive the
current route snapshot. Without a valid snapshot, they bypass safely.

### Event loss

Sequence gaps mark registry entries stale. The daemon sends a rescan request and
does not claim the registry is complete until the rescan ends.

### Snapshot corruption

The daemon rejects failed CRC/SHA/schema validation, falls back to
`previous.snapshot`, and enters safe bypass if neither snapshot is valid. The
App is notified when a fresh upload is required.

### IPC pressure

Driver events may be dropped under pressure. Audio processing never waits for
queue capacity. App requests are rejected with a bounded error rather than
allowing unbounded memory growth.

## 19. Security and SELinux

The daemon validates App peer credentials and accepts only the expected App UID
and package association. It validates driver peer credentials and the expected
audioserver domain where the platform exposes the required security context.

The module must provide or verify the permissions needed for:

- daemon creation of abstract sockets;
- audioserver connection to the driver socket;
- App connection to the App socket;
- daemon launching the explicit App FGS action;
- daemon reading required route/audio state;
- App exclusion from the daemon's private snapshot directory.

If a target ROM rejects the App socket or driver socket policy, installation
must report daemon backend unavailable and use the legacy backend. It must not
silently claim that the daemon is active.

Every connection also validates protocol version, nonce, frame length, CRC,
request sequence, and resource upload limits. No audio sample data is persisted
or sent through these protocols.

## 20. Telemetry

Daemon telemetry includes:

```text
daemon_pid
protocol_version
driver_connected
app_connected
current_route_key
route_epoch
daemon_generation
driver_generation
session_count
last_snapshot_status
last_nack_reason
last_rescan_time
last_app_sync_time
```

Driver telemetry includes:

```text
context_count
session ids
io ids
active graph generation
resource generation
bypass reason
last snapshot apply result
last event sequence
```

Telemetry contains no audio samples or user audio content.

## 21. Testing Strategy

### Native unit tests

- frame encoding/decoding, limits, and CRC;
- schema/version rejection;
- atomic snapshot write and crash recovery;
- device key normalization;
- generation conflict handling;
- session registry transitions;
- event sequence gap and rescan;
- driver snapshot staging, commit, and NACK;
- daemon reconnect;
- driver non-blocking behavior while daemon is unavailable.

### Kotlin unit tests

- canonical App state conversion;
- App generation increments;
- stale generation rejection and resync;
- `AudioDeviceInfo` to device key conversion;
- daemon wake Intent parsing and ACK;
- `DaemonClient` reconnect state machine;
- legacy backend fallback.

### Host integration tests

Run a fake driver client, fake App client, fake route source, and temporary
snapshot store. Cover both connection orders, daemon restart, event loss,
corrupt snapshots, route debounce, duplicate App clients, and resource upload
reordering/truncation.

### Device acceptance tests

1. Reboot and confirm daemon late-start.
2. Start music before starting the App and verify daemon/driver recovery.
3. Connect the App and verify generation and apply ACKs.
4. Switch between speaker, wired, USB, and Bluetooth routes.
5. Kill the App and verify the current effect state remains active.
6. Restart the daemon and verify rescan and registry recovery.
7. Restart audioserver and verify snapshot recovery.
8. Corrupt `current.snapshot` and verify fallback or safe bypass.
9. Stress IPC queue pressure and verify no audio-thread stall.
10. Verify logs contain no audio samples, secrets, or unauthorized file access.

## 22. Implementation Phases

### Phase 0: Protocol and fake daemon

Define frame, snapshot, device key, and generation types. Add codec and schema
tests without changing runtime behavior.

### Phase 1: Driver observe-only

Add driver event publication and daemon registry ingestion. Do not apply
snapshots yet. Measure event rate and bridge overhead.

### Phase 2: Snapshot storage

Add App canonical snapshot upload and daemon atomic storage. The driver may
validate and acknowledge snapshots but remains in observe-only apply mode.

### Phase 3: Snapshot application

Enable control-thread snapshot staging, resource restoration, graph preparation,
and atomic publish. Keep the App legacy backend as a fallback.

### Phase 4: Route-first restore

Enable route watcher, device key selection, snapshot-first restoration, and
on-demand App wake-up/reconciliation. Start the daemon through the verified
init service and boot-completed trigger.

### Phase 5: Registry and rescan

Enable full context lifecycle tracking, sequence-gap recovery, and audioserver
restart recovery.

### Phase 6: Default daemon backend

Use the daemon backend when the daemon and policy checks are healthy. Keep the
legacy backend available for unsupported ROMs and module rollback.

## 23. Success Criteria

The implementation is accepted only when all of the following hold:

- A saved device snapshot can be restored while the App is not running.
- A route switch does not depend on the App starting first.
- Killing the App does not release or clear the active driver state.
- Restarting the daemon does not block the audio thread or force an avoidable
  bypass.
- Restarting audioserver allows daemon-driven snapshot recovery.
- Corrupt snapshots cannot become the active graph.
- Lost session events can be repaired by rescan.
- `Process()` has no new blocking IO, locks, file access, or allocation.
- Generation conflicts produce deterministic ACK/NACK behavior.
- Missing snapshots result in safe bypass rather than reuse of another device's
  configuration.
- Unsupported socket or SELinux policy is reported honestly and activates the
  legacy fallback.

## 24. Rollback and Compatibility

The module must be able to disable the daemon backend without removing the
driver library. A runtime compatibility flag and module rollback path must
exist before Phase 3 is enabled on a device.

Rollback requirements:

- stop daemon supervision;
- close daemon bridge connections without touching the audio processing path;
- switch App to legacy direct backend;
- preserve current driver snapshot until the next safe control transition;
- keep the previous module and APK artifacts available for device acceptance;
- record the reason for fallback in diagnostics.
