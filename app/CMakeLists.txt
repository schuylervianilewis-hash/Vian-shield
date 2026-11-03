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

add_library(netguard SHARED
    "${NETGUARD_SOURCE_DIR}/netguard.c"
    "${NETGUARD_SOURCE_DIR}/session.c"
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
