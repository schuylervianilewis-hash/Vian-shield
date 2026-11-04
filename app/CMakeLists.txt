cmake_minimum_required(VERSION 3.22.1)
project(vianshield_netguard C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Build the C packet engine from the pinned TrackerControl submodule without
# importing its Android app module or its ServiceSinkhole VPN service.
set(NETGUARD_SOURCE_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/tracker-control/app/src/main/jni/netguard")

if(NOT EXISTS "${NETGUARD_SOURCE_DIR}/netguard.c")
    message(FATAL_ERROR "TrackerControl submodule missing: ${NETGUARD_SOURCE_DIR}")
endif()

if(NOT DEFINED WGBRIDGE_LIB_DIR)
    message(FATAL_ERROR "WGBRIDGE_LIB_DIR not set; Gradle must run wgbridgeBuild first.")
endif()

add_compile_options("-ffile-prefix-map=${NETGUARD_SOURCE_DIR}=.")

# Vianshield policy-refresh shim for the pinned TrackerControl packet engine.
# A route-table update must also re-check existing TCP/UDP sessions; otherwise
# a connection allowed before the user presses Block can keep using its cached
# per-session decision. This patch is intentionally anchored to the pinned
# upstream source and fails the native build if the expected code moves.
set(NETGUARD_SESSION_SOURCE "${NETGUARD_SOURCE_DIR}/session.c")
file(READ "${NETGUARD_SESSION_SOURCE}" VIAN_SESSION_SOURCE)
set(VIAN_SESSION_INCLUDE_ANCHOR [=[#include "netguard.h"]=])
string(FIND "${VIAN_SESSION_SOURCE}" "${VIAN_SESSION_INCLUDE_ANCHOR}" VIAN_SESSION_INCLUDE_POSITION)
if(VIAN_SESSION_INCLUDE_POSITION EQUAL -1)
    message(FATAL_ERROR "TrackerControl session.c changed: missing policy refresh include anchor")
endif()
set(VIAN_SESSION_INCLUDE_REPLACEMENT [=[#include "netguard.h"
#include <stdatomic.h>

static _Atomic int vianshield_policy_refresh_pending = 0;

void request_policy_refresh(void) {
    atomic_store_explicit(&vianshield_policy_refresh_pending, 1, memory_order_release);
}

static int take_policy_refresh(void) {
    return atomic_exchange_explicit(&vianshield_policy_refresh_pending, 0, memory_order_acq_rel);
}
]=])
string(REPLACE "${VIAN_SESSION_INCLUDE_ANCHOR}" "${VIAN_SESSION_INCLUDE_REPLACEMENT}"
       VIAN_SESSION_SOURCE "${VIAN_SESSION_SOURCE}")

set(VIAN_SESSION_REFRESH_ANCHOR [=[            time_t now = time(NULL);]=])
string(FIND "${VIAN_SESSION_SOURCE}" "${VIAN_SESSION_REFRESH_ANCHOR}" VIAN_SESSION_REFRESH_POSITION)
if(VIAN_SESSION_REFRESH_POSITION EQUAL -1)
    message(FATAL_ERROR "TrackerControl session.c changed: missing session-policy refresh point")
endif()
set(VIAN_SESSION_REFRESH_REPLACEMENT [=[            // Run on the packet-loop thread while ctx->lock is held.
            // This rechecks existing sessions against the latest per-app rules.
            if (take_policy_refresh())
                check_allowed(args);

            time_t now = time(NULL);]=])
string(REPLACE "${VIAN_SESSION_REFRESH_ANCHOR}" "${VIAN_SESSION_REFRESH_REPLACEMENT}"
       VIAN_SESSION_SOURCE "${VIAN_SESSION_SOURCE}")
set(VIAN_PATCHED_SESSION_SOURCE "${CMAKE_CURRENT_BINARY_DIR}/vianshield_session.c")
file(WRITE "${VIAN_PATCHED_SESSION_SOURCE}" "${VIAN_SESSION_SOURCE}")

set(NETGUARD_CORE_SOURCE "${NETGUARD_SOURCE_DIR}/netguard.c")
file(READ "${NETGUARD_CORE_SOURCE}" VIAN_NETGUARD_SOURCE)
set(VIAN_NETGUARD_INCLUDE_ANCHOR [=[#include "netguard.h"]=])
string(FIND "${VIAN_NETGUARD_SOURCE}" "${VIAN_NETGUARD_INCLUDE_ANCHOR}" VIAN_NETGUARD_INCLUDE_POSITION)
if(VIAN_NETGUARD_INCLUDE_POSITION EQUAL -1)
    message(FATAL_ERROR "TrackerControl netguard.c changed: missing policy refresh declaration anchor")
endif()
set(VIAN_NETGUARD_INCLUDE_REPLACEMENT [=[#include "netguard.h"
void request_policy_refresh(void);]=])
string(REPLACE "${VIAN_NETGUARD_INCLUDE_ANCHOR}" "${VIAN_NETGUARD_INCLUDE_REPLACEMENT}"
       VIAN_NETGUARD_SOURCE "${VIAN_NETGUARD_SOURCE}")

set(VIAN_ROUTE_UPDATE_ANCHOR [=[    set_route_uids(uids, count, default_tunnel ? 1 : 0, dns_direct ? 1 : 0);]=])
string(FIND "${VIAN_NETGUARD_SOURCE}" "${VIAN_ROUTE_UPDATE_ANCHOR}" VIAN_ROUTE_UPDATE_POSITION)
if(VIAN_ROUTE_UPDATE_POSITION EQUAL -1)
    message(FATAL_ERROR "TrackerControl netguard.c changed: missing native route update anchor")
endif()
set(VIAN_ROUTE_UPDATE_REPLACEMENT [=[    set_route_uids(uids, count, default_tunnel ? 1 : 0, dns_direct ? 1 : 0);
    request_policy_refresh();]=])
string(REPLACE "${VIAN_ROUTE_UPDATE_ANCHOR}" "${VIAN_ROUTE_UPDATE_REPLACEMENT}"
       VIAN_NETGUARD_SOURCE "${VIAN_NETGUARD_SOURCE}")
set(VIAN_PATCHED_NETGUARD_SOURCE "${CMAKE_CURRENT_BINARY_DIR}/vianshield_netguard.c")
file(WRITE "${VIAN_PATCHED_NETGUARD_SOURCE}" "${VIAN_NETGUARD_SOURCE}")

add_library(netguard SHARED
    "${VIAN_PATCHED_NETGUARD_SOURCE}"
    "${VIAN_PATCHED_SESSION_SOURCE}"
    "${NETGUARD_SOURCE_DIR}/ip.c"
    "${NETGUARD_SOURCE_DIR}/ip6_ext.c"
    "${NETGUARD_SOURCE_DIR}/policy.c"
    "${NETGUARD_SOURCE_DIR}/tls.c"
    "${NETGUARD_SOURCE_DIR}/tcp.c"
    "${NETGUARD_SOURCE_DIR}/dns_frame.c"
    "${NETGUARD_SOURCE_DIR}/udp.c"
    "${NETGUARD_SOURCE_DIR}/icmp.c"
    "${NETGUARD_SOURCE_DIR}/dns.c"
    "${NETGUARD_SOURCE_DIR}/pcap.c"
    "${NETGUARD_SOURCE_DIR}/util.c"
)

target_include_directories(netguard PRIVATE "${NETGUARD_SOURCE_DIR}")

find_library(log-lib log)

# dns.c calls tcdns_* directly. Those C ABI symbols are exported by the
# pinned Rust wgbridge cdylib (wgbridge-rs/src/tcdns_capi.rs).
add_library(wgbridge SHARED IMPORTED)
set_target_properties(wgbridge PROPERTIES
    IMPORTED_LOCATION "${WGBRIDGE_LIB_DIR}/${ANDROID_ABI}/libwgbridge.so"
    IMPORTED_NO_SONAME TRUE
)

target_link_libraries(netguard PRIVATE ${log-lib} wgbridge)

target_link_options(netguard PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
    "-Wl,--build-id=none"
)

add_custom_command(TARGET netguard POST_BUILD
    COMMAND ${CMAKE_OBJCOPY} --remove-section .comment
            --remove-section .note.gnu.build-id
            $<TARGET_FILE:netguard>
    VERBATIM
)
