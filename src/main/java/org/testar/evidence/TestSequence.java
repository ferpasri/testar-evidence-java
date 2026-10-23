package org.testar.evidence;

import java.math.BigInteger;

public record TestSequence(String id, String testRun, BigInteger sequenceIndex, String sourceSequence, Properties properties) {

    public TestSequence {
        Validation.text(id, "Test sequence ID");
        Validation.text(testRun, "Test run ID");
        Validation.unsigned(sequenceIndex, "Sequence index");
        Validation.require(sequenceIndex.signum() > 0, "invalid_record", "Sequence index must be positive");
        Validation.text(sourceSequence, "Source sequence");
        Validation.present(properties, "Properties");
    }
    public TestSequence(String id, String testRun, long sequenceIndex, String sourceSequence, Properties properties) {
        this(id, testRun, BigInteger.valueOf(sequenceIndex), sourceSequence, properties);
    }
}
