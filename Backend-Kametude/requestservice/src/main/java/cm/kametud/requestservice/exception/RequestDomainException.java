package cm.kametud.requestservice.exception;

import org.springframework.http.HttpStatus;

public class RequestDomainException extends RuntimeException {
    private final HttpStatus status;

    public RequestDomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
