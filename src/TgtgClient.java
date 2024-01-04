import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class TgtgClient {
    private static final Logger logger = LogManager.getLogger(TgtgClient.class);

    private static final String BASE_URL = "https://apptoogoodtogo.com/api/";
    private static final String API_ITEM_ENDPOINT = "item/v8/";
    private static final String AUTH_BY_EMAIL_ENDPOINT = "auth/v3/authByEmail";
    private static final String AUTH_POLLING_ENDPOINT = "auth/v3/authByRequestPollingId";
    private static final String SIGNUP_BY_EMAIL_ENDPOINT = "auth/v3/signUpByEmail";
    private static final String REFRESH_ENDPOINT = "auth/v3/token/refresh";
    private static final String ACTIVE_ORDER_ENDPOINT = "order/v7/active";
    private static final String INACTIVE_ORDER_ENDPOINT = "order/v7/inactive";
    private static final String CREATE_ORDER_ENDPOINT = "order/v7/create/";
    private static final String ABORT_ORDER_ENDPOINT = "order/v7/%s/abort";
    private static final String ORDER_STATUS_ENDPOINT = "order/v7/%s/status";
    private static final String API_BUCKET_ENDPOINT = "discover/v1/bucket";
    private static final String DEFAULT_APK_VERSION = "22.5.5";
    private static final String[] USER_AGENTS = {
            "TGTG/%s Dalvik/2.1.0 (Linux; U; Android 9; Nexus 5 Build/M4B30Z)",
            "TGTG/%s Dalvik/2.1.0 (Linux; U; Android 10; SM-G935F Build/NRD90M)",
            "TGTG/%s Dalvik/2.1.0 (Linux; Android 12; SM-G920V Build/MMB29K)"
    };
    private static final int DEFAULT_ACCESS_TOKEN_LIFETIME = 3600 * 4; // 4 hours
    private static final int MAX_POLLING_TRIES = 30; // 30 * POLLING_WAIT_TIME = 5 minutes
    private static final int POLLING_WAIT_TIME = 10; // 10 Seconds

    private final String email;
    private String access_token;
    private String refresh_token;
    private String user_id;
    private final String user_agent;
    private int timeout;
    private LocalDateTime last_time_token_refreshed;
    private final String device_type = "ANDROID";
    private String cookie;


    public TgtgClient(String email, Map<String, String> credentials) {

        this.email = email;

        this.user_agent = getUserAgent();

        if (credentials != null) {
            if (credentials.containsKey("access_token")) {
                this.access_token = credentials.get("access_token");
            }

            if (credentials.containsKey("refresh_token")) {
                this.refresh_token = credentials.get("refresh_token");
            }

            if (credentials.containsKey("user_id")) {
                this.user_id = credentials.get("user_id");
            }

            if (credentials.containsKey("cookie")) {
                this.cookie = credentials.get("cookie");
            }
        }
    }

    private String getUserAgent() {
        String version;
        try {
            version = ApkVersionFetcher.getLastApkVersion();
        } catch (IOException e) {
            version = DEFAULT_APK_VERSION;
            logger.error("Failed to get last version.", e);
        }

        logger.info("Using version " + version);

        return USER_AGENTS[new Random().nextInt(USER_AGENTS.length)].formatted(version);
    }

    private String getUrl(String... pathSegments) {
        try {
            URI baseUri = new URI(BASE_URL);
            URI resolvedUri = baseUri.resolve(String.join("/", pathSegments));
            logger.info("Success in joining URL.");
            return resolvedUri.toString();
        } catch (URISyntaxException e) {
            logger.error("Error joining URL.", e);
            return null;
        }
    }

    public Map<String, String> getCredentials() {
        login();
        Map<String, String> credentials = new HashMap<>();
        credentials.put("access_token", access_token);
        credentials.put("refresh_token", refresh_token);
        credentials.put("user_id", user_id);
        credentials.put("cookie", cookie);
        logger.info("Success in getting credentials.");
        return credentials;
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("accept", "application/json");
        headers.put("Accept-Encoding", "gzip");
        String language = "en-GB";
        headers.put("accept-language", language);
        headers.put("content-type", "application/json; charset=utf-8");
        headers.put("user-agent", user_agent);
        if (cookie != null && !cookie.isEmpty()) {
            headers.put("Cookie", cookie);
        }
        if (access_token != null && !access_token.isEmpty()) {
            headers.put("authorization", "Bearer " + access_token);
        }
        logger.info("Success in getting headers.");
        return headers;
    }

    private boolean alreadyLogged() {
        return (access_token != null && !access_token.isEmpty() && refresh_token != null && !refresh_token.isEmpty()
                && user_id != null && !user_id.isEmpty());
    }

    private void refreshToken() {
        if(last_time_token_refreshed != null &&
                Duration.between(last_time_token_refreshed, LocalDateTime.now()).getSeconds() <= DEFAULT_ACCESS_TOKEN_LIFETIME) {
            return;
        }

        try {
            JSONObject requestBody = new JSONObject()
                    .put("refresh_token", refresh_token);

            Response response = Jsoup
                    .connect(getUrl(REFRESH_ENDPOINT))
                    .method(Connection.Method.POST)
                    .requestBody(requestBody.toString())
                    .headers(getHeaders())
                    .timeout(timeout)
                    .ignoreContentType(true)
                    .execute();

            if(response.statusCode() == HttpURLConnection.HTTP_OK) {
                String responseBody = response.body();
                JSONObject refreshTokenResponse = new JSONObject(responseBody);

                access_token = (String) refreshTokenResponse.get("access_token");
                refresh_token = (String) refreshTokenResponse.get("refresh_token");
                last_time_token_refreshed = LocalDateTime.now();
                cookie = response.header("Set-Cookie");
                logger.info("Success in refreshing token.");
            } else {
                if (response.statusCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
                    logger.error("Too many requests. Status code: {}", response.statusCode());
                } else {
                    logger.error("Refresh token failed with status code {}", response.statusCode());
                }
            }
        } catch (IOException e) {
            logger.error("Error during refreshing token", e);
        }
    }

    public void login() {
        if (!(email != null || (access_token != null && refresh_token != null && user_id != null && cookie != null))) {
            logger.error("Login error. You must provide at least email or access_token, refresh_token, user_id, and cookie");
        }
        if(alreadyLogged()) {
            refreshToken();
        } else {
            try {
                JSONObject requestBody = new JSONObject()
                        .put("device_type", device_type)
                        .put("email", email);

                Response response = Jsoup
                        .connect(getUrl(AUTH_BY_EMAIL_ENDPOINT))
                        .method(Connection.Method.POST)
                        .requestBody(requestBody.toString())
                        .headers(getHeaders())
                        .timeout(timeout)
                        .ignoreContentType(true)
                        .execute();

                if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                    String responseBody = response.body();
                    JSONObject firstLoginResponse = new JSONObject(responseBody);

                    if ("TERMS".equals(firstLoginResponse.get("state"))) {
                        logger.error("This email is not linked to a tgtg account. Please sign up with this email first. " + email);
                    } else if ("WAIT".equals(firstLoginResponse.get("state"))) {
                        startPolling(firstLoginResponse.getString("polling_id"));
                    } else {
                        logger.error("Login failed. " + response.statusCode());
                    }
                } else {
                    if (response.statusCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
                        logger.error("Too many requests. Status code: {}", response.statusCode());
                    } else {
                        logger.error("Login failed with status code {}", response.statusCode());
                    }
                }
            } catch (IOException e) {
                logger.error("Error during login", e);
            }
        }
    }

    public void startPolling(String polling_id) {
        for (int i = 0; i < MAX_POLLING_TRIES; i++) {
            try {
                JSONObject requestBody = new JSONObject()
                        .put("device_type", device_type)
                        .put("email", email)
                        .put("request_polling_id", polling_id);

                Response response = Jsoup
                        .connect(getUrl(AUTH_POLLING_ENDPOINT))
                        .method(Connection.Method.POST)
                        .requestBody(requestBody.toString())
                        .headers(getHeaders())
                        .timeout(timeout)
                        .ignoreContentType(true)
                        .execute();

                if (response.statusCode() == HttpURLConnection.HTTP_ACCEPTED) {
                    logger.info("Check your mailbox on PC to continue... " +
                            "(Opening email on mobile won't work, if you have installed tgtg app.)");
                    Thread.sleep(POLLING_WAIT_TIME * 1000);
                } else if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                    logger.info("Logged in!");
                    String responseBody = response.body();
                    JSONObject loginResponse = new JSONObject(responseBody);

                    access_token = (String) loginResponse.get("access_token");
                    refresh_token = (String) loginResponse.get("refresh_token");
                    last_time_token_refreshed = LocalDateTime.now();
                    user_id = loginResponse.getJSONObject("startup_data").getJSONObject("user").getString("user_id");
                    cookie = response.header("Set-Cookie");
                    return;
                } else {
                    if (response.statusCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
                        logger.error("Too many requests. Status code: {}", response.statusCode());
                    } else {
                        logger.error("Login failed with status code {}", response.statusCode());
                    }
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Error during polling", e);
            }
        }
        logger.error("Max retries ({}) reached. Polling stopped.", MAX_POLLING_TRIES * POLLING_WAIT_TIME);
    }

    public JSONObject getItems(
            double latitude,
            double longitude,
            int radius,
            int pageSize,
            int page,
            boolean discover,
            boolean favoritesOnly,
            List<String> itemCategories,
            List<String> dietCategories,
            String pickupEarliest,
            String pickupLatest,
            String searchPhrase,
            boolean withStockOnly,
            boolean hiddenOnly,
            boolean weCareOnly
    ) {
        login();

        Map<String, Object> origin = new HashMap<>();
        origin.put("latitude", latitude);
        origin.put("longitude", longitude);

        Map<String, Object> data = new HashMap<>();
        data.put("user_id", user_id);
        data.put("origin", origin);
        data.put("radius", radius);
        data.put("page_size", pageSize);
        data.put("page", page);
        data.put("discover", discover);
        data.put("favorites_only", favoritesOnly);
        data.put("item_categories", itemCategories != null ? itemCategories : List.of());
        data.put("diet_categories", dietCategories != null ? dietCategories : List.of());
        data.put("pickup_earliest", pickupEarliest);
        data.put("pickup_latest", pickupLatest);
        data.put("search_phrase", searchPhrase);
        data.put("with_stock_only", withStockOnly);
        data.put("hidden_only", hiddenOnly);
        data.put("we_care_only", weCareOnly);

        try {
            Response response = Jsoup
                    .connect(getUrl(API_ITEM_ENDPOINT))
                    .method(Connection.Method.POST)
                    .requestBody(new JSONObject(data).toString())
                    .headers(getHeaders())
                    .timeout(timeout)
                    .ignoreContentType(true)
                    .execute();

            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                logger.info("Success in getting items.");
                return parseDataItems(new JSONObject(response.body()));
            } else {
                logger.error("Getting items error. Status code={}", response.statusCode());
            }
        } catch (IOException e) {
            logger.error("Error during getItems", e);
        }
        return new JSONObject();
    }

    private JSONObject parseDataItems(JSONObject jsonData) {
        JSONArray itemsArray = jsonData.getJSONArray("items");
        //System.out.println(itemsArray);
        return new JSONObject()
                .put("items", StreamSupport.stream(itemsArray.spliterator(), false)
                        .map(item -> (JSONObject) item)
                        .map(itemObject -> {
                            JSONObject current_item = new JSONObject();
                            current_item.put("item_id", itemObject.getJSONObject("item").getString("item_id"));
                            current_item.put("store_name", itemObject.getJSONObject("store").getString("store_name"));
                            current_item.put("cover_picture", itemObject.getJSONObject("item").getJSONObject("cover_picture").getString("current_url"));
                            current_item.put("logo_picture", itemObject.getJSONObject("item").getJSONObject("logo_picture").getString("current_url"));
                            current_item.put("category", itemObject.getJSONObject("item").getString("item_category"));
                            current_item.put("address", itemObject.getJSONObject("store").getJSONObject("store_location").getJSONObject("address").getString("address_line"));
                            current_item.put("description", itemObject.getJSONObject("item").getString("description"));
                            current_item.put("items_available", itemObject.getInt("items_available"));

                            if (itemObject.has("item") && itemObject.getJSONObject("item").has("average_overall_rating") && itemObject.getJSONObject("item").getJSONObject("average_overall_rating").has("average_overall_rating")) {
                                JSONObject averageRatingObject = itemObject.getJSONObject("item").getJSONObject("average_overall_rating");
                                double averageRating = averageRatingObject.getDouble("average_overall_rating");
                                double roundedAverageRating = Math.round(averageRating * 100.0) / 100.0;
                                current_item.put("rating", roundedAverageRating);
                            } else {
                                current_item.put("rating", "");
                            }

                            if (current_item.getInt("items_available") == 0) {
                                return current_item;
                            }

                            try {
                                if (itemObject.has("item") && itemObject.getJSONObject("item").has("item_price")) {
                                    current_item.put("price_after", formatPrice(itemObject.getJSONObject("item").getJSONObject("item_price")));
                                } else {
                                    current_item.put("price_after", "");
                                    logger.error("Lack of price after.");
                                }

                                if (itemObject.has("item") && itemObject.getJSONObject("item").has("item_value")) {
                                    current_item.put("price_before", formatPrice(itemObject.getJSONObject("item").getJSONObject("item_value")));
                                } else {
                                    current_item.put("price_before", "");
                                    logger.error("Lack of price before");
                                }
                            } catch (Exception e) {
                                current_item.put("price_after", "");
                                current_item.put("price_before", "");
                                logger.error("Error during formatting prices");
                            }

                            try {
                                if (itemObject.has("pickup_interval")) {
                                    LocalDateTime localPickupStart = Instant.parse(itemObject.getJSONObject("pickup_interval").getString("start")).atZone(ZoneId.of("UTC")).toLocalDateTime();
                                    LocalDateTime localPickupEnd = Instant.parse(itemObject.getJSONObject("pickup_interval").getString("end")).atZone(ZoneId.of("UTC")).toLocalDateTime();
                                    current_item.put("pickup_start", formatPickup(localPickupStart));
                                    current_item.put("pickup_end", formatPickup(localPickupEnd));
                                } else {
                                    current_item.put("pickup_start", "");
                                    current_item.put("pickup_end", "");
                                    logger.error("Lack of pickup interval");
                                }
                            } catch (Exception e) {
                                current_item.put("pickup_start", "");
                                current_item.put("pickup_end", "");
                                logger.error("Error during formatting pickup interval");
                            }

                            return current_item;
                        })
                        .collect(Collectors.toList()));
    }

    private String formatPrice(JSONObject price) {
        int minorUnits = price.getInt("minor_units");
        int decimals = price.getInt("decimals");
        String code = price.getString("code");

        double formattedPrice = minorUnits / Math.pow(10, decimals);

        return String.format(Locale.ENGLISH, "%.2f%s", formattedPrice, code);
    }

    private String formatPickup(LocalDateTime pickupDateTime) {
        return pickupDateTime.format(DateTimeFormatter.ofPattern("EEE dd MMM HH:mm", Locale.ENGLISH));
    }

    public JSONObject getItem(String item_id) {
       login();

        JSONObject requestBody = new JSONObject()
                .put("user_id", user_id)
                .put("origin", JSONObject.NULL);

        try {
            Response response = Jsoup
                    .connect(getUrl(API_ITEM_ENDPOINT, item_id))
                    .method(Connection.Method.POST)
                    .requestBody(requestBody.toString())
                    .headers(getHeaders())
                    .timeout(timeout)
                    .ignoreContentType(true)
                    .execute();

            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                logger.info("Success in getting item id " + item_id);
                return (new JSONObject(response.body())); // ToDo parse data
            } else {
                logger.error("Getting item id {} error. Status code={}", item_id, response.statusCode());
            }
        } catch (IOException e) {
            logger.error("Error during getItem. ID " + item_id, e);
        }
        return new JSONObject();

    }

    // ToDo getFavourites
    // ToDo setFavourite

}