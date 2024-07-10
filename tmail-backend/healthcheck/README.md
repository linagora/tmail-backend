# Twake Mail Healthcheck

This module gathers the custom healthcheck for Twake Mail.

## Tasks healthcheck

The goal is to check if wanted tasks are executed well within the required time range:
- If all tasks are executed in time, return Healthy
- If some tasks are executed in time, return Degraded
- If no task is finished in time, return Unhealthy

To use this tasks healthcheck, please declare in `healthcheck.properties`:
```properties
# Map of task name to its required execution duration. 
# The delimiter between map entries is "," while between the mapping is ":".
# Default to empty map (no check/always healthy).
healthcheck.tasks.execution=TaskAName:2day,TaskBName:2month,taskCName:2week
```
