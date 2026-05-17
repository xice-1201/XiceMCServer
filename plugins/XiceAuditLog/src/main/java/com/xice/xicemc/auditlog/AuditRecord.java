package com.xice.xicemc.auditlog;

public record AuditRecord(
        long createdAt,
        String action,
        String playerUuid,
        String playerName,
        String world,
        int x,
        int y,
        int z,
        String targetType,
        String itemType,
        int itemAmount,
        String details
) {
}
