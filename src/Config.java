import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Scanner;

public class Config {
    private static final Logger logger = LogManager.getLogger(Config.class);
    private static final String CONFIG_FILE_PATH = "resources/config.json";


    public static void makeConfig() {
        JSONObject config = loadConfig(CONFIG_FILE_PATH);

        if (config == null) {
            createConfigFile();
            config = loadConfig(CONFIG_FILE_PATH);
        }

        assert config != null;
        if (!config.has("tgtg")) {
            config.put("tgtg", new JSONObject());
        }

        JSONObject tgtg = config.getJSONObject("tgtg");

        if (!tgtg.has("email")) {
            String email = getEmailFromConsole();
            tgtg.put("email", email);
            saveConfig(config);
        }

        String email = config.getJSONObject("tgtg").getString("email");

        if (!tgtg.has("credentials")) {
            Map<String, String> credentials_ = null;
            TgtgClient tgtgClient = new TgtgClient(email, credentials_);
            Map<String, String> credentials = tgtgClient.getCredentials();
            tgtg.put("credentials", credentials);
            saveConfig(config);
        }

        if (!config.has("location")) {
            config.put("location", new JSONObject());
        }

        JSONObject location = config.getJSONObject("location");

        if (!location.has("lat") || !location.has("lon") || !location.has("range")) {
            JSONObject location_ = getLocationFromConsole();
            config.put("location", location_);
            saveConfig(config);
        }
    }

    public static JSONObject loadConfig(String configFilePath) {
        Path path = Paths.get(configFilePath);

        if (!Files.exists(path)) {
            return null;
        }

        try {
            String content = Files.readString(path);

            if (content.trim().isEmpty()) {
                return null;
            }

            return new JSONObject(content);
        } catch (IOException e) {
            logger.error("Error during config loading.", e);
        } catch (JSONException e) {
            logger.error("Error parsing JSON in config file.", e);
        }

        return null;
    }

    private static void createConfigFile() {
        JSONObject defaultConfig = new JSONObject();
        defaultConfig.put("location", new JSONObject());
        defaultConfig.put("tgtg", new JSONObject());

        try (Writer writer = new FileWriter(CONFIG_FILE_PATH)) {
            writer.write(defaultConfig.toString());
        } catch (IOException e) {
            logger.error("Error during creating config file.");
        }
    }

    private static String getEmailFromConsole() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter email: ");
        return scanner.nextLine();
    }

    private static JSONObject getLocationFromConsole() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter latitude: ");
        double lat = scanner.nextDouble();
        System.out.print("Enter longitude: ");
        double lon = scanner.nextDouble();
        System.out.print("Enter range (in km): ");
        double range = scanner.nextInt();

        JSONObject location = new JSONObject();
        location.put("lat", lat);
        location.put("lon", lon);
        location.put("range", range);

        return location;
    }

    private static void saveConfig(JSONObject config) {
        try (Writer writer = new FileWriter(CONFIG_FILE_PATH)) {
            writer.write(config.toString());
        } catch (IOException e) {
            logger.error("Error during saving config.");
        }
    }
}