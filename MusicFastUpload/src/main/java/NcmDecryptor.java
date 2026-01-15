import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NcmDecryptor {
    private static final String MAGIC = "CTENFDAM";
    private static final String KEY_PREFIX = "neteasecloudmusic";
    
    // 核心 AES 密钥（网易云内置的固定密钥）
    private static final byte[] CORE_KEY = {
        0x68, 0x7A, 0x48, 0x52, 0x41, 0x6D, 0x73, 0x6F,
        0x35, 0x6B, 0x49, 0x6E, 0x62, 0x61, 0x78, 0x57
    };

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
            
            decryptNcmFile(ncmFile, mp3File);
            ncmFile.delete();

            return true;
        } catch (Exception e) {
            System.err.println("解密 NCM 文件时出错: " + new File(ncmFilePath).getName() + " - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static void decryptNcmFile(File ncmFile, File outFile) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(ncmFile, "r");
             RandomAccessFile out = new RandomAccessFile(outFile, "rw")) {

            // 1. 读取并验证魔数
            byte[] magicBytes = new byte[8];
            raf.readFully(magicBytes);
            String magic = new String(magicBytes, "UTF-8");
            
            System.out.println("魔数: " + magic);
            
            if (!MAGIC.equals(magic)) {
                throw new Exception("不是有效的 NCM 文件");
            }

            // 2. 读取版本号 (2 字节)
            raf.skipBytes(2);

            // 3. 解取 AES 密钥块
            int keyLen = readLittleEndianInt(raf);
            System.out.println("密钥块长度: " + keyLen);
            
            // 密钥块长度需要 padding 到 16 的倍数
            int paddedKeyLen = ((keyLen + 15) / 16) * 16;
            byte[] keyData = new byte[paddedKeyLen];
            raf.readFully(keyData, 0, keyLen);
            
            System.out.println("填充后密钥块长度: " + paddedKeyLen);
            
            // 对 keyData 进行按位异或 0x64 处理（只对实际读取的字节进行异或）
            for (int i = 0; i < keyLen; i++) {
                keyData[i] ^= 0x64;
            }
            
            // AES 解密得到真正用于 RC4 的密钥
            byte[] decryptedKey = aesDecrypt(keyData, CORE_KEY);
            System.out.println("解密后密钥长度: " + decryptedKey.length);
            
            // 去掉前缀 "neteasecloudmusic" (17 字节)
            byte[] finalKey;
            if (decryptedKey.length > 17) {
                finalKey = new byte[decryptedKey.length - 17];
                System.arraycopy(decryptedKey, 17, finalKey, 0, finalKey.length);
                
                // 去掉 PKCS7 填充
                int paddingLen = finalKey[finalKey.length - 1] & 0xff;
                if (paddingLen > 0 && paddingLen <= 16 && paddingLen < finalKey.length) {
                    byte[] unpaddedKey = new byte[finalKey.length - paddingLen];
                    System.arraycopy(finalKey, 0, unpaddedKey, 0, unpaddedKey.length);
                    finalKey = unpaddedKey;
                }
                
                System.out.println("最终密钥长度: " + finalKey.length);
            } else {
                throw new Exception("解密后的密钥长度不足");
            }
            
            // 4. 构建 S-Box
            int[] box = buildSBox(finalKey);

            // 5. 跳过元数据、CRC和封面块（这些块在当前NCM文件中不存在）
            // 从日志可以看出，当前NCM文件在密钥块之后直接就是音频数据
            // 文件指针位置应该已经是正确的位置，不需要跳过任何块
            System.out.println("当前文件位置: " + raf.getFilePointer() + " (音频数据起始位置)");

            // 6. 解密音频数据
            System.out.println("文件总长度: " + raf.length());
            
            // 读取所有音频数据
            long audioDataSize = raf.length() - raf.getFilePointer();
            byte[] audioData = new byte[(int) audioDataSize];
            raf.readFully(audioData);
            
            // 使用RC4算法解密音频数据
            // 创建S-Box的副本，因为RC4算法会修改S-Box
            int[] sBox = new int[256];
            System.arraycopy(box, 0, sBox, 0, 256);
            
            byte[] decryptedAudio = new byte[audioData.length];
            int i = 0;
            int j = 0;
            
            for (int k = 0; k < audioData.length; k++) {
                i = (i + 1) % 256;
                j = (j + sBox[i]) % 256;
                
                // 交换S-Box中的元素
                int temp = sBox[i];
                sBox[i] = sBox[j];
                sBox[j] = temp;
                
                // 生成密钥字节并异或
                int keyByte = (sBox[i] + sBox[j]) % 256;
                decryptedAudio[k] = (byte) (audioData[k] ^ keyByte);
            }
            
            // 写入解密后的音频数据
            out.write(decryptedAudio);
            
            System.out.println("解密音频数据: " + decryptedAudio.length + " 字节");
        }
    }

    private static int[] buildSBox(byte[] key) {
        int[] box = new int[256];
        for (int i = 0; i < 256; i++) {
            box[i] = i;
        }
        
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + box[i] + (key[i % key.length] & 0xff)) & 0xff;
            int temp = box[i];
            box[i] = box[j];
            box[j] = temp;
        }
        
        return box;
    }
    
    private static byte[] rc4Decrypt(byte[] data, int[] box) {
        int i = 0;
        int j = 0;
        byte[] result = new byte[data.length];
        
        for (int k = 0; k < data.length; k++) {
            i = (i + 1) % 256;
            j = (j + box[i]) % 256;
            
            int temp = box[i];
            box[i] = box[j];
            box[j] = temp;
            
            int keyByte = (box[i] + box[j]) % 256;
            result[k] = (byte) (data[k] ^ keyByte);
        }
        
        return result;
    }

    private static byte[] aesDecrypt(byte[] data, byte[] key) throws Exception {
        SecretKeySpec spec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, spec);
        return cipher.doFinal(data);
    }

    private static int readLittleEndianInt(RandomAccessFile raf) throws Exception {
        byte[] b = new byte[4];
        raf.readFully(b);
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        return bb.getInt();
    }
}