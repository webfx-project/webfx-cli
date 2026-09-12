package dev.webfx.cli.commands;

import dev.webfx.cli.core.Logger;
import dev.webfx.cli.exceptions.CliException;
import dev.webfx.platform.secret.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Manages a configuration file encrypted with a passphrase, so that secrets (API keys, signing keys)
 * don't sit in clear on a developer machine. The application decrypts it at startup, asking for the
 * passphrase once.
 *
 * <p>A secret is never given on the command line, always typed at a prompt that doesn't display it:
 * a command line is kept in the shell history, and is readable by any process running as you for as
 * long as the command lasts.
 *
 * @author Bruno Salmon
 */
@Command(name = "secret", description = "Manage a configuration file encrypted with a passphrase.",
        subcommands = {
                Secret.Init.class,
                Secret.Add.class,
                Secret.Keys.class,
                Secret.Reveal.class,
                Secret.Remove.class,
                Secret.Rekey.class,
                Secret.Import.class,
                Secret.Export.class,
        })
public final class Secret extends CommonSubcommand {

    /** Base of the commands working on an encrypted file. */
    abstract static class SecretCommand extends CommonSubcommand {

        @Option(names = {"-f", "--file"}, description = "The encrypted file (default: the only .secret file in the conf directory).")
        String file;

        Path secretFile() {
            if (file != null)
                return Paths.get(file);
            Path directory = projectPath(getProjectDirectory()).resolve("conf");
            if (!Files.isDirectory(directory))
                throw new CliException("No " + directory + " directory - name the file with --file");
            try (Stream<Path> files = Files.list(directory)) {
                java.util.List<Path> secretFiles = files.filter(p -> SecretFile.isSecretFileName(p.getFileName().toString())).sorted().toList();
                if (secretFiles.isEmpty())
                    throw new CliException("No encrypted file in " + directory + " - create one with: webfx secret init <name>.properties" + SecretFile.EXTENSION);
                if (secretFiles.size() > 1)
                    throw new CliException("Several encrypted files in " + directory + " - name the one to use with --file: "
                                           + secretFiles.stream().map(p -> p.getFileName().toString()).toList());
                return secretFiles.get(0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** The decrypted content, together with the passphrase that opened it, to write it back with. */
        Opened open() {
            Path path = secretFile();
            if (!Files.exists(path))
                throw new CliException(path + " doesn't exist - create it with: webfx secret init " + path.getFileName());
            String encrypted = SecretFile.readText(path);
            char[] configured = configuredPassphrase();
            if (configured != null)
                return new Opened(path, decryptOrFail(encrypted, configured, path), configured);
            for (int attempt = 1; attempt <= 3; attempt++) {
                char[] passphrase = ask("Passphrase for " + path.getFileName());
                try {
                    return new Opened(path, SecretCipher.decrypt(encrypted, passphrase), passphrase);
                } catch (WrongPassphraseException e) {
                    Arrays.fill(passphrase, '\0');
                    Logger.log("That passphrase didn't open it" + (attempt < 3 ? " - try again" : ""));
                }
            }
            throw new CliException("Wrong passphrase for " + path.getFileName());
        }

        private String decryptOrFail(String encrypted, char[] passphrase, Path path) {
            try {
                return SecretCipher.decrypt(encrypted, passphrase);
            } catch (WrongPassphraseException e) {
                throw new CliException("The passphrase given by " + SecretPassphrase.ENVIRONMENT_VARIABLE + " doesn't open " + path.getFileName());
            }
        }

        /** The entry commands read and write properties lines, which would corrupt a file of another format. */
        void requirePropertiesFile(Path path) {
            String plainFileName = SecretFile.plainFileName(path.getFileName().toString());
            if (!plainFileName.endsWith(".properties"))
                throw new CliException(path.getFileName() + " holds a " + plainFileName.substring(plainFileName.lastIndexOf('.') + 1)
                                       + " file, whose entries this command doesn't edit. Use export and import instead.");
        }

        void save(Opened opened, String plainText) {
            SecretFile.write(opened.path(), plainText, opened.passphrase());
            opened.wipe();
        }
    }

    record Opened(Path path, String text, char[] passphrase) {
        void wipe() {
            Arrays.fill(passphrase, '\0');
        }
    }

    @Command(name = "init", description = "Create a new encrypted file.")
    static class Init extends CommonSubcommand implements Runnable {

        @Parameters(paramLabel = "<file>", description = "Name of the file to create, ex: my-variables.properties" + SecretFile.EXTENSION)
        private String newFile;

        @Override
        public void run() {
            Path path = projectPath(getProjectDirectory()).resolve(newFile);
            SecretFile.plainFileName(path.getFileName().toString()); // refuses a name that doesn't say its format
            if (Files.exists(path))
                throw new CliException(path + " already exists");
            char[] passphrase = askNewPassphrase();
            SecretFile.write(path, "", passphrase);
            Arrays.fill(passphrase, '\0');
            Logger.log("Created " + path + " - add secrets to it with: webfx secret add <KEY> --file " + path);
        }
    }

    @Command(name = "add", aliases = "set", description = "Add a secret, or replace it if it is already there.")
    static class Add extends SecretCommand implements Runnable {

        @Parameters(paramLabel = "<KEY>", description = "Name of the secret, ex: DEEPL_API_KEY")
        private String key;

        @Override
        public void run() {
            Opened opened = open();
            requirePropertiesFile(opened.path());
            boolean replacing = SecretProperties.keys(opened.text()).contains(key);
            char[] value = askTwice("Value of " + key);
            try {
                save(opened, SecretProperties.set(opened.text(), key, new String(value)));
            } finally {
                Arrays.fill(value, '\0');
            }
            Logger.log((replacing ? "Replaced " : "Added ") + key + " in " + opened.path().getFileName());
        }
    }

    @Command(name = "list", description = "List the names of the secrets (not their values).")
    static class Keys extends SecretCommand implements Runnable {

        @Override
        public void run() {
            Opened opened = open();
            opened.wipe();
            requirePropertiesFile(opened.path());
            java.util.List<String> keys = SecretProperties.keys(opened.text());
            if (keys.isEmpty())
                Logger.log(opened.path().getFileName() + " holds no secret yet");
            else
                keys.forEach(Logger::log);
        }
    }

    @Command(name = "reveal", description = "Print one secret. Mind who can see the screen, and the terminal scrollback.")
    static class Reveal extends SecretCommand implements Runnable {

        @Parameters(paramLabel = "<KEY>", description = "Name of the secret to print")
        private String key;

        @Override
        public void run() {
            Opened opened = open();
            opened.wipe();
            requirePropertiesFile(opened.path());
            String value = SecretProperties.get(opened.text(), key);
            if (value == null)
                throw new CliException("No " + key + " in " + opened.path().getFileName());
            System.out.println(value);
        }
    }

    @Command(name = "remove", description = "Remove a secret.")
    static class Remove extends SecretCommand implements Runnable {

        @Parameters(paramLabel = "<KEY>", description = "Name of the secret to remove")
        private String key;

        @Override
        public void run() {
            Opened opened = open();
            requirePropertiesFile(opened.path());
            if (!SecretProperties.keys(opened.text()).contains(key)) {
                opened.wipe();
                throw new CliException("No " + key + " in " + opened.path().getFileName());
            }
            save(opened, SecretProperties.remove(opened.text(), key));
            Logger.log("Removed " + key + " from " + opened.path().getFileName());
        }
    }

    @Command(name = "rekey", description = "Change the passphrase.")
    static class Rekey extends SecretCommand implements Runnable {

        @Override
        public void run() {
            Opened opened = open();
            opened.wipe();
            char[] passphrase = askNewPassphrase();
            SecretFile.write(opened.path(), opened.text(), passphrase);
            Arrays.fill(passphrase, '\0');
            Logger.log("Changed the passphrase of " + opened.path().getFileName()
                       + " - store the new one where you keep it, the old one no longer opens this file");
        }
    }

    @Command(name = "import", description = "Encrypt an existing configuration file, and delete the readable one.")
    static class Import extends CommonSubcommand implements Runnable {

        @Parameters(paramLabel = "<file>", description = "The readable file to encrypt, ex: my-variables.properties")
        private String plainFile;

        @Option(names = {"-k", "--keep"}, description = "Keep the readable file instead of deleting it.")
        private boolean keep;

        @Override
        public void run() {
            Path source = projectPath(getProjectDirectory()).resolve(plainFile);
            if (!Files.exists(source))
                throw new CliException(source + " doesn't exist");
            if (SecretFile.isSecretFileName(source.getFileName().toString()) || SecretCipher.isEncrypted(SecretFile.readText(source)))
                throw new CliException(source.getFileName() + " is already encrypted");
            Path target = source.resolveSibling(source.getFileName() + SecretFile.EXTENSION);
            if (Files.exists(target))
                throw new CliException(target + " already exists - remove it first, or add the entries one by one");
            String plainText = SecretFile.readText(source);
            char[] passphrase = askNewPassphrase();
            SecretFile.write(target, plainText, passphrase);
            Arrays.fill(passphrase, '\0');
            Logger.log("Encrypted " + source.getFileName() + " into " + target.getFileName());
            if (keep)
                Logger.log("Kept " + source.getFileName() + ", which still holds the secrets in clear");
            else {
                try {
                    Files.delete(source);
                    Logger.log("Deleted " + source.getFileName());
                } catch (IOException e) {
                    throw new CliException("Could not delete " + source + " (" + e + ")");
                }
            }
            Logger.log("Deleting a file doesn't remove it from backups, nor from an IDE's local history."
                       + " Treat what it held as exposed, and rotate what is worth rotating.");
        }
    }

    @Command(name = "export", description = "Print the whole decrypted content, for recovery.")
    static class Export extends SecretCommand implements Runnable {

        @Override
        public void run() {
            Opened opened = open();
            opened.wipe();
            System.out.println(opened.text());
        }
    }

    /** The directory the command works in: the one given by -D/--directory, or the current one. */
    static Path projectPath(String projectDirectory) {
        return Paths.get(projectDirectory == null || projectDirectory.isEmpty() ? "." : projectDirectory);
    }

    // ------------------------------------- Asking, without displaying -------------------------------------

    private static char[] configuredPassphrase() {
        String value = System.getenv(SecretPassphrase.ENVIRONMENT_VARIABLE);
        return value == null || value.isEmpty() ? null : value.toCharArray();
    }

    static char[] ask(String label) {
        char[] typed = SecretPrompt.ask(label, java.util.List.of(), SecretPrompt.Preference.TERMINAL_FIRST);
        if (typed == null || typed.length == 0)
            throw new CliException("Nothing typed, nothing done");
        return typed;
    }

    /** Asks twice and compares, as a value typed or pasted wrong would only be noticed much later. */
    static char[] askTwice(String label) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            char[] first = ask(label);
            char[] second = ask("Again, to be sure");
            if (Arrays.equals(first, second)) {
                Arrays.fill(second, '\0');
                return first;
            }
            Arrays.fill(first, '\0');
            Arrays.fill(second, '\0');
            Logger.log("Those two don't match" + (attempt < 3 ? " - try again" : ""));
        }
        throw new CliException("The two entries didn't match");
    }

    static char[] askNewPassphrase() {
        char[] passphrase = askTwice("New passphrase");
        if (passphrase.length < 12)
            Logger.log("⚠️ That passphrase is short. A stolen file can be attacked offline, so prefer a long one"
                       + " - several random words, or a generated passphrase kept in your password manager.");
        return passphrase;
    }
}
