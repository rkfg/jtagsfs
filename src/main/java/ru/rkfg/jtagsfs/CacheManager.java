package ru.rkfg.jtagsfs;

import static ru.rkfg.jtagsfs.Consts.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public enum CacheManager {
    INSTANCE;

    private Map<String, CachedFile> fileCache = new HashMap<String, CachedFile>();
    private Map<String, LockableFile> fileStreamCache = new HashMap<String, LockableFile>();
    private Set<String> nonExistent = new HashSet<String>();

    private Timer cleanupTimer = new Timer("file cache cleanup");

    private CacheManager() {
        cleanupTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                synchronized (fileCache) {

                    long curTime = System.currentTimeMillis();
                    Iterator<Entry<String, CachedFile>> iter = fileCache.entrySet().iterator();
                    int count = 0;
                    while (iter.hasNext()) {
                        Entry<String, CachedFile> entry = iter.next();
                        CachedFile cachedFile = entry.getValue();
                        if (curTime - cachedFile.getCreated() > CACHECLEANUPPERIOD) {
                            iter.remove();
                            count++;
                        }
                    }
                    System.err.println("Cleaned " + count + " file records. " + fileStreamCache.size() + " retained.");
                }
            }
        }, CACHECLEANUPPERIOD, CACHECLEANUPPERIOD);
    }

    public void removeCachedFile(Filepath filepath) {
        synchronized (fileCache) {
            fileCache.remove(filepath.asStringPath());
        }
    }

    public CachedFile getCachedFile(String strPath) {
        synchronized (fileCache) {
            return fileCache.get(strPath);
        }
    }

    public void putCachedFile(String strPath, CachedFile file) {
        synchronized (fileCache) {
            fileCache.put(strPath, file);
        }
    }

    public LockableFile getStreamFile(String strPath) {
        synchronized (fileStreamCache) {
            return fileStreamCache.get(strPath);
        }
    }

    public void putStreamFile(String strPath, LockableFile lockableFile) {
        synchronized (fileStreamCache) {
            fileStreamCache.put(strPath, lockableFile);
        }
    }

    public void removeStreamFile(String strPath) {
        synchronized (fileStreamCache) {
            fileStreamCache.remove(strPath);
        }
    }

    public void cleanup() {
        cleanupTimer.cancel();
    }

    public void putNonExistentFile(String strPath) {
        synchronized (nonExistent) {
            nonExistent.add(strPath);
        }
    }

    public boolean isNonExistentFile(String strPath) {
        synchronized (nonExistent) {
            return nonExistent.contains(strPath);
        }
    }

    public void removeNonExistentFile(String strPath) {
        synchronized (nonExistent) {
            nonExistent.remove(strPath);
        }
    }

}
