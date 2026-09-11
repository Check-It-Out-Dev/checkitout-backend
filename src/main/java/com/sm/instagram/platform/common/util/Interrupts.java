package com.sm.instagram.platform.common.util;

/**
 * One answer to "was that exception this thread's interrupt?", instead of six copies of it.
 *
 * <p>A {@code catch (Exception e)} around blocking work swallows {@link InterruptedException} along
 * with everything else. Catching it clears the thread's interrupt flag, so a pooled thread that was
 * asked to stop carries on as if nothing had happened, and the shutdown that asked never arrives.
 * The contract is: if the exception you caught <em>is</em> the interrupt, put the flag back before
 * you go on handling the failure (sonar java:S2142).
 *
 * <p>Deliberately narrow. An {@code ExecutionException} whose cause is an InterruptedException means
 * some <em>other</em> thread was interrupted while running the task; this thread was not, and setting
 * its flag would be a lie that aborts unrelated work later. Only an interrupt delivered to this
 * thread counts, and the JDK spells that two ways: {@link InterruptedException} and
 * {@link java.io.InterruptedIOException} (thrown by blocking IO on some implementations).
 *
 * <p><strong>Why this only answers the question and does not act on it.</strong> An earlier version
 * restored the flag itself, which reads better and is exactly as correct -- and left every one of
 * its twenty call sites still reported as swallowing the interrupt. SonarJava's check walks up to
 * three frames into a called method looking for {@code Thread.interrupt()}, but
 * {@code methodSymbol().declaration()} only resolves for a method declared in the file being
 * analysed. A helper in another file is a black box to it, so the flag has to be restored where the
 * exception is caught. The predicate is shared because deciding what counts as an interrupt is the
 * part worth writing once; the one line that acts on it belongs in view of the catch block anyway.
 */
public final class Interrupts {

    private Interrupts() {
    }

    /**
     * Whether {@code caught} is an interrupt delivered to <em>this</em> thread, in which case the
     * caller must restore the flag: {@code Thread.currentThread().interrupt();}.
     *
     * @param caught the exception a broad catch block received; null is tolerated
     * @return true when the caught exception is this thread's interrupt
     */
    public static boolean isInterrupt(Throwable caught) {
        return caught instanceof InterruptedException || caught instanceof java.io.InterruptedIOException;
    }
}
