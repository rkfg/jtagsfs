package ru.rkfg.jtagsfs;

import static ru.rkfg.jtagsfs.Consts.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import net.fusejna.StructStat.StatWrapper;

import org.hibernate.Session;

import ru.rkfg.jtagsfs.FSHandlerManager.FSHandlerException;
import ru.rkfg.jtagsfs.domain.Tag;

public class ControlHandler extends UnsupportedFSHandler {

    @Override
    public String getPrefix() {
        return "control";
    }

    @Override
    public List<String> readdir(Filepath filepath) {
        return FSHandlerManager.getTags(filepath.getPath(), true);
    }

    @Override
    public void getattr(Filepath filepath, StatWrapper stat) throws FSHandlerException {
        if (filepath.getPath().length == 0 || filepath.getPath().length > 0 && readdir(filepath.up(1)).contains(filepath.getPathLast())) {
            stat.mode(VirtualEntry.DIRMODE);
            stat.size(4096);
        } else {
            throw new FSHandlerException("notfound");
        }
    }

    @Override
    public int getDepth() {
        return 1;
    }

    @Override
    public int read(Filepath filepath, ByteBuffer buffer, long size, long offset) throws FSHandlerException {
        return 0;
    }

    @Override
    public void mkdir(Filepath filepath) throws FSHandlerException {
        List<String> tags = Arrays.asList(filepath.getPath());
        if (tags.size() == 0 || tags.contains(ENDOFTAGS)) {
            throw new FSHandlerException("invaliddir");
        }
        if (filepath.getPathLength() > 1) {
            FSHandlerManager.addTag(filepath.getPathLast(), filepath.getPath()[filepath.getPathLength() - 2]);
        } else {
            FSHandlerManager.addTag(filepath.getPathLast());
        }
    }

    @Override
    public void rename(final Filepath from, final Filepath to) throws FSHandlerException {
        if (from.getName() != null || to.getName() != null) {
            throw new FSHandlerException("notsupp");
        }
        HibernateUtil.exec(new HibernateCallback<Void>() {

            @Override
            public Void run(Session session) {
                String fromTagName = from.getPathLast();
                String toTagName = to.getPathLast();
                Tag fromTag = FSHandlerManager.getTagByName(fromTagName, session);
                fromTag.setName(toTagName);
                if (to.getPathLength() > 1) {
                    String toParentTagName = to.getPath()[to.getPathLength() - 2];
                    Tag parentTag = FSHandlerManager.getTagByName(toParentTagName, session);
                    fromTag.setParent(parentTag);
                } else {
                    fromTag.setParent(null);
                }
                return null;
            }
        });
    }

    @Override
    public void rmdir(final Filepath filepath) throws FSHandlerException {
        String result = HibernateUtil.exec(new HibernateCallback<String>() {

            @Override
            public String run(Session session) {
                String tagName = filepath.getPathLast();
                Tag toDelete = (Tag) session.createQuery("from Tag t where t.name = :t").setString("t", tagName).uniqueResult();
                if (toDelete == null) {
                    return "notfound";
                }
                if (toDelete.getFiles().size() > 0) {
                    return "access";
                }
                session.delete(toDelete);
                return null;
            }
        });
        if (result != null) {
            throw new FSHandlerException(result);
        }
    }

}
