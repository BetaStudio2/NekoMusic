import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static String backendUrl;

    public static void main(String[] args) {
        try {
            // 第一步：读取配置文件
            loadConfig();
            System.out.println("后端地址: " + backendUrl);

            // 第二步：要求用户输入音乐本地路径
            Scanner scanner = new Scanner(System.in);
            System.out.print("请输入音乐的本地路径（例如 D:\\CloudMusic）: ");
            String musicPath = scanner.nextLine().trim();

            // 处理路径中的 @ 符号
            if (musicPath.startsWith("@")) {
                musicPath = musicPath.substring(1);
            }

            Path path = Paths.get(musicPath);
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                System.err.println("错误：路径不存在或不是目录");
                return;
            }

            // 第三步：扫描文件夹，查找 ncm、mp3 和 lrc 文件
            List<String> mp3Files = new ArrayList<>();
            List<String> ncmFiles = new ArrayList<>();
            List<String> lrcFiles = new ArrayList<>();

            scanDirectory(path, mp3Files, ncmFiles, lrcFiles);

            // 第四步：统计并输出结果
            int totalMusic = mp3Files.size() + ncmFiles.size();
            int musicWithLyrics = 0;

            // 检查每首音乐是否有对应的歌词文件
            for (String mp3File : mp3Files) {
                String baseName = getBaseName(mp3File);
                if (hasMatchingLyrics(baseName, lrcFiles)) {
                    musicWithLyrics++;
                }
            }

            for (String ncmFile : ncmFiles) {
                String baseName = getBaseName(ncmFile);
                if (hasMatchingLyrics(baseName, lrcFiles)) {
                    musicWithLyrics++;
                }
            }

            System.out.println("\n扫描结果:");
            System.out.println("找到了 " + totalMusic + " 首音乐");
            System.out.println("其中 " + ncmFiles.size() + " 首音乐是 ncm 加密格式");
            System.out.println("其中 " + musicWithLyrics + " 首音乐存在歌词");

            // 显示详细信息（可选）
            if (totalMusic > 0) {
                System.out.println("\nMP3 文件列表:");
                for (String file : mp3Files) {
                    System.out.println("  - " + file);
                }

                System.out.println("\nNCM 文件列表:");
                for (String file : ncmFiles) {
                    System.out.println("  - " + file);
                }

                System.out.println("\nLRC 文件列表:");
                for (String file : lrcFiles) {
                    System.out.println("  - " + file);
                }
            }

        } catch (Exception e) {
            System.err.println("程序运行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadConfig() throws IOException {
        // 尝试多个可能的配置文件路径
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

        // 读取文件内容并解析
        List<String> lines = Files.readAllLines(configPath);
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("adder:")) {
                backendUrl = line.substring("adder:".length()).trim();
                break;
            }
        }
        
        if (backendUrl == null || backendUrl.isEmpty()) {
            throw new IOException("配置文件中未找到 adder 配置");
        }
    }

    private static void scanDirectory(Path dir, List<String> mp3Files, List<String> ncmFiles, List<String> lrcFiles) throws IOException {
        Files.walk(dir)
            .filter(Files::isRegularFile)
            .forEach(path -> {
                String fileName = path.getFileName().toString().toLowerCase();
                if (fileName.endsWith(".mp3")) {
                    mp3Files.add(path.toString());
                } else if (fileName.endsWith(".ncm")) {
                    ncmFiles.add(path.toString());
                } else if (fileName.endsWith(".lrc")) {
                    lrcFiles.add(path.toString());
                }
            });
    }

    private static String getBaseName(String filePath) {
        String fileName = new File(filePath).getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    private static boolean hasMatchingLyrics(String baseName, List<String> lrcFiles) {
        for (String lrcFile : lrcFiles) {
            String lrcBaseName = getBaseName(lrcFile);
            if (lrcBaseName.equalsIgnoreCase(baseName)) {
                return true;
            }
        }
        return false;
    }
}
