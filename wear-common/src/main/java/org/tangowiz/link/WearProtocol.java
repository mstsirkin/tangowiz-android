package org.tangowiz.link;

/** Additive protocol-1 features; absent advertisements mean legacy protocol 1. */
public final class WearProtocol {
    public static final int VERSION = 1;
    public static final String DJ_BOOST = "dj-popularity-v1";
    public static final String ORCHESTRA_KEYWORD = "orchestra-keyword-v1";
    public static int negotiate(int[] peerVersions) {
        if (peerVersions == null) return VERSION;
        for (int version : peerVersions) if (version == VERSION) return VERSION;
        return 0;
    }
    public static boolean compatible(int wireVersion, int[] peerVersions) {
        return wireVersion == VERSION && negotiate(peerVersions) == VERSION;
    }
    private WearProtocol() {}
}
