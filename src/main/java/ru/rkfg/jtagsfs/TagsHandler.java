package ru.rkfg.jtagsfs;

import static ru.rkfg.jtagsfs.Consts.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;

import org.hibernate.NonUniqueResultException;
import org.hibernate.Session;

import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;
import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerFileException;
import ru.rkfg.jtagsfs.domain.FileRecord;
import ru.rkfg.jtagsfs.domain.Tag;

public class TagsHandler extends AbstractTagsHandler {

    CacheManager cacheManager = CacheManager.INSTANCE;

    public TagsHandler() {
    }

    // this permanent session is only used for tags enumeration purposes, it won't be closed/reopened for performance reasons
    private Session getattrSession = HibernateUtil.getSession();
    private Charset charset = Charset.forName("utf-8");

    private File openFileByNameId(String name, Long id) {
        return new File(STORAGE + File.separator + id % 1000 + File.separator + id + IDSEPARATOR + name);
    }

    private File openFileByFilepath(Filepath filepath) {
        String strPath = filepath.asStringPath();
        // make the entire operation atomic
        synchronized (cacheManager) {
            CachedFile cachedFile = cacheManager.getCachedFile(strPath);
            if (cacheManager.isNonExistentFile(strPath)) {
                throw new FSHandlerFileException("cached nonexistent");
            }
            if (cachedFile == null) {
                FileRecord fileRecord = null;
                try {
                    fileRecord = getFileRecordByFilepath(filepath);
                } catch (FSHandlerFileException e) {
                    cacheManager.putNonExistentFile(strPath);
                    throw e;
                }
                cachedFile = new CachedFile(openFileByNameId(fileRecord.getName(), fileRecord.getId()));
                cacheManager.putCachedFile(strPath, cachedFile);
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
            queryTags.append("from FileRecord f left join fetch f.tags where f.name = :name and (1=1");
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

    @Override
    public void create(final Filepath filepath, final FileInfoWrapper info) throws FSHandlerException {
        if (!filepath.getStrippedFilename().equals(filepath.getName())) {
            throw new FSHandlerException("notfound");
        }
        HibernateUtil.exec(new HibernateCallback<Void>() {

            public Void run(Session session) {
                FileRecord fileRecord = new FileRecord(filepath.getStrippedFilename(), new HashSet<Tag>(filepath.getTagsEntries()));
                session.save(fileRecord);
                cacheManager.removeNonExistentFile(filepath.asStringPath());
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
            stat.mode(VirtualEntry.FILEMODE);
            if (filepath.isTagsListPath()) {
                stat.size(65536); // ought to be enough for anybody™
                stat.setAllTimesMillis(System.currentTimeMillis());
            } else {
                try {
                    File file = openFileByFilepath(filepath);
                    stat.size(file.length());
                    stat.setAllTimesMillis(file.lastModified());
                } catch (FSHandlerFileException e) {
                    throw new FSHandlerException("notfound");
                }
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
        if (filepath.isTagsListPath()) {
            return;
        }
        synchronized (cacheManager) {
            String strPath = filepath.asStringPath();
            LockableFile lockable = cacheManager.getStreamFile(strPath);
            if (lockable != null) {
                lockable.lock();
            } else {
                File file = openFileByFilepath(filepath);
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                    RandomAccessFile raFile = new RandomAccessFile(file, "rw");
                    cacheManager.putStreamFile(strPath, new LockableFile(raFile));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public int read(Filepath filepath, ByteBuffer buffer, long size, long offset) throws FSHandlerException {
        if (filepath.isTagsListPath()) {
            int result = 0;
            filepath.removeTagslistExt();
            for (Tag tag : getFileRecordByFilepath(filepath).getTags()) {
                byte[] tagName = tag.getName().getBytes(charset);
                buffer.put(tagName).put((byte) '\n');
                result += tagName.length + 1;
            }
            return result;
        }
        try {
            String strPath = filepath.asStringPath();
            LockableFile lockableStream = cacheManager.getStreamFile(strPath);
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
        synchronized (cacheManager) {
            String strPath = filepath.asStringPath();
            LockableFile lockableFile = cacheManager.getStreamFile(strPath);
            if (lockableFile == null) {
                throw new FSHandlerException("notopened");
            }
            if (lockableFile.release() == 0) {
                try {
                    lockableFile.getFile().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                cacheManager.removeStreamFile(strPath);
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
                fileRecord.getTags().addAll(to.getTagsEntries());
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
                synchronized (cacheManager) {
                    cacheManager.removeCachedFile(from);
                    cacheManager.removeCachedFile(to);
                }
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
                cacheManager.removeCachedFile(filepath);
                return null;
            }
        });

    }

    @Override
    public int write(Filepath filepath, ByteBuffer buffer, long bufSize, long writeOffset) throws FSHandlerException {
        try {
            String strPath = filepath.asStringPath();
            LockableFile lockable = cacheManager.getStreamFile(strPath);
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
