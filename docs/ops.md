# Operations Notes

## Maintenance Rhythm

The server is designed for low-maintenance operation, with routine admin work grouped into a weekly maintenance window.

Weekly checklist:

1. Confirm backups completed successfully.
2. Check disk usage.
3. Review crash reports and recent console errors.
4. Review moderation reports and CoreProtect evidence if needed.
5. Apply tested configuration changes.
6. Update plugins only after reading changelogs and testing when possible.

## Deployment Principle

Configuration should be changed locally, committed to Git, pushed to GitHub, then deployed to the cloud server through a script or controlled workflow.

World data, player data, logs, backups, databases, and secrets are not managed by Git.
