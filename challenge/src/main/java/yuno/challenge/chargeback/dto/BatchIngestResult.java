package yuno.challenge.chargeback.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BatchIngestResult {

    private final int total;
    private final int succeeded;
    private final int failed;
    private final int duplicates;
    private final List<String> successfulIds;
    private final List<IngestError> errors;

    @Getter
    @Builder
    public static class IngestError {
        private final int rowIndex;
        private final String rawId;
        private final String errorMessage;
        private final String errorCode;
    }
}