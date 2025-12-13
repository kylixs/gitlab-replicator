package com.gitlab.mirror.cli;

import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.command.*;
import com.gitlab.mirror.cli.config.CliConfig;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.util.Arrays;

/**
 * Main entry point for GitLab Mirror CLI
 *
 * @author GitLab Mirror Team
 */
public class GitLabMirrorCli {

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                printUsage();
                System.exit(0);
            }

            // Load configuration
            CliConfig config = CliConfig.fromEnvironment();

            // Validate token for non-public commands
            String command = args[0];
            if (!command.equals("help") && !command.equals("version")) {
                if (config.getApiToken() == null || config.getApiToken().isEmpty()) {
                    OutputFormatter.printError("API token not configured");
                    OutputFormatter.printInfo("Set GITLAB_MIRROR_TOKEN environment variable or configure in .env file");
                    System.exit(1);
                }
            }

            // Create API client
            ApiClient apiClient = new ApiClient(config);

            // Route to appropriate command handler
            String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

            switch (command) {
                case "projects":
                    new ProjectCommand(apiClient).execute(commandArgs);
                    break;
                case "discover":
                    new DiscoverCommand(apiClient).execute(commandArgs);
                    break;
                case "mirrors":
                    new MirrorListCommand(apiClient).execute(commandArgs);
                    break;
                case "mirror":
                    new MirrorCommand(apiClient).execute(commandArgs);
                    break;
                case "compensate":
                    new CompensateCommand(apiClient).execute(commandArgs);
                    break;
                case "sync":
                    new SyncCommand(apiClient).execute(commandArgs);
                    break;
                case "events":
                    new EventCommand(apiClient).execute(commandArgs);
                    break;
                case "export":
                    new ExportCommand(apiClient).execute(commandArgs);
                    break;
                case "pull":
                    new PullSyncCommand(apiClient).execute(commandArgs);
                    break;
                case "task":
                    new TaskCommand(apiClient).execute(commandArgs);
                    break;
                case "scheduler":
                    new SchedulerCommand(apiClient).execute(commandArgs);
                    break;
                case "help":
                case "--help":
                case "-h":
                    printUsage();
                    break;
                case "version":
                case "--version":
                case "-v":
                    printVersion();
                    break;
                default:
                    OutputFormatter.printError("Unknown command: " + command);
                    System.out.println();
                    printUsage();
                    System.exit(1);
            }

        } catch (Exception e) {
            OutputFormatter.printError("Command failed: " + e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println(OutputFormatter.CYAN + "GitLab Mirror CLI" + OutputFormatter.RESET);
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Usage:" + OutputFormatter.RESET);
        System.out.println("  gitlab-mirror <command> [options]");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Commands:" + OutputFormatter.RESET);
        System.out.println("  projects              List projects");
        System.out.println("  discover              Trigger project discovery");
        System.out.println("  mirrors               List mirrors");
        System.out.println("  mirror <project>      Show mirror details");
        System.out.println("  compensate            Trigger mirror compensation check");
        System.out.println("  sync <project>        Trigger push mirror sync for a project");
        System.out.println("  events                List events");
        System.out.println("  export                Export data");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Pull-Sync Commands:" + OutputFormatter.RESET);
        System.out.println("  pull <subcommand>     Pull sync management");
        System.out.println("  task <subcommand>     Task management");
        System.out.println("  scheduler <subcommand> Scheduler management");
        System.out.println();
        System.out.println("  help                  Show this help message");
        System.out.println("  version               Show version information");
        System.out.println();
        System.out.println(OutputFormatter.YELLOW + "Environment Variables:" + OutputFormatter.RESET);
        System.out.println("  GITLAB_MIRROR_API_URL      API base URL (default: http://localhost:8080)");
        System.out.println("  GITLAB_MIRROR_TOKEN        API authentication token");
        System.out.println();
    }

    private static void printVersion() {
        System.out.println("GitLab Mirror CLI v1.0.0-SNAPSHOT");
    }
}
