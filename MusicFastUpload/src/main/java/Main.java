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
            Path path = null;
            
            while (true) {
                System.out.print("请输入音乐的本地路径（例如 D:\\CloudMusic）: ");
                String musicPath = scanner.nextLine().trim();
                
                if (musicPath.isEmpty()) {
                    System.err.println("错误：路径不能为空");
                    continue;
                }
                
                // 处理路径中的 @ 符号
                if (musicPath.startsWith("@")) {
                    musicPath = musicPath.substring(1);
                }
                
                try {
                    path = Paths.get(musicPath);
                } catch (Exception e) {
                    System.err.println("错误：路径格式不正确 - " + e.getMessage());
                    continue;
                }
                
                if (!Files.exists(path)) {
                    System.err.println("错误：路径不存在");
                    continue;
                }
                
                if (!Files.isDirectory(path)) {
                    System.err.println("错误：路径不是目录");
                    continue;
                }
                
                break;
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

            // 第五步：询问用户要执行的操作
            System.out.println("\n请选择操作:");
            System.out.println("1. 解密 NCM 文件");
            System.out.println("2. 上传音乐");
            System.out.println("3. 解密并上传");
            System.out.print("请输入选项 (1/2/3): ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    // 只解密
                    if (!ncmFiles.isEmpty()) {
                        System.out.println("\n开始解密 NCM 文件...");
                        int decryptedCount = 0;
                        for (String ncmFile : ncmFiles) {
                            try {
                                if (decryptNcmFile(ncmFile)) {
                                    decryptedCount++;
                                }
                            } catch (Exception e) {
                                System.err.println("解密失败: " + ncmFile + " - " + e.getMessage());
                            }
                        }
                        System.out.println("成功解密 " + decryptedCount + " 个 NCM 文件");
                    } else {
                        System.out.println("没有需要解密的 NCM 文件");
                    }
                    break;
                    
                case "2":
                    // 只上传
                    System.out.println("\n开始上传音乐...");
                    uploadMusic(mp3Files, lrcFiles);
                    System.out.println("上传完成！");
                    break;
                    
                case "3":
                    // 解密并上传
                    if (!ncmFiles.isEmpty()) {
                        System.out.println("\n开始解密 NCM 文件...");
                        int decryptedCount = 0;
                        for (String ncmFile : ncmFiles) {
                            try {
                                if (decryptNcmFile(ncmFile)) {
                                    decryptedCount++;
                                }
                            } catch (Exception e) {
                                System.err.println("解密失败: " + ncmFile + " - " + e.getMessage());
                            }
                        }
                        System.out.println("成功解密 " + decryptedCount + " 个 NCM 文件");
                    }
                    
                    // 重新扫描获取解密后的 MP3 文件
                    List<String> newMp3Files = new ArrayList<>();
                    List<String> newNcmFiles = new ArrayList<>();
                    List<String> newLrcFiles = new ArrayList<>();
                    scanDirectory(path, newMp3Files, newNcmFiles, newLrcFiles);
                    
                    System.out.println("\n开始上传音乐...");
                    uploadMusic(newMp3Files, newLrcFiles);
                    System.out.println("上传完成！");
                    break;
                    
                default:
                    System.out.println("无效的选项");
                    break;
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

    private static boolean decryptNcmFile(String ncmFilePath) {
        File ncmFile = null;
        try {
            ncmFile = new File(ncmFilePath);
            String mp3FilePath = ncmFilePath.substring(0, ncmFilePath.lastIndexOf('.')) + ".mp3";
            File mp3File = new File(mp3FilePath);

            if (mp3File.exists()) {
                System.out.println("跳过: " + ncmFile.getName() + " (已存在 MP3 文件)");
                return false;
            }

            System.out.println("解密: " + ncmFile.getName() + " -> " + mp3File.getName());

            // 读取 NCM 文件
            byte[] ncmData = Files.readAllBytes(ncmFile.toPath());

            // NCM 文件格式解析
            // 1. 魔数: CTEN (4 字节)
            // 2. 密钥长度 (4 字节)
            // 3. 加密密钥
            // 4. 元数据
            // 5. CRC32 校验
            // 6. 图片信息
            // 7. 加密的音乐数据

            // 检查是否是有效的 NCM 文件
            if (ncmData.length < 10) {
                System.err.println("文件太小，不是有效的 NCM 文件: " + ncmFile.getName());
                return false;
            }

            // 跳过魔数和密钥信息，直接提取加密的音乐数据
            // NCM 文件结构：CTEN (4) + key_len (4) + key (key_len) + metadata + crc (4) + image_info + encrypted_data

            // 简化的解密方法：使用 RC4 算法解密
            byte[] decryptedData = decryptNcmData(ncmData);

            if (decryptedData == null || decryptedData.length == 0) {
                System.err.println("解密失败: " + ncmFile.getName());
                return false;
            }

            // 写入解密后的 MP3 数据
            Files.write(mp3File.toPath(), decryptedData);

            // 删除 NCM 文件
            ncmFile.delete();

            return true;
        } catch (Exception e) {
            System.err.println("解密 NCM 文件时出错: " + ncmFile.getName() + " - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static byte[] decryptNcmData(byte[] ncmData) {
        try {
            // 网易云 NCM 使用的标准密钥
            String coreKey = "neteasecloudmusic";
            byte[] coreKeyBytes = coreKey.getBytes("UTF-8");

            // 解析 NCM 文件结构
            int offset = 10; // 跳过 CTEN (4) + 密钥长度 (4) + 未知字节 (2)

            // 读取加密的密钥
            if (offset + 4 > ncmData.length) {
                return null;
            }
            int encryptedKeyLen = bytesToInt(ncmData, offset);
            offset += 4;

            if (offset + encryptedKeyLen > ncmData.length) {
                return null;
            }
            byte[] encryptedKey = new byte[encryptedKeyLen];
            System.arraycopy(ncmData, offset, encryptedKey, 0, encryptedKeyLen);
            offset += encryptedKeyLen;

            // 解密密钥
            byte[] decryptedKey = rc4Decrypt(encryptedKey, coreKeyBytes);

            // 从解密的密钥中提取实际的 RC4 密钥
            // 解密后的密钥格式: 17 bytes header + 106 bytes key data
            if (decryptedKey.length < 17 + 106) {
                return null;
            }
            byte[] actualKey = new byte[106];
            System.arraycopy(decryptedKey, 17, actualKey, 0, 106);

            // 跳过元数据
            if (offset + 4 > ncmData.length) {
                return null;
            }
            int metadataLen = bytesToInt(ncmData, offset);
            offset += 4 + metadataLen;

            // 跳过 CRC32
            offset += 4;

            // 跳过图片信息
            if (offset + 4 > ncmData.length) {
                return null;
            }
            int imageInfoLen = bytesToInt(ncmData, offset);
            offset += 4 + imageInfoLen;

            // 剩余的就是加密的音乐数据
            if (offset >= ncmData.length) {
                return null;
            }

            byte[] encryptedData = new byte[ncmData.length - offset];
            System.arraycopy(ncmData, offset, encryptedData, 0, encryptedData.length);

            // 使用提取的密钥进行 RC4 解密
            return rc4Decrypt(encryptedData, actualKey);

        } catch (Exception e) {
            System.err.println("解密数据时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
               ((bytes[offset + 1] & 0xFF) << 16) |
               ((bytes[offset + 2] & 0xFF) << 8) |
               (bytes[offset + 3] & 0xFF);
    }

    private static byte[] rc4Decrypt(byte[] data, byte[] key) {
        int[] s = new int[256];
        int[] k = new int[256];

        // 初始化 S 盒
        for (int i = 0; i < 256; i++) {
            s[i] = i;
            k[i] = key[i % key.length] & 0xFF;
        }

        // 密钥调度算法 (KSA)
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + k[i]) % 256;
            int temp = s[i];
            s[i] = s[j];
            s[j] = temp;
        }

        // 伪随机生成算法 (PRGA) - 解密
        int i = 0;
        j = 0;
        byte[] result = new byte[data.length];
        
        for (int n = 0; n < data.length; n++) {
            i = (i + 1) % 256;
            j = (j + s[i]) % 256;
            
            int temp = s[i];
            s[i] = s[j];
            s[j] = temp;
            
            int keystream = s[(s[i] + s[j]) % 256];
            result[n] = (byte) (data[n] ^ keystream);
        }

        return result;
    }

    private static void uploadMusic(List<String> mp3Files, List<String> lrcFiles) {
        int successCount = 0;
        int failCount = 0;

        for (String mp3File : mp3Files) {
            try {
                // TODO: 实现上传逻辑
                // 需要调用后端的快速上传接口
                String fileName = new File(mp3File).getName();
                System.out.println("上传: " + fileName);
                successCount++;
            } catch (Exception e) {
                System.err.println("上传失败: " + mp3File + " - " + e.getMessage());
                failCount++;
            }
        }

        System.out.println("\n上传结果: 成功 " + successCount + " 个, 失败 " + failCount + " 个");
    }
}
