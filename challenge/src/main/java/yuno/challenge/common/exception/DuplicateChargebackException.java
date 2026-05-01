package yuno.challenge.common.exception;

public class DuplicateChargebackException extends RuntimeException {

    public DuplicateChargebackException(String idempotencyKey) {
        super("Duplicate chargeback detected. Idempotency key already processed: " + idempotencyKey);
    }
}