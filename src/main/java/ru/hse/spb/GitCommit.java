package ru.hse.spb;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GitCommit {
    String author;
    String date;
    String message;
    GitTree gitTree = null;
    GitCommit parrent = null;

    GitCommit(String message, GitTree gitTree, GitCommit parrent) {
        author = System.getProperty("user.name");
        date = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss").format(new Date());
        this.message = message;
        this.gitTree = gitTree;
        this.parrent = parrent;
    }

    GitCommit(String author, String date, String message, GitTree gitTree, GitCommit parrent) {
        this.author = author;
        this.date = date;
        this.message = message;
        this.gitTree = gitTree;
        this.parrent = parrent;
    }
}
