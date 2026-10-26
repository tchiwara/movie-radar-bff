package dev.tchiwara.movieradar.error;

public class UpstreamException extends RuntimeException {
    private final boolean timeout;
    private final int status;
    private final String failureKind;
    private final String upstreamType;
    private final String upstreamMessage;
    public UpstreamException(String message, boolean timeout) {
        this(message, timeout, 0, timeout ? "timeout" : "error-status", null, null, null);
    }
    public UpstreamException(String message, boolean timeout, int status, String failureKind, Throwable cause) {
        this(message, timeout, status, failureKind, cause, null, null);
    }
    public UpstreamException(String message, boolean timeout, int status, String failureKind, Throwable cause,
                             String upstreamType, String upstreamMessage) {
        super(message, cause);
        this.timeout = timeout;
        this.status = status;
        this.failureKind = failureKind;
        this.upstreamType = upstreamType;
        this.upstreamMessage = upstreamMessage;
    }
    public boolean isTimeout() { return timeout; }
    public int status() { return status; }
    public String failureKind() { return failureKind; }
    public String upstreamType() { return upstreamType; }
    public String upstreamMessage() { return upstreamMessage; }
}
