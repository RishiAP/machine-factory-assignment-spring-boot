# Benchmark

## Environment
- **CPU**: Intel® Core™ i3-1115G4 (2 cores / 4 threads, up to 4.10 GHz)
  - **Base frequency**: 3.00 GHz
  - **Configurable TDP-up base frequency**: 3.00 GHz
  - **Configurable TDP-down base frequency**: 1.70 GHz
- **RAM**: 16 GB DDR4-3200

## Command
```
./mvnw test -Dtest=MachineEventServiceTest#testPerformance1000EventsUnder1Second
```

## Result
- **Batch size**: 1000 events
- **Time taken**: **424 ms**

## Notes
- Benchmark uses the same PostgreSQL configuration as the application.
- The benchmark is executed inside the JUnit test:
  - [MachineEventServiceTest](src/test/java/com/example/machine_factory/service/MachineEventServiceTest.java#L387)
