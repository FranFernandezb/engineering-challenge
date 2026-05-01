package yuno.challenge.common.exception;

public class InvalidProcessorException extends RuntimeException {

    public InvalidProcessorException(String processorName) {
        super("Unknown or unsupported processor: " + processorName);
    }
}
