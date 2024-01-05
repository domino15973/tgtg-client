import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final Logger logger = LogManager.getLogger(Config.class);

    public static void main(String[] args) {
        Config.makeConfig();

        JSONObject config = Config.loadConfig("resources/config.json");

        String email = null;
        Map<String, String> credentialsMap = null;
        if (config != null && config.has("tgtg")) {
            JSONObject tgtg = config.getJSONObject("tgtg");

            if (tgtg.has("email")) {
                email = tgtg.getString("email");

                if (tgtg.has("credentials")) {
                    JSONObject credentials = tgtg.getJSONObject("credentials");

                    credentialsMap = new HashMap<>();
                    for (String key : credentials.keySet()) {
                        credentialsMap.put(key, credentials.getString(key));
                    }
                }
            }
        } else {
            logger.error("Error during load config in main");
        }

        double latitude = 0;
        double longitude = 0;
        int range = 0;
        if (config != null && config.has("location")) {
            JSONObject location = config.getJSONObject("location");
            if (location.has("range")) {
                range = location.getInt("range");
            }
            if (location.has("lon")) {
                longitude = location.getDouble("lon");
            }
            if (location.has("lat")) {
                latitude = location.getDouble("lat");
            }
        } else {
            logger.error("Error during load location in mail");
        }

        TgtgClient tgtgClient = new TgtgClient(email, credentialsMap);

        System.out.println(tgtgClient.getItems(
                latitude,
                longitude,
                range,
                50,
                1,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
        ));

        // System.out.println(tgtgClient.getItem("item_id"));

        // tgtgClient.setFavorite("item_id", true);

        // System.out.println(tgtgClient.getFavorites());

    }
}