## AMQP contract

- work-inbound — produced tasks (Producer → Worker)
- work-outbound — processed / discarded / dead-letter tasks (Worker → Producer/Audit)
- certified-result — certified tasks (Producer → Audit)

### ⚠️ Dead-letter convention

If a message fails processing and is dead-lettered, it is re-published to the `work-outbound` exchange with routing key:

`task.produced.<color>`

In the context of the Audit service, such messages are treated as DISCARDED

# Scenarios Reproducing

## Scenario 3

_What are the results when you change the deployment setup to: 3
instances of A, 3 instance of B, 1 instance of C
AND when the processing time on B is changed to 8 seconds?_

#### Reproducing

1. Change in Worker application.yml setting `worker.handling-ms: 8000` and `worker.fail-every-second-task: false`
2. Clean & Build in gradle
3. Kill all workers
```bash
pkill -f worker-0.0.1-SNAPSHOT.jar
```

4. Up 3 workers:
```bash
for p in 8020 8021 8022; do java -jar build/libs/worker-0.0.1-SNAPSHOT.jar --server.port=$p > worker-$p.log 2>&1 & done
```

## Scenario 4

_1. Create modified version of B that immediately throws and error on
every second task it receives._

_2. Ensure that all of the tasks are processed by Workers, including those
that initially failed (you can increase the number of deployed Workers
if needed)_

#### Reproducing

1. Change in Worker application.yml setting `worker.handling-ms: 8000` and `worker.fail-every-second-task: true`
2. Clean & Build in gradle
3. Kill all workers
```bash
pkill -f worker-0.0.1-SNAPSHOT.jar
```

4. Up 3 workers:
```bash
for p in 8021 8022 8023; do java -jar build/libs/worker-0.0.1-SNAPSHOT.jar --server.port=$p > worker-$p.log 2>&1 & done
```

