import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class NcmDecryptor {
    private static final String MAGIC = "CTEN";
    private static final String DECRYPT_KEY = "neteasecloudmusic";

    public static boolean decrypt(String ncmFilePath) {
        try {
            File ncmFile = new File(ncmFilePath);
            String mp3FilePath = ncmFilePath.substring(0, ncmFilePath.lastIndexOf('.')) + ".mp3";
            File mp3File = new File(mp3FilePath);

            if (mp3File.exists()) {
                System.out.println("跳过: " + ncmFile.getName() + " (已存在 MP3 文件)");
                return false;
            }

            System.out.println("解密: " + ncmFile.getName() + " -> " + mp3File.getName());

            byte[] ncmData = Files.readAllBytes(ncmFile.toPath());
            byte[] decryptedData = decryptNcmFile(ncmData);

            if (decryptedData == null || decryptedData.length == 0) {
                System.err.println("解密失败: " + ncmFile.getName());
                return false;
            }

            Files.write(mp3File.toPath(), decryptedData);
            ncmFile.delete();

            return true;
        } catch (Exception e) {
            System.err.println("解密 NCM 文件时出错: " + new File(ncmFilePath).getName() + " - " + e.getMessage());
            return false;
        }
    }

    private static byte[] decryptNcmFile(byte[] ncmData) {
        try {
            if (ncmData.length < 10) {
                System.err.println("文件太小");
                return null;
            }

            if (!isValidNcmFile(ncmData)) {
                System.err.println("不是有效的 NCM 文件");
                return null;
            }

            int offset = parseNcmHeader(ncmData);
            if (offset == -1 || offset >= ncmData.length) {
                System.err.println("无法解析 NCM 文件头");
                return null;
            }

            byte[] encryptedData = new byte[ncmData.length - offset];
            System.arraycopy(ncmData, offset, encryptedData, 0, encryptedData.length);

            return xorDecrypt(encryptedData, DECRYPT_KEY.getBytes("UTF-8"));

        } catch (Exception e) {
            System.err.println("解密数据时出错: " + e.getMessage());
            return null;
        }
    }

    private static boolean isValidNcmFile(byte[] data) {
        return data[0] == 'C' && data[1] == 'T' && data[2] == 'E' && data[3] == 'N';
    }

    private static int parseNcmHeader(byte[] data) {
        try {
            int offset = 8;
            
            int keyLen = readInt(data, 4);
            if (keyLen <= 0 || keyLen > data.length - 8) {
                return -1;
            }
            offset += keyLen;
            
            int metadataLen = readInt(data, offset);
            offset += 4 + metadataLen;
            
            offset += 4;
            
            int imageInfoLen = readInt(data, offset);
            offset += 4 + imageInfoLen;
            
            return offset;
        } catch (Exception e) {
            return -1;
        }
    }

    private static int readInt(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return 0;
        }
        return (data[offset] & 0xFF) |
               ((data[offset + 1] & 0xFF) << 8) |
               ((data[offset + 2] & 0xFF) << 16) |
               ((data[offset + 3] & 0xFF) << 24);
    }

    private static byte[] xorDecrypt(byte[] data, byte[] key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return result;
    }
}