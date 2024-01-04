import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.json.JSONArray;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApkVersionFetcher {

    private static final Logger logger = LogManager.getLogger(ApkVersionFetcher.class);

    private static final Pattern PATTERN = Pattern.compile(
            "AF_initDataCallback\\(\\{key:\\s*'ds:5'.*?data:([\\s\\S]*?), sideChannel:\\s*\\{.+?</script"
    );

    public static String getLastApkVersion() throws IOException {
        String url = "https://play.google.com/store/apps/details?id=com.app.tgtg&hl=en&gl=US";
        Document document = Jsoup.connect(url).get();
        String htmlContent = document.html();

        Matcher matcher = PATTERN.matcher(htmlContent);
        if (matcher.find()) {
            String jsonData = matcher.group(1);
            logger.info("Success in getting last apk version.");
            return parseJsonData(jsonData);
        } else {
            String errorMessage = "Failed to find APK data in HTML.";
            logger.error(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    private static String parseJsonData(String jsonData) {
        JSONArray topLevelArray = new JSONArray(jsonData);

        String version = topLevelArray.getJSONArray(1).getJSONArray(2).getJSONArray(140)
                .getJSONArray(0).getJSONArray(0).getString(0);

        return version;
    }
}