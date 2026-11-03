/*
 * Vianshield JNI compatibility facade for the TrackerControl/NetGuard packet
 * engine. This is deliberately a plain Java object, NOT an Android Service or
 * VpnService. VianshieldVpnService remains the sole owner of the TUN.
 *
 * The package, class, method and model names below form a native ABI: the
 * upstream C engine resolves them by JNI symbol and GetMethodID/GetFieldID.
 * Preserve them unless the native side is migrated in the same change.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package eu.faircode.netguard;

import java.util.Objects;

/**
 * Plain-object JNI facade required by the imported NetGuard packet engine.
 *
 * The TUN descriptor passed to runTunnel is borrowed. This class never closes
 * it. The caller (VianshieldVpnService) owns and closes its ParcelFileDescriptor.
 */
public final class ServiceSinkhole {
    static {
        // libnetguard has a DT_NEEDED dependency on libwgbridge for the
        // tcdns_* C ABI. Android's linker loads that dependency first.
        System.loadLibrary("netguard");
    }

    public interface Callbacks {
        void onNativeExit(int error, String reason);
        void onNativeError(int error, String message);
        boolean protect(int socketFd);
        void logPacket(Packet packet);
        void dnsResolved(ResourceRecord record);
        boolean isDomainBlocked(String domain);
        int getUidQ(int version, int protocol, String sourceAddress, int sourcePort,
                    String destinationAddress, int destinationPort);
        Allowed isAddressAllowed(Packet packet);
        void accountUsage(Usage usage);
    }

    private final Callbacks callbacks;
    private long nativeContext;
    private Thread tunnelThread;
    private int logLevel = android.util.Log.WARN;
    private boolean tcpMssClamp;

    public ServiceSinkhole(Callbacks callbacks) {
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    /**
     * Allocates the native context and configures the native packet engine.
     * This does not establish a VPN or start packet processing.
     */
    public synchronized void initialize(int sdk, int logLevel, boolean tcpMssClamp) {
        if (nativeContext != 0)
            throw new IllegalStateException("Native packet engine is already initialized");

        long context = jni_init(sdk);
        if (context == 0)
            throw new IllegalStateException("Native packet engine returned a null context");

        nativeContext = context;
        try {
            this.logLevel = logLevel;
            this.tcpMssClamp = tcpMssClamp;
        } catch (RuntimeException | Error failure) {
            nativeContext = 0;
            jni_done(context);
            throw failure;
        }
    }

    /**
     * Starts the blocking native packet loop on a dedicated worker thread.
     * The TUN fd is borrowed and must remain open until stopTunnel returns.
     */
    public synchronized void runTunnel(int tunFd, boolean forwardDns53, int blockedDnsRcode) {
        if (nativeContext == 0)
            throw new IllegalStateException("Native packet engine is not initialized");
        if (tunnelThread != null && tunnelThread.isAlive())
            throw new IllegalStateException("Native packet loop is already running");

        if (tunnelThread != null) {
            // The upstream service clears stale sessions after a native loop
            // exits unexpectedly before starting a replacement loop.
            jni_clear(nativeContext);
            tunnelThread = null;
        }

        // Upstream calls jni_start before every jni_run, including recovery
        // after a prior run has returned and its session list was cleared.
        jni_start(nativeContext, logLevel, tcpMssClamp);
        final long context = nativeContext;
        Thread worker = new Thread(() -> {
            try {
                jni_run(context, tunFd, forwardDns53, blockedDnsRcode);
            } catch (Throwable failure) {
                callbacks.onNativeError(-1, "JNI packet loop failed: " + failure);
            }
        }, "Vianshield-NetGuard-PacketLoop");
        tunnelThread = worker;
        worker.start();
    }

    /**
     * Stops the native loop, waits until it no longer touches the TUN fd, then
     * clears its sessions. Safe to call when the loop has already exited.
     */
    public void stopTunnel() {
        final Thread worker;
        final long context;
        synchronized (this) {
            context = nativeContext;
            worker = tunnelThread;
            if (context == 0)
                return;
            if (worker != null && worker.isAlive())
                jni_stop(context);
        }

        boolean interrupted = false;
        if (worker != null) {
            while (worker.isAlive()) {
                try {
                    worker.join();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        }

        synchronized (this) {
            if (nativeContext == context) {
                jni_clear(context);
                if (tunnelThread == worker)
                    tunnelThread = null;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    /** Releases the native context. Call only after stopTunnel has returned. */
    public void close() {
        stopTunnel();
        synchronized (this) {
            if (nativeContext == 0)
                return;
            long context = nativeContext;
            nativeContext = 0;
            jni_done(context);
            tunnelThread = null;
        }
    }

    public synchronized int[] getStats() {
        if (nativeContext == 0)
            return new int[5];
        return jni_get_stats(nativeContext);
    }

    public int getMtu() {
        return jni_get_mtu();
    }

    public void setSniEnabled(boolean enabled) {
        jni_sni(enabled);
    }

    // Callback entry points. Names and descriptors must match netguard.c.
    public final void nativeExit(int error, String reason) {
        callbacks.onNativeExit(error, reason);
    }

    public final void nativeError(int error, String message) {
        callbacks.onNativeError(error, message);
    }

    public final boolean protect(int socketFd) {
        return callbacks.protect(socketFd);
    }

    public final void logPacket(Packet packet) {
        callbacks.logPacket(packet);
    }

    public final void dnsResolved(ResourceRecord record) {
        callbacks.dnsResolved(record);
    }

    public final boolean isDomainBlocked(String domain) {
        return callbacks.isDomainBlocked(domain);
    }

    public final int getUidQ(int version, int protocol, String sourceAddress, int sourcePort,
                             String destinationAddress, int destinationPort) {
        return callbacks.getUidQ(version, protocol, sourceAddress, sourcePort,
                destinationAddress, destinationPort);
    }

    public final Allowed isAddressAllowed(Packet packet) {
        return callbacks.isAddressAllowed(packet);
    }

    public final void accountUsage(Usage usage) {
        callbacks.accountUsage(usage);
    }

    private native long jni_init(int sdk);
    private native void jni_start(long context, int logLevel, boolean tcpMssClamp);
    private native void jni_run(long context, int tun, boolean forwardDns53, int blockedDnsRcode);
    private native void jni_stop(long context);
    private native void jni_clear(long context);
    private native int jni_get_mtu();
    private native int[] jni_get_stats(long context);
    private static native void jni_pcap(String name, int recordSize, int fileSize);
    private native void jni_socks5(String address, int port, String username, String password);
    private native void jni_sni(boolean enabled);
    private native int jni_wireguard_start();
    private native void jni_wireguard_stop();
    private native void jni_wireguard_required(boolean required);
    private native void jni_wireguard_route(int[] overrideUids, boolean defaultTunnel, boolean dnsDirect);
    private native void jni_done(long context);
}
