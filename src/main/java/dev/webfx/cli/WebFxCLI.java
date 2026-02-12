package dev.webfx.cli;

import dev.webfx.cli.commands.*;
import dev.webfx.cli.exceptions.CliException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Help.ColorScheme;
import picocli.CommandLine.IHelpFactory;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
@Command(name = "webfx", description = " __        __   _     _______  __\n" +
        " \\ \\      / ___| |__ |  ___\\ \\/ /\n" +
        "  \\ \\ /\\ / / _ | '_ \\| |_   \\  /\n" +
        "   \\ V  V |  __| |_) |  _|  /  \\\n" +
        "    \\_/\\_/ \\___|_.__/|_|   /_/\\_\\\n",
        subcommands = {
                Init.class,
                Create.class,
                Build.class,
                Run.class,
                Update.class,
                Rename.class,
                Bump.class,
                Install.class
        },
        mixinStandardHelpOptions = true,
        versionProvider = WebFxCLI.DevVersionProvider.class)
public final class WebFxCLI extends CommonCommand {

    public static void main(String... args) {
        String mode = System.getProperty("jdeploy.mode", "command");
        if ("gui".equals(mode)) {
            showAboutDialog();
            return;
        }
        System.exit(executeCommand(args));
    }

    private static void showAboutDialog() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            JFrame frame = new JFrame("WebFX");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

            // Logo/Title
            JLabel titleLabel = new JLabel("WebFX");
            titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            mainPanel.add(titleLabel);

            mainPanel.add(Box.createVerticalStrut(10));

            // Version
            JLabel versionLabel = new JLabel("Version: " + getVersion());
            versionLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            mainPanel.add(versionLabel);

            mainPanel.add(Box.createVerticalStrut(20));

            // Description
            String description = "<html><div style='text-align: center; width: 350px;'>" +
                    "WebFX CLI is a command-line tool for creating and managing WebFX projects.<br><br>" +
                    "WebFX allows you to write JavaFX applications that run in the browser via GWT/J2CL transpilation." +
                    "</div></html>";
            JLabel descLabel = new JLabel(description);
            descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            mainPanel.add(descLabel);

            mainPanel.add(Box.createVerticalStrut(20));

            // CLI Instructions
            JPanel instructionsPanel = new JPanel();
            instructionsPanel.setLayout(new BoxLayout(instructionsPanel, BoxLayout.Y_AXIS));
            instructionsPanel.setBorder(BorderFactory.createTitledBorder("Command Line Usage"));
            instructionsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

            String[] commands = {
                    "webfx --help          Show all commands",
                    "webfx init            Initialize a WebFX repository",
                    "webfx create          Create WebFX module(s)",
                    "webfx build           Build a WebFX application",
                    "webfx run             Run a WebFX application",
                    "webfx update          Update module files"
            };

            for (String cmd : commands) {
                JLabel cmdLabel = new JLabel(cmd);
                cmdLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                cmdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                instructionsPanel.add(cmdLabel);
                instructionsPanel.add(Box.createVerticalStrut(3));
            }

            mainPanel.add(instructionsPanel);

            mainPanel.add(Box.createVerticalStrut(20));

            // Buttons panel
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

            ActionListener openUrl = e -> {
                String url = e.getActionCommand();
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Could not open browser. Please visit:\n" + url,
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            };

            JButton webfxSiteBtn = new JButton("WebFX Website");
            webfxSiteBtn.setActionCommand("https://webfx.dev");
            webfxSiteBtn.addActionListener(openUrl);
            buttonPanel.add(webfxSiteBtn);

            JButton webfxGithubBtn = new JButton("WebFX GitHub");
            webfxGithubBtn.setActionCommand("https://github.com/webfx-project/webfx");
            webfxGithubBtn.addActionListener(openUrl);
            buttonPanel.add(webfxGithubBtn);

            JButton cliGithubBtn = new JButton("CLI GitHub");
            cliGithubBtn.setActionCommand("https://github.com/webfx-project/webfx-cli");
            cliGithubBtn.addActionListener(openUrl);
            buttonPanel.add(cliGithubBtn);

            buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
            mainPanel.add(buttonPanel);

            mainPanel.add(Box.createVerticalStrut(15));

            // Close button
            JButton closeBtn = new JButton("Close");
            closeBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            closeBtn.addActionListener(e -> System.exit(0));
            mainPanel.add(closeBtn);

            frame.add(mainPanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }

    public static int executeCommand(String... args) {
        return new CommandLine(new WebFxCLI())
                .setHelpFactory(new HelpFactory())
                .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                    // Removing the stack trace if this is raised by the cli tool (just showing the short message)
                    if (ex instanceof CliException)
                        ex.setStackTrace(new StackTraceElement[0]);
                    throw ex;
                })
                .execute(args);
    }

    private static class HelpFactory implements IHelpFactory {
        @Override
        public Help create(CommandSpec commandSpec, ColorScheme colorScheme) {
            return new Help(commandSpec, colorScheme) {
                @Override
                public String parameterList(java.util.List<PositionalParamSpec> positionalParams) {
                    return super.parameterList(positionalParams.stream().filter(this::isNotKeywordParameter).collect(Collectors.toList()));
                }

                boolean isNotKeywordParameter(PositionalParamSpec param) {
                    return !Keywords.Keyword.class.isAssignableFrom(param.auxiliaryTypes()[0]);
                }

                @Override
                protected Ansi.Text createDetailedSynopsisPositionalsText(Collection<ArgSpec> done) {
                    Ansi.Text positionalParamText = ansi().new Text(0);
                    java.util.List<PositionalParamSpec> positionals = new ArrayList<>(commandSpec.positionalParameters()); // iterate in declaration order
                    if (hasAtFileParameter()) {
                        positionals.add(0, AT_FILE_POSITIONAL_PARAM);
                        AT_FILE_POSITIONAL_PARAM.messages(commandSpec.usageMessage().messages());
                    }
                    positionals.removeAll(done);
                    for (PositionalParamSpec positionalParam : positionals) {
                        positionalParamText = concatPositionalText(" ", positionalParamText, colorScheme, positionalParam, parameterLabelRenderer());
                    }
                    return positionalParamText;
                }

                Ansi.Text concatPositionalText(String prefix, Ansi.Text text, ColorScheme colorScheme, PositionalParamSpec positionalParam, IParamLabelRenderer parameterLabelRenderer) {
                    if (!positionalParam.hidden()) {
                        Ansi.Text label = parameterLabelRenderer.renderParameterLabel(positionalParam, colorScheme.ansi(),
                                // Testing if the parameter is a keyword parameter
                                isNotKeywordParameter(positionalParam) ?
                                        colorScheme.parameterStyles() : // no => will appear with standard parameter color
                                        colorScheme.commandStyles()     // yes => will appear as white (like for commands)
                        );
                        text = text.concat(prefix).concat(label);
                    }
                    return text;
                }
            };
        }
    }

    public static class DevVersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            return new String[] { WebFxCLI.getVersion() };
        }
    }

    public static String getVersion() {
        try (InputStream pis = WebFxCLI.class.getClassLoader().getResourceAsStream("dev/webfx/cli/version/dev/version.ini")) {
            Properties devVersionProperties = new Properties();
            devVersionProperties.load(pis);
            return devVersionProperties.getProperty("version") + " ~ " + devVersionProperties.getProperty("build.timestamp") + " GMT";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}