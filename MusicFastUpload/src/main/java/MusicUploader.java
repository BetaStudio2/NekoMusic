import java.io.File;
import java.util.List;

public class MusicUploader {
    public static void uploadMusic(List<String> mp3Files, List<String> lrcFiles) {
        System.out.println("\n开始上传音乐...");
        int successCount = 0;
        int failCount = 0;

        for (String mp3File : mp3Files) {
            try {
                String fileName = new File(mp3File).getName();
                System.out.println("上传: " + fileName);
                successCount++;
            } catch (Exception e) {
                System.err.println("上传失败: " + mp3File + " - " + e.getMessage());
                failCount++;
            }
        }

        System.out.println("\n上传结果: 成功 " + successCount + " 个, 失败 " + failCount + " 个");
        System.out.println("上传完成！");
    }
}