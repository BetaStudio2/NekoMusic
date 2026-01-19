import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static String backendUrl;

    public static void main(String[] args) {
        try {
            backendUrl = ConfigLoader.loadBackendUrl();
            System.out.println("后端地址: " + backendUrl);

            Scanner scanner = new Scanner(System.in);
            Path musicPath = InputHandler.getValidPath(scanner);
            
            FileScanner.ScanResult scanResult = FileScanner.scanDirectory(musicPath);
            displayScanResults(scanResult);

            handleUserChoice(scanner, musicPath, scanResult);

        } catch (Exception e) {
            System.err.println("程序运行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void displayScanResults(FileScanner.ScanResult scanResult) {
        int totalMusic = scanResult.getTotalMusic();
        int musicWithLyrics = FileScanner.countMusicWithLyrics(
            scanResult.getMp3Files(), 
            scanResult.getFlacFiles(),
            scanResult.getWavFiles(),
            scanResult.getNcmFiles(), 
            scanResult.getLrcFiles()
        );

        System.out.println("\n扫描结果:");
        System.out.println("找到了 " + totalMusic + " 首音乐");
        System.out.println("其中 " + scanResult.getMp3Files().size() + " 首是 MP3 格式");
        System.out.println("其中 " + scanResult.getFlacFiles().size() + " 首是 FLAC 格式");
        System.out.println("其中 " + scanResult.getWavFiles().size() + " 首是 WAV 格式");
        System.out.println("其中 " + scanResult.getNcmFiles().size() + " 首音乐是 ncm 加密格式");
        System.out.println("其中 " + musicWithLyrics + " 首音乐存在歌词");
    }

    private static void handleUserChoice(Scanner scanner, Path musicPath, FileScanner.ScanResult scanResult) throws IOException {
        int choice = InputHandler.getUserChoice(scanner);
        
        switch (choice) {
            case 1:
                decryptNcmFiles(scanResult.getNcmFiles());
                break;
            case 2:
                MusicUploader.uploadMusic(
                    scanResult.getMp3Files(), 
                    scanResult.getFlacFiles(),
                    scanResult.getWavFiles(),
                    scanResult.getLrcFiles(), 
                    backendUrl
                );
                break;
            case 3:
                int decryptedCount = decryptNcmFiles(scanResult.getNcmFiles());
                if (decryptedCount > 0) {
                    FileScanner.ScanResult newScanResult = FileScanner.scanDirectory(musicPath);
                    MusicUploader.uploadMusic(
                        newScanResult.getMp3Files(), 
                        newScanResult.getFlacFiles(),
                        newScanResult.getWavFiles(),
                        newScanResult.getLrcFiles(), 
                        backendUrl
                    );
                }
                break;
            default:
                System.out.println("无效的选项");
                break;
        }
    }

    private static int decryptNcmFiles(List<String> ncmFiles) {
        if (ncmFiles.isEmpty()) {
            System.out.println("没有需要解密的 NCM 文件");
            return 0;
        }

        System.out.println("\n开始解密 NCM 文件...");
        int successCount = 0;
        for (String ncmFile : ncmFiles) {
            if (NcmDecryptor.decrypt(ncmFile)) {
                successCount++;
            }
        }
        System.out.println("成功解密 " + successCount + " 个 NCM 文件");
        return successCount;
    }
}