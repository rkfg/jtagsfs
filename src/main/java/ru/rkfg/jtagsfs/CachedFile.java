package ru.rkfg.jtagsfs;

import java.io.File;

public class CachedFile {
    private File file;
    private long created;

    public File getFile() {
        return file;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public CachedFile(File file) {
        super();
        this.file = file;
        this.created = System.currentTimeMillis();
    }

}

