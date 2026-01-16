import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.util.Scanner;

public class MusicUploader {
    private static final String UPLOAD_ENDPOINT = "/api/music/upload";
    private static String backendUrl;
    private static Scanner scanner = new Scanner(System.in);
    
    public static void uploadMusic(List<String> mp3Files, List<String> lrcFiles, String backendUrl) {
        MusicUploader.backendUrl = backendUrl;
        System.out.println("\n开始上传音乐到后端: " + backendUrl + UPLOAD_ENDPOINT);
        
        // 清空Scanner缓冲区
        scanner = new Scanner(System.in);
        
        // 上传前询问语言
        System.out.print("\n请输入语言 (默认: 日语): ");
        String language = scanner.nextLine().trim();
        if (language.isEmpty()) {
            language = "日语";
        }
        System.out.println("使用语言: " + language);
        
        // 询问管理员token
        System.out.print("\n请输入管理员token: ");
        String token = scanner.nextLine().trim();
        if (token.isEmpty()) {
            System.out.println("警告: 未输入token，可能会因未授权而失败");
        }
        System.out.println();
        
        int successCount = 0;
        int failCount = 0;

        for (String mp3File : mp3Files) {
            try {
                String fileName = new File(mp3File).getName();
                System.out.println("\n处理: " + fileName);
                
                // 先查找对应的歌词文件
                String lrcFile = findMatchingLrcFile(mp3File, lrcFiles);
                
                if (lrcFile == null) {
                    System.out.println("  跳过: 未找到对应的歌词文件");
                    failCount++;
                    continue;
                }
                
                System.out.println("  找到歌词文件: " + new File(lrcFile).getName());
                
                // 上传音乐
                if (uploadSingleMusic(mp3File, lrcFile, language, token)) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                System.err.println("  处理失败: " + mp3File + " - " + e.getMessage());
                failCount++;
            }
        }

        System.out.println("\n上传结果: 成功 " + successCount + " 个, 失败 " + failCount + " 个");
        System.out.println("上传完成！");
    }
    
    private static String findMatchingLrcFile(String mp3File, List<String> lrcFiles) {
        String mp3Name = new File(mp3File).getName();
        String baseName = mp3Name.substring(0, mp3Name.lastIndexOf('.'));
        
        System.out.println("  查找歌词文件:");
        System.out.println("    MP3文件名: " + mp3Name);
        System.out.println("    MP3基础名: " + baseName);
        System.out.println("    可用歌词文件数: " + lrcFiles.size());
        
        // 尝试精确匹配
        for (String lrcFile : lrcFiles) {
            String lrcName = new File(lrcFile).getName();
            String lrcBaseName = lrcName.substring(0, lrcName.lastIndexOf('.'));
            
            System.out.println("    检查: " + lrcBaseName);
            
            if (baseName.equals(lrcBaseName)) {
                System.out.println("    ✓ 精确匹配成功: " + lrcName);
                return lrcFile;
            }
        }
        
        // 尝试模糊匹配（忽略大小写和特殊字符）
        String normalizedBaseName = baseName.toLowerCase()
            .replaceAll("[\\s\\-_\\[\\]()（）]", "");
        
        System.out.println("    尝试模糊匹配，规范化名称: " + normalizedBaseName);
        
        for (String lrcFile : lrcFiles) {
            String lrcName = new File(lrcFile).getName();
            String lrcBaseName = lrcName.substring(0, lrcName.lastIndexOf('.'));
            String normalizedLrcName = lrcBaseName.toLowerCase()
                .replaceAll("[\\s\\-_\\[\\]()（）]", "");
            
            if (normalizedBaseName.equals(normalizedLrcName)) {
                System.out.println("    ✓ 模糊匹配成功: " + lrcName);
                return lrcFile;
            }
        }
        
        System.out.println("    ✗ 未找到匹配的歌词文件");
        return null;
    }
    
    private static boolean uploadSingleMusic(String mp3File, String lrcFile, String language, String token) {
        try {
            Path mp3Path = Paths.get(mp3File);
            Path lrcPath = Paths.get(lrcFile);
            String mp3FileName = mp3Path.getFileName().toString();
            String lrcFileName = lrcPath.getFileName().toString();
            
            // 使用JAudiotagger解析MP3文件元数据
            org.jaudiotagger.audio.AudioFile audioFile = org.jaudiotagger.audio.AudioFileIO.read(mp3Path.toFile());
            org.jaudiotagger.tag.Tag tag = audioFile.getTag();
            org.jaudiotagger.audio.AudioHeader audioHeader = audioFile.getAudioHeader();
            
            // 提取元数据
            String title = tag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE);
            String artist = tag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST);
            String album = tag.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM);
            int duration = audioHeader.getTrackLength();
            
            // 如果没有提取到标题或艺术家，从文件名提取
            if (title == null || title.isEmpty() || artist == null || artist.isEmpty()) {
                if (mp3FileName.contains(" - ")) {
                    String[] parts = mp3FileName.split(" - ", 2);
                    if (parts.length == 2) {
                        if (artist == null || artist.isEmpty()) {
                            artist = parts[0].trim();
                        }
                        if (title == null || title.isEmpty()) {
                            title = parts[1].substring(0, parts[1].lastIndexOf('.')).trim();
                        }
                    }
                } else {
                    if (title == null || title.isEmpty()) {
                        title = mp3FileName.substring(0, mp3FileName.lastIndexOf('.'));
                    }
                }
            }
            
            // 默认值
            if (title == null || title.isEmpty()) title = "未知标题";
            if (artist == null || artist.isEmpty()) artist = "未知艺术家";
            if (album == null || album.isEmpty()) album = "未知专辑";
            
            // 显示音乐信息
            System.out.println("  音乐信息:");
            System.out.println("    标题: " + title);
            System.out.println("    艺术家: " + artist);
            System.out.println("    专辑: " + album);
            System.out.println("    时长: " + duration + " 秒");
            System.out.println("    语言: " + language);
            
            // 构建multipart请求
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
            
            // 构建multipart/form-data请求体
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            StringBuilder requestBody = new StringBuilder();
            
            // 添加title字段
            requestBody.append("--").append(boundary).append("\r\n");
            requestBody.append("Content-Disposition: form-data; name=\"title\"\r\n\r\n");
            requestBody.append(title).append("\r\n");
            
            // 添加artist字段
            requestBody.append("--").append(boundary).append("\r\n");
            requestBody.append("Content-Disposition: form-data; name=\"artist\"\r\n\r\n");
            requestBody.append(artist).append("\r\n");
            
            // 添加album字段
            requestBody.append("--").append(boundary).append("\r\n");
            requestBody.append("Content-Disposition: form-data; name=\"album\"\r\n\r\n");
            requestBody.append(album).append("\r\n");
            
            // 添加language字段
            requestBody.append("--").append(boundary).append("\r\n");
            requestBody.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
            requestBody.append(language).append("\r\n");
            
            // 添加duration字段
            requestBody.append("--").append(boundary).append("\r\n");
            requestBody.append("Content-Disposition: form-data; name=\"duration\"\r\n\r\n");
            requestBody.append(duration).append("\r\n");
            
            // 添加musicFile字段
            requestBody.append("--").append(boundary).append("\r\n");
            requestBody.append("Content-Disposition: form-data; name=\"musicFile\"; filename=\"")
                   .append(mp3FileName).append("\"\r\n");
            requestBody.append("Content-Type: audio/mpeg\r\n\r\n");
            
            // 读取MP3文件内容
            byte[] mp3Bytes = Files.readAllBytes(mp3Path);
            requestBody.append(new String(mp3Bytes, "ISO-8859-1")).append("\r\n");
            
            // 添加lyricsFile字段
            requestBody.append("--").append(boundary).append("\r\n");
            requestBody.append("Content-Disposition: form-data; name=\"lyricsFile\"; filename=\"")
                   .append(lrcFileName).append("\"\r\n");
            requestBody.append("Content-Type: text/plain\r\n\r\n");
            
            byte[] lrcBytes = Files.readAllBytes(lrcPath);
            requestBody.append(new String(lrcBytes, "UTF-8")).append("\r\n");
            
            // 结束boundary
            requestBody.append("--").append(boundary).append("--\r\n");
            
            // 创建请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + UPLOAD_ENDPOINT))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofString(requestBody.toString()));
            
            // 如果提供了token，添加Authorization头
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
            
            HttpRequest request = requestBuilder.build();
            
            // 发送请求
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("  响应状态: " + response.statusCode());
            System.out.println("  响应内容: " + response.body());
            
            // 检查响应
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                if (responseBody.contains("\"success\":true")) {
                    System.out.println("  上传成功！");
                    return true;
                } else {
                    System.out.println("  上传失败: " + responseBody);
                    return false;
                }
            } else {
                System.out.println("  上传失败，状态码: " + response.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("  上传异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}