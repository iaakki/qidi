package app.qidi;

// Runs inside a Shizuku UserService process as uid shell; see QidiShellService.
interface IQidiShell {
    // Transaction code reserved by the Shizuku server for tearing down a user service.
    void destroy() = 16777114;

    // Returns [exitCode, stdout, stderr].
    String[] exec(String command) = 1;
}
