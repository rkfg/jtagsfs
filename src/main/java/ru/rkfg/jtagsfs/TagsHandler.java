package ru.rkfg.jtagsfs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;

import org.hibernate.Session;

import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;
import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerFileException;
import ru.rkfg.jtagsfs.domain.FileRecord;
import ru.rkfg.jtagsfs.domain.Tag;

public class TagsHandler extends UnsupportedFSHandler {

    private class LockableFile {
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

    private HashMap<String, LockableFile> fileCache = new HashMap<String, LockableFile>();

    @Override
    public void create(final Filepath filepath, final FileInfoWrapper info) throws FSHandlerException {
        HibernateUtil.exec(new HibernateCallback<Void>() {

            public Void run(Session session) {
                FileRecord fileRecord = new FileRecord(filepath.getName(), new HashSet<Tag>(FSHandlerManager.getTagsEntries(filepath)));
                session.save(fileRecord);
                return null;
            }
        });
        open(filepath, info);
    }

    @Override
    public void getattr(final Filepath filepath, StatWrapper stat) throws FSHandlerException {
        if (filepath.getName() == null) {
            if (filepath.isTagPath()) {
                Boolean tagExists = HibernateUtil.exec(new HibernateCallback<Boolean>() {

                    @Override
                    public Boolean run(Session session) {
                        return FSHandlerManager.getTagByName(filepath.getPathLast(), session) != null;
                    }
                });
                if (!tagExists) {
                    throw new FSHandlerException("notfound");
                }
            }
            stat.mode(VirtualEntry.DIRMODE);
            stat.size(4096);
        } else {
            try {
                File file = FSHandlerManager.openFileByFilepath(filepath);
                stat.size(file.length());
                stat.mode(VirtualEntry.FILEMODE);
                stat.setAllTimesMillis(file.lastModified());
            } catch (FSHandlerFileException e) {
                throw new FSHandlerException("notfound");
            }
        }
    }

    @Override
    public int getDepth() {
        return 1;
    }

    @Override
    public String getPrefix() {
        return "tags";
    }

    @Override
    public void mkdir(Filepath filepath) throws FSHandlerException {
        FSHandlerManager.addTagForFilepath(filepath);
    }

    @Override
    public void open(Filepath filepath, FileInfoWrapper info) throws FSHandlerException {
        synchronized (fileCache) {
            String strPath = filepath.asStringPath();
            LockableFile lockable = fileCache.get(strPath);
            if (lockable != null) {
                lockable.lock();
            } else {
                File file = FSHandlerManager.openFileByFilepath(filepath);
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                    RandomAccessFile raFile = new RandomAccessFile(file, "rw");
                    fileCache.put(strPath, new LockableFile(raFile));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public int read(Filepath filepath, ByteBuffer buffer, long size, long offset) throws FSHandlerException {
        try {
            String strPath = filepath.asStringPath();
            LockableFile lockableStream;
            synchronized (fileCache) {
                lockableStream = fileCache.get(strPath);
            }
            if (lockableStream == null) {
                throw new FSHandlerException("notopened");
            }
            synchronized (lockableStream) {
                RandomAccessFile file = lockableStream.getFile();
                file.seek(offset);
                int read = file.getChannel().read(buffer);
                if (read < 0) {
                    read = 0;
                }
                return read;
            }
        } catch (IOException e) {
            throw new FSHandlerException("err: " + e.getMessage());
        }
    }

    @Override
    public List<String> readdir(Filepath filepath) throws FSHandlerException {
        if (!filepath.isContent()) {
            List<String> tags;
            if (filepath.getPathLength() == 0 || filepath.getPathLast().equals(Consts.CONCATTAGS) || filepath.getPathLast().equals(Consts.EXCLUDETAGS)) {
                tags = FSHandlerManager.getTags(new String[0]);
            } else {
                tags = FSHandlerManager.getTags(filepath.getPath(), false);
                tags.add(Consts.CONCATTAGS);
                tags.add(Consts.EXCLUDETAGS);
                tags.add(Consts.ENDOFTAGS);
                tags.add(Consts.TAGGEDCONTENT);
            }
            return tags;
        } else {
            if (filepath.getPathLength() == 0 || filepath.getPathLast().equals(Consts.CONCATTAGS) || filepath.getPathLast().equals(Consts.EXCLUDETAGS)) {
                throw new FSHandlerException("Empty tags or rvalue in concat.");
            }
            return FSHandlerManager.listFiles(filepath);
        }
    }

    @Override
    public void release(Filepath filepath, FileInfoWrapper info) throws FSHandlerException {
        synchronized (fileCache) {
            String strPath = filepath.asStringPath();
            LockableFile lockableFile = fileCache.get(strPath);
            if (lockableFile == null) {
                throw new FSHandlerException("notopened");
            }
            if (lockableFile.release() == 0) {
                try {
                    lockableFile.getFile().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                fileCache.remove(strPath);
            }
        }
    }

    @Override
    public void rename(final Filepath from, final Filepath to) throws FSHandlerException {
        if (from.getName() == null) {
            throw new FSHandlerException("notsupp");
        }
        HibernateUtil.exec(new HibernateCallback<Void>() {

            public Void run(Session session) {
                FileRecord fileRecord = FSHandlerManager.getFileRecordByFilepath(from, session);
                fileRecord.getTags().clear();
                fileRecord.getTags().addAll(FSHandlerManager.getTagsEntries(to));
                String toName = FSHandlerManager.stripFilename(to.getName());
                if (!fileRecord.getName().equals(toName)) {
                    try {
                        File target = FSHandlerManager.openFileByFilepath(to);
                        if (target.exists()) {
                            // delete the target file
                            unlink(to);
                        }
                    } catch (FSHandlerFileException e) {
                        // file not found in DB, no need to delete target
                    }
                    FSHandlerManager.openFileByFilepath(from).renameTo(FSHandlerManager.openFileByNameId(toName, fileRecord.getId()));
                }
                fileRecord.setName(toName);
                FSHandlerManager.removeFileFromCache(from);
                FSHandlerManager.removeFileFromCache(to);
                return null;
            }
        });

    }

    @Override
    public void truncate(Filepath filepath, long offset) throws FSHandlerException {
        try {
            File file = FSHandlerManager.openFileByFilepath(filepath);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.getChannel().truncate(offset);
            fileOutputStream.close();
            return;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new FSHandlerException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new FSHandlerException(e.getMessage());
        }
    }

    @Override
    public void unlink(final Filepath filepath) {
        HibernateUtil.exec(new HibernateCallback<Void>() {

            public Void run(Session session) {
                FSHandlerManager.openFileByFilepath(filepath).delete();
                session.delete(FSHandlerManager.getFileRecordByFilepath(filepath, session));
                FSHandlerManager.removeFileFromCache(filepath);
                return null;
            }
        });

    }

    @Override
    public int write(Filepath filepath, ByteBuffer buffer, long bufSize, long writeOffset) throws FSHandlerException {
        try {
            String strPath = filepath.asStringPath();
            LockableFile lockable;
            synchronized (fileCache) {
                lockable = fileCache.get(strPath);
            }
            if (lockable == null) {
                throw new FSHandlerException("notopened");
            }
            synchronized (lockable) {
                RandomAccessFile file = lockable.getFile();
                file.seek(writeOffset);
                file.getChannel().write(buffer);
            }
            return (int) bufSize;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new FSHandlerException("err: " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new FSHandlerException("err: " + e.getMessage());
        }
    }
}
