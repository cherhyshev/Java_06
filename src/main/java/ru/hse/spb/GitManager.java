package ru.hse.spb;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

class GitManager {
    final static Path gitAppPath = Paths.get(System.getProperty("user.dir") + File.separator + ".gitapp");
    final static Path gitObjectsPath = Paths.get(gitAppPath.getFileName() + File.separator + "objects");
    final static Path gitBranchesPath = Paths.get(gitAppPath.getFileName() + File.separator + "branches");
    final static Path gitHeadPath = Paths.get(gitAppPath.getFileName() + File.separator + "HEAD");
    final static Path gitStagingPath = Paths.get(gitAppPath.getFileName() + File.separator + "staging");


    static String getHead() throws IOException {
        return fileContentFromPath(gitHeadPath);
    }

    static void setHead(String newHead) throws IOException {
        writeToFile(gitHeadPath, newHead);
    }

    static GitCommit getLastCommit() throws IOException {
        return loadCommit(
                new CommitHash(fileContentFromPath(Paths.get(gitBranchesPath.getFileName() + File.separator + getHead()))));
    }

    static String getLastCommitSHA() throws IOException, NoSuchAlgorithmException {
        return saveCommit(getLastCommit()).sha1;
    }

    static void setLastCommit(GitCommit gitCommit) throws IOException, NoSuchAlgorithmException {
        writeToFile(Paths.get(gitBranchesPath.getFileName() + File.separator + getHead()), saveCommit(gitCommit).sha1);

    }

    static GitTree getStaging() throws IOException {
        InputStream is = new ByteArrayInputStream(Files.readAllBytes(gitStagingPath));
        String s = stringFromInputStream(is);
        return loadTree(new TreeHash(s));
    }

    static void setStaging(GitTree gitTree) throws IOException, NoSuchAlgorithmException {
        writeToFile(gitStagingPath, saveTree(gitTree).sha1);
    }


    private static FileHash saveFile(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        return new FileHash(save(inputStream));
    }

    private static InputStream loadFile(FileHash fileHash) {
        return load(fileHash.sha1);
    }

    private static TreeHash saveTree(GitTree gitTree) throws IOException, NoSuchAlgorithmException {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, InputStream> entry : gitTree.filesMap.entrySet()) {
            String fileName = entry.getKey();
            InputStream inputStream = entry.getValue();
            stringBuilder.append(fileName)
                    .append(" ")
                    .append(saveFile(inputStream).sha1)
                    .append("\n");
        }
        return new TreeHash(save(new ByteArrayInputStream(stringBuilder.toString().getBytes(Charset.forName("UTF-8")))));

    }

    private static GitTree loadTree(TreeHash treeHash) throws IOException {
        InputStream loadStream = load(treeHash.sha1);
        String treeString = stringFromInputStream(loadStream);
        GitTree gitTree = new GitTree();
        Arrays.stream(treeString.split("\\r?\\n")).forEach(line -> {
            int index = line.indexOf(' ');
            int nextIndex = line.indexOf(' ', index + 1);
            String name = line.substring(0, index);
            String sha1 = line.substring(index + 1, nextIndex);
            gitTree.filesMap.put(name, loadFile(new FileHash(sha1)));
        });

        return gitTree;

    }

    static CommitHash saveCommit(GitCommit gitCommit) throws IOException, NoSuchAlgorithmException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(gitCommit.author)
                .append("\n")
                .append(gitCommit.date)
                .append("\n")
                .append(gitCommit.message)
                .append("\n")
                .append(saveTree(gitCommit.gitTree).sha1)
                .append("\n")
                .append((gitCommit.parrent != null) ? saveCommit(gitCommit.parrent).sha1 : "null");
        return new CommitHash(save(new ByteArrayInputStream(stringBuilder.toString().getBytes(Charset.forName("UTF-8")))));

    }

    static GitCommit loadCommit(CommitHash commitHash) throws IOException {
        if (commitHash.sha1 == "null") {
            return null;
        }

        InputStream commitStream = load(commitHash.sha1);
        String commitString = stringFromInputStream(commitStream);
        String[] lines = commitString.split("\\r?\\n");
        return new GitCommit(lines[0], lines[1], lines[2], loadTree(new TreeHash(lines[3])), loadCommit(new CommitHash(lines[4])));
    }

    static void clearWorkingDirectory() throws IOException {
        Files.find(Paths.get(System.getProperty("user.dir")), 999, (p, bfa) -> bfa.isRegularFile())
                .filter(path -> path.toFile().isFile())
                .forEach(path1 -> {
                    try {
                        Files.deleteIfExists(path1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    static void writeToFile(Path targetFilePath, String content) throws FileNotFoundException {
        try (PrintWriter writer = new PrintWriter(targetFilePath.toFile())) {
            writer.print(content);
        }

    }

    static String stringFromInputStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        return new String(buffer, StandardCharsets.UTF_8);
    }

    static String fileContentFromPath(Path filePath) throws IOException {
        return new String(Files.readAllBytes(filePath));
    }


    private static class FileHash {
        String sha1;

        FileHash(String sha1) {
            this.sha1 = sha1;
        }
    }

    private static class TreeHash {
        String sha1;

        TreeHash(String sha1) {
            this.sha1 = sha1;
        }
    }

    static class CommitHash {
        String sha1;

        CommitHash(String sha1) {
            this.sha1 = sha1;
        }

    }

    private static InputStream load(String sha1) {
        Path shaDirPath = Paths.get(gitObjectsPath.getFileSystem() + File.separator + sha1.substring(0, 2));
        if (Files.exists(shaDirPath) && Files.isDirectory(shaDirPath)) {
            Path shaFilePath = Paths.get(shaDirPath.toString() + File.separator + sha1.substring(2));

            if (Files.exists(shaFilePath) && !Files.isDirectory(shaFilePath)) {
                try {
                    return new ByteArrayInputStream(Files.readAllBytes(shaFilePath));
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

    private static String save(InputStream inputStream) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        String sha1;
        int n = 0;
        byte[] buffer = new byte[inputStream.available()];
        while (n != -1) {
            n = inputStream.read(buffer);
            if (n > 0) {
                digest.update(buffer, 0, n);
            }
        }

        sha1 = String.format("%040x", new BigInteger(1, digest.digest()));
        Path shaDir = Paths.get(gitObjectsPath.getFileSystem() + File.separator + sha1.substring(0, 2));

        Files.createDirectories(shaDir);
        Path shaFile = Paths.get(shaDir.toString() + File.separator + sha1.substring(2));
        Files.write(shaFile, buffer);

        return sha1;
    }

}
