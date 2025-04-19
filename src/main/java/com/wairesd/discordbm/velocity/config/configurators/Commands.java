
package com.wairesd.discordbm.velocity.config.configurators;

import com.wairesd.discordbm.velocity.command.build.actions.SendMessageAction;
import com.wairesd.discordbm.velocity.command.build.conditions.PermissionCondition;
import com.wairesd.discordbm.velocity.command.build.models.CommandAction;
import com.wairesd.discordbm.velocity.command.build.models.CommandCondition;
import com.wairesd.discordbm.velocity.command.build.models.CommandOption;
import com.wairesd.discordbm.velocity.command.build.models.CustomCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Commands {
    private static final Logger logger = LoggerFactory.getLogger(Commands.class);
    private static Path dataDirectory;
    private static List<CustomCommand> customCommands;

    public static void init(Path dataDir) {
        dataDirectory = dataDir;
        load();
    }

    public static void load() {
        CompletableFuture.runAsync(() -> {
            try {
                Path commandsPath = dataDirectory.resolve("commands.yml");
                if (!Files.exists(commandsPath)) {
                    Files.createDirectories(dataDirectory);
                    try (InputStream in = Commands.class.getClassLoader().getResourceAsStream("commands.yml")) {
                        if (in != null) {
                            Files.copy(in, commandsPath);
                        } else {
                            logger.error("commands.yml not found in resources!");
                            return;
                        }
                    }
                }
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(Files.newInputStream(commandsPath));
                List<Map<String, Object>> commandsList = (List<Map<String, Object>>) data.get("commands");
                customCommands = new ArrayList<>();

                for (Map<String, Object> cmdData : commandsList) {
                    CustomCommand cmd = parseCommand(cmdData);
                    customCommands.add(cmd);
                }
                logger.info("commands.yml loaded successfully with {} commands", customCommands.size());
            } catch (Exception e) {
                logger.error("Error loading commands.yml: {}", e.getMessage(), e);
            }
        });
    }

    public static void reload() {
        load();
    }

    public static List<CustomCommand> getCustomCommands() {
        return customCommands != null ? customCommands : Collections.emptyList();
    }

    private static CustomCommand parseCommand(Map<String, Object> cmdData) {
        String name = (String) cmdData.get("name");
        String description = (String) cmdData.get("description");
        String context = (String) cmdData.getOrDefault("context", "both");
        List<Map<String, Object>> optionsData = (List<Map<String, Object>>) cmdData.getOrDefault("options", Collections.emptyList());
        List<Map<String, Object>> conditionsData = (List<Map<String, Object>>) cmdData.getOrDefault("conditions", Collections.emptyList());
        List<Map<String, Object>> actionsData = (List<Map<String, Object>>) cmdData.getOrDefault("actions", Collections.emptyList());

        List<CommandOption> options = optionsData.stream().map(Commands::createOption).toList();
        List<CommandCondition> conditions = conditionsData.stream().map(Commands::createCondition).toList();
        List<CommandAction> actions = actionsData.stream().map(Commands::createAction).toList();

        return new CustomCommand(name, description, context, options, conditions, actions);
    }

    private static CommandOption createOption(Map<String, Object> data) {
        return new CommandOption(
                (String) data.get("name"),
                (String) data.get("type"),
                (String) data.get("description"),
                (boolean) data.getOrDefault("required", false)
        );
    }

    private static CommandCondition createCondition(Map<String, Object> data) {
        String type = (String) data.get("type");
        if ("permission".equals(type)) {
            return new PermissionCondition(data);
        }
        return null; // Add more condition types as needed
    }

    private static CommandAction createAction(Map<String, Object> data) {
        String type = (String) data.get("type");
        if ("send_message".equals(type)) {
            return new SendMessageAction(data);
        }
        return null; // Add more action types as needed
    }
}