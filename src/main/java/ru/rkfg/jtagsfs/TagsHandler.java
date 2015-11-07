package ru.rkfg.jtagsfs;

import static ru.rkfg.jtagsfs.Consts.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;

import org.hibernate.NonUniqueResultException;
import org.hibernate.Session;

import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;
import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerFileException;
import ru.rkfg.jtagsfs.domain.FileRecord;
import ru.rkfg.jtagsfs.domain.Tag;

public class TagsHandler extends AbstractTagsHandler {

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

    private HashMap<String, CachedFile> fileCache = new HashMap<String, CachedFile>();
    private HashMap<String, LockableFile> fileStreamCache = new HashMap<String, LockableFile>();

    private Timer cleanupTimer = new Timer("file cache cleanup");

    public TagsHandler() {
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

    private void removeFileFromCache(Filepath filepath) {
        synchronized (fileCache) {
            fileCache.remove(filepath.asStringPath());
        }
    }

    // this permanent session is only used for tags enumeration purposes, it won't be closed/reopened for performance reasons
    private Session getattrSession = HibernateUtil.getSession();

    private File openFileByNameId(String name, Long id) {
        return new File(STORAGE + File.separator + id % 1000 + File.separator + id + IDSEPARATOR + name);
    }

    private File openFileByFilepath(Filepath filepath) {
        String strPath = filepath.asStringPath();
        synchronized (fileCache) {
            CachedFile cachedFile = fileCache.get(strPath);
            if (cachedFile == null) {
                FileRecord fileRecord = getFileRecordByFilepath(filepath);
                cachedFile = new CachedFile(openFileByNameId(fileRecord.getName(), fileRecord.getId()));
                fileCache.put(strPath, cachedFile);
            } else {
                cachedFile.setCreated(System.currentTimeMillis());
            }
            return cachedFile.getFile();
        }
    }

    private FileRecord getFileRecordByFilepath(final Filepath filepath) {
        return HibernateUtil.exec(new HibernateCallback<FileRecord>() {

            public FileRecord run(Session session) {
                return getFileRecordByFilepath(filepath, session);
            }
        });
    }

    private FileRecord getFileRecordByFilepath(final Filepath filepath, Session session) {
        String name = filepath.getName();
        try {
            StringBuilder queryTags = new StringBuilder();
            queryTags.append("from FileRecord f where f.name = :name and (1=1");
            boolean negate = false;
            HashMap<String, Object> tagParams = new HashMap<String, Object>();
            int tagIndex = 0;
            for (String tag : filepath.getPath()) {
                if (tag.equals(CONCATTAGS)) {
                    queryTags.append(" or 1=1");
                } else if (tag.equals(EXCLUDETAGS)) {
                    negate = true;
                } else {
                    queryTags.append(" and :t").append(tagIndex).append(" ").append(negate ? "not " : "")
                            .append("in (select t.name from Tag t where t in elements(f.tags))");
                    tagParams.put("t" + tagIndex, tag);
                    tagIndex++;
                    negate = false;
                }
            }
            queryTags.append(")");
            int index = name.indexOf(IDSEPARATOR);
            if (index > 0) {
                try {
                    Long id = Long.valueOf(name.substring(0, index));
                    queryTags.append(" and f.id = ").append(id);
                } catch (NumberFormatException e) {
                    throw new FSHandlerFileException("File with name " + name
                            + " not found in DB and contains invalid ID before separator.");
                }
            }
            FileRecord fileRecord = (FileRecord) session.createQuery(queryTags.toString())
                    .setString("name", filepath.getStrippedFilename()).setProperties(tagParams).uniqueResult();
            if (fileRecord != null) {
                return fileRecord;
            } else {
                throw new FSHandlerFileException("File with name " + name + " not found in DB.");
            }
        } catch (NonUniqueResultException e) {
            throw new FSHandlerFileException("Duplicate files found with name " + name);
        }
    }

    private List<String> listFiles(final Filepath filepath) {
        return HibernateUtil.exec(new HibernateCallback<List<String>>() {

            public List<String> run(Session session) {
                StringBuilder where = new StringBuilder();
                int n = 0;
                HashMap<String, Object> params = new HashMap<String, Object>();
                boolean negate = false;
                for (String tag : filepath.getPath()) {
                    if (tag.equals(CONCATTAGS)) {
                        where.append(" or 1=1");
                    } else if (tag.equals(EXCLUDETAGS)) {
                        negate = true;
                    } else {
                        Tag tagEntity = (Tag) session.createQuery("from Tag t where t.name = :tag").setString("tag", tag).uniqueResult();
                        if (tagEntity != null) {
                            where.append(" and :tag" + n + (negate ? " not" : "") + " in elements(f.tags)");
                            negate = false;
                            params.put("tag" + n, tagEntity);
                        }
                        n++;
                    }
                }
                @SuppressWarnings("unchecked")
                List<FileRecord> fileRecords = session
                        .createQuery("select f from FileRecord f where 1=1" + where.toString() + " order by f.name").setProperties(params)
                        .list();
                // if we're in @@ directory, every file will have an id and tags anyway
                if (fileRecords.size() > 1 && !filepath.isContentWithTags()) {
                    FileRecord curRec = fileRecords.get(0);
                    boolean doRename = false;
                    boolean equal = false;
                    for (FileRecord fileRecord : fileRecords.subList(1, fileRecords.size())) {
                        equal = curRec.getName().equals(fileRecord.getName());
                        if (equal || doRename) {
                            session.evict(curRec);
                            curRec.setName(curRec.getId() + IDSEPARATOR + curRec.getName());
                        }
                        doRename = equal;
                        curRec = fileRecord;
                    }
                    if (doRename) {
                        session.evict(curRec);
                        curRec.setName(curRec.getId() + IDSEPARATOR + curRec.getName());
                    }
                }
                List<String> result = new LinkedList<String>();
                if (filepath.isContentWithTags()) {
                    StringBuilder strRecord = new StringBuilder();
                    for (FileRecord fileRecord : fileRecords) {
                        strRecord.append(fileRecord.getId()).append(IDSEPARATOR);
                        for (Tag tag : fileRecord.getTags()) {
                            strRecord.append(tag.getName()).append(IDSEPARATOR);
                        }
                        strRecord.append(fileRecord.getName());
                        result.add(strRecord.toString());
                        strRecord.setLength(0);
                    }
                } else {
                    for (FileRecord fileRecord : fileRecords) {
                        result.add(fileRecord.getName());
                    }
                }
                return result;
            }
        });
    }

    private List<Tag> getTagsEntries(final Filepath filepath) {
        return HibernateUtil.exec(new HibernateCallback<List<Tag>>() {

            @SuppressWarnings("unchecked")
            public List<Tag> run(Session session) {
                return session.createQuery("from Tag t where t.name in (:tags)").setParameterList("tags", filepath.getPath()).list();
            }
        });
    }

    @Override
    public void create(final Filepath filepath, final FileInfoWrapper info) throws FSHandlerException {
        if (!filepath.getStrippedFilename().equals(filepath.getName())) {
            throw new FSHandlerException("notfound");
        }
        HibernateUtil.exec(new HibernateCallback<Void>() {

            public Void run(Session session) {
                FileRecord fileRecord = new FileRecord(filepath.getStrippedFilename(), new HashSet<Tag>(getTagsEntries(filepath)));
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
                synchronized (getattrSession) {
                    Tag tag = getTagByName(filepath.getPathLast(), getattrSession);
                    if (tag == null) {
                        throw new FSHandlerException("notfound");
                    }
                    if (checkLackOfParent(filepath, tag)) {
                        // this is not normal, tag has a parent but it's not present in the file path.
                        // There's a chance that the tag was renamed/moved so we're clearing the session cache and trying again.
                        getattrSession.clear();
                        tag = getTagByName(filepath.getPathLast(), getattrSession);
                        if (checkLackOfParent(filepath, tag)) {
                            // we've failed twice so this should be an actual error
                            throw new FSHandlerException("notfound");
                        }
                    }
                }
            }
            stat.mode(VirtualEntry.DIRMODE);
            stat.size(4096);
        } else {
            try {
                File file = openFileByFilepath(filepath);
                stat.size(file.length());
                stat.mode(VirtualEntry.FILEMODE);
                stat.setAllTimesMillis(file.lastModified());
            } catch (FSHandlerFileException e) {
                throw new FSHandlerException("notfound");
            }
        }
    }

    private boolean checkLackOfParent(final Filepath filepath, Tag tag) {
        return tag.getParent() != null
                && (filepath.getPathLength() < 2 || !Arrays.asList(filepath.getPath()).contains(tag.getParent().getName()));
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
        addTagForFilepath(filepath);
    }

    @Override
    public void open(Filepath filepath, FileInfoWrapper info) throws FSHandlerException {
        synchronized (fileStreamCache) {
            String strPath = filepath.asStringPath();
            LockableFile lockable = fileStreamCache.get(strPath);
            if (lockable != null) {
                lockable.lock();
            } else {
                File file = openFileByFilepath(filepath);
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                    RandomAccessFile raFile = new RandomAccessFile(file, "rw");
                    fileStreamCache.put(strPath, new LockableFile(raFile));
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
            synchronized (fileStreamCache) {
                lockableStream = fileStreamCache.get(strPath);
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
            if (filepath.getPathLength() == 0 || filepath.getPathLast().equals(Consts.CONCATTAGS)) {
                tags = getTags(new String[0]);
            } else if (filepath.getPathLast().equals(Consts.EXCLUDETAGS)) {
                tags = getTags(filepath.getPath(), false);
            } else {
                tags = getTags(filepath.getPath(), false);
                tags.add(Consts.CONCATTAGS);
                tags.add(Consts.EXCLUDETAGS);
                tags.add(Consts.ENDOFTAGS);
                tags.add(Consts.TAGGEDCONTENT);
            }
            return tags;
        } else {
            if (filepath.getPathLength() == 0 || filepath.getPathLast().equals(Consts.CONCATTAGS)
                    || filepath.getPathLast().equals(Consts.EXCLUDETAGS)) {
                throw new FSHandlerException("Empty tags or rvalue in concat.");
            }
            return listFiles(filepath);
        }
    }

    @Override
    public void release(Filepath filepath, FileInfoWrapper info) throws FSHandlerException {
        synchronized (fileStreamCache) {
            String strPath = filepath.asStringPath();
            LockableFile lockableFile = fileStreamCache.get(strPath);
            if (lockableFile == null) {
                throw new FSHandlerException("notopened");
            }
            if (lockableFile.release() == 0) {
                try {
                    lockableFile.getFile().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                fileStreamCache.remove(strPath);
            }
        }
    }

    @Override
    public void rename(final Filepath from, final Filepath to) throws FSHandlerException {
        if (from.getName() == null && (!from.isTagPath() || !to.isTagPath() || to.getName() != null)) {
            throw new FSHandlerException("notsupp");
        }
        HibernateUtil.exec(new HibernateCallback<Void>() {

            public Void run(Session session) {
                if (from.getName() == null) {
                    renameTag(from, to, session);
                    return null;
                }
                FileRecord fileRecord = getFileRecordByFilepath(from, session);
                fileRecord.getTags().clear();
                fileRecord.getTags().addAll(getTagsEntries(to));
                String toName = to.getStrippedFilename();
                if (!fileRecord.getName().equals(toName)) {
                    try {
                        File target = openFileByFilepath(to);
                        if (target.exists()) {
                            // delete the target file
                            unlink(to);
                        }
                    } catch (FSHandlerFileException e) {
                        // file not found in DB, no need to delete target
                    }
                    openFileByFilepath(from).renameTo(openFileByNameId(toName, fileRecord.getId()));
                }
                fileRecord.setName(toName);
                removeFileFromCache(from);
                removeFileFromCache(to);
                return null;
            }
        });

    }

    @Override
    public void truncate(Filepath filepath, long offset) throws FSHandlerException {
        try {
            File file = openFileByFilepath(filepath);
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
                openFileByFilepath(filepath).delete();
                session.delete(getFileRecordByFilepath(filepath, session));
                removeFileFromCache(filepath);
                return null;
            }
        });

    }

    @Override
    public int write(Filepath filepath, ByteBuffer buffer, long bufSize, long writeOffset) throws FSHandlerException {
        try {
            String strPath = filepath.asStringPath();
            LockableFile lockable;
            synchronized (fileStreamCache) {
                lockable = fileStreamCache.get(strPath);
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

    @Override
    public void cleanup() {
        cleanupTimer.cancel();
    }
}
