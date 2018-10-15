package ru.hse.spb;

import java.io.*;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static ru.hse.spb.GitManager.*;

public class GitCommands {

    private GitCommands() {
    }

    /**
     * @return проинициализированна ли папка .gitapp относительно директории запуска
     */
    private static boolean isInitialized() {
        return Files.exists(gitAppPath) && Files.isDirectory(gitAppPath);
    }

    /**
     * Создает в текущей папке директорию .gitapp и инициализирует в ней
     * файлы для хранения объектов системы контроля версий
     */
    static void init() {
        if (isInitialized()) {
            System.out.println("Error! GitApp is already initialized in this directory.");
            return;
        }
        try {
            Files.createDirectories(gitAppPath);
            Files.createDirectories(gitObjectsPath);
            Files.createDirectories(gitBranchesPath);
            Files.createFile(gitHeadPath);
            writeToFile(gitHeadPath, "master");
            Files.createFile(Paths.get(gitBranchesPath.getFileName() + File.separator + "master"));
            writeToFile(Paths.get(gitBranchesPath.getFileName() + File.separator + "master"), "null");
            Files.createFile(gitStagingPath);
            setStaging(new GitTree());
            System.out.println("Initializing completed successfully!");
        } catch (IOException | NoSuchAlgorithmException e) {
            System.out.println("Error while initializing!");
        }
    }

    /**
     * Добавляет файлы в текущий staging
     *
     * @param fileNames - массив имен файлов для добавления в staging (снимок состояния рабочей директории)
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static void add(String[] fileNames) throws IOException, NoSuchAlgorithmException {

        GitTree gitTree = getStaging();
        for (String fileName : fileNames) {
            String relativeFileName = new File(System.getProperty("user.dir")).toURI()
                    .relativize(new File(fileName).toURI()).getPath();
            gitTree.filesMap.put(relativeFileName, new ByteArrayInputStream(Files.readAllBytes(Paths.get(relativeFileName))));
        }
        setStaging(gitTree);
    }

    /**
     * Удаляет файлы из текущего staging
     *
     * @param fileNames - массив имен файлов для удаления из staging
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static void rm(String[] fileNames) throws IOException, NoSuchAlgorithmException {
        GitTree gitTree = getStaging();
        for (String fileName : fileNames) {
            String relativeFileName = new File(System.getProperty("user.dir")).toURI()
                    .relativize(new File(fileName).toURI()).getPath();
            gitTree.filesMap.remove(relativeFileName);
        }
        setStaging(gitTree);

    }

    /**
     * Создает объект commit из сообщения, текущего staging, и родитель - предыдущий коммит в этой ветке
     *
     * @param message - сообщение, записываемое в commit
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static void commit(String message) throws IOException, NoSuchAlgorithmException {
        GitCommit gitCommit = new GitCommit(message, getStaging(), getLastCommit());
        saveCommit(gitCommit);
        setLastCommit(gitCommit);
    }

    /**
     * Проходит рекурсивно по всем файлам в рабочей директории, сравнивает со staging и
     * выводит состояние файла относительно staging: создан, изменен или удален
     *
     * @throws IOException
     */
    static void status() throws IOException {
        System.out.println("On branch " + fileContentFromPath(gitHeadPath));
        GitTree gitTree = getStaging();

        Files.find(Paths.get(System.getProperty("user.dir")), 999, (p, bfa) -> bfa.isRegularFile())
                .map(f -> new File(System.getProperty("user.dir")).toURI().relativize(f.toUri()).getPath())
                .forEach(f -> {
                    if (!gitTree.filesMap.containsKey(f)) {
                        System.out.println("New:\t" + f);
                    } else {
                        InputStream is = gitTree.filesMap.get(f);
                        String workdirContents = null;
                        try {
                            workdirContents = stringFromInputStream(is);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        String stagingContents = null;
                        try {
                            stagingContents = fileContentFromPath(Paths.get(System.getProperty("user.dir") + File.separator + f));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (!workdirContents.equals(stagingContents)) {
                            System.out.println("Modified:\t" + f);
                        }
                        gitTree.filesMap.remove(f);
                    }
                });
        for (String filename : gitTree.filesMap.keySet()) {
            System.out.println("Deleted:\t" + filename);
        }
    }

    /**
     * Выводит лог последнего коммита в текущей ветке
     *
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static void log() throws IOException, NoSuchAlgorithmException {
        log(getLastCommitSHA());
    }

    /**
     * Выводит рекурсивно коммит для данного SHA1 и всех родительских коммитов по отношению к данному
     *
     * @param revision - SHA1 коммита, лог которого требуется вывести
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static void log(String revision) throws IOException, NoSuchAlgorithmException {
        GitCommit gitCommit = loadCommit(new CommitHash(revision));
        for (GitCommit tmp = gitCommit; tmp != null; tmp = tmp.parrent) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("commit ")
                    .append(saveCommit(tmp).sha1)
                    .append('\n')
                    .append(tmp.author)
                    .append('\n')
                    .append(tmp.date)
                    .append('\n')
                    .append(tmp.message)
                    .append('\n');
            System.out.println(stringBuilder.toString());
        }

    }

    /**
     * Заменяет содержимое текущей рабочей директории на содержимое коммита по SHA1, заменяет staging
     *
     * @param revision - SHA1 коммита, к которому сбрасываемся
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static void reset(String revision) throws IOException, NoSuchAlgorithmException {
        GitCommit gitCommit = loadCommit(new CommitHash(revision));
        clearWorkingDirectory();
        GitTree newWorkingTree = gitCommit.gitTree;
        setStaging(newWorkingTree);
        for (Map.Entry<String, InputStream> entry : newWorkingTree.filesMap.entrySet()) {
            String fileName = entry.getKey();
            InputStream inputStream = entry.getValue();
            writeToFile(Paths.get(fileName), stringFromInputStream(inputStream));
        }
    }

    /**
     * Сдвигает текущую ветку на коммит с данным SHA1, обновляет staging из этого коммита.
     * Не изменяет файлы в рабочей директории.
     *
     * @param revision - SHA1 коммита, по которому делаем checkout
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static void checkout(String revision) throws IOException, NoSuchAlgorithmException {
        GitCommit gitCommit = loadCommit(new CommitHash(revision));
        GitTree newWorkingTree = gitCommit.gitTree;
        setStaging(newWorkingTree);
        String currentBranch = getHead();
        writeToFile(Paths.get(gitBranchesPath.getFileName() + File.separator + currentBranch), revision);

    }

    /**
     * Выкачивает файлы из текущего staging и заменяет ими имеющиеся с совпадающими именами.
     *
     * @param filenames - список имен файлов, подлежащих замене
     * @throws IOException
     */
    static void checkoutFiles(String[] filenames) throws IOException {
        Set<String> filenamesSet = new HashSet<>(Arrays.asList(filenames));
        GitTree gitTree = getStaging();
        for (Map.Entry<String, InputStream> entry : gitTree.filesMap.entrySet()) {
            String fileName = entry.getKey();
            InputStream inputStream = entry.getValue();
            if (filenamesSet.contains(fileName)) {
                writeToFile(Paths.get(fileName), stringFromInputStream(inputStream));
            }
        }

    }

    /**
     * Изменяет текущую ветку на branchName, очищает рабочую папку,
     * обновляет staging и копирует файлы из него в рабочую папку
     *
     * @param branchName - имя ветки для переключения
     * @throws IOException
     */
    static void checkoutToBranch(String branchName) throws IOException, NoSuchAlgorithmException {
        setHead(branchName);
        clearWorkingDirectory();
        String lastCommit = fileContentFromPath(Paths.get(gitBranchesPath.getFileName() + File.separator + branchName));
        GitCommit gitCommit = loadCommit(new CommitHash(lastCommit));
        setStaging(gitCommit.gitTree);
        for (Map.Entry<String, InputStream> entry : gitCommit.gitTree.filesMap.entrySet()) {
            String fileName = entry.getKey();
            InputStream inputStream = entry.getValue();
            writeToFile(Paths.get(fileName), stringFromInputStream(inputStream));
        }
    }

    /**
     * Создает новую ветку и переключается на нее
     *
     * @param branchName - имя новой ветки
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static void createNewBranchAndCheckout(String branchName) throws IOException, NoSuchAlgorithmException {
        Files.createFile(Paths.get(gitBranchesPath.getFileName() + File.separator + branchName));
        writeToFile(Paths.get(gitBranchesPath.getFileName() + File.separator + branchName), "null");
        setStaging(new GitTree());
        checkoutToBranch(branchName);
        System.out.println("Switch to branch " + branchName);
    }

    /**
     * Удаляет текущую ветку, делает checkout по master
     *
     * @param branchName - имя ветки для удаления
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static void deleteBranchAndCheckoutToMaster(String branchName) throws IOException, NoSuchAlgorithmException {
        Files.delete(Paths.get(gitBranchesPath.getFileName() + File.separator + branchName));
        checkoutToBranch("master");
    }

    /**
     * Выводит список веток
     *
     * @throws IOException
     */
    public static void listBranches() throws IOException {
        System.out.println("List of branches: \n");
        Files.find(gitBranchesPath, 999, (p, bfa) -> bfa.isRegularFile())
                .filter(p -> p.toFile().isFile()).forEach(p -> System.out.println(p.getFileName().toString()));
    }

    /**
     * Сравнивает файлы из ветки otherBranch с текущей, при наличии конфликтов предлагает выбрать,
     * какую из версий поместить в merge.
     *
     * @param otherBranch - имя ветки для слияния
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public static void merge(String otherBranch) throws IOException, NoSuchAlgorithmException {
        GitCommit lastCommit = getLastCommit();
        GitTree lastCommitTree = lastCommit.gitTree;
        GitCommit otherBranchLastCommit = loadCommit(new CommitHash(fileContentFromPath(
                Paths.get(gitBranchesPath.getFileName() + File.separator + otherBranch))));
        for (Map.Entry<String, InputStream> entry : otherBranchLastCommit.gitTree.filesMap.entrySet()) {
            String fileName = entry.getKey();
            InputStream inputStream = entry.getValue();
            if (lastCommitTree.filesMap.containsKey(fileName)) {
                String currentString = stringFromInputStream(lastCommitTree.filesMap.get(fileName));
                String mergeString = stringFromInputStream(inputStream);
                if (!currentString.equals(mergeString)) {
                    System.out.println("Merge conflict of file " + fileName + " in branches " + getHead() + " and " + otherBranch);
                    System.out.println("Enter 1 to leave version in branch " + getHead() + " or 2 to leave version in branch " + otherBranch);
                    Scanner scanner = new Scanner(System.in);
                    int choice = scanner.nextInt();
                    switch (choice) {
                        case 1:
                            break;
                        case 2:
                            lastCommitTree.filesMap.remove(fileName);
                            lastCommitTree.filesMap.put(fileName, inputStream);
                            break;
                        default:
                            System.out.println("Bad input! Enter 1 or 2.");
                    }
                }
            }
        }
        setStaging(lastCommitTree);
        setLastCommit(lastCommit);
    }
}
