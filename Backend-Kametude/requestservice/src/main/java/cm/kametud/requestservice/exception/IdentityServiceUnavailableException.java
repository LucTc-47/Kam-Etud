package cm.kametud.requestservice.exception;

import org.springframework.http.HttpStatus;

public class IdentityServiceUnavailableException extends RequestDomainException {
    public IdentityServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public IdentityServiceUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
        initCause(cause);
    }
}
