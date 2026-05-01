package yuno.challenge.common.exception;

public class ChargebackNotFoundException extends RuntimeException {

    public ChargebackNotFoundException(String chargebackId) {
        super("Chargeback not found: " + chargebackId);
    }
}
