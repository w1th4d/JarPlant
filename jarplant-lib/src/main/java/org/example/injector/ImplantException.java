package org.example.injector;

public class ImplantException extends Exception {
    public ImplantException() {
        super();
    }

    public ImplantException(String message) {
        super(message);
    }

    public ImplantException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImplantException(Throwable cause) {
        super(cause);
    }
}
