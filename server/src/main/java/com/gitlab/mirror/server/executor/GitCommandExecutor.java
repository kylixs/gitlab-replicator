package com.gitlab.mirror.server.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Git Command Executor
 * <p>
 * Executes Git commands via shell script to avoid creating multiple processes
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class GitCommandExecutor {

    // Timeout constants for different git operations
    private static final int REMOTE_SHA_TIMEOUT_SECONDS = 30;    // ls-remote - quick network call
    private static final int LOCAL_SHA_TIMEOUT_SECONDS = 10;     // local git operations
    private static final int CHECK_CHANGES_TIMEOUT_SECONDS = 60; // ls-remote + local SHA
    private static final int SYNC_TIMEOUT_SECONDS = 300;         // git push/pull - 5 minutes
    private static final int CLONE_TIMEOUT_SECONDS = 1800;       // git clone - 30 minutes
    private static final int VERIFY_TIMEOUT_SECONDS = 120;       // git fsck - 2 minutes
    private static final int CLEANUP_TIMEOUT_SECONDS = 300;      // git gc - 5 minutes

    private static final String SCRIPT_NAME = "git-sync.sh";

    private final String scriptPath;

    public GitCommandExecutor() throws IOException {
        this.scriptPath = extractScript();
    }

    /**
     * Git command execution result
     */
    public static class GitResult {
        private final boolean success;
        private final String output;
        private final String error;
        private final int exitCode;
        public final Map<String, String> parsedData;

        public GitResult(boolean success, String output, String error, int exitCode) {
            this.success = success;
            this.output = output;
            this.error = error;
            this.exitCode = exitCode;
            this.parsedData = parseOutput(output);
        }

        private Map<String, String> parseOutput(String output) {
            Map<String, String> data = new HashMap<>();
            if (output != null) {
                String[] lines = output.split("\n");
                for (String line : lines) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            data.put(parts[0].trim(), parts[1].trim());
                        }
                    }
                }
            }
            return data;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getOutput() {
            return output;
        }

        public String getError() {
            return error;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getParsedValue(String key) {
            return parsedData.get(key);
        }

        public boolean hasChanges() {
            return "true".equalsIgnoreCase(parsedData.get("HAS_CHANGES"));
        }
    }

    /**
     * Extract shell script from resources to temp directory
     */
    private String extractScript() throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/" + SCRIPT_NAME);
        Path tempScript = Files.createTempFile("git-sync-", ".sh");

        Files.copy(resource.getInputStream(), tempScript, StandardCopyOption.REPLACE_EXISTING);

        // Make executable
        tempScript.toFile().setExecutable(true);

        log.info("Extracted git-sync script to: {}", tempScript);

        // Delete on exit
        tempScript.toFile().deleteOnExit();

        return tempScript.toString();
    }

    /**
     * Clone repository with --mirror flag
     *
     * @param sourceUrl Source repository URL (with token embedded)
     * @param localPath Local path to clone to
     * @return Execution result
     */
    public GitResult cloneMirror(String sourceUrl, String localPath) {
        log.info("Cloning mirror repository to {}", localPath);

        return executeScript("clone-mirror", CLONE_TIMEOUT_SECONDS, sourceUrl, localPath);
    }

    /**
     * Check if remote has changes compared to local
     *
     * @param remoteUrl Remote repository URL
     * @param localPath Local repository path
     * @return Execution result with HAS_CHANGES, REMOTE_SHA, LOCAL_SHA
     */
    public GitResult checkChanges(String remoteUrl, String localPath) {
        log.debug("Checking for changes: {}", localPath);

        return executeScript("check-changes", CHECK_CHANGES_TIMEOUT_SECONDS, remoteUrl, localPath);
    }

    /**
     * Perform incremental sync (update + push)
     *
     * @param sourceUrl Source repository URL
     * @param targetUrl Target repository URL
     * @param localPath Local repository path
     * @return Execution result
     */
    public GitResult syncIncremental(String sourceUrl, String targetUrl, String localPath) {
        log.info("Performing incremental sync at {}", localPath);

        return executeScript("sync-incremental", SYNC_TIMEOUT_SECONDS,
            sourceUrl, targetUrl, localPath);
    }

    /**
     * Perform first sync (clone + push)
     *
     * @param sourceUrl Source repository URL
     * @param targetUrl Target repository URL
     * @param localPath Local repository path
     * @return Execution result
     */
    public GitResult syncFirst(String sourceUrl, String targetUrl, String localPath) {
        log.info("Performing first sync to {}", localPath);

        return executeScript("sync-first", CLONE_TIMEOUT_SECONDS,
            sourceUrl, targetUrl, localPath);
    }

    /**
     * Get remote HEAD SHA using ls-remote (returns GitResult)
     *
     * @param remoteUrl Remote repository URL
     * @return GitResult with HEAD_SHA parsed value
     */
    public GitResult getRemoteHeadSha(String remoteUrl) {
        log.debug("Getting remote SHA for {}", maskToken(remoteUrl));

        GitResult result = executeScript("get-remote-sha", REMOTE_SHA_TIMEOUT_SECONDS,
            remoteUrl, "HEAD");

        // Parse SHA from output and add to parsed data
        if (result.isSuccess() && result.getOutput() != null) {
            String sha = result.getOutput().trim();
            result.parsedData.put("HEAD_SHA", sha);
        }

        return result;
    }

    /**
     * Get remote HEAD SHA using ls-remote
     *
     * @param remoteUrl Remote repository URL
     * @param ref       Reference (default: HEAD)
     * @return SHA or null if failed
     */
    public String getRemoteHeadSha(String remoteUrl, String ref) {
        log.debug("Getting remote SHA for {}", maskToken(remoteUrl));

        GitResult result = executeScript("get-remote-sha", REMOTE_SHA_TIMEOUT_SECONDS,
            remoteUrl, ref != null ? ref : "HEAD");

        if (result.isSuccess() && result.getOutput() != null) {
            return result.getOutput().trim();
        }

        return null;
    }

    /**
     * Get local HEAD SHA
     *
     * @param localPath Local repository path
     * @return SHA or null if failed
     */
    public String getLocalHeadSha(String localPath) {
        log.debug("Getting local HEAD SHA at {}", localPath);

        GitResult result = executeScript("get-local-sha", LOCAL_SHA_TIMEOUT_SECONDS, localPath);

        if (result.isSuccess() && result.getOutput() != null) {
            return result.getOutput().trim();
        }

        return null;
    }

    /**
     * Verify repository integrity (git fsck --quick)
     *
     * @param localPath Local repository path
     * @return true if repository is valid
     */
    public GitResult verifyRepository(String localPath) {
        log.debug("Verifying repository at {}", localPath);

        return executeScript("verify", VERIFY_TIMEOUT_SECONDS, localPath);
    }

    /**
     * Clean up repository (git gc)
     *
     * @param localPath Local repository path
     * @return Execution result with SIZE_BEFORE, SIZE_AFTER, SAVED_KB
     */
    public GitResult cleanup(String localPath) {
        log.info("Running cleanup on {}", localPath);

        return executeScript("cleanup", CLEANUP_TIMEOUT_SECONDS, localPath);
    }

    /**
     * Run git gc on repository (alias for cleanup)
     *
     * @param localPath Local repository path
     * @return Execution result
     */
    public GitResult gcRepository(String localPath) {
        return cleanup(localPath);
    }

    /**
     * Check if directory is a valid git repository
     *
     * @param localPath Path to check
     * @return true if valid git repository
     */
    public boolean isValidRepository(String localPath) {
        File gitDir = new File(localPath);
        if (!gitDir.exists() || !gitDir.isDirectory()) {
            return false;
        }

        // Check for .git directory or git bare repo files
        File dotGit = new File(gitDir, ".git");
        File headFile = new File(gitDir, "HEAD");
        File refsDir = new File(gitDir, "refs");

        return dotGit.exists() || (headFile.exists() && refsDir.exists());
    }

    /**
     * Delete repository directory
     *
     * @param localPath Repository path to delete
     * @return true if deleted successfully
     */
    public boolean deleteRepository(String localPath) {
        log.info("Deleting repository at {}", localPath);

        try {
            Path path = Paths.get(localPath);
            if (Files.exists(path)) {
                deleteDirectory(path.toFile());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to delete repository: {}", localPath, e);
            return false;
        }
    }

    /**
     * Execute shell script command
     *
     * @param command        Script command
     * @param timeoutSeconds Timeout in seconds
     * @param args           Command arguments
     * @return Execution result
     */
    private GitResult executeScript(String command, int timeoutSeconds, String... args) {
        String[] fullCommand = new String[args.length + 2];
        fullCommand[0] = scriptPath;
        fullCommand[1] = command;
        System.arraycopy(args, 0, fullCommand, 2, args.length);

        ProcessBuilder pb = new ProcessBuilder(fullCommand);
        pb.redirectErrorStream(false);

        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        int exitCode = -1;

        try {
            Process process = pb.start();

            // Read stdout
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("Error reading process output", e);
                }
            });

            // Read stderr
            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("Error reading process error", e);
                }
            });

            outputThread.start();
            errorThread.start();

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                log.error("Script timed out after {} seconds: {}", timeoutSeconds, command);
                return new GitResult(false, output.toString(), "Script timed out", -1);
            }

            outputThread.join(1000);
            errorThread.join(1000);

            exitCode = process.exitValue();
            boolean success = (exitCode == 0);

            if (!success) {
                log.error("Script failed with exit code {}: {}\nError: {}",
                    exitCode, command, error.toString());
            }

            return new GitResult(success, output.toString(), error.toString(), exitCode);

        } catch (Exception e) {
            log.error("Failed to execute script: {}", command, e);
            return new GitResult(false, output.toString(), e.getMessage(), exitCode);
        }
    }

    /**
     * Recursively delete directory
     */
    private void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }

        if (!directory.delete()) {
            throw new IOException("Failed to delete: " + directory.getAbsolutePath());
        }
    }

    /**
     * Mask sensitive tokens in URLs
     */
    private String maskToken(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("://[^@]+@", "://***:***@");
    }
}
