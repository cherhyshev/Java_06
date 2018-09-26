package ru.spbau.mit;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;

final class GitUtils {
    private final static Path gitAppPath = Paths.get(System.getProperty("user.dir") + File.separator + ".gitapp");

    private GitUtils() {
    }

    private static boolean isInitialized() {
        return Files.exists(gitAppPath) && Files.isDirectory(gitAppPath);
    }

    static void init() {
        if (isInitialized()) {
            System.out.println("Error! GitApp is already initialized in this directory.");
            return;
        }
        try {
            Files.createDirectories(Paths.get(gitAppPath.getFileName() + File.separator + "objects"));
            Files.createFile(Paths.get(gitAppPath.getFileName() + File.separator + "HEAD"));
            System.out.println("Initializing completed successfully!");
        } catch (IOException e) {
            System.out.println("Error while initializing!");
        }
    }

    static void commit(String message, String[] fileNames) {
        if (!isInitialized()) {
            System.out.println("Folder .gitapp not found");
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (String fileName : fileNames) {
            String sha1 = hashObject(fileName);
            if (sha1 != null) {
                stringBuilder.append("blob ").append(sha1).append(' ').append(fileName).append('\n');
            }
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
        stringBuilder.append(System.getProperty("user.name")).append(' ')
                .append(dateFormat.format(new Date())).append('\n')
                .append(message);

        String sha = null;

        try {
            sha = createSha1(new ByteArrayInputStream(stringBuilder.toString().getBytes()));
        } catch (Exception e) {
            System.out.println("Error while calculating SHA1 of commit");
        }

        if (sha != null) {
            Path shaDir = Paths.get(System.getProperty("user.dir") + File.separator + ".gitapp"
                    + File.separator + "objects" + File.separator + sha.substring(0, 2));
            try {
                Files.createDirectories(shaDir);
            } catch (IOException e) {
                System.out.println("Error while creating directory for commit " + sha);
            }

            Path shaFile = Paths.get(shaDir.toString() + File.separator + sha.substring(2));

            try {
                if (!Files.exists(shaFile)) {
                    Files.copy(new ByteArrayInputStream(stringBuilder.toString().getBytes()), shaFile);
                }
                PrintWriter writer = new PrintWriter(new File(".gitapp" + File.separator + "HEAD"));
                writer.print(sha);
                writer.close();

            } catch (IOException e) {
                System.out.println("Error while creating commit and writing into HEAD");
            }
        }

    }

    static void log() {
        try {
            log(new BufferedReader(new FileReader(new File(".gitapp" + File.separator + "HEAD"))).readLine());
        } catch (FileNotFoundException e) {
            System.out.println("Error in access to HEAD");
        } catch (IOException e) {
            System.out.println("Error while reading from HEAD");
        }
    }

    static void log(String revision) {
        String logContent = catFile(revision);
        if (logContent != null) {
            System.out.println(logContent);
        }
    }

    static void checkout(String revision) {
        reset(revision);
    }

    static void reset(String revision) {
        String commitContent = catFile(revision);
        if (commitContent != null) {
            PrintWriter writer;
            try {
                writer = new PrintWriter(new File(".gitapp" + File.separator + "HEAD"));
            } catch (FileNotFoundException e) {
                System.out.println("Error while rewriting HEAD");
                return;
            }
            writer.print(revision);
            writer.close();

            String lines[] = commitContent.split("\\r?\\n");
            for (String line : lines) {
                String[] lineData = line.split("\\s");
                String contentBySha = null;
                try {
                    contentBySha = catFile(lineData[1]);
                } catch (Exception ignored){}
                if (contentBySha == null) {
                    break;
                }
                try {
                    FileWriter resetWriter = new FileWriter(new File(lineData[2]), false);
                    resetWriter.write(contentBySha);
                    resetWriter.close();
                } catch (IOException e) {
                    System.out.println("Error while rewriting files, reset to " + revision);
                }
            }
        }

    }

    private static String hashObject(String fileName) {
        if (!isInitialized()) {
            System.out.println("Folder .gitapp not found");
            return null;
        }
        Path filePath = Paths.get(System.getProperty("user.dir") + File.separator + fileName);
        if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
            String sha1;
            try {
                sha1 = createSha1(new FileInputStream(filePath.toFile()));
            } catch (Exception e) {
                System.out.println("Error while calculating SHA1 of file " + fileName);
                return null;
            }

            if (sha1 != null) {

                Path shaDir = Paths.get(System.getProperty("user.dir") + File.separator + ".gitapp"
                        + File.separator + "objects" + File.separator + sha1.substring(0, 2));
                try {
                    Files.createDirectories(shaDir);
                } catch (IOException e) {
                    System.out.println("Error while creating directory for file " + fileName);
                    return null;
                }

                Path shaFile = Paths.get(shaDir.toString() + File.separator + sha1.substring(2));

                try {
                    if (!Files.exists(shaFile)) {
                        Files.copy(filePath, shaFile);
                    }
                    return sha1;
                } catch (IOException e) {
                    System.out.println("Error while copying content and renaming BlobFile");
                    return null;
                }
            }
            return null;

        } else {
            System.out.println("File \"" + filePath.getFileName() + "\" is a directory or does not exist!");
            return null;
        }

    }


    private static String catFile(String sha1) {
        Path shaDirPath = Paths.get(System.getProperty("user.dir") + File.separator + ".gitapp"
                + File.separator + "objects" + File.separator + sha1.substring(0, 2));
        if (Files.exists(shaDirPath) && Files.isDirectory(shaDirPath)) {
            Path shaFilePath = Paths.get(shaDirPath.toString() + File.separator + sha1.substring(2));

            if (Files.exists(shaFilePath) && !Files.isDirectory(shaFilePath)) {
                try {
                    return new String(Files.readAllBytes(shaFilePath));
                } catch (IOException e) {
                    return null;
                }
            } else {
                return null;
            }

        } else {
            return null;
        }

    }

    private static String createSha1(InputStream inputStream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        String sha1;
        int n = 0;
        byte[] buffer = new byte[8192];
        while (n != -1) {
            n = inputStream.read(buffer);
            if (n > 0) {
                digest.update(buffer, 0, n);
            }
        }
        sha1 = String.format("%040x", new BigInteger(1, digest.digest()));
        return sha1;
    }

}
