import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {
    private static String backendUrl;
    private static String token;
    
    public static String loadBackendUrl() throws IOException {
        Path[] possiblePaths = {
            Paths.get("MusicFastUpload/src/main/resources/config.yml"),
            Paths.get("src/main/resources/config.yml"),
            Paths.get("config.yml"),
            Paths.get("../config.yml"),
            Paths.get("D:/Projects/Idea/Music/MusicFastUpload/src/main/resources/config.yml")
        };
        
        Path configPath = null;
        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                configPath = path;
                break;
            }
        }
        
        if (configPath == null) {
            throw new IOException("配置文件 config.yml 不存在");
        }

        List<String> lines = Files.readAllLines(configPath);
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("adder:")) {
                String url = line.substring("adder:".length()).trim();
                if (url != null && !url.isEmpty()) {
                    backendUrl = url;
                }
            } else if (line.startsWith("token:")) {
                String tokenValue = line.substring("token:".length()).trim();
                if (tokenValue != null && !tokenValue.isEmpty()) {
                    token = tokenValue;
                }
            }
        }
        
        if (backendUrl == null || backendUrl.isEmpty()) {
            throw new IOException("配置文件中未找到 adder 配置");
        }
        
        return backendUrl;
    }
    
    public static String getToken() {
        return token;
    }
}