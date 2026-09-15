package org.apache.kafka.connect.runtime;

class WorkerSourceTaskTest {
    void testCommitRecordFailure() {
        new WorkerSourceTask().execute();
    }
}
