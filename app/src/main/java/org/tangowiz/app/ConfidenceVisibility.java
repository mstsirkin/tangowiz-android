package org.tangowiz.app;

/** Shared presentation and history gate, expressed in displayed percent. */
public final class ConfidenceVisibility {
    public static boolean visible(int percent) { return percent >= 5; }
}
