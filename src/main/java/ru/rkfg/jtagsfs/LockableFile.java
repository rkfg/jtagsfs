package ru.rkfg.jtagsfs;

import java.io.RandomAccessFile;

public class LockableFile {
    int lockCount;
    RandomAccessFile stream;

    public LockableFile(RandomAccessFile stream) {
        this.stream = stream;
        lockCount = 1;
    }

    public RandomAccessFile getFile() {
        return stream;
    }

    public int lock() {
        return ++lockCount;
    }

    public int release() {
        return --lockCount;
    }

}

