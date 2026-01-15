import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class InputHandler {
    public static Path getValidPath(Scanner scanner) {
        while (true) {
            System.out.print("请输入音乐的本地路径（例如 D:\\CloudMusic）: ");
            String musicPath = scanner.nextLine().trim();
            
            if (musicPath.isEmpty()) {
                System.err.println("错误：路径不能为空");
                continue;
            }
            
            if (musicPath.startsWith("@")) {
                musicPath = musicPath.substring(1);
            }
            
            try {
                Path path = Paths.get(musicPath);
                if (!Files.exists(path)) {
                    System.err.println("错误：路径不存在");
                    continue;
                }
                if (!Files.isDirectory(path)) {
                    System.err.println("错误：路径不是目录");
                    continue;
                }
                return path;
            } catch (Exception e) {
                System.err.println("错误：路径格式不正确 - " + e.getMessage());
            }
        }
    }

    public static int getUserChoice(Scanner scanner) {
        System.out.println("\n请选择操作:");
        System.out.println("1. 解密 NCM 文件");
        System.out.println("2. 上传音乐");
        System.out.println("3. 解密并上传");
        System.out.print("请输入选项 (1/2/3): ");
        
        String choice = scanner.nextLine().trim();
        
        try {
            return Integer.parseInt(choice);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}