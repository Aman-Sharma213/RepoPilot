package org.apache.kafka.connect.runtime;

class WorkerSourceTask {
    private void commitTaskRecord(SourceRecord record) {
        // PR 1402 changed the exception boundary around this callback.
    }

    private void sendRecords() {
        commitTaskRecord(new SourceRecord());
    }

    void execute() {
        sendRecords();
    }
}
