package io.github.w1th4d.jarplant.implants;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public class RevShellImplantTests {
    @Test
    public void testHandleCommunications_HappyCase_Fine() throws IOException, InterruptedException {
        // Arrange
        PipedOutputStream sendFromFakeShell = new PipedOutputStream();
        PipedInputStream recvFromFakeShell = new PipedInputStream();
        PipedOutputStream sendFromFakeSocket = new PipedOutputStream();
        PipedInputStream recvFromFakeSocket = new PipedInputStream();

        PipedInputStream fromShell = new PipedInputStream(sendFromFakeShell);
        PipedOutputStream toShell = new PipedOutputStream(recvFromFakeShell);
        PipedInputStream fromSocket = new PipedInputStream(sendFromFakeSocket);
        PipedOutputStream toSocket = new PipedOutputStream(recvFromFakeSocket);

        Socket dummySocket = new Socket();

        // Act
        Thread comms = new Thread(() -> {
            try {
                RevShellImplant.handleCommunications(fromShell, toShell, fromSocket, toSocket, dummySocket); // Blocking call.
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        comms.start();

        // Assert
        // Simulate shell outputting something.
        byte[] shellGreeting = "This is FakeShell!\n".getBytes(StandardCharsets.UTF_8);
        sendFromFakeShell.write(shellGreeting);
        Assert.assertArrayEquals(shellGreeting, recvFromFakeSocket.readNBytes(shellGreeting.length));

        // Simulate socket sending a command.
        byte[] command = "whoami\n".getBytes(StandardCharsets.UTF_8);
        sendFromFakeSocket.write(command);
        Assert.assertArrayEquals(command, recvFromFakeShell.readNBytes(command.length));

        // Simulate receiving the result of the command.
        byte[] commandRet = "root\n".getBytes(StandardCharsets.UTF_8);
        sendFromFakeShell.write(commandRet);
        Assert.assertArrayEquals(commandRet, recvFromFakeSocket.readNBytes(commandRet.length));

        // Simulate disconnection.
        sendFromFakeSocket.close();
        recvFromFakeSocket.close();
        comms.join(Duration.ofSeconds(10).toMillis());   // Should return fast, but don't wait for too long.
        Assert.assertFalse(comms.isAlive());
    }

    @Test
    public void testFindUnixShellExecutable_RichLinuxSystem_FoundBash() throws IOException {
        // Arrange
        FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix());

        Path shPath = jimfs.getPath("/bin/sh");
        Files.createDirectories(shPath.getParent());
        Files.createFile(shPath);

        Path bashPath = jimfs.getPath("/bin/bash");
        Files.createDirectories(bashPath.getParent());
        Files.createFile(bashPath);

        // Act
        Optional<Path> bashExecutable = RevShellImplant.findUnixShellExecutable(jimfs);

        // Assert
        Assert.assertTrue(bashExecutable.isPresent());
        Assert.assertEquals(
                "/bin/bash",
                bashExecutable.get().toString()
        );
    }

    @Test
    public void testFindUnixShellExecutable_MinimalLinuxSystem_FoundSh() throws IOException {
        // Arrange
        FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix());

        Path shPath = jimfs.getPath("/bin/sh");
        Files.createDirectories(shPath.getParent());
        Files.createFile(shPath);

        // Act
        Optional<Path> bashExecutable = RevShellImplant.findUnixShellExecutable(jimfs);

        // Assert
        Assert.assertTrue(bashExecutable.isPresent());
        Assert.assertEquals(
                "/bin/sh",
                bashExecutable.get().toString()
        );
    }

    @Test
    public void testFindUnixShellExecutable_TypicalMacOsSystem_FoundBash() throws IOException {
        // Arrange
        FileSystem jimfs = Jimfs.newFileSystem(Configuration.osX());

        // All three exist on a typical macOS installation.
        Path bashPath = jimfs.getPath("/bin/bash");
        Files.createDirectories(bashPath.getParent());
        Files.createFile(bashPath);

        Path zshPath = jimfs.getPath("/bin/zsh");
        Files.createDirectories(zshPath.getParent());
        Files.createFile(zshPath);

        Path shPath = jimfs.getPath("/bin/sh");
        Files.createDirectories(shPath.getParent());
        Files.createFile(shPath);

        // Act
        Optional<Path> bashExecutable = RevShellImplant.findUnixShellExecutable(jimfs);

        // Assert
        Assert.assertTrue(bashExecutable.isPresent());
        Assert.assertEquals(
                // Even as zsh is the default shell for macOS, expect bash to be found first.
                "/bin/bash",
                bashExecutable.get().toString()
        );
    }

    @Test
    public void testFindWindowsShellExecutable_TypicalWin11System_FoundPowershell() throws IOException {
        // Arrange
        FileSystem jimfs = Jimfs.newFileSystem(Configuration.windows());
        String pathEnvVar = "C:\\Program Files\\Common Files\\Oracle\\Java\\javapath;C:\\Windows\\system32;C:\\Windows;C:\\Windows\\System32\\Wbem;C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\;C:\\Windows\\System32\\OpenSSH\\;C:\\Program Files\\Microsoft SQL Server\\150\\Tools\\Binn\\;C:\\Program Files\\Microsoft SQL Server\\Client SDK\\ODBC\\170\\Tools\\Binn\\;C:\\Program Files\\dotnet\\;C:\\Program Files\\Git\\cmd;C:\\Users\\User\\AppData\\Local\\Microsoft\\WindowsApps;C:\\Users\\User\\.dotnet\\tools"
                .replace("\\", "/");    // Because Jimfs does not support Windows-style path separators.
        for (String path : pathEnvVar.split(";")) {
            Files.createDirectories(jimfs.getPath(path));
        }
        Files.createFile(jimfs.getPath("C:/Windows/system32/cmd.exe"));
        Files.createFile(jimfs.getPath("C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe"));

        // Act
        Optional<Path> shellExecutable = RevShellImplant.findWindowsShellExecutable(jimfs, pathEnvVar);

        // Assert
        Assert.assertTrue(shellExecutable.isPresent());
        Assert.assertEquals(
                "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe",
                shellExecutable.get().toString()
        );
    }

    @Test
    public void testFindWindowsShellExecutable_NoPowerShellWindows_FoundCmdFallback() throws IOException {
        // Arrange
        FileSystem jimfs = Jimfs.newFileSystem(Configuration.windows());
        String pathEnvVar = "C:\\Program Files\\Common Files\\Oracle\\Java\\javapath;C:\\Windows\\system32;C:\\Windows;C:\\Windows\\System32\\Wbem;C:\\Windows\\System32\\OpenSSH\\;C:\\Program Files\\Microsoft SQL Server\\150\\Tools\\Binn\\;C:\\Program Files\\Microsoft SQL Server\\Client SDK\\ODBC\\170\\Tools\\Binn\\;C:\\Program Files\\dotnet\\;C:\\Program Files\\Git\\cmd;C:\\Users\\User\\AppData\\Local\\Microsoft\\WindowsApps;C:\\Users\\User\\.dotnet\\tools"
                .replace("\\", "/");    // Because Jimfs does not support Windows-style path separators.
        for (String path : pathEnvVar.split(";")) {
            Files.createDirectories(jimfs.getPath(path));
        }
        Files.createFile(jimfs.getPath("C:/Windows/system32/cmd.exe"));

        // Act
        Optional<Path> shellExecutable = RevShellImplant.findWindowsShellExecutable(jimfs, pathEnvVar);

        // Assert
        Assert.assertTrue(shellExecutable.isPresent());
        Assert.assertEquals(
                "C:\\Windows\\system32\\cmd.exe",
                shellExecutable.get().toString()
        );
    }
}
