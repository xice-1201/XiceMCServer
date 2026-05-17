package com.xice.xicemc.auditlog;

public interface AuditStorage {
    void start() throws Exception;

    void enqueue(AuditRecord record);

    void stop();
}
