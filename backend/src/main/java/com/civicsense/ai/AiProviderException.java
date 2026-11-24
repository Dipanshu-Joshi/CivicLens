package com.civicsense.ai;

/** Provider call failed. Timeout is distinguished so it can be persisted accurately. */
public class AiProviderException extends Exception {

    private final boolean timeout;

    public AiProviderException(String message, boolean timeout) {
        super(message);
        this.timeout = timeout;
    }

    public AiProviderException(String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
