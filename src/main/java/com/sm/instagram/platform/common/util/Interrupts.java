package com.sm.instagram.platform.common.util;

/**
 * One implementation of the re-interrupt contract, instead of six copies of it.
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
 */
public final class Interrupts {

    private Interrupts() {
    }

    /**
     * Restores this thread's interrupt flag when {@code caught} is the interrupt itself.
     *
     * @param caught the exception a broad catch block received; null is tolerated
     * @return true when the flag was restored, so a caller can log the distinction if it wants to
     */
    public static boolean preserveInterrupt(Throwable caught) {
        if (caught instanceof InterruptedException || caught instanceof java.io.InterruptedIOException) {
            Thread.currentThread().interrupt();
            return true;
        }
        return false;
    }
}
