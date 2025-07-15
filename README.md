# Payroll Desktop

This JavaFX application manages payroll-related operations.

## Building

Run `gradle build` to compile the project and produce runnable jars in `build/libs`.

## Creating an Installer

A Gradle task `packageExe` is provided to create a Windows installer using
[`jpackage`](https://docs.oracle.com/javase/10/tools/jpackage.htm). The task
requires running on a Windows machine with JDK 21+ installed.

```bash
gradle packageExe
```

The installer will be created in `build/installer`.

