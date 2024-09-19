package io.github.w1th4d.jarplant.implants.utils;

public class DecoderRuntimeException extends RuntimeException {
    public DecoderRuntimeException() {
    }

    public DecoderRuntimeException(String message) {
        super(message);
    }

    public DecoderRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public DecoderRuntimeException(Throwable cause) {
        super(cause);
    }
}
