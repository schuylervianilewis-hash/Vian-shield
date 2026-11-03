# TrackerControl Native Engine Adapter Plan

Baseline: TrackerControl commit `4a3d1939b3e7859540367e15d597d54cb2e6a118`.
Target: Vianshield branch `refactor/engine-neutral-purge`.

This is the implementation contract for the first runtime-integration phase. The pinned submodule remains a source reference; no TrackerControl runtime is linked into the APK yet.

## 1. Confirmed JNI boundary

The packet engine is not a standalone C library with a generic API. Its JNI exports are hard-bound to these Java class names:

- `eu.faircode.netguard.ServiceSinkhole`: `jni_init`, `jni_start`, `jni_run`, `jni_stop`, `jni_clear`, `jni_get_mtu`, `jni_get_stats`, `jni_pcap`, `jni_socks5`, `jni_sni`, WireGuard start/stop/required/route, and `jni_done`.
- `eu.faircode.netguard.Util`: native property/address helpers and memory profiling.

The C engine also resolves these Java model classes by fully-qualified name:

- `eu.faircode.netguard.Packet`
- `eu.faircode.netguard.Allowed`
- `eu.faircode.netguard.ResourceRecord`
- `eu.faircode.netguard.Usage`

It calls back into the service instance for:

- `nativeExit(int, String)`
- `nativeError(int, String)`
- `protect(int)`
- `logPacket(Packet)`
- `dnsResolved(ResourceRecord)`
- `isDomainBlocked(String)`
- `getUidQ(int, int, String, int, String, int)`
- `isAddressAllowed(Packet)`
- `accountUsage(Usage)`

These are runtime contracts: a renamed/missing class, method, signature, or field can compile and still fail when JNI resolves it. Any adapter must preserve or deliberately change all of these names/signatures together.

## 2. Vianshield ownership boundary

Keep `com.example.vpn.VianshieldVpnService` as the only Android `VpnService` and the only owner of the TUN descriptor.

TrackerControl's `ServiceSinkhole` must not be copied as a second `VpnService`, registered in the manifest, or started alongside Vianshield's service.

Introduce a plain-Java/Kotlin engine adapter (not an Android service) that owns the JNI context and exposes a narrow lifecycle:

1. initialize native context;
2. start packet worker with the TUN fd;
3. stop packet worker and wait for native exit;
4. clear native context and close owned descriptors;
5. publish packet, DNS, usage, and error callbacks to Vianshield repositories.

The Vianshield service remains responsible for Android VPN permission, foreground notification, TUN establishment, network callbacks, and final descriptor ownership. The adapter must never close the TUN fd unless ownership has explicitly transferred.

## 3. Extraction sequence

### Stage A — isolate the JNI contract

- Extract the four packet/data model classes and the JNI facade with their upstream signatures.
- Decide whether to preserve upstream package names initially (lowest-risk JNI port) or rename all C exports and class lookups to a Vianshield namespace (cleaner, larger change).
- Add compile-time and instrumentation tests for JNI class/method resolution before enabling packet processing.
- Keep the native library unreferenced by the live VPN service until the facade contract passes.

### Stage B — packet engine build

- Add only the NetGuard packet-engine C sources from `app/src/main/jni/netguard/` to Vianshield's CMake target.
- Do not copy TrackerControl's whole application Gradle configuration, app ID, manifest, theme, or activities.
- Do not add the Rust WireGuard bridge to this stage; first prove direct packet forwarding and per-app policy independently.
- Preserve upstream source notices and inventory each native dependency/license before distribution.

### Stage C — service adapter

- Establish the TUN using Vianshield's service.
- Start the native packet worker through the facade.
- Implement the callback policy using Vianshield-owned settings/repositories: per-app allow/block, domain blocking, UID attribution, DNS/resource evidence, and bounded usage logging.
- Make start/stop idempotent and serialize all native lifecycle calls.
- On any initialization, callback, or packet-worker failure, close the TUN and report protection as stopped/error. Never display Connected until the native worker is actually running.

### Stage D — validation before WireGuard

- Verify per-app block means no network access, including IPv4/IPv6 and DNS.
- Verify allowed apps retain direct internet access.
- Verify DNS/domain filtering and app attribution.
- Verify foreground/background transitions, revoke, force-stop/restart, reboot, Wi-Fi/mobile handover, and repeated start/stop.
- Test on physical Android devices; emulator build success is not packet-path validation.

### Stage E — WireGuard and tracker intelligence

Only after Stage D passes, integrate GotaTun's Rust bridge through a separate adapter. Then add tracker signatures/analysis, data and country attribution, and Vianshield-native Tracker UI. Keep filtering decisions and remote VPN routing as independent policy dimensions.

## 4. Current implementation status

- TrackerControl source is pinned as a Git submodule.
- License notice and source audit are present.
- Vianshield's existing VPN service is an engine-neutral fail-closed shell.
- No TrackerControl C library is currently linked into the Vianshield APK.
- No per-app packet policy or DNS filtering is active yet.
- The current build workflow run is still pending; its status must be checked before claiming CI success.

## 5. Do-not-do list

- Do not start TrackerControl's `ServiceSinkhole` beside Vianshield's VPN service.
- Do not copy the upstream app module wholesale.
- Do not add WireGuard before direct packet processing and app blocking are proven.
- Do not report VPN Connected merely because Android granted VPN permission or a TUN fd was created.
- Do not claim operational firewall protection based on native compilation alone.
