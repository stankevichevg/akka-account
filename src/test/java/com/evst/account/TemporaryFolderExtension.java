package com.evst.account;

import java.io.File;
import java.io.IOException;

/**
 * To save snapshots and event logs it tests, it's required to have temporal file storage.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class TemporaryFolderExtension {

    private final File parentFolder;
    private File folder;

    public TemporaryFolderExtension() {
        this(null);
    }

    public TemporaryFolderExtension(File parentFolder) {
        this.parentFolder = parentFolder;
    }

    public void cleanUp() {
        if (folder != null) {
            recursiveDelete(folder);
        }
    }

    public void create() throws IOException {
        folder = File.createTempFile("junit", null, parentFolder);
        folder.delete();
        folder.mkdir();
    }

    /**
     * Creates new folder.
     *
     * @param folderName folder name
     * @return file representing created folder
     */
    public File newFolder(String folderName) {
        File file = getRoot();
        file = new File(file, folderName);
        file.mkdir();
        return file;
    }

    private void recursiveDelete(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File each : files) {
                recursiveDelete(each);
            }
        }
        file.delete();
    }

    public File getRoot() {
        if (folder == null) {
            throw new IllegalStateException("create the folder first");
        }
        return folder;
    }

}
