# DutchTrains Agent Notes

- Do not run build or test commands directly inside the container.
- Use the manual execution flow instead:
  - Put the host-side command in `/tmp/dev-container/manual-execution.sh`.
  - Run `dev-container-exec` on the host.
  - Check output in `/tmp/dev-container/manual-execution.log`.
