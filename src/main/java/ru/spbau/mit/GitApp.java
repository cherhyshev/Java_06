package ru.spbau.mit;


import java.util.Arrays;

public class GitApp {
    public static void run(String[] args) {
        switch (args[0].toLowerCase()) {
            case "init":
                GitUtils.init();
                break;
            case "commit":
                GitUtils.commit(args[1], Arrays.copyOfRange(args, 2, args.length));
                break;
            case "log":
                if (args.length == 1) {
                    GitUtils.log();
                } else {
                    GitUtils.log(args[1]);
                }
                break;
            case "reset":
                GitUtils.reset(args[1]);
                break;
            case "checkout":
                GitUtils.checkout(args[1]);
                break;
            default:
                throw new IllegalArgumentException("Invalid command: " + args[0].toLowerCase());

        }
    }
}
