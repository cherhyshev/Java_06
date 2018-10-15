package ru.hse.spb;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


public class GitApp {
    public static void run(String[] args) throws IOException, NoSuchAlgorithmException {
//        System.out.println(Utils.save(new ByteArrayInputStream("kek".getBytes(StandardCharsets.UTF_8))));
        switch (args[0].toLowerCase()) {
            case "init":
                GitCommands.init();
                break;
            case "add":
                GitCommands.add(Arrays.copyOfRange(args, 1, args.length));
                break;
            case "rm":
                GitCommands.rm(Arrays.copyOfRange(args, 1, args.length));
                break;
            case "status":
                GitCommands.status();
                break;
            case "commit":
                GitCommands.commit(args[1]);
                break;
            case "log":
                if (args.length == 1) {
                    GitCommands.log();
                } else {
                    GitCommands.log(args[1]);
                }
                break;
            case "reset":
                GitCommands.reset(args[1]);
                break;
            case "checkout":
                if (args[1].equals("--")) {
                    GitCommands.checkoutFiles(Arrays.copyOfRange(args, 2, args.length));
                } else if (args[1].equals("-b")) {
                    GitCommands.createNewBranchAndCheckout(args[2]);
                } else if (args[1].length() == 40) {
                    GitCommands.checkout(args[1]);
                } else {
                    GitCommands.checkoutToBranch(args[1]);
                }
                break;
            case "branch":
                if (args.length == 1) {
                    GitCommands.listBranches();
                } else if (args[1].equals("-d")) {
                    GitCommands.deleteBranchAndCheckoutToMaster(args[2]);
                }

            case "merge":
                GitCommands.merge(args[1]);


            default:
                throw new IllegalArgumentException("Invalid command: " + args[0].toLowerCase());

        }
    }
}
