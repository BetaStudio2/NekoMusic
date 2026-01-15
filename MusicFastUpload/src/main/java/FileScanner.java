import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileScanner {
    public static ScanResult scanDirectory(Path dir) throws IOException {
        List<String> mp3Files = new ArrayList<>();
        List<String> ncmFiles = new ArrayList<>();
        List<String> lrcFiles = new ArrayList<>();

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

        return new ScanResult(mp3Files, ncmFiles, lrcFiles);
    }

    public static String getBaseName(String filePath) {
        String fileName = new File(filePath).getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    public static int countMusicWithLyrics(List<String> mp3Files, List<String> ncmFiles, List<String> lrcFiles) {
        int count = 0;
        for (String mp3File : mp3Files) {
            if (hasMatchingLyrics(getBaseName(mp3File), lrcFiles)) {
                count++;
            }
        }
        for (String ncmFile : ncmFiles) {
            if (hasMatchingLyrics(getBaseName(ncmFile), lrcFiles)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasMatchingLyrics(String baseName, List<String> lrcFiles) {
        for (String lrcFile : lrcFiles) {
            if (getBaseName(lrcFile).equalsIgnoreCase(baseName)) {
                return true;
            }
        }
        return false;
    }

    public static class ScanResult {
        private final List<String> mp3Files;
        private final List<String> ncmFiles;
        private final List<String> lrcFiles;

        public ScanResult(List<String> mp3Files, List<String> ncmFiles, List<String> lrcFiles) {
            this.mp3Files = mp3Files;
            this.ncmFiles = ncmFiles;
            this.lrcFiles = lrcFiles;
        }

        public List<String> getMp3Files() {
            return mp3Files;
        }

        public List<String> getNcmFiles() {
            return ncmFiles;
        }

        public List<String> getLrcFiles() {
            return lrcFiles;
        }

        public int getTotalMusic() {
            return mp3Files.size() + ncmFiles.size();
        }
    }
}